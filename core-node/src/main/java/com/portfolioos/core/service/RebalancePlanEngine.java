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
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, defaultEvaluator
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
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, triggerEvaluator, true
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
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, defaultEvaluator
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
            customTargets, fiscalYear, requestedTriggerType, manualLumpsumAmount, triggerEvaluator, false
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
            totalPool = manualLumpsumAmount;
        } else if (!resolution.hasSellSide()) {
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
            // Sell-side trigger active (DRAWDOWN, DRIFT, SCHEDULED)
            BigDecimal poolNeeded = liveCorpus.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal targetMonthlyExpense = FireTracker.calculateFireSummary(openLots, navMap, today).monthlyExpenseToday();
            if (poolNeeded.compareTo(new BigDecimal("10000.00")) < 0) poolNeeded = targetMonthlyExpense;
            totalPool = poolNeeded;

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

            // Sourcing from live open lots
            BigDecimal totalGain = BigDecimal.ZERO;
            BigDecimal totalLtcgExempt = BigDecimal.ZERO;
            BigDecimal totalStcgTaxable = BigDecimal.ZERO;
            BigDecimal totalTaxEstimate = BigDecimal.ZERO;
            BigDecimal currentHeadroom = headroomBefore;

            Set<String> activeAssetIds = FundTierClassifier.findActiveAssetIds(openLots, today);
            List<RebalanceLotImpactDto> soldLegacyLots = new ArrayList<>();
            List<RebalanceLotImpactDto> soldCoreLots = new ArrayList<>();
            BigDecimal soldLegacyAmount = BigDecimal.ZERO;
            BigDecimal soldCoreAmount = BigDecimal.ZERO;

            if (openLots != null) {
                for (Lot lot : openLots) {
                    if (poolRemaining.compareTo(BigDecimal.ZERO) <= 0) break;

                    BigDecimal nav = navMap != null && navMap.containsKey(lot.assetId()) ? navMap.get(lot.assetId()) : (lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ONE);
                    BigDecimal lotVal = lot.remainingUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
                    if (lotVal.compareTo(BigDecimal.ZERO) <= 0) continue;

                    BigDecimal lotSoldVal = lotVal.min(poolRemaining);
                    BigDecimal unitsSold = lotSoldVal.divide(nav, 4, RoundingMode.HALF_UP);
                    BigDecimal costBasis = unitsSold.multiply(lot.costPerUnit() != null ? lot.costPerUnit() : nav).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal gain = lotSoldVal.subtract(costBasis).max(BigDecimal.ZERO);

                    long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate() != null ? lot.acquisitionDate() : today.minusDays(400), today);
                    boolean isLongTerm = holdingDays > 365;
                    String taxTerm = isLongTerm ? "LONG_TERM" : "SHORT_TERM";

                    String regime;
                    BigDecimal exempt = BigDecimal.ZERO;
                    BigDecimal taxable = BigDecimal.ZERO;
                    BigDecimal tax = BigDecimal.ZERO;

                    if (isLongTerm) {
                        exempt = gain.min(currentHeadroom);
                        taxable = gain.subtract(exempt).max(BigDecimal.ZERO);
                        tax = taxable.multiply(new BigDecimal("0.125")).setScale(2, RoundingMode.HALF_UP);
                        regime = exempt.compareTo(BigDecimal.ZERO) > 0 && taxable.compareTo(BigDecimal.ZERO) == 0 ? "SEC_112A_EXEMPT" : "SEC_112A_TAXABLE_12_5";
                        currentHeadroom = currentHeadroom.subtract(exempt).max(BigDecimal.ZERO);
                        totalLtcgExempt = totalLtcgExempt.add(exempt);
                    } else {
                        taxable = gain;
                        tax = taxable.multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);
                        regime = "SLAB_RATE_STCG";
                        totalStcgTaxable = totalStcgTaxable.add(taxable);
                    }

                    totalGain = totalGain.add(gain);
                    totalTaxEstimate = totalTaxEstimate.add(tax);

                    RebalanceLotImpactDto lotImpact = new RebalanceLotImpactDto(
                        lot.lotId() != null ? lot.lotId() : UUID.randomUUID().toString(),
                        lot.assetId() != null ? lot.assetId() : "UNKNOWN",
                        lot.assetName() != null ? lot.assetName() : "Equity Fund",
                        lot.acquisitionDate() != null ? lot.acquisitionDate().toString() : "2024-01-01",
                        holdingDays,
                        unitsSold,
                        costBasis,
                        lotSoldVal,
                        gain,
                        taxTerm,
                        new LotTaxImpactDto(regime, exempt, taxable, tax)
                    );

                    boolean isLegacy = FundTierClassifier.isLegacyFund(lot.assetId(), activeAssetIds);
                    if (isLegacy) {
                        soldLegacyLots.add(lotImpact);
                        soldLegacyAmount = soldLegacyAmount.add(lotSoldVal);
                    } else {
                        soldCoreLots.add(lotImpact);
                        soldCoreAmount = soldCoreAmount.add(lotSoldVal);
                    }

                    poolRemaining = poolRemaining.subtract(lotSoldVal);
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
        BucketEngine.RebalanceEngineResult bucketResult = BucketEngine.evaluateRebalance(
            openLots, matchedLots, navMap, today, benchmarkCurrent, benchmarkRollingHigh, activeTargets, fiscalYear
        );

        Map<BucketEngine.Bucket, BucketEngine.BucketStatus> statusMap = new HashMap<>();
        if (bucketResult != null && bucketResult.bucketStatuses() != null) {
            for (BucketEngine.BucketStatus s : bucketResult.bucketStatuses()) {
                statusMap.put(s.bucket(), s);
            }
        }

        List<RebalanceBucketAllocationDto> buyBuckets = new ArrayList<>();

        for (BucketEngine.BucketTarget target : activeTargets) {
            String bucketName = target.bucket().name();
            double targetPct = target.targetPct().doubleValue();

            BucketEngine.BucketStatus status = statusMap.get(target.bucket());
            BigDecimal curVal = status != null ? status.currentValue() : BigDecimal.ZERO;
            double currentPct = status != null ? status.currentPct().doubleValue() : (liveCorpus.compareTo(BigDecimal.ZERO) > 0 ?
                Math.round((curVal.doubleValue() / liveCorpus.doubleValue()) * 1000.0) / 10.0 : targetPct);

            BigDecimal amountAllocated;
            if ("GOLD_FLOOR_BACKSTOP".equals(resolvedType)) {
                if (target.bucket() == BucketEngine.Bucket.GOLD_SILVER) {
                    amountAllocated = totalPool;
                } else {
                    amountAllocated = BigDecimal.ZERO;
                }
            } else if (target.bucket() == BucketEngine.Bucket.GOLD_SILVER && totalPool.compareTo(BigDecimal.ZERO) > 0) {
                // Apply GoldDampenerCalculator for Gold/Silver bucket using real NAV & 200-day MA deviation
                BigDecimal goldNav = BigDecimal.ZERO;
                if (openLots != null) {
                    for (Lot lot : openLots) {
                        if ("GOLD_SILVER".equals(BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName()))) {
                            goldNav = navMap != null && navMap.containsKey(lot.assetId()) ? navMap.get(lot.assetId()) : (lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ZERO);
                            if (goldNav.compareTo(BigDecimal.ZERO) > 0) break;
                        }
                    }
                }
                double currentPriceVal = goldNav.compareTo(BigDecimal.ZERO) > 0 ? goldNav.doubleValue() : (benchmarkCurrent != null ? benchmarkCurrent.doubleValue() : 1.0);
                double sma200Val = (benchmarkRollingHigh != null && benchmarkRollingHigh.compareTo(BigDecimal.ZERO) > 0) ? benchmarkRollingHigh.doubleValue() : currentPriceVal;
                double devPct = (sma200Val > 0.0) ? ((currentPriceVal - sma200Val) / sma200Val) * 100.0 : 0.0;

                GoldDampenerCalculator.DampenerMultipliers mults = GoldDampenerCalculator.calculateMultipliers(devPct);
                BigDecimal baseAlloc = totalPool.multiply(target.targetPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                amountAllocated = baseAlloc.multiply(BigDecimal.valueOf(mults.buyMultiplier())).setScale(2, RoundingMode.HALF_UP).min(totalPool);
            } else {
                amountAllocated = totalPool.multiply(target.targetPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            }

            BigDecimal postVal = curVal.add(amountAllocated);
            BigDecimal totalPostCorpus = liveCorpus.add(totalPool);

            double postPct = (totalPostCorpus.compareTo(BigDecimal.ZERO) > 0) ?
                Math.round((postVal.doubleValue() / totalPostCorpus.doubleValue()) * 1000.0) / 10.0 : currentPct;

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

        BuySidePlanDto buySide = new BuySidePlanDto(totalPool, isLumpsum, buyBuckets);

        // 5. Templated Narrative
        List<String> paragraphs = new ArrayList<>();
        String headline;
        double ddPct = resolution.drawdownContext().currentDrawdownPct();
        BigDecimal high = resolution.drawdownContext().rollingHighValue();

        if (isLumpsum) {
            headline = String.format("Manual Lump-Sum Inflow (₹%,d) — Redeploying per Target Allocation (Config %s)", totalPool.longValue(), activeVersion.versionId());
            paragraphs.add(String.format("Entered manual capital inflow of ₹%,d for deployment.", totalPool.longValue()));
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
                if (bucket.name().equals(tc.bucket())) {
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
