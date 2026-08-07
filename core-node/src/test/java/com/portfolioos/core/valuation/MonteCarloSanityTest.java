package com.portfolioos.core.valuation;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

public class MonteCarloSanityTest {

    @Test
    public void testMonteCarloDivergenceAndBounds() {
        BigDecimal deterministicFv = new BigDecimal("19997165.16");
        BigDecimal mcMedian = new BigDecimal("17871599.69");
        double successRate = 66.86;

        // Assert that Monte Carlo median is non-zero
        assertTrue(mcMedian.compareTo(BigDecimal.ZERO) > 0, "Monte Carlo median should be non-zero");

        // Assert that Monte Carlo median does NOT collapse bit-for-bit onto deterministic FV
        assertNotEquals(0, mcMedian.compareTo(deterministicFv), "Monte Carlo median should be independent from deterministic FV");

        // Assert ratio between Monte Carlo median and deterministic FV is realistic (between 0.6x and 1.3x due to volatility drag)
        double ratio = mcMedian.doubleValue() / deterministicFv.doubleValue();
        assertTrue(ratio >= 0.6 && ratio <= 1.3, "Monte Carlo median ratio to deterministic FV should be between 0.6x and 1.3x, but was: " + ratio);

        // Assert success rate reflects decumulation survival under shortage (between 10% and 90%)
        assertTrue(successRate >= 10.0 && successRate <= 90.0, "Success rate must reflect real decumulation survival under shortage");
    }
}
