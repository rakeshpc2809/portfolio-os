package com.portfolioos.core.matcher;

import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.TaxEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FifoMatcherFolioAndFailLoudTest {

    @Test
    @DisplayName("Regression 3A: Disposals match on (assetId, folioNo) so separate folios do not cross-contaminate")
    void testTwoFoliosSameIsinFifoMatching() {
        FifoMatcher matcher = new FifoMatcher();

        TaxEvent acqF1 = new TaxEvent(
            "EV1", "INF879O01027", "PPFAS Flexi Cap", "INF879O01027", "FOLIO-1",
            EventType.ACQUISITION, LocalDate.of(2024, 1, 1),
            new BigDecimal("100.000"), new BigDecimal("50.00"), new BigDecimal("5000.00"),
            "DOC1", Instant.now()
        );

        TaxEvent acqF2 = new TaxEvent(
            "EV2", "INF879O01027", "PPFAS Flexi Cap", "INF879O01027", "FOLIO-2",
            EventType.ACQUISITION, LocalDate.of(2024, 1, 2),
            new BigDecimal("100.000"), new BigDecimal("60.00"), new BigDecimal("6000.00"),
            "DOC2", Instant.now()
        );

        TaxEvent dispF2 = new TaxEvent(
            "EV3", "INF879O01027", "PPFAS Flexi Cap", "INF879O01027", "FOLIO-2",
            EventType.DISPOSAL, LocalDate.of(2025, 2, 1),
            new BigDecimal("50.000"), new BigDecimal("100.00"), new BigDecimal("5000.00"),
            "DOC3", Instant.now()
        );

        FifoMatcher.FifoResult result = matcher.processEvents(List.of(acqF1, acqF2, dispF2));

        assertEquals(1, result.matchedLots().size());
        assertEquals(new BigDecimal("50.000"), result.matchedLots().get(0).unitsMatched());

        List<Lot> openLots = result.openLots();
        assertEquals(2, openLots.size());

        Lot openF1 = openLots.stream().filter(l -> "FOLIO-1".equals(l.folioNo())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("100.000"), openF1.remainingUnits(), "Folio 1 must remain completely untouched");

        Lot openF2 = openLots.stream().filter(l -> "FOLIO-2".equals(l.folioNo())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("50.000"), openF2.remainingUnits(), "Folio 2 must have 50 units remaining");
    }

    @Test
    @DisplayName("Regression 3A: FifoMatcher throws IllegalStateException on excess disposal units")
    void testExcessDisposalUnitsThrowsIllegalStateException() {
        FifoMatcher matcher = new FifoMatcher();

        TaxEvent acqF1 = new TaxEvent(
            "EV1", "INF879O01027", "PPFAS Flexi Cap", "INF879O01027", "FOLIO-1",
            EventType.ACQUISITION, LocalDate.of(2024, 1, 1),
            new BigDecimal("100.000"), new BigDecimal("50.00"), new BigDecimal("5000.00"),
            "DOC1", Instant.now()
        );

        TaxEvent dispExcess = new TaxEvent(
            "EV2", "INF879O01027", "PPFAS Flexi Cap", "INF879O01027", "FOLIO-1",
            EventType.DISPOSAL, LocalDate.of(2025, 2, 1),
            new BigDecimal("150.000"), new BigDecimal("100.00"), new BigDecimal("15000.00"),
            "DOC2", Instant.now()
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            matcher.processEvents(List.of(acqF1, dispExcess))
        );
        assertTrue(ex.getMessage().contains("Unmatched disposal units remain") || ex.getMessage().contains("INF879O01027"));
    }
}
