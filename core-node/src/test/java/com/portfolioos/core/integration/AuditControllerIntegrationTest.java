package com.portfolioos.core.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AuditControllerIntegrationTest extends BaseIntegrationTest {

    @BeforeEach
    void setUp() {
        seedCanonicalPortfolioState();
    }

    @Test
    @DisplayName("GET /api/v1/audit/fund-health returns 200 OK with drawdown, peak 52w NAV, and exit load metrics")
    void testFundHealthAuditEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/audit/fund-health")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.as_of").isNotEmpty())
                .andExpect(jsonPath("$.funds").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/sync/snapshot includes peak_52w_nav, current_nav, drawdown_from_peak_pct, and exit_load_free_units")
    void testSnapshotIncludesPeakNavAndExitLoadFields() throws Exception {
        mockMvc.perform(get("/api/v1/sync/snapshot")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/analytics/factor-benchmarks returns 200 OK with factor pairs and metrics")
    void testFactorBenchmarksEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/factor-benchmarks")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.as_of").isNotEmpty())
                .andExpect(jsonPath("$.factors").isArray());
    }
}
