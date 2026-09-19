package com.portfolioos.core.controllers;

import com.portfolioos.core.integration.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AgentToolsControllerTest extends BaseIntegrationTest {

    @BeforeEach
    void setUp() {
        seedCanonicalPortfolioState();
    }

    @Test
    @DisplayName("Fail-Loud: Missing X-Api-Auth-Token returns HTTP 401 on GET /api/v1/agent/tools")
    void testMissingAuthTokenFailsLoudOnGetTools() throws Exception {
        mockMvc.perform(get("/api/v1/agent/tools")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Unauthorized: Missing or invalid X-Api-Auth-Token header or token parameter."));
    }

    @Test
    @DisplayName("Fail-Loud: Missing X-Api-Auth-Token returns HTTP 401 on POST /api/v1/agent/tools/execute")
    void testMissingAuthTokenFailsLoudOnExecute() throws Exception {
        mockMvc.perform(post("/api/v1/agent/tools/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tool\":\"getPortfolioValuation\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Unauthorized: Missing or invalid X-Api-Auth-Token header or token parameter."));
    }

    @Test
    @DisplayName("Valid X-Api-Auth-Token returns HTTP 200 with all 7 tool definitions")
    void testValidAuthTokenGetToolsSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/agent/tools")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(7))
            .andExpect(jsonPath("$[?(@.name == 'getPortfolioValuation')]").exists())
            .andExpect(jsonPath("$[?(@.name == 'simulateTrade')]").exists());
    }

    @Test
    @DisplayName("Valid X-Api-Auth-Token executes tool and returns HTTP 200 with result")
    void testValidAuthTokenExecuteToolSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/agent/tools/execute")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tool\":\"getPortfolioValuation\",\"arguments\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tool").value("getPortfolioValuation"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.result").exists());
    }

    @Test
    @DisplayName("Unknown tool name returns HTTP 200 with NOT_FOUND status")
    void testExecuteToolUnknownToolNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/agent/tools/execute")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tool\":\"nonExistentTool\",\"arguments\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tool").value("nonExistentTool"))
            .andExpect(jsonPath("$.status").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error_message").value("Unknown tool: nonExistentTool"));
    }

    @Test
    @DisplayName("Null or blank request returns HTTP 400 Bad Request")
    void testExecuteToolNullBodyBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/agent/tools/execute")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Valid X-Api-Auth-Token executes simulateTrade tool and returns SUCCESS with tax details")
    void testExecuteSimulateTradeSucceeds() throws Exception {
        String reqJson = """
            {
              "tool": "simulateTrade",
              "arguments": {
                "isin": "INF879O01027",
                "schemeName": "Parag Parikh Flexi Cap Fund - Direct Plan - Growth",
                "units": 50,
                "pricePerUnit": 84.50,
                "tradeType": "DISPOSAL"
              }
            }
            """;
        mockMvc.perform(post("/api/v1/agent/tools/execute")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reqJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tool").value("simulateTrade"))
            .andExpect(jsonPath("$.result.simulation_result.trade_type").value("DISPOSAL"))
            .andExpect(jsonPath("$.result.simulation_result.units").value(50.0))
            .andExpect(jsonPath("$.result.simulation_result.gross_trade_amount").value(4225.0))
            .andExpect(jsonPath("$.result.price_source").value("EXPLICIT_PARAMETER"))
            .andExpect(jsonPath("$.result.is_price_estimated").value(false));
    }

    @Test
    @DisplayName("simulateTrade resolves live NAV from ledger navMap when pricePerUnit is omitted")
    void testExecuteSimulateTradeResolvesLiveNavFromLedgerCache() throws Exception {
        String reqJson = """
            {
              "tool": "simulateTrade",
              "arguments": {
                "isin": "INF879O01027",
                "schemeName": "Parag Parikh Flexi Cap Fund - Direct Plan - Growth",
                "units": 50,
                "tradeType": "DISPOSAL"
              }
            }
            """;
        mockMvc.perform(post("/api/v1/agent/tools/execute")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reqJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tool").value("simulateTrade"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.result.price_source").value("LIVE_LEDGER_NAV"))
            .andExpect(jsonPath("$.result.is_price_estimated").value(false))
            .andExpect(jsonPath("$.result.simulation_result.units").value(50.0))
            .andExpect(jsonPath("$.result.simulation_result.price_per_unit").isNumber());
    }

    @Test
    @DisplayName("Valid X-Api-Auth-Token executes getRebalancePlan tool and returns trigger & waterfall steps")
    void testExecuteGetRebalancePlanSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/agent/tools/execute")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tool\":\"getRebalancePlan\",\"arguments\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tool").value("getRebalancePlan"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.result.trigger").exists())
            .andExpect(jsonPath("$.result.sell_side").exists());
    }

    @Test
    @DisplayName("Valid X-Api-Auth-Token executes getFireSummary tool and returns FIRE metrics")
    void testExecuteGetFireSummarySucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/agent/tools/execute")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tool\":\"getFireSummary\",\"arguments\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tool").value("getFireSummary"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.result.required_fire_corpus").exists())
            .andExpect(jsonPath("$.result.fire_status").exists());
    }
}
