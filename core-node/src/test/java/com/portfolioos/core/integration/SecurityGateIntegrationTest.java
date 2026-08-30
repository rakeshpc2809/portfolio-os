package com.portfolioos.core.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SecurityGateIntegrationTest extends BaseIntegrationTest {

    @BeforeEach
    void setUp() {
        seedCanonicalPortfolioState();
    }

    @Test
    @DisplayName("Fail-Loud: Missing X-Api-Auth-Token returns HTTP 401 Unauthorized")
    void testMissingAuthTokenFailsLoud() throws Exception {
        mockMvc.perform(get("/api/v1/sync/snapshot")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Unauthorized: Missing or invalid X-Api-Auth-Token header or token parameter."));
    }

    @Test
    @DisplayName("Fail-Loud: Invalid X-Api-Auth-Token returns HTTP 401 Unauthorized")
    void testInvalidAuthTokenFailsLoud() throws Exception {
        mockMvc.perform(get("/api/v1/sync/snapshot")
                .header("X-Api-Auth-Token", "invalid_token_999")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Unauthorized: Missing or invalid X-Api-Auth-Token header or token parameter."));
    }

    @Test
    @DisplayName("Valid X-Api-Auth-Token header succeeds with HTTP 200 OK")
    void testValidAuthHeaderSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/sync/snapshot")
                .header("X-Api-Auth-Token", AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Valid Bearer Authorization header succeeds with HTTP 200 OK")
    void testValidBearerAuthHeaderSucceeds() throws Exception {
        mockMvc.perform(get("/api/v1/sync/snapshot")
                .header("Authorization", "Bearer " + AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CORS OPTIONS preflight request bypasses auth gate")
    void testCorsOptionsPreflightBypassesAuth() throws Exception {
        mockMvc.perform(options("/api/v1/sync/snapshot"))
            .andExpect(status().isOk());
    }
}
