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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsolidationRebalanceEngine {

    public record ExistingSipAllocation(
        String assetId,
        String assetName,
        BigDecimal sipWeightPct,
        BigDecimal deploymentAmount
    ) {}

    public record PhasedOutAssetSummary(
        String assetId,
        String assetName,
        BigDecimal currentUnits,
        BigDecimal currentValue,
        BigDecimal totalCostBasis,
        BigDecimal unrealizedGain,
        boolean isLtcg,
        BigDecimal estimatedTaxDrag
    ) {}

    public record ConsolidationPreviewResult(
        List<PhasedOutAssetSummary> phasedOutAssets,
        BigDecimal totalProceeds,
        BigDecimal totalEstimatedGain,
        BigDecimal totalTaxDrag,
        BigDecimal ltcgExemptionHarvested,
        List<ExistingSipAllocation> proRataAllocations,
        boolean isRebalanceWindowOpen,
        String nextScheduledWindow
    ) {}



    public static ConsolidationPreviewResult calculateConsolidation(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal remainingExemption,
        String fiscalYear
    ) {
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);

        java.util.Set<String> activeAssetIds = com.portfolioos.core.matcher.FundTierClassifier.findActiveAssetIds(openLots, currentDate);
        List<Lot> phaseOutLots = openLots.stream().filter(lot ->
            com.portfolioos.core.matcher.FundTierClassifier.isLegacyFund(lot.assetId(), activeAssetIds)
        ).toList();

        BigDecimal totalProceeds = BigDecimal.ZERO;
        BigDecimal totalGain = BigDecimal.ZERO;
        BigDecimal totalTaxDrag = BigDecimal.ZERO;
        BigDecimal unusedExemption = remainingExemption;

        List<PhasedOutAssetSummary> phasedSummaries = new ArrayList<>();

        Map<String, List<Lot>> grouped = new HashMap<>();
        for (Lot lot : phaseOutLots) {
            grouped.computeIfAbsent(lot.assetId(), k -> new ArrayList<>()).add(lot);
        }

        for (Map.Entry<String, List<Lot>> entry : grouped.entrySet()) {
            String assetId = entry.getKey();
            List<Lot> lots = new ArrayList<>(entry.getValue());
            lots.sort(Comparator.comparing(l -> l.acquisitionDate() != null ? l.acquisitionDate() : currentDate));

            String assetName = lots.get(0).assetName();
            BigDecimal nav = NavResolver.requireValidNav(navMap, assetId, assetName, "ConsolidationRebalanceEngine");
            AssetCategory category = TaxClassifier.detectCategory(assetId, assetName);

            BigDecimal fundUnits = BigDecimal.ZERO;
            BigDecimal fundCost = BigDecimal.ZERO;
            BigDecimal fundCurVal = BigDecimal.ZERO;
            BigDecimal fundGain = BigDecimal.ZERO;
            BigDecimal fundTaxDrag = BigDecimal.ZERO;
            boolean hasLtcgLot = false;

            for (Lot lot : lots) {
                BigDecimal units = lot.remainingUnits();
                BigDecimal cost = lot.totalCostBasis();
                BigDecimal lotVal = units.multiply(nav);
                BigDecimal lotGain = lotVal.subtract(cost);

                long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate() != null ? lot.acquisitionDate() : currentDate, currentDate);
                com.portfolioos.core.model.TaxTerm term = TaxClassifier.classifyTaxTerm(
                    category,
                    holdingDays,
                    fiscalYear,
                    TaxClassifier.isListed(assetId, assetName),
                    lot.acquisitionDate(),
                    currentDate
                );
                boolean lotIsLtcg = (term == com.portfolioos.core.model.TaxTerm.LONG_TERM);
                if (lotIsLtcg) {
                    hasLtcgLot = true;
                }

                BigDecimal lotTax = BigDecimal.ZERO;
                if (lotGain.compareTo(BigDecimal.ZERO) > 0) {
                    if (category == AssetCategory.EQUITY && lotIsLtcg) {
                        BigDecimal exemptPortion = lotGain.min(unusedExemption);
                        BigDecimal taxableGain = lotGain.subtract(exemptPortion);
                        unusedExemption = unusedExemption.subtract(exemptPortion).max(BigDecimal.ZERO);
                        lotTax = taxableGain.multiply(rules.equityLtcgRate());
                    } else {
                        BigDecimal rate = TaxClassifier.resolveTaxRate(category, term, rules);
                        lotTax = lotGain.multiply(rate);
                    }
                }

                fundUnits = fundUnits.add(units);
                fundCost = fundCost.add(cost);
                fundCurVal = fundCurVal.add(lotVal);
                fundGain = fundGain.add(lotGain);
                fundTaxDrag = fundTaxDrag.add(lotTax);
            }

            totalProceeds = totalProceeds.add(fundCurVal);
            totalGain = totalGain.add(fundGain);
            totalTaxDrag = totalTaxDrag.add(fundTaxDrag);

            phasedSummaries.add(new PhasedOutAssetSummary(
                assetId, assetName, fundUnits, fundCurVal, fundCost, fundGain, hasLtcgLot, fundTaxDrag
            ));
        }

        BigDecimal netPostTaxProceeds = totalProceeds.subtract(totalTaxDrag).max(BigDecimal.ZERO);
        BigDecimal effectiveProceeds = netPostTaxProceeds.compareTo(BigDecimal.ZERO) > 0 ? netPostTaxProceeds : totalProceeds;

        List<ExistingSipAllocation> proRataAllocations = new ArrayList<>();
        Map<String, Double> sipAllocMap = com.portfolioos.core.rules.BucketConfigLoader.getRenormalizedSipAllocations(currentDate);

        for (Map.Entry<String, Double> fundEntry : sipAllocMap.entrySet()) {
            String fundId = fundEntry.getKey();
            double sipWeightFrac = fundEntry.getValue();
            BigDecimal weightPct = BigDecimal.valueOf(sipWeightFrac * 100.0).setScale(2, RoundingMode.HALF_UP);
            BigDecimal deployAmt = effectiveProceeds.multiply(BigDecimal.valueOf(sipWeightFrac)).setScale(2, RoundingMode.HALF_UP);

            proRataAllocations.add(new ExistingSipAllocation(
                fundId,
                fundId,
                weightPct,
                deployAmt
            ));
        }

        int month = currentDate.getMonthValue();
        boolean isWindowOpen = month == 3 || month == 9;
        String nextScheduled = (month <= 3) ? "March 31, " + currentDate.getYear() 
            : (month <= 9) ? "September 30, " + currentDate.getYear() 
            : "March 31, " + (currentDate.getYear() + 1);

        BigDecimal ltcgHarvested = remainingExemption.subtract(unusedExemption);

        return new ConsolidationPreviewResult(
            phasedSummaries,
            effectiveProceeds,
            totalGain,
            totalTaxDrag,
            ltcgHarvested,
            proRataAllocations,
            isWindowOpen,
            nextScheduled
        );
    }


}
