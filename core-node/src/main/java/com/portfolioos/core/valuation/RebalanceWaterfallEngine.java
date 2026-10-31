package com.portfolioos.core.valuation;

import com.portfolioos.core.matcher.FundTierClassifier;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import com.portfolioos.core.service.RebalancePlanEngine;

public class RebalanceWaterfallEngine {

    public record WaterfallStep(
        WaterfallTier tier,
        String lotId,
        String assetId,
        String assetName,
        BigDecimal unitsSold,
        BigDecimal proceeds,
        BigDecimal realizedGain,
        String taxTerm,
        BigDecimal taxDrag,
        String taxRegime,
        BigDecimal exemptionApplied,
        BigDecimal taxableAmount,
        boolean lossOffsetApplied
    ) {
        public WaterfallStep(
            WaterfallTier tier,
            String lotId,
            String assetId,
            String assetName,
            BigDecimal unitsSold,
            BigDecimal proceeds,
            BigDecimal realizedGain,
            String taxTerm,
            BigDecimal taxDrag,
            String taxRegime,
            BigDecimal exemptionApplied,
            BigDecimal taxableAmount
        ) {
            this(tier, lotId, assetId, assetName, unitsSold, proceeds, realizedGain, taxTerm, taxDrag, taxRegime, exemptionApplied, taxableAmount, false);
        }
    }

    public record WaterfallResult(
        BucketEngine.Bucket bucket,
        BigDecimal targetAmount,
        BigDecimal satisfiedAmount,
        BigDecimal deferredAmount,
        String deferralReason,
        List<WaterfallStep> steps,
        BigDecimal totalTaxDrag,
        BigDecimal ltcgExemptionConsumed
    ) {}

    public interface WaterfallTierStrategy {
        WaterfallTier tier();
        List<Lot> selectLots(List<Lot> legacyLots, List<Lot> coreLots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules);
    }

