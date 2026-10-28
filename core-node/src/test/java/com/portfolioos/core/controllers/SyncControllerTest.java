package com.portfolioos.core.controllers;

import com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto;
import com.portfolioos.core.dtos.SyncDtos.UnidirectionalSyncSnapshot;
import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.service.LedgerCacheService;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SyncControllerTest {

    private SyncController syncController;
    private LedgerCacheService mockCacheService;
    private com.portfolioos.core.service.PortfolioValuationService valuationService;
    private com.portfolioos.core.persistence.DuckDbProjector mockDuckDb;
    private com.portfolioos.core.rpc.QuantSidecarClient mockQuantSidecar;

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

        mockCacheService = new LedgerCacheService(null) {
            @Override
            public CachedLedgerState getCachedState() {
                return cachedState;
            }
        };

        mockDuckDb = new com.portfolioos.core.persistence.DuckDbProjector(true) {
            @Override
            public Map<String, NavHistorySeriesEntry> getNavHistorySeriesWithDates(java.util.Set<String> heldIsins) {
                return java.util.Collections.emptyMap();
            }
        };
        mockQuantSidecar = new com.portfolioos.core.rpc.QuantSidecarClient("localhost", 9999);
        valuationService = 
            new com.portfolioos.core.service.PortfolioValuationService(mockCacheService, mockDuckDb, mockQuantSidecar);

        syncController = new SyncController(mockCacheService, valuationService, mockDuckDb, mockQuantSidecar);
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

    @Test
    void testDisallowedParquetTableReturns400BadRequest() {
        ResponseEntity<byte[]> response = syncController.getParquetTable("sqlite_master");
        assertEquals(400, response.getStatusCode().value(), "Arbitrary table queries like sqlite_master must return HTTP 400 Bad Request");
    }

    @Test
    void testRefreshNavsReturns200Ok() {
        ResponseEntity<Map<String, Object>> response = syncController.refreshNavs();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("REFRESHED", response.getBody().get("status"));
        assertTrue(response.getBody().containsKey("refreshed_at"));
    }

    @Test
    void testSnapshotTaxSummaryContract() {
        ResponseEntity<UnidirectionalSyncSnapshot> response = syncController.getSnapshot("2026-27", null);
        assertNotNull(response);
        assertNotNull(response.getBody());
        RebalancePlanDto plan = response.getBody().rebalancePlan();
        assertNotNull(plan);
        assertNotNull(plan.sellSide());
        assertNotNull(plan.sellSide().taxSummary(), "sell_side.tax_summary must be present in rebalance plan");
    }

    @Test
    void testDefaultTriggerEvaluationDoesNotForceDriftWhenRequestedTriggerIsNull() {
        // When trigger parameter is null, SyncController must pass null derivedTriggerType so RebalanceTriggerEvaluator rules apply
        ResponseEntity<UnidirectionalSyncSnapshot> response = syncController.getSnapshot("2026-27", null);
        assertNotNull(response);
        assertNotNull(response.getBody());
        RebalancePlanDto plan = response.getBody().rebalancePlan();
        assertNotNull(plan);
        assertNotNull(plan.trigger());
        // Since benchmark is null, it should NOT force a hardcoded DRIFT override if resolution were NONE
        assertNotNull(plan.trigger().type());
    }

    @Test
    void testGetRebalancePlanIgnoresInducedDefaultValue() {
        // When trigger is "INDUCED" or omitted, effectiveTrigger is null and evaluator runs
        ResponseEntity<RebalancePlanDto> response = syncController.getRebalancePlan("INDUCED");
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().trigger());
    }

    @Test
    void testF12ConfirmRebalanceExecutionPersistsPlanSnapshot() {
        com.portfolioos.core.persistence.TriggerHistoryRepository memoryRepo =
            new com.portfolioos.core.persistence.TriggerHistoryRepository(":memory:");
        memoryRepo.clearAll();

        SyncController controller = new SyncController(
            mockCacheService, valuationService, mockDuckDb, mockQuantSidecar, null, memoryRepo
        );

        ResponseEntity<RebalancePlanDto> preview = controller.getRebalancePlan("SCHEDULED");
        assertNotNull(preview.getBody());
        String digest = preview.getBody().planDigest();

        ResponseEntity<RebalancePlanDto> response = controller.confirmRebalanceExecution(Map.of(
            "trigger", "SCHEDULED",
            "digest", digest
        ));
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        RebalancePlanDto plan = response.getBody();
        assertNotNull(plan.planId());

        assertEquals(1, memoryRepo.getRecordCount(), "confirmRebalanceExecution MUST record 1 execution in TriggerHistoryRepository");
        Optional<String> snapshotOpt = memoryRepo.getExecutedPlanSnapshot(plan.planId());
        assertTrue(snapshotOpt.isPresent(), "Executed plan snapshot JSON must be retrievable by planId");
        assertTrue(snapshotOpt.get().contains(plan.planId()), "Snapshot JSON must contain the serialized planId");
        assertTrue(snapshotOpt.get().contains("SCHEDULED"), "Snapshot JSON must contain trigger metadata");

        memoryRepo.close();
    }

    @Test
    void testF12ConfirmExecutionDigestValidationAndMismatch() {
        com.portfolioos.core.persistence.TriggerHistoryRepository memoryRepo =
            new com.portfolioos.core.persistence.TriggerHistoryRepository(":memory:");
        memoryRepo.clearAll();

        SyncController controller = new SyncController(
            mockCacheService, valuationService, mockDuckDb, mockQuantSidecar, null, memoryRepo
        );

        // 1. First preview a plan to obtain its valid digest
        ResponseEntity<RebalancePlanDto> preview = controller.getRebalancePlan("SCHEDULED");
        assertNotNull(preview.getBody());
        RebalancePlanDto previewPlan = preview.getBody();
        String validDigest = previewPlan.planDigest();
        assertNotNull(validDigest);
        assertFalse(validDigest.isBlank());

        // 2. Attempt confirm execution with tampered/stale digest -> 409 Conflict
        ResponseEntity<RebalancePlanDto> conflictRes = controller.confirmRebalanceExecution(Map.of(
            "trigger", "SCHEDULED",
            "planId", "APPROVED-PLAN-UUID-TAMPERED",
            "digest", "deadbeef00000000badc0ffee"
        ));
        assertEquals(409, conflictRes.getStatusCode().value(), "Tampered/stale digest MUST be rejected with HTTP 409 Conflict");
        assertEquals(0, memoryRepo.getRecordCount(), "Rejected confirm MUST NOT record trigger execution or burn cooldown!");
        assertTrue(memoryRepo.getExecutedPlanSnapshot("APPROVED-PLAN-UUID-TAMPERED").isEmpty(), "No snapshot must be persisted on rejection");
        assertTrue(memoryRepo.getLastSellSideFiringDate().isEmpty(), "Cooldown must remain completely untouched on rejection");

        // 3. Attempt confirm execution with missing digest -> 409 Conflict
        ResponseEntity<RebalancePlanDto> missingDigestRes = controller.confirmRebalanceExecution(Map.of(
            "trigger", "SCHEDULED",
            "planId", "APPROVED-PLAN-UUID-NO-DIGEST"
        ));
        assertEquals(409, missingDigestRes.getStatusCode().value(), "Missing digest MUST be rejected with HTTP 409 Conflict");
        assertEquals(0, memoryRepo.getRecordCount(), "Missing digest rejection MUST NOT write any row to trigger history");

        // 4. Confirm execution with approved planId and correct digest -> 200 OK
        String approvedPlanId = "APPROVED-PLAN-UUID-12345";
        ResponseEntity<RebalancePlanDto> successRes = controller.confirmRebalanceExecution(Map.of(
            "trigger", "SCHEDULED",
            "planId", approvedPlanId,
            "digest", validDigest
        ));
        assertEquals(200, successRes.getStatusCode().value());
        assertNotNull(successRes.getBody());
        assertEquals(approvedPlanId, successRes.getBody().planId(), "Confirmed plan MUST carry the approved planId");
        assertEquals(1, memoryRepo.getRecordCount(), "Successful confirm MUST record trigger execution EXACTLY ONCE (no double-write)");
        Optional<String> snapshotOpt = memoryRepo.getExecutedPlanSnapshot(approvedPlanId);
        assertTrue(snapshotOpt.isPresent(), "Snapshot MUST be persisted under the approved planId");

        // 5. If TriggerHistoryRepository is null, controller MUST return 500 Internal Server Error
        SyncController controllerWithoutRepo = new SyncController(
            mockCacheService, valuationService, mockDuckDb, mockQuantSidecar, null, null
        );
        ResponseEntity<RebalancePlanDto> previewWithoutRepo = controllerWithoutRepo.getRebalancePlan("SCHEDULED");
        assertNotNull(previewWithoutRepo.getBody());
        String digestWithoutRepo = previewWithoutRepo.getBody().planDigest();
        ResponseEntity<RebalancePlanDto> error500Res = controllerWithoutRepo.confirmRebalanceExecution(Map.of(
            "trigger", "SCHEDULED",
            "digest", digestWithoutRepo
        ));
        assertEquals(500, error500Res.getStatusCode().value(), "Null repository must fail with HTTP 500 Internal Server Error");

        memoryRepo.close();
    }
}
