package com.portfolioos.core.backup;

import com.portfolioos.core.dtos.ParsedEventDto;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.persistence.SqliteEventStore;
import com.portfolioos.core.service.LedgerCacheService;
import com.portfolioos.core.service.StatementIngestionUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class GoogleSheetsBackupServiceTest {

    private String tempDbPath;
    private SqliteEventStore eventStore;

    @BeforeEach
    void setUp() {
        tempDbPath = "target/test_backup_" + UUID.randomUUID() + ".db";
        System.setProperty("LEDGER_HMAC_SECRET", "test_hmac_secret_key_123");
        // Ensure env has fallback or property
        eventStore = new SqliteEventStore(tempDbPath);
    }

    @AfterEach
    void tearDown() {
        File f = new File(tempDbPath);
        if (f.exists()) f.delete();
    }

    private TaxEvent createMockEvent(String id, String isin, String units, String nav, String amount) {
        return new TaxEvent(
            id,
            isin,
            "Test Fund " + isin,
            isin,
            EventType.ACQUISITION,
            LocalDate.of(2026, 1, 15),
            new BigDecimal(units),
            new BigDecimal(nav),
            new BigDecimal(amount),
            "CAS_DOC_001",
            Instant.now()
        );
    }

    @Test
    @DisplayName("Durable Checkpoint & Idempotency: Unsynced events synced, second sync skips, checkpoint persists")
    void testDurableCheckpointAndIdempotency() {
        List<List<List<Object>>> appendCalls = new ArrayList<>();
        GoogleSheetsClient mockClient = (spreadsheetId, range, rows) -> {
            appendCalls.add(new ArrayList<>(rows));
            return rows.size();
        };

        GoogleSheetsBackupService service = new GoogleSheetsBackupService(
            eventStore, mockClient, "mock_sheet_123", 0L
        );

        // Append 3 events to SQLite
        TaxEvent e1 = createMockEvent("EVT-1", "INF109K0101", "100.0", "50.0", "5000.0");
        TaxEvent e2 = createMockEvent("EVT-2", "INF109K0102", "200.0", "25.0", "5000.0");
        TaxEvent e3 = createMockEvent("EVT-3", "INF109K0103", "300.0", "10.0", "3000.0");
        eventStore.appendEvents(List.of(e1, e2, e3));

        // First sync
        GoogleSheetsBackupService.SyncResult r1 = service.syncIncrementalEvents();
        assertTrue(r1.success());
        assertEquals(3, r1.rowsSynced());
        assertEquals(1, appendCalls.size());
        assertEquals(3, appendCalls.get(0).size());

        // Verify SQLite durable checkpoint state
        String checkpoint = eventStore.getBackupSyncCheckpoint(GoogleSheetsBackupService.SYNC_TARGET_SHEETS);
        assertEquals("EVT-3", checkpoint);

        // Second sync immediately - should skip since already up-to-date
        GoogleSheetsBackupService.SyncResult r2 = service.syncIncrementalEvents();
        assertTrue(r2.success());
        assertEquals(0, r2.rowsSynced());
        assertEquals(1, appendCalls.size(), "No additional append calls should be made when up to date");

        // Simulate Service Restart by creating brand new service instance against same DB
        GoogleSheetsBackupService restartedService = new GoogleSheetsBackupService(
            eventStore, mockClient, "mock_sheet_123", 0L
        );
        GoogleSheetsBackupService.SyncResult r3 = restartedService.syncIncrementalEvents();
        assertTrue(r3.success());
        assertEquals(0, r3.rowsSynced());
        assertEquals(1, appendCalls.size(), "Restarted service must read durable checkpoint from SQLite and not re-append");

        // Add 1 more event
        TaxEvent e4 = createMockEvent("EVT-4", "INF109K0104", "50.0", "100.0", "5000.0");
        eventStore.appendEvent(e4);

        GoogleSheetsBackupService.SyncResult r4 = restartedService.syncIncrementalEvents();
        assertTrue(r4.success());
        assertEquals(1, r4.rowsSynced());
        assertEquals(2, appendCalls.size());
        assertEquals(1, appendCalls.get(1).size());
        assertEquals("EVT-4", eventStore.getBackupSyncCheckpoint(GoogleSheetsBackupService.SYNC_TARGET_SHEETS));
    }

    @Test
    @DisplayName("Batching, Pacing & Rate-Limit: 1,200 events -> 3 batches (500 + 500 + 200) with explicit inter-batch pacing")
    void testBatchingAndPacingOnSeed() {
        List<Integer> batchSizes = new ArrayList<>();
        AtomicInteger paceCount = new AtomicInteger(0);
        AtomicLong totalPacedMillis = new AtomicLong(0);

        GoogleSheetsClient mockClient = new GoogleSheetsClient() {
            @Override
            public int appendRows(String spreadsheetId, String range, List<List<Object>> rows) {
                batchSizes.add(rows.size());
                return rows.size();
            }

            @Override
            public void pace(long millis) {
                paceCount.incrementAndGet();
                totalPacedMillis.addAndGet(millis);
            }
        };

        // Create 1,200 events
        List<TaxEvent> manyEvents = new ArrayList<>();
        for (int i = 1; i <= 1200; i++) {
            manyEvents.add(createMockEvent("EVT-" + i, "INF879O01" + i, "10.0", "50.0", "500.0"));
        }
        eventStore.appendEvents(manyEvents);

        GoogleSheetsBackupService service = new GoogleSheetsBackupService(
            eventStore, mockClient, "mock_sheet_123", 1000L
        );

        GoogleSheetsBackupService.SyncResult result = service.seedAllLedgerEvents();
        assertTrue(result.success());
        assertEquals(1200, result.rowsSynced());

        // Assert 3 batches: 500, 500, 200
        assertEquals(List.of(500, 500, 200), batchSizes, "1,200 events must be split into 500, 500, 200 batches");

        // Assert pacing between batches (2 inter-batch spaces for 3 batches)
        assertEquals(2, paceCount.get(), "Must execute pacing delay between consecutive batches");
        assertEquals(2000L, totalPacedMillis.get(), "Total paced milliseconds must match 2 x 1000ms delay");
    }

    @Test
    @DisplayName("Failure Isolation Guarantee: Sheets API exception never blocks local ledger commit")
    void testSheetsFailureNeverBlocksLedgerCommit() throws Exception {
        java.util.concurrent.CountDownLatch failureAttempted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean failurePathRan = new java.util.concurrent.atomic.AtomicBoolean(false);

        // Mock sheets client throwing 503 Service Unavailable / IOException and triggering the countdown latch
        GoogleSheetsClient failingClient = (spreadsheetId, range, rows) -> {
            failurePathRan.set(true);
            failureAttempted.countDown();
            throw new IOException("Google Sheets API 503 Service Unavailable - quota exhausted");
        };

        GoogleSheetsBackupService failingService = new GoogleSheetsBackupService(
            eventStore, failingClient, "mock_sheet_123", 0L
        );

        LedgerCacheService cacheService = new LedgerCacheService(eventStore);
        DuckDbProjector projector = new DuckDbProjector();

        StatementIngestionUseCase useCase = new StatementIngestionUseCase(
            eventStore, projector, cacheService, failingService
        );

        ParsedEventDto[] input = new ParsedEventDto[]{
            new ParsedEventDto(
                "CAS-EVENT-101", "INF109KC13X2", "Parag Parikh Flexi Cap", "INF109KC13X2",
                "ACQUISITION", "2026-02-10", new BigDecimal("150.00"), new BigDecimal("75.20"),
                new BigDecimal("11280.00"), "CAS_DOC_TEST"
            )
        };

        // Execution of CAS parse and commit MUST succeed synchronously with no exception thrown to caller
        assertDoesNotThrow(() -> {
            List<TaxEvent> committed = useCase.ingestParsedEvents(input);
            assertNotNull(committed);
            assertEquals(1, committed.size());
        });

        // Await the asynchronous backup failure execution to prove it actively ran and threw
        boolean finished = failureAttempted.await(5, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(finished, "Async backup failure path must execute within timeout");
        assertTrue(failurePathRan.get(), "Failing client append must have been called");

        // Local SQLite ledger records the event
        List<TaxEvent> storedEvents = eventStore.getAllEvents();
        assertEquals(1, storedEvents.size());
        assertEquals("CAS-EVENT-101", storedEvents.get(0).id());
    }
}
