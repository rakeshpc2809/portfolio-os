package com.portfolioos.core.integration;

import com.portfolioos.core.dtos.ParsedEventDto;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.service.StatementIngestionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StatementIngestionReconciliationGateIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private StatementIngestionUseCase ingestionUseCase;

    @BeforeEach
    void setUp() {
        seedCanonicalPortfolioState();
    }

    @Test
    @DisplayName("5.4 Invariant: Statement ingestion rejects corrupted transaction row (units * price != gross) with 0 ledger modification")
    void testCorruptedTransactionRowRejectedAtGateWithoutModifyingLedger() {
        int initialEventCount = eventStore.getAllEvents().size();
        String initialHash = eventStore.getLatestEventHash();

        ParsedEventDto corruptedDto = new ParsedEventDto(
            "EV_CORRUPT_1",
            ISIN_CORE_PPFAS,
            "Parag Parikh Flexi Cap Fund",
            ISIN_CORE_PPFAS,
            "ACQUISITION",
            "2026-05-10",
            new BigDecimal("100.000"), // 100 units
            new BigDecimal("50.000"),   // @ 50 nav -> expected gross = ₹5,000
            new BigDecimal("99999.00"), // corrupted gross amount: ₹99,999!
            "CORRUPTED_CAS_DOC",
            new BigDecimal("100.000")
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            ingestionUseCase.ingestParsedEvents(new ParsedEventDto[]{corruptedDto})
        );

        assertTrue(ex.getMessage().contains("Corrupted transaction row"));

        // Verify ledger isolation & complete rollback: 0 events added, HMAC hash intact
        List<TaxEvent> postEvents = eventStore.getAllEvents();
        assertEquals(initialEventCount, postEvents.size(), "Ledger event count must remain strictly unchanged on gate failure");
        assertEquals(initialHash, eventStore.getLatestEventHash(), "Ledger HMAC chain must remain untouched");
    }

    @Test
    @DisplayName("5.4 Invariant: Perturbed closing balance fails Reconciliation Gate with 0 ledger modification")
    void testPerturbedClosingBalanceFailsReconciliationGateWithoutModifyingLedger() {
        int initialEventCount = eventStore.getAllEvents().size();
        String initialHash = eventStore.getLatestEventHash();

        // Valid row math, but declared closing balance is perturbed (e.g. 500 units instead of expected post-FIFO units)
        ParsedEventDto perturbedBalanceDto = new ParsedEventDto(
            "EV_MISMATCH_1",
            ISIN_CORE_PPFAS,
            "Parag Parikh Flexi Cap Fund",
            ISIN_CORE_PPFAS,
            "ACQUISITION",
            "2026-06-01",
            new BigDecimal("10.000"),
            new BigDecimal("70.000"),
            new BigDecimal("700.00"),
            "CAS_DOC_PERTURBED",
            new BigDecimal("9999.999") // Declared closing units deliberately perturbed
        );

        com.portfolioos.core.reconciliation.ReconciliationGateFailureException ex =
            assertThrows(com.portfolioos.core.reconciliation.ReconciliationGateFailureException.class, () ->
                ingestionUseCase.ingestParsedEvents(new ParsedEventDto[]{perturbedBalanceDto})
            );

        assertNotNull(ex.getReconciliationResult());
        assertFalse(ex.getReconciliationResult().allMatched());

        // Verify ledger isolation & rollback: 0 events added, HMAC hash intact
        List<TaxEvent> postEvents = eventStore.getAllEvents();
        assertEquals(initialEventCount, postEvents.size(), "Ledger event count must remain unchanged when reconciliation gate fails");
        assertEquals(initialHash, eventStore.getLatestEventHash(), "Ledger HMAC chain must remain untouched");
    }
}
