package com.portfolioos.core.valuation;

import com.portfolioos.core.dtos.RebalancePlanDtos.GoldSilverContextDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GoldSilverRatioCalculatorTest {

    @Test
    @DisplayName("Should flag SILVER_UNDERVALUED and assign 40/60 split when ratio >= 80.0")
    void testSilverUndervaluedSplit() {
        GoldSilverContextDto ctx = GoldSilverRatioCalculator.evaluateRatio(84.5);
        assertEquals(84.5, ctx.goldSilverRatio());
        assertEquals("SILVER_UNDERVALUED", ctx.signal());
        assertEquals(40.0, ctx.goldTargetSplitPct());
        assertEquals(60.0, ctx.silverTargetSplitPct());
        assertFalse(ctx.isEstimated());
        assertEquals("LIVE_AMFI_ETF_SPOT", ctx.source());
        assertEquals("2026-08-31", ctx.asOfDate());
    }

    @Test
    @DisplayName("Should flag GOLD_UNDERVALUED and assign 60/40 split when ratio <= 65.0")
    void testGoldUndervaluedSplit() {
        GoldSilverContextDto ctx = GoldSilverRatioCalculator.evaluateRatio(62.0);
        assertEquals(62.0, ctx.goldSilverRatio());
        assertEquals("GOLD_UNDERVALUED", ctx.signal());
        assertEquals(60.0, ctx.goldTargetSplitPct());
        assertEquals(40.0, ctx.silverTargetSplitPct());
    }

    @Test
    @DisplayName("Should flag NEUTRAL and assign 50/50 split when 65.0 < ratio < 80.0")
    void testNeutralSplit() {
        GoldSilverContextDto ctx = GoldSilverRatioCalculator.evaluateRatio(72.5);
        assertEquals(72.5, ctx.goldSilverRatio());
        assertEquals("NEUTRAL", ctx.signal());
        assertEquals(50.0, ctx.goldTargetSplitPct());
        assertEquals(50.0, ctx.silverTargetSplitPct());
    }

    @Test
    @DisplayName("Should calculate ratio dynamically from live GOLDBEES and SILVERBEES NAVs")
    void testLiveNavMapCalculation() {
        // Gold NAV = 75.0 (0.01g gold => 7500/g), Silver NAV = 88.0 (1g silver => 88/g)
        // Ratio = 7500 / 88 = 85.22 => rounded to 85.2
        Map<String, BigDecimal> navMap = Map.of(
            "GOLDBEES", new BigDecimal("75.00"),
            "SILVERBEES", new BigDecimal("88.00")
        );

        GoldSilverContextDto ctx = GoldSilverRatioCalculator.calculateRatio(navMap);
        assertEquals(85.2, ctx.goldSilverRatio(), 0.1);
        assertEquals("SILVER_UNDERVALUED", ctx.signal());
        assertEquals(40.0, ctx.goldTargetSplitPct());
        assertEquals(60.0, ctx.silverTargetSplitPct());
        assertFalse(ctx.isEstimated());
        assertEquals("LIVE_AMFI_ETF_SPOT", ctx.source());
    }

    @Test
    @DisplayName("Should calculate ratio dynamically using benchmark ETF ISINs from AMFI NAV feed")
    void testLiveBenchmarkIsinCalculation() {
        // Nippon Gold ETF (INF204KB17I5) NAV = 127.54, Nippon Silver ETF (INF204KC1402) NAV = 224.41
        // Gold per gram: 127.54 * 100 = 12754; Silver per gram: 224.41 => ratio = 12754 / 224.41 = 56.8x (GOLD_UNDERVALUED)
        Map<String, BigDecimal> navMap = Map.of(
            "INF204KB17I5", new BigDecimal("127.54"),
            "INF204KC1402", new BigDecimal("224.41")
        );

        GoldSilverContextDto ctx = GoldSilverRatioCalculator.calculateRatio(navMap);
        assertEquals(56.8, ctx.goldSilverRatio(), 0.1);
        assertEquals("GOLD_UNDERVALUED", ctx.signal());
        assertEquals(60.0, ctx.goldTargetSplitPct());
        assertEquals(40.0, ctx.silverTargetSplitPct());
        assertFalse(ctx.isEstimated());
        assertEquals("LIVE_AMFI_ETF_SPOT", ctx.source());
    }

    @Test
    @DisplayName("Should fallback gracefully with isEstimated=true when only combined FoF is present")
    void testFallbackOnCombinedFofOnly() {
        // Motilal Oswal Gold and Silver ETFs FoF (INF247L01BM8) is a combined FoF, not separate spot ETFs
        Map<String, BigDecimal> navMap = Map.of(
            "INF247L01BM8", new BigDecimal("34.75")
        );

        GoldSilverContextDto ctx = GoldSilverRatioCalculator.calculateRatio(navMap);
        assertNotNull(ctx);
        assertEquals(84.5, ctx.goldSilverRatio());
        assertEquals("SILVER_UNDERVALUED", ctx.signal());
        assertTrue(ctx.isEstimated());
        assertEquals("STATUTORY_BENCHMARK_ESTIMATE", ctx.source());
        assertEquals("2026-08-31", ctx.asOfDate());
    }
}
