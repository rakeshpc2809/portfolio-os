package com.portfolioos.core.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ReportControllerIntegrationTest extends BaseIntegrationTest {

    @BeforeEach
    void setUp() {
        seedCanonicalPortfolioState();
    }

    @Test
    @DisplayName("GET /api/v1/reports/summary returns aggregate portfolio metrics")
    void testGetSummaryIntegration() throws Exception {
        mockMvc.perform(get("/api/v1/reports/summary")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_current_value", notNullValue()))
            .andExpect(jsonPath("$.total_invested", notNullValue()))
            .andExpect(jsonPath("$.total_unrealized_gain", notNullValue()))
            .andExpect(jsonPath("$.active_holding_count").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/reports/allocations/bucket matches /api/v1/sync/portfolio/bucket-allocation identically (Deduplication Check)")
    void testBucketAllocationDeduplicationIntegration() throws Exception {
        MvcResult reportResult = mockMvc.perform(get("/api/v1/reports/allocations/bucket")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

        MvcResult syncResult = mockMvc.perform(get("/api/v1/sync/portfolio/bucket-allocation")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

        String reportContent = reportResult.getResponse().getContentAsString();
        String syncContent = syncResult.getResponse().getContentAsString();

        assertEquals(reportContent, syncContent,
            "Reports and Sync bucket-allocation endpoints must return identical JSON payloads via PortfolioValuationService");
    }

    @Test
    @DisplayName("GET /api/v1/reports/tax/exemption calculates section 112A exemption status")
    void testGetTaxExemptionStatusIntegration() throws Exception {
        mockMvc.perform(get("/api/v1/reports/tax/exemption")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .param("fy", "2026-27")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.exemption_limit").value("125000.00"));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/overlap includes fund_a_name and fund_b_name in pairwise overlap and matrix")
    void testGetPortfolioOverlapAnalyticsFundNamesIntegration() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overlap")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OK"))
            .andExpect(jsonPath("$.pairwise_overlap.fund_a_name", notNullValue()))
            .andExpect(jsonPath("$.pairwise_overlap.fund_b_name", notNullValue()))
            .andExpect(jsonPath("$.pairwise_matrix[0].fund_a_name", notNullValue()))
            .andExpect(jsonPath("$.pairwise_matrix[0].fund_b_name", notNullValue()));
    }
}
