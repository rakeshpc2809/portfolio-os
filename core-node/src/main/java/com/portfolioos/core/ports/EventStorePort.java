package com.portfolioos.core.ports;

import com.portfolioos.core.model.TaxEvent;

import java.util.List;

public interface EventStorePort {
    String appendEvent(TaxEvent event);
    List<String> appendEvents(List<TaxEvent> events);
    List<TaxEvent> getEventsForAsset(String assetId);
    List<TaxEvent> getAllEvents();
    boolean verifyLedgerIntegrity();
    void clearAllEvents();
    String getLatestEventHash();
}
