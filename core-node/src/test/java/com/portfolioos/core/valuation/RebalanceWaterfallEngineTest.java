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
        assertEquals(new BigDecimal("5000.00"), result.satisfiedAmount());
        assertEquals(new BigDecimal("0.00"), result.deferredAmount());
        assertFalse(result.steps().isEmpty());

        RebalanceWaterfallEngine.WaterfallStep step = result.steps().get(0);
        assertEquals(WaterfallTier.STCG_URGENT_ONLY, step.tier());
    }
}
