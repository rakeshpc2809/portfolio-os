package com.portfolioos.core.controllers;

import com.portfolioos.core.rules.BucketConfigLoader;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class ConfigControllerTest {

    @Test
    void testGetBucketTargets() {
        ConfigController controller = new ConfigController();
        ResponseEntity<BucketConfigLoader.BucketRulesConfig> response = controller.getBucketTargets();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void testGetRebalancePlanAliasReturns307Redirect() {
        ConfigController controller = new ConfigController();
        ResponseEntity<?> response = controller.getRebalancePlanAlias("INDUCED");

        assertNotNull(response);
        assertEquals(307, response.getStatusCode().value());
        assertTrue(response.getHeaders().containsKey("Location"));
        assertEquals("/api/v1/sync/rebalance/plan?trigger=INDUCED", response.getHeaders().getFirst("Location"));
    }
}
