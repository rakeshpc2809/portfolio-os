package com.portfolioos.core.backup;

import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.ports.EventStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service orchestrating one-way, append-only backup synchronization
 * from the local SQLite/DuckDB event store to Google Sheets.
 */
@Service
public class GoogleSheetsBackupService {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsBackupService.class);
    public static final String SYNC_TARGET_SHEETS = "GOOGLE_SHEETS_LEDGER_EVENTS";
    public static final String TAB_NAME = "ledger_events";
    public static final int BATCH_SIZE = 500;
    public static final long DEFAULT_INTER_BATCH_DELAY_MS = 1000L;

    private final EventStorePort eventStore;
    private final GoogleSheetsClient sheetsClient;
    private final String spreadsheetId;
    private final long interBatchDelayMs;

    public GoogleSheetsBackupService(
        EventStorePort eventStore,
        GoogleSheetsClient sheetsClient,
        @Value("${google.sheets.backup.spreadsheet-id:}") String spreadsheetId,
        @Value("${google.sheets.backup.inter-batch-delay-ms:1000}") long interBatchDelayMs
    ) {
        this.eventStore = eventStore;
        this.sheetsClient = sheetsClient;
        this.spreadsheetId = spreadsheetId != null && !spreadsheetId.isBlank()
            ? spreadsheetId
            : System.getenv("GOOGLE_SHEETS_BACKUP_SPREADSHEET_ID");
        this.interBatchDelayMs = interBatchDelayMs;
    }

    /**
     * Converts a TaxEvent into flat columns matching ledger_events tab schema:
     * event_id, date, isin, type, units, nav, amount, synced_at
     */
    public List<Object> mapEventToRow(TaxEvent event) {
        String eventId = event.id();
        String date = event.eventDate() != null ? event.eventDate().toString() : "";
        String isin = event.isin() != null ? event.isin() : "";
        String type = event.eventType() != null ? event.eventType().name() : "";
        String units = event.units() != null ? event.units().toPlainString() : "0";
        String nav = event.pricePerUnit() != null ? event.pricePerUnit().toPlainString() : "0";
        String amount = event.grossAmount() != null ? event.grossAmount().toPlainString() : "0";
        String syncedAt = Instant.now().toString();

        return List.of(eventId, date, isin, type, units, nav, amount, syncedAt);
    }

    /**
     * Non-blocking trigger called after CAS statement ingestion commit.
     */
    public CompletableFuture<SyncResult> triggerAsyncIncrementalBackup() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return syncIncrementalEvents();
            } catch (Exception e) {
                log.warn("Isolated failure during async Google Sheets backup sync: {}", e.getMessage(), e);
                return new SyncResult(false, 0, "Failed: " + e.getMessage());
            }
        });
    }

    /**
     * Synchronously performs incremental backup of unsynced events past checkpoint.
     */
    public synchronized SyncResult syncIncrementalEvents() {
        if (spreadsheetId == null || spreadsheetId.isBlank()) {
            log.info("Google Sheets backup skipped: GOOGLE_SHEETS_BACKUP_SPREADSHEET_ID is not configured.");
            return new SyncResult(false, 0, "SPREADSHEET_ID_NOT_CONFIGURED");
        }

        String lastCheckpoint = eventStore.getBackupSyncCheckpoint(SYNC_TARGET_SHEETS);
        List<TaxEvent> unbacked = eventStore.getEventsAfter(lastCheckpoint);

        if (unbacked.isEmpty()) {
            log.debug("Google Sheets backup: Checkpoint up-to-date (0 unsynced events).");
            return new SyncResult(true, 0, "ALREADY_UP_TO_DATE");
        }

        log.info("Google Sheets backup: Syncing {} unsynced events (checkpoint: {})...", unbacked.size(), lastCheckpoint);
        int rowsAppended = writeEventsInBatches(unbacked, false);

        String newestId = unbacked.get(unbacked.size() - 1).id();
        eventStore.updateBackupSyncCheckpoint(SYNC_TARGET_SHEETS, newestId, rowsAppended);
        log.info("Google Sheets backup: Successfully appended {} events. New checkpoint: {}", rowsAppended, newestId);

        return new SyncResult(true, rowsAppended, "SYNC_COMPLETE");
    }

    /**
     * One-time bulk seed of all ledger events with 500-row batching, pacing, and 429 backoff.
     */
    public synchronized SyncResult seedAllLedgerEvents() {
        if (spreadsheetId == null || spreadsheetId.isBlank()) {
            throw new IllegalStateException("Cannot seed Google Sheets backup: GOOGLE_SHEETS_BACKUP_SPREADSHEET_ID not configured.");
        }

        List<TaxEvent> all = eventStore.getAllEvents();
        if (all.isEmpty()) {
            return new SyncResult(true, 0, "LEDGER_EMPTY");
        }

        log.info("Starting one-time bulk seed of {} ledger events to Google Sheets tab '{}'...", all.size(), TAB_NAME);
        int rowsAppended = writeEventsInBatches(all, true);

        String newestId = all.get(all.size() - 1).id();
        eventStore.updateBackupSyncCheckpoint(SYNC_TARGET_SHEETS, newestId, rowsAppended);
        log.info("One-time bulk seed complete: {} rows appended. Checkpoint updated to {}.", rowsAppended, newestId);

        return new SyncResult(true, rowsAppended, "SEED_COMPLETE");
    }

    private int writeEventsInBatches(List<TaxEvent> events, boolean applyPacing) {
        int totalRowsAppended = 0;
        int total = events.size();

        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, total);
            List<TaxEvent> batch = events.subList(i, end);

            List<List<Object>> rows = new ArrayList<>(batch.size());
            for (TaxEvent te : batch) {
                rows.add(mapEventToRow(te));
            }

            int appended = executeAppendWithRetry(rows);
            totalRowsAppended += appended;

            // Inter-batch pacing to avoid 300 writes/minute rate limit
            if (applyPacing && end < total) {
                try {
                    sheetsClient.pace(interBatchDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Inter-batch pacing interrupted", ie);
                }
            }
        }

        return totalRowsAppended;
    }

    private int executeAppendWithRetry(List<List<Object>> rows) {
        int maxRetries = 3;
        long backoffMs = 2000L;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return sheetsClient.appendRows(spreadsheetId, TAB_NAME + "!A:H", rows);
            } catch (Exception e) {
                boolean isRateLimit = e.getMessage() != null &&
                    (e.getMessage().contains("429") || e.getMessage().toLowerCase().contains("quota") || e.getMessage().toLowerCase().contains("rate"));

                if (attempt < maxRetries && isRateLimit) {
                    log.warn("Rate limit / 429 encountered appending to Google Sheets (attempt {}/{}). Backing off for {}ms...", attempt, maxRetries, backoffMs);
                    try {
                        sheetsClient.pace(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Backoff interrupted", ie);
                    }
                    backoffMs *= 2; // exponential backoff
                } else if (attempt < maxRetries) {
                    log.warn("Error appending to Google Sheets (attempt {}/{}): {}. Retrying...", attempt, maxRetries, e.getMessage());
                    try {
                        sheetsClient.pace(1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry wait interrupted", ie);
                    }
                } else {
                    if (e instanceof RuntimeException re) throw re;
                    throw new RuntimeException("Failed to append rows to Google Sheets after " + maxRetries + " attempts", e);
                }
            }
        }
        return 0;
    }

    public record SyncResult(boolean success, int rowsSynced, String message) {}
}
