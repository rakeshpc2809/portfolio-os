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

        List<WaterfallTierStrategy> strategiesToRun = new ArrayList<>(REGULAR_STRATEGIES);
        if (urgent || isLiquidBucket) {
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
                    BigDecimal alreadyTrimmed = legacySchemeTrimmedMap.getOrDefault(lot.assetId(), BigDecimal.ZERO);
                    BigDecimal schemeCapRemaining = schemeTotal.subtract(alreadyTrimmed).max(BigDecimal.ZERO);
                    if (schemeCapRemaining.compareTo(BigDecimal.ZERO) <= 0) continue;
                    lotTarget = lotTarget.min(schemeCapRemaining);
                }

                LotProcessResult res = processLot(strategy.tier(), lot, navMap, lotTarget, unusedExemption, rules, today, urgent, isLiquidBucket);
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

        if (!isLtcg && !urgent && !isLiquidBucket) {
            return null;
        }

        BigDecimal redemption = lotValue.min(remainingTarget);
        BigDecimal unitsSold = nav.compareTo(BigDecimal.ZERO) > 0
            ? redemption.divide(nav, 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal costBasis = unitsSold.multiply(lot.effectiveCostPerUnit(nav)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gain = redemption.subtract(costBasis).setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxDrag = BigDecimal.ZERO;
        BigDecimal newExemption = unusedExemption;
        if (gain.compareTo(BigDecimal.ZERO) > 0) {
            if (isLtcg) {
                if (cat == AssetCategory.EQUITY) {
                    BigDecimal exempt = gain.min(newExemption);
                    BigDecimal taxable = gain.subtract(exempt);
                    newExemption = newExemption.subtract(exempt).max(BigDecimal.ZERO);
                    taxDrag = taxable.multiply(rules.equityLtcgRate()).setScale(2, RoundingMode.HALF_UP);
                } else {
                    // Tier 3: Gold/Silver FoF (>730d) or Grandfathered Debt LTCG taxed at 12.5% without Sec 112A exemption
                    taxDrag = gain.multiply(rules.goldInternationalLtcgRate()).setScale(2, RoundingMode.HALF_UP);
                }
            } else {
                // Tier 5 (Equity STCG @ 20%) vs Tier 6 (Sec 50AA / Gold FoF STCG @ 30% Slab Rate)
                BigDecimal stcgRate = (cat == AssetCategory.EQUITY) ? rules.equityStcgRate() : rules.slabRate();
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

    /**
     * Enforces RTA FIFO within each scheme while ranking schemes by lowest FIFO head-lot tax cost.
     */
    private static void sortLotsByTaxCost(List<Lot> lots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules) {
        Map<String, Lot> oldestLotPerScheme = new HashMap<>();
        for (Lot l : lots) {
            oldestLotPerScheme.merge(l.assetId(), l, (a, b) ->
                a.acquisitionDate().isBefore(b.acquisitionDate()) ? a : b
            );
        }
        lots.sort((l1, l2) -> {
            if (l1.assetId().equalsIgnoreCase(l2.assetId())) {
                // Strict chronological FIFO within the same scheme (Sec 45(2A) compliance)
                return l1.acquisitionDate().compareTo(l2.acquisitionDate());
            }
            Lot head1 = oldestLotPerScheme.getOrDefault(l1.assetId(), l1);
            Lot head2 = oldestLotPerScheme.getOrDefault(l2.assetId(), l2);
            int rank1 = computeLotTaxRank(head1, navMap, today, rules);
            int rank2 = computeLotTaxRank(head2, navMap, today, rules);
            if (rank1 != rank2) return Integer.compare(rank1, rank2);
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
            List<Lot> lots = legacyLots.stream().filter(l -> {
                BigDecimal nav = NavResolver.requireValidNav(navMap, l, "RebalanceWaterfallEngine.LegacyTier");
                BigDecimal gain = nav.subtract(l.effectiveCostPerUnit(nav));
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
            return eligibleCore.stream().filter(l -> {
                BigDecimal nav = NavResolver.requireValidNav(navMap, l, "RebalanceWaterfallEngine.LossHarvestTier");
                return nav.subtract(l.effectiveCostPerUnit(nav)).compareTo(BigDecimal.ZERO) < 0;
            }).sorted(Comparator.comparing(l -> {
                BigDecimal nav = NavResolver.requireValidNav(navMap, l, "RebalanceWaterfallEngine.LossHarvestTier");
                return nav.subtract(l.effectiveCostPerUnit(nav));
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
        List<Lot> eligibleCore = filterOverweightCoreLots(coreLots, navMap, today);
        return eligibleCore.stream().filter(l -> {
            BigDecimal nav = NavResolver.requireValidNav(navMap, l, "RebalanceWaterfallEngine.selectCoreLots");
            if (nav.subtract(l.effectiveCostPerUnit(nav)).compareTo(BigDecimal.ZERO) < 0) return false;
            AssetCategory cat = TaxClassifier.detectCategory(l.assetId(), l.assetName());
            long threshold = getThresholdDays(cat, rules);
            long holdingDays = ChronoUnit.DAYS.between(l.acquisitionDate(), today);
            boolean isLtcg = threshold > 0 && holdingDays >= threshold;
            return requireLtcg ? isLtcg : !isLtcg;
        }).sorted(Comparator.comparing(l -> {
            BigDecimal nav = NavResolver.requireValidNav(navMap, l, "RebalanceWaterfallEngine.selectCoreLots");
            return nav.subtract(l.effectiveCostPerUnit(nav));
        })).toList();
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
