package com.portfolioos.core.goals;

import com.portfolioos.core.model.Lot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GoalTrackerTest {

    @Test
    void testCalculateGoalSummaryWithDefaultAllocations() {
        Lot liquidLot = new Lot(
            "LOT_1",
            "ARBITRAGE_1",
            "Invesco Arbitrage Fund",
            LocalDate.of(2024, 1, 1),
            new BigDecimal("1000.0"),
            new BigDecimal("1000.0"),
            new BigDecimal("100.0"),
            new BigDecimal("100000.0"),
            false,
            BigDecimal.ZERO
        );

        Map<String, BigDecimal> navMap = Map.of("ARBITRAGE_1", new BigDecimal("100.0"));

        GoalTracker.GoalSummary summary = GoalTracker.calculateGoalSummary(List.of(liquidLot), navMap);
        assertNotNull(summary);
        assertEquals(new BigDecimal("100000.00"), summary.totalLiquidHoldings());
        assertEquals(new BigDecimal("0.00"), summary.allocatedGoalsAmount());
        assertEquals(new BigDecimal("100000.00"), summary.unallocatedCash());
        assertTrue(summary.allocationsByGoal().containsKey(GoalTracker.GoalTag.EMERGENCY));
    }

    @Test
    void testCalculateGoalSummaryWithCustomAllocations() {
        Lot liquidLot = new Lot(
            "LOT_1",
            "ARBITRAGE_1",
            "Invesco Arbitrage Fund",
            LocalDate.of(2024, 1, 1),
            new BigDecimal("5000.0"),
            new BigDecimal("5000.0"),
            new BigDecimal("100.0"),
            new BigDecimal("500000.0"),
            false,
            BigDecimal.ZERO
        );
        Map<String, BigDecimal> navMap = Map.of("ARBITRAGE_1", new BigDecimal("100.0"));

        List<GoalTracker.GoalAllocation> customAllocations = List.of(
            new GoalTracker.GoalAllocation("ARBITRAGE_1", "Invesco Arbitrage", GoalTracker.GoalTag.EMERGENCY, new BigDecimal("200000.00")),
            new GoalTracker.GoalAllocation("ARBITRAGE_1", "Invesco Arbitrage", GoalTracker.GoalTag.BIKE, new BigDecimal("150000.00"))
        );

        GoalTracker.GoalSummary summary = GoalTracker.calculateGoalSummary(
            List.of(liquidLot),
            navMap,
            customAllocations
        );

        assertNotNull(summary);
        assertEquals(new BigDecimal("500000.00"), summary.totalLiquidHoldings());
        assertEquals(new BigDecimal("350000.00"), summary.allocatedGoalsAmount());
        assertEquals(new BigDecimal("150000.00"), summary.unallocatedCash());
    }

    @Test
    void testCalculateGoalSummaryThrowsOnMissingNav() {
        Lot liquidLot = new Lot(
            "LOT_1",
            "ARBITRAGE_1",
            "Invesco Arbitrage Fund",
            LocalDate.of(2024, 1, 1),
            new BigDecimal("1000.0"),
            new BigDecimal("1000.0"),
            new BigDecimal("100.0"),
            new BigDecimal("100000.0"),
            false,
            BigDecimal.ZERO
        );

        Map<String, BigDecimal> emptyNavMap = Map.of();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            GoalTracker.calculateGoalSummary(List.of(liquidLot), emptyNavMap)
        );
        assertTrue(ex.getMessage().contains("CRITICAL VALUATION ERROR"));
        assertTrue(ex.getMessage().contains("ARBITRAGE_1"));
    }
}
