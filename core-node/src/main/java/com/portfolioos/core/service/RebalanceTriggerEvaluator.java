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

    public record TriggerResolution(
        String triggerType,            // DRAWDOWN, DRIFT, SCHEDULED, GOLD_FLOOR_BACKSTOP, NONE
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

    public TriggerResolution getCurrentStatus(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        BucketConfigLoader.BucketTargetVersion activeVersion,
        LocalDate currentDate
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
        BigDecimal high = (benchmarkRollingHigh != null && benchmarkRollingHigh.compareTo(BigDecimal.ZERO) > 0) ? benchmarkRollingHigh : BigDecimal.ZERO;
        BigDecimal curr = (benchmarkCurrent != null && benchmarkCurrent.compareTo(BigDecimal.ZERO) > 0) ? benchmarkCurrent : BigDecimal.ZERO;
        double ddPct = PortfolioConstants.calculateDrawdownPct(curr, high);

        if (curr.compareTo(BigDecimal.ZERO) <= 0 || high.compareTo(BigDecimal.ZERO) <= 0) {
            System.err.println("WARNING: Real Nifty benchmark market data unavailable (benchmarkCurrent/benchmarkRollingHigh is null). Drawdown Trigger Disarmed (0.00% DD).");
        }

        String armedTier = ddPct >= PortfolioConstants.DRAWDOWN_TIER_3_PCT ? "TIER_20"
            : (ddPct >= PortfolioConstants.DRAWDOWN_TIER_2_PCT ? "TIER_15"
            : (ddPct >= PortfolioConstants.DRAWDOWN_TIER_1_PCT ? "TIER_10" : "NONE"));

        String nextTier = ddPct < 10.0 ? "TIER_10" : (ddPct < 15.0 ? "TIER_15" : (ddPct < 20.0 ? "TIER_20" : "MAX_TIER_REACHED"));
        double nextTierTargetPct = ddPct < 10.0 ? 10.0 : (ddPct < 15.0 ? 15.0 : (ddPct < 20.0 ? 20.0 : 20.0));
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

        // 3. Query Cooldown & Gold Idle State from Repository (PURE READ)
        Optional<LocalDateTime> lastSellOpt = repository.getLastSellSideFiringDate();
        long daysSinceLastSell = lastSellOpt.map(dt -> ChronoUnit.DAYS.between(dt.toLocalDate(), today)).orElse(9999L);
        boolean sellCooldownActive = daysSinceLastSell < PortfolioConstants.REBALANCE_COOLDOWN_DAYS;

        // F8: Drawdown Rung Escalation vs Cooldown
        int currentRung = "TIER_20".equals(armedTier) ? 20 : ("TIER_15".equals(armedTier) ? 15 : ("TIER_10".equals(armedTier) ? 10 : 0));
        int maxFiredRungInCooldown = repository.getMaxFiredDrawdownTierWithinDays(today, (int) PortfolioConstants.REBALANCE_COOLDOWN_DAYS);
        boolean drawdownEscalated = currentRung > maxFiredRungInCooldown;

        Optional<LocalDateTime> lastGoldBuyOpt = repository.getLastGoldBuyDate();
        long monthsSinceLastGoldBuy = lastGoldBuyOpt.map(dt -> ChronoUnit.MONTHS.between(dt.toLocalDate(), today)).orElse(9999L);
        boolean goldIdleActive = monthsSinceLastGoldBuy >= PortfolioConstants.GOLD_FLOOR_IDLE_MONTHS;

        // 4. Canonical Bucket Drift Evaluation (matches Web App and BucketEngine)
        List<String> driftedBuckets = new ArrayList<>();

        // F11: Config-driven targets and bands from activeVersion
        BucketConfigLoader.BucketTargetVersion ver = activeVersion != null ? activeVersion : BucketConfigLoader.getActiveVersion(today);
        double coreTargetPct = 50.0;
        double coreBandPct = 5.0;
        double targetLmRatio = 0.60;
        double goldCurrentWeightPct = 0.0;
        double goldTargetWeightPct = 10.0;

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
                } else if ("GOLD_SILVER".equalsIgnoreCase(tc.bucket()) || "HEDGE_COMMODITY".equalsIgnoreCase(tc.bucket()) || "COMMODITY".equalsIgnoreCase(tc.bucket())) {
                    goldTargetWeightPct = tc.targetPct();
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
            BigDecimal effectiveCorpus = activeCorpus.compareTo(BigDecimal.ZERO) > 0 ? activeCorpus : liveCorpus;
            if (coreTotalVal.compareTo(BigDecimal.ZERO) > 0 && effectiveCorpus.compareTo(BigDecimal.ZERO) > 0) {
                double coreWeightPct = (coreTotalVal.doubleValue() / effectiveCorpus.doubleValue()) * 100.0;
                double coreUpper = coreTargetPct + (3 * coreBandPct);
                double coreLower = Math.max(0.0, coreTargetPct - (3 * coreBandPct));
                if (coreWeightPct > coreUpper || coreWeightPct < coreLower) {
                    driftedBuckets.add("CORE_AGGREGATE_BREACH");
                    coreAggregateBreached = true;
                }
                double largeMidRatio = largeMidVal.doubleValue() / coreTotalVal.doubleValue();
                double lmRatioUpper = targetLmRatio + PortfolioConstants.CORE_INTERNAL_RATIO_BAND;
                double lmRatioLower = targetLmRatio - PortfolioConstants.CORE_INTERNAL_RATIO_BAND;
                if (largeMidRatio > lmRatioUpper || largeMidRatio < lmRatioLower) {
                    driftedBuckets.add("CORE_INTERNAL_CIRCUIT_BREAKER");
                }
            }

            if (engineResult != null && engineResult.bucketStatuses() != null) {
                for (BucketEngine.BucketStatus bs : engineResult.bucketStatuses()) {
                    if (bs.bucket() == BucketEngine.Bucket.GOLD_SILVER || bs.bucket() == BucketEngine.Bucket.HEDGE_COMMODITY) {
                        goldCurrentWeightPct = bs.currentPct().doubleValue();
                        goldTargetWeightPct = bs.targetPct().doubleValue();
                    }
                    if (bs.isDrifted() && bs.bucket() != BucketEngine.Bucket.LEGACY_HOLDINGS) {
                        if (bs.bucket() == BucketEngine.Bucket.EQUITY_CORE) {
                            if (!coreAggregateBreached && !driftedBuckets.contains("EQUITY_CORE")) {
                                driftedBuckets.add("EQUITY_CORE");
                            }
                        } else {
                            if (!driftedBuckets.contains(bs.bucket().name())) {
                                driftedBuckets.add(bs.bucket().name());
                            }
                        }
                    }
                }
            }
        }

        // 5. Trigger Resolution Priority Order
        String triggerType = "NONE";
        String reasonCode = "NO_REBALANCE_REQUIRED";
        String reasonLabel = "Portfolio is balanced and within thresholds";
        boolean hasSellSide = false;
        boolean hasGoldBuy = false;
        boolean sellTriggerEvaluated = false;

        // Priority 1: DRAWDOWN
        if (!"NONE".equals(armedTier)) {
            sellTriggerEvaluated = true;
            if (sellCooldownActive && !drawdownEscalated) {
                reasonCode = "DRAWDOWN_BLOCKED_BY_COOLDOWN";
                reasonLabel = String.format("Drawdown tier %s crossed but sell rebalance is on 30-day cooldown (%d days since last sell)", armedTier, daysSinceLastSell);
            } else {
                triggerType = "DRAWDOWN";
                reasonCode = "DRAWDOWN_TIER_" + armedTier.replace("TIER_", "");
                reasonLabel = String.format("%s%% Portfolio Drawdown Tier Triggered", armedTier.replace("TIER_", ""));
                hasSellSide = true;
                hasGoldBuy = true;
            }
        }

        // Priority 2: DRIFT (if Drawdown was not evaluated)
        if (!sellTriggerEvaluated && !driftedBuckets.isEmpty()) {
            sellTriggerEvaluated = true;
            if (sellCooldownActive) {
                reasonCode = "DRIFT_BLOCKED_BY_COOLDOWN";
                reasonLabel = String.format("Bucket drift detected (%s) but sell rebalance is on 30-day cooldown (%d days since last sell)",
                    String.join(", ", driftedBuckets), daysSinceLastSell);
            } else {
                triggerType = "DRIFT";
                reasonCode = "DRIFT_THRESHOLD_EXCEEDED";
                reasonLabel = String.format("Bucket Allocation Drift Exceeded Threshold (%s)", String.join(", ", driftedBuckets));
                hasSellSide = true;
                hasGoldBuy = true;
            }
        }

        // Priority 3: SCHEDULED (March/September window, if Drawdown/Drift not evaluated)
        if (!sellTriggerEvaluated && (today.getMonthValue() == 3 || today.getMonthValue() == 9)) {
            sellTriggerEvaluated = true;
            if (sellCooldownActive) {
                reasonCode = "SCHEDULED_BLOCKED_BY_COOLDOWN";
                reasonLabel = String.format("Scheduled window active but sell rebalance is on 30-day cooldown (%d days since last sell)", daysSinceLastSell);
            } else {
                triggerType = "SCHEDULED";
                reasonCode = "SCHEDULED_RECONSTITUTION";
                reasonLabel = "March/September Scheduled Reconstitution Window";
                hasSellSide = true;
                hasGoldBuy = true;
            }
        }

        // Priority 4: GOLD_FLOOR_BACKSTOP (Buy-only, exempt from 30-day sell cooldown)
        if ("NONE".equals(triggerType)) {
            double goldUnderweightPts = goldTargetWeightPct - goldCurrentWeightPct;
            if (goldIdleActive && goldUnderweightPts >= PortfolioConstants.GOLD_FLOOR_UNDERWEIGHT_PTS) {
                triggerType = "GOLD_FLOOR_BACKSTOP";
                hasSellSide = false;
                hasGoldBuy = true;
                if (!sellTriggerEvaluated) {
                    reasonCode = "GOLD_FLOOR_BACKSTOP_TRIGGERED";
                    reasonLabel = String.format("Gold/Silver Floor Backstop Triggered (Idle %d months, %.1f pts underweight)",
                        monthsSinceLastGoldBuy > 9000 ? 6 : monthsSinceLastGoldBuy, goldUnderweightPts);
                } else {
                    // Preserve sell cooldown reasonCode (e.g. DRIFT_BLOCKED_BY_COOLDOWN) while noting the backstop
                    reasonLabel = String.format("%s; Buy-only Gold/Silver Floor Backstop Triggered (Idle %d months, %.1f pts underweight)",
                        reasonLabel, monthsSinceLastGoldBuy > 9000 ? 6 : monthsSinceLastGoldBuy, goldUnderweightPts);
                }
            }
        }

        return new TriggerResolution(
            triggerType,
            reasonCode,
            reasonLabel,
            hasSellSide,
            hasGoldBuy,
            sellCooldownActive,
            daysSinceLastSell,
            goldIdleActive,
            monthsSinceLastGoldBuy,
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
            LocalDate today = currentDate != null ? currentDate : LocalDate.now();
            int drawdownTier = 0;
            if ("DRAWDOWN".equalsIgnoreCase(resolution.triggerType())) {
                String armed = resolution.drawdownContext() != null ? resolution.drawdownContext().armedTier() : null;
                if ("TIER_20".equals(armed)) drawdownTier = 20;
                else if ("TIER_15".equals(armed)) drawdownTier = 15;
                else if ("TIER_10".equals(armed)) drawdownTier = 10;
            }
            repository.recordExecution(
                planId,
                resolution.triggerType(),
                resolution.reasonCode(),
                today.atStartOfDay(),
                resolution.hasSellSide(),
                resolution.hasGoldBuy(),
                "{\"driftedBuckets\":" + resolution.driftedBuckets() + "}",
                drawdownTier
            );
        }

        return resolution;
    }
}
