package com.portfolioos.core.valuation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class GoldDampenerCalculatorTest {

    @Test
    @DisplayName("Cheap state (dev <= 0%): buy multiplier = 130%, sell multiplier = 60%")
    void testCheapStateMultipliers() {
        GoldDampenerCalculator.DampenerMultipliers mults = GoldDampenerCalculator.calculateMultipliers(-5.0);
        assertEquals(1.30, mults.buyMultiplier(), 0.0001);
        assertEquals(0.60, mults.sellMultiplier(), 0.0001);

        GoldDampenerCalculator.DampenerMultipliers zeroMults = GoldDampenerCalculator.calculateMultipliers(0.0);
        assertEquals(1.30, zeroMults.buyMultiplier(), 0.0001);
        assertEquals(0.60, zeroMults.sellMultiplier(), 0.0001);
    }

    @Test
    @DisplayName("Midpoint linear taper (dev = 10%): buy = 85% (0.85), sell = 100% (1.00)")
    void testMidpointLinearTaperMultipliers() {
        GoldDampenerCalculator.DampenerMultipliers mults = GoldDampenerCalculator.calculateMultipliers(10.0);
        assertEquals(0.85, mults.buyMultiplier(), 0.0001, "At dev=10%, buy multiplier must taper linearly to 85%");
        assertEquals(1.00, mults.sellMultiplier(), 0.0001, "At dev=10%, sell multiplier must taper linearly to 100%");
    }

    @Test
    @DisplayName("Extended state (dev >= 20%): buy multiplier = 40% (0.40), sell multiplier = 140% (1.40)")
    void testExtendedStateMultipliers() {
        GoldDampenerCalculator.DampenerMultipliers mults = GoldDampenerCalculator.calculateMultipliers(20.0);
        assertEquals(0.40, mults.buyMultiplier(), 0.0001);
        assertEquals(1.40, mults.sellMultiplier(), 0.0001);

        GoldDampenerCalculator.DampenerMultipliers overMults = GoldDampenerCalculator.calculateMultipliers(25.0);
        assertEquals(0.40, overMults.buyMultiplier(), 0.0001);
        assertEquals(1.40, overMults.sellMultiplier(), 0.0001);
    }

    @Test
    @DisplayName("Floor backstop under extended NAV state (+20%): forces 1.0x override multiplier and sizes to 50% gap")
    void testFloorBackstopOverridesDampenerUnderExtendedState() {
        double targetWeightPct = 15.0;
        double currentWeightPct = 10.0; // 5 points underweight
        double currentPrice = 120.0;
        double trailingMa = 100.0; // dev = +20% (highly extended)
        BigDecimal corpus = new BigDecimal("1000000.00"); // 10 Lakhs

        // Normal buy allocation at dev=+20% would damp buy to 40%: (5% * 10L) * 0.40 = 20,000
        BigDecimal normalDampenedBuy = GoldDampenerCalculator.calculateSizedAllocation(
            targetWeightPct, currentWeightPct, currentPrice, trailingMa, corpus, false
        );
        assertEquals(new BigDecimal("20000.00"), normalDampenedBuy);

        // Floor backstop under extended state (+20%) MUST override dampener to 1.0x and size to 50% of gap (2.5%):
        // 2.5% * 10L * 1.0x = 25,000
        BigDecimal floorBackstopSized = GoldDampenerCalculator.calculateSizedAllocation(
            targetWeightPct, currentWeightPct, currentPrice, trailingMa, corpus, true
        );
        assertEquals(new BigDecimal("25000.00"), floorBackstopSized,
            "Floor backstop must override price dampening to 1.0x multiplier and size to close 50% of remaining gap");
    }
}
