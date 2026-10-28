package com.portfolioos.core.valuation;

import com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.service.RebalancePlanEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScratchRedRunTest {

    @Test
    @DisplayName("F1: Scheme with older gain lot blocks cherry-picking newer LTCG loss lot under Section 45(2A) RTA FIFO")
    void testF1SchemeWithOlderGainBlocksNewerLossHarvest() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        // Scheme INF109KC12U0:
        // Lot 1 (Older, 700 days ago, LTCG): Cost = 10.00, NAV = 15.00 -> +5.00 Gain per unit
        Lot olderGainLot = new Lot("L1-GAIN", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(700), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10.00"), new BigDecimal("1000.00"), false, BigDecimal.ZERO);

        // Lot 2 (Newer, 400 days ago, LTCG loss): Cost = 18.00, NAV = 15.00 -> -3.00 Loss per unit
        Lot newerLossLot = new Lot("L2-LOSS", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(400), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("18.00"), new BigDecimal("1800.00"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("15.00")
        );

        // Trim 1,500 INR (100 units). Under RTA FIFO, the RTA will liquidate Lot 1 (the oldest lot).
        // LossHarvestTierStrategy must NOT select Lot 2 out of order ahead of Lot 1!
        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("1500.00"),
            List.of(olderGainLot, newerLossLot),
            navMap,
            new BigDecimal("125000.00"),
            false,
            today,
            "2026-27"
        );

        assertNotNull(result);
        assertEquals(1, result.steps().size(), "Only 1 lot should be redeemed for 1,500 proceeds");
        RebalanceWaterfallEngine.WaterfallStep step = result.steps().get(0);
        assertEquals("L1-GAIN", step.lotId(), "RTA FIFO must liquidate the oldest lot (L1-GAIN), not cherry-pick newer loss lot (L2-LOSS)");
        assertEquals(WaterfallTier.LTCG_WITHIN_EXEMPTION, step.tier(), "L1-GAIN must be redeemed under LTCG tier, not LOSS_HARVEST");
        assertEquals(new BigDecimal("500.00"), step.realizedGain(), "Realized gain must be +500.00 from L1-GAIN");
    }

    @Test
    @DisplayName("F1: Same-scheme LTCG lots must be consumed in strict chronological FIFO order, not sorted by gain per unit")
    void testF1SameSchemeLtcgLotsConsumedInFifoOrder() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        Lot olderLot = new Lot("L1-OLD", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(800), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10.00"), new BigDecimal("1000.00"), false, BigDecimal.ZERO);

        Lot newerLot = new Lot("L2-NEW", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(400), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("18.00"), new BigDecimal("1800.00"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("20.00")
        );

        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("1000.00"),
            List.of(olderLot, newerLot),
            navMap,
            new BigDecimal("125000.00"),
            false,
            today,
            "2026-27"
        );

        assertNotNull(result);
        assertEquals(1, result.steps().size());
        RebalanceWaterfallEngine.WaterfallStep step = result.steps().get(0);
        assertEquals("L1-OLD", step.lotId(), "RTA FIFO strictly consumes oldest lot L1-OLD first, ignoring intra-scheme gain ratios");
        assertEquals(new BigDecimal("50.0000"), step.unitsSold());
        assertEquals(new BigDecimal("1000.00"), step.proceeds());
    }

    @Test
    @DisplayName("F3: Active bucket with in-band drift (<= bandPct) must NOT be trimmed on non-drawdown trigger")
    void testF3SubBandExcessNotTrimmedWhenDriftWithinBand() {
        BigDecimal nav = new BigDecimal("100.00");
        LocalDate today = LocalDate.of(2026, 3, 15);

        Lot coreLot = new Lot("lot-core", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(400), new BigDecimal("820"), new BigDecimal("820"), new BigDecimal("50.00"), new BigDecimal("41000.00"), false, null);
        Lot goldLot = new Lot("lot-gold", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds",
            today.minusDays(400), new BigDecimal("180"), new BigDecimal("180"), new BigDecimal("50.00"), new BigDecimal("9000.00"), false, null);

        List<BucketEngine.BucketTarget> targets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("80.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("20.00"), new BigDecimal("5.00"))
        );

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", nav,
            "INF247L01BM8", nav
        );
        BigDecimal corpus = new BigDecimal("100000.00");

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            List.of(coreLot, goldLot), Collections.emptyList(), navMap, today,
            corpus, corpus, targets, "2026-27", null, null
        );

        assertNotNull(plan);
        assertNotNull(plan.sellSide());
        assertEquals(0, BigDecimal.ZERO.compareTo(plan.sellSide().totalRequired()),
            "Active bucket within drift band (+2.0% <= 5.0%) must NOT be trimmed on scheduled/drift evaluation");
    }

    @Test
    @DisplayName("F3: sellSideActiveCorpus excludes LEGACY_HOLDINGS for uniform target valuation parity with BucketEngine")
    void testF3ActiveCorpusDenominatorExcludesLegacyHoldings() {
        BigDecimal nav = new BigDecimal("100.00");
        LocalDate today = LocalDate.of(2026, 8, 26);

        Lot legacyLot = new Lot("lot-legacy", "INF247L01BQ9", "Motilal Oswal Nifty Microcap 250 Index Fund",
            today.minusDays(400), new BigDecimal("500"), new BigDecimal("500"), new BigDecimal("80.00"), new BigDecimal("40000.00"), false, null);

        Lot coreLot = new Lot("lot-core", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(400), new BigDecimal("500"), new BigDecimal("500"), new BigDecimal("80.00"), new BigDecimal("40000.00"), false, null);

        List<BucketEngine.BucketTarget> targets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("80.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("20.00"), new BigDecimal("5.00"))
        );

        Map<String, BigDecimal> navMap = Map.of(
            "INF247L01BQ9", nav,
            "INF109KC12U0", nav
        );
        BigDecimal corpus = new BigDecimal("100000.00");

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            List.of(legacyLot, coreLot), Collections.emptyList(), navMap, today,
            corpus, corpus, targets, "2026-27", null, null
        );

        assertNotNull(plan);
        assertNotNull(plan.sellSide());
        assertTrue(plan.sellSide().totalRequired().compareTo(new BigDecimal("50000.00")) > 0,
            "Core bucket should be trimmed for overweight drift in addition to legacy liquidation");
    }
}
