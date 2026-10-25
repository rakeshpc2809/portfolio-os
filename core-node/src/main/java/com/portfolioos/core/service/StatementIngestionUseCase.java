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

        java.util.Map<String, Integer> dayOccurrences = new java.util.HashMap<>();
        List<TaxEvent> incomingEvents = new java.util.ArrayList<>();
        java.util.Set<String> incomingFingerprints = new java.util.HashSet<>();

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

            LocalDate eventDate = LocalDate.parse(dto.eventDate());
            EventType eventType = EventType.valueOf(dto.eventType());
            String folio = dto.folioNo();
            String isin = dto.isin();
            String occKey = (folio != null ? folio : "") + "|" + (isin != null ? isin : "") + "|" + eventDate + "|" + eventType.name() + "|" + dto.grossAmount().toPlainString() + "|" + dto.units().toPlainString();
            int occurrenceIdx = dayOccurrences.getOrDefault(occKey, 0);
            dayOccurrences.put(occKey, occurrenceIdx + 1);

            String fingerprint = dto.txFingerprint();
            if (fingerprint == null || fingerprint.isBlank()) {
                fingerprint = SqliteEventStore.computeTxFingerprint(folio, isin, eventDate, eventType, dto.grossAmount(), dto.units(), occurrenceIdx);
            }
            incomingFingerprints.add(fingerprint);

            TaxEvent te = new TaxEvent(
                dto.id() != null ? dto.id() : UUID.randomUUID().toString(),
                dto.assetId(),
                dto.assetName(),
                dto.isin(),
                folio,
                eventType,
                eventDate,
                dto.units(),
                dto.pricePerUnit(),
                dto.grossAmount(),
                dto.sourceDocumentId(),
                Instant.now(),
                fingerprint
            );
            incomingEvents.add(te);
        }

        Map<String, BigDecimal> declaredBalances = new java.util.HashMap<>();
        Map<String, String> assetToFolio = new java.util.HashMap<>();
        for (ParsedEventDto dto : dtoList) {
            if (dto.declaredClosingUnits() != null && dto.assetId() != null) {
                declaredBalances.put(dto.assetId(), dto.declaredClosingUnits());
                if (dto.folioNo() != null && !dto.folioNo().isBlank()) {
                    assetToFolio.put(dto.assetId(), dto.folioNo());
                }
            }
        }

        synchronized (ingestionLock) {
            java.util.Set<String> existingFps = eventStore.getExistingFingerprints(incomingFingerprints);
            List<TaxEvent> newTaxEvents = incomingEvents.stream()
                .filter(e -> !existingFps.contains(e.txFingerprint()))
                .toList();

            if (!declaredBalances.isEmpty()) {
                // Combine existing ledger events with ONLY new non-duplicate events
                List<TaxEvent> combined = new ArrayList<>(eventStore.getAllEvents());
                combined.addAll(newTaxEvents);
                com.portfolioos.core.matcher.FifoMatcher fifoMatcher = new com.portfolioos.core.matcher.FifoMatcher();
                com.portfolioos.core.matcher.FifoMatcher.FifoResult fifoResult = fifoMatcher.processEvents(
                    combined,
                    duckDbProjector != null ? duckDbProjector.getJan2018FmvMap() : Map.of()
                );
                com.portfolioos.core.reconciliation.ReconciliationGate.MultiAssetReconciliationResult recon =
                    com.portfolioos.core.reconciliation.ReconciliationGate.validateStatementPerAsset(fifoResult, declaredBalances);

                // Build and persist reconciliation_log entries immediately (Fix #2: do not lose MISMATCH on rollback)
                Instant now = Instant.now();
                List<com.portfolioos.core.ports.EventStorePort.ReconciliationRecord> records = new ArrayList<>();
                for (com.portfolioos.core.reconciliation.ReconciliationGate.AssetReconciliationResult ar : recon.assetResults()) {
                    String isin = ar.assetId();
                    String folio = assetToFolio.getOrDefault(isin, "");
                    BigDecimal casUnits = ar.declaredUnits().setScale(3, java.math.RoundingMode.HALF_UP);
                    BigDecimal ledgerUnits = ar.calculatedUnits().setScale(3, java.math.RoundingMode.HALF_UP);
                    BigDecimal delta = ar.delta().setScale(3, java.math.RoundingMode.HALF_UP);
                    String status = ar.isMatched() ? "MATCHED" : "MISMATCH";
                    records.add(new com.portfolioos.core.ports.EventStorePort.ReconciliationRecord(now, isin, folio, casUnits, ledgerUnits, delta, status));
                }

                eventStore.recordReconciliationLog(records);

                if (!recon.allMatched()) {
                    throw new com.portfolioos.core.reconciliation.ReconciliationGateFailureException(recon);
                }
            }

            if (!newTaxEvents.isEmpty()) {
                // Dual-write step 1: Write to primary SQLite Ledger (Fix #1: sequential hash chain for new events)
                eventStore.appendEvents(newTaxEvents);

                try {
                    // Dual-write step 2: Re-project events in DuckDB analytical database
                    List<TaxEvent> allEvents = eventStore.getAllEvents();
                    duckDbProjector.projectEvents(allEvents);
                } catch (Exception e) {
                    System.err.println("ALERT: Analytical DuckDB projection failed post-ledger commit. Ledger remains intact: " + e.getMessage());
                    throw new RuntimeException("Dual-write failure: Analytical DuckDB projection failed (ledger committed intact): " + e.getMessage(), e);
                }

                // Evict/Invalidate central ledger cache
                cacheService.invalidateCache();
            }

            return newTaxEvents;
        }
    }
}
