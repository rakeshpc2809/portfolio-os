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
    void testCoreLotTaxEfficiencySortingMultipleNavScales() {
        LocalDate today = LocalDate.of(2026, 8, 1);
        LocalDate acqOld = LocalDate.of(2020, 1, 1); // Very old -> LTCG

        // Lot 1: High NAV, low % gain
        // Cost 800, NAV 1000 -> Absolute Gain 200, % Gain 20%
        Lot highNavLowPctGain = new Lot("L_HIGH_NAV", "FUND_HIGH", "High NAV Fund",
            acqOld, new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("800"), new BigDecimal("8000"), false, BigDecimal.ZERO);

        // Lot 2: Low NAV, high % gain
        // Cost 20, NAV 40 -> Absolute Gain 20, % Gain 50%
        Lot lowNavHighPctGain = new Lot("L_LOW_NAV", "FUND_LOW", "Low NAV Fund",
            acqOld, new BigDecimal("250"), new BigDecimal("250"), new BigDecimal("20"), new BigDecimal("5000"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> navMap = Map.of(
            "FUND_HIGH", new BigDecimal("1000"),
            "FUND_LOW", new BigDecimal("40")
        );

        // We only want to sell 1000 INR worth of funds. 
        // A perfectly tax-optimized sort should pick the lot with the LOWEST % gain first (Lot 1), 
        // regardless of its absolute gain being mathematically larger (200 > 20).
        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("1000"),
            List.of(highNavLowPctGain, lowNavHighPctGain),
            navMap,
            new BigDecimal("125000"),
            false,
            today,
            "2026-27"
        );

        assertNotNull(result);
        assertEquals(new BigDecimal("1000.00"), result.satisfiedAmount());
        assertFalse(result.steps().isEmpty());

        // The first step should be selling from FUND_HIGH because it has a 20% tax exposure per rupee,
        // compared to FUND_LOW which has a 50% tax exposure per rupee.
        assertEquals("FUND_HIGH", result.steps().get(0).assetId(), 
            "The lot with the lowest % gain should be selected first, proving cross-NAV-scale sorting works.");
    }
}
