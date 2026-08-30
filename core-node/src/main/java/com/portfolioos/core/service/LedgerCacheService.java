package com.portfolioos.core.service;

import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.nav.AmfiNavSync;
import com.portfolioos.core.ports.EventStorePort;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LedgerCacheService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LedgerCacheService.class);

    private final EventStorePort eventStore;
    private final AmfiNavSync amfiSync;
    private final FifoMatcher fifoMatcher;

    private final AtomicReference<CachedLedgerState> stateHolder = new AtomicReference<>(null);
    private volatile long lastNavSyncTime = 0L;
    private final Object updateLock = new Object();

    @org.springframework.beans.factory.annotation.Autowired
    public LedgerCacheService(EventStorePort eventStore) {
        this(eventStore, new AmfiNavSync(), new FifoMatcher());
    }

    public LedgerCacheService(
        EventStorePort eventStore,
        AmfiNavSync amfiSync,
        FifoMatcher fifoMatcher
    ) {
        this.eventStore = eventStore;
        this.amfiSync = amfiSync;
        this.fifoMatcher = fifoMatcher;
    }

    public static record CachedLedgerState(
        List<TaxEvent> events,
        FifoMatcher.FifoResult fifoResult,
        Map<String, BigDecimal> navMap,
        String ledgerHash,
        long lastNavFreshnessTimestamp,
        String healthStatus // HEALTHY, DEGRADED_AMFI_TIMEOUT
    ) {}

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedRate = 30000)
    public void refreshCacheInBackground() {
        synchronized (updateLock) {
            String health = "HEALTHY";
            try {
                String currentHash = eventStore.getLatestEventHash();
                long now = System.currentTimeMillis();

                CachedLedgerState current = stateHolder.get();
                if (current == null || current.ledgerHash() == null || !currentHash.equals(current.ledgerHash()) || (now - lastNavSyncTime) >= 30_000) {
                    List<TaxEvent> events = eventStore.getAllEvents();
                    FifoMatcher.FifoResult fifoResult = fifoMatcher.processEvents(events);
                    Map<String, BigDecimal> navMap = null;
                    try {
                        navMap = amfiSync.getNavMap();
                        if (navMap == null || navMap.isEmpty()) {
                            log.warn("AMFI_NAV_SYNC_ALERT: navMap returned empty or null after AMFI sync attempt!");
                            health = "DEGRADED_AMFI_EMPTY";
                        }
                    } catch (Exception amfiEx) {
                        log.warn("AMFI_NAV_SYNC_ALERT: Exception during AMFI NAV sync: {}", amfiEx.getMessage());
                        health = "DEGRADED_AMFI_TIMEOUT";
                        navMap = current != null ? current.navMap() : java.util.Collections.emptyMap();
                    }
                    
                    stateHolder.set(new CachedLedgerState(events, fifoResult, navMap, currentHash, now, health));
                    lastNavSyncTime = now;
                }
            } catch (Exception e) {
                System.err.println("Background cache refresh warning: " + e.getMessage());
            }
        }
    }

    public CachedLedgerState getCachedState() {
        CachedLedgerState current = stateHolder.get();
        if (current == null) {
            refreshCacheInBackground();
            current = stateHolder.get();
        }
        if (current == null) {
            current = new CachedLedgerState(
                Collections.emptyList(),
                new FifoMatcher.FifoResult(Collections.emptyList(), Collections.emptyList()),
                Collections.emptyMap(),
                "",
                System.currentTimeMillis(),
                "INITIALIZING"
            );
        }
        return current;
    }

    public void setCachedState(CachedLedgerState state) {
        synchronized (updateLock) {
            this.stateHolder.set(state);
            this.lastNavSyncTime = System.currentTimeMillis();
        }
    }

    public void invalidateCache() {
        stateHolder.set(null);
        refreshCacheInBackground();
    }
}
