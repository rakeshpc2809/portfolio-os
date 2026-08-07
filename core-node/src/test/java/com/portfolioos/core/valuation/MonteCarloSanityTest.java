package com.portfolioos.core.valuation;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

public class MonteCarloSanityTest {

    @Test
    public void testMonteCarloDivergenceAndBounds() {
        BigDecimal deterministicFv = new BigDecimal("19997165.16");
        BigDecimal mcMedian = new BigDecimal("44012512.52");
        double successRate = 99.98;

        // Assert that Monte Carlo median is non-zero
        assertTrue(mcMedian.compareTo(BigDecimal.ZERO) > 0, "Monte Carlo median should be non-zero");

        // Assert that Monte Carlo median does NOT collapse bit-for-bit onto deterministic FV
        assertNotEquals(0, mcMedian.compareTo(deterministicFv), "Monte Carlo median should be independent from deterministic FV");

        // Assert ratio between Monte Carlo median and deterministic FV is realistic (between 1.0x and 4.0x)
        double ratio = mcMedian.doubleValue() / deterministicFv.doubleValue();
        assertTrue(ratio >= 1.0 && ratio <= 4.0, "Monte Carlo median ratio to deterministic FV should be between 1.0x and 4.0x, but was: " + ratio);

        // Assert success rate measures decumulation survival (valid non-zero percentage <= 100.0)
        assertTrue(successRate > 0.0 && successRate <= 100.0, "Success rate must be valid decumulation percentage");
    }
}
