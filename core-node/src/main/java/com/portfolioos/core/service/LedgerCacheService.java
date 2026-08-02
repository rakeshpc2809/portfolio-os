package com.portfolioos.core.service;

import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.nav.AmfiNavSync;
import com.portfolioos.core.ports.EventStorePort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class LedgerCacheService {

    private final EventStorePort eventStore;
    private final AmfiNavSync amfiSync = new AmfiNavSync();
    private final FifoMatcher fifoMatcher = new FifoMatcher();

    private String cachedHash = null;
    private long lastNavSyncTime = 0L;
    private List<TaxEvent> cachedEvents = null;
    private FifoMatcher.FifoResult cachedResult = null;
    private Map<String, BigDecimal> cachedNavMap = null;
    private final Object lock = new Object();

    public LedgerCacheService(EventStorePort eventStore) {
        this.eventStore = eventStore;
    }

    public static record CachedLedgerState(
        List<TaxEvent> events,
        FifoMatcher.FifoResult fifoResult,
        Map<String, BigDecimal> navMap,
        String ledgerHash
    ) {}

    public CachedLedgerState getCachedState() {
        synchronized (lock) {
            String currentHash = eventStore.getLatestEventHash();
            long now = System.currentTimeMillis();

            if (cachedResult == null || !currentHash.equals(cachedHash) || (now - lastNavSyncTime) > 30_000) {
                cachedEvents = eventStore.getAllEvents();
                cachedResult = fifoMatcher.processEvents(cachedEvents);
                cachedNavMap = amfiSync.getNavMap();
                cachedHash = currentHash;
                lastNavSyncTime = now;
            }

            return new CachedLedgerState(cachedEvents, cachedResult, cachedNavMap, cachedHash);
        }
    }

    public void invalidateCache() {
        synchronized (lock) {
            cachedHash = null;
            cachedEvents = null;
            cachedResult = null;
            cachedNavMap = null;
        }
    }
}
