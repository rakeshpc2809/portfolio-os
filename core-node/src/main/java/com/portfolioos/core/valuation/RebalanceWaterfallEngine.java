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
import java.util.List;
import java.util.Map;

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
        BigDecimal taxDrag
    ) {}

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
        new CoreLtcgTierStrategy()
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

        for (Lot lot : openLots) {
            if (FundTierClassifier.isLegacyFund(lot.assetId(), activeAssetIds)) {
                legacyLots.add(lot);
            } else {
                BucketEngine.Bucket lotBucket = BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName());
                if (bucket == null || lotBucket == bucket) {
                    coreLots.add(lot);
                }
            }
        }

        Map<String, BigDecimal> legacySchemeValueMap = new HashMap<>();
        for (Lot lot : legacyLots) {
            boolean hasNav = navMap != null && navMap.containsKey(lot.assetId());
            if (!hasNav) {
                log.warn("AMFI_NAV_SYNC_ALERT: Missing ISIN {} in navMap during waterfall engine calculation, using fallback costPerUnit {}", lot.assetId(), lot.costPerUnit());
            }
            if (!hasNav && lot.costPerUnit() == null) {
                throw new IllegalStateException("CRITICAL VALUATION ERROR: Asset ISIN " + lot.assetId() + " is missing both live NAV and lot costPerUnit basis.");
            }
            BigDecimal nav = hasNav ? navMap.get(lot.assetId()) : lot.costPerUnit();
            BigDecimal val = lot.remainingUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
            legacySchemeValueMap.put(lot.assetId(), legacySchemeValueMap.getOrDefault(lot.assetId(), BigDecimal.ZERO).add(val));
        }
        Map<String, BigDecimal> legacySchemeTrimmedMap = new HashMap<>();

        List<WaterfallTierStrategy> strategiesToRun = new ArrayList<>(REGULAR_STRATEGIES);
        if (urgent) {
            strategiesToRun.add(URGENT_STCG_STRATEGY);
        }

        for (WaterfallTierStrategy strategy : strategiesToRun) {
            if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;
            List<Lot> candidateLots = strategy.selectLots(legacyLots, coreLots, navMap, today, rules);
            for (Lot lot : candidateLots) {
                if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal lotTarget = remainingTarget;
                if (strategy.tier() == WaterfallTier.LEGACY_FUND) {
                    BigDecimal schemeTotal = legacySchemeValueMap.getOrDefault(lot.assetId(), BigDecimal.ZERO);
                    BigDecimal maxSchemeTrim = schemeTotal.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal alreadyTrimmed = legacySchemeTrimmedMap.getOrDefault(lot.assetId(), BigDecimal.ZERO);
                    BigDecimal schemeCapRemaining = maxSchemeTrim.subtract(alreadyTrimmed).max(BigDecimal.ZERO);
                    if (schemeCapRemaining.compareTo(BigDecimal.ZERO) <= 0) continue;
                    lotTarget = lotTarget.min(schemeCapRemaining);
                }

                LotProcessResult res = processLot(strategy.tier(), lot, navMap, lotTarget, unusedExemption, rules, today, urgent);
                if (res != null && res.proceeds().compareTo(BigDecimal.ZERO) > 0) {
                    steps.add(res.step());
                    satisfiedAmount = satisfiedAmount.add(res.proceeds());
                    remainingTarget = remainingTarget.subtract(res.proceeds());
                    unusedExemption = res.newUnusedExemption();
                    totalTaxDrag = totalTaxDrag.add(res.taxDrag());
                    if (strategy.tier() == WaterfallTier.LEGACY_FUND) {
                        legacySchemeTrimmedMap.put(lot.assetId(),
                            legacySchemeTrimmedMap.getOrDefault(lot.assetId(), BigDecimal.ZERO).add(res.proceeds()));
                    }
                }
            }
        }

        BigDecimal deferredAmount = remainingTarget.max(BigDecimal.ZERO);
        String deferralReason = null;
        if (deferredAmount.compareTo(BigDecimal.ZERO) > 0) {
            deferralReason = "No LTCG-eligible lots or urgency flag to justify STCG sale";
        }

        BigDecimal exemptionConsumed = initialExemption.subtract(unusedExemption);

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
        boolean urgent
    ) {
        BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
        BigDecimal lotValue = lot.remainingUnits().multiply(nav);
        if (lotValue.compareTo(BigDecimal.ZERO) <= 0) return null;

        BigDecimal redemption = lotValue.min(remainingTarget);
        BigDecimal unitsSold = nav.compareTo(BigDecimal.ZERO) > 0 ? redemption.divide(nav, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal costBasis = unitsSold.multiply(lot.costPerUnit());
        BigDecimal gain = redemption.subtract(costBasis);

        AssetCategory cat = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
        long threshold = getThresholdDays(cat, rules);
        long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
        boolean isLtcg = threshold > 0 && holdingDays >= threshold;

        // USER DIRECTIVE (Fix 2a): STCG lots are 100% EXCLUDED during DRIFT or SCHEDULED rebalancing.
        // Under DRAWDOWN or urgent de-risking (urgent == true), controlled STCG realization IS allowed
        // with tax drag explicitly calculated and logged as a trade-off.
        if (!isLtcg && !urgent) {
            return null;
        }

        BigDecimal taxDrag = BigDecimal.ZERO;
        BigDecimal newExemption = unusedExemption;

        if (gain.compareTo(BigDecimal.ZERO) > 0) {
            if (isLtcg) {
                BigDecimal exempt = gain.min(newExemption);
                BigDecimal taxable = gain.subtract(exempt);
                newExemption = newExemption.subtract(exempt).max(BigDecimal.ZERO);
                taxDrag = taxable.multiply(rules.equityLtcgRate()).setScale(2, RoundingMode.HALF_UP);
            } else {
                BigDecimal stcgRate = (cat == AssetCategory.EQUITY) ? rules.equityStcgRate() : new BigDecimal("0.20");
                taxDrag = gain.multiply(stcgRate).setScale(2, RoundingMode.HALF_UP);
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
            taxDrag
        );

        return new LotProcessResult(step, redemption, taxDrag, newExemption);
    }

    private static void sortLotsByTaxCost(List<Lot> lots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules) {
        lots.sort((l1, l2) -> {
            BigDecimal nav1 = navMap.getOrDefault(l1.assetId(), l1.costPerUnit());
            BigDecimal gain1 = nav1.subtract(l1.costPerUnit());
            AssetCategory cat1 = TaxClassifier.detectCategory(l1.assetId(), l1.assetName());
            long thresh1 = getThresholdDays(cat1, rules);
            long days1 = ChronoUnit.DAYS.between(l1.acquisitionDate(), today);
            boolean isLtcg1 = thresh1 > 0 && days1 >= thresh1;
            int rank1 = gain1.compareTo(BigDecimal.ZERO) < 0 ? 0 : (isLtcg1 ? 1 : 2);

            BigDecimal nav2 = navMap.getOrDefault(l2.assetId(), l2.costPerUnit());
            BigDecimal gain2 = nav2.subtract(l2.costPerUnit());
            AssetCategory cat2 = TaxClassifier.detectCategory(l2.assetId(), l2.assetName());
            long thresh2 = getThresholdDays(cat2, rules);
            long days2 = ChronoUnit.DAYS.between(l2.acquisitionDate(), today);
            boolean isLtcg2 = thresh2 > 0 && days2 >= thresh2;
            int rank2 = gain2.compareTo(BigDecimal.ZERO) < 0 ? 0 : (isLtcg2 ? 1 : 2);

            int cmp = Integer.compare(rank1, rank2);
            if (cmp != 0) return cmp;

            BigDecimal pct1 = nav1.compareTo(BigDecimal.ZERO) > 0 ? gain1.divide(nav1, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal pct2 = nav2.compareTo(BigDecimal.ZERO) > 0 ? gain2.divide(nav2, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            return pct1.compareTo(pct2);
        });
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
            List<Lot> lots = legacyLots.stream().filter(l -> {
                BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
                BigDecimal gain = nav.subtract(l.costPerUnit());
                if (gain.compareTo(BigDecimal.ZERO) < 0) return true; // Always allow loss harvest
                AssetCategory cat = TaxClassifier.detectCategory(l.assetId(), l.assetName());
                long threshold = getThresholdDays(cat, rules);
                long holdingDays = ChronoUnit.DAYS.between(l.acquisitionDate(), today);
                return threshold > 0 && holdingDays >= threshold; // Strictly ONLY LTCG lots allowed
            }).collect(java.util.stream.Collectors.toList());

            sortLotsByTaxCost(lots, navMap, today, rules);
            return lots;
        }
    }

    private static class LossHarvestTierStrategy implements WaterfallTierStrategy {
        @Override
        public WaterfallTier tier() { return WaterfallTier.LOSS_HARVEST; }

        @Override
        public List<Lot> selectLots(List<Lot> legacyLots, List<Lot> coreLots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules) {
            return coreLots.stream().filter(l -> {
                BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
                return nav.subtract(l.costPerUnit()).compareTo(BigDecimal.ZERO) < 0;
            }).sorted(Comparator.comparing(l -> {
                BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
                return nav.compareTo(BigDecimal.ZERO) > 0 
                    ? nav.subtract(l.costPerUnit()).divide(nav, 6, RoundingMode.HALF_UP) 
                    : BigDecimal.ZERO;
            })).toList();
        }
    }

    private static class CoreLtcgTierStrategy implements WaterfallTierStrategy {
        @Override
        public WaterfallTier tier() { return WaterfallTier.LTCG_WITHIN_EXEMPTION; }

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
        return coreLots.stream().filter(l -> {
            BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
            if (nav.subtract(l.costPerUnit()).compareTo(BigDecimal.ZERO) < 0) return false;
            AssetCategory cat = TaxClassifier.detectCategory(l.assetId(), l.assetName());
            long threshold = getThresholdDays(cat, rules);
            long holdingDays = ChronoUnit.DAYS.between(l.acquisitionDate(), today);
            boolean isLtcg = threshold > 0 && holdingDays >= threshold;
            return requireLtcg ? isLtcg : !isLtcg;
        }).sorted(Comparator.comparing(l -> {
            BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
            return nav.compareTo(BigDecimal.ZERO) > 0 
                ? nav.subtract(l.costPerUnit()).divide(nav, 6, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO;
        })).toList();
    }
}
