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

    @Test
    @DisplayName("F-10 Invariant: Multi-lot phased-out fund computes exact FIFO tax drag per lot")
    void testMultiLotFifoTaxCalculation() {
        LocalDate today = LocalDate.of(2026, 8, 15);
        // Lot 1: 500 days old (LTCG). 100 units @ cost 20 = 2000. Nav 30 -> gain 1000.
        Lot lotLtcg = new Lot("L1", "INF247L01BQ9", "Motilal Oswal Nifty Microcap 250 Index Fund", today.minusDays(500),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("2000"), false, null);
        // Lot 2: 30 days old (STCG). 100 units @ cost 20 = 2000. Nav 30 -> gain 1000.
        Lot lotStcg = new Lot("L2", "INF247L01BQ9", "Motilal Oswal Nifty Microcap 250 Index Fund", today.minusDays(30),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("2000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF247L01BQ9", new BigDecimal("30"));
        // 0 remaining exemption to test raw rates: LTCG @ 12.5% = 125.00, STCG @ 20% = 200.00. Total tax = 325.00
        ConsolidationRebalanceEngine.ConsolidationPreviewResult result = ConsolidationRebalanceEngine.calculateConsolidation(
            List.of(lotLtcg, lotStcg), navMap, today, BigDecimal.ZERO, "2026-27"
        );

        assertNotNull(result);
        assertEquals(1, result.phasedOutAssets().size());
        ConsolidationRebalanceEngine.PhasedOutAssetSummary summary = result.phasedOutAssets().get(0);
        assertEquals(0, new BigDecimal("6000").compareTo(summary.currentValue()));
        assertEquals(0, new BigDecimal("2000").compareTo(summary.unrealizedGain()));
        // Total tax drag must be 125.00 (LTCG) + 200.00 (STCG) = 325.00
        assertEquals(0, new BigDecimal("325.000").compareTo(summary.estimatedTaxDrag()),
            "FIFO lot evaluation must separately compute LTCG (12.5%) and STCG (20%) instead of aggregating earliest date");
        assertEquals(0, new BigDecimal("325.000").compareTo(result.totalTaxDrag()));
        // Effective proceeds must be 6000 - 325 = 5675
        assertEquals(0, new BigDecimal("5675.000").compareTo(result.totalProceeds()));
    }

    @Test
    @DisplayName("F-10 Invariant: Section 50AA Debt fund gains taxed at slab rate (30%), not equity rate")
    void testSpecifiedDebtFundTaxDragUsesSlabRate() {
        LocalDate today = LocalDate.of(2026, 8, 15);
        // Post-April-2023 debt fund purchase: 100 units @ cost 100 = 10,000. Nav 150 -> val 15,000, gain 5,000.
        Lot debtLot = new Lot("D1", "INF179K0101", "HDFC Liquid Debt Fund Direct Growth", today.minusDays(200),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF179K0101", new BigDecimal("150"));
        ConsolidationRebalanceEngine.ConsolidationPreviewResult result = ConsolidationRebalanceEngine.calculateConsolidation(
            List.of(debtLot), navMap, today, new BigDecimal("125000"), "2026-27"
        );

        assertNotNull(result);
        assertEquals(1, result.phasedOutAssets().size());
        ConsolidationRebalanceEngine.PhasedOutAssetSummary summary = result.phasedOutAssets().get(0);
        assertEquals(0, new BigDecimal("15000").compareTo(summary.currentValue()));
        assertEquals(0, new BigDecimal("5000").compareTo(summary.unrealizedGain()));
        // Section 50AA debt is taxed at 30% slab rate (5,000 * 0.30 = 1,500.00), no 112A equity exemption applies
        assertEquals(0, new BigDecimal("1500.000").compareTo(summary.estimatedTaxDrag()),
            "Section 50AA debt must be taxed at 30% slab rate, not equity STCG rate (20%)");
        assertEquals(0, new BigDecimal("1500.000").compareTo(result.totalTaxDrag()));
        assertEquals(0, new BigDecimal("13500.000").compareTo(result.totalProceeds()));
    }

    @Test
    @DisplayName("F-10 Invariant: Unlisted Gold FoF taxes STCG at slab rate (30%) and LTCG at 12.5%")
    void testGoldFundTaxDragUsesSlabRateForStcgAndLtcgRateForLtcg() {
        LocalDate today = LocalDate.of(2026, 8, 15);
        // Lot 1: 800 days old (LTCG >= 730d). 100 units @ cost 100 = 10,000. Nav 120 -> gain 2,000. Tax @ 12.5% = 250.00
        Lot goldLtcg = new Lot("G1", "INF109K0180", "ICICI Prudential Regular Gold Savings Fund", today.minusDays(800),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null);
        // Lot 2: 100 days old (STCG < 730d). 100 units @ cost 100 = 10,000. Nav 120 -> gain 2,000. Tax @ 30% slab = 600.00
        Lot goldStcg = new Lot("G2", "INF109K0180", "ICICI Prudential Regular Gold Savings Fund", today.minusDays(100),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF109K0180", new BigDecimal("120"));
        ConsolidationRebalanceEngine.ConsolidationPreviewResult result = ConsolidationRebalanceEngine.calculateConsolidation(
            List.of(goldLtcg, goldStcg), navMap, today, new BigDecimal("125000"), "2026-27"
        );

        assertNotNull(result);
        assertEquals(1, result.phasedOutAssets().size());
        ConsolidationRebalanceEngine.PhasedOutAssetSummary summary = result.phasedOutAssets().get(0);
        assertEquals(0, new BigDecimal("24000").compareTo(summary.currentValue()));
        assertEquals(0, new BigDecimal("4000").compareTo(summary.unrealizedGain()));
        // Total tax drag: LTCG (2,000 * 12.5% = 250) + STCG (2,000 * 30% = 600) = 850.00
        assertEquals(0, new BigDecimal("850.000").compareTo(summary.estimatedTaxDrag()),
            "Gold FoF must tax LTCG at 12.5% and STCG at 30% slab rate without equity exemption");
        assertEquals(0, new BigDecimal("850.000").compareTo(result.totalTaxDrag()));
        assertEquals(0, new BigDecimal("23150.000").compareTo(result.totalProceeds()));
    }
}
