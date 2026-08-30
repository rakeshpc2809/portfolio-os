package com.portfolioos.core.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SyncControllerIntegrationTest extends BaseIntegrationTest {

    @BeforeEach
    void setUp() {
        seedCanonicalPortfolioState();
    }

    @Test
    @DisplayName("GET /api/v1/sync/snapshot returns complete schema and disarmed drawdown when benchmark null")
    void testGetSnapshotIntegration() throws Exception {
        mockMvc.perform(get("/api/v1/sync/snapshot")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sync_info").exists())
            .andExpect(jsonPath("$.holdings").isArray())
            .andExpect(jsonPath("$.tax_lots").isArray())
            .andExpect(jsonPath("$.radar_signals").isArray())
            .andExpect(jsonPath("$.rebalance_plan").exists())
            .andExpect(jsonPath("$.rebalance_plan.trigger").exists());
    }

    @Test
    @DisplayName("GET /api/v1/sync/portfolio/bucket-allocation aggregates 4 canonical buckets to v2.3 targets (50/30/10/10)")
    void testBucketAllocationIntegration() throws Exception {
        mockMvc.perform(get("/api/v1/sync/portfolio/bucket-allocation")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.bucket == 'EQUITY_CORE')].target_pct").value(hasItem("50.00")))
            .andExpect(jsonPath("$[?(@.bucket == 'EQUITY_SATELLITE')].target_pct").value(hasItem("30.00")))
            .andExpect(jsonPath("$[?(@.bucket == 'GOLD_SILVER')].target_pct").value(hasItem("10.00")))
            .andExpect(jsonPath("$[?(@.bucket == 'LIQUID_BUFFER')].target_pct").value(hasItem("10.00")));
    }

    @Test
    @DisplayName("GET /api/v1/sync/rebalance/plan executes Step 0 legacy fund liquidation waterfall")
    void testRebalancePlanStep0LegacyWaterfall() throws Exception {
        mockMvc.perform(get("/api/v1/sync/rebalance/plan")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .param("trigger", "INDUCED")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trigger.type").exists())
            .andExpect(jsonPath("$.sell_side.waterfall").isArray())
            .andExpect(jsonPath("$.buy_side.buckets").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/sync/rebalance/simulate-lumpsum correctly incorporates capital injection and redeployment")
    void testSimulateLumpsumIntegration() throws Exception {
        String requestJson = """
            {
                "amount": 50000.00,
                "includeRebalance": true
            }
            """;

        mockMvc.perform(post("/api/v1/sync/rebalance/simulate-lumpsum")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buy_side.total_to_invest", greaterThanOrEqualTo(50000.00)))
            .andExpect(jsonPath("$.buy_side.buckets").isArray())
            .andExpect(jsonPath("$.manual_lumpsum_meta.entered_amount", greaterThanOrEqualTo(50000.00)));
    }
}
