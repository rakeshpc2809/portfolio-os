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
        double nextTierTargetPct = 30.0;

        if (benchmarkAvailable && ddPct >= 10.0) {
            for (var rung : rungs) {
                if (ddPct >= rung.drawdownPct().doubleValue()) {
                    armedTier = rung.tier();
                    currentRung = (int) rung.drawdownPct().doubleValue();
                }
            }
        }

        for (var rung : rungs) {
            if (rung.drawdownPct().doubleValue() > ddPct) {
                nextTier = rung.tier();
                nextTierTargetPct = rung.drawdownPct().doubleValue();
                break;
            }
        }
        double nextTierDistancePct = Math.max(0.0, Math.round((nextTierTargetPct - ddPct) * 10.0) / 10.0);

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

        BucketConfigLoader.BucketTargetVersion ver = activeVersion != null ? activeVersion : BucketConfigLoader.getActiveVersion(today);
        double coreTargetPct = 50.0;
        double coreBandPct = 5.0;
        double targetLmRatio = 0.60;

        if (ver != null && ver.targets() != null) {
            for (BucketConfigLoader.BucketTargetConfig tc : ver.targets()) {
                if ("EQUITY_CORE".equalsIgnoreCase(tc.bucket()) || "CORE".equalsIgnoreCase(tc.bucket())) {
                    coreTargetPct = tc.targetPct();
                    coreBandPct = tc.bandPct();
                    if (tc.preferredFunds() != null) {
                        for (BucketConfigLoader.PreferredFundConfig pfc : tc.preferredFunds()) {
                            if ("INF109KC12U0".equalsIgnoreCase(pfc.fundId()) || (pfc.fundName() != null && pfc.fundName().toUpperCase().contains("LARGEMIDCAP"))) {
                                targetLmRatio = pfc.allocationWeight();
                            }
                        }
                    }
                }
            }
        }

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

            // Compute core aggregate values
            BigDecimal largeMidVal = BigDecimal.ZERO;
            BigDecimal ppfcVal = BigDecimal.ZERO;
            if (openLots != null) {
                for (Lot lot : openLots) {
                    BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "RebalanceTriggerEvaluator");
                    BigDecimal lotVal = lot.remainingUnits().multiply(nav);

                    if ("INF109KC12U0".equalsIgnoreCase(lot.assetId()) || (lot.assetName() != null && lot.assetName().toUpperCase().contains("LARGEMIDCAP"))) {
                        largeMidVal = largeMidVal.add(lotVal);
                    } else if ("INF879O01027".equalsIgnoreCase(lot.assetId()) || (lot.assetName() != null && lot.assetName().toUpperCase().contains("PARAG PARIKH"))) {
                        ppfcVal = ppfcVal.add(lotVal);
                    }
                }
            }

            BigDecimal coreTotalVal = largeMidVal.add(ppfcVal);
            boolean coreAggregateBreached = false;
            BigDecimal effectiveActiveCorpus = activeCorpus.compareTo(BigDecimal.ZERO) > 0 ? activeCorpus : liveCorpus;
            if (coreTotalVal.compareTo(BigDecimal.ZERO) > 0 && effectiveActiveCorpus.compareTo(BigDecimal.ZERO) > 0) {
                double coreWeightPct = (coreTotalVal.doubleValue() / effectiveActiveCorpus.doubleValue()) * 100.0;
                double coreUpper = coreTargetPct + (3 * coreBandPct);
                double coreLower = Math.max(0.0, coreTargetPct - (3 * coreBandPct));
                if (coreWeightPct > coreUpper || coreWeightPct < coreLower) {
                    driftedBuckets.add("CORE_AGGREGATE_BREACH");
                    upperBreachedBuckets.add("CORE_AGGREGATE_BREACH");
                    coreAggregateBreached = true;
                }
                double largeMidRatio = largeMidVal.doubleValue() / coreTotalVal.doubleValue();
                double lmRatioUpper = targetLmRatio + PortfolioConstants.CORE_INTERNAL_RATIO_BAND;
                double lmRatioLower = targetLmRatio - PortfolioConstants.CORE_INTERNAL_RATIO_BAND;
                if (largeMidRatio > lmRatioUpper || largeMidRatio < lmRatioLower) {
                    boolean isV24OrLater = ver != null && ver.versionId() != null && (ver.versionId().startsWith("2.4") || ver.versionId().startsWith("v2.4") || ver.versionId().startsWith("3.0") || ver.versionId().startsWith("v3.0"));
                    if (!isV24OrLater) {
                        driftedBuckets.add("CORE_INTERNAL_CIRCUIT_BREAKER");
                        upperBreachedBuckets.add("CORE_INTERNAL_CIRCUIT_BREAKER");
                    }
                }
            }

            if (engineResult != null && engineResult.bucketStatuses() != null) {
                for (BucketEngine.BucketStatus bs : engineResult.bucketStatuses()) {
                    if (bs.bucket() == BucketEngine.Bucket.LEGACY_HOLDINGS) {
                        continue;
                    }
                    boolean isLiquid = (bs.bucket() == BucketEngine.Bucket.LIQUID_BUFFER || bs.bucket() == BucketEngine.Bucket.LIQUIDITY_ARBITRAGE);
                    if (isLiquid && ddPct >= 5.0) {
                        continue; // Hold zone or drawdown active: ignore liquid buffer drift
                    }

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
                    var spec = (v3Config != null && v3Config.buckets() != null) ? v3Config.buckets().get(bType) : null;
                    BigDecimal bandLower = spec != null ? spec.bandLowerPct() : new BigDecimal("5.00");
                    BigDecimal bandUpper = spec != null ? spec.bandUpperPct() : new BigDecimal("5.00");

                    if (drift.compareTo(bandUpper) > 0) {
                        upperBreachedBuckets.add(bs.bucket().name());
                        if (!driftedBuckets.contains(bs.bucket().name())) {
                            driftedBuckets.add(bs.bucket().name());
                        }
                    } else if (drift.compareTo(bandLower.negate()) < 0) {
                        lowerBreachedBuckets.add(bs.bucket().name());
                        if (!driftedBuckets.contains(bs.bucket().name())) {
                            driftedBuckets.add(bs.bucket().name());
                        }
                    }
                }
            }
        }

        // 5. Trigger Resolution Priority Order
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
        }

        // Priority 2: DRIFT (Upper-Band Breach Only, Self-Funded)
        if (!upperBreachedBuckets.isEmpty()) {
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
            if ("DRAWDOWN".equalsIgnoreCase(resolution.triggerType())) {
                String armed = resolution.drawdownContext() != null ? resolution.drawdownContext().armedTier() : null;
                if ("TIER_30".equals(armed)) drawdownTier = 30;
                else if ("TIER_20".equals(armed)) drawdownTier = 20;
                else if ("TIER_10".equals(armed)) drawdownTier = 10;
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
