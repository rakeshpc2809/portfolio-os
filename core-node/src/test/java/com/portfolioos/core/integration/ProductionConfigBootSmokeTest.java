package com.portfolioos.core.integration;

import com.portfolioos.core.rules.BucketConfigLoader;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ProductionConfigBootSmokeTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Production Config Smoke Test: Bucket Targets Rollup to v2.3 50/30/10/10 with Exact Band Metadata")
    void testProductionBucketConfigRollup() {
        BucketConfigLoader.BucketTargetVersion activeVersion = BucketConfigLoader.getActiveVersion(LocalDate.now());
        assertNotNull(activeVersion, "Active bucket version must not be null in production configuration");
        assertEquals("v2.3", activeVersion.versionId(), "Active production bucket version must be v2.3");

        List<BucketConfigLoader.BucketTargetConfig> targets = activeVersion.targets();
        assertNotNull(targets, "Bucket targets list must not be null");
        assertFalse(targets.isEmpty(), "Bucket targets list must not be empty");

        Map<String, BucketConfigLoader.BucketTargetConfig> targetMap = new java.util.HashMap<>();
        double totalPct = 0.0;
        for (BucketConfigLoader.BucketTargetConfig target : targets) {
            totalPct += target.targetPct();
            targetMap.put(target.bucket(), target);
        }

        assertEquals(100.0, totalPct, 0.001, "Total target percentage across all buckets must equal exactly 100.0%");

        // Validate EQUITY_CORE
        BucketConfigLoader.BucketTargetConfig core = targetMap.get("EQUITY_CORE");
        assertNotNull(core, "EQUITY_CORE bucket must be present");
        assertEquals(50.0, core.targetPct(), 0.001);
        assertEquals(5.0, core.bandPct(), 0.001, "EQUITY_CORE band_pct in v2.3 must be 5.0%");
        assertEquals(5.0, core.triggerDriftPct(), 0.001, "EQUITY_CORE trigger_drift_pct in v2.3 must be 5.0%");

        // Validate EQUITY_SATELLITE
        BucketConfigLoader.BucketTargetConfig satellite = targetMap.get("EQUITY_SATELLITE");
        assertNotNull(satellite, "EQUITY_SATELLITE bucket must be present");
        assertEquals(30.0, satellite.targetPct(), 0.001);
        assertEquals(5.0, satellite.bandPct(), 0.001, "EQUITY_SATELLITE band_pct in v2.3 must be 5.0%");
        assertEquals(5.0, satellite.triggerDriftPct(), 0.001, "EQUITY_SATELLITE trigger_drift_pct in v2.3 must be 5.0%");

        // Validate GOLD_SILVER
        BucketConfigLoader.BucketTargetConfig goldSilver = targetMap.get("GOLD_SILVER");
        assertNotNull(goldSilver, "GOLD_SILVER bucket must be present");
        assertEquals(10.0, goldSilver.targetPct(), 0.001);
        assertEquals(5.0, goldSilver.bandPct(), 0.001, "GOLD_SILVER band_pct in v2.3 must be 5.0%");
        assertEquals(12.0, goldSilver.triggerDriftPct(), 0.001, "GOLD_SILVER trigger_drift_pct in v2.3 must be 12.0%");

        // Validate LIQUID_BUFFER
        BucketConfigLoader.BucketTargetConfig liquid = targetMap.get("LIQUID_BUFFER");
        assertNotNull(liquid, "LIQUID_BUFFER bucket must be present");
        assertEquals(10.0, liquid.targetPct(), 0.001);
        assertEquals(5.0, liquid.bandPct(), 0.001, "LIQUID_BUFFER band_pct in v2.3 must be 5.0%");
        assertEquals(5.0, liquid.triggerDriftPct(), 0.001, "LIQUID_BUFFER trigger_drift_pct in v2.3 must be 5.0%");
    }

    @Test
    @DisplayName("Production Config Smoke Test: FY 2026-27 and FY 2025-26 Tax Rules Load with Canonical Rates and 1.25L Exemption")
    void testProductionTaxRulesSmoke() {
        TaxRulesConfig config2627 = TaxRulesLoader.loadRules("2026-27");
        assertNotNull(config2627, "FY 2026-27 tax rules must load without null");
        assertEquals("2026-27", config2627.fiscalYear());
        assertEquals(0, new BigDecimal("125000").compareTo(config2627.equityExemptionLimit()),
            "Post-budget FY 2026-27 Section 112A limit must be Rs 1,25,000");
        assertEquals(0, new BigDecimal("0.125").compareTo(config2627.equityLtcgRate()),
            "Post-budget FY 2026-27 Equity LTCG rate must be 12.5%");
        assertEquals(0, new BigDecimal("0.20").compareTo(config2627.equityStcgRate()),
            "Post-budget FY 2026-27 Equity STCG rate must be 20.0%");

        TaxRulesConfig config2526 = TaxRulesLoader.loadRules("2025-26");
        assertNotNull(config2526, "FY 2025-26 tax rules must load without null");
        assertEquals(0, new BigDecimal("125000").compareTo(config2526.equityExemptionLimit()));
    }
}
