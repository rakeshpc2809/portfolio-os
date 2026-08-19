package com.portfolioos.core.fire;

import com.portfolioos.core.model.Lot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FireTrackerTest {

    @Test
    void testCalculateFireSummary() {
        Lot lot = new Lot(
            "LOT_1",
            "NIFTY_LARGEMIDCAP_1",
            "ICICI Nifty LargeMidcap",
            LocalDate.of(2024, 1, 1),
            new BigDecimal("1000.0"),
            new BigDecimal("1000.0"),
            new BigDecimal("100.0"),
            new BigDecimal("100000.0"),
            false,
            BigDecimal.ZERO
        );

        Map<String, BigDecimal> navMap = Map.of("NIFTY_LARGEMIDCAP_1", new BigDecimal("150.0"));
        FireTracker.FireProfile profile = new FireTracker.FireProfile();

        FireTracker.FireSummary summary = FireTracker.calculateFireSummary(
            List.of(lot),
            navMap,
            LocalDate.of(2026, 8, 19),
            profile,
            new BigDecimal("500000.00"),
            95.0,
            new BigDecimal("25000000.00"),
            new BigDecimal("18000000.00")
        );

        assertNotNull(summary);
        assertEquals("Primary Expense Target", summary.activeScenarioLabel());
        assertTrue(summary.fireInvestableNetWorth().compareTo(BigDecimal.ZERO) >= 0);
        assertNotNull(summary.status());
    }

    @Test
    void testFireProfileGetters() {
        FireTracker.FireProfile profile = new FireTracker.FireProfile();
        assertNotNull(profile.birthDate());
        assertEquals(45, profile.targetRetirementAge());
        assertEquals(new BigDecimal("3.0"), profile.swrPercent());
        assertNotNull(profile.scenarios());
        assertFalse(profile.scenarios().isEmpty());
    }
}
