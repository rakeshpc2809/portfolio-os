package com.portfolioos.core.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto;
import com.portfolioos.core.dtos.ReportDtos.PortfolioSummaryResponse;
import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.service.LedgerCacheService;
import com.portfolioos.core.service.PortfolioValuationService;
import com.portfolioos.core.service.SimulationService;
import com.portfolioos.core.service.TaxOptimizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
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

        com.portfolioos.core.persistence.DuckDbProjector mockDuckDb = new com.portfolioos.core.persistence.DuckDbProjector(true) {
            @Override
            public Map<String, Object> getPairwiseFundOverlap(String fundA, String fundB) {
                Map<String, Object> overlap = new HashMap<>();
                overlap.put("fundA", fundA);
                overlap.put("fundB", fundB);
                overlap.put("overlap_percentage", 42.5);
                return overlap;
            }
        };

        com.portfolioos.core.rpc.QuantSidecarClient mockQuantSidecar = new com.portfolioos.core.rpc.QuantSidecarClient("localhost", 9999);

        PortfolioValuationService mockValuationService = new PortfolioValuationService(mockCacheService, mockDuckDb, mockQuantSidecar, null) {
            @Override
            public PortfolioSummaryResponse getPortfolioSummary(String fy) {
                return new PortfolioSummaryResponse(
                    "100000.00", "80000.00", "-20000.00", "0.00%", 1, 0
                );
            }
        };

        SimulationService mockSimulationService = new SimulationService(mockCacheService);

        TaxOptimizationService mockTaxService = new TaxOptimizationService(new EventStorePort() {
            @Override public String appendEvent(TaxEvent event) { return "EV_1"; }
            @Override public List<String> appendEvents(List<TaxEvent> events) { return List.of("EV_1"); }
            @Override public List<TaxEvent> getEventsForAsset(String assetId) { return List.of(acq); }
            @Override public List<TaxEvent> getAllEvents() { return List.of(acq); }
            @Override public boolean verifyLedgerIntegrity() { return true; }
            @Override public void clearAllEvents() {}
            @Override public String getLatestEventHash() { return "HASH"; }
        });

        queryTools = new PortfolioQueryTools(
            mockValuationService,
            mockTaxService,
            mockSimulationService,
            mockDuckDb,
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

    @Test
    void testToolDefinitionsSchemaDiscovery() throws Exception {
        List<ToolDtos.ToolDefinitionDto> definitions = queryTools.getToolDefinitions();
        assertNotNull(definitions);
        assertEquals(7, definitions.size(), "All 7 portfolio query tools must be registered");

        List<String> names = definitions.stream().map(ToolDtos.ToolDefinitionDto::name).toList();
        assertTrue(names.contains("getPortfolioValuation"));
        assertTrue(names.contains("getFundRegistry"));
        assertTrue(names.contains("getFireSummary"));
        assertTrue(names.contains("getRebalancePlan"));
        assertTrue(names.contains("getTaxHarvestOpportunities"));
        assertTrue(names.contains("getPairwiseFundOverlap"));
        assertTrue(names.contains("simulateTrade"));

        // Verify simulateTrade parameters and enum serialization
        ToolDtos.ToolDefinitionDto simTool = definitions.stream()
            .filter(d -> d.name().equals("simulateTrade"))
            .findFirst()
            .orElseThrow();
        assertEquals(5, simTool.parameters().required().size());
        assertEquals("DISPOSAL", simTool.parameters().properties().get("tradeType").enumValues().get(0));
        assertEquals("ACQUISITION", simTool.parameters().properties().get("tradeType").enumValues().get(1));

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(definitions);
        assertTrue(json.contains("\"enum\":[\"DISPOSAL\",\"ACQUISITION\"]"),
            "tradeType must serialize with JSON Schema key 'enum'");
        assertFalse(json.contains("\"enum\":null"),
            "Properties without enums must omit null enum field");
    }

    @Test
    void testExecuteToolDispatchParameterlessAndOverlap() {
        // Parameterless tools
        ToolDtos.ToolExecutionResponse valRes = queryTools.executeTool("getPortfolioValuation", Map.of());
        assertEquals("SUCCESS", valRes.status());
        assertNotNull(valRes.result());

        ToolDtos.ToolExecutionResponse regRes = queryTools.executeTool("getFundRegistry", null);
        assertEquals("SUCCESS", regRes.status());
        assertNotNull(regRes.result());

        ToolDtos.ToolExecutionResponse rebRes = queryTools.executeTool("getRebalancePlan", Map.of());
        assertEquals("SUCCESS", rebRes.status());
        assertNotNull(rebRes.result());

        ToolDtos.ToolExecutionResponse taxRes = queryTools.executeTool("getTaxHarvestOpportunities", Map.of());
        assertEquals("SUCCESS", taxRes.status());
        assertNotNull(taxRes.result());

        // Pairwise overlap - missing params
        ToolDtos.ToolExecutionResponse missRes = queryTools.executeTool("getPairwiseFundOverlap", Map.of("fundA", "INF109KC13X2"));
        assertEquals("INVALID_PARAM", missRes.status());

        // Pairwise overlap - non-existent fund
        ToolDtos.ToolExecutionResponse notFoundRes = queryTools.executeTool(
            "getPairwiseFundOverlap",
            Map.of("fundA", "INF109KC13X2", "fundB", "NON_EXISTENT_ISIN")
        );
        assertEquals("NOT_FOUND", notFoundRes.status());

        // Pairwise overlap - valid
        ToolDtos.ToolExecutionResponse validOverlap = queryTools.executeTool(
            "getPairwiseFundOverlap",
            Map.of("fundA", "INF109KC13X2", "fundB", "INF109KC13X2")
        );
        assertEquals("SUCCESS", validOverlap.status());
    }

    @Test
    void testExecuteToolSimulateTradeValidationAndCoercion() {
        // Invalid params: non-positive units
        ToolDtos.ToolExecutionResponse invalidUnits = queryTools.executeTool("simulateTrade", Map.of(
            "isin", "INF109KC13X2",
            "schemeName", "ICICI Nifty200",
            "units", -10,
            "pricePerUnit", 100,
            "tradeType", "DISPOSAL"
        ));
        assertEquals("INVALID_PARAM", invalidUnits.status());

        // Invalid params: unrecognized tradeType
        ToolDtos.ToolExecutionResponse invalidType = queryTools.executeTool("simulateTrade", Map.of(
            "isin", "INF109KC13X2",
            "schemeName", "ICICI Nifty200",
            "units", 10,
            "pricePerUnit", 100,
            "tradeType", "SWAP"
        ));
        assertEquals("INVALID_PARAM", invalidType.status());

        // Coercion test: Integer units, Double pricePerUnit
        ToolDtos.ToolExecutionResponse resInt = queryTools.executeTool("simulateTrade", Map.of(
            "isin", "INF109KC13X2",
            "schemeName", "ICICI Nifty200",
            "units", 50,
            "pricePerUnit", 85.50,
            "tradeType", "DISPOSAL"
        ));
        assertEquals("SUCCESS", resInt.status());
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadInt = (Map<String, Object>) resInt.result();
        SimulationService.TradeSimulationResult simInt = (SimulationService.TradeSimulationResult) payloadInt.get("simulation_result");
        assertNotNull(simInt);
        assertEquals(0, new BigDecimal("50").compareTo(simInt.units()));
        assertEquals(0, new BigDecimal("85.50").compareTo(simInt.pricePerUnit()));
        assertEquals(0, new BigDecimal("4275.00").compareTo(simInt.grossTradeAmount()));

        // Coercion test: Long units, String pricePerUnit
        ToolDtos.ToolExecutionResponse resLongStr = queryTools.executeTool("simulateTrade", Map.of(
            "isin", "INF109KC13X2",
            "schemeName", "ICICI Nifty200",
            "units", 100L,
            "pricePerUnit", "90.25",
            "tradeType", "ACQUISITION"
        ));
        assertEquals("SUCCESS", resLongStr.status());
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadLongStr = (Map<String, Object>) resLongStr.result();
        SimulationService.TradeSimulationResult simLongStr = (SimulationService.TradeSimulationResult) payloadLongStr.get("simulation_result");
        assertNotNull(simLongStr);
        assertEquals(0, new BigDecimal("100").compareTo(simLongStr.units()));
        assertEquals(0, new BigDecimal("90.25").compareTo(simLongStr.pricePerUnit()));
        assertEquals(0, new BigDecimal("9025.00").compareTo(simLongStr.grossTradeAmount()));
    }

    @Test
    void testExecuteToolUnknownAndInvalidToolHandling() {
        ToolDtos.ToolExecutionResponse nullTool = queryTools.executeTool(null, Map.of());
        assertEquals("INVALID_PARAM", nullTool.status());

        ToolDtos.ToolExecutionResponse blankTool = queryTools.executeTool("   ", Map.of());
        assertEquals("INVALID_PARAM", blankTool.status());

        ToolDtos.ToolExecutionResponse unknown = queryTools.executeTool("arbitraryUnknownTool", Map.of());
        assertEquals("NOT_FOUND", unknown.status());
        assertTrue(unknown.errorMessage().contains("Unknown tool: arbitraryUnknownTool"));
    }

    @Test
    void testParseBigDecimalCoercion() {
        assertNull(PortfolioQueryTools.parseBigDecimal(null));
        assertNull(PortfolioQueryTools.parseBigDecimal("not_a_number"));

        assertEquals(new BigDecimal("100"), PortfolioQueryTools.parseBigDecimal(100));
        assertEquals(BigDecimal.valueOf(9999999999L), PortfolioQueryTools.parseBigDecimal(9999999999L));
        assertEquals(BigDecimal.valueOf((short) 12), PortfolioQueryTools.parseBigDecimal((short) 12));
        assertEquals(BigDecimal.valueOf((byte) 3), PortfolioQueryTools.parseBigDecimal((byte) 3));
        assertEquals(BigDecimal.valueOf(123.45), PortfolioQueryTools.parseBigDecimal(123.45));
        assertEquals(BigDecimal.valueOf((double) 67.5f), PortfolioQueryTools.parseBigDecimal(67.5f));
        assertEquals(new BigDecimal("456.78"), PortfolioQueryTools.parseBigDecimal("  456.78  "));
        assertEquals(new BigDecimal("500.00"), PortfolioQueryTools.parseBigDecimal(new BigDecimal("500.00")));
    }
}