    private static final List<WaterfallTierStrategy> REGULAR_STRATEGIES = List.of(
        new LegacyTierStrategy(),
        new LossHarvestTierStrategy(),
        new CoreLtcgWithinExemptionTierStrategy(),
        new CoreLtcgBeyondExemptionTierStrategy()
    );

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RebalanceWaterfallEngine.class);
    private static final WaterfallTierStrategy URGENT_STCG_STRATEGY = new CoreStcgUrgentTierStrategy();

    public static WaterfallResult buildTrimWaterfall(
        BucketEngine.Bucket bucket,
        BigDecimal trimAmount,
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal remainingExemption,
        boolean urgent,
        LocalDate currentDate,
        String fiscalYear
    ) {
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
        LocalDate today = currentDate != null ? currentDate : LocalDate.now();

        BigDecimal remainingTarget = trimAmount;
        BigDecimal unusedExemption = remainingExemption != null ? remainingExemption : BigDecimal.ZERO;
        BigDecimal initialExemption = unusedExemption;
        BigDecimal totalTaxDrag = BigDecimal.ZERO;
        BigDecimal satisfiedAmount = BigDecimal.ZERO;

        List<WaterfallStep> steps = new ArrayList<>();

        java.util.Set<String> activeAssetIds = FundTierClassifier.findActiveAssetIds(openLots, today);
        List<Lot> legacyLots = new ArrayList<>();
        List<Lot> coreLots = new ArrayList<>();

        boolean isLiquidBucket = (bucket == BucketEngine.Bucket.LIQUID_BUFFER || bucket == BucketEngine.Bucket.LIQUIDITY_ARBITRAGE);

        for (Lot lot : openLots) {
            BucketEngine.Bucket lotBucket = BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName());
            boolean isLiquid = (lotBucket == BucketEngine.Bucket.LIQUID_BUFFER || lotBucket == BucketEngine.Bucket.LIQUIDITY_ARBITRAGE);

            if (isLiquidBucket) {
                // When trimming LIQUID_BUFFER (dry powder drawdown deployment), strictly trim liquid buffer lots
                if (isLiquid) {
                    coreLots.add(lot);
                }
            } else {
                // Equity and other bucket trims: prioritize legacy lots first
                if (!isLiquid && FundTierClassifier.isLegacyFund(lot.assetId(), activeAssetIds)) {
                    legacyLots.add(lot);
                } else {
                    boolean isSatellite = (bucket == BucketEngine.Bucket.EQUITY_SATELLITE ||
                                           bucket == BucketEngine.Bucket.SATELLITE_VALUE ||
                                           bucket == BucketEngine.Bucket.SATELLITE_MOMENTUM ||
                                           bucket == BucketEngine.Bucket.SATELLITE_SMALLCAP) &&
                                          (lotBucket == BucketEngine.Bucket.EQUITY_SATELLITE ||
                                           lotBucket == BucketEngine.Bucket.SATELLITE_VALUE ||
                                           lotBucket == BucketEngine.Bucket.SATELLITE_MOMENTUM ||
                                           lotBucket == BucketEngine.Bucket.SATELLITE_SMALLCAP);
                    boolean isCore = (bucket == BucketEngine.Bucket.EQUITY_CORE) && (lotBucket == BucketEngine.Bucket.EQUITY_CORE);
                    boolean isGold = (bucket == BucketEngine.Bucket.GOLD_SILVER || bucket == BucketEngine.Bucket.HEDGE_COMMODITY) &&
                                     (lotBucket == BucketEngine.Bucket.GOLD_SILVER || lotBucket == BucketEngine.Bucket.HEDGE_COMMODITY);

                    if (bucket == null || lotBucket == bucket || isSatellite || isCore || isGold) {
                        coreLots.add(lot);
                    }
                }
            }
        }

        Map<String, BigDecimal> legacySchemeValueMap = new HashMap<>();
        for (Lot lot : legacyLots) {
            BigDecimal nav = NavResolver.requireValidNav(navMap, lot, "RebalanceWaterfallEngine");
            BigDecimal val = lot.remainingUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
            legacySchemeValueMap.put(lot.assetId(), legacySchemeValueMap.getOrDefault(lot.assetId(), BigDecimal.ZERO).add(val));
        }
        Map<String, BigDecimal> legacySchemeTrimmedMap = new HashMap<>();

        List<Lot> workingLegacyLots = new ArrayList<>(legacyLots);
        List<Lot> workingCoreLots = new ArrayList<>(coreLots);

        List<WaterfallTierStrategy> strategiesToRun = new ArrayList<>(REGULAR_STRATEGIES);
        if (urgent || isLiquidBucket) {
            strategiesToRun.add(URGENT_STCG_STRATEGY);
        }

        for (WaterfallTierStrategy strategy : strategiesToRun) {
            if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;
            if (strategy instanceof LossHarvestTierStrategy) {
                boolean isStep0Legacy = (bucket == BucketEngine.Bucket.LEGACY_HOLDINGS);
                boolean isBucketBeingTrimmed = (trimAmount != null && trimAmount.compareTo(BigDecimal.ZERO) > 0);
                BigDecimal grossStcg = BigDecimal.ZERO;
                BigDecimal grossLtcg = BigDecimal.ZERO;
                for (WaterfallStep s : steps) {
                    if (s.realizedGain() != null && s.realizedGain().compareTo(BigDecimal.ZERO) > 0) {
                        if ("LONG_TERM".equals(s.taxTerm())) {
                            grossLtcg = grossLtcg.add(s.realizedGain());
                        } else {
                            grossStcg = grossStcg.add(s.realizedGain());
                        }
                    }
                }
                if (!shouldHarvestLossLot(isStep0Legacy, isBucketBeingTrimmed, grossStcg, grossLtcg, unusedExemption)) {
                    continue;
                }
            }
            List<Lot> candidateLots = strategy.selectLots(workingLegacyLots, workingCoreLots, navMap, today, rules);
            for (Lot lot : candidateLots) {
                if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;

                Lot currentLot = findWorkingLot(lot.lotId(), workingLegacyLots, workingCoreLots);
                if (currentLot == null || currentLot.remainingUnits().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal lotTarget = remainingTarget;
                if (strategy.tier() == WaterfallTier.LEGACY_FUND) {
                    BigDecimal schemeTotal = legacySchemeValueMap.getOrDefault(currentLot.assetId(), BigDecimal.ZERO);
                    BigDecimal alreadyTrimmed = legacySchemeTrimmedMap.getOrDefault(currentLot.assetId(), BigDecimal.ZERO);
                    BigDecimal schemeCapRemaining = schemeTotal.subtract(alreadyTrimmed).max(BigDecimal.ZERO);
                    if (schemeCapRemaining.compareTo(BigDecimal.ZERO) <= 0) continue;
                    lotTarget = lotTarget.min(schemeCapRemaining);
                }

                LotProcessResult res = processLot(strategy.tier(), currentLot, navMap, lotTarget, unusedExemption, rules, today, urgent, isLiquidBucket);
                if (res != null && res.proceeds().compareTo(BigDecimal.ZERO) > 0) {
                    steps.add(res.step());
                    satisfiedAmount = satisfiedAmount.add(res.proceeds());
                    remainingTarget = remainingTarget.subtract(res.proceeds());
                    unusedExemption = res.newUnusedExemption();
                    totalTaxDrag = totalTaxDrag.add(res.taxDrag());
                    if (strategy.tier() == WaterfallTier.LEGACY_FUND) {
                        legacySchemeTrimmedMap.put(currentLot.assetId(),
                            legacySchemeTrimmedMap.getOrDefault(currentLot.assetId(), BigDecimal.ZERO).add(res.proceeds()));
                    }
                    deductWorkingUnits(currentLot.lotId(), res.step().unitsSold(), workingLegacyLots, workingCoreLots);
                }
            }
        }

        BigDecimal deferredAmount = remainingTarget.max(BigDecimal.ZERO);
        String deferralReason = null;
        if (deferredAmount.compareTo(BigDecimal.ZERO) > 0) {
            deferralReason = "No LTCG-eligible lots or urgency flag to justify STCG sale";
        }

        // F5 Section 70 Netting Second Pass on Waterfall Steps
        boolean hasLossSteps = steps.stream().anyMatch(s -> s.realizedGain().compareTo(BigDecimal.ZERO) < 0);
        if (hasLossSteps) {
            BigDecimal gStcgEq = BigDecimal.ZERO;
            BigDecimal gStcgSlab = BigDecimal.ZERO;
            BigDecimal gStcl = BigDecimal.ZERO;
            BigDecimal gLtcgEq = BigDecimal.ZERO;
            BigDecimal gLtcgNonEq = BigDecimal.ZERO;
            BigDecimal gLtcl = BigDecimal.ZERO;
            for (WaterfallStep s : steps) {
                boolean isLong = "LONG_TERM".equals(s.taxTerm());
                AssetCategory cat = TaxClassifier.detectCategory(s.assetId(), s.assetName());
                if (s.realizedGain().compareTo(BigDecimal.ZERO) < 0) {
                    if (isLong) gLtcl = gLtcl.add(s.realizedGain().abs());
                    else gStcl = gStcl.add(s.realizedGain().abs());
                } else {
                    if (isLong) {
                        if (cat == AssetCategory.EQUITY) gLtcgEq = gLtcgEq.add(s.realizedGain());
                        else gLtcgNonEq = gLtcgNonEq.add(s.realizedGain());
                    } else {
                        if (cat == AssetCategory.EQUITY) gStcgEq = gStcgEq.add(s.realizedGain());
                        else gStcgSlab = gStcgSlab.add(s.realizedGain());
                    }
                }
            }
            RebalancePlanEngine.Section70NettingResult net = RebalancePlanEngine.applySection70Netting(
                gStcgEq, gStcgSlab, gStcl, gLtcgEq, gLtcgNonEq, gLtcl, initialExemption, rules
            );
            steps = prorateSection70Netting(steps, net, rules);
            totalTaxDrag = net.totalNetTaxEstimate();
            unusedExemption = net.remainingHeadroomAfter();
        }

        BigDecimal exemptionConsumed = initialExemption.subtract(unusedExemption).max(BigDecimal.ZERO);

        return new WaterfallResult(
            bucket,
            trimAmount,
            satisfiedAmount.setScale(2, RoundingMode.HALF_UP),
            deferredAmount.setScale(2, RoundingMode.HALF_UP),
            deferralReason,
            steps,
            totalTaxDrag.setScale(2, RoundingMode.HALF_UP),
            exemptionConsumed.setScale(2, RoundingMode.HALF_UP)
        );
    }

    public static List<WaterfallStep> prorateSection70Netting(
        List<WaterfallStep> steps,
        RebalancePlanEngine.Section70NettingResult nettingResult,
        TaxRulesConfig rules
    ) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        if (nettingResult == null || rules == null) {
            return steps;
        }

        List<WaterfallStep> stcgSlabSteps = new ArrayList<>();
        List<WaterfallStep> stcgEqSteps = new ArrayList<>();
        List<WaterfallStep> ltcgNonEqSteps = new ArrayList<>();
        List<WaterfallStep> ltcgEqSteps = new ArrayList<>();

        BigDecimal sumStcgSlab = BigDecimal.ZERO;
        BigDecimal sumStcgEq = BigDecimal.ZERO;
        BigDecimal sumLtcgNonEq = BigDecimal.ZERO;
        BigDecimal sumLtcgEq = BigDecimal.ZERO;

        for (WaterfallStep step : steps) {
            if (step.realizedGain().compareTo(BigDecimal.ZERO) > 0) {
                boolean isLtcg = "LONG_TERM".equals(step.taxTerm());
                AssetCategory cat = TaxClassifier.detectCategory(step.assetId(), step.assetName());
                if (isLtcg) {
                    if (cat == AssetCategory.EQUITY) {
                        ltcgEqSteps.add(step);
                        sumLtcgEq = sumLtcgEq.add(step.realizedGain());
                    } else {
                        ltcgNonEqSteps.add(step);
                        sumLtcgNonEq = sumLtcgNonEq.add(step.realizedGain());
                    }
                } else {
                    if (cat == AssetCategory.EQUITY) {
                        stcgEqSteps.add(step);
                        sumStcgEq = sumStcgEq.add(step.realizedGain());
                    } else {
                        stcgSlabSteps.add(step);
                        sumStcgSlab = sumStcgSlab.add(step.realizedGain());
                    }
                }
            }
        }

        Map<WaterfallStep, WaterfallStep> replacedMap = new IdentityHashMap<>();

        // 1. STCG Slab
        BigDecimal netTaxStcgSlab = nettingResult.netTaxableStcgSlab().multiply(rules.slabRate()).setScale(2, RoundingMode.HALF_UP);
        boolean slabLossOffset = sumStcgSlab.compareTo(BigDecimal.ZERO) > 0 && nettingResult.netTaxableStcgSlab().compareTo(sumStcgSlab) < 0;
        BigDecimal remTaxSlab = netTaxStcgSlab;
        BigDecimal remTaxableSlab = nettingResult.netTaxableStcgSlab();
        for (int i = 0; i < stcgSlabSteps.size(); i++) {
            WaterfallStep s = stcgSlabSteps.get(i);
            BigDecimal lotTax;
            BigDecimal lotTaxable;
            if (i == stcgSlabSteps.size() - 1) {
                lotTax = remTaxSlab;
                lotTaxable = remTaxableSlab;
            } else {
                lotTax = netTaxStcgSlab.multiply(s.realizedGain()).divide(sumStcgSlab, 2, RoundingMode.HALF_UP);
                lotTaxable = nettingResult.netTaxableStcgSlab().multiply(s.realizedGain()).divide(sumStcgSlab, 2, RoundingMode.HALF_UP);
                remTaxSlab = remTaxSlab.subtract(lotTax);
                remTaxableSlab = remTaxableSlab.subtract(lotTaxable);
            }
            replacedMap.put(s, new WaterfallStep(
                s.tier(), s.lotId(), s.assetId(), s.assetName(), s.unitsSold(), s.proceeds(), s.realizedGain(),
                s.taxTerm(), lotTax, s.taxRegime(), s.exemptionApplied(), lotTaxable, slabLossOffset
            ));
        }

        // 2. STCG Equity
        BigDecimal netTaxStcgEq = nettingResult.netTaxableStcgEquity().multiply(rules.equityStcgRate()).setScale(2, RoundingMode.HALF_UP);
        boolean stcgEqLossOffset = sumStcgEq.compareTo(BigDecimal.ZERO) > 0 && nettingResult.netTaxableStcgEquity().compareTo(sumStcgEq) < 0;
        BigDecimal remTaxStcgEq = netTaxStcgEq;
        BigDecimal remTaxableStcgEq = nettingResult.netTaxableStcgEquity();
        for (int i = 0; i < stcgEqSteps.size(); i++) {
            WaterfallStep s = stcgEqSteps.get(i);
            BigDecimal lotTax;
            BigDecimal lotTaxable;
            if (i == stcgEqSteps.size() - 1) {
                lotTax = remTaxStcgEq;
                lotTaxable = remTaxableStcgEq;
            } else {
                lotTax = netTaxStcgEq.multiply(s.realizedGain()).divide(sumStcgEq, 2, RoundingMode.HALF_UP);
                lotTaxable = nettingResult.netTaxableStcgEquity().multiply(s.realizedGain()).divide(sumStcgEq, 2, RoundingMode.HALF_UP);
                remTaxStcgEq = remTaxStcgEq.subtract(lotTax);
                remTaxableStcgEq = remTaxableStcgEq.subtract(lotTaxable);
            }
            replacedMap.put(s, new WaterfallStep(
                s.tier(), s.lotId(), s.assetId(), s.assetName(), s.unitsSold(), s.proceeds(), s.realizedGain(),
                s.taxTerm(), lotTax, s.taxRegime(), s.exemptionApplied(), lotTaxable, stcgEqLossOffset
            ));
        }

        // 3. LTCG Non-Equity
        BigDecimal netTaxLtcgNonEq = nettingResult.netTaxableLtcgNonEquity().multiply(rules.goldInternationalLtcgRate()).setScale(2, RoundingMode.HALF_UP);
        boolean ltcgNonEqLossOffset = sumLtcgNonEq.compareTo(BigDecimal.ZERO) > 0 && nettingResult.netTaxableLtcgNonEquity().compareTo(sumLtcgNonEq) < 0;
        BigDecimal remTaxLtcgNonEq = netTaxLtcgNonEq;
        BigDecimal remTaxableLtcgNonEq = nettingResult.netTaxableLtcgNonEquity();
        for (int i = 0; i < ltcgNonEqSteps.size(); i++) {
            WaterfallStep s = ltcgNonEqSteps.get(i);
            BigDecimal lotTax;
            BigDecimal lotTaxable;
            if (i == ltcgNonEqSteps.size() - 1) {
                lotTax = remTaxLtcgNonEq;
                lotTaxable = remTaxableLtcgNonEq;
            } else {
                lotTax = netTaxLtcgNonEq.multiply(s.realizedGain()).divide(sumLtcgNonEq, 2, RoundingMode.HALF_UP);
                lotTaxable = nettingResult.netTaxableLtcgNonEquity().multiply(s.realizedGain()).divide(sumLtcgNonEq, 2, RoundingMode.HALF_UP);
                remTaxLtcgNonEq = remTaxLtcgNonEq.subtract(lotTax);
                remTaxableLtcgNonEq = remTaxableLtcgNonEq.subtract(lotTaxable);
            }
            replacedMap.put(s, new WaterfallStep(
                s.tier(), s.lotId(), s.assetId(), s.assetName(), s.unitsSold(), s.proceeds(), s.realizedGain(),
                s.taxTerm(), lotTax, s.taxRegime(), s.exemptionApplied(), lotTaxable, ltcgNonEqLossOffset
            ));
        }

        // 4. LTCG Equity
        BigDecimal netTaxLtcgEq = nettingResult.netTaxableLtcgEquity().multiply(rules.equityLtcgRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalExemptionUsed = nettingResult.ltcgExemptionUsed();
        BigDecimal totalTaxableLtcgEq = nettingResult.netTaxableLtcgEquity();
        boolean ltcgEqLossOffset = sumLtcgEq.compareTo(totalExemptionUsed.add(totalTaxableLtcgEq)) > 0;
        BigDecimal remTaxLtcgEq = netTaxLtcgEq;
        BigDecimal remExemptionLtcgEq = totalExemptionUsed;
        BigDecimal remTaxableLtcgEq = totalTaxableLtcgEq;
        for (int i = 0; i < ltcgEqSteps.size(); i++) {
            WaterfallStep s = ltcgEqSteps.get(i);
            BigDecimal lotTax;
            BigDecimal lotExempt;
            BigDecimal lotTaxable;
            if (i == ltcgEqSteps.size() - 1) {
                lotTax = remTaxLtcgEq;
                lotExempt = remExemptionLtcgEq;
                lotTaxable = remTaxableLtcgEq;
            } else {
                lotTax = sumLtcgEq.compareTo(BigDecimal.ZERO) > 0 ? netTaxLtcgEq.multiply(s.realizedGain()).divide(sumLtcgEq, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                lotExempt = sumLtcgEq.compareTo(BigDecimal.ZERO) > 0 ? totalExemptionUsed.multiply(s.realizedGain()).divide(sumLtcgEq, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                lotTaxable = sumLtcgEq.compareTo(BigDecimal.ZERO) > 0 ? totalTaxableLtcgEq.multiply(s.realizedGain()).divide(sumLtcgEq, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                remTaxLtcgEq = remTaxLtcgEq.subtract(lotTax);
                remExemptionLtcgEq = remExemptionLtcgEq.subtract(lotExempt);
                remTaxableLtcgEq = remTaxableLtcgEq.subtract(lotTaxable);
            }
            String regime = lotTax.compareTo(BigDecimal.ZERO) == 0 ? "SEC_112A_EXEMPT" : "SEC_112A_TAXABLE_12_5";
            replacedMap.put(s, new WaterfallStep(
                s.tier(), s.lotId(), s.assetId(), s.assetName(), s.unitsSold(), s.proceeds(), s.realizedGain(),
                s.taxTerm(), lotTax, regime, lotExempt, lotTaxable, ltcgEqLossOffset
            ));
        }

        List<WaterfallStep> result = new ArrayList<>(steps.size());
        for (WaterfallStep step : steps) {
            if (replacedMap.containsKey(step)) {
                result.add(replacedMap.get(step));
            } else {
                result.add(new WaterfallStep(
                    step.tier(), step.lotId(), step.assetId(), step.assetName(), step.unitsSold(), step.proceeds(), step.realizedGain(),
                    step.taxTerm(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), step.taxRegime(), step.exemptionApplied(), step.taxableAmount(), false
                ));
            }
        }
        return result;
    }

    private static Lot findWorkingLot(String lotId, List<Lot> legacy, List<Lot> core) {
        for (Lot l : legacy) {
            if (l.lotId().equals(lotId)) return l;
        }
        for (Lot l : core) {
            if (l.lotId().equals(lotId)) return l;
        }
        return null;
    }

    private static void deductWorkingUnits(String lotId, BigDecimal unitsSold, List<Lot> legacy, List<Lot> core) {
        for (int i = 0; i < legacy.size(); i++) {
            Lot l = legacy.get(i);
            if (l.lotId().equals(lotId)) {
                BigDecimal newUnits = l.remainingUnits().subtract(unitsSold);
                if (newUnits.compareTo(BigDecimal.ZERO) <= 0) {
                    legacy.remove(i);
                } else {
                    legacy.set(i, l.withRemainingUnitsAndCost(newUnits, l.costPerUnit(), l.costPerUnit().multiply(newUnits).setScale(2, RoundingMode.HALF_UP)));
                }
                return;
            }
        }
        for (int i = 0; i < core.size(); i++) {
            Lot l = core.get(i);
            if (l.lotId().equals(lotId)) {
                BigDecimal newUnits = l.remainingUnits().subtract(unitsSold);
                if (newUnits.compareTo(BigDecimal.ZERO) <= 0) {
                    core.remove(i);
                } else {
                    core.set(i, l.withRemainingUnitsAndCost(newUnits, l.costPerUnit(), l.costPerUnit().multiply(newUnits).setScale(2, RoundingMode.HALF_UP)));
                }
                return;
            }
        }
    }

    private record LotProcessResult(
        WaterfallStep step,
        BigDecimal proceeds,
        BigDecimal taxDrag,
        BigDecimal newUnusedExemption
    ) {}

    private static LotProcessResult processLot(
        WaterfallTier tier,
        Lot lot,
        Map<String, BigDecimal> navMap,
        BigDecimal remainingTarget,
        BigDecimal unusedExemption,
        TaxRulesConfig rules,
        LocalDate today,
        boolean urgent,
        boolean isLiquidBucket
    ) {
        BigDecimal nav = NavResolver.requireValidNav(navMap, lot, "RebalanceWaterfallEngine.processLot");
        BigDecimal lotValue = lot.remainingUnits().multiply(nav);
        if (lotValue.compareTo(BigDecimal.ZERO) <= 0) return null;

        long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
        int exitLoadDays = ExitLoadCalculator.getRequiredExitLoadDays(lot.assetId(), lot.assetName());
        if (!urgent && holdingDays < exitLoadDays) {
            return null; // Never incur exit load (e.g. PPFAS < 730d, Arbitrage < 15d) on non-urgent drift/scheduled trims
        }

        AssetCategory cat = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
        long threshold = getThresholdDays(cat, rules);
        boolean isLtcg = threshold > 0 && holdingDays >= threshold;
        boolean isLossLot = nav.subtract(lot.effectiveCostPerUnit(nav)).compareTo(BigDecimal.ZERO) < 0;

        // F5: Short-term loss lots are always eligible in LOSS_HARVEST / LEGACY_FUND tiers even when !urgent
        if (!isLtcg && !isLossLot && !urgent && !isLiquidBucket) {
            return null;
        }

        if (tier == WaterfallTier.LTCG_WITHIN_EXEMPTION && (unusedExemption == null || unusedExemption.compareTo(BigDecimal.ZERO) <= 0 || cat != AssetCategory.EQUITY)) {
            return null;
        }

        BigDecimal redemption = lotValue.min(remainingTarget);
        BigDecimal unitsSold = nav.compareTo(BigDecimal.ZERO) > 0
            ? redemption.divide(nav, 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal costBasis = unitsSold.multiply(lot.effectiveCostPerUnit(nav)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gain = redemption.subtract(costBasis).setScale(2, RoundingMode.HALF_UP);

        // F6: Cap LTCG_WITHIN_EXEMPTION strictly at unusedExemption; remainder of lot will be processed by LTCG_BEYOND_EXEMPTION
        if (tier == WaterfallTier.LTCG_WITHIN_EXEMPTION && gain.compareTo(unusedExemption) > 0 && gain.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal exemptFraction = unusedExemption.divide(gain, 8, RoundingMode.HALF_UP);
            redemption = redemption.multiply(exemptFraction).setScale(2, RoundingMode.HALF_UP);
            unitsSold = nav.compareTo(BigDecimal.ZERO) > 0
                ? redemption.divide(nav, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            costBasis = unitsSold.multiply(lot.effectiveCostPerUnit(nav)).setScale(2, RoundingMode.HALF_UP);
            gain = unusedExemption.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal taxDrag = BigDecimal.ZERO;
        BigDecimal newExemption = unusedExemption;
        BigDecimal exempt = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        String regime;

        if (gain.compareTo(BigDecimal.ZERO) > 0) {
            if (isLtcg) {
                if (cat == AssetCategory.EQUITY) {
                    if (tier == WaterfallTier.LTCG_BEYOND_EXEMPTION) {
                        taxable = gain;
                        taxDrag = taxable.multiply(rules.equityLtcgRate()).setScale(2, RoundingMode.HALF_UP);
                        regime = "SEC_112A_TAXABLE_12_5";
                    } else {
                        exempt = gain.min(newExemption);
                        taxable = gain.subtract(exempt);
                        newExemption = newExemption.subtract(exempt).max(BigDecimal.ZERO);
                        taxDrag = taxable.multiply(rules.equityLtcgRate()).setScale(2, RoundingMode.HALF_UP);
                        regime = taxable.compareTo(BigDecimal.ZERO) == 0 ? "SEC_112A_EXEMPT" : "SEC_112A_TAXABLE_12_5";
                    }
                } else {
                    // Non-equity LTCG (Gold/Silver FoF >730d or Grandfathered Debt) taxed at 12.5% without Sec 112A exemption under Sec 112
                    taxable = gain;
                    taxDrag = gain.multiply(rules.goldInternationalLtcgRate()).setScale(2, RoundingMode.HALF_UP);
                    regime = "SEC_112_TAXABLE_12_5";
                }
            } else {
                // Tier 5 (Equity STCG @ 20%) vs Tier 6 (Sec 50AA / Gold FoF STCG @ 30% Slab Rate)
                taxable = gain;
                BigDecimal stcgRate = (cat == AssetCategory.EQUITY) ? rules.equityStcgRate() : rules.slabRate();
                taxDrag = gain.multiply(stcgRate).setScale(2, RoundingMode.HALF_UP);
                regime = "SLAB_RATE_STCG";
            }
        } else {
            // Loss lot
            if (isLtcg) {
                regime = (cat == AssetCategory.EQUITY) ? "SEC_112A_EXEMPT" : "SEC_112_TAXABLE_12_5";
            } else {
                regime = "SLAB_RATE_STCG";
            }
        }

        WaterfallStep step = new WaterfallStep(
            tier,
            lot.lotId(),
            lot.assetId(),
            lot.assetName(),
            unitsSold,
            redemption.setScale(2, RoundingMode.HALF_UP),
            gain.setScale(2, RoundingMode.HALF_UP),
            isLtcg ? "LONG_TERM" : "SHORT_TERM",
            taxDrag,
            regime,
            exempt.setScale(2, RoundingMode.HALF_UP),
            taxable.setScale(2, RoundingMode.HALF_UP)
        );
        return new LotProcessResult(step, redemption, taxDrag, newExemption);
    }

    /**
     * Enforces RTA FIFO within each scheme while ranking schemes by lowest FIFO head-lot tax cost.
     * Preserves strict acquisitionDate ASC within the same scheme (Sec 45(2A)).
     * Breaks ties across schemes of the same rank using head lot gainRatio ascending.
     */
    private static void sortLotsByTaxCost(List<Lot> lots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules) {
        Map<String, Lot> oldestLotPerScheme = new HashMap<>();
        for (Lot l : lots) {
            oldestLotPerScheme.merge(l.assetId(), l, (a, b) -> {
                int c = a.acquisitionDate().compareTo(b.acquisitionDate());
                if (c != 0) return c < 0 ? a : b;
                return a.lotId().compareTo(b.lotId()) <= 0 ? a : b;
            });
        }
        lots.sort((l1, l2) -> {
            if (l1.assetId().equalsIgnoreCase(l2.assetId())) {
                // Strict chronological FIFO within the same scheme (Sec 45(2A) compliance)
                int cmp = l1.acquisitionDate().compareTo(l2.acquisitionDate());
                if (cmp != 0) return cmp;
                return l1.lotId().compareTo(l2.lotId());
            }
            Lot head1 = oldestLotPerScheme.getOrDefault(l1.assetId(), l1);
            Lot head2 = oldestLotPerScheme.getOrDefault(l2.assetId(), l2);
            int rank1 = computeLotTaxRank(head1, navMap, today, rules);
            int rank2 = computeLotTaxRank(head2, navMap, today, rules);
            if (rank1 != rank2) return Integer.compare(rank1, rank2);

            // Secondary tie-breaker across different schemes that share the same rank:
            // compare the head lot's gainRatio ((nav - head.effectiveCostPerUnit(nav)) / nav) ascending
            BigDecimal nav1 = NavResolver.requireValidNav(navMap, head1, "sortLotsByTaxCost.tieBreaker1");
            BigDecimal nav2 = NavResolver.requireValidNav(navMap, head2, "sortLotsByTaxCost.tieBreaker2");
            BigDecimal gainRatio1 = nav1.compareTo(BigDecimal.ZERO) > 0
                ? nav1.subtract(head1.effectiveCostPerUnit(nav1)).divide(nav1, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            BigDecimal gainRatio2 = nav2.compareTo(BigDecimal.ZERO) > 0
                ? nav2.subtract(head2.effectiveCostPerUnit(nav2)).divide(nav2, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            int ratioCmp = gainRatio1.compareTo(gainRatio2);
            if (ratioCmp != 0) return ratioCmp;

            return l1.assetId().compareTo(l2.assetId());
        });
    }

    private static int computeLotTaxRank(Lot l, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules) {
        BigDecimal nav = NavResolver.requireValidNav(navMap, l, "RebalanceWaterfallEngine.computeLotTaxRank");
        BigDecimal gain = nav.subtract(l.effectiveCostPerUnit(nav));
        if (gain.compareTo(BigDecimal.ZERO) < 0) return 0; // Loss harvest first
        AssetCategory cat = TaxClassifier.detectCategory(l.assetId(), l.assetName());
        long thresh = getThresholdDays(cat, rules);
        long days = ChronoUnit.DAYS.between(l.acquisitionDate(), today);
        boolean isLtcg = thresh > 0 && days >= thresh;
        if (isLtcg && cat == AssetCategory.EQUITY) return 1; // Sec 112A Equity LTCG
        if (isLtcg) return 2; // Gold/Debt 12.5% LTCG
        if (cat == AssetCategory.EQUITY) return 3; // Equity 20% STCG
        return 4; // Slab rate STCG
    }

    private static long getThresholdDays(AssetCategory category, TaxRulesConfig rules) {
        return switch (category) {
            case EQUITY -> rules.equityLtcgThresholdDays();
            case GOLD_SILVER, INTERNATIONAL, SGB -> rules.goldInternationalThresholdDays();
            case DEBT_SPECIFIED_50AA -> -1L;
        };
    }

    // --- Strategy Implementations ---

    private static class LegacyTierStrategy implements WaterfallTierStrategy {
        @Override
        public WaterfallTier tier() { return WaterfallTier.LEGACY_FUND; }

        @Override
        public List<Lot> selectLots(List<Lot> legacyLots, List<Lot> coreLots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules) {
            // RTA FIFO Compliance (F1): Group legacy lots by scheme and preserve chronological FIFO prefix.
            Map<String, List<Lot>> byScheme = new HashMap<>();
            for (Lot l : legacyLots) {
                byScheme.computeIfAbsent(l.assetId(), k -> new ArrayList<>()).add(l);
            }
            List<Lot> fifoLegacy = new ArrayList<>();
            for (List<Lot> schemeLots : byScheme.values()) {
                schemeLots.sort(Comparator.comparing(Lot::acquisitionDate).thenComparing(Lot::lotId));
                for (Lot l : schemeLots) {
                    BigDecimal nav = NavResolver.requireValidNav(navMap, l, "RebalanceWaterfallEngine.LegacyTier");
                    BigDecimal gain = nav.subtract(l.effectiveCostPerUnit(nav));
                    if (gain.compareTo(BigDecimal.ZERO) < 0) {
                        fifoLegacy.add(l);
                    } else {
                        AssetCategory cat = TaxClassifier.detectCategory(l.assetId(), l.assetName());
                        long threshold = getThresholdDays(cat, rules);
                        long holdingDays = ChronoUnit.DAYS.between(l.acquisitionDate(), today);
                        boolean isLtcg = threshold > 0 && holdingDays >= threshold;
                        if (isLtcg) {
                            fifoLegacy.add(l);
                        } else {
                            break; // Older STCG gain lot blocks reaching any newer lots in this scheme under RTA FIFO
                        }
                    }
                }
            }
            sortLotsByTaxCost(fifoLegacy, navMap, today, rules);
            return fifoLegacy;
        }
    }

    public static BigDecimal resolveLargeMidcapTargetWeight(LocalDate today) {
        com.portfolioos.core.rules.BucketConfigLoader.BucketTargetVersion activeVersion =
            com.portfolioos.core.rules.BucketConfigLoader.getActiveVersion(today != null ? today : LocalDate.now());
        if (activeVersion == null || activeVersion.targets() == null) {
            String msg = "CONFIG ERROR: Unable to load active bucket target configuration for date " + today;
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        for (com.portfolioos.core.rules.BucketConfigLoader.BucketTargetConfig tc : activeVersion.targets()) {
            if (tc.bucket() != null && (tc.bucket().equalsIgnoreCase("EQUITY_CORE") || tc.bucket().equalsIgnoreCase("CORE") || "CORE".equalsIgnoreCase(tc.strategy()))) {
                if (tc.preferredFunds() != null) {
                    for (com.portfolioos.core.rules.BucketConfigLoader.PreferredFundConfig pfc : tc.preferredFunds()) {
                        if ("INF109KC12U0".equalsIgnoreCase(pfc.fundId()) && pfc.allocationWeight() > 0) {
                            return BigDecimal.valueOf(pfc.allocationWeight()).setScale(4, RoundingMode.HALF_UP);
                        }
                    }
                }
            }
        }

        String msg = "CONFIG ERROR: Missing target allocation weight for LargeMidcap 250 (INF109KC12U0) in Core bucket config for date " + today;
        log.error(msg);
        throw new IllegalStateException(msg);
    }

    public static List<Lot> filterOverweightCoreLots(List<Lot> coreLots, Map<String, BigDecimal> navMap, LocalDate today) {
        if (coreLots == null || coreLots.isEmpty()) return List.of();

        BigDecimal lmValue = BigDecimal.ZERO;
        BigDecimal ppfcValue = BigDecimal.ZERO;

        for (Lot l : coreLots) {
            BigDecimal nav = NavResolver.requireValidNav(navMap, l, "RebalanceWaterfallEngine.filterOverweightCoreLots");
            BigDecimal val = l.remainingUnits().multiply(nav);
            if ("INF109KC12U0".equalsIgnoreCase(l.assetId())) {
                lmValue = lmValue.add(val);
            } else if ("INF879O01027".equalsIgnoreCase(l.assetId())) {
                ppfcValue = ppfcValue.add(val);
            }
        }

        BigDecimal totalCore = lmValue.add(ppfcValue);
        if (totalCore.compareTo(BigDecimal.ZERO) <= 0) return coreLots;

        BigDecimal targetLmWeight = resolveLargeMidcapTargetWeight(today);

        BigDecimal lmRatio = lmValue.divide(totalCore, 4, RoundingMode.HALF_UP);
        String targetTrimIsin = null;
        if (lmRatio.compareTo(targetLmWeight) > 0) {
            targetTrimIsin = "INF109KC12U0"; // Trim LargeMid250 only, protect PPFC
        } else if (lmRatio.compareTo(targetLmWeight) < 0) {
            targetTrimIsin = "INF879O01027"; // Trim PPFC only, protect LargeMid250
        }

        if (targetTrimIsin != null) {
            String finalTargetIsin = targetTrimIsin;
            List<Lot> filtered = coreLots.stream().filter(l -> finalTargetIsin.equalsIgnoreCase(l.assetId())).toList();
            if (!filtered.isEmpty()) return filtered;
        }

        return coreLots;
    }

    private static class LossHarvestTierStrategy implements WaterfallTierStrategy {
        @Override
        public WaterfallTier tier() { return WaterfallTier.LOSS_HARVEST; }

        @Override
        public List<Lot> selectLots(List<Lot> legacyLots, List<Lot> coreLots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules) {
            List<Lot> eligibleCore = filterOverweightCoreLots(coreLots, navMap, today);
            // RTA FIFO Compliance (F1): A lot in a scheme is only redeemable for loss harvesting if ALL older lots
            // in that same scheme are also loss-making (i.e. an unbroken prefix of the scheme's FIFO queue).
            List<Lot> fifoLossPrefix = new ArrayList<>();
            Map<String, List<Lot>> byScheme = new HashMap<>();
            for (Lot l : eligibleCore) {
                byScheme.computeIfAbsent(l.assetId(), k -> new ArrayList<>()).add(l);
            }
            for (List<Lot> schemeLots : byScheme.values()) {
                schemeLots.sort(Comparator.comparing(Lot::acquisitionDate).thenComparing(Lot::lotId));
                for (Lot l : schemeLots) {
                    BigDecimal nav = NavResolver.requireValidNav(navMap, l, "RebalanceWaterfallEngine.LossHarvestTier");
                    if (nav.subtract(l.effectiveCostPerUnit(nav)).compareTo(BigDecimal.ZERO) < 0) {
                        fifoLossPrefix.add(l);
                    } else {
                        break; // Older lot is in positive gain; RTA FIFO blocks reaching any newer loss lots behind it!
                    }
                }
            }
            sortLotsByTaxCost(fifoLossPrefix, navMap, today, rules);
            return fifoLossPrefix;
        }
    }

    private static class CoreLtcgWithinExemptionTierStrategy implements WaterfallTierStrategy {
        @Override
        public WaterfallTier tier() { return WaterfallTier.LTCG_WITHIN_EXEMPTION; }

        @Override
        public List<Lot> selectLots(List<Lot> legacyLots, List<Lot> coreLots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules) {
            return selectCoreLotsByHoldingCondition(coreLots, navMap, today, rules, true);
        }
    }

    private static class CoreLtcgBeyondExemptionTierStrategy implements WaterfallTierStrategy {
        @Override
        public WaterfallTier tier() { return WaterfallTier.LTCG_BEYOND_EXEMPTION; }

        @Override
        public List<Lot> selectLots(List<Lot> legacyLots, List<Lot> coreLots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules) {
            return selectCoreLotsByHoldingCondition(coreLots, navMap, today, rules, true);
        }
    }

    private static class CoreStcgUrgentTierStrategy implements WaterfallTierStrategy {
        @Override
        public WaterfallTier tier() { return WaterfallTier.STCG_URGENT_ONLY; }

        @Override
        public List<Lot> selectLots(List<Lot> legacyLots, List<Lot> coreLots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules) {
            return selectCoreLotsByHoldingCondition(coreLots, navMap, today, rules, false);
        }
    }

    private static List<Lot> selectCoreLotsByHoldingCondition(
        List<Lot> coreLots,
        Map<String, BigDecimal> navMap,
        LocalDate today,
        TaxRulesConfig rules,
        boolean requireLtcg
    ) {
        List<Lot> eligibleCore = filterOverweightCoreLots(coreLots, navMap, today);
        // RTA FIFO Compliance (F1): Within each scheme, lots MUST be consumed in strict acquisitionDate ASC order.
        // Never sort lots within the same scheme by gain per unit!
        Map<String, List<Lot>> byScheme = new HashMap<>();
        for (Lot l : eligibleCore) {
            byScheme.computeIfAbsent(l.assetId(), k -> new ArrayList<>()).add(l);
        }
        List<Lot> fifoOrdered = new ArrayList<>();
        for (List<Lot> schemeLots : byScheme.values()) {
            schemeLots.sort(Comparator.comparing(Lot::acquisitionDate).thenComparing(Lot::lotId));
            for (Lot l : schemeLots) {
                BigDecimal nav = NavResolver.requireValidNav(navMap, l, "RebalanceWaterfallEngine.selectCoreLots");
                if (nav.subtract(l.effectiveCostPerUnit(nav)).compareTo(BigDecimal.ZERO) < 0) {
                    // F1 Residual: Never skip over an unsold older loss lot to sell a newer gain lot in the same scheme!
                    break;
                }
                AssetCategory cat = TaxClassifier.detectCategory(l.assetId(), l.assetName());
                long threshold = getThresholdDays(cat, rules);
                long holdingDays = ChronoUnit.DAYS.between(l.acquisitionDate(), today);
                boolean isLtcg = threshold > 0 && holdingDays >= threshold;
                if (requireLtcg ? isLtcg : !isLtcg) {
                    fifoOrdered.add(l);
                } else if (!requireLtcg && isLtcg) {
                    continue; // Already consumed in LTCG tier
                } else {
                    break; // Chronological FIFO boundary reached
                }
            }
        }
        sortLotsByTaxCost(fifoOrdered, navMap, today, rules);
        return fifoOrdered;
    }

    public static boolean shouldHarvestLossLot(
        boolean isStep0Legacy,
        boolean isBucketBeingTrimmed,
        BigDecimal netStcgAfterCarryForward,
        BigDecimal netLtcgAfterCarryForward,
        BigDecimal remaining112aHeadroom
    ) {
        if (isStep0Legacy) {
            return true;
        }
        if (isBucketBeingTrimmed) {
            return true;
        }
        if (netStcgAfterCarryForward != null && netStcgAfterCarryForward.compareTo(BigDecimal.ZERO) > 0) {
            return true;
        }
        if (netLtcgAfterCarryForward != null && remaining112aHeadroom != null
                && netLtcgAfterCarryForward.compareTo(remaining112aHeadroom) > 0) {
            return true;
        }
        return false;
    }

    public record RoutingResult(
        Map<com.portfolioos.core.config.BucketTargetsConfig.BucketType, BigDecimal> bucketBuysInr,
        BigDecimal cashOverflowInr
    ) {}

    public static RoutingResult routeProceedsV24(
        com.portfolioos.core.config.BucketTargetsConfig config,
        com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType triggerType,
        BigDecimal coreTrimPoolInr,
        BigDecimal generalPoolInr,
        BigDecimal effectiveActiveCorpusInr,
        Map<com.portfolioos.core.config.BucketTargetsConfig.BucketType, BigDecimal> postSellValuesInr,
        Map<com.portfolioos.core.config.BucketTargetsConfig.BucketType, BigDecimal> targetWeightsPct
    ) {
        BigDecimal minOrder = config.globalInvariants().minTradeAmountInr();
        BigDecimal hardCapPct = config.globalInvariants().liquidHardCapPct();

        Map<com.portfolioos.core.config.BucketTargetsConfig.BucketType, BigDecimal> buys =
            new java.util.EnumMap<>(com.portfolioos.core.config.BucketTargetsConfig.BucketType.class);
        for (com.portfolioos.core.config.BucketTargetsConfig.BucketType b : com.portfolioos.core.config.BucketTargetsConfig.BucketType.values()) {
            buys.put(b, BigDecimal.ZERO);
        }

        BigDecimal preLiquid = postSellValuesInr.getOrDefault(com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER, BigDecimal.ZERO);
        BigDecimal maxAllowedLiquidVal = effectiveActiveCorpusInr.multiply(hardCapPct)
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal maxAdditionalLiquidHeadroom = maxAllowedLiquidVal.subtract(preLiquid).max(BigDecimal.ZERO);

        Map<com.portfolioos.core.config.BucketTargetsConfig.BucketType, BigDecimal> deficits =
            new java.util.EnumMap<>(com.portfolioos.core.config.BucketTargetsConfig.BucketType.class);
        for (com.portfolioos.core.config.BucketTargetsConfig.BucketType b : com.portfolioos.core.config.BucketTargetsConfig.BucketType.values()) {
            deficits.put(b, BigDecimal.ZERO);
        }
        for (var entry : targetWeightsPct.entrySet()) {
            com.portfolioos.core.config.BucketTargetsConfig.BucketType b = entry.getKey();
            BigDecimal targetVal = effectiveActiveCorpusInr.multiply(entry.getValue())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal currentVal = postSellValuesInr.getOrDefault(b, BigDecimal.ZERO);
            BigDecimal def = targetVal.subtract(currentVal).max(BigDecimal.ZERO);
            deficits.put(b, def);
        }

        BigDecimal cashOverflow = BigDecimal.ZERO;

        // 0. GOLD_FLOOR_BACKSTOP Routing (Buy-only, 100% into GOLD_SILVER)
        if (triggerType == com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType.GOLD_FLOOR_BACKSTOP) {
            BigDecimal pool = generalPoolInr.add(coreTrimPoolInr);
            if (pool.compareTo(minOrder) < 0) {
                return new RoutingResult(buys, pool);
            }
            buys.put(com.portfolioos.core.config.BucketTargetsConfig.BucketType.GOLD_SILVER, pool);
            return new RoutingResult(buys, BigDecimal.ZERO);
        }

        // 1. DRAWDOWN Routing: Strictly into EQUITY_CORE first, then SATELLITE (0% to Gold)
        if (triggerType == com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType.DRAWDOWN) {
            BigDecimal pool = generalPoolInr.add(coreTrimPoolInr);
            pool = fillBucketV24(buys, deficits, com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_CORE, pool, minOrder);
            pool = fillBucketV24(buys, deficits, com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_SATELLITE, pool, minOrder);
            // If drawdown deployment exceeds calculated deficits, all remaining crisis dry powder goes to EQUITY_CORE
            if (pool.compareTo(minOrder) >= 0) {
                buys.put(com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_CORE,
                    buys.get(com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_CORE).add(pool));
                pool = BigDecimal.ZERO;
            }
            cashOverflow = cashOverflow.add(pool);
            return new RoutingResult(buys, cashOverflow);
        }

        // 2. Core Trim Pool Routing
        BigDecimal remCorePool = coreTrimPoolInr;
        remCorePool = fillBucketV24(buys, deficits, com.portfolioos.core.config.BucketTargetsConfig.BucketType.GOLD_SILVER, remCorePool, minOrder);
        remCorePool = fillBucketV24(buys, deficits, com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER, remCorePool, minOrder);
        remCorePool = fillBucketV24(buys, deficits, com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_CORE, remCorePool, minOrder);

        BigDecimal liquidUsedSoFar = buys.get(com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER);
        BigDecimal remainingLiquidCap = maxAdditionalLiquidHeadroom.subtract(liquidUsedSoFar).max(BigDecimal.ZERO);
        if (remCorePool.compareTo(minOrder) >= 0 && remainingLiquidCap.compareTo(minOrder) >= 0) {
            BigDecimal sinkAlloc = remCorePool.min(remainingLiquidCap);
            buys.put(com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER, liquidUsedSoFar.add(sinkAlloc));
            remCorePool = remCorePool.subtract(sinkAlloc);
        }
        cashOverflow = cashOverflow.add(remCorePool);

        if (coreTrimPoolInr.compareTo(BigDecimal.ZERO) > 0) {
            deficits.put(com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_SATELLITE, BigDecimal.ZERO);
        }

        // 3. General Pool Routing
        BigDecimal remGeneral = generalPoolInr;
        BigDecimal totalRemainingDeficit = deficits.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        if (remGeneral.compareTo(minOrder) >= 0 && totalRemainingDeficit.compareTo(BigDecimal.ZERO) > 0) {
            if (remGeneral.compareTo(totalRemainingDeficit) >= 0) {
                for (com.portfolioos.core.config.BucketTargetsConfig.BucketType b : List.of(
                        com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_CORE,
                        com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_SATELLITE,
                        com.portfolioos.core.config.BucketTargetsConfig.BucketType.GOLD_SILVER,
                        com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER)) {
                    BigDecimal d = deficits.getOrDefault(b, BigDecimal.ZERO);
                    if (d.compareTo(minOrder) >= 0) {
                        buys.put(b, buys.getOrDefault(b, BigDecimal.ZERO).add(d));
                        remGeneral = remGeneral.subtract(d);
                        deficits.put(b, BigDecimal.ZERO);
                    }
                }
            } else {
                BigDecimal allocatedSum = BigDecimal.ZERO;
                com.portfolioos.core.config.BucketTargetsConfig.BucketType largestDeficitBucket =
                    com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_CORE;
                BigDecimal maxDef = BigDecimal.ZERO;

                for (com.portfolioos.core.config.BucketTargetsConfig.BucketType b : List.of(
                        com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_CORE,
                        com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_SATELLITE,
                        com.portfolioos.core.config.BucketTargetsConfig.BucketType.GOLD_SILVER,
                        com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER)) {
                    BigDecimal d = deficits.getOrDefault(b, BigDecimal.ZERO);
                    if (d.compareTo(maxDef) > 0) {
                        maxDef = d;
                        largestDeficitBucket = b;
                    }
                    BigDecimal share = remGeneral.multiply(d)
                        .divide(totalRemainingDeficit, 2, RoundingMode.DOWN);
                    if (share.compareTo(minOrder) >= 0) {
                        buys.put(b, buys.getOrDefault(b, BigDecimal.ZERO).add(share));
                        allocatedSum = allocatedSum.add(share);
                    }
                }
                BigDecimal remainder = remGeneral.subtract(allocatedSum);
                if (buys.getOrDefault(largestDeficitBucket, BigDecimal.ZERO).add(remainder).compareTo(deficits.getOrDefault(largestDeficitBucket, BigDecimal.ZERO)) <= 0
                        && buys.getOrDefault(largestDeficitBucket, BigDecimal.ZERO).add(remainder).compareTo(minOrder) >= 0) {
                    buys.put(largestDeficitBucket, buys.getOrDefault(largestDeficitBucket, BigDecimal.ZERO).add(remainder));
                    remGeneral = BigDecimal.ZERO;
                } else {
                    remGeneral = remainder;
                }
            }
        }

        BigDecimal finalLiquidHeadroom = maxAdditionalLiquidHeadroom
            .subtract(buys.getOrDefault(com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER, BigDecimal.ZERO)).max(BigDecimal.ZERO);
        if (remGeneral.compareTo(minOrder) >= 0 && finalLiquidHeadroom.compareTo(minOrder) >= 0) {
            BigDecimal extraLiq = remGeneral.min(finalLiquidHeadroom);
            buys.put(com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER,
                buys.getOrDefault(com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER, BigDecimal.ZERO).add(extraLiq));
            remGeneral = remGeneral.subtract(extraLiq);
        }
        cashOverflow = cashOverflow.add(remGeneral);

        return new RoutingResult(buys, cashOverflow);
    }

    private static BigDecimal fillBucketV24(
            Map<com.portfolioos.core.config.BucketTargetsConfig.BucketType, BigDecimal> buys,
            Map<com.portfolioos.core.config.BucketTargetsConfig.BucketType, BigDecimal> deficits,
            com.portfolioos.core.config.BucketTargetsConfig.BucketType bucket,
            BigDecimal availablePool,
            BigDecimal minOrder) {
        BigDecimal deficit = deficits.getOrDefault(bucket, BigDecimal.ZERO);
        if (availablePool.compareTo(minOrder) < 0 || deficit.compareTo(minOrder) < 0) {
            return availablePool;
        }
        BigDecimal alloc = availablePool.min(deficit);
        if (alloc.compareTo(minOrder) < 0) {
            return availablePool;
        }
        buys.put(bucket, buys.get(bucket).add(alloc));
        deficits.put(bucket, deficit.subtract(alloc));
        return availablePool.subtract(alloc);
    }

    public record TrimDestinationAllocation(
        BigDecimal toGold,
        BigDecimal toArbTarget,
        BigDecimal toCore,
        BigDecimal toArbTerminal,
        BigDecimal toCashOverflow
    ) {}

    public static TrimDestinationAllocation routeTrimProceeds(
        BigDecimal trimAmount,
        BigDecimal goldValue,
        BigDecimal goldTarget,
        BigDecimal arbValue,
        BigDecimal arbTarget,
        BigDecimal coreValue,
        BigDecimal coreTarget,
        BigDecimal totalPortfolioValue
    ) {
        BigDecimal rem = trimAmount != null ? trimAmount : BigDecimal.ZERO;
        BigDecimal gVal = goldValue != null ? goldValue : BigDecimal.ZERO;
        BigDecimal gTgt = goldTarget != null ? goldTarget : BigDecimal.ZERO;
        BigDecimal aVal = arbValue != null ? arbValue : BigDecimal.ZERO;
        BigDecimal aTgt = arbTarget != null ? arbTarget : BigDecimal.ZERO;
        BigDecimal cVal = coreValue != null ? coreValue : BigDecimal.ZERO;
        BigDecimal cTgt = coreTarget != null ? coreTarget : BigDecimal.ZERO;
        BigDecimal totalVal = totalPortfolioValue != null ? totalPortfolioValue : BigDecimal.ZERO;

        // Step 1: Hedge floor (Gold/Silver target = 10%)
        BigDecimal goldDeficit = gTgt.subtract(gVal).max(BigDecimal.ZERO);
        BigDecimal toGold = rem.min(goldDeficit).setScale(2, RoundingMode.HALF_UP);
        rem = rem.subtract(toGold);

        // Step 2: Liquidity target fill (Arbitrage target = 10%)
        BigDecimal arbDeficit = aTgt.subtract(aVal).max(BigDecimal.ZERO);
        BigDecimal toArbTarget = rem.min(arbDeficit).setScale(2, RoundingMode.HALF_UP);
        rem = rem.subtract(toArbTarget);

        // Step 3: Core deficit fill (Core target = 50%)
        BigDecimal coreDeficit = cTgt.subtract(cVal).max(BigDecimal.ZERO);
        BigDecimal toCore = rem.min(coreDeficit).setScale(2, RoundingMode.HALF_UP);
        rem = rem.subtract(toCore);

        // Step 4: Arbitrage terminal sink, capped at 15% hard ceiling
        BigDecimal arbHardCap = totalVal.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal arbPostStep2 = aVal.add(toArbTarget);
        BigDecimal arbHeadroom = arbHardCap.subtract(arbPostStep2).max(BigDecimal.ZERO);
        BigDecimal toArbTerminal = rem.min(arbHeadroom).setScale(2, RoundingMode.HALF_UP);
        rem = rem.subtract(toArbTerminal);

        // Step 5: Cash overflow (UNALLOCATED_CASH ledger entry)
        BigDecimal toCashOverflow = rem.setScale(2, RoundingMode.HALF_UP);

        return new TrimDestinationAllocation(toGold, toArbTarget, toCore, toArbTerminal, toCashOverflow);
    }
}
