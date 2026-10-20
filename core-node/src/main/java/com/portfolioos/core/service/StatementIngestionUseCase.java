package com.portfolioos.core.service;

import com.portfolioos.core.dtos.ParsedEventDto;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.persistence.SqliteEventStore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StatementIngestionUseCase {

    private final com.portfolioos.core.ports.EventStorePort eventStore;
    private final DuckDbProjector duckDbProjector;
    private final LedgerCacheService cacheService;

    public StatementIngestionUseCase(
        com.portfolioos.core.ports.EventStorePort eventStore,
        DuckDbProjector duckDbProjector,
        LedgerCacheService cacheService
    ) {
        this.eventStore = eventStore;
        this.duckDbProjector = duckDbProjector;
        this.cacheService = cacheService;
    }

    private final Object ingestionLock = new Object();

    public List<TaxEvent> ingestParsedEvents(ParsedEventDto[] dtoList) {
        if (dtoList == null || dtoList.length == 0) {
            return List.of();
        }

        List<TaxEvent> taxEvents = new ArrayList<>();
        for (ParsedEventDto dto : dtoList) {
            if (dto.units() == null || dto.units().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Corrupted transaction row: units must be positive. Found: " + dto.units());
            }
            if (dto.pricePerUnit() == null || dto.pricePerUnit().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Corrupted transaction row: pricePerUnit must be positive. Found: " + dto.pricePerUnit());
            }
            if (dto.grossAmount() == null || dto.grossAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Corrupted transaction row: grossAmount must be positive. Found: " + dto.grossAmount());
            }
            BigDecimal expectedGross = dto.units().multiply(dto.pricePerUnit());
            BigDecimal grossDelta = expectedGross.subtract(dto.grossAmount()).abs();
            BigDecimal maxAllowedDelta = expectedGross.multiply(new BigDecimal("0.02")).max(new BigDecimal("10.00"));
            if (grossDelta.compareTo(maxAllowedDelta) > 0) {
                throw new IllegalArgumentException("Corrupted transaction row: units (" + dto.units() + ") * price (" + dto.pricePerUnit() + ") = " + expectedGross + " diverges from declared grossAmount (" + dto.grossAmount() + ") by " + grossDelta);
            }

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

        Map<String, BigDecimal> declaredBalances = new java.util.HashMap<>();
        for (ParsedEventDto dto : dtoList) {
            if (dto.declaredClosingUnits() != null && dto.assetId() != null) {
                declaredBalances.put(dto.assetId(), dto.declaredClosingUnits());
            }
        }

        synchronized (ingestionLock) {
            if (!declaredBalances.isEmpty()) {
                List<TaxEvent> combined = new ArrayList<>(eventStore.getAllEvents());
                combined.addAll(taxEvents);
                com.portfolioos.core.matcher.FifoMatcher fifoMatcher = new com.portfolioos.core.matcher.FifoMatcher();
                com.portfolioos.core.matcher.FifoMatcher.FifoResult fifoResult = fifoMatcher.processEvents(combined);
                com.portfolioos.core.reconciliation.ReconciliationGate.MultiAssetReconciliationResult recon =
                    com.portfolioos.core.reconciliation.ReconciliationGate.validateStatementPerAsset(fifoResult, declaredBalances);
                if (!recon.allMatched()) {
                    throw new com.portfolioos.core.reconciliation.ReconciliationGateFailureException(recon);
                }
            }

            // Dual-write step 1: Write to primary SQLite Ledger
            eventStore.appendEvents(taxEvents);

            try {
                // Dual-write step 2: Re-project events in DuckDB analytical database
                List<TaxEvent> allEvents = eventStore.getAllEvents();
                duckDbProjector.projectEvents(allEvents);
            } catch (Exception e) {
                // SQLite ledger commit is immutable. Never delete events from the HMAC chain.
                System.err.println("ALERT: Analytical DuckDB projection failed post-ledger commit. Ledger remains intact: " + e.getMessage());
                throw new RuntimeException("Dual-write failure: Analytical DuckDB projection failed (ledger committed intact): " + e.getMessage(), e);
            }

            // Evict/Invalidate central ledger cache
            cacheService.invalidateCache();

            return taxEvents;
        }
    }
}
