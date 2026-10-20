package com.portfolioos.core.matcher;

import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.model.TaxTerm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FifoMatcherGrandfatheringTest {

    private final FifoMatcher matcher = new FifoMatcher();

    @Test
    @DisplayName("FifoMatcher: Pre-2018 acquisition populates isGrandfathered and FMV from map")
    void testGrandfatheredLotPopulation() {
        LocalDate pre2018Date = LocalDate.of(2017, 6, 15);
        TaxEvent acq = new TaxEvent(
            UUID.randomUUID().toString(),
            "INF879O01027",
            "Parag Parikh Flexi Cap Fund",
            "INF879O01027",
            EventType.ACQUISITION,
            pre2018Date,
            new BigDecimal("100.0000"),
            new BigDecimal("20.0000"),
            new BigDecimal("2000.0000"),
            "doc1",
            Instant.now()
        );

        Map<String, BigDecimal> fmvMap = Map.of("INF879O01027", new BigDecimal("35.0000"));
        FifoMatcher.FifoResult result = matcher.processEvents(List.of(acq), fmvMap);

        assertEquals(1, result.openLots().size());
        var lot = result.openLots().get(0);
        assertTrue(lot.isGrandfathered(), "Lot acquired before 2018-01-31 must be grandfathered");
        assertEquals(0, new BigDecimal("35.0000").compareTo(lot.fmv20180131()));
    }

    @Test
    @DisplayName("FifoMatcher: Section 55(2)(ac) deemed cost on disposal uses max(cost, min(fmv, saleProceeds))")
    void testSection552acDeemedCostCalculation() {
        // Buy 100 units @ 20 in 2017. Total cost = 2,000.
        // FMV on 31-Jan-2018 = 35. Total FMV = 3,500.
        // Sell 100 units @ 50 in 2026. Sale proceeds = 5,000.
        // Deemed cost = max(2000, min(3500, 5000)) = 3,500.
        // Realized gain = 5,000 - 3,500 = 1,500.
        TaxEvent acq = new TaxEvent(
            UUID.randomUUID().toString(),
            "INF879O01027",
            "Parag Parikh Flexi Cap Fund",
            "INF879O01027",
            EventType.ACQUISITION,
            LocalDate.of(2017, 5, 10),
            new BigDecimal("100.0000"),
            new BigDecimal("20.0000"),
            new BigDecimal("2000.0000"),
            "doc1",
            Instant.now()
        );

        TaxEvent sell = new TaxEvent(
            UUID.randomUUID().toString(),
            "INF879O01027",
            "Parag Parikh Flexi Cap Fund",
            "INF879O01027",
            EventType.DISPOSAL,
            LocalDate.of(2026, 8, 15),
            new BigDecimal("100.0000"),
            new BigDecimal("50.0000"),
            new BigDecimal("5000.0000"),
            "doc2",
            Instant.now()
        );

        Map<String, BigDecimal> fmvMap = Map.of("INF879O01027", new BigDecimal("35.0000"));
        FifoMatcher.FifoResult result = matcher.processEvents(List.of(acq, sell), fmvMap);

        assertEquals(0, result.openLots().size());
        assertEquals(1, result.matchedLots().size());
        var match = result.matchedLots().get(0);

        assertEquals(TaxTerm.LONG_TERM, match.taxTerm());
        assertEquals(0, new BigDecimal("3500.0000").compareTo(match.costBasis()), "Cost basis must be deemed cost under Sec 55(2)(ac)");
        assertEquals(0, new BigDecimal("5000.0000").compareTo(match.saleProceeds()));
        assertEquals(0, new BigDecimal("1500.0000").compareTo(match.realizedGain()), "Realized gain must be saleProceeds - deemedCost");
    }

    @Test
    @DisplayName("FifoMatcher: Post-2018 acquisition is not grandfathered")
    void testPost2018AcquisitionNotGrandfathered() {
        TaxEvent acq = new TaxEvent(
            UUID.randomUUID().toString(),
            "INF879O01027",
            "Parag Parikh Flexi Cap Fund",
            "INF879O01027",
            EventType.ACQUISITION,
            LocalDate.of(2018, 5, 10),
            new BigDecimal("100.0000"),
            new BigDecimal("25.0000"),
            new BigDecimal("2500.0000"),
            "doc1",
            Instant.now()
        );

        Map<String, BigDecimal> fmvMap = Map.of("INF879O01027", new BigDecimal("35.0000"));
        FifoMatcher.FifoResult result = matcher.processEvents(List.of(acq), fmvMap);

        assertEquals(1, result.openLots().size());
        var lot = result.openLots().get(0);
        assertFalse(lot.isGrandfathered(), "Lot acquired after 2018-01-31 must not be grandfathered");
        assertEquals(0, BigDecimal.ZERO.compareTo(lot.fmv20180131()));
    }
}
