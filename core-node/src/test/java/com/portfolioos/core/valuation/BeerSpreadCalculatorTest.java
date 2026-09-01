package com.portfolioos.core.valuation;

import com.portfolioos.core.dtos.RebalancePlanDtos.BeerSpreadContextDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeerSpreadCalculatorTest {

    @Test
    @DisplayName("Should correctly calculate reciprocal earnings yield and positive spread")
    void testStandardBeerSpreadCalculation() {
        // G-Sec = 7.10%, Nifty PE = 22.40 => Earnings Yield = 100/22.40 = 4.46%
        // Spread = 7.10 - 4.46 = +2.64% (> 2.50% threshold => EQUITY_EXPENSIVE)
        BeerSpreadContextDto ctx = BeerSpreadCalculator.evaluateBeerSpread(7.10, 22.40, "2026-08-31");
        assertEquals(7.10, ctx.gsec10yYieldPct());
        assertEquals(22.40, ctx.nifty50Pe());
        assertEquals(4.46, ctx.nifty50EarningsYieldPct());
        assertEquals(2.64, ctx.beerSpreadPct());
        assertEquals("EQUITY_EXPENSIVE", ctx.valuationZone());
        assertEquals("2026-08-31", ctx.asOfDate());
    }

    @Test
    @DisplayName("Should flag FAIR_VALUE when spread is between 0.0% and 2.50%")
    void testFairValuationZone() {
        // G-Sec = 6.80%, Nifty PE = 20.0 => Earnings Yield = 5.0%
        // Spread = 6.80 - 5.0 = +1.80% => FAIR_VALUE
        BeerSpreadContextDto ctx = BeerSpreadCalculator.evaluateBeerSpread(6.80, 20.00, "2026-08-31");
        assertEquals(5.00, ctx.nifty50EarningsYieldPct());
        assertEquals(1.80, ctx.beerSpreadPct());
        assertEquals("FAIR_VALUE", ctx.valuationZone());
    }

    @Test
    @DisplayName("Should flag EQUITY_ATTRACTIVE when spread is negative")
    void testEquityAttractiveZone() {
        // G-Sec = 6.50%, Nifty PE = 14.0 => Earnings Yield = 7.14%
        // Spread = 6.50 - 7.14 = -0.64% => EQUITY_ATTRACTIVE
        BeerSpreadContextDto ctx = BeerSpreadCalculator.evaluateBeerSpread(6.50, 14.00, "2026-08-31");
        assertEquals(7.14, ctx.nifty50EarningsYieldPct());
        assertEquals(-0.64, ctx.beerSpreadPct());
        assertEquals("EQUITY_ATTRACTIVE", ctx.valuationZone());
    }

    @Test
    @DisplayName("Should read default indicators from file or fallback safely")
    void testCurrentSpreadReading() {
        BeerSpreadContextDto ctx = BeerSpreadCalculator.calculateCurrentSpread();
        assertNotNull(ctx);
        assertTrue(ctx.gsec10yYieldPct() > 0);
        assertTrue(ctx.nifty50Pe() > 0);
        assertNotNull(ctx.asOfDate());
    }
}
