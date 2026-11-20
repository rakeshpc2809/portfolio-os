package com.portfolioos.core.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record BucketTargetsConfig(
    String version,
    GlobalInvariants globalInvariants,
    TaxParameters taxParameters,
    List<DrawdownRung> drawdownRungs,
    Map<BucketType, BucketSpec> buckets,
    List<String> legacyIsinBlacklist
) {
    public enum BucketType {
        EQUITY_CORE, EQUITY_SATELLITE, GOLD_SILVER, LIQUID_BUFFER, LEGACY_HOLDINGS
    }

    public record GlobalInvariants(
        int rebalanceCooldownDays,
        BigDecimal minTradeAmountInr,
        BigDecimal liquidHardCapPct,
        String drawdownAnchorMode,
        BigDecimal drawdownHoldZoneMinPct,
        BigDecimal drawdownHoldZoneMaxPct,
        BigDecimal urgentDrawdownThresholdPct,
        List<Integer> scheduledMonths,
        BigDecimal taxableTrimFraction
    ) {
        public GlobalInvariants(
            int rebalanceCooldownDays,
            BigDecimal minTradeAmountInr,
            BigDecimal liquidHardCapPct,
            String drawdownAnchorMode,
            BigDecimal drawdownHoldZoneMinPct,
            BigDecimal drawdownHoldZoneMaxPct,
            BigDecimal urgentDrawdownThresholdPct,
            List<Integer> scheduledMonths
        ) {
            this(rebalanceCooldownDays, minTradeAmountInr, liquidHardCapPct, drawdownAnchorMode,
                drawdownHoldZoneMinPct, drawdownHoldZoneMaxPct, urgentDrawdownThresholdPct,
                scheduledMonths, new BigDecimal("0.50"));
        }
    }

    public record TaxParameters(
        BigDecimal ltcgExemptionLimitInr,
        BigDecimal equityLtcgBaseRate,
        BigDecimal equityStcgBaseRate,
        BigDecimal fofLtcgBaseRate,
        BigDecimal debt50aaSlabBaseRate,
        BigDecimal healthEducationCessRate
    ) {
        public BigDecimal effectiveEquityLtcgRate() {
            return equityLtcgBaseRate.multiply(BigDecimal.ONE.add(healthEducationCessRate))
                .setScale(4, RoundingMode.HALF_UP); // 0.1300 (13.0%)
        }
        public BigDecimal effectiveEquityStcgRate() {
            return equityStcgBaseRate.multiply(BigDecimal.ONE.add(healthEducationCessRate))
                .setScale(4, RoundingMode.HALF_UP); // 0.2080 (20.8%)
        }
    }

    public record DrawdownRung(
        String tier,
        BigDecimal drawdownPct,
        BigDecimal liquidTargetPct,
        BigDecimal coreTargetPct,
        BigDecimal liquidDeployFraction
    ) {}

    public record IntraBucketRoutingSpec(
        boolean remediateOnPassiveFlowOnly,
        String primaryIsin,
        BigDecimal primaryTargetRatio,
        BigDecimal ratioBand
    ) {}

    public record BucketSpec(
        BigDecimal targetPct,
        BigDecimal bandLowerPct,
        BigDecimal bandUpperPct,
        IntraBucketRoutingSpec intraBucketRouting,
        List<SchemeSpec> schemes
    ) {
        public BucketSpec(
            BigDecimal targetPct,
            BigDecimal bandLowerPct,
            BigDecimal bandUpperPct,
            List<SchemeSpec> schemes
        ) {
            this(targetPct, bandLowerPct, bandUpperPct, null, schemes);
        }
    }

    public record SchemeSpec(
        String isin,
        String name,
        String amcCode,
        BigDecimal weightWithinBucket,
        int exitLoadDays,
        int ltcgHoldingDays,
        int settlementDays
    ) {}

    public BucketTargetsConfig {
        Objects.requireNonNull(globalInvariants, "globalInvariants required");
        Objects.requireNonNull(taxParameters, "taxParameters required");
        Objects.requireNonNull(buckets, "buckets required");

        if (drawdownRungs != null) {
            drawdownRungs = drawdownRungs.stream()
                .sorted(Comparator.comparing(DrawdownRung::drawdownPct))
                .toList();
        }

        BigDecimal totalTarget = buckets.values().stream()
            .map(BucketSpec::targetPct)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalTarget.compareTo(new BigDecimal("100.00")) != 0) {
            throw new IllegalStateException("Bucket targets in YAML must sum to 100.00%, found: " + totalTarget);
        }
    }
}
