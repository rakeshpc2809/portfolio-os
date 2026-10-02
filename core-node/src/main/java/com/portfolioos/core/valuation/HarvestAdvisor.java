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

public class HarvestAdvisor {

    public record TaxHarvestRecommendation(
        String assetId,
        String assetName,
        String lotId,
        BigDecimal unitsToHarvest,
        BigDecimal redemptionProceeds,
        BigDecimal unrealizedLtcgGain,
        BigDecimal exemptionHeadroomConsumed,
        String recommendationText
    ) {}

    public record TaxHarvestResult(
        String fiscalYear,
        BigDecimal exemptionLimit,
        BigDecimal exemptionUsedSoFar,
        BigDecimal exemptionRemaining,
        BigDecimal totalUnrealizedLtcgAvailable,
        BigDecimal harvestableLtcgGain,
        List<TaxHarvestRecommendation> recommendations
    ) {}

    public static TaxHarvestResult generateHarvestPlan(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal exemptionUsedThisFy,
        String fiscalYear
    ) {
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
        BigDecimal limit = rules.equityExemptionLimit();
        BigDecimal remainingExemption = limit.subtract(exemptionUsedThisFy).max(BigDecimal.ZERO);

        LocalDate today = LocalDate.now();
        List<LotWithGain> ltcgLots = new ArrayList<>();
        BigDecimal totalUnrealizedLtcg = BigDecimal.ZERO;

        for (Lot lot : openLots) {
            AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
            if (category != AssetCategory.EQUITY) continue;

            long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
            if (holdingDays >= rules.equityLtcgThresholdDays()) {
                BigDecimal nav = NavResolver.requireValidNav(navMap, lot, "HarvestAdvisor");
                BigDecimal currentVal = lot.remainingUnits().multiply(nav);
                BigDecimal gain = currentVal.subtract(lot.totalCostBasis());

                if (gain.compareTo(BigDecimal.ZERO) > 0) {
                    totalUnrealizedLtcg = totalUnrealizedLtcg.add(gain);
                    ltcgLots.add(new LotWithGain(lot, nav, gain));
                }
            }
        }

        // Sort lots by gain descending to maximize headroom utilization
        ltcgLots.sort(Comparator.comparing(LotWithGain::gain).reversed());

        BigDecimal headroomLeft = remainingExemption;
        BigDecimal totalHarvestedGain = BigDecimal.ZERO;
        List<TaxHarvestRecommendation> recommendations = new ArrayList<>();

        for (LotWithGain entry : ltcgLots) {
            if (headroomLeft.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal harvestableGain = entry.gain().min(headroomLeft);
            BigDecimal proportionToSell = BigDecimal.ONE;
            if (entry.gain().compareTo(BigDecimal.ZERO) > 0) {
                proportionToSell = harvestableGain.divide(entry.gain(), 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
            }

            BigDecimal unitsToSell = entry.lot().remainingUnits().multiply(proportionToSell).setScale(4, RoundingMode.HALF_UP);
            BigDecimal proceeds = unitsToSell.multiply(entry.nav()).setScale(2, RoundingMode.HALF_UP);

            headroomLeft = headroomLeft.subtract(harvestableGain).max(BigDecimal.ZERO);
            totalHarvestedGain = totalHarvestedGain.add(harvestableGain);

            String text = "Sell " + unitsToSell + " units of " + entry.lot().assetName() + 
                         " to harvest ₹" + harvestableGain.setScale(0, RoundingMode.HALF_UP) + " tax-free LTCG gain, then same-day rebuy.";

            recommendations.add(new TaxHarvestRecommendation(
                entry.lot().assetId(),
                entry.lot().assetName(),
                entry.lot().lotId(),
                unitsToSell,
                proceeds,
                harvestableGain.setScale(2, RoundingMode.HALF_UP),
                harvestableGain.setScale(2, RoundingMode.HALF_UP),
                text
            ));
        }

        return new TaxHarvestResult(
            fiscalYear,
            limit,
            exemptionUsedThisFy,
            remainingExemption,
            totalUnrealizedLtcg.setScale(2, RoundingMode.HALF_UP),
            totalHarvestedGain.setScale(2, RoundingMode.HALF_UP),
            recommendations
        );
    }

    private record LotWithGain(Lot lot, BigDecimal nav, BigDecimal gain) {}
}
