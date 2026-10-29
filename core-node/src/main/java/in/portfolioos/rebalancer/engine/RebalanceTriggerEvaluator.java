package in.portfolioos.rebalancer.engine;

import in.portfolioos.rebalancer.config.BucketTargetsConfig;
import in.portfolioos.rebalancer.config.BucketTargetsConfig.BucketType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

public final class RebalanceTriggerEvaluator {

    public enum TriggerType {
        DRAWDOWN,
        DRIFT,
        SCHEDULED_MAINTENANCE, // Only executes Step 0 Legacy + Tax Harvest (no active trims)
        GOLD_FLOOR_BACKSTOP,
        NONE
    }

    public record TriggerDecision(
        TriggerType type,
        String drawdownTier,
        boolean urgent,
        BigDecimal niftyDrawdownPct
    ) {}

    public TriggerDecision evaluate(
            BucketTargetsConfig config,
            BigDecimal currentNiftyTri,
            BigDecimal athWatermarkNiftyTri,
            Map<BucketType, BigDecimal> activeBucketWeightsPct,
            LocalDate today,
            Optional<LocalDate> lastSellRebalanceDate,
            Optional<String> lastFiredDrawdownTierIn30d,
            Optional<LocalDate> lastGoldBuyDate) {

        if (currentNiftyTri == null || athWatermarkNiftyTri == null
                || currentNiftyTri.compareTo(BigDecimal.ZERO) <= 0
                || athWatermarkNiftyTri.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Fail-closed: Invalid or missing Nifty 500 TRI ATH watermark inputs");
        }

        // 1. Compute Drawdown against persistent All-Time High (ATH) Watermark
        BigDecimal effectiveHigh = currentNiftyTri.max(athWatermarkNiftyTri);
        BigDecimal drawdownPct = effectiveHigh.subtract(currentNiftyTri)
            .multiply(new BigDecimal("100"))
            .divide(effectiveHigh, 4, RoundingMode.HALF_UP);

        boolean urgent = drawdownPct.compareTo(config.globalInvariants().urgentDrawdownThresholdPct()) >= 0;
        boolean inCooldown = lastSellRebalanceDate
            .map(d -> ChronoUnit.DAYS.between(d, today) < config.globalInvariants().rebalanceCooldownDays())
            .orElse(false);

        // Priority 1: DRAWDOWN (bypasses cooldown ONLY if a deeper rung than last 30d arms)
        Optional<BucketTargetsConfig.DrawdownRung> armedRung = config.drawdownRungs().stream()
            .filter(r -> drawdownPct.compareTo(r.drawdownPct()) >= 0)
            .reduce((first, second) -> second); // Highest armed tier

        if (armedRung.isPresent()) {
            String tier = armedRung.get().tier();
            boolean deeperThanRecent = lastFiredDrawdownTierIn30d
                .map(prev -> isDeeperTier(tier, prev))
                .orElse(true);
            if (!inCooldown || deeperThanRecent) {
                return new TriggerDecision(TriggerType.DRAWDOWN, tier, urgent, drawdownPct);
            }
        }

        // Priority 2: DRIFT (5/25 Hard-Band breach only; Intra-Core ratio is NEVER a standalone sell trigger)
        if (!inCooldown && hasBucketBandBreach(config, activeBucketWeightsPct)) {
            return new TriggerDecision(TriggerType.DRIFT, "NONE", urgent, drawdownPct);
        }

        // Priority 3: SCHEDULED_MAINTENANCE (March/Sept outside NSE reconstitution window)
        if (!inCooldown && isScheduledWindow(config, today)) {
            return new TriggerDecision(TriggerType.SCHEDULED_MAINTENANCE, "NONE", false, drawdownPct);
        }

        // Priority 4: GOLD_FLOOR_BACKSTOP (Buy-only, exempt from sell cooldown)
        if (isGoldBackstopArmed(config, activeBucketWeightsPct, today, lastGoldBuyDate)) {
            return new TriggerDecision(TriggerType.GOLD_FLOOR_BACKSTOP, "NONE", false, drawdownPct);
        }

        return new TriggerDecision(TriggerType.NONE, "NONE", false, drawdownPct);
    }

    private boolean hasBucketBandBreach(BucketTargetsConfig config, Map<BucketType, BigDecimal> weightsPct) {
        for (var entry : config.buckets().entrySet()) {
            BucketType bucket = entry.getKey();
            var spec = entry.getValue();
            BigDecimal w = weightsPct.getOrDefault(bucket, BigDecimal.ZERO);
            BigDecimal lowerBound = spec.targetPct().subtract(spec.bandLowerPct());
            BigDecimal upperBound = spec.targetPct().add(spec.bandUpperPct());

            // GOLD_SILVER downside is governed by GOLD_FLOOR_BACKSTOP, only upside (>22%) triggers DRIFT trim
            if (bucket == BucketType.GOLD_SILVER) {
                if (w.compareTo(upperBound) > 0) return true;
            } else {
                if (w.compareTo(lowerBound) < 0 || w.compareTo(upperBound) > 0) return true;
            }
        }
        return false;
    }

    private boolean isGoldBackstopArmed(
            BucketTargetsConfig config,
            Map<BucketType, BigDecimal> weightsPct,
            LocalDate today,
            Optional<LocalDate> lastGoldBuyDate) {
        var goldSpec = config.buckets().get(BucketType.GOLD_SILVER);
        BigDecimal goldWeight = weightsPct.getOrDefault(BucketType.GOLD_SILVER, BigDecimal.ZERO);
        BigDecimal gap = goldSpec.targetPct().subtract(goldWeight);

        boolean idleLongEnough = lastGoldBuyDate
            .map(d -> ChronoUnit.MONTHS.between(d, today) >= goldSpec.backstop().minIdleMonths())
            .orElse(true);

        return idleLongEnough && gap.compareTo(goldSpec.backstop().minUnderweightGapPct()) >= 0;
    }

    private boolean isScheduledWindow(BucketTargetsConfig config, LocalDate today) {
        if (!config.globalInvariants().scheduledMonths().contains(today.getMonthValue())) {
            return false;
        }
        int daysLeftInMonth = today.lengthOfMonth() - today.getDayOfMonth();
        int blackoutDays = config.globalInvariants().nseReconstitutionBlackoutHours() / 24;
        return daysLeftInMonth >= blackoutDays;
    }

    private boolean isDeeperTier(String candidate, String previous) {
        int c = Integer.parseInt(candidate.replace("TIER_", ""));
        int p = Integer.parseInt(previous.replace("TIER_", ""));
        return c > p;
    }
}

