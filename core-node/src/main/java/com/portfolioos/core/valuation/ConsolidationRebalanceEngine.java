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

    private static final Map<String, Pair<String, BigDecimal>> CORE_SIP_WEIGHTS = new HashMap<>();

    static {
        CORE_SIP_WEIGHTS.put("NIFTY_LARGEMIDCAP_250", new Pair<>("Nifty LargeMidcap 250 Index Fund", new BigDecimal("33.0")));
        CORE_SIP_WEIGHTS.put("PARAG_PARIKH_FLEXI", new Pair<>("Parag Parikh Flexi Cap Fund", new BigDecimal("24.0")));
        CORE_SIP_WEIGHTS.put("ARBITRAGE_LIQUID", new Pair<>("Kotak Equity Arbitrage / Liquid Buffer", new BigDecimal("16.0")));
        CORE_SIP_WEIGHTS.put("NIFTY_VALUE_30", new Pair<>("Nifty200 Value 30 Index Fund", new BigDecimal("11.0")));
        CORE_SIP_WEIGHTS.put("NIFTY_MOMENTUM_50", new Pair<>("Nifty200 Momentum Quality 50 Index Fund", new BigDecimal("9.0")));
        CORE_SIP_WEIGHTS.put("NIFTY_SMALLCAP_250", new Pair<>("Nifty Smallcap 250 Index Fund", new BigDecimal("7.0")));
    }

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
            List<Lot> lots = entry.getValue();

            String assetName = lots.get(0).assetName();
            BigDecimal totalUnits = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;
            LocalDate oldestAcq = null;

            for (Lot lot : lots) {
                totalUnits = totalUnits.add(lot.remainingUnits());
                totalCost = totalCost.add(lot.totalCostBasis());
                if (oldestAcq == null || lot.acquisitionDate().isBefore(oldestAcq)) {
                    oldestAcq = lot.acquisitionDate();
                }
            }

            BigDecimal nav = navMap.getOrDefault(assetId, BigDecimal.ZERO);
            if (nav.compareTo(BigDecimal.ZERO) == 0 && totalUnits.compareTo(BigDecimal.ZERO) > 0) {
                nav = totalCost.divide(totalUnits, 4, RoundingMode.HALF_UP);
            }

            BigDecimal curVal = totalUnits.multiply(nav);
            BigDecimal gain = curVal.subtract(totalCost);

            AssetCategory category = TaxClassifier.detectCategory(assetId, assetName);
            long holdingDays = ChronoUnit.DAYS.between(oldestAcq != null ? oldestAcq : currentDate, currentDate);
            
            long thresholdDays = switch (category) {
                case EQUITY -> rules.equityLtcgThresholdDays();
                case GOLD_SILVER, INTERNATIONAL, SGB -> rules.goldInternationalThresholdDays();
                case DEBT_SPECIFIED_50AA -> -1L;
            };

            boolean isLtcg = thresholdDays > 0 && holdingDays >= thresholdDays;

            BigDecimal taxDrag = BigDecimal.ZERO;
            if (gain.compareTo(BigDecimal.ZERO) > 0) {
                if (isLtcg) {
                    BigDecimal exemptPortion = gain.min(unusedExemption);
                    BigDecimal taxableGain = gain.subtract(exemptPortion);
                    unusedExemption = unusedExemption.subtract(exemptPortion).max(BigDecimal.ZERO);
                    taxDrag = taxableGain.multiply(rules.equityLtcgRate());
                } else {
                    taxDrag = gain.multiply(rules.equityStcgRate());
                }
            }

            totalProceeds = totalProceeds.add(curVal);
            totalGain = totalGain.add(gain);
            totalTaxDrag = totalTaxDrag.add(taxDrag);

            phasedSummaries.add(new PhasedOutAssetSummary(
                assetId, assetName, totalUnits, curVal, totalCost, gain, isLtcg, taxDrag
            ));
        }

        BigDecimal netPostTaxProceeds = totalProceeds.subtract(totalTaxDrag).max(BigDecimal.ZERO);
        BigDecimal effectiveProceeds = netPostTaxProceeds.compareTo(BigDecimal.ZERO) > 0 ? netPostTaxProceeds : totalProceeds;

        List<ExistingSipAllocation> proRataAllocations = new ArrayList<>();
        for (Map.Entry<String, Pair<String, BigDecimal>> entry : CORE_SIP_WEIGHTS.entrySet()) {
            String id = entry.getKey();
            Pair<String, BigDecimal> pair = entry.getValue();
            BigDecimal weightPct = pair.second();
            BigDecimal deployAmt = effectiveProceeds.multiply(weightPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            proRataAllocations.add(new ExistingSipAllocation(
                id, pair.first(), weightPct, deployAmt
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

    private static class Pair<A, B> {
        private final A first;
        private final B second;
        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
        public A first() { return first; }
        public B second() { return second; }
    }
}
