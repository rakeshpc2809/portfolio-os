package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HarvestAdvisorTest {

    @Test
    @DisplayName("HarvestAdvisor generates harvest plan with valid live NAV")
    void testGenerateHarvestPlanSuccess() {
        LocalDate acqDate = LocalDate.now().minusDays(400); // LTCG
        Lot lot = new Lot("L1", "INF879O01027", "Parag Parikh Flexi Cap Fund Direct Growth", acqDate,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF879O01027", new BigDecimal("100"));
        HarvestAdvisor.TaxHarvestResult result = HarvestAdvisor.generateHarvestPlan(
            List.of(lot), navMap, BigDecimal.ZERO, "2026-27"
        );

        assertNotNull(result);
        assertEquals(new BigDecimal("5000.00"), result.totalUnrealizedLtcgAvailable());
        assertEquals(new BigDecimal("5000.00"), result.harvestableLtcgGain());
        assertEquals(1, result.recommendations().size());
        assertEquals("INF879O01027", result.recommendations().get(0).assetId());
    }

    @Test
    @DisplayName("Fail-Loud Invariant: HarvestAdvisor throws IllegalStateException on missing NAV")
    void testHarvestAdvisorThrowsOnMissingNav() {
        LocalDate acqDate = LocalDate.now().minusDays(400);
        Lot lot = new Lot("L1", "INF879O01027", "Parag Parikh Flexi Cap Fund Direct Growth", acqDate,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Map<String, BigDecimal> emptyNavMap = Map.of();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            HarvestAdvisor.generateHarvestPlan(
                List.of(lot), emptyNavMap, BigDecimal.ZERO, "2026-27"
            )
        );
        assertTrue(ex.getMessage().contains("CRITICAL VALUATION ERROR"));
        assertTrue(ex.getMessage().contains("INF879O01027"));
    }
}
