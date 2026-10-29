package in.portfolioos.rebalancer.engine;

import in.portfolioos.rebalancer.config.BucketTargetsConfig;
import in.portfolioos.rebalancer.config.BucketTargetsConfig.BucketType;
import in.portfolioos.rebalancer.engine.RebalanceTriggerEvaluator.TriggerType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class RebalanceWaterfallEngine {

    public enum SettlementPhasing {
        INTRA_AMC_ATOMIC_SWITCH, // T+0 switch within same AMC (e.g. ICICI -> ICICI)
        CROSS_AMC_TWO_LEG        // Leg 1 Sell (T+1/T+2) -> Bank Settlement -> Leg 2 Buy
    }

    public record RoutingResult(
        Map<BucketType, BigDecimal> bucketBuysInr,
        BigDecimal cashOverflowInr
    ) {}

    /**
     * Guard against Harmful Loss Harvesting:
     * Never harvest a standalone loss lot (resetting cost basis lower & restarting LTCG clock)
     * UNLESS the bucket is already being trimmed for drift/legacy OR there are taxable gains to offset.
     */
    public boolean shouldHarvestLossLot(
            boolean isStep0Legacy,
            boolean isBucketBeingTrimmed,
            BigDecimal realizedAndPlanTaxableGainsInr) {
        if (isStep0Legacy || isBucketBeingTrimmed) {
            return true;
        }
        return realizedAndPlanTaxableGainsInr.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Deterministic Capped Water-Filling Router.
     * NEVER rescales buckets above their deficits or caps after allocation.
     */
    public RoutingResult routeProceeds(
            BucketTargetsConfig config,
            TriggerType triggerType,
            BigDecimal coreTrimPoolInr,
            BigDecimal generalPoolInr,
            BigDecimal effectiveActiveCorpusInr,
            Map<BucketType, BigDecimal> postSellBucketValuesInr,
            Map<BucketType, BigDecimal> regimeShiftedTargetsPct) {

        BigDecimal minOrder = config.globalInvariants().minTradeAmountInr();
        Map<BucketType, BigDecimal> buys = new EnumMap<>(BucketType.class);
        for (BucketType b : List.of(BucketType.EQUITY_CORE, BucketType.EQUITY_SATELLITE,
                                    BucketType.GOLD_SILVER, BucketType.LIQUID_BUFFER)) {
            buys.put(b, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        // Compute exact Rupee deficits against post-Step-0 effectiveActiveCorpusInr
        Map<BucketType, BigDecimal> deficits = new EnumMap<>(BucketType.class);
        for (BucketType b : buys.keySet()) {
            BigDecimal targetPct = regimeShiftedTargetsPct.get(b);
            BigDecimal targetInr = effectiveActiveCorpusInr.multiply(targetPct)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal currentInr = postSellBucketValuesInr.getOrDefault(b, BigDecimal.ZERO);
            BigDecimal deficit = targetInr.subtract(currentInr).max(BigDecimal.ZERO);
            // Suppress dust deficits below ₹1,000
            deficits.put(b, deficit.compareTo(minOrder) >= 0 ? deficit : BigDecimal.ZERO);
        }

        // Hard 15% Rupee ceiling for LIQUID_BUFFER
        BigDecimal liquidHardCapInr = effectiveActiveCorpusInr
            .multiply(config.globalInvariants().liquidHardCapPct())
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal currentLiquidInr = postSellBucketValuesInr.getOrDefault(BucketType.LIQUID_BUFFER, BigDecimal.ZERO);
        BigDecimal maxAdditionalLiquidHeadroom = liquidHardCapInr.subtract(currentLiquidInr).max(BigDecimal.ZERO);

        BigDecimal cashOverflow = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // -------------------------------------------------------------------------
        // 1. Route DRAWDOWN Pool: Strictly into EQUITY_CORE first, then SATELLITE (0% to Gold)
        // -------------------------------------------------------------------------
        if (triggerType == TriggerType.DRAWDOWN) {
            BigDecimal remaining = coreTrimPoolInr.add(generalPoolInr);
            remaining = fillBucket(buys, deficits, BucketType.EQUITY_CORE, remaining, minOrder);
            remaining = fillBucket(buys, deficits, BucketType.EQUITY_SATELLITE, remaining, minOrder);
            // If drawdown deployment exceeds calculated deficits, all remaining crisis dry powder goes to EQUITY_CORE
            if (remaining.compareTo(minOrder) >= 0) {
                buys.put(BucketType.EQUITY_CORE, buys.get(BucketType.EQUITY_CORE).add(remaining));
                remaining = BigDecimal.ZERO;
            }
            cashOverflow = cashOverflow.add(remaining);
            return new RoutingResult(buys, cashOverflow);
        }

        // -------------------------------------------------------------------------
        // 2. Route CORE Trim Pool (Strict Cascade: Gold -> Liquid Target -> Core -> Liquid 15% Cap -> Cash)
        //    NEVER routes to EQUITY_SATELLITE.
        // -------------------------------------------------------------------------
        BigDecimal remCorePool = coreTrimPoolInr;
        remCorePool = fillBucket(buys, deficits, BucketType.GOLD_SILVER, remCorePool, minOrder);
        remCorePool = fillBucket(buys, deficits, BucketType.LIQUID_BUFFER, remCorePool, minOrder);
        remCorePool = fillBucket(buys, deficits, BucketType.EQUITY_CORE, remCorePool, minOrder);

        // Step 4 of Waterfall: Arbitrage terminal sink up to 15% hard portfolio ceiling
        BigDecimal liquidUsedSoFar = buys.get(BucketType.LIQUID_BUFFER);
        BigDecimal remainingLiquidCap = maxAdditionalLiquidHeadroom.subtract(liquidUsedSoFar).max(BigDecimal.ZERO);
        if (remCorePool.compareTo(minOrder) >= 0 && remainingLiquidCap.compareTo(minOrder) >= 0) {
            BigDecimal sinkAlloc = remCorePool.min(remainingLiquidCap);
            buys.put(BucketType.LIQUID_BUFFER, liquidUsedSoFar.add(sinkAlloc));
            remCorePool = remCorePool.subtract(sinkAlloc);
        }
        // Step 5: Unallocated Cash Overflow
        cashOverflow = cashOverflow.add(remCorePool);

        // -------------------------------------------------------------------------
        // 3. Route General Pool (Step 0 Legacy / Satellite trims) via Capped Water-Filling
        // -------------------------------------------------------------------------
        BigDecimal remGeneral = generalPoolInr;
        BigDecimal totalRemainingDeficit = deficits.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        if (remGeneral.compareTo(minOrder) >= 0 && totalRemainingDeficit.compareTo(BigDecimal.ZERO) > 0) {
            if (remGeneral.compareTo(totalRemainingDeficit) >= 0) {
                // Pool covers 100% of all remaining deficits without violating caps
                for (BucketType b : List.of(BucketType.EQUITY_CORE, BucketType.EQUITY_SATELLITE,
                                            BucketType.GOLD_SILVER, BucketType.LIQUID_BUFFER)) {
                    BigDecimal d = deficits.get(b);
                    if (d.compareTo(minOrder) >= 0) {
                        buys.put(b, buys.get(b).add(d));
                        remGeneral = remGeneral.subtract(d);
                        deficits.put(b, BigDecimal.ZERO);
                    }
                }
            } else {
                // Proportional allocation strictly bounded by each bucket's deficit
                BigDecimal allocatedSum = BigDecimal.ZERO;
                BucketType largestDeficitBucket = BucketType.EQUITY_CORE;
                BigDecimal maxDef = BigDecimal.ZERO;

                for (BucketType b : List.of(BucketType.EQUITY_CORE, BucketType.EQUITY_SATELLITE,
                                            BucketType.GOLD_SILVER, BucketType.LIQUID_BUFFER)) {
                    BigDecimal d = deficits.get(b);
                    if (d.compareTo(maxDef) > 0) {
                        maxDef = d;
                        largestDeficitBucket = b;
                    }
                    BigDecimal share = remGeneral.multiply(d)
                        .divide(totalRemainingDeficit, 2, RoundingMode.DOWN);
                    if (share.compareTo(minOrder) >= 0) {
                        buys.put(b, buys.get(b).add(share));
                        allocatedSum = allocatedSum.add(share);
                    }
                }
                // Route exact paise remainder to largest deficit bucket (or cashOverflow if capped)
                BigDecimal remainder = remGeneral.subtract(allocatedSum);
                if (buys.get(largestDeficitBucket).add(remainder).compareTo(deficits.get(largestDeficitBucket)) <= 0
                        && buys.get(largestDeficitBucket).add(remainder).compareTo(minOrder) >= 0) {
                    buys.put(largestDeficitBucket, buys.get(largestDeficitBucket).add(remainder));
                    remGeneral = BigDecimal.ZERO;
                } else {
                    remGeneral = remainder;
                }
            }
        }

        // Any leftover from General Pool spills into Liquid up to 15% cap, then CashOverflow
        BigDecimal finalLiquidHeadroom = maxAdditionalLiquidHeadroom
            .subtract(buys.get(BucketType.LIQUID_BUFFER)).max(BigDecimal.ZERO);
        if (remGeneral.compareTo(minOrder) >= 0 && finalLiquidHeadroom.compareTo(minOrder) >= 0) {
            BigDecimal extraLiq = remGeneral.min(finalLiquidHeadroom);
            buys.put(BucketType.LIQUID_BUFFER, buys.get(BucketType.LIQUID_BUFFER).add(extraLiq));
            remGeneral = remGeneral.subtract(extraLiq);
        }
        cashOverflow = cashOverflow.add(remGeneral);

        return new RoutingResult(buys, cashOverflow);
    }

    private BigDecimal fillBucket(
            Map<BucketType, BigDecimal> buys,
            Map<BucketType, BigDecimal> deficits,
            BucketType bucket,
            BigDecimal availablePool,
            BigDecimal minOrder) {
        BigDecimal deficit = deficits.getOrDefault(bucket, BigDecimal.ZERO);
        if (availablePool.compareTo(minOrder) < 0 || deficit.compareTo(minOrder) < 0) {
            return availablePool;
        }
        BigDecimal alloc = availablePool.min(deficit);
        if (alloc.compareTo(minOrder) < 0) {
            return availablePool;
        }
        buys.put(bucket, buys.get(bucket).add(alloc));
        deficits.put(bucket, deficit.subtract(alloc));
        return availablePool.subtract(alloc);
    }
}

