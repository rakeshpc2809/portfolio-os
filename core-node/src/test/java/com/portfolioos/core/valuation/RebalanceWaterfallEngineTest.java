package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;

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
}
