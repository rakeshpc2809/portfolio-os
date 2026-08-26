package com.portfolioos.core.service;

import com.portfolioos.core.fire.FireTracker;
import com.portfolioos.core.dtos.RebalancePlanDtos.*;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.matcher.FundTierClassifier;
import com.portfolioos.core.persistence.TriggerHistoryRepository;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.rules.BucketConfigLoader;
import com.portfolioos.core.valuation.BucketEngine;
import com.portfolioos.core.valuation.FundTrendDampenerCalculator;
import com.portfolioos.core.valuation.GoldDampenerCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class RebalancePlanEngine {

    private static RebalanceTriggerEvaluator defaultEvaluator = new RebalanceTriggerEvaluator(new TriggerHistoryRepository());

    public static void setTriggerEvaluator(RebalanceTriggerEvaluator evaluator) {
        if (evaluator != null) {
            defaultEvaluator = evaluator;
        }
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
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, false, defaultEvaluator
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
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, includeRebalance, defaultEvaluator
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
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, false, defaultEvaluator
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
        List<BucketEngine.BucketTarget> activeTargets = (customTargets != null && !customTargets.isEmpty())
            ? customTargets : BucketConfigLoader.getActiveBucketTargets(today);
        BucketConfigLoader.BucketTargetVersion activeVersion = BucketConfigLoader.getActiveVersion(today);

        // 2. Portfolio Valuation
        BigDecimal liveCorpus = BigDecimal.ZERO;
        Map<String, BigDecimal> fundValuations = new HashMap<>();

        if (openLots != null) {
            for (Lot lot : openLots) {
                BigDecimal nav = (navMap != null && navMap.containsKey(lot.assetId()))
                    ? navMap.get(lot.assetId()) : (lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ONE);
                BigDecimal val = lot.remainingUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
                liveCorpus = liveCorpus.add(val);
                fundValuations.put(lot.assetId(), fundValuations.getOrDefault(lot.assetId(), BigDecimal.ZERO).add(val));
            }
        }

        boolean isLumpsum = "MANUAL_LUMPSUM".equalsIgnoreCase(requestedTriggerType);

        RebalanceTriggerEvaluator evaluator = (triggerEvaluator != null) ? triggerEvaluator : defaultEvaluator;
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

        // Exemption status before trade
        ExemptionTracker.ExemptionStatus exBefore = ExemptionTracker.calculateExemptionStatus(matchedLots != null ? matchedLots : List.of(), fiscalYear);
        BigDecimal headroomBefore = new BigDecimal(exBefore.exemptionRemaining());

        // 3. Sell Side Sourcing Logic
        BigDecimal totalPool;
        SellSidePlanDto sellSide = null;

        if (isLumpsum) {
            if (manualLumpsumAmount == null || manualLumpsumAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Lump-sum rebalance simulation requires a valid positive manualLumpsumAmount.");
            }
        }

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
        } else if (!isLumpsum && !resolution.hasSellSide()) {
            if ("GOLD_FLOOR_BACKSTOP".equals(resolvedType)) {
                // Gold Floor Backstop top-up sizing (buy-only)
                double goldTargetPct = 15.0;
                double goldCurrentPct = 0.0;
                for (BucketConfigLoader.BucketTargetConfig tc : activeVersion.targets()) {
                    if ("GOLD_SILVER".equals(tc.bucket())) {
                        goldTargetPct = tc.targetPct();
                        break;
                    }
                }
                BigDecimal goldVal = BigDecimal.ZERO;
                for (Lot lot : openLots) {
                    if ("GOLD_SILVER".equals(BucketConfigLoader.mapAssetToBucket(lot.assetId(), lot.assetName()))) {
                        BigDecimal nav = navMap != null && navMap.containsKey(lot.assetId()) ? navMap.get(lot.assetId()) : (lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ONE);
                        goldVal = goldVal.add(lot.remainingUnits().multiply(nav));
                    }
                }
                if (liveCorpus.compareTo(BigDecimal.ZERO) > 0) {
                    goldCurrentPct = (goldVal.doubleValue() / liveCorpus.doubleValue()) * 100.0;
                }
                totalPool = GoldDampenerCalculator.calculateSizedAllocation(
                    goldTargetPct, goldCurrentPct, 1.0, 1.0, liveCorpus, true
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
            if (activeTargets != null) {
                for (BucketEngine.BucketTarget target : activeTargets) {
                    BigDecimal targetPct = target.targetPct();
                    BigDecimal targetVal = liveCorpus.multiply(targetPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                    BigDecimal curVal = BigDecimal.ZERO;
                    if (openLots != null) {
                        for (Lot lot : openLots) {
                            BucketEngine.Bucket b = BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName());
                            if (target.bucket() == b) {
                                BigDecimal nav = navMap != null && navMap.containsKey(lot.assetId()) ? navMap.get(lot.assetId()) : (lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ONE);
                                curVal = curVal.add(lot.remainingUnits().multiply(nav));
                            }
                        }
                    }
                    if (curVal.compareTo(targetVal) > 0) {
                        BigDecimal excessVal = curVal.subtract(targetVal);
                        BigDecimal dampenedTrim = FundTrendDampenerCalculator.calculateDampenedTrim(excessVal, targetVal.doubleValue());
                        poolNeeded = poolNeeded.add(dampenedTrim);
                    }
                }
            }

            if (poolNeeded.compareTo(BigDecimal.ZERO) == 0 && !isLumpsum) {
                BigDecimal targetMonthlyExpense = FireTracker.calculateFireSummary(openLots, navMap, today).monthlyExpenseToday();
                poolNeeded = targetMonthlyExpense;
            }

            if (isLumpsum) {
                totalPool = poolNeeded.add(manualLumpsumAmount);
            } else {
                totalPool = poolNeeded;
            }

            List<WaterfallTierDto> waterfallTiers = new ArrayList<>();
            BigDecimal poolRemaining = poolNeeded;

            // Tier 1: Arbitrage Buffer
            waterfallTiers.add(new WaterfallTierDto(
                "ARBITRAGE_BUFFER",
                "Arbitrage Buffer",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "FULLY_DEPLOYED",
                List.of()
            ));

            boolean isUrgent = false;
            if (resolution != null && resolution.drawdownContext() != null) {
                isUrgent = resolution.drawdownContext().currentDrawdownPct() >= 15.0;
            }

            com.portfolioos.core.valuation.RebalanceWaterfallEngine.WaterfallResult waterfallResult =
                com.portfolioos.core.valuation.RebalanceWaterfallEngine.buildTrimWaterfall(
                    BucketEngine.Bucket.EQUITY_CORE,
                    poolNeeded,
                    openLots != null ? openLots : List.of(),
                    navMap != null ? navMap : Map.of(),
                    headroomBefore,
                    isUrgent,
                    today,
                    fiscalYear
                );

            BigDecimal totalGain = BigDecimal.ZERO;
            BigDecimal totalLtcgExempt = BigDecimal.ZERO;
            BigDecimal totalStcgTaxable = BigDecimal.ZERO;
            BigDecimal totalTaxEstimate = waterfallResult.totalTaxDrag();
            BigDecimal currentHeadroom = headroomBefore;

            List<RebalanceLotImpactDto> soldLegacyLots = new ArrayList<>();
            List<RebalanceLotImpactDto> soldCoreLots = new ArrayList<>();
            BigDecimal soldLegacyAmount = BigDecimal.ZERO;
            BigDecimal soldCoreAmount = BigDecimal.ZERO;

            if (waterfallResult.steps() != null) {
                for (com.portfolioos.core.valuation.RebalanceWaterfallEngine.WaterfallStep step : waterfallResult.steps()) {
                    Lot origLot = null;
                    if (openLots != null) {
                        for (Lot l : openLots) {
                            if (l.lotId() != null && l.lotId().equals(step.lotId())) {
                                origLot = l;
                                break;
                            }
                        }
                    }

                    long holdingDays = (origLot != null && origLot.acquisitionDate() != null) ?
                        ChronoUnit.DAYS.between(origLot.acquisitionDate(), today) : 400L;

                    BigDecimal costBasis = step.proceeds().subtract(step.realizedGain()).max(BigDecimal.ZERO);
                    boolean isLongTerm = "LONG_TERM".equals(step.taxTerm());

                    BigDecimal exempt = BigDecimal.ZERO;
                    BigDecimal taxable = BigDecimal.ZERO;

                    if (isLongTerm) {
                        exempt = step.realizedGain().min(currentHeadroom);
                        taxable = step.realizedGain().subtract(exempt).max(BigDecimal.ZERO);
                        currentHeadroom = currentHeadroom.subtract(exempt).max(BigDecimal.ZERO);
                        totalLtcgExempt = totalLtcgExempt.add(exempt);
                    } else {
                        taxable = step.realizedGain();
                        totalStcgTaxable = totalStcgTaxable.add(taxable);
                    }

                    totalGain = totalGain.add(step.realizedGain());

                    String regime = isLongTerm ?
                        (exempt.compareTo(BigDecimal.ZERO) > 0 && taxable.compareTo(BigDecimal.ZERO) == 0 ? "SEC_112A_EXEMPT" : "SEC_112A_TAXABLE_12_5") :
                        "SLAB_RATE_STCG";

                    RebalanceLotImpactDto lotImpact = new RebalanceLotImpactDto(
                        step.lotId(),
                        step.assetId(),
                        step.assetName(),
                        (origLot != null && origLot.acquisitionDate() != null) ? origLot.acquisitionDate().toString() : today.toString(),
                        holdingDays,
                        step.unitsSold(),
                        costBasis,
                        step.proceeds(),
                        step.realizedGain(),
                        step.taxTerm(),
                        new LotTaxImpactDto(regime, exempt, taxable, step.taxDrag())
                    );

                    if (step.tier() == com.portfolioos.core.valuation.WaterfallTier.LEGACY_FUND) {
                        soldLegacyLots.add(lotImpact);
                        soldLegacyAmount = soldLegacyAmount.add(step.proceeds());
                    } else {
                        soldCoreLots.add(lotImpact);
                        soldCoreAmount = soldCoreAmount.add(step.proceeds());
                    }
                }
            }

            // Tier 2: Legacy Fund Lots
            waterfallTiers.add(new WaterfallTierDto(
                "LEGACY_FUND",
                "Legacy Fund Lots",
                soldLegacyAmount,
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
                soldCoreAmount.compareTo(BigDecimal.ZERO) == 0 ? (soldLegacyAmount.compareTo(BigDecimal.ZERO) > 0 ? "COVERED_BY_PRIOR_TIERS" : "NO_TRIMMABLE_LOTS") : null,
                soldCoreLots
            ));

            TaxSummaryDto taxSummary = new TaxSummaryDto(
                totalGain,
                totalLtcgExempt,
                totalStcgTaxable,
                totalTaxEstimate,
                headroomBefore,
                headroomBefore.subtract(totalLtcgExempt).max(BigDecimal.ZERO)
            );

            sellSide = new SellSidePlanDto(poolNeeded, waterfallTiers, taxSummary);
        }

        // 4. Dynamic Buy Side Allocations Resolving to REAL Portfolio Fund ISINs
        BigDecimal freshCash = isLumpsum ? (manualLumpsumAmount != null ? manualLumpsumAmount : BigDecimal.ZERO) : ((sellSide == null || sellSide.totalRequired() == null || sellSide.totalRequired().compareTo(BigDecimal.ZERO) == 0) ? totalPool : BigDecimal.ZERO);
        BigDecimal postCorpus = liveCorpus.add(freshCash);

        BucketEngine.RebalanceEngineResult bucketResult = BucketEngine.evaluateRebalance(
            openLots, matchedLots, navMap, today, benchmarkCurrent, benchmarkRollingHigh, activeTargets, fiscalYear
        );

        Map<BucketEngine.Bucket, BucketEngine.BucketStatus> statusMap = new HashMap<>();
        if (bucketResult != null && bucketResult.bucketStatuses() != null) {
            for (BucketEngine.BucketStatus s : bucketResult.bucketStatuses()) {
                statusMap.put(s.bucket(), s);
            }
        }

        Map<BucketEngine.Bucket, BigDecimal> bucketShortfalls = new HashMap<>();
        BigDecimal totalShortfall = BigDecimal.ZERO;

        for (BucketEngine.BucketTarget target : activeTargets) {
            BucketEngine.BucketStatus status = statusMap.get(target.bucket());
            BigDecimal curVal = status != null ? status.currentValue() : BigDecimal.ZERO;
            BigDecimal targetVal = postCorpus.multiply(target.targetPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            BigDecimal shortfall = targetVal.subtract(curVal).max(BigDecimal.ZERO);
            bucketShortfalls.put(target.bucket(), shortfall);
            totalShortfall = totalShortfall.add(shortfall);
        }

        List<RebalanceBucketAllocationDto> buyBuckets = new ArrayList<>();

        for (BucketEngine.BucketTarget target : activeTargets) {
            String bucketName = target.bucket().name();
            double targetPct = target.targetPct().doubleValue();

            BucketEngine.BucketStatus status = statusMap.get(target.bucket());
            BigDecimal curVal = status != null ? status.currentValue() : BigDecimal.ZERO;
            double currentPct = (liveCorpus.compareTo(BigDecimal.ZERO) > 0) ?
                Math.round((curVal.doubleValue() / liveCorpus.doubleValue()) * 1000.0) / 10.0 : targetPct;

            BigDecimal amountAllocated = BigDecimal.ZERO;
            BigDecimal shortfall = bucketShortfalls.getOrDefault(target.bucket(), BigDecimal.ZERO);

            if ("GOLD_FLOOR_BACKSTOP".equals(resolvedType)) {
                if (target.bucket() == BucketEngine.Bucket.GOLD_SILVER) {
                    amountAllocated = totalPool;
                }
            } else if (totalShortfall.compareTo(BigDecimal.ZERO) > 0 && shortfall.compareTo(BigDecimal.ZERO) > 0) {
                amountAllocated = totalPool.multiply(shortfall).divide(totalShortfall, 2, RoundingMode.HALF_UP).min(shortfall);
            }

            BigDecimal postVal = curVal.add(amountAllocated);
            double postPct = (postCorpus.compareTo(BigDecimal.ZERO) > 0) ?
                Math.round((postVal.doubleValue() / postCorpus.doubleValue()) * 1000.0) / 10.0 : currentPct;

            List<FundAllocationDto> realFunds = resolveRealFundBreakdown(target.bucket(), amountAllocated, activeVersion);

            buyBuckets.add(new RebalanceBucketAllocationDto(
                bucketName,
                targetPct,
                currentPct,
                postPct,
                amountAllocated,
                realFunds
            ));
        }

        // Budget Conservation Normalization: Ensure sum(amountAllocated) strictly equals totalPool
        BigDecimal rawSum = buyBuckets.stream().map(RebalanceBucketAllocationDto::amountAllocated).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (rawSum.compareTo(BigDecimal.ZERO) > 0 && totalPool.compareTo(BigDecimal.ZERO) > 0 && rawSum.compareTo(totalPool) != 0) {
            List<RebalanceBucketAllocationDto> normalizedBuckets = new ArrayList<>();
            BigDecimal runningAlloc = BigDecimal.ZERO;
            for (int i = 0; i < buyBuckets.size(); i++) {
                RebalanceBucketAllocationDto b = buyBuckets.get(i);
                BigDecimal normAlloc;
                if (i == buyBuckets.size() - 1) {
                    normAlloc = totalPool.subtract(runningAlloc).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                } else {
                    normAlloc = b.amountAllocated().multiply(totalPool).divide(rawSum, 2, RoundingMode.HALF_UP);
                    runningAlloc = runningAlloc.add(normAlloc);
                }
                List<FundAllocationDto> realFunds = resolveRealFundBreakdown(BucketEngine.Bucket.valueOf(b.bucket()), normAlloc, activeVersion);
                BigDecimal curVal = statusMap.containsKey(BucketEngine.Bucket.valueOf(b.bucket())) ?
                    statusMap.get(BucketEngine.Bucket.valueOf(b.bucket())).currentValue() : BigDecimal.ZERO;
                BigDecimal postVal = curVal.add(normAlloc);
                double postPct = (postCorpus.compareTo(BigDecimal.ZERO) > 0) ?
                    Math.round((postVal.doubleValue() / postCorpus.doubleValue()) * 1000.0) / 10.0 : b.targetPct();

                normalizedBuckets.add(new RebalanceBucketAllocationDto(
                    b.bucket(), b.targetPct(), b.currentPct(), postPct, normAlloc, realFunds
                ));
            }
            buyBuckets = normalizedBuckets;
        }

        BuySidePlanDto buySide = new BuySidePlanDto(totalPool, isLumpsum, buyBuckets);

        // 5. Templated Narrative
        List<String> paragraphs = new ArrayList<>();
        if (benchmarkCurrent == null || benchmarkRollingHigh == null) {
            paragraphs.add("Notice: Drawdown protection is currently INACTIVE (no live benchmark index data source configured). Portfolio is operating under DRIFT & SCHEDULED rebalance rules.");
        }
        String headline;
        double ddPct = resolution.drawdownContext().currentDrawdownPct();
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

        return new RebalancePlanDto(
            planId,
            generatedAt,
            trigger,
            sellSide,
            buySide,
            narrative,
            lumpsumMeta
        );
    }

    private static List<FundAllocationDto> resolveRealFundBreakdown(BucketEngine.Bucket bucket, BigDecimal totalAmount, BucketConfigLoader.BucketTargetVersion activeVersion) {
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
            funds.add(new FundAllocationDto(pf.fundId(), pf.fundName(), alloc));
        }

        return funds;
    }
}
