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
    @DisplayName("HarvestAdvisor generates harvest plan with valid live NAV when exit load window has passed")
    void testGenerateHarvestPlanSuccess() {
        LocalDate acqDate = LocalDate.now().minusDays(800); // LTCG (>365d) and Exit-Load Free (>730d for PPFAS)
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
    @DisplayName("F9: PPFAS Flexi Cap held between 365 and 729 days is LTCG but excluded due to exit load")
    void testPpfasFlexicapBetween365And730DaysExcludedFromHarvestDueToExitLoad() {
        LocalDate acqDate = LocalDate.now().minusDays(400); // 400 days: LTCG (>365d) but exit load active (<730d)
        Lot lot = new Lot("L1", "INF879O01027", "Parag Parikh Flexi Cap Fund Direct Growth", acqDate,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF879O01027", new BigDecimal("100"));
        HarvestAdvisor.TaxHarvestResult result = HarvestAdvisor.generateHarvestPlan(
            List.of(lot), navMap, BigDecimal.ZERO, "2026-27"
        );

        assertNotNull(result);
        assertEquals(0, BigDecimal.ZERO.compareTo(result.totalUnrealizedLtcgAvailable()), "Lot with active exit load should not be harvested");
        assertTrue(result.recommendations().isEmpty());
    }

    @Test
    @DisplayName("F9: Intra-scheme lots are recommended in strict chronological FIFO order")
    void testHarvestRecommendationsEnforceRtaFifoWithinSameScheme() {
        // Kotak Nifty 100 EW has 0 or minimal exit load
        LocalDate olderDate = LocalDate.now().minusDays(500);
        LocalDate newerDate = LocalDate.now().minusDays(400);

        // Older lot with smaller gain (e.g. 50 units @ 80 cost, gain = 1000)
        Lot olderLot = new Lot("L_OLD", "INF174KA1TY2", "Kotak Nifty 100 Equal Weight Index Fund Direct Growth", olderDate,
            new BigDecimal("50"), new BigDecimal("50"), new BigDecimal("80"), new BigDecimal("4000"), false, null);

        // Newer lot with larger gain (e.g. 100 units @ 50 cost, gain = 5000)
        Lot newerLot = new Lot("L_NEW", "INF174KA1TY2", "Kotak Nifty 100 Equal Weight Index Fund Direct Growth", newerDate,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF174KA1TY2", new BigDecimal("100"));

        HarvestAdvisor.TaxHarvestResult result = HarvestAdvisor.generateHarvestPlan(
            List.of(newerLot, olderLot), navMap, BigDecimal.ZERO, "2026-27"
        );

        assertNotNull(result);
        assertEquals(2, result.recommendations().size());
        // Older lot MUST be first despite newer lot having larger gain!
        assertEquals("L_OLD", result.recommendations().get(0).lotId());
        assertEquals("L_NEW", result.recommendations().get(1).lotId());
    }

    @Test
    @DisplayName("Fail-Loud Invariant: HarvestAdvisor throws IllegalStateException on missing NAV")
    void testHarvestAdvisorThrowsOnMissingNav() {
        LocalDate acqDate = LocalDate.now().minusDays(800); // Beyond exit load so NAV resolution is attempted
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

    @Test
    @DisplayName("Kotak Nifty 100 Equal Weight and Midcap 150 LTCG lots are eligible for tax harvest recommendations")
    void testHarvestRecommendationsIncludeEligibleLtcgLots() {
        LocalDate acqDate = LocalDate.now().minusDays(400); // LTCG
        Lot kotakLot = new Lot("L_KOTAK", "INF174KA1TY2", "Kotak Nifty 100 Equal Weight Index Fund Direct Growth", acqDate,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Lot midcapLot = new Lot("L_MIDCAP", "INF247L01916", "Motilal Oswal Nifty Midcap 150 Index Fund Direct Growth", acqDate,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF174KA1TY2", new BigDecimal("100"),
            "INF247L01916", new BigDecimal("100")
        );

        HarvestAdvisor.TaxHarvestResult result = HarvestAdvisor.generateHarvestPlan(
            List.of(kotakLot, midcapLot), navMap, BigDecimal.ZERO, "2026-27"
        );

        assertNotNull(result);
        assertEquals(2, result.recommendations().size(), "Both eligible LTCG lots should be included in tax harvest recommendations");
        assertTrue(result.recommendations().stream().anyMatch(r -> r.assetId().equals("INF174KA1TY2")));
        assertTrue(result.recommendations().stream().anyMatch(r -> r.assetId().equals("INF247L01916")));
    }
}
