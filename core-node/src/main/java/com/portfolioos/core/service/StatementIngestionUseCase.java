package com.portfolioos.core.service;

import com.portfolioos.core.dtos.ParsedEventDto;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.persistence.SqliteEventStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class StatementIngestionUseCase {

    private final com.portfolioos.core.ports.EventStorePort eventStore;
    private final DuckDbProjector duckDbProjector;
    private final LedgerCacheService cacheService;
    private final com.portfolioos.core.backup.GoogleSheetsBackupService sheetsBackupService;

    public StatementIngestionUseCase(
        com.portfolioos.core.ports.EventStorePort eventStore,
        DuckDbProjector duckDbProjector,
        LedgerCacheService cacheService,
        com.portfolioos.core.backup.GoogleSheetsBackupService sheetsBackupService
    ) {
        this.eventStore = eventStore;
        this.duckDbProjector = duckDbProjector;
        this.cacheService = cacheService;
        this.sheetsBackupService = sheetsBackupService;
    }

    public List<TaxEvent> ingestParsedEvents(ParsedEventDto[] dtoList) {
        if (dtoList == null || dtoList.length == 0) {
            return List.of();
        }

        List<TaxEvent> taxEvents = new ArrayList<>();
        for (ParsedEventDto dto : dtoList) {
            TaxEvent te = new TaxEvent(
                dto.id() != null ? dto.id() : UUID.randomUUID().toString(),
                dto.assetId(),
                dto.assetName(),
                dto.isin(),
                EventType.valueOf(dto.eventType()),
                LocalDate.parse(dto.eventDate()),
                dto.units(),
                dto.pricePerUnit(),
                dto.grossAmount(),
                dto.sourceDocumentId(),
                Instant.now()
            );
            taxEvents.add(te);
        }

        // Dual-write step 1: Write to primary SQLite Ledger
        eventStore.appendEvents(taxEvents);

        try {
            // Dual-write step 2: Re-project events in DuckDB analytical database
            List<TaxEvent> allEvents = eventStore.getAllEvents();
            duckDbProjector.projectEvents(allEvents);
        } catch (Exception e) {
            System.err.println("CRITICAL: DuckDB projection failed during statement ingestion: " + e.getMessage());
            throw new RuntimeException("Dual-write failure: Analytical DuckDB projection failed: " + e.getMessage(), e);
        }

        // Evict/Invalidate central ledger cache
        cacheService.invalidateCache();

        // Non-blocking asynchronous backup sync to Google Sheets (failures never block or fail ledger commit)
        if (sheetsBackupService != null) {
            sheetsBackupService.triggerAsyncIncrementalBackup();
        }

        return taxEvents;
    }
}
