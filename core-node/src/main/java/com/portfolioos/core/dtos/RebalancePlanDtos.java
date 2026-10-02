package com.portfolioos.core.dtos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RebalancePlanDtos {

    public record GoldSilverContextDto(
        double goldSilverRatio,
        String signal,
        double goldTargetSplitPct,
        double silverTargetSplitPct,
        boolean isEstimated,
        String source,
        String asOfDate
    ) {
        public GoldSilverContextDto(
            double goldSilverRatio,
            String signal,
            double goldTargetSplitPct,
            double silverTargetSplitPct
        ) {
            this(
                goldSilverRatio,
                signal,
                goldTargetSplitPct,
                silverTargetSplitPct,
                true,
                "STATUTORY_BENCHMARK_ESTIMATE",
                null
            );
        }
    }

    public record ReconstitutionContextDto(
        String nextReconstitutionDate,
        long daysToReconstitution,
        boolean isWindowActive,
        String executionRecommendation
    ) {}

    public record BeerSpreadContextDto(
        double gsec10yYieldPct,
        double nifty50Pe,
        double nifty50EarningsYieldPct,
        double beerSpreadPct,
        String valuationZone,
        String asOfDate,
        boolean isFallback,
        String sourceStatus
    ) {
        public BeerSpreadContextDto(
            double gsec10yYieldPct,
            double nifty50Pe,
            double nifty50EarningsYieldPct,
            double beerSpreadPct,
            String valuationZone,
            String asOfDate
        ) {
            this(
                gsec10yYieldPct,
                nifty50Pe,
                nifty50EarningsYieldPct,
                beerSpreadPct,
                valuationZone,
                asOfDate,
                asOfDate == null || asOfDate.isBlank(),
                (asOfDate == null || asOfDate.isBlank()) ? "FALLBACK_CACHED" : "LIVE_FETCH"
            );
        }
    }

    public record RebalancePlanDto(
        String planId,
        String generatedAt,
        RebalanceTriggerDto trigger,
        SellSidePlanDto sellSide,
        BuySidePlanDto buySide,
        ReasoningNarrativeDto reasoningNarrative,
        ManualLumpsumMetaDto manualLumpsumMeta,
        GoldSilverContextDto goldSilverContext,
        ReconstitutionContextDto reconstitutionContext,
        BeerSpreadContextDto beerSpreadContext
    ) {
        public RebalancePlanDto(
            String planId,
            String generatedAt,
            RebalanceTriggerDto trigger,
            SellSidePlanDto sellSide,
            BuySidePlanDto buySide,
            ReasoningNarrativeDto reasoningNarrative,
            ManualLumpsumMetaDto manualLumpsumMeta
        ) {
            this(
                planId,
                generatedAt,
                trigger,
                sellSide,
                buySide,
                reasoningNarrative,
                manualLumpsumMeta,
                null,
                null,
                null
            );
        }
    }

    public record RebalanceTriggerDto(
        String type, // DRAWDOWN, DRIFT, SCHEDULED, GOLD_FLOOR_BACKSTOP, MANUAL_LUMPSUM
        String legacyTriggerType, // INDUCED (for DRAWDOWN/DRIFT), SCHEDULED, MANUAL_LUMPSUM
        String reasonCode,
        String reasonLabel,
        String scheduledWindowLabel,
        DrawdownContextDto drawdownContext
    ) {
        public RebalanceTriggerDto(
            String type,
            String reasonCode,
            String reasonLabel,
            String scheduledWindowLabel,
            DrawdownContextDto drawdownContext
        ) {
            this(
                type,
                ("DRAWDOWN".equals(type) || "DRIFT".equals(type)) ? "INDUCED" : type,
                reasonCode,
                reasonLabel,
                scheduledWindowLabel,
                drawdownContext
            );
        }

        public boolean isInduced() {
            return "INDUCED".equals(legacyTriggerType);
        }
    }

    public record DrawdownContextDto(
        double currentDrawdownPct,
        BigDecimal rollingHighValue,
        String rollingHighDate,
        BigDecimal currentValue,
        String armedTier,
        String nextTier,
        double nextTierDistancePct
    ) {}

    public record SellSidePlanDto(
        BigDecimal totalRequired,
        List<WaterfallTierDto> waterfall,
        TaxSummaryDto taxSummary,
        List<ConsolidatedSellOrderDto> orders
    ) {
        public SellSidePlanDto(BigDecimal totalRequired, List<WaterfallTierDto> waterfall, TaxSummaryDto taxSummary) {
            this(totalRequired, waterfall, taxSummary, buildConsolidatedOrders(waterfall));
        }

        public static List<ConsolidatedSellOrderDto> buildConsolidatedOrders(List<WaterfallTierDto> waterfall) {
            if (waterfall == null || waterfall.isEmpty()) {
                return List.of();
            }
            Map<String, List<RebalanceLotImpactDto>> grouped = new LinkedHashMap<>();
            for (WaterfallTierDto tier : waterfall) {
                if (tier.lots() != null) {
                    for (RebalanceLotImpactDto lot : tier.lots()) {
                        String key = lot.fundId() != null && !lot.fundId().isBlank() ? lot.fundId() : lot.fundName();
                        if (key == null) key = "UNKNOWN_HOLDING";
                        grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(lot);
                    }
                }
            }

            List<ConsolidatedSellOrderDto> result = new ArrayList<>();
            for (Map.Entry<String, List<RebalanceLotImpactDto>> entry : grouped.entrySet()) {
                List<RebalanceLotImpactDto> lotList = entry.getValue();
                if (lotList.isEmpty()) continue;
                RebalanceLotImpactDto first = lotList.get(0);
                String fundId = first.fundId();
                String fundName = first.fundName();

                BigDecimal totalProceeds = BigDecimal.ZERO;
                BigDecimal totalUnits = BigDecimal.ZERO;
                BigDecimal totalRealizedGain = BigDecimal.ZERO;
                BigDecimal totalExemption = BigDecimal.ZERO;
                BigDecimal totalTax = BigDecimal.ZERO;
                boolean allExempt = true;

                for (RebalanceLotImpactDto l : lotList) {
                    if (l.saleProceeds() != null) totalProceeds = totalProceeds.add(l.saleProceeds());
                    if (l.unitsSold() != null) totalUnits = totalUnits.add(l.unitsSold());
                    if (l.realizedGain() != null) totalRealizedGain = totalRealizedGain.add(l.realizedGain());
                    if (l.taxImpact() != null) {
                        if (l.taxImpact().exemptionApplied() != null) {
                            totalExemption = totalExemption.add(l.taxImpact().exemptionApplied());
                        }
                        if (l.taxImpact().taxAmount() != null) {
                            totalTax = totalTax.add(l.taxImpact().taxAmount());
                        }
                        if (!"SEC_112A_EXEMPT".equals(l.taxImpact().regime()) && (l.taxImpact().exemptionApplied() == null || l.taxImpact().exemptionApplied().compareTo(BigDecimal.ZERO) <= 0)) {
                            allExempt = false;
                        }
                    } else {
                        allExempt = false;
                    }
                }

                int count = lotList.size();
                String classification;
                String summaryLabel;
                if (totalExemption.compareTo(BigDecimal.ZERO) > 0) {
                    classification = "SEC_112A_EXEMPT";
                    BigDecimal taxSaved = totalExemption.multiply(new BigDecimal("0.125")).setScale(0, RoundingMode.HALF_UP);
                    summaryLabel = count + (count == 1 ? " Lot" : " Lots") + " · LTCG Exempt (Saved ₹ " + String.format("%,d", taxSaved.longValue()) + " Tax)";
                } else if (allExempt) {
                    classification = "SEC_112A_EXEMPT";
                    summaryLabel = count + (count == 1 ? " Lot" : " Lots") + " · Sec 112A Exempt";
                } else if (totalRealizedGain.compareTo(BigDecimal.ZERO) < 0) {
                    classification = "LOSS_HARVEST";
                    summaryLabel = count + (count == 1 ? " Lot" : " Lots") + " · Tax-Loss Harvest";
                } else {
                    classification = "TAXABLE_TRIM";
                    summaryLabel = count + (count == 1 ? " Lot" : " Lots") + " · Taxable Trim";
                }

                result.add(new ConsolidatedSellOrderDto(
                    fundId,
                    fundName,
                    totalProceeds,
                    totalUnits,
                    totalRealizedGain,
                    totalExemption,
                    totalTax,
                    count,
                    classification,
                    summaryLabel,
                    lotList
                ));
            }
            return result;
        }
    }

    public record ConsolidatedSellOrderDto(
        String fundId,
        String fundName,
        BigDecimal totalProceeds,
        BigDecimal totalUnits,
        BigDecimal totalRealizedGain,
        BigDecimal totalExemptionApplied,
        BigDecimal totalTaxAmount,
        int lotCount,
        String taxClassification,
        String summaryLabel,
        double currentPct,
        double postPct,
        BigDecimal preValuation,
        BigDecimal postValuation,
        List<RebalanceLotImpactDto> lots
    ) {
        public ConsolidatedSellOrderDto(
            String fundId,
            String fundName,
            BigDecimal totalProceeds,
            BigDecimal totalUnits,
            BigDecimal totalRealizedGain,
            BigDecimal totalExemptionApplied,
            BigDecimal totalTaxAmount,
            int lotCount,
            String taxClassification,
            String summaryLabel,
            List<RebalanceLotImpactDto> lots
        ) {
            this(fundId, fundName, totalProceeds, totalUnits, totalRealizedGain, totalExemptionApplied, totalTaxAmount, lotCount, taxClassification, summaryLabel, 0.0, 0.0, BigDecimal.ZERO, BigDecimal.ZERO, lots);
        }
    }

    public record WaterfallTierDto(
        String tier,
        String tierLabel,
        BigDecimal available,
        BigDecimal sold,
        String skippedReason, // FULLY_DEPLOYED, NOT_APPLICABLE, INSUFFICIENT, null
        List<RebalanceLotImpactDto> lots
    ) {}

    public record RebalanceLotImpactDto(
        String lotId,
        String fundId,
        String fundName,
        String acquisitionDate,
        long holdingDays,
        BigDecimal unitsSold,
        BigDecimal costBasis,
        BigDecimal saleProceeds,
        BigDecimal realizedGain,
        String taxTerm,
        LotTaxImpactDto taxImpact
    ) {}

    public record LotTaxImpactDto(
        String regime, // SEC_112A_EXEMPT, SEC_112A_TAXABLE_12_5, SLAB_RATE_STCG
        BigDecimal exemptionApplied,
        BigDecimal taxableAmount,
        BigDecimal taxAmount
    ) {}

    public record TaxSummaryDto(
        BigDecimal totalRealizedGain,
        BigDecimal totalLtcgExempt,
        BigDecimal totalStcgTaxable,
        BigDecimal totalTaxEstimate,
        BigDecimal exemptionHeadroomBefore,
        BigDecimal exemptionHeadroomAfter,
        BigDecimal statutoryExemptionCap,
        double exemptionUtilizedPct
    ) {
        public TaxSummaryDto(
            BigDecimal totalRealizedGain,
            BigDecimal totalLtcgExempt,
            BigDecimal totalStcgTaxable,
            BigDecimal totalTaxEstimate,
            BigDecimal exemptionHeadroomBefore,
            BigDecimal exemptionHeadroomAfter
        ) {
            this(
                totalRealizedGain,
                totalLtcgExempt,
                totalStcgTaxable,
                totalTaxEstimate,
                exemptionHeadroomBefore,
                exemptionHeadroomAfter,
                BigDecimal.valueOf(125000.00),
                (exemptionHeadroomBefore != null && exemptionHeadroomBefore.compareTo(BigDecimal.ZERO) > 0 && totalLtcgExempt != null)
                    ? totalLtcgExempt.divide(BigDecimal.valueOf(125000.00), 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100.0
                    : 0.0
            );
        }
    }

    public record BuySidePlanDto(
        BigDecimal totalToInvest,
        boolean isManualLumpsum,
        List<RebalanceBucketAllocationDto> buckets
    ) {}

    public record RebalanceBucketAllocationDto(
        String bucket,
        double targetPct,
        double currentPct,
        double postRebalancePct,
        BigDecimal amountAllocated,
        List<FundAllocationDto> fundBreakdown
    ) {}

    public record FundAllocationDto(
        String fundId,
        String fundName,
        BigDecimal amount,
        double currentPct,
        double postPct,
        BigDecimal preValuation,
        BigDecimal postValuation
    ) {
        public FundAllocationDto(String fundId, String fundName, BigDecimal amount) {
            this(fundId, fundName, amount, 0.0, 0.0, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public record ReasoningNarrativeDto(
        String headline,
        List<String> paragraphs,
        String generatedFromTemplateVersion
    ) {}

    public record ManualLumpsumMetaDto(
        BigDecimal enteredAmount,
        String enteredDate,
        String driftContextNote,
        Boolean includeRebalance
    ) {
        public ManualLumpsumMetaDto(BigDecimal enteredAmount, String enteredDate, String driftContextNote) {
            this(enteredAmount, enteredDate, driftContextNote, false);
        }
    }
}
