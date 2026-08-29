package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RebalanceEngineTest {

    @Test
    @DisplayName("RebalanceEngine calculates rebalance preview with valid live NAV")
    void testCalculateRebalancePreviewSuccess() {
        LocalDate acqDate = LocalDate.now().minusDays(400); // LTCG
        Lot lot = new Lot("L1", "INF879O01027", "Parag Parikh Flexi Cap", acqDate,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF879O01027", new BigDecimal("100"));
        RebalanceEngine.RebalancePreviewResult result = RebalanceEngine.calculateRebalancePreview(
            List.of(lot), navMap, new BigDecimal("5000"), new BigDecimal("125000"), "2026-27"
        );

        assertNotNull(result);
        assertEquals(0, new BigDecimal("5000").compareTo(result.targetRedemptionAmount()));
        assertEquals(0, new BigDecimal("5000").compareTo(result.actualRedemptionAmount()));
        assertEquals(1, result.selectedLots().size());
        assertEquals("INF879O01027", result.selectedLots().get(0).assetId());
    }

    @Test
    @DisplayName("Fail-Loud Invariant: RebalanceEngine throws IllegalStateException on missing NAV")
    void testRebalanceEngineThrowsOnMissingNav() {
        LocalDate acqDate = LocalDate.now().minusDays(400);
        Lot lot = new Lot("L1", "INF879O01027", "Parag Parikh Flexi Cap", acqDate,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Map<String, BigDecimal> emptyNavMap = Map.of();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            RebalanceEngine.calculateRebalancePreview(
                List.of(lot), emptyNavMap, new BigDecimal("5000"), new BigDecimal("125000"), "2026-27"
            )
        );
        assertTrue(ex.getMessage().contains("CRITICAL VALUATION ERROR"));
        assertTrue(ex.getMessage().contains("INF879O01027"));
    }
}
