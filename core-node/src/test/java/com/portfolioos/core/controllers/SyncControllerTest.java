package com.portfolioos.core.controllers;

import com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto;
import com.portfolioos.core.dtos.SyncDtos.UnidirectionalSyncSnapshot;
import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.service.LedgerCacheService;
import com.portfolioos.core.persistence.DuckDbProjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SyncControllerTest {

    private SyncController syncController;

    @BeforeEach
    void setUp() {
        // Seed open lots for asset INF109KC13X2 (ICICI Nifty 200)
        TaxEvent acq = new TaxEvent(
            "EV_ACQ_1",
            "INF109KC13X2",
            "ICICI Nifty200",
            "INF109KC13X2",
            EventType.ACQUISITION,
            LocalDate.of(2024, 1, 1),
            new BigDecimal("1000.0"),
            new BigDecimal("100.0"),
            new BigDecimal("100000.0"),
            "CAS_IMPORT",
            Instant.now()
        );

        FifoMatcher matcher = new FifoMatcher();
        FifoMatcher.FifoResult fifoResult = matcher.processEvents(List.of(acq));

        // NAV dropped to 80.0 (20% drop in personal portfolio valuation from cost of 100.0)
        Map<String, BigDecimal> navMap = Map.of("INF109KC13X2", new BigDecimal("80.0"));

        LedgerCacheService.CachedLedgerState cachedState = new LedgerCacheService.CachedLedgerState(
            List.of(acq),
            fifoResult,
            navMap,
            "HASH_TEST",
            System.currentTimeMillis(),
            "HEALTHY"
        );

        LedgerCacheService mockCacheService = new LedgerCacheService(null) {
            @Override
            public CachedLedgerState getCachedState() {
                return cachedState;
            }
        };

        syncController = new SyncController(mockCacheService, new DuckDbProjector(":memory:"));
    }

    @Test
    void testSite1SnapshotDisarmsDrawdownWhenBenchmarkNull() {
        ResponseEntity<UnidirectionalSyncSnapshot> response = syncController.getSnapshot("2026-27", null);
        assertNotNull(response);
        assertNotNull(response.getBody());

        RebalancePlanDto plan = response.getBody().rebalancePlan();
        assertNotNull(plan);
        assertEquals("NONE", plan.trigger().drawdownContext().armedTier(),
            "Site 1 /getSnapshot must disarm drawdown to NONE when no live benchmark market feed is wired");
        assertEquals(0.0, plan.trigger().drawdownContext().currentDrawdownPct(),
            "Site 1 /getSnapshot must report 0.0% drawdown when benchmark is null");
    }

    @Test
    void testSite2RebalancePlanDisarmsDrawdownWhenBenchmarkNull() {
        ResponseEntity<RebalancePlanDto> response = syncController.getRebalancePlan("INDUCED");
        assertNotNull(response);
        assertNotNull(response.getBody());

        RebalancePlanDto plan = response.getBody();
        assertEquals("NONE", plan.trigger().drawdownContext().armedTier(),
            "Site 2 /getRebalancePlan must disarm drawdown to NONE when no live benchmark market feed is wired");
        assertEquals(0.0, plan.trigger().drawdownContext().currentDrawdownPct(),
            "Site 2 /getRebalancePlan must report 0.0% drawdown when benchmark is null");
    }

    @Test
    void testConsistencyBetweenSnapshotAndRebalancePlanEndpoints() {
        UnidirectionalSyncSnapshot snapshot = syncController.getSnapshot("2026-27", null).getBody();
        RebalancePlanDto plan = syncController.getRebalancePlan("INDUCED").getBody();

        assertNotNull(snapshot);
        assertNotNull(plan);

        assertEquals(snapshot.rebalancePlan().trigger().drawdownContext().armedTier(), plan.trigger().drawdownContext().armedTier(),
            "Site 1 and Site 2 endpoints must return identical armedTier");
        assertEquals(snapshot.rebalancePlan().trigger().drawdownContext().currentDrawdownPct(), plan.trigger().drawdownContext().currentDrawdownPct(),
            "Site 1 and Site 2 endpoints must return identical currentDrawdownPct");
    }

    @Test
    void testRegressionNoPersonalNetWorthPassedAsBenchmarkParam() throws Exception {
        File file = new File("src/main/java/com/portfolioos/core/controllers/SyncController.java");
        assertTrue(file.exists());
        String content = Files.readString(file.toPath());

        assertFalse(content.contains("buildPlan(\n            openLots, matchedLots, navMap, LocalDate.now(), totalPortfolioCurrentVal, rollingHigh,"),
            "Site 1 must not pass rollingHigh into benchmark parameter slot");
        assertFalse(content.contains("buildPreviewPlan(\n            openLots, matchedLots, navMap, LocalDate.now(), totalCurrentVal, rollingHigh,"),
            "Site 2 must not pass rollingHigh into benchmark parameter slot");
    }
}
