package com.portfolioos.core.dtos;

import java.math.BigDecimal;
import java.util.List;

public class RebalancePlanDtos {

    public record RebalancePlanDto(
        String planId,
        String generatedAt,
        RebalanceTriggerDto trigger,
        SellSidePlanDto sellSide,
        BuySidePlanDto buySide,
        ReasoningNarrativeDto reasoningNarrative,
        ManualLumpsumMetaDto manualLumpsumMeta
    ) {}

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
        TaxSummaryDto taxSummary
    ) {}

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
        BigDecimal exemptionHeadroomAfter
    ) {}

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
        BigDecimal amount
    ) {}

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
