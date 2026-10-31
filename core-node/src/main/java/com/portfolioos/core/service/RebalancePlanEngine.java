package com.portfolioos.core.service;

import com.portfolioos.core.fire.FireTracker;
import com.portfolioos.core.dtos.RebalancePlanDtos.*;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.matcher.FundTierClassifier;
import com.portfolioos.core.persistence.TriggerHistoryRepository;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.common.PortfolioConstants;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.rules.BucketConfigLoader;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;
import com.portfolioos.core.valuation.BucketEngine;
import com.portfolioos.core.valuation.FundTrendDampenerCalculator;
import com.portfolioos.core.valuation.GoldDampenerCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class RebalancePlanEngine {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RebalancePlanEngine.class);

    public record Section70NettingResult(
        BigDecimal netTaxableStcgEquity,
        BigDecimal netTaxableStcgSlab,
        BigDecimal ltcgExemptionUsed,
        BigDecimal netTaxableLtcgEquity,
        BigDecimal netTaxableLtcgNonEquity,
        BigDecimal totalNetTaxEstimate,
        BigDecimal remainingHeadroomAfter
    ) {}

    public static Section70NettingResult applySection70Netting(
        BigDecimal grossStcgEquity,
        BigDecimal grossStcgSlab,
        BigDecimal grossStcl,
        BigDecimal grossLtcgEquity,
        BigDecimal grossLtcgNonEquity,
        BigDecimal grossLtcl,
        BigDecimal availableExemptionHeadroom,
        TaxRulesConfig rules
    ) {
        // 1. STCL offsets highest-taxed STCG first (Slab 30%, then Equity STCG 20%)
        BigDecimal remStcl = grossStcl != null ? grossStcl.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal gStcgSlab = grossStcgSlab != null ? grossStcgSlab.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal offsetSlab = remStcl.min(gStcgSlab);
        BigDecimal netStcgSlab = gStcgSlab.subtract(offsetSlab);
        remStcl = remStcl.subtract(offsetSlab);

        BigDecimal gStcgEq = grossStcgEquity != null ? grossStcgEquity.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal offsetStcgEq = remStcl.min(gStcgEq);
        BigDecimal netStcgEquity = gStcgEq.subtract(offsetStcgEq);
        remStcl = remStcl.subtract(offsetStcgEq);

        // 2. LTCL + remaining STCL offset LTCG.
        // Statutory Loss Netting (ITA 2025 Sec 108 / ITA 1961 Sec 70):
        // Losses are set off against gross LTCG before the statutory Section 112A threshold is applied on remaining net gains.
        // Non-equity LTCG (12.5% without exemption under Sec 112) is offset first, followed by Equity LTCG.
        BigDecimal remLongTermOffset = (grossLtcl != null ? grossLtcl.max(BigDecimal.ZERO) : BigDecimal.ZERO).add(remStcl);
        BigDecimal gLtcgNonEq = grossLtcgNonEquity != null ? grossLtcgNonEquity.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal offsetNonEqLtcg = remLongTermOffset.min(gLtcgNonEq);
        BigDecimal netLtcgNonEquity = gLtcgNonEq.subtract(offsetNonEqLtcg);
        remLongTermOffset = remLongTermOffset.subtract(offsetNonEqLtcg);

        BigDecimal gLtcgEq = grossLtcgEquity != null ? grossLtcgEquity.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal netEquityLtcgBeforeExemption = gLtcgEq.subtract(remLongTermOffset).max(BigDecimal.ZERO);
        BigDecimal headroom = availableExemptionHeadroom != null ? availableExemptionHeadroom.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal exemptionUsed = netEquityLtcgBeforeExemption.min(headroom).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netTaxableLtcgEquity = netEquityLtcgBeforeExemption.subtract(exemptionUsed).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal headroomAfter = headroom.subtract(exemptionUsed).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal stcgEqTax = netStcgEquity.multiply(rules.equityStcgRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal stcgSlabTax = netStcgSlab.multiply(rules.slabRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ltcgEqTax = netTaxableLtcgEquity.multiply(rules.equityLtcgRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ltcgNonEqTax = netLtcgNonEquity.multiply(rules.goldInternationalLtcgRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalNetTax = stcgEqTax.add(stcgSlabTax).add(ltcgEqTax).add(ltcgNonEqTax).setScale(2, RoundingMode.HALF_UP);

        return new Section70NettingResult(
            netStcgEquity.setScale(2, RoundingMode.HALF_UP),
            netStcgSlab.setScale(2, RoundingMode.HALF_UP),
            exemptionUsed,
            netTaxableLtcgEquity,
            netLtcgNonEquity.setScale(2, RoundingMode.HALF_UP),
            totalNetTax,
            headroomAfter
        );
    }

    public static RebalancePlanDto confirmPlanExecution(RebalancePlanDto plan, TriggerHistoryRepository repository) {
        if (plan == null) {
            return null;
        }
        if (repository == null) {
            throw new IllegalStateException("TriggerHistoryRepository cannot be null when confirming plan execution");
        }
        String planId = plan.planId();
            String triggerType = plan.trigger() != null ? plan.trigger().type() : "SCHEDULED";
            String reasonCode = plan.trigger() != null ? plan.trigger().reasonCode() : "CONFIRMED_EXECUTION";
            boolean hasSellSide = plan.sellSide() != null && plan.sellSide().totalRequired() != null && plan.sellSide().totalRequired().compareTo(BigDecimal.ZERO) > 0;
            boolean hasGoldBuy = plan.buySide() != null && plan.buySide().buckets() != null && plan.buySide().buckets().stream().anyMatch(b -> (b.bucket().contains("GOLD") || b.bucket().contains("COMMODITY")) && b.amountAllocated() != null && b.amountAllocated().compareTo(BigDecimal.ZERO) > 0);
            int drawdownTier = 0;
            if ("DRAWDOWN".equalsIgnoreCase(triggerType) && plan.trigger() != null && plan.trigger().drawdownContext() != null) {
                String armed = plan.trigger().drawdownContext().armedTier();
                if ("TIER_20".equals(armed)) drawdownTier = 20;
                else if ("TIER_15".equals(armed)) drawdownTier = 15;
                else if ("TIER_10".equals(armed)) drawdownTier = 10;
            }
            String detailsJson = "";
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                detailsJson = mapper.writeValueAsString(plan);
            } catch (Exception e) {
                log.warn("Failed to serialize rebalance plan snapshot JSON for plan {}", planId, e);
            }
            repository.recordExecution(
                planId,
                triggerType,
                reasonCode,
                LocalDateTime.now(),
                hasSellSide,
                hasGoldBuy,
                detailsJson,
                drawdownTier
            );
            if ("SCHEDULED".equalsIgnoreCase(triggerType) || "SCHEDULED_MAINTENANCE".equalsIgnoreCase(triggerType)) {
                repository.recordScheduledRunExecution(LocalDateTime.now().getYear(), LocalDateTime.now().getMonthValue(), LocalDateTime.now());
            }
        return plan;
    }

    private static volatile RebalanceTriggerEvaluator defaultEvaluator;

    private static synchronized RebalanceTriggerEvaluator getDefaultEvaluator() {
        if (defaultEvaluator == null) {
            defaultEvaluator = new RebalanceTriggerEvaluator(new TriggerHistoryRepository());
        }
        return defaultEvaluator;
    }

    public static void setTriggerEvaluator(RebalanceTriggerEvaluator evaluator) {
        if (evaluator != null) {
            defaultEvaluator = evaluator;
        }
    }

    public static com.portfolioos.core.valuation.ConsolidationRebalanceEngine.ConsolidationPreviewResult calculateConsolidation(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        LocalDate asOfDate,
        BigDecimal remainingExemption,
        String fiscalYear
    ) {
        return com.portfolioos.core.valuation.ConsolidationRebalanceEngine.calculateConsolidation(openLots, navMap, asOfDate, remainingExemption, fiscalYear);
    }

    public static RebalancePlanDto buildPlan(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        String fiscalYear,
        String requestedTriggerType, // SCHEDULED, INDUCED, DRAWDOWN, DRIFT, MANUAL_LUMPSUM
        BigDecimal manualLumpsumAmount
    ) {
        return buildPlan(
            openLots, matchedLots, navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh,
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, false, getDefaultEvaluator()
        );
    }

    public static RebalancePlanDto buildPlan(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        String fiscalYear,
        String requestedTriggerType,
        BigDecimal manualLumpsumAmount,
        boolean includeRebalance
    ) {
        return buildPlan(
            openLots, matchedLots, navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh,
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, includeRebalance, getDefaultEvaluator()
        );
    }

    public static RebalancePlanDto buildPlan(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        String fiscalYear,
        String requestedTriggerType,
        BigDecimal manualLumpsumAmount,
        boolean includeRebalance,
        RebalanceTriggerEvaluator triggerEvaluator
    ) {
        return buildPlanInternal(
            openLots, matchedLots, navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh,
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, includeRebalance, triggerEvaluator, true
        );
    }

    public static RebalancePlanDto buildPlan(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        String fiscalYear,
        String requestedTriggerType,
        BigDecimal manualLumpsumAmount,
        RebalanceTriggerEvaluator triggerEvaluator
    ) {
        return buildPlanInternal(
            openLots, matchedLots, navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh,
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, false, triggerEvaluator, true
        );
    }

    public static RebalancePlanDto buildPreviewPlan(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        String fiscalYear,
        String requestedTriggerType,
        BigDecimal manualLumpsumAmount
    ) {
        return buildPreviewPlan(
            openLots, matchedLots, navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh,
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, false, getDefaultEvaluator()
        );
    }

    public static RebalancePlanDto buildPreviewPlan(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        String fiscalYear,
        String requestedTriggerType,
        BigDecimal manualLumpsumAmount,
        boolean includeRebalance
    ) {
        return buildPreviewPlan(
            openLots, matchedLots, navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh,
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, includeRebalance, getDefaultEvaluator()
        );
    }

    public static RebalancePlanDto buildPreviewPlan(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        String fiscalYear,
        String requestedTriggerType,
        BigDecimal manualLumpsumAmount,
        RebalanceTriggerEvaluator triggerEvaluator
    ) {
        return buildPlanInternal(
            openLots, matchedLots, navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh,
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, false, triggerEvaluator, false
        );
    }

    public static RebalancePlanDto buildPreviewPlan(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        String fiscalYear,
        String requestedTriggerType,
        BigDecimal manualLumpsumAmount,
        boolean includeRebalance,
        RebalanceTriggerEvaluator triggerEvaluator
    ) {
        return buildPlanInternal(
            openLots, matchedLots, navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh,
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, includeRebalance, triggerEvaluator, false
        );
    }

    private static RebalancePlanDto buildPlanInternal(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        String fiscalYear,
        String requestedTriggerType,
        BigDecimal manualLumpsumAmount,
        boolean includeRebalance,
        RebalanceTriggerEvaluator triggerEvaluator,
        boolean recordExecution
    ) {
        String planId = UUID.randomUUID().toString();
        LocalDate today = currentDate != null ? currentDate : LocalDate.now();
        String generatedAt = today.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // 1. Point-in-Time Bucket Targets
        List<BucketEngine.BucketTarget> baseTargets = (customTargets != null && !customTargets.isEmpty())
            ? customTargets : BucketConfigLoader.getActiveBucketTargets(today);
        BucketConfigLoader.BucketTargetVersion activeVersion = BucketConfigLoader.getActiveVersion(today);
        com.portfolioos.core.config.BucketTargetsConfig v24Config = BucketConfigLoader.loadV24Config();

        // 2. Portfolio Valuation
        BigDecimal liveCorpus = BigDecimal.ZERO;
        Map<String, BigDecimal> fundValuations = new HashMap<>();

        if (openLots != null) {
            for (Lot lot : openLots) {
                BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "RebalancePlanEngine.liveCorpus");
                BigDecimal val = lot.remainingUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
                liveCorpus = liveCorpus.add(val);
                fundValuations.put(lot.assetId(), fundValuations.getOrDefault(lot.assetId(), BigDecimal.ZERO).add(val));
            }
        }

        boolean isLumpsum = "MANUAL_LUMPSUM".equalsIgnoreCase(requestedTriggerType);

        RebalanceTriggerEvaluator evaluator = (triggerEvaluator != null) ? triggerEvaluator : getDefaultEvaluator();
        RebalanceTriggerEvaluator.TriggerResolution resolution;

        if (isLumpsum || !recordExecution) {
            // Read-only preview or manual lumpsum entry: zero side-effects on trigger history DB
            resolution = evaluator.getCurrentStatus(
                openLots, navMap, benchmarkCurrent, benchmarkRollingHigh, customTargets, activeVersion, today
            );
        } else {
            // Execution: evaluate and record trigger firing in trigger history DB
            resolution = evaluator.evaluateAndRecord(
                planId, openLots, navMap, benchmarkCurrent, benchmarkRollingHigh, customTargets, activeVersion, today
            );
        }

        String resolvedType = requestedTriggerType != null ? requestedTriggerType : (isLumpsum ? "MANUAL_LUMPSUM" : resolution.triggerType());
        String reasonCode = isLumpsum ? "USER_LUMPSUM_ENTRY" : resolution.reasonCode();
        String reasonLabel = isLumpsum ? "Manual Lump-Sum Entry" : resolution.reasonLabel();

        RebalanceTriggerDto trigger = new RebalanceTriggerDto(
            resolvedType,
            reasonCode,
            reasonLabel,
            "March/September Reconstitution Window",
            resolution.drawdownContext()
        );

        // Derive Dynamic Regime-Shifted Targets
        double ddPct = (resolution != null && resolution.drawdownContext() != null)
            ? resolution.drawdownContext().currentDrawdownPct() : 0.0;

        BigDecimal liquidCurVal = BigDecimal.ZERO;
        BigDecimal sellSideActiveCorpus = BigDecimal.ZERO;
        if (openLots != null) {
            for (Lot lot : openLots) {
                BucketEngine.Bucket b = BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName());
                BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "RebalancePlanEngine.activeCorpus");
                BigDecimal lotVal = lot.remainingUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
                if (b != BucketEngine.Bucket.LEGACY_HOLDINGS) {
                    sellSideActiveCorpus = sellSideActiveCorpus.add(lotVal);
                }
                BucketEngine.Bucket mappedB = resolveTargetBucket(b, baseTargets);
                if (mappedB == BucketEngine.Bucket.LIQUID_BUFFER || mappedB == BucketEngine.Bucket.LIQUIDITY_ARBITRAGE) {
                    liquidCurVal = liquidCurVal.add(lotVal);
                }
            }
        }
        BigDecimal currentLiquidPct = sellSideActiveCorpus.compareTo(BigDecimal.ZERO) > 0
            ? liquidCurVal.multiply(new BigDecimal("100")).divide(sellSideActiveCorpus, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        List<BucketEngine.BucketTarget> activeTargets = BucketEngine.calculateRegimeShiftedTargets(baseTargets, ddPct, currentLiquidPct);

        // Exemption status before trade
        ExemptionTracker.ExemptionStatus exBefore = ExemptionTracker.calculateExemptionStatus(matchedLots != null ? matchedLots : List.of(), fiscalYear);
        BigDecimal headroomBefore = new BigDecimal(exBefore.exemptionRemaining());

        // 3. Sell Side Sourcing Logic
        BigDecimal totalPool;
        SellSidePlanDto sellSide = null;
        BigDecimal soldArbitrageAmount = BigDecimal.ZERO;
        BigDecimal soldLegacyAmount = BigDecimal.ZERO;
        BigDecimal soldCoreAmount = BigDecimal.ZERO;
        Map<BucketEngine.Bucket, BigDecimal> bucketSoldAmounts = new HashMap<>();
        boolean isUrgent = PortfolioConstants.isUrgentRebalance(ddPct);
        List<Lot> workingLots = new ArrayList<>(openLots != null ? openLots : List.of());

        if (isLumpsum) {
            if (manualLumpsumAmount == null || manualLumpsumAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Lump-sum rebalance simulation requires a valid positive manualLumpsumAmount.");
            }
        }

        boolean hasSellSide = !"GOLD_FLOOR_BACKSTOP".equalsIgnoreCase(resolvedType) && (resolution.hasSellSide() ||
            (requestedTriggerType != null && ("DRIFT".equalsIgnoreCase(requestedTriggerType) || "SCHEDULED".equalsIgnoreCase(requestedTriggerType) || "DRAWDOWN".equalsIgnoreCase(requestedTriggerType))));

        if (isLumpsum && !includeRebalance) {
            totalPool = manualLumpsumAmount;
            sellSide = new SellSidePlanDto(
                BigDecimal.ZERO,
                List.of(
                    new WaterfallTierDto("ARBITRAGE_BUFFER", "Arbitrage Buffer", BigDecimal.ZERO, BigDecimal.ZERO, "NO_TRIGGER_ACTIVE", List.of()),
                    new WaterfallTierDto("LEGACY_FUND", "Legacy Fund Lots", BigDecimal.ZERO, BigDecimal.ZERO, "NO_TRIGGER_ACTIVE", List.of()),
                    new WaterfallTierDto("CORE_FUND", "Core Fund Lots", BigDecimal.ZERO, BigDecimal.ZERO, "NO_TRIGGER_ACTIVE", List.of())
                ),
                new TaxSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, headroomBefore, headroomBefore)
            );
        } else if (!isLumpsum && !hasSellSide) {
            if ("GOLD_FLOOR_BACKSTOP".equals(resolvedType)) {
                // Gold Floor Backstop top-up sizing (buy-only)
                double goldTargetPct = (v24Config != null && v24Config.buckets() != null && v24Config.buckets().containsKey(com.portfolioos.core.config.BucketTargetsConfig.BucketType.GOLD_SILVER))
                    ? v24Config.buckets().get(com.portfolioos.core.config.BucketTargetsConfig.BucketType.GOLD_SILVER).targetPct().doubleValue()
                    : 10.0;
                double goldCurrentPct = 0.0;
                BigDecimal goldVal = BigDecimal.ZERO;
                if (openLots != null) {
                    for (Lot lot : openLots) {
                        if ("GOLD_SILVER".equals(BucketConfigLoader.mapAssetToBucket(lot.assetId(), lot.assetName()))) {
                            BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "RebalancePlanEngine.goldVal");
                            goldVal = goldVal.add(lot.remainingUnits().multiply(nav));
                        }
                    }
                }
                BigDecimal corpusBasis = sellSideActiveCorpus.compareTo(BigDecimal.ZERO) > 0 ? sellSideActiveCorpus : liveCorpus;
                if (corpusBasis.compareTo(BigDecimal.ZERO) > 0) {
                    goldCurrentPct = (goldVal.doubleValue() / corpusBasis.doubleValue()) * 100.0;
                }
                totalPool = GoldDampenerCalculator.calculateSizedAllocation(
                    goldTargetPct, goldCurrentPct, 1.0, 1.0, corpusBasis, true
                );
            } else {
                totalPool = BigDecimal.ZERO;
            }
            sellSide = new SellSidePlanDto(
                BigDecimal.ZERO,
                List.of(
                    new WaterfallTierDto("ARBITRAGE_BUFFER", "Arbitrage Buffer", BigDecimal.ZERO, BigDecimal.ZERO, "NO_TRIGGER_ACTIVE", List.of()),
                    new WaterfallTierDto("LEGACY_FUND", "Legacy Fund Lots", BigDecimal.ZERO, BigDecimal.ZERO, "NO_TRIGGER_ACTIVE", List.of()),
                    new WaterfallTierDto("CORE_FUND", "Core Fund Lots", BigDecimal.ZERO, BigDecimal.ZERO, "NO_TRIGGER_ACTIVE", List.of())
                ),
                new TaxSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, headroomBefore, headroomBefore)
            );
        } else {
            // Sell-side trigger active OR (isLumpsum && includeRebalance == true)
            // Calculate true excess drift across over-allocated buckets
            BigDecimal poolNeeded = BigDecimal.ZERO;
            Map<BucketEngine.Bucket, BigDecimal> bucketTrims = new java.util.LinkedHashMap<>();
            BigDecimal totalDeficit = BigDecimal.ZERO;
            double maxAbsDrift = 0.0;

            if (activeTargets != null && sellSideActiveCorpus.compareTo(BigDecimal.ZERO) > 0) {
                for (BucketEngine.BucketTarget target : activeTargets) {
                    BigDecimal targetPct = target.targetPct();
                    BigDecimal bandPct = target.bandPct() != null ? target.bandPct() : new BigDecimal("5.0");
                    BigDecimal targetVal = sellSideActiveCorpus.multiply(targetPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                    BigDecimal curVal = BigDecimal.ZERO;
                    if (openLots != null) {
                        for (Lot lot : openLots) {
                            BucketEngine.Bucket b = BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName());
                            BucketEngine.Bucket mappedB = resolveTargetBucket(b, activeTargets);
                            if (target.bucket() == mappedB) {
                                BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "RebalancePlanEngine.excessVal");
                                curVal = curVal.add(lot.remainingUnits().multiply(nav));
                            }
                        }
                    }
                    if (curVal.compareTo(targetVal) > 0) {
                        BigDecimal excessVal = curVal.subtract(targetVal);
                        BigDecimal curWeightPct = curVal.multiply(new BigDecimal("100")).divide(sellSideActiveCorpus, 4, RoundingMode.HALF_UP);
                    BigDecimal driftPts = curWeightPct.subtract(targetPct);
                    maxAbsDrift = Math.max(maxAbsDrift, driftPts.abs().doubleValue());
                    boolean isLiquid = (target.bucket() == BucketEngine.Bucket.LIQUID_BUFFER || target.bucket() == BucketEngine.Bucket.LIQUIDITY_ARBITRAGE);
                    boolean isDrawdownLiquidDeploy = isLiquid && ("DRAWDOWN".equalsIgnoreCase(resolvedType) || ddPct >= 10.0);
                    // F3: Only trim an active bucket if it breached its drift band (or is a Drawdown Rung liquid buffer deployment)
                    if (!isDrawdownLiquidDeploy && driftPts.compareTo(bandPct) <= 0) {
                        continue;
                    }

                    BigDecimal dampenedTrim;
                    if (isDrawdownLiquidDeploy) {
                        // BYPASS FundTrendDampenerCalculator for LIQUID_BUFFER during DRAWDOWN triggers:
                        // Deploy the exact rule-based excess (25%, 50%, or 100% of buffer) with zero volatility dampening!
                        dampenedTrim = excessVal;
                    } else if (targetVal.compareTo(BigDecimal.ZERO) <= 0) {
                        dampenedTrim = excessVal;
                    } else {
                        dampenedTrim = FundTrendDampenerCalculator.calculateDampenedTrim(excessVal, targetVal.doubleValue());
                    }
                    if (dampenedTrim.compareTo(BigDecimal.ZERO) > 0) {
                        bucketTrims.put(target.bucket(), dampenedTrim);
                        poolNeeded = poolNeeded.add(dampenedTrim);
                    }
                } else if (curVal.compareTo(targetVal) < 0) {
                    BigDecimal curWeightPct = sellSideActiveCorpus.compareTo(BigDecimal.ZERO) > 0
                        ? curVal.multiply(new BigDecimal("100")).divide(sellSideActiveCorpus, 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                    BigDecimal driftPts = curWeightPct.subtract(targetPct);
                    maxAbsDrift = Math.max(maxAbsDrift, driftPts.abs().doubleValue());
                    totalDeficit = totalDeficit.add(targetVal.subtract(curVal));
                }
            }
        }

        // Step 0: Legacy holdings liquidation runs unconditionally before everything else.
        // All eligible LTCG and loss-making legacy lots are liquidated with no stalling at deficit/exemption limits.
        BigDecimal totalLegacyValue = BigDecimal.ZERO;
        if (openLots != null) {
            for (Lot lot : openLots) {
                BucketEngine.Bucket b = BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName());
                if (b == BucketEngine.Bucket.LEGACY_HOLDINGS) {
                    BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "RebalancePlanEngine.legacyVal");
                    totalLegacyValue = totalLegacyValue.add(lot.remainingUnits().multiply(nav));
                }
            }
        }
        Map<BucketEngine.Bucket, BigDecimal> orderedTrims = new java.util.LinkedHashMap<>();
        if (totalLegacyValue.compareTo(BigDecimal.ZERO) > 0) {
            orderedTrims.put(BucketEngine.Bucket.LEGACY_HOLDINGS, totalLegacyValue.setScale(2, RoundingMode.HALF_UP));
        }
        if (bucketTrims.containsKey(BucketEngine.Bucket.LIQUID_BUFFER)) {
            orderedTrims.put(BucketEngine.Bucket.LIQUID_BUFFER, bucketTrims.get(BucketEngine.Bucket.LIQUID_BUFFER));
        } else if (bucketTrims.containsKey(BucketEngine.Bucket.LIQUIDITY_ARBITRAGE)) {
            orderedTrims.put(BucketEngine.Bucket.LIQUIDITY_ARBITRAGE, bucketTrims.get(BucketEngine.Bucket.LIQUIDITY_ARBITRAGE));
        }
        for (Map.Entry<BucketEngine.Bucket, BigDecimal> entry : bucketTrims.entrySet()) {
            if (!orderedTrims.containsKey(entry.getKey())) {
                orderedTrims.put(entry.getKey(), entry.getValue());
            }
        }
        bucketTrims = orderedTrims;
        if (totalLegacyValue.compareTo(BigDecimal.ZERO) > 0) {
            poolNeeded = poolNeeded.add(totalLegacyValue.setScale(2, RoundingMode.HALF_UP));
        }

        if (isLumpsum) {
            totalPool = poolNeeded.add(manualLumpsumAmount);
        } else {
            totalPool = poolNeeded;
        }

        List<WaterfallTierDto> waterfallTiers = new ArrayList<>();
        BigDecimal poolRemaining = poolNeeded;

        double currentDd = (resolution != null && resolution.drawdownContext() != null)
            ? resolution.drawdownContext().currentDrawdownPct() : 0.0;
        isUrgent = PortfolioConstants.isUrgentRebalance(currentDd);

        List<com.portfolioos.core.valuation.RebalanceWaterfallEngine.WaterfallStep> allSteps = new ArrayList<>();
        BigDecimal totalTaxEstimate = BigDecimal.ZERO;
        BigDecimal currentHeadroom = headroomBefore;

        workingLots = new ArrayList<>(openLots != null ? openLots : List.of());

        for (Map.Entry<BucketEngine.Bucket, BigDecimal> entry : bucketTrims.entrySet()) {
            BucketEngine.Bucket b = entry.getKey();
            BigDecimal neededForBucket = entry.getValue();

            com.portfolioos.core.valuation.RebalanceWaterfallEngine.WaterfallResult waterfallResult =
                com.portfolioos.core.valuation.RebalanceWaterfallEngine.buildTrimWaterfall(
                    b,
                    neededForBucket,
                    workingLots,
                    navMap != null ? navMap : Map.of(),
                    currentHeadroom,
                    isUrgent,
                    today,
                    fiscalYear
                );

            if (waterfallResult.steps() != null) {
                allSteps.addAll(waterfallResult.steps());
                currentHeadroom = currentHeadroom.subtract(waterfallResult.ltcgExemptionConsumed()).max(BigDecimal.ZERO);
                totalTaxEstimate = totalTaxEstimate.add(waterfallResult.totalTaxDrag());

                for (com.portfolioos.core.valuation.RebalanceWaterfallEngine.WaterfallStep s : waterfallResult.steps()) {
                    for (int i = 0; i < workingLots.size(); i++) {
                        Lot l = workingLots.get(i);
                        if (l.lotId().equals(s.lotId())) {
                            BigDecimal newUnits = l.remainingUnits().subtract(s.unitsSold());
                            if (newUnits.compareTo(BigDecimal.ZERO) <= 0) {
                                workingLots.remove(i);
                            } else {
                                workingLots.set(i, l.withRemainingUnitsAndCost(
                                    newUnits, l.costPerUnit(),
                                    l.costPerUnit().multiply(newUnits).setScale(2, RoundingMode.HALF_UP)
                                ));
                            }
                            break;
                        }
                    }
                }
            }
        }

        BigDecimal totalGain = BigDecimal.ZERO;
        BigDecimal grossStcgEquity = BigDecimal.ZERO;
        BigDecimal grossStcgSlab = BigDecimal.ZERO;
        BigDecimal grossStcl = BigDecimal.ZERO;
        BigDecimal grossLtcgEquity = BigDecimal.ZERO;
        BigDecimal grossLtcgNonEquity = BigDecimal.ZERO;
        BigDecimal grossLtcl = BigDecimal.ZERO;

        List<RebalanceLotImpactDto> soldArbitrageLots = new ArrayList<>();
        soldArbitrageAmount = BigDecimal.ZERO;
        List<RebalanceLotImpactDto> soldLegacyLots = new ArrayList<>();
        soldLegacyAmount = BigDecimal.ZERO;
        List<RebalanceLotImpactDto> soldCoreLots = new ArrayList<>();
        soldCoreAmount = BigDecimal.ZERO;

        if (!allSteps.isEmpty()) {
            for (com.portfolioos.core.valuation.RebalanceWaterfallEngine.WaterfallStep step : allSteps) {
                Lot origLot = null;
                if (openLots != null) {
                    for (Lot l : openLots) {
                        if (l.lotId() != null && l.lotId().equals(step.lotId())) {
                            origLot = l;
                            break;
                        }
                    }
                }

                if (origLot == null || origLot.acquisitionDate() == null) {
                    throw new IllegalStateException("CRITICAL REBALANCE AUDIT FAILURE: Lot " + step.lotId() + " (" + step.assetId() + ") missing original lot metadata or acquisition date in open lots pool");
                }

                totalGain = totalGain.add(step.realizedGain());

                // Income-tax Act, 2025 Section 108 & 111 Statutory Loss Accumulation
                boolean isLongTerm = "LONG_TERM".equals(step.taxTerm());
                AssetCategory stepCat = TaxClassifier.detectCategory(step.assetId(), step.assetName());
                if (step.realizedGain().compareTo(BigDecimal.ZERO) < 0) {
                    if (isLongTerm) {
                        grossLtcl = grossLtcl.add(step.realizedGain().abs());
                    } else {
                        grossStcl = grossStcl.add(step.realizedGain().abs());
                    }
                } else {
                    if (isLongTerm) {
                        if (stepCat == AssetCategory.EQUITY) {
                            grossLtcgEquity = grossLtcgEquity.add(step.realizedGain());
                        } else {
                            grossLtcgNonEquity = grossLtcgNonEquity.add(step.realizedGain());
                        }
                    } else {
                        if (stepCat == AssetCategory.EQUITY) {
                            grossStcgEquity = grossStcgEquity.add(step.realizedGain());
                        } else {
                            grossStcgSlab = grossStcgSlab.add(step.realizedGain());
                        }
                    }
                }
            }
        }

        // Income-tax Act, 2025 Section 108 / ITA 1961 Section 70 Statutory Loss Netting
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
        Section70NettingResult nettingResult = applySection70Netting(
            grossStcgEquity,
            grossStcgSlab,
            grossStcl,
            grossLtcgEquity,
            grossLtcgNonEquity,
            grossLtcl,
            headroomBefore,
            rules
        );

        BigDecimal totalLtcgExempt = nettingResult.ltcgExemptionUsed();
        BigDecimal totalStcgTaxable = nettingResult.netTaxableStcgEquity().add(nettingResult.netTaxableStcgSlab());
        totalTaxEstimate = nettingResult.totalNetTaxEstimate();
        BigDecimal exemptionHeadroomAfter = nettingResult.remainingHeadroomAfter();

        // F5 Second Pass: Align DTO lot-level tax estimates with Section 70 statutory netting
        List<com.portfolioos.core.valuation.RebalanceWaterfallEngine.WaterfallStep> alignedSteps =
            com.portfolioos.core.valuation.RebalanceWaterfallEngine.prorateSection70Netting(allSteps, nettingResult, rules);

        if (!alignedSteps.isEmpty()) {
            for (com.portfolioos.core.valuation.RebalanceWaterfallEngine.WaterfallStep step : alignedSteps) {
                Lot origLot = null;
                if (openLots != null) {
                    for (Lot l : openLots) {
                        if (l.lotId() != null && l.lotId().equals(step.lotId())) {
                            origLot = l;
                            break;
                        }
                    }
                }

                long holdingDays = ChronoUnit.DAYS.between(origLot.acquisitionDate(), today);
                BigDecimal costBasis = step.proceeds().subtract(step.realizedGain()).max(BigDecimal.ZERO);

                // Read aligned tax metadata from WaterfallStep
                String regime = step.taxRegime();
                BigDecimal exempt = step.exemptionApplied();
                BigDecimal taxable = step.taxableAmount();

                RebalanceLotImpactDto lotImpact = new RebalanceLotImpactDto(
                    step.lotId(),
                    step.assetId(),
                    step.assetName(),
                    origLot.acquisitionDate().toString(),
                    holdingDays,
                    step.unitsSold(),
                    costBasis,
                    step.proceeds(),
                    step.realizedGain(),
                    step.taxTerm(),
                    new LotTaxImpactDto(regime, exempt, taxable, step.taxDrag(), step.lossOffsetApplied()),
                    step.lossOffsetApplied()
                );

                BucketEngine.Bucket rawBucket = origLot != null ? BucketEngine.classifyAssetToBucket(origLot.assetId(), origLot.assetName()) : null;
                BucketEngine.Bucket lotBucket = resolveTargetBucket(rawBucket, activeTargets);
                boolean isArbitrageLot = (lotBucket == BucketEngine.Bucket.LIQUID_BUFFER || lotBucket == BucketEngine.Bucket.LIQUIDITY_ARBITRAGE);

                if (isArbitrageLot) {
                    soldArbitrageLots.add(lotImpact);
                    soldArbitrageAmount = soldArbitrageAmount.add(step.proceeds());
                    bucketSoldAmounts.put(lotBucket, bucketSoldAmounts.getOrDefault(lotBucket, BigDecimal.ZERO).add(step.proceeds()));
                } else if (step.tier() == com.portfolioos.core.valuation.WaterfallTier.LEGACY_FUND) {
                    soldLegacyLots.add(lotImpact);
                    soldLegacyAmount = soldLegacyAmount.add(step.proceeds());
                } else {
                    soldCoreLots.add(lotImpact);
                    soldCoreAmount = soldCoreAmount.add(step.proceeds());
                    bucketSoldAmounts.put(lotBucket, bucketSoldAmounts.getOrDefault(lotBucket, BigDecimal.ZERO).add(step.proceeds()));
                }
            }
        }

        // Tier 1: Arbitrage Buffer
        waterfallTiers.add(new WaterfallTierDto(
            "ARBITRAGE_BUFFER",
            "Arbitrage Buffer",
            liquidCurVal,
            soldArbitrageAmount,
            soldArbitrageAmount.compareTo(BigDecimal.ZERO) == 0 ? (liquidCurVal.compareTo(BigDecimal.ZERO) == 0 ? "FULLY_DEPLOYED" : "NO_TRIGGER_ACTIVE") : null,
            soldArbitrageLots
        ));

        // Tier 2: Legacy Fund Lots
        waterfallTiers.add(new WaterfallTierDto(
            "LEGACY_FUND",
            "Legacy Fund Lots",
            totalLegacyValue,
            soldLegacyAmount,
            soldLegacyAmount.compareTo(BigDecimal.ZERO) == 0 ? "NO_TRIMMABLE_LOTS" : null,
            soldLegacyLots
        ));

        // Tier 3: Core Fund Lots
        waterfallTiers.add(new WaterfallTierDto(
            "CORE_FUND",
            "Core Fund Lots",
            soldCoreAmount,
            soldCoreAmount,
            soldCoreAmount.compareTo(BigDecimal.ZERO) == 0 ? ((soldArbitrageAmount.compareTo(BigDecimal.ZERO) > 0 || soldLegacyAmount.compareTo(BigDecimal.ZERO) > 0) ? "COVERED_BY_PRIOR_TIERS" : "NO_TRIMMABLE_LOTS") : null,
            soldCoreLots
        ));

        TaxSummaryDto taxSummary = new TaxSummaryDto(
            totalGain,
            totalLtcgExempt,
            totalStcgTaxable,
            totalTaxEstimate,
            headroomBefore,
            exemptionHeadroomAfter
        );

            BigDecimal actualSoldAmount = soldArbitrageAmount.add(soldLegacyAmount).add(soldCoreAmount);
            if (!isLumpsum) {
                totalPool = actualSoldAmount;
            } else {
                totalPool = actualSoldAmount.add(manualLumpsumAmount != null ? manualLumpsumAmount : BigDecimal.ZERO);
            }

            sellSide = new SellSidePlanDto(actualSoldAmount, waterfallTiers, taxSummary);
        }

        // 4. Dynamic Buy Side Allocations Resolving to REAL Portfolio Fund ISINs
        BigDecimal freshCash = isLumpsum ? (manualLumpsumAmount != null ? manualLumpsumAmount : BigDecimal.ZERO) : ((sellSide == null || sellSide.totalRequired() == null || sellSide.totalRequired().compareTo(BigDecimal.ZERO) == 0) ? totalPool : BigDecimal.ZERO);
        BigDecimal postCorpus = liveCorpus.add(freshCash);

        TriggerHistoryRepository historyRepo = triggerEvaluator != null ? triggerEvaluator.getRepository() : null;
        BucketEngine.RebalanceEngineResult bucketResult = BucketEngine.evaluateRebalance(
            openLots, matchedLots, navMap, today, benchmarkCurrent, benchmarkRollingHigh, baseTargets, fiscalYear, historyRepo
        );

        Map<BucketEngine.Bucket, BucketEngine.BucketStatus> statusMap = new HashMap<>();
        BigDecimal activeCorpus = BigDecimal.ZERO;
        if (bucketResult != null && bucketResult.bucketStatuses() != null) {
            for (BucketEngine.BucketStatus s : bucketResult.bucketStatuses()) {
                statusMap.put(s.bucket(), s);
                if (s.bucket() != BucketEngine.Bucket.LEGACY_HOLDINGS) {
                    activeCorpus = activeCorpus.add(s.currentValue());
                }
            }
        }
        BigDecimal newInflowsToActive = soldLegacyAmount.add(
            isLumpsum && manualLumpsumAmount != null ? manualLumpsumAmount : BigDecimal.ZERO
        );
        BigDecimal postActiveCorpus = activeCorpus.add(newInflowsToActive);

        Map<BucketEngine.Bucket, BigDecimal> bucketShortfalls = new HashMap<>();
        BigDecimal totalShortfall = BigDecimal.ZERO;

        for (BucketEngine.BucketTarget target : activeTargets) {
            BucketEngine.BucketStatus status = statusMap.get(target.bucket());
            BigDecimal curVal = status != null ? status.currentValue() : BigDecimal.ZERO;
            BigDecimal targetVal = postActiveCorpus.multiply(target.targetPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            BigDecimal shortfall = targetVal.subtract(curVal).max(BigDecimal.ZERO);
            bucketShortfalls.put(target.bucket(), shortfall);
            totalShortfall = totalShortfall.add(shortfall);
        }

        // Determine TriggerType for v2.4 water-filling routing
        com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType tType;
        if ("SCHEDULED".equalsIgnoreCase(resolvedType) || "SCHEDULED_MAINTENANCE".equalsIgnoreCase(resolvedType)) {
            tType = com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType.SCHEDULED_MAINTENANCE;
        } else if ("DRAWDOWN".equalsIgnoreCase(resolvedType)) {
            tType = com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType.DRAWDOWN;
        } else if ("DRIFT".equalsIgnoreCase(resolvedType)) {
            tType = com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType.DRIFT;
        } else if ("GOLD_FLOOR_BACKSTOP".equalsIgnoreCase(resolvedType)) {
            tType = com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType.GOLD_FLOOR_BACKSTOP;
        } else {
            tType = com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType.NONE;
        }

        boolean hasWaterfallBuckets = activeTargets != null && activeTargets.stream().anyMatch(t -> t.bucket() == BucketEngine.Bucket.GOLD_SILVER || t.bucket() == BucketEngine.Bucket.LIQUID_BUFFER);
        if (v24Config == null) {
            v24Config = BucketConfigLoader.loadV24Config();
        }
        com.portfolioos.core.valuation.RebalanceWaterfallEngine.RoutingResult routingResult = null;

        if (hasWaterfallBuckets && !isLumpsum) {
            BigDecimal coreTrimProceeds = bucketSoldAmounts.getOrDefault(BucketEngine.Bucket.EQUITY_CORE, BigDecimal.ZERO);
            BigDecimal generalPool = totalPool.subtract(coreTrimProceeds).max(BigDecimal.ZERO);
            BigDecimal effectiveActiveCorpus = postActiveCorpus.compareTo(BigDecimal.ZERO) > 0 ? postActiveCorpus : sellSideActiveCorpus;

            Map<com.portfolioos.core.config.BucketTargetsConfig.BucketType, BigDecimal> postSellValues =
                new java.util.EnumMap<>(com.portfolioos.core.config.BucketTargetsConfig.BucketType.class);
            Map<com.portfolioos.core.config.BucketTargetsConfig.BucketType, BigDecimal> targetWeightsPct =
                new java.util.EnumMap<>(com.portfolioos.core.config.BucketTargetsConfig.BucketType.class);

            for (BucketEngine.BucketTarget target : activeTargets) {
                com.portfolioos.core.config.BucketTargetsConfig.BucketType bType = mapToV24BucketType(target.bucket());
                BigDecimal curVal = statusMap.containsKey(target.bucket()) ? statusMap.get(target.bucket()).currentValue() : BigDecimal.ZERO;
                BigDecimal soldVal = bucketSoldAmounts.getOrDefault(target.bucket(), BigDecimal.ZERO);
                BigDecimal postVal = curVal.subtract(soldVal).max(BigDecimal.ZERO);
                postSellValues.put(bType, postSellValues.getOrDefault(bType, BigDecimal.ZERO).add(postVal));
                targetWeightsPct.put(bType, targetWeightsPct.getOrDefault(bType, BigDecimal.ZERO).add(target.targetPct()));
            }

            routingResult = com.portfolioos.core.valuation.RebalanceWaterfallEngine.routeProceedsV24(
                v24Config,
                tType,
                coreTrimProceeds,
                generalPool,
                effectiveActiveCorpus,
                postSellValues,
                targetWeightsPct
            );
        }

        List<RebalanceBucketAllocationDto> buyBuckets = new ArrayList<>();

        for (BucketEngine.BucketTarget target : activeTargets) {
            String bucketName = target.bucket().name();
            double targetPct = target.targetPct().doubleValue();

            BucketEngine.BucketStatus status = statusMap.get(target.bucket());
            BigDecimal curVal = status != null ? status.currentValue() : BigDecimal.ZERO;
            double currentPct = (status != null && status.currentPct() != null) ? status.currentPct().doubleValue() : targetPct;

            BigDecimal amountAllocated = BigDecimal.ZERO;
            BigDecimal shortfall = bucketShortfalls.getOrDefault(target.bucket(), BigDecimal.ZERO);

            if (routingResult != null) {
                com.portfolioos.core.config.BucketTargetsConfig.BucketType bType = mapToV24BucketType(target.bucket());
                amountAllocated = routingResult.bucketBuysInr().getOrDefault(bType, BigDecimal.ZERO);
            } else if ("GOLD_FLOOR_BACKSTOP".equals(resolvedType)) {
                if (target.bucket() == BucketEngine.Bucket.GOLD_SILVER) {
                    amountAllocated = totalPool;
                }
            } else if (totalShortfall.compareTo(BigDecimal.ZERO) > 0 && shortfall.compareTo(BigDecimal.ZERO) > 0) {
                amountAllocated = totalPool.multiply(shortfall).divide(totalShortfall, 2, RoundingMode.HALF_UP).min(shortfall);
            }

            BigDecimal soldFromBucket = bucketSoldAmounts.getOrDefault(target.bucket(), BigDecimal.ZERO);
            if (soldFromBucket.compareTo(curVal) > 0) {
                throw new IllegalStateException("RebalancePlanEngine: Trim proceeds for bucket " + target.bucket() +
                    " (" + soldFromBucket + ") exceed pre-rebalance valuation (" + curVal + ")");
            }
            BigDecimal postVal = curVal.subtract(soldFromBucket).add(amountAllocated);
            double postPct = (postActiveCorpus.compareTo(BigDecimal.ZERO) > 0) ?
                Math.round((postVal.doubleValue() / postActiveCorpus.doubleValue()) * 1000.0) / 10.0 : currentPct;

            List<FundAllocationDto> realFunds = resolveRealFundBreakdown(target.bucket(), amountAllocated, activeVersion, openLots, navMap, liveCorpus, postCorpus);

            buyBuckets.add(new RebalanceBucketAllocationDto(
                bucketName,
                targetPct,
                currentPct,
                postPct,
                amountAllocated,
                realFunds
            ));
        }

        BigDecimal cashOverflow = (routingResult != null) ? routingResult.cashOverflowInr() : BigDecimal.ZERO;
        BuySidePlanDto buySide = new BuySidePlanDto(totalPool, isLumpsum, buyBuckets, cashOverflow);

        // 5. Templated Narrative
        List<String> paragraphs = new ArrayList<>();
        if (benchmarkCurrent == null || benchmarkRollingHigh == null) {
            paragraphs.add("Notice: Drawdown protection is currently INACTIVE (no live benchmark index data source configured). Portfolio is operating under DRIFT & SCHEDULED rebalance rules.");
        }
        String headline;
        ddPct = resolution.drawdownContext().currentDrawdownPct();
        BigDecimal high = resolution.drawdownContext().rollingHighValue();

        ManualLumpsumMetaDto manualLumpsumMeta = null;
        if (isLumpsum) {
            String modeNote = includeRebalance ? "Manual Lump-Sum + Portfolio Rebalance" : "Manual Lump-Sum Only (No Sales)";
            manualLumpsumMeta = new ManualLumpsumMetaDto(manualLumpsumAmount, today.toString(), modeNote, includeRebalance);

            if (includeRebalance) {
                headline = String.format("Manual Lump-Sum (₹%,d) + Rebalance Liquidations — Combined Redeployment (Config %s)",
                    manualLumpsumAmount.longValue(), activeVersion.versionId());
                paragraphs.add(String.format("Entered manual lump-sum of ₹%,d combined with portfolio rebalance liquidations.", manualLumpsumAmount.longValue()));
            } else {
                headline = String.format("Manual Lump-Sum Inflow (₹%,d) — Redeploying per Target Allocation (Config %s)",
                    manualLumpsumAmount.longValue(), activeVersion.versionId());
                paragraphs.add(String.format("Entered manual capital inflow of ₹%,d for standalone deployment (no holdings sold).", manualLumpsumAmount.longValue()));
            }
            paragraphs.add(String.format("Current portfolio drawdown is %.1f%% below rolling high of ₹%,d.", ddPct, high.longValue()));
            paragraphs.add(String.format("Target allocations reference Config Version %s (effective from %s).", activeVersion.versionId(), activeVersion.effectiveFrom()));
        } else if (!resolution.hasSellSide()) {
            if ("GOLD_FLOOR_BACKSTOP".equals(resolvedType)) {
                headline = String.format("Gold Floor Backstop Triggered — Buy-Side Allocation of ₹%,d", totalPool.longValue());
                paragraphs.add("Gold/Silver bucket has been idle from buy allocations for 6+ months and is underweight target allocation.");
                paragraphs.add(String.format("Allocating ₹%,d top-up to close 50%% of remaining gap (exempt from sell cooldown).", totalPool.longValue()));
            } else {
                headline = String.format("No Rebalance Required — %s", resolution.reasonLabel());
                paragraphs.add(String.format("Current portfolio status: %s.", resolution.reasonLabel()));
                paragraphs.add("No asset sales or rebalance capital pooling are required at this time.");
            }
            paragraphs.add(String.format("Target allocations reference Config Version %s (effective from %s).", activeVersion.versionId(), activeVersion.effectiveFrom()));
        } else if ("DRAWDOWN".equalsIgnoreCase(resolvedType)) {
            headline = String.format("Drawdown Deployment Triggered — Deploying %s of Arbitrage Buffer into Equity Core",
                ddPct >= 20.0 ? "100%" : (ddPct >= 15.0 ? "50%" : "25%"));
            paragraphs.add(String.format("Triggered by %s (Nifty 500 drawdown at %.1f%% from 52-week peak).", reasonLabel, ddPct));
            double dynamicLiquidTargetPct = activeTargets.stream()
                .filter(t -> t.bucket() == BucketEngine.Bucket.LIQUID_BUFFER || t.bucket() == BucketEngine.Bucket.LIQUIDITY_ARBITRAGE)
                .findFirst().map(t -> t.targetPct().doubleValue()).orElse(0.0);
            paragraphs.add(String.format("Drawdown Rung active: target for Arbitrage Buffer shifted to %.1f%%, deploying ₹%,d into core equity.",
                dynamicLiquidTargetPct, soldArbitrageAmount.longValue()));
            if (sellSide != null && sellSide.taxSummary() != null) {
                TaxSummaryDto ts = sellSide.taxSummary();
                paragraphs.add(String.format("Redeeming dry powder lots realized ₹%,d total gain (₹%,d LTCG exempt under Sec 112A, ₹%,d STCG taxable).",
                    ts.totalRealizedGain().longValue(), ts.totalLtcgExempt().longValue(), ts.totalStcgTaxable().longValue()));
                paragraphs.add(String.format("Total estimated tax for this deployment: ₹%,d. Remaining FY exemption headroom: ₹%,d.",
                    ts.totalTaxEstimate().longValue(), ts.exemptionHeadroomAfter().longValue()));
            }
            paragraphs.add(String.format("Target allocations reference Config Version %s (effective from %s).", activeVersion.versionId(), activeVersion.effectiveFrom()));
        } else {
            headline = String.format("%s triggered — trimming legacy funds first to preserve tax efficiency", reasonLabel);
            paragraphs.add(String.format("Triggered by %s.", reasonLabel));
            paragraphs.add("Per your rebalance waterfall priority, arbitrage buffer was checked first (currently fully deployed).");
            if (sellSide != null && sellSide.taxSummary() != null) {
                TaxSummaryDto ts = sellSide.taxSummary();
                paragraphs.add(String.format("Trimming open lots realized ₹%,d total gain (₹%,d LTCG exempt under Sec 112A, ₹%,d STCG taxable).",
                    ts.totalRealizedGain().longValue(), ts.totalLtcgExempt().longValue(), ts.totalStcgTaxable().longValue()));
                paragraphs.add(String.format("Total estimated tax for this rebalance: ₹%,d. Remaining FY exemption headroom after trade: ₹%,d.",
                    ts.totalTaxEstimate().longValue(), ts.exemptionHeadroomAfter().longValue()));
            }
            paragraphs.add(String.format("Target allocations reference Config Version %s (effective from %s).", activeVersion.versionId(), activeVersion.effectiveFrom()));
        }

        if (cashOverflow.compareTo(BigDecimal.ZERO) > 0) {
            paragraphs.add(String.format("Cash overflow of ₹%,.2f held in unallocated reserve due to liquid buffer cap or minimum trade thresholds.", cashOverflow));
        }

        ReasoningNarrativeDto narrative = new ReasoningNarrativeDto(
            headline,
            paragraphs,
            "waterfall-v1"
        );

        ManualLumpsumMetaDto lumpsumMeta = isLumpsum ? new ManualLumpsumMetaDto(
            totalPool,
            today.toString(),
            String.format("Portfolio currently %.1f%% below rolling high", ddPct)
        ) : null;

        var goldSilverCtx = com.portfolioos.core.valuation.GoldSilverRatioCalculator.calculateRatio(navMap);
        var reconCtx = com.portfolioos.core.valuation.ReconstitutionCalendar.calculateReconstitutionStatus(today);
        BeerSpreadContextDto beerSpreadCtx = loadMarketIndicators();

        // 6-Invariant Runtime Firewall: Verify invariants before computing digest and releasing plan
        BigDecimal actualSoldAmount = (sellSide != null && sellSide.totalRequired() != null)
            ? sellSide.totalRequired() : BigDecimal.ZERO;
        if (hasWaterfallBuckets && !isLumpsum && v24Config != null && sellSide != null && buySide != null && (!buyBuckets.isEmpty() || actualSoldAmount.compareTo(BigDecimal.ZERO) > 0)) {
            com.portfolioos.core.valuation.RebalanceInvariantVerifier verifier = new com.portfolioos.core.valuation.RebalanceInvariantVerifier();

            List<com.portfolioos.core.valuation.RebalanceInvariantVerifier.LotOrder> soldLotOrders = new ArrayList<>();
            if (sellSide.waterfall() != null) {
                for (WaterfallTierDto t : sellSide.waterfall()) {
                    if (t.lots() != null) {
                        for (RebalanceLotImpactDto l : t.lots()) {
                            BigDecimal estTax = (l.taxImpact() != null && l.taxImpact().taxAmount() != null)
                                ? l.taxImpact().taxAmount() : BigDecimal.ZERO;
                            BigDecimal ltcgUsed = (l.taxImpact() != null && l.taxImpact().exemptionApplied() != null)
                                ? l.taxImpact().exemptionApplied() : BigDecimal.ZERO;
                            boolean withinExitLoad = l.holdingDays() < com.portfolioos.core.valuation.ExitLoadCalculator.getRequiredExitLoadDays(l.fundId(), l.fundName());
                            soldLotOrders.add(new com.portfolioos.core.valuation.RebalanceInvariantVerifier.LotOrder(
                                l.lotId(),
                                null,
                                l.fundId(),
                                l.acquisitionDate() != null ? LocalDate.parse(l.acquisitionDate()) : today,
                                l.saleProceeds(),
                                estTax,
                                ltcgUsed,
                                withinExitLoad
                            ));
                        }
                    }
                }
            }

            List<com.portfolioos.core.valuation.RebalanceInvariantVerifier.BuyOrder> buyOrders = new ArrayList<>();
            for (RebalanceBucketAllocationDto b : buyBuckets) {
                if (b.amountAllocated() == null || b.amountAllocated().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                try {
                    com.portfolioos.core.config.BucketTargetsConfig.BucketType bType =
                        mapToV24BucketType(BucketEngine.Bucket.valueOf(b.bucket()));
                    buyOrders.add(new com.portfolioos.core.valuation.RebalanceInvariantVerifier.BuyOrder(
                        bType,
                        b.bucket(),
                        b.amountAllocated()
                    ));
                } catch (Exception e) {
                    // ignore unmapped bucket
                }
            }

            Map<String, List<LocalDate>> remainingUnsoldLotDates = new HashMap<>();
            if (workingLots != null) {
                for (Lot wl : workingLots) {
                    String key = "DEFAULT::" + wl.assetId();
                    remainingUnsoldLotDates.computeIfAbsent(key, k -> new ArrayList<>()).add(wl.acquisitionDate());
                }
            }

            Map<com.portfolioos.core.config.BucketTargetsConfig.BucketType, BigDecimal> postTradeVals = new EnumMap<>(com.portfolioos.core.config.BucketTargetsConfig.BucketType.class);
            for (RebalanceBucketAllocationDto b : buyBuckets) {
                try {
                    com.portfolioos.core.config.BucketTargetsConfig.BucketType bType =
                        mapToV24BucketType(BucketEngine.Bucket.valueOf(b.bucket()));
                    BigDecimal curVal = statusMap.containsKey(BucketEngine.Bucket.valueOf(b.bucket()))
                        ? statusMap.get(BucketEngine.Bucket.valueOf(b.bucket())).currentValue() : BigDecimal.ZERO;
                    BigDecimal soldVal = bucketSoldAmounts.getOrDefault(BucketEngine.Bucket.valueOf(b.bucket()), BigDecimal.ZERO);
                    BigDecimal postVal = curVal.subtract(soldVal).add(b.amountAllocated());
                    postTradeVals.put(bType, postVal);
                } catch (Exception e) {}
            }

            List<com.portfolioos.core.valuation.RebalanceInvariantVerifier.PreTradeLotSnapshot> preTradeSnapshots = new ArrayList<>();
            if (openLots != null) {
                for (Lot ol : openLots) {
                    preTradeSnapshots.add(new com.portfolioos.core.valuation.RebalanceInvariantVerifier.PreTradeLotSnapshot(
                        ol.lotId(), null, ol.assetId(), ol.acquisitionDate()
                    ));
                }
            }

            BigDecimal summaryTax = (sellSide.taxSummary() != null && sellSide.taxSummary().totalTaxEstimate() != null)
                ? sellSide.taxSummary().totalTaxEstimate() : BigDecimal.ZERO;
            BigDecimal remainingExemption = (sellSide.taxSummary() != null && sellSide.taxSummary().exemptionHeadroomBefore() != null)
                ? sellSide.taxSummary().exemptionHeadroomBefore() : (headroomBefore != null ? headroomBefore : BigDecimal.ZERO);

            boolean coreTrimmed = bucketSoldAmounts.getOrDefault(BucketEngine.Bucket.EQUITY_CORE, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0;
            BigDecimal effectiveCorpus = postActiveCorpus.compareTo(BigDecimal.ZERO) > 0 ? postActiveCorpus : sellSideActiveCorpus;

            com.portfolioos.core.valuation.RebalanceInvariantVerifier.VerifiablePlan vPlan =
                new com.portfolioos.core.valuation.RebalanceInvariantVerifier.VerifiablePlan(
                    tType,
                    isUrgent,
                    coreTrimmed,
                    effectiveCorpus,
                    soldLotOrders,
                    remainingUnsoldLotDates,
                    buyOrders,
                    postTradeVals,
                    cashOverflow,
                    summaryTax,
                    remainingExemption
                );

            // FAIL CLOSED: Invariant violation aborts plan creation
            verifier.assertAllInvariants(vPlan, preTradeSnapshots, v24Config);
        }

        return new RebalancePlanDto(
            planId,
            generatedAt,
            trigger,
            sellSide,
            buySide,
            narrative,
            lumpsumMeta,
            goldSilverCtx,
            reconCtx,
            beerSpreadCtx
        );
    }

    @SuppressWarnings("unchecked")
    public static BeerSpreadContextDto loadMarketIndicators() {
        List<java.io.File> candidates = List.of(
            new java.io.File("data/market_indicators.json"),
            new java.io.File("../data/market_indicators.json"),
            new java.io.File("../../data/market_indicators.json"),
            new java.io.File("/app/data/market_indicators.json")
        );
        for (java.io.File f : candidates) {
            if (f.exists()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String, Object> map = mapper.readValue(f, Map.class);
                    double gsec = map.get("gsec_10y_yield_pct") != null ? Double.parseDouble(map.get("gsec_10y_yield_pct").toString()) : 7.10;
                    double pe = map.get("nifty50_pe") != null ? Double.parseDouble(map.get("nifty50_pe").toString()) : 22.40;
                    double ey = pe > 0 ? (100.0 / pe) : 0.0;
                    double beer = gsec - ey;
                    String asOf = String.valueOf(map.getOrDefault("as_of_date", LocalDate.now().toString()));
                    boolean isFb = Boolean.parseBoolean(String.valueOf(map.getOrDefault("is_fallback", "true")));
                    String src = String.valueOf(map.getOrDefault("source_status", "FALLBACK_CACHED"));
                    String zone = beer > 2.5 ? "OVERVALUED" : (beer < 1.0 ? "UNDERVALUED" : "FAIR");
                    return new BeerSpreadContextDto(gsec, pe, ey, beer, zone, asOf, isFb, src);
                } catch (Exception e) {
                    // fallback below
                }
            }
        }
        return new BeerSpreadContextDto(7.10, 22.40, 100.0 / 22.40, 7.10 - (100.0 / 22.40), "FAIR", LocalDate.now().toString(), true, "FALLBACK");
    }

    private static List<FundAllocationDto> resolveRealFundBreakdown(
        BucketEngine.Bucket bucket,
        BigDecimal totalAmount,
        BucketConfigLoader.BucketTargetVersion activeVersion,
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal preActiveCorpus,
        BigDecimal postActiveCorpus
    ) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        List<BucketConfigLoader.PreferredFundConfig> prefFunds = List.of();
        if (activeVersion != null && activeVersion.targets() != null) {
            for (BucketConfigLoader.BucketTargetConfig tc : activeVersion.targets()) {
                String tcB = tc.bucket().toUpperCase();
                if (bucket.name().equals(tcB) ||
                    (bucket == BucketEngine.Bucket.EQUITY_CORE && "CORE".equals(tcB)) ||
                    (bucket == BucketEngine.Bucket.GOLD_SILVER && "HEDGE_COMMODITY".equals(tcB)) ||
                    (bucket == BucketEngine.Bucket.LIQUID_BUFFER && "LIQUIDITY_ARBITRAGE".equals(tcB)) ||
                    (bucket == BucketEngine.Bucket.HEDGE_COMMODITY && "GOLD_SILVER".equals(tcB)) ||
                    (bucket == BucketEngine.Bucket.LIQUIDITY_ARBITRAGE && "LIQUID_BUFFER".equals(tcB))) {
                    prefFunds = tc.preferredFunds();
                    break;
                }
            }
        }

        if (prefFunds == null || prefFunds.isEmpty()) {
            prefFunds = BucketConfigLoader.getDefaultPreferredFundsForBucket(bucket.name());
        }

        // Map existing fund valuations by assetId and assetName
        Map<String, BigDecimal> fundValByAssetId = new HashMap<>();
        Map<String, BigDecimal> fundValByName = new HashMap<>();
        if (openLots != null) {
            for (Lot lot : openLots) {
                BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "fundPreVal");
                BigDecimal val = lot.remainingUnits().multiply(nav);
                if (lot.assetId() != null) {
                    String aId = lot.assetId().toLowerCase().trim();
                    fundValByAssetId.put(aId, fundValByAssetId.getOrDefault(aId, BigDecimal.ZERO).add(val));
                }
                if (lot.assetName() != null) {
                    String aName = lot.assetName().toLowerCase().trim();
                    fundValByName.put(aName, fundValByName.getOrDefault(aName, BigDecimal.ZERO).add(val));
                }
            }
        }

        List<FundAllocationDto> funds = new ArrayList<>();
        BigDecimal remaining = totalAmount;

        for (int i = 0; i < prefFunds.size(); i++) {
            BucketConfigLoader.PreferredFundConfig pf = prefFunds.get(i);
            BigDecimal alloc;
            if (i == prefFunds.size() - 1) {
                alloc = remaining;
            } else {
                alloc = totalAmount.multiply(BigDecimal.valueOf(pf.allocationWeight())).setScale(2, RoundingMode.HALF_UP);
                remaining = remaining.subtract(alloc);
            }

            BigDecimal preVal = BigDecimal.ZERO;
            if (pf.fundId() != null && fundValByAssetId.containsKey(pf.fundId().toLowerCase().trim())) {
                preVal = fundValByAssetId.get(pf.fundId().toLowerCase().trim());
            } else if (pf.fundName() != null && fundValByName.containsKey(pf.fundName().toLowerCase().trim())) {
                preVal = fundValByName.get(pf.fundName().toLowerCase().trim());
            }

            BigDecimal postVal = preVal.add(alloc);
            double currentPct = (preActiveCorpus != null && preActiveCorpus.compareTo(BigDecimal.ZERO) > 0)
                ? Math.round((preVal.doubleValue() / preActiveCorpus.doubleValue()) * 1000.0) / 10.0
                : 0.0;
            double postPct = (postActiveCorpus != null && postActiveCorpus.compareTo(BigDecimal.ZERO) > 0)
                ? Math.round((postVal.doubleValue() / postActiveCorpus.doubleValue()) * 1000.0) / 10.0
                : currentPct;

            funds.add(new FundAllocationDto(pf.fundId(), pf.fundName(), alloc, currentPct, postPct, preVal, postVal));
        }

        return funds;
    }

    static BucketEngine.Bucket resolveTargetBucket(BucketEngine.Bucket rawBucket, List<BucketEngine.BucketTarget> targets) {
        if (rawBucket == null || targets == null) return rawBucket;
        for (BucketEngine.BucketTarget t : targets) {
            if (t.bucket() == rawBucket) return rawBucket;
        }
        if (rawBucket == BucketEngine.Bucket.SATELLITE_VALUE ||
            rawBucket == BucketEngine.Bucket.SATELLITE_MOMENTUM ||
            rawBucket == BucketEngine.Bucket.SATELLITE_SMALLCAP) {
            for (BucketEngine.BucketTarget t : targets) {
                if (t.bucket() == BucketEngine.Bucket.EQUITY_SATELLITE) return BucketEngine.Bucket.EQUITY_SATELLITE;
            }
        }
        if (rawBucket == BucketEngine.Bucket.HEDGE_COMMODITY) {
            for (BucketEngine.BucketTarget t : targets) {
                if (t.bucket() == BucketEngine.Bucket.GOLD_SILVER) return BucketEngine.Bucket.GOLD_SILVER;
            }
        }
        if (rawBucket == BucketEngine.Bucket.LIQUIDITY_ARBITRAGE) {
            for (BucketEngine.BucketTarget t : targets) {
                if (t.bucket() == BucketEngine.Bucket.LIQUID_BUFFER) return BucketEngine.Bucket.LIQUID_BUFFER;
            }
        }
        return rawBucket;
    }

    private static com.portfolioos.core.config.BucketTargetsConfig.BucketType mapToV24BucketType(BucketEngine.Bucket b) {
        if (b == null) return com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER;
        return switch (b) {
            case EQUITY_CORE -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_CORE;
            case EQUITY_SATELLITE, SATELLITE_VALUE, SATELLITE_MOMENTUM, SATELLITE_SMALLCAP -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_SATELLITE;
            case GOLD_SILVER, HEDGE_COMMODITY -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.GOLD_SILVER;
            case LIQUID_BUFFER, LIQUIDITY_ARBITRAGE -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER;
            case LEGACY_HOLDINGS -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.LEGACY_HOLDINGS;
        };
    }

    /**
     * Core Lumpsum Allocation Parity Helper.
     * Computes lump-sum deployment amounts across portfolio buckets given an available cash corpus
     * and an existing RebalancePlanDto, implementing the identical 2-step priority contract as
     * mobile's LocalTradeSimulator.allocateLumpsum:
     * 1. Gold Floor Backstop: If trigger.type() is "GOLD_FLOOR_BACKSTOP", route up to 50% of the Gold
     *    bucket deficit first (min(remainingCash, goldTargetAmount * 0.50)).
     * 2. Drawdown Regime Exclusion & Proportional Allocation: If drawdownPct >= 10.0, exclude LIQUID_BUFFER
     *    and ARBITRAGE from fresh capital injection, distributing remaining cash proportionally to
     *    underweight bucket deficits (or target weights if none are underweight).
     *
     * @param availableCash Cash amount available for lump-sum deployment
     * @param rebalancePlan Existing rebalance plan containing target buckets and trigger/drawdown context
     * @return Map of bucket name to allocated lump-sum deployment amount
     */
    public static Map<String, BigDecimal> allocateLumpsum(BigDecimal availableCash, RebalancePlanDto rebalancePlan) {
        if (availableCash == null || availableCash.compareTo(BigDecimal.ZERO) <= 0 || rebalancePlan == null) {
            return Map.of();
        }
        BuySidePlanDto buySide = rebalancePlan.buySide();
        List<RebalanceBucketAllocationDto> buckets = buySide != null ? buySide.buckets() : null;
        if (buckets == null || buckets.isEmpty()) {
            return Map.of();
        }

        BigDecimal remainingCash = availableCash;
        Map<String, BigDecimal> allocations = new LinkedHashMap<>();

        String triggerType = rebalancePlan.trigger() != null ? rebalancePlan.trigger().type() : null;
        double drawdownPct = 0.0;
        if (rebalancePlan.trigger() != null && rebalancePlan.trigger().drawdownContext() != null) {
            drawdownPct = rebalancePlan.trigger().drawdownContext().currentDrawdownPct();
        }

        // 1. Gold Floor Backstop: When trigger is GOLD_FLOOR_BACKSTOP, route up to 50% of the Gold gap first
        if ("GOLD_FLOOR_BACKSTOP".equalsIgnoreCase(triggerType)) {
            RebalanceBucketAllocationDto goldBucket = null;
            for (RebalanceBucketAllocationDto b : buckets) {
                String nameUpper = b.bucket().toUpperCase();
                if (nameUpper.contains("GOLD") || nameUpper.contains("COMMODITY") || nameUpper.contains("HEDGE")) {
                    goldBucket = b;
                    break;
                }
            }
            if (goldBucket != null) {
                double goldGapPct = Math.max(0.0, goldBucket.targetPct() - goldBucket.currentPct());
                if (goldGapPct > 0.0) {
                    BigDecimal baseCorpus = (buySide.totalToInvest() != null && buySide.totalToInvest().compareTo(BigDecimal.ZERO) > 0)
                        ? buySide.totalToInvest()
                        : availableCash;
                    BigDecimal goldTargetAmount = baseCorpus.multiply(BigDecimal.valueOf(goldGapPct / 100.0));
                    BigDecimal halfGap = goldTargetAmount.multiply(BigDecimal.valueOf(0.50));
                    BigDecimal backstopFill = halfGap.compareTo(BigDecimal.ZERO) > 0
                        ? remainingCash.min(halfGap)
                        : remainingCash.min(availableCash.multiply(BigDecimal.valueOf(0.50)));
                    allocations.put(goldBucket.bucket(), backstopFill.setScale(2, RoundingMode.HALF_UP));
                    remainingCash = remainingCash.subtract(backstopFill);
                }
            }
        }

        if (remainingCash.compareTo(BigDecimal.ZERO) <= 0) {
            return allocations;
        }

        // 2. Drawdown Regime-Shifted Target Awareness:
        // During market crash (drawdown >= 10%), never route fresh lumpsum cash into LIQUID_BUFFER / ARBITRAGE!
        List<RebalanceBucketAllocationDto> effectiveBuckets = new ArrayList<>();
        for (RebalanceBucketAllocationDto b : buckets) {
            if (drawdownPct >= 10.0) {
                String nameUpper = b.bucket().toUpperCase();
                if (nameUpper.contains("LIQUID") || nameUpper.contains("ARBITRAGE")) {
                    continue;
                }
            }
            effectiveBuckets.add(b);
        }

        Map<String, Double> underweights = new LinkedHashMap<>();
        for (RebalanceBucketAllocationDto b : effectiveBuckets) {
            double deficit = b.targetPct() - b.currentPct();
            if (deficit > 0.0) {
                underweights.put(b.bucket(), deficit);
            }
        }

        if (underweights.isEmpty()) {
            double totalTarget = 0.0;
            for (RebalanceBucketAllocationDto b : effectiveBuckets) {
                totalTarget += b.targetPct();
            }
            if (totalTarget > 0.0) {
                for (RebalanceBucketAllocationDto b : effectiveBuckets) {
                    BigDecimal share = remainingCash.multiply(BigDecimal.valueOf(b.targetPct() / totalTarget));
                    BigDecimal prev = allocations.getOrDefault(b.bucket(), BigDecimal.ZERO);
                    allocations.put(b.bucket(), prev.add(share).setScale(2, RoundingMode.HALF_UP));
                }
            }
            return allocations;
        }

        double totalDeficit = 0.0;
        for (double deficit : underweights.values()) {
            totalDeficit += deficit;
        }

        for (Map.Entry<String, Double> entry : underweights.entrySet()) {
            String bucket = entry.getKey();
            double deficit = entry.getValue();
            BigDecimal share = remainingCash.multiply(BigDecimal.valueOf(deficit / totalDeficit));
            BigDecimal prev = allocations.getOrDefault(bucket, BigDecimal.ZERO);
            allocations.put(bucket, prev.add(share).setScale(2, RoundingMode.HALF_UP));
        }

        return allocations;
    }
}
