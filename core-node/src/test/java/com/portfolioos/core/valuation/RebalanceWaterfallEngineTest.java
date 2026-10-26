package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RebalanceWaterfallEngineTest {

    @Test
    void testLegacyFundPriorityDynamicInactiveSip() {
        LocalDate today = LocalDate.of(2026, 8, 1);
        LocalDate acqRecent = LocalDate.of(2026, 7, 15); // Active fund: purchase within 3 months
        LocalDate acqOld = LocalDate.of(2024, 1, 1);     // Inactive/Legacy fund: no purchase in last 3 months

        // Core active fund lot (purchased 17 days ago)
        Lot coreActiveLot = new Lot("L1", "NIFTY_LARGEMIDCAP_250", "Nifty LargeMidcap 250 Index Fund",
            acqRecent, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, BigDecimal.ZERO);

        // Inactive fund lot (no purchase in last 3 months)
        Lot inactiveLegacyLot = new Lot("L2", "OLD_FUND_XYZ", "Old Phased Out Fund",
            acqOld, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "NIFTY_LARGEMIDCAP_250", new BigDecimal("150"),
            "OLD_FUND_XYZ", new BigDecimal("150")
        );

        // Trim 5,000 INR
        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("5000"),
            List.of(coreActiveLot, inactiveLegacyLot),
            navMap,
            new BigDecimal("125000"),
            false,
            today,
            "2026-27"
        );

        assertNotNull(result);
        assertEquals(new BigDecimal("5000.00"), result.satisfiedAmount());
        assertEquals(new BigDecimal("0.00"), result.deferredAmount());
        assertFalse(result.steps().isEmpty());

        // First step must be Tier 1 (LEGACY_FUND) for the inactive fund
        RebalanceWaterfallEngine.WaterfallStep firstStep = result.steps().get(0);
        assertEquals(WaterfallTier.LEGACY_FUND, firstStep.tier());
        assertEquals("OLD_FUND_XYZ", firstStep.assetId());
    }

    @Test
    void testStcgDeferralWhenNotUrgent() {
        LocalDate today = LocalDate.of(2026, 8, 1);
        LocalDate acqRecent = LocalDate.of(2026, 7, 1); // 1 month old -> STCG

        Lot recentLot = new Lot("L1", "NIFTY_LARGEMIDCAP_250", "Nifty LargeMidcap 250 Index Fund",
            acqRecent, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "NIFTY_LARGEMIDCAP_250", new BigDecimal("150")
        );

        // Trim 5,000 INR, urgent = false
        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("5000"),
            List.of(recentLot),
            navMap,
            new BigDecimal("125000"),
            false,
            today,
            "2026-27"
        );

        assertNotNull(result);
        assertEquals(new BigDecimal("0.00"), result.satisfiedAmount());
        assertEquals(new BigDecimal("5000.00"), result.deferredAmount());
        assertTrue(result.steps().isEmpty());
        assertNotNull(result.deferralReason());
    }

    @Test
    void testStcgExecutionWhenUrgent() {
        LocalDate today = LocalDate.of(2026, 8, 1);
        LocalDate acqRecent = LocalDate.of(2026, 7, 1); // 1 month old -> STCG

        Lot recentLot = new Lot("L1", "NIFTY_LARGEMIDCAP_250", "Nifty LargeMidcap 250 Index Fund",
            acqRecent, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "NIFTY_LARGEMIDCAP_250", new BigDecimal("150")
        );

        // Trim 5,000 INR, urgent = true
        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("5000"),
            List.of(recentLot),
            navMap,
            new BigDecimal("125000"),
            true,
            today,
            "2026-27"
        );

        assertNotNull(result);
        assertEquals(new BigDecimal("5000.00"), result.satisfiedAmount(), "Under DRAWDOWN / urgent trigger, STCG realization IS allowed with tax drag explicitly calculated");
        assertEquals(new BigDecimal("0.00"), result.deferredAmount());
        assertFalse(result.steps().isEmpty());
        assertEquals(WaterfallTier.STCG_URGENT_ONLY, result.steps().get(0).tier());
        assertEquals("SHORT_TERM", result.steps().get(0).taxTerm());
        assertTrue(result.totalTaxDrag().compareTo(BigDecimal.ZERO) > 0, "STCG tax drag must be strictly greater than 0 under DRAWDOWN trigger");
        assertEquals(new BigDecimal("333.33"), result.totalTaxDrag(), "STCG tax drag must equal 20% of realized gain (333.33)");
    }

    @Test
    void testStcgExcludedWhenNotUrgent() {
        LocalDate today = LocalDate.of(2026, 8, 1);
        LocalDate acqRecent = LocalDate.of(2026, 7, 1); // 1 month old -> STCG

        Lot recentLot = new Lot("L1", "NIFTY_LARGEMIDCAP_250", "Nifty LargeMidcap 250 Index Fund",
            acqRecent, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of("NIFTY_LARGEMIDCAP_250", new BigDecimal("150"));

        // Trim 5,000 INR, urgent = false (routine DRIFT trigger)
        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("5000"),
            List.of(recentLot),
            navMap,
            new BigDecimal("125000"),
            false,
            today,
            "2026-27"
        );

        assertNotNull(result);
        assertEquals(new BigDecimal("0.00"), result.satisfiedAmount(), "STCG lots must be 100% excluded under routine DRIFT (urgent=false)");
        assertEquals(new BigDecimal("5000.00"), result.deferredAmount());
        assertTrue(result.steps().isEmpty());
    }

    @Test
    void testBuildTrimWaterfallThrowsOnMissingNav() {
        LocalDate today = LocalDate.of(2026, 8, 1);
        Lot lot = new Lot("L1", "NIFTY_LARGEMIDCAP_250", "Nifty LargeMidcap 250 Index Fund",
            today, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> emptyNavMap = Map.of();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            RebalanceWaterfallEngine.buildTrimWaterfall(
                BucketEngine.Bucket.EQUITY_CORE,
                new BigDecimal("5000"),
                List.of(lot),
                emptyNavMap,
                new BigDecimal("125000"),
                false,
                today,
                "2026-27"
            )
        );
        assertTrue(ex.getMessage().contains("CRITICAL VALUATION ERROR"));
        assertTrue(ex.getMessage().contains("NIFTY_LARGEMIDCAP_250"));
    }

    @Test
    void testResolveLargeMidcapTargetWeight_ReadsFromYamlConfig() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        BigDecimal targetWeight = RebalanceWaterfallEngine.resolveLargeMidcapTargetWeight(today);
        assertNotNull(targetWeight);
        assertEquals(new BigDecimal("0.6000"), targetWeight, "Must dynamically resolve 60.00% sub-weight for LargeMidcap 250 from v2.3 YAML config");
    }

    @Test
    void testFilterOverweightCoreLots_LargeMidOverweight_TrimsLargeMidOnly() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        // LargeMid 250: 700 remainingUnits * 100 = 70,000 INR (70% of 100,000 total Core) -> Overweight vs 60%
        Lot lmLot = new Lot("L1", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusMonths(6), new BigDecimal("700"), new BigDecimal("700"), new BigDecimal("100"), new BigDecimal("70000"), false, BigDecimal.ZERO);

        // PPFC: 300 remainingUnits * 100 = 30,000 INR (30% of 100,000 total Core) -> Underweight vs 40% (Shielded)
        Lot ppfcLot = new Lot("L2", "INF879O01027", "Parag Parikh Flexi Cap Fund",
            today.minusMonths(6), new BigDecimal("300"), new BigDecimal("300"), new BigDecimal("100"), new BigDecimal("30000"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("100"),
            "INF879O01027", new BigDecimal("100")
        );

        List<Lot> eligibleLots = RebalanceWaterfallEngine.filterOverweightCoreLots(List.of(lmLot, ppfcLot), navMap, today);
        assertEquals(1, eligibleLots.size());
        assertEquals("INF109KC12U0", eligibleLots.get(0).assetId(), "Only overweight LargeMidcap 250 lot must be returned for trimming, PPFC must be shielded");
    }

    @Test
    void testFilterOverweightCoreLots_PpfcOverweight_TrimsPpfcOnly() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        // LargeMid 250: 500 remainingUnits * 100 = 50,000 INR (50% of 100,000 total Core) -> Underweight vs 60% (Shielded)
        Lot lmLot = new Lot("L1", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusMonths(6), new BigDecimal("500"), new BigDecimal("500"), new BigDecimal("100"), new BigDecimal("50000"), false, BigDecimal.ZERO);

        // PPFC: 500 remainingUnits * 100 = 50,000 INR (50% of 100,000 total Core) -> Overweight vs 40%
        Lot ppfcLot = new Lot("L2", "INF879O01027", "Parag Parikh Flexi Cap Fund",
            today.minusMonths(6), new BigDecimal("500"), new BigDecimal("500"), new BigDecimal("100"), new BigDecimal("50000"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("100"),
            "INF879O01027", new BigDecimal("100")
        );

        List<Lot> eligibleLots = RebalanceWaterfallEngine.filterOverweightCoreLots(List.of(lmLot, ppfcLot), navMap, today);
        assertEquals(1, eligibleLots.size());
        assertEquals("INF879O01027", eligibleLots.get(0).assetId(), "Only overweight PPFC lot must be returned for trimming, LargeMidcap 250 must be shielded");
    }

    @Test
    void testLegacyTierStrategyIncludesKotakEqualWeight() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        // Kotak Equal Weight: inactive SIP, LTCG, legacy candidate (no harvest protection)
        Lot kotakLot = new Lot("L_KOTAK", "INF174KA1TY2", "Kotak Nifty 100 Equal Weight Index Fund Direct Growth",
            today.minusYears(2), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, BigDecimal.ZERO);

        // Motilal Midcap 150: inactive SIP, LTCG, legacy candidate
        Lot midcapLot = new Lot("L_MIDCAP", "INF247L01916", "Motilal Oswal Nifty Midcap 150 Index Fund Direct Growth",
            today.minusYears(2), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "INF174KA1TY2", new BigDecimal("100"),
            "INF247L01916", new BigDecimal("100")
        );

        // Trim 5,000 INR specifically from Kotak legacy lot
        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("5000"),
            List.of(kotakLot),
            navMap,
            new BigDecimal("125000"),
            false,
            today,
            "2026-27"
        );

        assertNotNull(result);
        assertEquals(1, result.steps().size());
        assertEquals("INF174KA1TY2", result.steps().get(0).assetId(), "Kotak Equal Weight must be trimmed when eligible as legacy lot");
        assertEquals(0, new BigDecimal("5000.00").compareTo(result.steps().get(0).proceeds()));
    }

    @Test
    @DisplayName("F1: Scheme with older gain lot blocks cherry-picking newer loss lot under Section 45(2A) RTA FIFO")
    void testF1SchemeWithOlderGainBlocksNewerLossHarvest() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        // Scheme INF109KC12U0:
        // Lot 1 (Older, 700 days ago, LTCG): Cost = 10.00, NAV = 15.00 -> +5.00 Gain per unit
        Lot olderGainLot = new Lot("L1-GAIN", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(700), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10.00"), new BigDecimal("1000.00"), false, BigDecimal.ZERO);

        // Lot 2 (Newer, 30 days ago, STCL): Cost = 18.00, NAV = 15.00 -> -3.00 Loss per unit
        Lot newerLossLot = new Lot("L2-LOSS", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(30), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("18.00"), new BigDecimal("1800.00"), false, BigDecimal.ZERO);

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
        // Scheme INF109KC12U0:
        // Lot 1 (Older, 800 days ago, LTCG): Cost = 10.00, NAV = 20.00 -> +10.00 Gain per unit (higher gain)
        Lot olderLot = new Lot("L1-OLD", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(800), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10.00"), new BigDecimal("1000.00"), false, BigDecimal.ZERO);

        // Lot 2 (Newer, 400 days ago, LTCG): Cost = 18.00, NAV = 20.00 -> +2.00 Gain per unit (lower gain)
        Lot newerLot = new Lot("L2-NEW", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(400), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("18.00"), new BigDecimal("1800.00"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("20.00")
        );

        // Trim 1,000 INR (50 units).
        // At HEAD, selectCoreLots sorted by (nav - cost) ASC, so it consumed L2-NEW (+2/unit) before L1-OLD (+10/unit).
        // Under F1, RTA FIFO strictly mandates consuming L1-OLD first!
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
    @DisplayName("F1 Residual: Older loss lot must be consumed first by LossHarvestTierStrategy before newer LTCG gain lot in same scheme")
    void testF1OlderLossLotConsumedFirstBeforeNewerLtcgGainLotInSameScheme() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        // Scheme INF109KC12U0:
        // Lot 1 (Older, 600 days ago, loss lot): 100 units @ cost 30.00, NAV = 20.00 -> Loss of -10/unit
        Lot olderLossLot = new Lot("L1-LOSS", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(600), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("30.00"), new BigDecimal("3000.00"), false, BigDecimal.ZERO);

        // Lot 2 (Newer, 400 days ago, LTCG gain lot): 100 units @ cost 10.00, NAV = 20.00 -> Gain of +10/unit
        Lot newerGainLot = new Lot("L2-GAIN", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(400), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10.00"), new BigDecimal("1000.00"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("20.00")
        );

        // Scenario 1: Trim 2,500 INR (125 units).
        // Older L1-LOSS must be fully consumed first (100 units * 20 = 2000 INR) under LOSS_HARVEST.
        // Then remaining 500 INR (25 units) must be consumed from L2-GAIN under LTCG_WITHIN_EXEMPTION.
        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("2500.00"),
            List.of(olderLossLot, newerGainLot),
            navMap,
            new BigDecimal("125000.00"),
            false,
            today,
            "2026-27"
        );

        assertNotNull(result);
        assertEquals(2, result.steps().size(), "Must execute 2 steps across LOSS_HARVEST and LTCG tiers");

        RebalanceWaterfallEngine.WaterfallStep step1 = result.steps().get(0);
        assertEquals("L1-LOSS", step1.lotId());
        assertEquals(WaterfallTier.LOSS_HARVEST, step1.tier());
        assertEquals(new BigDecimal("100.0000"), step1.unitsSold());
        assertEquals(new BigDecimal("2000.00"), step1.proceeds());
        assertEquals(new BigDecimal("-1000.00"), step1.realizedGain());

        RebalanceWaterfallEngine.WaterfallStep step2 = result.steps().get(1);
        assertEquals("L2-GAIN", step2.lotId());
        assertEquals(WaterfallTier.LTCG_WITHIN_EXEMPTION, step2.tier());
        assertEquals(new BigDecimal("25.0000"), step2.unitsSold());
        assertEquals(new BigDecimal("500.00"), step2.proceeds());
        assertEquals(new BigDecimal("250.00"), step2.realizedGain());

        // Scenario 2: Trim only 1,000 INR (50 units).
        // L1-LOSS is partially consumed (50 units). The remaining 50 units of L1-LOSS stay unsold.
        // L2-GAIN must NOT be touched because L1-LOSS still has remaining units!
        RebalanceWaterfallEngine.WaterfallResult resultPartial = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("1000.00"),
            List.of(olderLossLot, newerGainLot),
            navMap,
            new BigDecimal("125000.00"),
            false,
            today,
            "2026-27"
        );

        assertNotNull(resultPartial);
        assertEquals(1, resultPartial.steps().size(), "Only L1-LOSS should be partially consumed");
        assertEquals("L1-LOSS", resultPartial.steps().get(0).lotId());
        assertEquals(new BigDecimal("50.0000"), resultPartial.steps().get(0).unitsSold());
    }

    @Test
    @DisplayName("F5: Short-term loss lots are eligible for tax-loss harvesting in non-urgent runs")
    void testF5ShortTermLossLotHarvestedWhenNotUrgent() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        // Short-term loss lot (acquired 30 days ago): Cost = 30.00, NAV = 20.00
        Lot stclLot = new Lot("STCL-1", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(30), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("30.00"), new BigDecimal("3000.00"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("20.00")
        );

        // Trim 1,000 INR with urgent = false
        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("1000.00"),
            List.of(stclLot),
            navMap,
            new BigDecimal("125000.00"),
            false, // Non-urgent run
            today,
            "2026-27"
        );

        assertNotNull(result);
        assertEquals(1, result.steps().size(), "Short-term loss lot MUST be harvested even when non-urgent");
        RebalanceWaterfallEngine.WaterfallStep step = result.steps().get(0);
        assertEquals("STCL-1", step.lotId());
        assertEquals(WaterfallTier.LOSS_HARVEST, step.tier());
        assertEquals(new BigDecimal("50.0000"), step.unitsSold());
        assertEquals(new BigDecimal("1000.00"), step.proceeds());
        assertEquals(new BigDecimal("-500.00"), step.realizedGain());
    }

    @Test
    @DisplayName("F6: LTCG lot straddling unused exemption is split between LTCG_WITHIN_EXEMPTION and LTCG_BEYOND_EXEMPTION")
    void testF6ExemptionCappingAndSplitIntoBeyondExemption() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        // LTCG Lot: 100 units, Cost = 10.00, NAV = 30.00 -> Gain = +20/unit. Total lot value = 3,000, Total gain = 2,000
        Lot ltcgLot = new Lot("L1-SPLIT", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(500), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10.00"), new BigDecimal("1000.00"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("30.00")
        );

        // Unused exemption is only 1,000 INR. Trim target is 3,000 INR (all 100 units).
        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("3000.00"),
            List.of(ltcgLot),
            navMap,
            new BigDecimal("1000.00"), // Headroom = 1,000
            false,
            today,
            "2026-27"
        );

        assertNotNull(result);
        assertEquals(2, result.steps().size(), "Lot must be split into exempt tier and beyond exemption tier");

        RebalanceWaterfallEngine.WaterfallStep stepExempt = result.steps().get(0);
        assertEquals("L1-SPLIT", stepExempt.lotId());
        assertEquals(WaterfallTier.LTCG_WITHIN_EXEMPTION, stepExempt.tier());
        assertEquals(new BigDecimal("1000.00"), stepExempt.realizedGain(), "Exempt slice gain must be capped at 1,000");
        assertEquals(new BigDecimal("1000.00"), stepExempt.exemptionApplied());
        assertEquals(new BigDecimal("0.00"), stepExempt.taxableAmount());

        RebalanceWaterfallEngine.WaterfallStep stepTaxable = result.steps().get(1);
        assertEquals("L1-SPLIT", stepTaxable.lotId());
        assertEquals(WaterfallTier.LTCG_BEYOND_EXEMPTION, stepTaxable.tier());
        assertEquals(new BigDecimal("1000.00"), stepTaxable.realizedGain(), "Taxable slice gain is remaining 1,000");
        assertEquals(new BigDecimal("0.00"), stepTaxable.exemptionApplied());
        assertEquals(new BigDecimal("1000.00"), stepTaxable.taxableAmount());
    }
}
