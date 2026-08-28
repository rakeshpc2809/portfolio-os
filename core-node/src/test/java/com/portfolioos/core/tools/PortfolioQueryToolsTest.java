package com.portfolioos.core.tools;

import com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto;
import com.portfolioos.core.dtos.ReportDtos.PortfolioSummaryResponse;
import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.service.LedgerCacheService;
import com.portfolioos.core.service.PortfolioValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioQueryToolsTest {

    private PortfolioQueryTools queryTools;

    @BeforeEach
    void setUp() {
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

        PortfolioValuationService mockValuationService = new PortfolioValuationService(mockCacheService) {
            @Override
            public PortfolioSummaryResponse getPortfolioSummary(String fy) {
                return new PortfolioSummaryResponse(
                    "100000.00", "80000.00", "-20000.00", "0.00%", 1, 0
                );
            }
        };

        DuckDbProjector duckDbProjector = new DuckDbProjector(":memory:");

        queryTools = new PortfolioQueryTools(
            mockValuationService,
            null,
            null,
            duckDbProjector,
            mockCacheService
        );
    }

    @Test
    void testSite3LlmToolGetRebalancePlanDisarmsDrawdown() {
        Map<String, Object> result = queryTools.getRebalancePlan();
        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals("getRebalancePlan", result.get("source_tool"));

        Object triggerObj = result.get("trigger");
        assertNotNull(triggerObj);
        
        com.portfolioos.core.dtos.RebalancePlanDtos.RebalanceTriggerDto trigger = 
            (com.portfolioos.core.dtos.RebalancePlanDtos.RebalanceTriggerDto) triggerObj;

        assertEquals("NONE", trigger.drawdownContext().armedTier(),
            "Site 3 LLM tool getRebalancePlan must disarm drawdown to NONE when no live benchmark feed is wired");
        assertEquals(0.0, trigger.drawdownContext().currentDrawdownPct(),
            "Site 3 LLM tool getRebalancePlan must report 0.0% drawdown when benchmark is null");
    }

    @Test
    void testRegressionNoPersonalNetWorthPassedAsBenchmarkParamInQueryTools() throws Exception {
        File file = new File("src/main/java/com/portfolioos/core/tools/PortfolioQueryTools.java");
        assertTrue(file.exists());
        String content = Files.readString(file.toPath());

        assertFalse(content.contains("buildPreviewPlan(\n            state.fifoResult().openLots(),\n            state.fifoResult().matchedLots(),\n            state.navMap(),\n            LocalDate.now(),\n            currentVal,\n            rollingHigh,"),
            "Site 3 must not pass rollingHigh into benchmark parameter slot of buildPreviewPlan");
    }
}
