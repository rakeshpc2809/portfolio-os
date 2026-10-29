package in.portfolioos.rebalancer.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record BucketTargetsConfig(
    String version,
    GlobalInvariants globalInvariants,
    TaxParameters taxParameters,
    List<DrawdownRung> drawdownRungs,
    List<DampenerTier> dampenerTiers,
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
        int nseReconstitutionBlackoutHours
    ) {}

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

    public record DampenerTier(BigDecimal maxRelativeExcessPct, BigDecimal multiplier) {}

    public record BucketSpec(
        BigDecimal targetPct,
        BigDecimal bandLowerPct,
        BigDecimal bandUpperPct,
        BackstopSpec backstop,
        List<SchemeSpec> schemes
    ) {}

    public record BackstopSpec(
        int minIdleMonths,
        BigDecimal minUnderweightGapPct,
        BigDecimal gapCloseFraction
    ) {}

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
        BigDecimal totalTarget = buckets.values().stream()
            .map(BucketSpec::targetPct)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalTarget.compareTo(new BigDecimal("100.00")) != 0) {
            throw new IllegalStateException("Bucket targets in YAML must sum to 100.00%, found: " + totalTarget);
        }
    }
}

