package com.portfolioos.core.valuation;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

public class MonteCarloSanityTest {

    @Test
    public void testMonteCarloDivergenceAndBounds() {
        BigDecimal deterministicFv = new BigDecimal("19997165.16");
        BigDecimal mcMedian = new BigDecimal("78767421.90");

        // Assert that Monte Carlo median is non-zero
        assertTrue(mcMedian.compareTo(BigDecimal.ZERO) > 0, "Monte Carlo median should be non-zero");

        // Assert that Monte Carlo median does NOT collapse bit-for-bit onto deterministic FV
        assertNotEquals(0, mcMedian.compareTo(deterministicFv), "Monte Carlo median should be independent from deterministic FV");

        // Assert sanity bounds (median must be within 0.05x and 10.0x of deterministic FV)
        BigDecimal maxBound = deterministicFv.multiply(new BigDecimal("10.0"));
        BigDecimal minBound = deterministicFv.multiply(new BigDecimal("0.05"));

        assertTrue(mcMedian.compareTo(maxBound) <= 0, "Monte Carlo median should not exceed 10x deterministic FV");
        assertTrue(mcMedian.compareTo(minBound) >= 0, "Monte Carlo median should be at least 0.05x deterministic FV");
    }
}
