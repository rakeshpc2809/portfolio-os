package com.portfolioos.core.valuation;

import com.portfolioos.core.matcher.FundTierClassifier;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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

        // Partition lots into legacy vs core (dynamic check: no purchases/SIP in last 3 months)
        java.util.Set<String> activeAssetIds = FundTierClassifier.findActiveAssetIds(openLots, today);
        List<Lot> legacyLots = new ArrayList<>();
        List<Lot> coreLots = new ArrayList<>();

        for (Lot lot : openLots) {
            if (FundTierClassifier.isLegacyFund(lot.assetId(), activeAssetIds)) {
                legacyLots.add(lot);
            } else {
                coreLots.add(lot);
            }
        }

        // TIER 1: Legacy Funds (Cleanup phase - trim first, lowest tax cost first)
        if (remainingTarget.compareTo(BigDecimal.ZERO) > 0 && !legacyLots.isEmpty()) {
            sortLotsByTaxCost(legacyLots, navMap, today, rules);
            for (Lot lot : legacyLots) {
                if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;
                LotProcessResult res = processLot(WaterfallTier.LEGACY_FUND, lot, navMap, remainingTarget, unusedExemption, rules, today);
                if (res != null) {
                    steps.add(res.step());
                    satisfiedAmount = satisfiedAmount.add(res.proceeds());
                    remainingTarget = remainingTarget.subtract(res.proceeds());
                    unusedExemption = res.newUnusedExemption();
                    totalTaxDrag = totalTaxDrag.add(res.taxDrag());
                }
            }
        }

        // TIER 2: Core Fund Loss-Harvesting Lots (Gain < 0)
        if (remainingTarget.compareTo(BigDecimal.ZERO) > 0 && !coreLots.isEmpty()) {
            List<Lot> lossLots = coreLots.stream().filter(l -> {
                BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
                return nav.subtract(l.costPerUnit()).compareTo(BigDecimal.ZERO) < 0;
            }).sorted(Comparator.comparing(l -> {
                BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
                return nav.subtract(l.costPerUnit());
            })).toList();

            for (Lot lot : lossLots) {
                if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;
                LotProcessResult res = processLot(WaterfallTier.LOSS_HARVEST, lot, navMap, remainingTarget, unusedExemption, rules, today);
                if (res != null) {
                    steps.add(res.step());
                    satisfiedAmount = satisfiedAmount.add(res.proceeds());
                    remainingTarget = remainingTarget.subtract(res.proceeds());
                    unusedExemption = res.newUnusedExemption();
                    totalTaxDrag = totalTaxDrag.add(res.taxDrag());
                }
            }
        }

        // TIER 3 & 4: Core Fund LTCG Lots (Gain >= 0, Holding Days >= Threshold)
        if (remainingTarget.compareTo(BigDecimal.ZERO) > 0 && !coreLots.isEmpty()) {
            List<Lot> ltcgGainLots = coreLots.stream().filter(l -> {
                BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
                if (nav.subtract(l.costPerUnit()).compareTo(BigDecimal.ZERO) < 0) return false;
                AssetCategory cat = TaxClassifier.detectCategory(l.assetId(), l.assetName());
                long threshold = getThresholdDays(cat, rules);
                long holdingDays = ChronoUnit.DAYS.between(l.acquisitionDate(), today);
                return threshold > 0 && holdingDays >= threshold;
            }).sorted(Comparator.comparing(l -> {
                BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
                return nav.subtract(l.costPerUnit());
            })).toList();

            for (Lot lot : ltcgGainLots) {
                if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
                BigDecimal lotValue = lot.remainingUnits().multiply(nav);
                BigDecimal redemption = lotValue.min(remainingTarget);
                BigDecimal unitsSold = nav.compareTo(BigDecimal.ZERO) > 0 ? redemption.divide(nav, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal costBasis = unitsSold.multiply(lot.costPerUnit());
                BigDecimal gain = redemption.subtract(costBasis);

                if (gain.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal exemptPortion = gain.min(unusedExemption);
                    BigDecimal taxableGain = gain.subtract(exemptPortion);

                    if (exemptPortion.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal exemptUnits = unitsSold.multiply(exemptPortion).divide(gain, 4, RoundingMode.HALF_UP);
                        BigDecimal exemptProceeds = exemptUnits.multiply(nav);
                        unusedExemption = unusedExemption.subtract(exemptPortion).max(BigDecimal.ZERO);

                        steps.add(new WaterfallStep(
                            WaterfallTier.LTCG_WITHIN_EXEMPTION,
                            lot.lotId(),
                            lot.assetId(),
                            lot.assetName(),
                            exemptUnits,
                            exemptProceeds,
                            exemptPortion,
                            "LONG_TERM",
                            BigDecimal.ZERO
                        ));
                    }

                    if (taxableGain.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal taxableUnits = unitsSold.subtract(unitsSold.multiply(exemptPortion).divide(gain, 4, RoundingMode.HALF_UP));
                        BigDecimal taxableProceeds = taxableUnits.multiply(nav);
                        BigDecimal taxDrag = taxableGain.multiply(rules.equityLtcgRate()).setScale(2, RoundingMode.HALF_UP);
                        totalTaxDrag = totalTaxDrag.add(taxDrag);

                        steps.add(new WaterfallStep(
                            WaterfallTier.LTCG_BEYOND_EXEMPTION,
                            lot.lotId(),
                            lot.assetId(),
                            lot.assetName(),
                            taxableUnits,
                            taxableProceeds,
                            taxableGain,
                            "LONG_TERM",
                            taxDrag
                        ));
                    }
                } else {
                    steps.add(new WaterfallStep(
                        WaterfallTier.LTCG_WITHIN_EXEMPTION,
                        lot.lotId(),
                        lot.assetId(),
                        lot.assetName(),
                        unitsSold,
                        redemption,
                        gain,
                        "LONG_TERM",
                        BigDecimal.ZERO
                    ));
                }

                satisfiedAmount = satisfiedAmount.add(redemption);
                remainingTarget = remainingTarget.subtract(redemption);
            }
        }

        // TIER 5: Core Fund STCG Lots (Only if urgent = true)
        if (remainingTarget.compareTo(BigDecimal.ZERO) > 0 && urgent && !coreLots.isEmpty()) {
            List<Lot> stcgGainLots = coreLots.stream().filter(l -> {
                BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
                if (nav.subtract(l.costPerUnit()).compareTo(BigDecimal.ZERO) < 0) return false;
                AssetCategory cat = TaxClassifier.detectCategory(l.assetId(), l.assetName());
                long threshold = getThresholdDays(cat, rules);
                long holdingDays = ChronoUnit.DAYS.between(l.acquisitionDate(), today);
                return threshold <= 0 || holdingDays < threshold;
            }).sorted(Comparator.comparing(l -> {
                BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
                return nav.subtract(l.costPerUnit());
            })).toList();

            for (Lot lot : stcgGainLots) {
                if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;
                LotProcessResult res = processLot(WaterfallTier.STCG_URGENT_ONLY, lot, navMap, remainingTarget, unusedExemption, rules, today);
                if (res != null) {
                    steps.add(res.step());
                    satisfiedAmount = satisfiedAmount.add(res.proceeds());
                    remainingTarget = remainingTarget.subtract(res.proceeds());
                    unusedExemption = res.newUnusedExemption();
                    totalTaxDrag = totalTaxDrag.add(res.taxDrag());
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
        LocalDate today
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

            return Integer.compare(rank1, rank2);
        });
    }

    private static long getThresholdDays(AssetCategory category, TaxRulesConfig rules) {
        return switch (category) {
            case EQUITY -> rules.equityLtcgThresholdDays();
            case GOLD_SILVER, INTERNATIONAL, SGB -> rules.goldInternationalThresholdDays();
            case DEBT_SPECIFIED_50AA -> -1L;
        };
    }
}
