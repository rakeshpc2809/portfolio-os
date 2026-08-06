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
    void testLegacyFundPriority() {
        LocalDate today = LocalDate.of(2026, 8, 1);
        LocalDate acqOld = LocalDate.of(2024, 1, 1);

        // Core fund lot
        Lot coreLot = new Lot("L1", "NIFTY_LARGEMIDCAP_250", "Nifty LargeMidcap 250 Index Fund",
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), acqOld);

        // Legacy fund lot (NIFTY100_EW)
        Lot legacyLot = new Lot("L2", "NIFTY100_EW", "Nifty 100 Equal Weight Index Fund",
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), acqOld);

        Map<String, BigDecimal> navMap = Map.of(
            "NIFTY_LARGEMIDCAP_250", new BigDecimal("150"),
            "NIFTY100_EW", new BigDecimal("150")
        );

        // Trim 5,000 INR
        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            new BigDecimal("5000"),
            List.of(coreLot, legacyLot),
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

        // First step must be Tier 1 (LEGACY_FUND)
        RebalanceWaterfallEngine.WaterfallStep firstStep = result.steps().get(0);
        assertEquals(WaterfallTier.LEGACY_FUND, firstStep.tier());
        assertEquals("NIFTY100_EW", firstStep.assetId());
    }

    @Test
    void testStcgDeferralWhenNotUrgent() {
        LocalDate today = LocalDate.of(2026, 8, 1);
        LocalDate acqRecent = LocalDate.of(2026, 5, 1); // 3 months old -> STCG

        Lot recentLot = new Lot("L1", "NIFTY_LARGEMIDCAP_250", "Nifty LargeMidcap 250 Index Fund",
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), acqRecent);

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
        LocalDate acqRecent = LocalDate.of(2026, 5, 1); // 3 months old -> STCG

        Lot recentLot = new Lot("L1", "NIFTY_LARGEMIDCAP_250", "Nifty LargeMidcap 250 Index Fund",
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), acqRecent);

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
