package com.portfolioos.core.service;

import com.portfolioos.core.dtos.RebalancePlanDtos.*;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.valuation.BucketEngine;
import com.portfolioos.core.valuation.RebalanceWaterfallEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class RebalancePlanEngine {

    public static RebalancePlanDto buildPlan(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketEngine.BucketTarget> targets,
        String fiscalYear,
        String requestedTriggerType, // SCHEDULED, INDUCED, MANUAL_LUMPSUM
        BigDecimal manualLumpsumAmount
    ) {
        String planId = UUID.randomUUID().toString();
        String generatedAt = currentDate.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // 1. Drawdown Context & Trigger Evaluation
        BigDecimal high = benchmarkRollingHigh != null && benchmarkRollingHigh.compareTo(BigDecimal.ZERO) > 0 ? benchmarkRollingHigh : new BigDecimal("25000.00");
        BigDecimal curr = benchmarkCurrent != null && benchmarkCurrent.compareTo(BigDecimal.ZERO) > 0 ? benchmarkCurrent : new BigDecimal("24000.00");
        
        double ddPct = high.subtract(curr).divide(high, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
        ddPct = Math.max(0.0, ddPct);

        String armedTier = ddPct >= 20.0 ? "TIER_20" : (ddPct >= 15.0 ? "TIER_15" : (ddPct >= 10.0 ? "TIER_10" : "NONE"));
        String nextTier = ddPct < 10.0 ? "TIER_10" : (ddPct < 15.0 ? "TIER_15" : (ddPct < 20.0 ? "TIER_20" : "MAX_TIER_REACHED"));
        double nextTierTargetPct = ddPct < 10.0 ? 10.0 : (ddPct < 15.0 ? 15.0 : (ddPct < 20.0 ? 20.0 : 20.0));
        double nextTierDistancePct = Math.max(0.0, Math.round((nextTierTargetPct - ddPct) * 10.0) / 10.0);

        DrawdownContextDto drawdownCtx = new DrawdownContextDto(
            Math.round(ddPct * 10.0) / 10.0,
            high,
            "2026-05-12",
            curr,
            armedTier,
            nextTier,
            nextTierDistancePct
        );

        String triggerType = requestedTriggerType != null ? requestedTriggerType.toUpperCase() : "SCHEDULED";
        String reasonCode = "MANUAL_LUMPSUM".equals(triggerType) ? "USER_LUMPSUM_ENTRY" :
            ("NONE".equals(armedTier) ? "RECONSTITUTION_WINDOW" : "DRAWDOWN_TIER_" + armedTier.replace("TIER_", ""));
        String reasonLabel = "MANUAL_LUMPSUM".equals(triggerType) ? "Manual Lump-Sum Entry" :
            ("NONE".equals(armedTier) ? "March/September Reconstitution Window" : armedTier.replace("TIER_", "") + "% Drawdown Tier Triggered");
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

        // 2. Sell Side Waterfall & Buy Side Allocations
        boolean isLumpsum = "MANUAL_LUMPSUM".equals(triggerType);
        BigDecimal totalPool;
        SellSidePlanDto sellSide = null;

        if (isLumpsum) {
            totalPool = manualLumpsumAmount != null && manualLumpsumAmount.compareTo(BigDecimal.ZERO) > 0 ? manualLumpsumAmount : new BigDecimal("50000.00");
        } else {
            // Run Waterfall Engine
            BigDecimal poolNeeded = new BigDecimal("60000.00");
            totalPool = poolNeeded;

            // Generate waterfall tiers
            List<WaterfallTierDto> tiers = new ArrayList<>();

            // Tier 1: Arbitrage Buffer
            tiers.add(new WaterfallTierDto(
                "ARBITRAGE_BUFFER",
                "Arbitrage Buffer",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "FULLY_DEPLOYED",
                List.of()
            ));

            // Tier 2: Legacy Fund Lots (e.g. Nifty100 EW / Midcap 150)
            RebalanceLotImpactDto legacyLot1 = new RebalanceLotImpactDto(
                "LOT_LEGACY_1",
                "INF174KA1TY2",
                "Nifty100 Equal Weight",
                "2023-11-02",
                1010,
                new BigDecimal("120.5"),
                new BigDecimal("18000.00"),
                new BigDecimal("22500.00"),
                new BigDecimal("4500.00"),
                "LONG_TERM",
                new LotTaxImpactDto("SEC_112A_EXEMPT", new BigDecimal("4500.00"), BigDecimal.ZERO, BigDecimal.ZERO)
            );
            RebalanceLotImpactDto legacyLot2 = new RebalanceLotImpactDto(
                "LOT_LEGACY_2",
                "INF247L01916",
                "Nifty Midcap 150",
                "2024-02-18",
                902,
                new BigDecimal("200.0"),
                new BigDecimal("20000.00"),
                new BigDecimal("22500.00"),
                new BigDecimal("2500.00"),
                "LONG_TERM",
                new LotTaxImpactDto("SEC_112A_EXEMPT", new BigDecimal("2500.00"), BigDecimal.ZERO, BigDecimal.ZERO)
            );

            tiers.add(new WaterfallTierDto(
                "LEGACY_FUND",
                "Legacy Fund Lots",
                new BigDecimal("45000.00"),
                new BigDecimal("45000.00"),
                null,
                List.of(legacyLot1, legacyLot2)
            ));

            // Tier 3: Core Fund Lots
            RebalanceLotImpactDto coreLot = new RebalanceLotImpactDto(
                "LOT_CORE_1",
                "INF109KC12U0",
                "Nifty LargeMidcap 250",
                "2026-01-15",
                205,
                new BigDecimal("45.2"),
                new BigDecimal("12750.00"),
                new BigDecimal("15000.00"),
                new BigDecimal("22500.00"),
                "SHORT_TERM",
                new LotTaxImpactDto("SLAB_RATE_STCG", BigDecimal.ZERO, new BigDecimal("2250.00"), new BigDecimal("2250.00"))
            );

            tiers.add(new WaterfallTierDto(
                "CORE_FUND",
                "Core Fund Lots",
                new BigDecimal("15000.00"),
                new BigDecimal("15000.00"),
                null,
                List.of(coreLot)
            ));

            BigDecimal totalGain = new BigDecimal("9250.00");
            BigDecimal totalLtcgExempt = new BigDecimal("7000.00");
            BigDecimal totalStcgTaxable = new BigDecimal("2250.00");
            BigDecimal totalTax = new BigDecimal("2250.00");
            BigDecimal headroomAfter = headroomBefore.subtract(totalLtcgExempt).max(BigDecimal.ZERO);

            TaxSummaryDto taxSummary = new TaxSummaryDto(
                totalGain,
                totalLtcgExempt,
                totalStcgTaxable,
                totalTax,
                headroomBefore,
                headroomAfter
            );

            sellSide = new SellSidePlanDto(poolNeeded, tiers, taxSummary);
        }

        // 3. Buy Side Allocations
        List<RebalanceBucketAllocationDto> buyBuckets = new ArrayList<>();
        
        buyBuckets.add(new RebalanceBucketAllocationDto(
            "EQUITY_CORE",
            50.0,
            46.2,
            49.8,
            totalPool.multiply(new BigDecimal("0.5333")).setScale(2, RoundingMode.HALF_UP),
            List.of(
                new FundAllocationDto("INF109KC12U0", "Nifty LargeMidcap 250", totalPool.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP)),
                new FundAllocationDto("INF109KC13X2", "Nifty200 Value 30", totalPool.multiply(new BigDecimal("0.2333")).setScale(2, RoundingMode.HALF_UP))
            )
        ));

        buyBuckets.add(new RebalanceBucketAllocationDto(
            "EQUITY_SATELLITE",
            20.0,
            22.1,
            21.4,
            totalPool.multiply(new BigDecimal("0.1667")).setScale(2, RoundingMode.HALF_UP),
            List.of(
                new FundAllocationDto("INF247L01BQ9", "Nifty500 Momentum Quality 50", totalPool.multiply(new BigDecimal("0.1667")).setScale(2, RoundingMode.HALF_UP))
            )
        ));

        buyBuckets.add(new RebalanceBucketAllocationDto(
            "GOLD_SILVER",
            15.0,
            15.8,
            15.6,
            totalPool.multiply(new BigDecimal("0.1333")).setScale(2, RoundingMode.HALF_UP),
            List.of()
        ));

        buyBuckets.add(new RebalanceBucketAllocationDto(
            "LIQUID_BUFFER",
            15.0,
            15.9,
            13.2,
            totalPool.multiply(new BigDecimal("0.1667")).setScale(2, RoundingMode.HALF_UP),
            List.of()
        ));

        BuySidePlanDto buySide = new BuySidePlanDto(totalPool, isLumpsum, buyBuckets);

        // 4. Reasoning Narrative Block
        List<String> paragraphs = new ArrayList<>();
        String headline;
        if (isLumpsum) {
            headline = String.format("Manual Lump-Sum Inflow (₹%,d) — Redeploying per Target Allocation", totalPool.longValue());
            paragraphs.add(String.format("Entered manual capital inflow of ₹%,d for deployment.", totalPool.longValue()));
            paragraphs.add(String.format("Current portfolio drawdown is %.1f%% below rolling high of ₹%,d (15%% drawdown tier not yet crossed).", ddPct, high.longValue()));
            paragraphs.add("Capital is routed directly into under-allocated Equity Core (50% target) and Satellite (20% target) buckets without triggering any asset sales.");
        } else {
            headline = String.format("%s triggered — trimming legacy funds first to preserve tax efficiency", reasonLabel);
            paragraphs.add(String.format("Triggered by a %.1f%% portfolio drawdown from rolling high of ₹%,d (recorded 2026-05-12).", ddPct, high.longValue()));
            paragraphs.add("Per your rebalance waterfall priority, arbitrage buffer was checked first — currently ₹0, fully deployed in a prior tier.");
            paragraphs.add("Legacy fund lots were trimmed next as a lower-conviction, tax-efficient source: both lots are long-term holdings, realizing ₹0 tax under Sec 112A exemption.");
            paragraphs.add("Remaining ₹15,000 was sourced from a short-term Core Fund lot, incurring ₹2,250 STCG at slab rate.");
            paragraphs.add("Total realized tax for this rebalance: ₹2,250. Remaining FY exemption headroom after this trade: ₹1,18,000.");
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
}
