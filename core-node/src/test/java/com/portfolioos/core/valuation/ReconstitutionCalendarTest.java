package com.portfolioos.core.valuation;

import com.portfolioos.core.dtos.RebalancePlanDtos.ReconstitutionContextDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ReconstitutionCalendarTest {

    @Test
    @DisplayName("Should correctly calculate countdown and target March 31 when current date is in early year")
    void testEarlyYearTargetMarch31() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        ReconstitutionContextDto ctx = ReconstitutionCalendar.calculateReconstitutionStatus(date);
        assertEquals("2026-03-31", ctx.nextReconstitutionDate());
        assertEquals(75, ctx.daysToReconstitution());
        assertFalse(ctx.isWindowActive());
        assertEquals("PROCEED", ctx.executionRecommendation());
    }

    @Test
    @DisplayName("Should activate 48h blackout window within 2 days of March 31 reconstitution")
    void testMarch48hBlackoutWindow() {
        LocalDate date = LocalDate.of(2026, 3, 30);
        ReconstitutionContextDto ctx = ReconstitutionCalendar.calculateReconstitutionStatus(date);
        assertEquals("2026-03-31", ctx.nextReconstitutionDate());
        assertEquals(1, ctx.daysToReconstitution());
        assertTrue(ctx.isWindowActive());
        assertEquals("REBALANCE_PAUSED_48H_RECONSTITUTION", ctx.executionRecommendation());
    }

    @Test
    @DisplayName("Should correctly transition to September 30 target on April 1")
    void testAprilTransitionToSeptember30() {
        LocalDate date = LocalDate.of(2026, 4, 1);
        ReconstitutionContextDto ctx = ReconstitutionCalendar.calculateReconstitutionStatus(date);
        assertEquals("2026-09-30", ctx.nextReconstitutionDate());
        assertEquals(182, ctx.daysToReconstitution());
        assertFalse(ctx.isWindowActive());
        assertEquals("PROCEED", ctx.executionRecommendation());
    }

    @Test
    @DisplayName("Should correctly cross year boundary from late year (e.g. Dec 15) to March 31 next year")
    void testYearBoundaryRollover() {
        LocalDate date = LocalDate.of(2026, 12, 15);
        ReconstitutionContextDto ctx = ReconstitutionCalendar.calculateReconstitutionStatus(date);
        assertEquals("2027-03-31", ctx.nextReconstitutionDate());
        assertEquals(106, ctx.daysToReconstitution());
        assertFalse(ctx.isWindowActive());
        assertEquals("PROCEED", ctx.executionRecommendation());
    }

    @Test
    @DisplayName("Should handle leap year arithmetic correctly (e.g. Feb 28 in leap year 2028)")
    void testLeapYearArithmetic() {
        LocalDate date = LocalDate.of(2028, 2, 28);
        ReconstitutionContextDto ctx = ReconstitutionCalendar.calculateReconstitutionStatus(date);
        assertEquals("2028-03-31", ctx.nextReconstitutionDate());
        assertEquals(32, ctx.daysToReconstitution()); // 1 day left in Feb (Feb 29) + 31 in Mar = 32 days
        assertFalse(ctx.isWindowActive());
        assertEquals("PROCEED", ctx.executionRecommendation());
    }
}
