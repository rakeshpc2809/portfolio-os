package com.portfolioos.core.integration;

import com.portfolioos.core.dtos.ParsedEventDto;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.reconciliation.ReconciliationGateFailureException;
import com.portfolioos.core.service.StatementIngestionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IdempotentLedgerReconciliationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private StatementIngestionUseCase ingestionUseCase;

    @BeforeEach
    void setUp() {
        eventStore.clearAllEvents();
        // Clear reconciliation log before each test
        try (var conn = ((com.portfolioos.core.persistence.SqliteEventStore) eventStore).getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM reconciliation_log");
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Phase 1: Uploading identical CAS PDF transactions yields inserted: 0 on second run and 0.000 delta in reconciliation_log")
    void testDoubleUploadIdenticalCasYieldsZeroInsertedAndZeroDeltaInReconciliationLog() {
        ParsedEventDto[] batch = new ParsedEventDto[]{
            new ParsedEventDto(
                "EV_PPFAS_1",
                ISIN_CORE_PPFAS,
                "Parag Parikh Flexi Cap Fund Direct Growth",
                ISIN_CORE_PPFAS,
                "1234567/89",
                "ACQUISITION",
                "2026-04-10",
                new BigDecimal("100.000"),
                new BigDecimal("50.000"),
                new BigDecimal("5000.00"),
                "TEST_CAS_IDENTICAL.pdf",
                new BigDecimal("100.000"),
                null
            ),
            new ParsedEventDto(
                "EV_LARGEMID_1",
                ISIN_CORE_LARGEMID,
                "ICICI Prudential Nifty LargeMidcap 250 Index Fund Direct Growth",
                ISIN_CORE_LARGEMID,
                "9876543/21",
                "ACQUISITION",
                "2026-04-15",
                new BigDecimal("200.000"),
                new BigDecimal("25.000"),
                new BigDecimal("5000.00"),
                "TEST_CAS_IDENTICAL.pdf",
                new BigDecimal("200.000"),
                null
            )
        };

        // First run: Both events should be inserted
        List<TaxEvent> firstInserted = ingestionUseCase.ingestParsedEvents(batch);
        assertEquals(2, firstInserted.size(), "First upload must insert all 2 transactions");
        assertEquals(2, eventStore.getAllEvents().size(), "SQLite ledger must contain 2 events");

        // Second run with identical CAS PDF batch
        List<TaxEvent> secondInserted = ingestionUseCase.ingestParsedEvents(batch);
        assertEquals(0, secondInserted.size(), "Second upload of identical CAS must yield inserted: 0");
        assertEquals(2, eventStore.getAllEvents().size(), "Ledger row count must strictly remain 2");

        // Verify reconciliation_log has committed entries with 0.000 delta across all folios
        List<EventStorePort.ReconciliationRecord> logs = eventStore.getReconciliationLogs();
        assertFalse(logs.isEmpty(), "Reconciliation log must contain records");
        for (EventStorePort.ReconciliationRecord r : logs) {
            assertEquals("MATCHED", r.status(), "Status must be MATCHED for all assets");
            assertEquals(new BigDecimal("0.000"), r.delta(), "Delta must be strictly 0.000 across all assets");
        }
    }

    @Test
    @DisplayName("Fix #1: Overlapping CAS upload computes previous_hash sequentially starting from latest committed event_hash")
    void testOverlappingCasUploadComputesSequentialPreviousHash() {
        // Step 1: Ingest base event A
        ParsedEventDto eventA = new ParsedEventDto(
            "EV_A",
            ISIN_CORE_PPFAS,
            "Parag Parikh Flexi Cap Fund",
            ISIN_CORE_PPFAS,
            "12345/67",
            "ACQUISITION",
            "2026-01-10",
            new BigDecimal("50.000"),
            new BigDecimal("50.000"),
            new BigDecimal("2500.00"),
            "STATEMENT_JAN.pdf",
            new BigDecimal("50.000"),
            null
        );
        List<TaxEvent> resA = ingestionUseCase.ingestParsedEvents(new ParsedEventDto[]{eventA});
        assertEquals(1, resA.size());
        String hashA = eventStore.getLatestEventHash();
        assertNotNull(hashA);
        assertNotEquals("GENESIS", hashA);

        // Step 2: Ingest overlapping statement containing event A (duplicate) + new event B
        ParsedEventDto eventB = new ParsedEventDto(
            "EV_B",
            ISIN_CORE_PPFAS,
            "Parag Parikh Flexi Cap Fund",
            ISIN_CORE_PPFAS,
            "12345/67",
            "ACQUISITION",
            "2026-02-10",
            new BigDecimal("50.000"),
            new BigDecimal("55.000"),
            new BigDecimal("2750.00"),
            "STATEMENT_FEB_OVERLAPPING.pdf",
            new BigDecimal("100.000"),
            null
        );

        List<TaxEvent> resB = ingestionUseCase.ingestParsedEvents(new ParsedEventDto[]{eventA, eventB});
        assertEquals(1, resB.size(), "Only event B must be inserted; duplicate event A skipped");

        List<TaxEvent> allEvents = eventStore.getAllEvents();
        assertEquals(2, allEvents.size());

        // Verify HMAC cryptographic chain integrity
        assertTrue(eventStore.verifyLedgerIntegrity(), "Cryptographic ledger HMAC chain must remain 100% valid post-deduplication");

        // Verify Event B's previous_hash directly equals hash A
        try (var conn = ((com.portfolioos.core.persistence.SqliteEventStore) eventStore).getConnection();
             var stmt = conn.prepareStatement("SELECT previous_hash, event_hash FROM tax_events WHERE asset_id = ? AND event_date = '2026-02-10'")) {
            stmt.setString(1, ISIN_CORE_PPFAS);
            var rs = stmt.executeQuery();
            assertTrue(rs.next());
            assertEquals(hashA, rs.getString("previous_hash"), "Event B's previous_hash must chain directly from latest committed hash A");
        } catch (Exception e) {
            fail("Database inspection failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Fix #2: Reconciliation mismatch commits to reconciliation_log without being rolled back, keeping ledger intact")
    void testReconciliationMismatchPersistsLogEntryWithoutModifyingLedger() {
        ParsedEventDto validDto = new ParsedEventDto(
            "EV_VALID",
            ISIN_CORE_PPFAS,
            "Parag Parikh Flexi Cap Fund",
            ISIN_CORE_PPFAS,
            "F100",
            "ACQUISITION",
            "2026-03-01",
            new BigDecimal("10.000"),
            new BigDecimal("100.000"),
            new BigDecimal("1000.00"),
            "CAS_INITIAL.pdf",
            new BigDecimal("10.000"),
            null
        );
        ingestionUseCase.ingestParsedEvents(new ParsedEventDto[]{validDto});
        int initialEventCount = eventStore.getAllEvents().size();
        assertEquals(1, initialEventCount);

        // Upload batch with mismatched declared closing units (declared 999.000 instead of 20.000)
        ParsedEventDto mismatchDto = new ParsedEventDto(
            "EV_MISMATCH",
            ISIN_CORE_PPFAS,
            "Parag Parikh Flexi Cap Fund",
            ISIN_CORE_PPFAS,
            "F100",
            "ACQUISITION",
            "2026-04-01",
            new BigDecimal("10.000"),
            new BigDecimal("100.000"),
            new BigDecimal("1000.00"),
            "CAS_MISMATCH.pdf",
            new BigDecimal("999.000"),
            null
        );

        assertThrows(ReconciliationGateFailureException.class, () ->
            ingestionUseCase.ingestParsedEvents(new ParsedEventDto[]{mismatchDto})
        );

        // Ledger must be completely isolated and unmodified
        assertEquals(initialEventCount, eventStore.getAllEvents().size(), "Ledger event count must remain strictly unchanged on gate failure");

        // Fix #2 Verification: reconciliation_log MUST have the MISMATCH entry committed
        List<EventStorePort.ReconciliationRecord> logs = eventStore.getReconciliationLogs();
        assertFalse(logs.isEmpty(), "Reconciliation log must NOT be empty after mismatch");
        EventStorePort.ReconciliationRecord latestLog = logs.get(0);
        assertEquals("MISMATCH", latestLog.status(), "Reconciliation log status must be recorded as MISMATCH");
        assertTrue(latestLog.delta().compareTo(new BigDecimal("0.001")) > 0, "Delta must record the divergence");
    }

    @Test
    @DisplayName("Fix #2: Verification that 3-decimal place unit comparisons work cleanly")
    void testThreeDecimalPlacesUnitComparison() {
        ParsedEventDto dto = new ParsedEventDto(
            "EV_DEC",
            ISIN_CORE_PPFAS,
            "Parag Parikh Flexi Cap Fund",
            ISIN_CORE_PPFAS,
            "F100",
            "ACQUISITION",
            "2026-03-01",
            new BigDecimal("100.123"),
            new BigDecimal("50.000"),
            new BigDecimal("5006.15"),
            "CAS_DEC.pdf",
            new BigDecimal("100.123"),
            null
        );
        List<TaxEvent> inserted = ingestionUseCase.ingestParsedEvents(new ParsedEventDto[]{dto});
        assertEquals(1, inserted.size());

        List<EventStorePort.ReconciliationRecord> logs = eventStore.getReconciliationLogs();
        assertFalse(logs.isEmpty());
        assertEquals("MATCHED", logs.get(0).status());
        assertEquals(new BigDecimal("0.000"), logs.get(0).delta());
        assertEquals(new BigDecimal("100.123"), logs.get(0).casUnits());
        assertEquals(new BigDecimal("100.123"), logs.get(0).ledgerUnits());
    }

    @Test
    @DisplayName("Verification: Cumulative Folio Reconciliation compares CAS declared closing units against cumulative SQLite balance")
    void testCumulativeReconciliationAcrossHistoricalEvents() {
        // Step 1: Event 1 (+100 units) is already in SQLite (with or without folioNo)
        ParsedEventDto event1 = new ParsedEventDto(
            "EV_CUMULATIVE_1",
            ISIN_CORE_PPFAS,
            "Parag Parikh Flexi Cap Fund",
            ISIN_CORE_PPFAS,
            "12345/67",
            "ACQUISITION",
            "2026-01-10",
            new BigDecimal("100.000"),
            new BigDecimal("50.000"),
            new BigDecimal("5000.00"),
            "STATEMENT_JAN.pdf",
            new BigDecimal("100.000"),
            null
        );
        List<TaxEvent> inserted1 = ingestionUseCase.ingestParsedEvents(new ParsedEventDto[]{event1});
        assertEquals(1, inserted1.size());
        assertEquals(1, eventStore.getAllEvents().size());

        // Step 2: Upload 2 introduces Event 2 (+50 units) only, with declared_closing_units = 150.000
        ParsedEventDto event2 = new ParsedEventDto(
            "EV_CUMULATIVE_2",
            ISIN_CORE_PPFAS,
            "Parag Parikh Flexi Cap Fund",
            ISIN_CORE_PPFAS,
            "12345/67",
            "ACQUISITION",
            "2026-02-10",
            new BigDecimal("50.000"),
            new BigDecimal("60.000"),
            new BigDecimal("3000.00"),
            "STATEMENT_FEB.pdf",
            new BigDecimal("150.000"),
            null
        );
        List<TaxEvent> inserted2 = ingestionUseCase.ingestParsedEvents(new ParsedEventDto[]{event2});
        assertEquals(1, inserted2.size(), "Event 2 must be inserted");
        assertEquals(2, eventStore.getAllEvents().size(), "Ledger must now contain both Event 1 and Event 2");

        // Verify reconciliation_log records MATCHED with delta 0.000 comparing 150.000 vs 150.000
        List<EventStorePort.ReconciliationRecord> logs = eventStore.getReconciliationLogs();
        assertFalse(logs.isEmpty());
        EventStorePort.ReconciliationRecord latestLog = logs.get(0);
        assertEquals("MATCHED", latestLog.status(), "Cumulative reconciliation must pass as MATCHED");
        assertEquals(new BigDecimal("150.000"), latestLog.casUnits(), "CAS units must be 150.000");
        assertEquals(new BigDecimal("150.000"), latestLog.ledgerUnits(), "Cumulative ledger units must be 150.000");
        assertEquals(new BigDecimal("0.000"), latestLog.delta(), "Delta must be strictly 0.000");
    }
}
