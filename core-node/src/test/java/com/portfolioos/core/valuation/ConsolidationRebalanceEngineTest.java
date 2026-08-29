package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ConsolidationRebalanceEngineTest {

    @Test
    @DisplayName("ConsolidationRebalanceEngine generates consolidation preview with valid live NAV")
    void testConsolidationPreviewSuccess() {
        LocalDate acqDate = LocalDate.of(2024, 1, 15);
        Lot lot = new Lot("L1", "INF247L01BQ9", "Motilal Oswal Nifty Microcap 250 Index Fund", acqDate,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("2000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF247L01BQ9", new BigDecimal("30"));
        ConsolidationRebalanceEngine.ConsolidationPreviewResult result = ConsolidationRebalanceEngine.calculateConsolidation(
            List.of(lot), navMap, LocalDate.of(2026, 8, 15), new BigDecimal("125000"), "2026-27"
        );

        assertNotNull(result);
        assertEquals(1, result.phasedOutAssets().size());
        assertEquals(0, new BigDecimal("3000").compareTo(result.phasedOutAssets().get(0).currentValue()));
        assertEquals(0, new BigDecimal("1000").compareTo(result.phasedOutAssets().get(0).unrealizedGain()));
    }

    @Test
    @DisplayName("Fail-Loud Invariant: ConsolidationRebalanceEngine throws IllegalStateException on missing NAV")
    void testConsolidationRebalanceEngineThrowsOnMissingNav() {
        LocalDate acqDate = LocalDate.of(2024, 1, 15);
        Lot lot = new Lot("L1", "INF247L01BQ9", "Motilal Oswal Nifty Microcap 250 Index Fund", acqDate,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("2000"), false, null);

        Map<String, BigDecimal> emptyNavMap = Map.of();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            ConsolidationRebalanceEngine.calculateConsolidation(
                List.of(lot), emptyNavMap, LocalDate.of(2026, 8, 15), new BigDecimal("125000"), "2026-27"
            )
        );
        assertTrue(ex.getMessage().contains("CRITICAL VALUATION ERROR"));
        assertTrue(ex.getMessage().contains("INF247L01BQ9"));
    }
}
