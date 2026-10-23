package com.portfolioos.core.ports;

import com.portfolioos.core.model.TaxEvent;

import java.util.List;

public interface EventStorePort {
    record ReconciliationRecord(
        java.time.Instant runAt,
        String isin,
        String folio,
        java.math.BigDecimal casUnits,
        java.math.BigDecimal ledgerUnits,
        java.math.BigDecimal delta,
        String status
    ) {}

    String appendEvent(TaxEvent event);
    List<String> appendEvents(List<TaxEvent> events);
    List<TaxEvent> getEventsForAsset(String assetId);
    List<TaxEvent> getAllEvents();
    boolean verifyLedgerIntegrity();
    void clearAllEvents();
    String getLatestEventHash();
    default void deleteEvents(List<String> eventIds) {}
    default void recordReconciliationLog(List<ReconciliationRecord> records) {}
    default List<ReconciliationRecord> getReconciliationLogs() { return List.of(); }
    default java.util.Set<String> getExistingFingerprints(java.util.Set<String> fingerprints) { return java.util.Set.of(); }
    default boolean isFingerprintPresent(String fingerprint) { return false; }
}
