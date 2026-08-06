package com.portfolioos.core.valuation;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
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

public class RebalanceEngine {

    public record RebalanceLotSelection(
        String lotId,
        String assetId,
        String assetName,
        BigDecimal unitsToSell,
        BigDecimal redemptionProceeds,
        BigDecimal costBasis,
        BigDecimal estimatedGain,
        String taxTerm,
        BigDecimal estimatedTaxDrag
    ) {}

    public record RebalancePreviewResult(
        BigDecimal targetRedemptionAmount,
        BigDecimal actualRedemptionAmount,
        BigDecimal deferredAmount,
        BigDecimal totalEstimatedGain,
        BigDecimal totalTaxDrag,
        BigDecimal effectiveTaxRatePct,
        BigDecimal ltcgExemptionHarvested,
        List<RebalanceLotSelection> selectedLots
    ) {}

    public static RebalancePreviewResult calculateRebalancePreview(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal targetAmount,
        BigDecimal remainingExemption,
        String fiscalYear
    ) {
        return calculateRebalancePreview(openLots, navMap, targetAmount, remainingExemption, fiscalYear, true);
    }

    public static RebalancePreviewResult calculateRebalancePreview(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal targetAmount,
        BigDecimal remainingExemption,
        String fiscalYear,
        boolean allowStcg
    ) {
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
        BigDecimal remainingTarget = targetAmount;
        BigDecimal unusedExemption = remainingExemption;
        BigDecimal totalGain = BigDecimal.ZERO;
        BigDecimal totalTaxDrag = BigDecimal.ZERO;
        BigDecimal actualRedemption = BigDecimal.ZERO;

        List<RebalanceLotSelection> selected = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // Sort: loss-making first (0), then long-term (1), then short-term (2)
        List<Lot> candidateLots = new ArrayList<>(openLots);

        if (!allowStcg) {
            // Drop positive-gain short-term lots entirely
            candidateLots = candidateLots.stream().filter(l -> {
                BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
                BigDecimal gain = nav.subtract(l.costPerUnit());
                if (gain.compareTo(BigDecimal.ZERO) < 0) return true; // Keep loss-making lots
                AssetCategory cat = TaxClassifier.detectCategory(l.assetId(), l.assetName());
                long threshold = getThresholdDays(cat, rules);
                long holdingDays = ChronoUnit.DAYS.between(l.acquisitionDate(), today);
                return threshold > 0 && holdingDays >= threshold; // Keep LTCG lots
            }).toList();
        }

        List<Lot> sortedLots = new ArrayList<>(candidateLots);
        sortedLots.sort((l1, l2) -> {
            BigDecimal nav1 = navMap.getOrDefault(l1.assetId(), l1.costPerUnit());
            BigDecimal gainPerUnit1 = nav1.subtract(l1.costPerUnit());
            AssetCategory cat1 = TaxClassifier.detectCategory(l1.assetId(), l1.assetName());
            long holdingDays1 = ChronoUnit.DAYS.between(l1.acquisitionDate(), today);
            long thresholdDays1 = getThresholdDays(cat1, rules);
            boolean isLtcg1 = thresholdDays1 > 0 && holdingDays1 >= thresholdDays1;

            int rank1 = (gainPerUnit1.compareTo(BigDecimal.ZERO) < 0) ? 0 : (isLtcg1 ? 1 : 2);

            BigDecimal nav2 = navMap.getOrDefault(l2.assetId(), l2.costPerUnit());
            BigDecimal gainPerUnit2 = nav2.subtract(l2.costPerUnit());
            AssetCategory cat2 = TaxClassifier.detectCategory(l2.assetId(), l2.assetName());
            long holdingDays2 = ChronoUnit.DAYS.between(l2.acquisitionDate(), today);
            long thresholdDays2 = getThresholdDays(cat2, rules);
            boolean isLtcg2 = thresholdDays2 > 0 && holdingDays2 >= thresholdDays2;

            int rank2 = (gainPerUnit2.compareTo(BigDecimal.ZERO) < 0) ? 0 : (isLtcg2 ? 1 : 2);

            return Integer.compare(rank1, rank2);
        });

        for (Lot lot : sortedLots) {
            if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
            BigDecimal lotValue = lot.remainingUnits().multiply(nav);
            BigDecimal redemptionFromLot = lotValue.min(remainingTarget);

            BigDecimal unitsToSell = BigDecimal.ZERO;
            if (nav.compareTo(BigDecimal.ZERO) > 0) {
                unitsToSell = redemptionFromLot.divide(nav, 4, RoundingMode.HALF_UP);
            }
            BigDecimal costBasisSlice = unitsToSell.multiply(lot.costPerUnit());
            BigDecimal gainSlice = redemptionFromLot.subtract(costBasisSlice);

            AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
            long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
            long thresholdDays = getThresholdDays(category, rules);
            boolean isLtcg = thresholdDays > 0 && holdingDays >= thresholdDays;

            BigDecimal taxDrag = BigDecimal.ZERO;
            if (gainSlice.compareTo(BigDecimal.ZERO) > 0) {
                if (isLtcg) {
                    BigDecimal exemptPortion = gainSlice.min(unusedExemption);
                    BigDecimal taxableGain = gainSlice.subtract(exemptPortion);
                    unusedExemption = unusedExemption.subtract(exemptPortion).max(BigDecimal.ZERO);
                    taxDrag = taxableGain.multiply(rules.equityLtcgRate());
                } else {
                    BigDecimal stcgRate = (category == AssetCategory.EQUITY) ? rules.equityStcgRate() : new BigDecimal("0.20");
                    taxDrag = gainSlice.multiply(stcgRate);
                }
            }

            selected.add(new RebalanceLotSelection(
                lot.lotId(),
                lot.assetId(),
                lot.assetName(),
                unitsToSell,
                redemptionFromLot,
                costBasisSlice,
                gainSlice,
                isLtcg ? "LONG_TERM" : "SHORT_TERM",
                taxDrag
            ));

            actualRedemption = actualRedemption.add(redemptionFromLot);
            totalGain = totalGain.add(gainSlice);
            totalTaxDrag = totalTaxDrag.add(taxDrag);
            remainingTarget = remainingTarget.subtract(redemptionFromLot);
        }

        BigDecimal ltcgHarvested = remainingExemption.subtract(unusedExemption);
        BigDecimal effTaxRate = BigDecimal.ZERO;
        if (actualRedemption.compareTo(BigDecimal.ZERO) > 0) {
            effTaxRate = totalTaxDrag.multiply(new BigDecimal("100")).divide(actualRedemption, 2, RoundingMode.HALF_UP);
        }

        BigDecimal deferredAmount = targetAmount.subtract(actualRedemption).max(BigDecimal.ZERO);

        return new RebalancePreviewResult(
            targetAmount,
            actualRedemption,
            deferredAmount,
            totalGain,
            totalTaxDrag,
            effTaxRate,
            ltcgHarvested,
            selected
        );
    }

    private static long getThresholdDays(AssetCategory category, TaxRulesConfig rules) {
        return switch (category) {
            case EQUITY -> rules.equityLtcgThresholdDays();
            case GOLD_SILVER, INTERNATIONAL, SGB -> rules.goldInternationalThresholdDays();
            case DEBT_SPECIFIED_50AA -> -1L;
        };
    }
}
