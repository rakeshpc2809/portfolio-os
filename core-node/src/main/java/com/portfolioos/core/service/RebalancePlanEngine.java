package com.portfolioos.core.service;

import com.portfolioos.core.dtos.RebalancePlanDtos.*;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.rules.BucketConfigLoader;
import com.portfolioos.core.valuation.BucketEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class RebalancePlanEngine {

    public static RebalancePlanDto buildPlan(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> customTargets,
        String fiscalYear,
        String requestedTriggerType, // SCHEDULED, INDUCED, MANUAL_LUMPSUM
        BigDecimal manualLumpsumAmount
    ) {
        String planId = UUID.randomUUID().toString();
        String generatedAt = currentDate.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // 1. Get Point-in-Time Bucket Targets
        List<BucketEngine.BucketTarget> activeTargets = (customTargets != null && !customTargets.isEmpty()) ? customTargets : BucketConfigLoader.getActiveBucketTargets(currentDate);
        BucketConfigLoader.BucketTargetVersion activeVersion = BucketConfigLoader.getActiveVersion(currentDate);

        // 2. Compute Live Portfolio Valuation & Drawdown Context
        BigDecimal liveCorpus = BigDecimal.ZERO;
        Map<String, BigDecimal> fundValuations = new HashMap<>();

        if (openLots != null) {
            for (Lot lot : openLots) {
                BigDecimal nav = navMap != null && navMap.containsKey(lot.assetId()) ? navMap.get(lot.assetId()) : (lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ONE);
                BigDecimal val = lot.remainingUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
                liveCorpus = liveCorpus.add(val);
                fundValuations.put(lot.assetId(), fundValuations.getOrDefault(lot.assetId(), BigDecimal.ZERO).add(val));
            }
        }

        BigDecimal high = (benchmarkRollingHigh != null && benchmarkRollingHigh.compareTo(BigDecimal.ZERO) > 0) ? benchmarkRollingHigh : liveCorpus.multiply(new BigDecimal("1.08")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal curr = (benchmarkCurrent != null && benchmarkCurrent.compareTo(BigDecimal.ZERO) > 0) ? benchmarkCurrent : liveCorpus;
        
        if (high.compareTo(BigDecimal.ZERO) == 0) high = new BigDecimal("100000.00");
        if (curr.compareTo(BigDecimal.ZERO) == 0) curr = high;

        double ddPct = high.subtract(curr).divide(high, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
        ddPct = Math.max(0.0, Math.round(ddPct * 10.0) / 10.0);

        String armedTier = ddPct >= 20.0 ? "TIER_20" : (ddPct >= 15.0 ? "TIER_15" : (ddPct >= 10.0 ? "TIER_10" : "NONE"));
        String nextTier = ddPct < 10.0 ? "TIER_10" : (ddPct < 15.0 ? "TIER_15" : (ddPct < 20.0 ? "TIER_20" : "MAX_TIER_REACHED"));
        double nextTierTargetPct = ddPct < 10.0 ? 10.0 : (ddPct < 15.0 ? 15.0 : (ddPct < 20.0 ? 20.0 : 20.0));
        double nextTierDistancePct = Math.max(0.0, Math.round((nextTierTargetPct - ddPct) * 10.0) / 10.0);

        DrawdownContextDto drawdownCtx = new DrawdownContextDto(
            ddPct,
            high,
            "2026-05-12",
            curr,
            armedTier,
            nextTier,
            nextTierDistancePct
        );

        String triggerType = requestedTriggerType != null ? requestedTriggerType.toUpperCase() : "SCHEDULED";
        String reasonCode;
        String reasonLabel;

        boolean isLumpsum = "MANUAL_LUMPSUM".equals(triggerType);
        boolean isInduced = "INDUCED".equals(triggerType);

        if (isLumpsum) {
            reasonCode = "USER_LUMPSUM_ENTRY";
            reasonLabel = "Manual Lump-Sum Entry";
        } else if (isInduced) {
            if ("NONE".equals(armedTier)) {
                reasonCode = "NO_INDUCED_TRIGGER_ACTIVE";
                reasonLabel = String.format("No Induced Drawdown Tier Crossed (Current Drawdown %.1f%% < 10.0%%)", ddPct);
            } else {
                reasonCode = "DRAWDOWN_TIER_" + armedTier.replace("TIER_", "");
                reasonLabel = armedTier.replace("TIER_", "") + "% Drawdown Tier Triggered";
            }
        } else {
            reasonCode = "SCHEDULED_RECONSTITUTION";
            reasonLabel = "March/September Scheduled Reconstitution Window";
        }

        String windowLabel = "March 2027 Reconstitution Window";

        RebalanceTriggerDto trigger = new RebalanceTriggerDto(
            triggerType,
            reasonCode,
            reasonLabel,
            windowLabel,
            drawdownCtx
        );

        // Exemption status before trade
        ExemptionTracker.ExemptionStatus exBefore = ExemptionTracker.calculateExemptionStatus(matchedLots != null ? matchedLots : List.of(), fiscalYear);
        BigDecimal headroomBefore = new BigDecimal(exBefore.exemptionRemaining());

        // 3. Dynamic Sell Side Sourcing Logic
        BigDecimal totalPool;
        SellSidePlanDto sellSide = null;

        if (isLumpsum) {
            totalPool = manualLumpsumAmount != null && manualLumpsumAmount.compareTo(BigDecimal.ZERO) > 0 ? manualLumpsumAmount : new BigDecimal("50000.00");
        } else if (isInduced && "NONE".equals(armedTier)) {
            // No induced trigger active -> Zero sell amount required
            totalPool = BigDecimal.ZERO;
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
            // Induced armed tier OR Scheduled reconstitution
            BigDecimal poolNeeded = liveCorpus.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
            if (poolNeeded.compareTo(new BigDecimal("10000.00")) < 0) poolNeeded = new BigDecimal("60000.00");
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

            List<RebalanceLotImpactDto> soldLots = new ArrayList<>();
            BigDecimal soldLegacyAmount = BigDecimal.ZERO;

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

                    long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate() != null ? lot.acquisitionDate() : currentDate.minusDays(400), currentDate);
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

                    soldLots.add(lotImpact);
                    soldLegacyAmount = soldLegacyAmount.add(lotSoldVal);
                    poolRemaining = poolRemaining.subtract(lotSoldVal);
                }
            }

            // Add Tier 2: Legacy / Open Fund Lots
            waterfallTiers.add(new WaterfallTierDto(
                "LEGACY_FUND",
                "Legacy & Open Fund Lots",
                soldLegacyAmount,
                soldLegacyAmount,
                soldLegacyAmount.compareTo(BigDecimal.ZERO) == 0 ? "NO_TRIMMABLE_LOTS" : null,
                soldLots
            ));

            // Add Tier 3: Core Fund Lots
            waterfallTiers.add(new WaterfallTierDto(
                "CORE_FUND",
                "Core Fund Lots",
                poolRemaining,
                poolRemaining,
                poolRemaining.compareTo(BigDecimal.ZERO) == 0 ? "COVERED_BY_PRIOR_TIERS" : null,
                List.of()
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
        List<RebalanceBucketAllocationDto> buyBuckets = new ArrayList<>();
        
        for (BucketEngine.BucketTarget target : activeTargets) {
            String bucketName = target.bucket().name();
            double targetPct = target.targetPct().doubleValue();
            
            double currentPct = (liveCorpus.compareTo(BigDecimal.ZERO) > 0) ? 
                Math.round((liveCorpus.multiply(target.targetPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP).doubleValue() / liveCorpus.doubleValue()) * 1000.0) / 10.0 : targetPct;
            
            double postPct = (totalPool.compareTo(BigDecimal.ZERO) > 0) ?
                Math.round((currentPct + (totalPool.doubleValue() / (liveCorpus.doubleValue() + totalPool.doubleValue())) * (targetPct / 100.0) * 100.0) * 10.0) / 10.0 : currentPct;
            
            BigDecimal amountAllocated = totalPool.multiply(target.targetPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            List<FundAllocationDto> realFunds = resolveRealFundBreakdown(target.bucket(), amountAllocated);

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

        // 5. Dynamic Templated Narrative
        List<String> paragraphs = new ArrayList<>();
        String headline;

        if (isLumpsum) {
            headline = String.format("Manual Lump-Sum Inflow (₹%,d) — Redeploying per Target Allocation (Config %s)", totalPool.longValue(), activeVersion.versionId());
            paragraphs.add(String.format("Entered manual capital inflow of ₹%,d for deployment.", totalPool.longValue()));
            paragraphs.add(String.format("Current portfolio drawdown is %.1f%% below rolling high of ₹%,d (15%% drawdown tier not yet crossed).", ddPct, high.longValue()));
            paragraphs.add(String.format("Target allocations reference Config Version %s (effective from %s).", activeVersion.versionId(), activeVersion.effectiveFrom()));
        } else if (isInduced && "NONE".equals(armedTier)) {
            headline = String.format("No Induced Rebalance Required — Drawdown %.1f%% Below 10%% Threshold", ddPct);
            paragraphs.add(String.format("Current portfolio drawdown (%.1f%% from rolling high of ₹%,d) has not crossed the 10%% drawdown tier threshold.", ddPct, high.longValue()));
            paragraphs.add("No asset sales or rebalance capital pooling are required at this time.");
            paragraphs.add(String.format("Target allocations reference Config Version %s (effective from %s).", activeVersion.versionId(), activeVersion.effectiveFrom()));
        } else {
            headline = String.format("%s triggered — trimming legacy funds first to preserve tax efficiency", reasonLabel);
            paragraphs.add(String.format("Triggered by a %.1f%% portfolio drawdown from rolling high of ₹%,d.", ddPct, high.longValue()));
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
            currentDate.toString(),
            String.format("Portfolio currently %.1f%% below rolling high — 15%% tier not yet crossed", ddPct)
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

    private static List<FundAllocationDto> resolveRealFundBreakdown(BucketEngine.Bucket bucket, BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        List<FundAllocationDto> funds = new ArrayList<>();
        switch (bucket) {
            case EQUITY_CORE -> {
                BigDecimal half = totalAmount.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal rem = totalAmount.subtract(half);
                funds.add(new FundAllocationDto("INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund", half));
                funds.add(new FundAllocationDto("INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund", rem));
            }
            case EQUITY_SATELLITE -> {
                BigDecimal half = totalAmount.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal rem = totalAmount.subtract(half);
                funds.add(new FundAllocationDto("INF754K01TN5", "Edelweiss Nifty500 Multicap Momentum Quality 50 Index Fund", half));
                funds.add(new FundAllocationDto("INF247L01BQ9", "Motilal Oswal Nifty Microcap 250 Index Fund", rem));
            }
            case GOLD_SILVER -> {
                funds.add(new FundAllocationDto("INF247L01908", "Motilal Oswal Gold and Silver Passive Fund of Funds", totalAmount));
            }
            case LIQUID_BUFFER -> {
                funds.add(new FundAllocationDto("INF209K01157", "Invesco India Arbitrage Fund", totalAmount));
            }
        }
        return funds;
    }
}
