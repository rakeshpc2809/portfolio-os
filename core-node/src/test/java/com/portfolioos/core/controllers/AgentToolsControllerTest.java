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
}
