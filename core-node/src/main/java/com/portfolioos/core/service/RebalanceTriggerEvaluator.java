package com.portfolioos.core.service;

import com.portfolioos.core.common.PortfolioConstants;

import com.portfolioos.core.dtos.RebalancePlanDtos.DrawdownContextDto;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.persistence.TriggerHistoryRepository;
import com.portfolioos.core.rules.BucketConfigLoader;
import com.portfolioos.core.valuation.BucketEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RebalanceTriggerEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RebalanceTriggerEvaluator.class);

    private final TriggerHistoryRepository repository;

    public enum TriggerType {
        DRAWDOWN,
        DRIFT,
        CASH_INFLOW_NEEDED,    // Lower-band breach only; 0 sells, guides fresh bank cash
        SCHEDULED_MAINTENANCE, // March only: Step 0 Legacy + Sec 112A ₹1.25L harvest
        NONE
    }

    public record TriggerResolution(
        String triggerType,            // DRAWDOWN, DRIFT, SCHEDULED_MAINTENANCE, CASH_INFLOW_NEEDED, NONE
        String reasonCode,
        String reasonLabel,
        boolean hasSellSide,
        boolean hasGoldBuy,
        boolean sellCooldownActive,
        long daysSinceLastSell,
        boolean goldIdleActive,
        long monthsSinceLastGoldBuy,
        List<String> driftedBuckets,
        DrawdownContextDto drawdownContext
    ) {}

    public RebalanceTriggerEvaluator(TriggerHistoryRepository repository) {
        this.repository = repository;
    }

    public TriggerHistoryRepository getRepository() {
        return repository;
    }

    public TriggerResolution getCurrentStatus(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        BucketConfigLoader.BucketTargetVersion activeVersion,
        LocalDate currentDate
    ) {
        return getCurrentStatus(openLots, navMap, benchmarkCurrent, benchmarkRollingHigh, customTargets, activeVersion, currentDate, false);
    }

    public TriggerResolution getCurrentStatus(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        BucketConfigLoader.BucketTargetVersion activeVersion,
        LocalDate currentDate,
        boolean allowScopedFeedDegradation
    ) {
        LocalDate today = currentDate != null ? currentDate : LocalDate.now();

        // 1. Calculate Corpus and Bucket Valuations
        BigDecimal liveCorpus = BigDecimal.ZERO;
        BigDecimal activeCorpus = BigDecimal.ZERO;
        Map<String, BigDecimal> bucketValuations = new HashMap<>();

        if (openLots != null) {
            for (Lot lot : openLots) {
                BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "RebalanceTriggerEvaluator");
                BigDecimal lotVal = lot.remainingUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
                liveCorpus = liveCorpus.add(lotVal);

                BucketEngine.Bucket b = BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName());
                if (b != BucketEngine.Bucket.LEGACY_HOLDINGS) {
                    activeCorpus = activeCorpus.add(lotVal);
                }

                String bucketName = BucketConfigLoader.mapAssetToBucket(lot.assetId(), lot.assetName());
                bucketValuations.put(bucketName, bucketValuations.getOrDefault(bucketName, BigDecimal.ZERO).add(lotVal));
            }
        }

        // 2. Compute Drawdown Context
        BigDecimal high = null;
        BigDecimal curr = benchmarkCurrent;
        double ddPct = 0.0;
        boolean benchmarkSupplied = benchmarkCurrent != null && benchmarkRollingHigh != null;

        if (benchmarkSupplied) {
            if (benchmarkRollingHigh.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("CRITICAL BENCHMARK FAILURE: Missing or invalid 52-week peak for Nifty 500 TRI Benchmark");
            }
            if (benchmarkCurrent.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("CRITICAL BENCHMARK FAILURE: Missing or invalid live level for Nifty 500 TRI Benchmark");
            }

            if (repository != null) {
                Optional<BigDecimal> storedWatermarkOpt = repository.getNiftyTriWatermark();
                if (storedWatermarkOpt.isPresent()) {
                    BigDecimal storedWatermark = storedWatermarkOpt.get();
                    if (storedWatermark.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalStateException("CRITICAL INTEGRITY FAILURE: Stored Nifty TRI watermark <= 0");
                    }
                    if (curr.compareTo(storedWatermark) > 0) {
                        repository.setNiftyTriWatermark(curr);
                        storedWatermark = curr;
                    }
                    high = storedWatermark.max(benchmarkRollingHigh).max(curr);
                } else {
                    BigDecimal initialWatermark = benchmarkRollingHigh.max(curr);
                    repository.setNiftyTriWatermark(initialWatermark);
                    high = initialWatermark;
                }
            } else {
                high = benchmarkRollingHigh.max(curr);
            }
            ddPct = PortfolioConstants.requireValidDrawdownPct(curr, high, "Nifty 500 TRI Benchmark");
        } else {
            if (!allowScopedFeedDegradation) {
                PortfolioConstants.requireValidDrawdownPct(benchmarkCurrent, benchmarkRollingHigh, "Nifty 500 TRI Benchmark");
            }
            high = BigDecimal.ZERO;
            ddPct = 0.0;
        }

        boolean benchmarkAvailable = benchmarkSupplied && benchmarkCurrent.compareTo(BigDecimal.ZERO) > 0 && benchmarkRollingHigh.compareTo(BigDecimal.ZERO) > 0;

        var v3Config = com.portfolioos.core.rules.BucketConfigLoader.loadV24Config();
        List<com.portfolioos.core.config.BucketTargetsConfig.DrawdownRung> rungs =
            (v3Config != null && v3Config.drawdownRungs() != null)
                ? v3Config.drawdownRungs()
                : List.of();

        String armedTier = "NONE";
        int currentRung = 0;
        String nextTier = "MAX_TIER_REACHED";
        double nextTierTargetPct = 0.0;
        boolean nextTierFound = false;

        if (benchmarkAvailable) {
            for (var rung : rungs) {
                if (ddPct >= rung.drawdownPct().doubleValue()) {
                    armedTier = rung.tier();
                    currentRung = rung.drawdownPct().intValue();
                }
            }
            for (var rung : rungs) {
                if (rung.drawdownPct().doubleValue() > ddPct) {
                    nextTier = rung.tier();
                    nextTierTargetPct = rung.drawdownPct().doubleValue();
                    nextTierFound = true;
                    break;
                }
            }
        }
        double nextTierDistancePct = nextTierFound
            ? Math.max(0.0, Math.round((nextTierTargetPct - ddPct) * 10.0) / 10.0)
            : 0.0;

        DrawdownContextDto drawdownCtx = new DrawdownContextDto(
            ddPct,
            high,
            today.toString(),
            curr,
            armedTier,
            nextTier,
            nextTierDistancePct
        );

        // 3. Query Cooldown & Cycle-Latched Drawdown State from Repository
        Optional<LocalDateTime> lastSellOpt = repository != null ? repository.getLastSellSideFiringDate() : Optional.empty();
        long daysSinceLastSell = lastSellOpt.map(dt -> ChronoUnit.DAYS.between(dt.toLocalDate(), today)).orElse(9999L);
        boolean sellCooldownActive = daysSinceLastSell < PortfolioConstants.REBALANCE_COOLDOWN_DAYS;

        int maxFiredRung = repository != null ? repository.getActiveDrawdownMaxFiredTier() : 0;

        // 4. Canonical Bucket Drift Evaluation (Upper & Lower Breaches Tracked Separately)
        List<String> driftedBuckets = new ArrayList<>();
        List<String> upperBreachedBuckets = new ArrayList<>();
        List<String> lowerBreachedBuckets = new ArrayList<>();

        if (liveCorpus.compareTo(BigDecimal.ZERO) > 0) {
            List<BucketEngine.BucketTarget> engineTargets = (customTargets != null && !customTargets.isEmpty())
                ? customTargets
                : BucketConfigLoader.getActiveBucketTargets(today);

            String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(today);
            BucketEngine.RebalanceEngineResult engineResult = BucketEngine.evaluateRebalance(
                openLots,
                navMap,
                today,
                benchmarkCurrent,
                benchmarkRollingHigh,
                engineTargets,
                currentFy
            );

            if (engineResult != null && engineResult.bucketStatuses() != null) {
                for (BucketEngine.BucketStatus bs : engineResult.bucketStatuses()) {
                    if (bs.bucket() == BucketEngine.Bucket.LEGACY_HOLDINGS) {
                        continue;
                    }
                    boolean isLiquid = (bs.bucket() == BucketEngine.Bucket.LIQUID_BUFFER || bs.bucket() == BucketEngine.Bucket.LIQUIDITY_ARBITRAGE);
                    boolean isGold = (bs.bucket() == BucketEngine.Bucket.GOLD_SILVER || bs.bucket() == BucketEngine.Bucket.HEDGE_COMMODITY);

                    BigDecimal curPct = bs.currentPct();
                    BigDecimal targetPct = bs.targetPct();
                    BigDecimal drift = curPct.subtract(targetPct);

                    com.portfolioos.core.config.BucketTargetsConfig.BucketType bType = switch (bs.bucket()) {
                        case EQUITY_CORE -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_CORE;
                        case EQUITY_SATELLITE, SATELLITE_VALUE, SATELLITE_MOMENTUM, SATELLITE_SMALLCAP -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_SATELLITE;
                        case GOLD_SILVER, HEDGE_COMMODITY -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.GOLD_SILVER;
                        case LIQUID_BUFFER, LIQUIDITY_ARBITRAGE -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER;
                        case LEGACY_HOLDINGS -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.LEGACY_HOLDINGS;
                    };
                    BigDecimal bandLower = null;
                    BigDecimal bandUpper = null;
                    if (engineTargets != null) {
                        for (var et : engineTargets) {
                            if (et.bucket() == bs.bucket()) {
                                bandLower = et.bandLowerPct();
                                bandUpper = et.bandUpperPct();
                                break;
                            }
                        }
                    }
                    if (bandLower == null || bandUpper == null) {
                        var spec = (v3Config != null && v3Config.buckets() != null) ? v3Config.buckets().get(bType) : null;
                        if (spec == null) {
                            throw new IllegalStateException("CRITICAL CONFIG ERROR: Missing bucket specification for " + bType + " in bucket_targets.yaml");
                        }
                        if (bandLower == null) bandLower = spec.bandLowerPct();
                        if (bandUpper == null) bandUpper = spec.bandUpperPct();
                    }

                    if (drift.compareTo(bandUpper) > 0) {
                        // Allow upper-band breach for liquid buffer if ddPct < 10.0
                        if (!isLiquid || ddPct < 10.0) {
                            upperBreachedBuckets.add(bs.bucket().name());
                            if (!driftedBuckets.contains(bs.bucket().name())) {
                                driftedBuckets.add(bs.bucket().name());
                            }
                        }
                    } else if (drift.compareTo(bandLower.negate()) < 0) {
                        // Suppress LIQUID_BUFFER lower-band breach in hold zone (ddPct >= 5.0) and during drawdown (ddPct >= 10.0)
                        // Also exclude GOLD_SILVER and LIQUID_BUFFER from CASH_INFLOW_NEEDED when ddPct >= 10.0
                        boolean suppressLower = (isLiquid && ddPct >= 5.0) || ((isLiquid || isGold) && ddPct >= 10.0);
                        if (!suppressLower) {
                            lowerBreachedBuckets.add(bs.bucket().name());
                            if (!driftedBuckets.contains(bs.bucket().name())) {
                                driftedBuckets.add(bs.bucket().name());
                            }
                        }
                    }
                }
            }
        }

        // 5. Trigger Resolution Priority Order
        boolean skipDriftSells = false;
        // Priority 1: DRAWDOWN (Cycle-Latched, Config-Driven)
        if (currentRung > 0) {
            if (currentRung > maxFiredRung) {
                return new TriggerResolution(
                    "DRAWDOWN",
                    "DRAWDOWN_TIER_" + currentRung,
                    String.format("%d%% Portfolio Drawdown Tier Triggered", currentRung),
                    true,
                    false,
                    sellCooldownActive,
                    daysSinceLastSell,
                    false,
                    0L,
                    driftedBuckets,
                    drawdownCtx
                );
            } else {
                skipDriftSells = true;
            }
        }

        // Priority 2: DRIFT (Upper-Band Breach Only, Self-Funded)
        if (!skipDriftSells && !upperBreachedBuckets.isEmpty()) {
            if (sellCooldownActive) {
                return new TriggerResolution(
                    "NONE",
                    "DRIFT_BLOCKED_BY_COOLDOWN",
                    String.format("Bucket drift detected (%s) but sell rebalance is on 30-day cooldown (%d days since last sell)",
                        String.join(", ", driftedBuckets), daysSinceLastSell),
                    false,
                    false,
                    true,
                    daysSinceLastSell,
                    false,
                    0L,
                    driftedBuckets,
                    drawdownCtx
                );
            } else {
                return new TriggerResolution(
                    "DRIFT",
                    "DRIFT_THRESHOLD_EXCEEDED",
                    String.format("Bucket Allocation Drift Exceeded Threshold (%s)", String.join(", ", driftedBuckets)),
                    true,
                    false,
                    false,
                    daysSinceLastSell,
                    false,
                    0L,
                    driftedBuckets,
                    drawdownCtx
                );
            }
        }

        // Priority 3: SCHEDULED_MAINTENANCE (March Only)
        if (v3Config != null && v3Config.globalInvariants() != null &&
            v3Config.globalInvariants().scheduledMonths() != null &&
            v3Config.globalInvariants().scheduledMonths().contains(today.getMonthValue())) {
            boolean alreadyExecutedThisMonth = repository != null && repository.hasScheduledRunExecutedForMonth(today.getYear(), today.getMonthValue());
            if (!alreadyExecutedThisMonth) {
                if (sellCooldownActive) {
                    return new TriggerResolution(
                        "NONE",
                        "SCHEDULED_BLOCKED_BY_COOLDOWN",
                        String.format("Scheduled window active but sell rebalance is on 30-day cooldown (%d days since last sell)", daysSinceLastSell),
                        false,
                        false,
                        true,
                        daysSinceLastSell,
                        false,
                        0L,
                        driftedBuckets,
                        drawdownCtx
                    );
                } else {
                    return new TriggerResolution(
                        "SCHEDULED_MAINTENANCE",
                        "SCHEDULED_RECONSTITUTION",
                        "March Scheduled Maintenance Window",
                        true,
                        false,
                        false,
                        daysSinceLastSell,
                        false,
                        0L,
                        driftedBuckets,
                        drawdownCtx
                    );
                }
            }
        }

        // Priority 4: CASH_INFLOW_NEEDED (Replaces GOLD_FLOOR_BACKSTOP)
        if (!lowerBreachedBuckets.isEmpty()) {
            return new TriggerResolution(
                "CASH_INFLOW_NEEDED",
                "CASH_INFLOW_NEEDED",
                "Underweight bucket(s): " + String.join(", ", lowerBreachedBuckets) + " — route next bank surplus here (0 sells)",
                false,
                false,
                sellCooldownActive,
                daysSinceLastSell,
                false,
                0L,
                driftedBuckets,
                drawdownCtx
            );
        }

        if (skipDriftSells) {
            return new TriggerResolution(
                "NONE",
                "DRAWDOWN_RUNG_ALREADY_DEPLOYED",
                String.format("Drawdown tier %s already deployed in current cycle", armedTier),
                false,
                false,
                sellCooldownActive,
                daysSinceLastSell,
                false,
                0L,
                driftedBuckets,
                drawdownCtx
            );
        }

        return new TriggerResolution(
            "NONE",
            "NO_REBALANCE_REQUIRED",
            "Portfolio is balanced and within thresholds",
            false,
            false,
            sellCooldownActive,
            daysSinceLastSell,
            false,
            0L,
            driftedBuckets,
            drawdownCtx
        );
    }

    public TriggerResolution evaluateAndRecord(
        String planId,
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        BucketConfigLoader.BucketTargetVersion activeVersion,
        LocalDate currentDate
    ) {
        TriggerResolution resolution = getCurrentStatus(
            openLots, navMap, benchmarkCurrent, benchmarkRollingHigh, customTargets, activeVersion, currentDate
        );

        if (!"NONE".equals(resolution.triggerType())) {
            if (repository == null) {
                throw new IllegalStateException("TriggerHistoryRepository cannot be null when recording trigger execution");
            }
            if (benchmarkCurrent != null && benchmarkCurrent.compareTo(BigDecimal.ZERO) > 0) {
                Optional<BigDecimal> storedWatermarkOpt = repository.getNiftyTriWatermark();
                if (storedWatermarkOpt.isPresent()) {
                    if (benchmarkCurrent.compareTo(storedWatermarkOpt.get()) > 0) {
                        repository.setNiftyTriWatermark(benchmarkCurrent);
                    }
                } else {
                    BigDecimal initialWatermark = (benchmarkRollingHigh != null ? benchmarkRollingHigh : BigDecimal.ZERO).max(benchmarkCurrent);
                    repository.setNiftyTriWatermark(initialWatermark);
                }
            }
            LocalDate today = currentDate != null ? currentDate : LocalDate.now();
            int drawdownTier = 0;
            if ("DRAWDOWN".equalsIgnoreCase(resolution.triggerType()) && resolution.drawdownContext() != null) {
                String armed = resolution.drawdownContext().armedTier();
                var v3Conf = com.portfolioos.core.rules.BucketConfigLoader.loadV24Config();
                List<com.portfolioos.core.config.BucketTargetsConfig.DrawdownRung> rungs = (v3Conf != null && v3Conf.drawdownRungs() != null) ? v3Conf.drawdownRungs() : List.of();
                for (var r : rungs) {
                    if (r.tier().equalsIgnoreCase(armed)) {
                        drawdownTier = r.drawdownPct().intValue();
                        break;
                    }
                }
                if (drawdownTier == 0 && armed != null && armed.startsWith("TIER_")) {
                    try {
                        drawdownTier = Integer.parseInt(armed.substring(5));
                    } catch (Exception ignored) {}
                }
            }
            String metadataJson = "{}";
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                metadataJson = mapper.writeValueAsString(Map.of("driftedBuckets", resolution.driftedBuckets() != null ? resolution.driftedBuckets() : List.of()));
            } catch (Exception e) {
                log.warn("Failed to serialize rebalance metadata json: {}", e.getMessage());
            }
            repository.recordExecution(
                planId,
                resolution.triggerType(),
                resolution.reasonCode(),
                today.atStartOfDay(),
                resolution.hasSellSide(),
                resolution.hasGoldBuy(),
                metadataJson,
                drawdownTier
            );
            if ("SCHEDULED".equalsIgnoreCase(resolution.triggerType()) || "SCHEDULED_MAINTENANCE".equalsIgnoreCase(resolution.triggerType())) {
                repository.recordScheduledRunExecution(today.getYear(), today.getMonthValue(), today.atStartOfDay());
            }
        }

        return resolution;
    }
}
