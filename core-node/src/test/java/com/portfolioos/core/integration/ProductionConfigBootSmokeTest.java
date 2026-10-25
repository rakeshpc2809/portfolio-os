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

    @Test
    @DisplayName("Integration Test: Satellite standalone drift threshold (13.0% breach) fires DRIFT rebalance trigger under live Spring Boot context")
    void testSatelliteStandaloneDriftBreachIntegration() {
        com.portfolioos.core.persistence.TriggerHistoryRepository repository = new com.portfolioos.core.persistence.TriggerHistoryRepository(":memory:");
        com.portfolioos.core.service.RebalanceTriggerEvaluator evaluator = new com.portfolioos.core.service.RebalanceTriggerEvaluator(repository);

        LocalDate today = LocalDate.of(2026, 8, 26);
        BigDecimal nav = new BigDecimal("100.00");

        // Total corpus: 1,000,000
        // Satellite Value target in v2.3 is 10.0%, max drift band is 13.0% (min 7.0%, max 13.0%)
        // We set Satellite Value to 140,000 (14.0% > 13.0% standalone threshold)
        // Remainder 860,000 in Core
        com.portfolioos.core.model.Lot satValueLot = new com.portfolioos.core.model.Lot(
            "lot-sat-val", "INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund",
            today.minusMonths(6), new BigDecimal("1400"), new BigDecimal("1400"), nav, new BigDecimal("140000.00"), false, null);
        com.portfolioos.core.model.Lot coreLot = new com.portfolioos.core.model.Lot(
            "lot-core", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusMonths(6), new BigDecimal("8600"), new BigDecimal("8600"), nav, new BigDecimal("860000.00"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC13X2", nav,
            "INF109KC12U0", nav
        );

        com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            List.of(satValueLot, coreLot), navMap,
            new BigDecimal("25000.00"), new BigDecimal("25000.00"), // 0% drawdown
            null, null, today
        );

        assertNotNull(res);
        assertEquals("DRIFT", res.triggerType(), "Satellite standalone drift exceeding 13.0% must fire DRIFT trigger");
        assertEquals("DRIFT_THRESHOLD_EXCEEDED", res.reasonCode());
        assertTrue(res.reasonLabel().contains("EQUITY_SATELLITE") || res.reasonLabel().contains("satellite_value"),
            "Reason label must identify satellite drift breach: " + res.reasonLabel());
        assertTrue(res.hasSellSide(), "Drift trigger must require sell-side rebalance");
        repository.close();
    }

    @Test
    @DisplayName("Integration Test: Core aggregate drift breach (70.0% > 65.0% upper band) fires DRIFT rebalance trigger under live Spring Boot context")
    void testCoreAggregateDriftBreachIntegration() {
        com.portfolioos.core.persistence.TriggerHistoryRepository repository = new com.portfolioos.core.persistence.TriggerHistoryRepository(":memory:");
        com.portfolioos.core.service.RebalanceTriggerEvaluator evaluator = new com.portfolioos.core.service.RebalanceTriggerEvaluator(repository);

        LocalDate today = LocalDate.of(2026, 8, 26);
        BigDecimal nav = new BigDecimal("100.00");

        // Total corpus: 1,000,000
        // Core target is 50.0%, drift bands [35%, 65%]
        // We set Core to 700,000 (70.0% > 65.0% upper drift band)
        // With balanced 60:40 internal ratio (420k LargeMid / 280k PPFC = 0.60) so circuit breaker does NOT fire
        // Remainder 300,000 in Gold
        com.portfolioos.core.model.Lot largeMidLot = new com.portfolioos.core.model.Lot(
            "lot-lm", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusMonths(6), new BigDecimal("4200"), new BigDecimal("4200"), nav, new BigDecimal("420000.00"), false, null);
        com.portfolioos.core.model.Lot ppfcLot = new com.portfolioos.core.model.Lot(
            "lot-ppfc", "INF879O01027", "Parag Parikh Flexi Cap Fund",
            today.minusMonths(6), new BigDecimal("2800"), new BigDecimal("2800"), nav, new BigDecimal("280000.00"), false, null);
        com.portfolioos.core.model.Lot goldLot = new com.portfolioos.core.model.Lot(
            "lot-gold", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds",
            today.minusMonths(6), new BigDecimal("3000"), new BigDecimal("3000"), nav, new BigDecimal("300000.00"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", nav,
            "INF879O01027", nav,
            "INF247L01BM8", nav
        );

        com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            List.of(largeMidLot, ppfcLot, goldLot), navMap,
            new BigDecimal("25000.00"), new BigDecimal("25000.00"), // 0% drawdown
            null, null, today
        );

        assertNotNull(res);
        assertEquals("DRIFT", res.triggerType(), "Core aggregate drift (70.0% > 65.0%) must fire DRIFT trigger");
        assertEquals("DRIFT_THRESHOLD_EXCEEDED", res.reasonCode());
        assertTrue(res.reasonLabel().contains("CORE_AGGREGATE_BREACH"), "Reason label must identify CORE_AGGREGATE_BREACH");
        assertFalse(res.reasonLabel().contains("CORE_INTERNAL_CIRCUIT_BREAKER"), "Circuit breaker must NOT fire when internal ratio is 60:40");
        assertTrue(res.hasSellSide());
        repository.close();
    }

    @Test
    @DisplayName("Integration Test: Core internal circuit breaker ratio breach (0.80 > 0.75) fires DRIFT independently when aggregate is exactly 50%")
    void testCoreInternalCircuitBreakerIntegration() {
        com.portfolioos.core.persistence.TriggerHistoryRepository repository = new com.portfolioos.core.persistence.TriggerHistoryRepository(":memory:");
        com.portfolioos.core.service.RebalanceTriggerEvaluator evaluator = new com.portfolioos.core.service.RebalanceTriggerEvaluator(repository);

        LocalDate today = LocalDate.of(2026, 8, 26);
        BigDecimal nav = new BigDecimal("100.00");

        // Total corpus: 1,000,000
        // Core target is 50.0%, drift bands [35%, 65%], internal circuit breaker largemid_max_ratio = 0.75
        // Within Core: 500,000 total Core (50% aggregate, exactly on target)
        // LargeMidcap = 400,000 (80% of Core > 75% circuit breaker)
        // PPFC = 100,000 (20% of Core < 45% circuit breaker)
        // Other buckets: 500,000 in Gold
        com.portfolioos.core.model.Lot largeMidLot = new com.portfolioos.core.model.Lot(
            "lot-lm", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusMonths(6), new BigDecimal("4000"), new BigDecimal("4000"), nav, new BigDecimal("400000.00"), false, null);
        com.portfolioos.core.model.Lot ppfcLot = new com.portfolioos.core.model.Lot(
            "lot-ppfc", "INF879O01027", "Parag Parikh Flexi Cap Fund",
            today.minusMonths(6), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);
        com.portfolioos.core.model.Lot goldLot = new com.portfolioos.core.model.Lot(
            "lot-gold", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds",
            today.minusMonths(6), new BigDecimal("5000"), new BigDecimal("5000"), nav, new BigDecimal("500000.00"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", nav,
            "INF879O01027", nav,
            "INF247L01BM8", nav
        );

        com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            List.of(largeMidLot, ppfcLot, goldLot), navMap,
            new BigDecimal("25000.00"), new BigDecimal("25000.00"), // 0% drawdown
            null, null, today
        );

        assertNotNull(res);
        assertEquals("DRIFT", res.triggerType(), "Core internal circuit breaker breach (0.80 > 0.75) must fire DRIFT trigger");
        assertEquals("DRIFT_THRESHOLD_EXCEEDED", res.reasonCode());
        assertTrue(res.reasonLabel().contains("CORE_INTERNAL_CIRCUIT_BREAKER"), "Reason label must identify CORE_INTERNAL_CIRCUIT_BREAKER breach");
        assertFalse(res.reasonLabel().contains("CORE_AGGREGATE_BREACH"), "CORE_AGGREGATE_BREACH must NOT fire when Core aggregate is 50%");
        assertTrue(res.hasSellSide());
        repository.close();
    }

    @Test
    @DisplayName("Integration Test: Core scheme-level imbalance triggers intra-bucket trim routing (LargeMid trimmed, PPFC shielded) and routes proceeds via v2.3 waterfall")
    void testCoreSchemeLevelTrimRoutingIntegration() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        BigDecimal nav = new BigDecimal("100.00");

        // Core lots: LargeMid 400,000 (80%), PPFC 100,000 (20%) -> imbalanced vs 60:40 target
        com.portfolioos.core.model.Lot largeMidLot = new com.portfolioos.core.model.Lot(
            "lot-lm", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusMonths(14), new BigDecimal("4000"), new BigDecimal("4000"), nav, new BigDecimal("400000.00"), false, null);
        com.portfolioos.core.model.Lot ppfcLot = new com.portfolioos.core.model.Lot(
            "lot-ppfc", "INF879O01027", "Parag Parikh Flexi Cap Fund",
            today.minusMonths(14), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", nav,
            "INF879O01027", nav
        );

        // 1. Verify dynamic resolution of 60% target weight from active YAML under Spring context
        BigDecimal lmTargetWeight = com.portfolioos.core.valuation.RebalanceWaterfallEngine.resolveLargeMidcapTargetWeight(today);
        assertEquals(new BigDecimal("0.6000"), lmTargetWeight, "Dynamic LargeMid target weight must be 60.00% under v2.3");

        // 2. Verify filterOverweightCoreLots trims ONLY overweight LargeMid (400k/500k = 80% > 60%) and shields PPFC
        List<com.portfolioos.core.model.Lot> eligibleLots = com.portfolioos.core.valuation.RebalanceWaterfallEngine.filterOverweightCoreLots(
            List.of(largeMidLot, ppfcLot), navMap, today
        );
        assertEquals(1, eligibleLots.size(), "Only the overweight scheme must be eligible for trim");
        assertEquals("INF109KC12U0", eligibleLots.get(0).assetId(), "LargeMidcap 250 must be selected; PPFC must be shielded");

        // 3. Verify waterfall execution selects only the overweight scheme
        com.portfolioos.core.valuation.RebalanceWaterfallEngine.WaterfallResult waterfall =
            com.portfolioos.core.valuation.RebalanceWaterfallEngine.buildTrimWaterfall(
                com.portfolioos.core.valuation.BucketEngine.Bucket.EQUITY_CORE,
                new BigDecimal("50000.00"),
                List.of(largeMidLot, ppfcLot),
                navMap,
                new BigDecimal("125000.00"),
                false,
                today,
                "2026-27"
            );
        assertNotNull(waterfall);
        assertEquals(new BigDecimal("50000.00"), waterfall.satisfiedAmount());
        assertFalse(waterfall.steps().isEmpty());
        for (com.portfolioos.core.valuation.RebalanceWaterfallEngine.WaterfallStep step : waterfall.steps()) {
            assertEquals("INF109KC12U0", step.assetId(), "Every trim step within Core must trim LargeMidcap 250 only");
        }

        // 4. Verify 5-step routing of the trimmed proceeds (Path A: Gold deficit fill per funding_priority_on_trim)
        com.portfolioos.core.valuation.RebalanceWaterfallEngine.TrimDestinationAllocation alloc =
            com.portfolioos.core.valuation.RebalanceWaterfallEngine.routeTrimProceeds(
                new BigDecimal("50000.00"),
                new BigDecimal("70000.00"), new BigDecimal("100000.00"),  // Gold deficit 30,000 (100k target, 70k actual)
                new BigDecimal("90000.00"), new BigDecimal("100000.00"),  // Arb deficit 10,000
                new BigDecimal("500000.00"), new BigDecimal("500000.00"), // Core at target
                new BigDecimal("1000000.00")
            );
        assertNotNull(alloc);
        assertEquals(new BigDecimal("30000.00"), alloc.toGold(), "Step 1 priority: fills 30,000 Gold deficit");
        assertEquals(new BigDecimal("10000.00"), alloc.toArbTarget(), "Step 2 priority: fills 10,000 Arbitrage deficit");
        assertEquals(new BigDecimal("10000.00"), alloc.toArbTerminal(), "Step 4: remaining 10,000 to Arbitrage terminal sink (lands at 11% <= 15% cap)");
        assertEquals(new BigDecimal("0.00"), alloc.toCashOverflow());
    }

    @Test
    @DisplayName("Integration Test: Balanced portfolio within all standalone thresholds yields NO_REBALANCE_REQUIRED under live Spring Boot context")
    void testBalancedPortfolioNoDriftIntegration() {
        com.portfolioos.core.persistence.TriggerHistoryRepository repository = new com.portfolioos.core.persistence.TriggerHistoryRepository(":memory:");
        com.portfolioos.core.service.RebalanceTriggerEvaluator evaluator = new com.portfolioos.core.service.RebalanceTriggerEvaluator(repository);

        LocalDate today = LocalDate.of(2026, 8, 26);
        BigDecimal nav = new BigDecimal("100.00");

        // Total corpus: 1,000,000 exactly matching v2.3 targets:
        // Core: 50% (30% LargeMid, 20% PPFC -> ratio 0.60, within [0.45, 0.75])
        // Satellites: Value 10%, Momentum 10%, SmallCap 10%
        // Gold: 10%, Liquid: 10%
        com.portfolioos.core.model.Lot lm = new com.portfolioos.core.model.Lot("l1", "INF109KC12U0", "ICICI LargeMidcap 250", today.minusMonths(6), new BigDecimal("3000"), new BigDecimal("3000"), nav, new BigDecimal("300000.00"), false, null);
        com.portfolioos.core.model.Lot pp = new com.portfolioos.core.model.Lot("l2", "INF879O01027", "Parag Parikh Flexi Cap", today.minusMonths(6), new BigDecimal("2000"), new BigDecimal("2000"), nav, new BigDecimal("200000.00"), false, null);
        com.portfolioos.core.model.Lot sv = new com.portfolioos.core.model.Lot("l3", "INF109KC13X2", "ICICI Nifty200 Value 30", today.minusMonths(6), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);
        com.portfolioos.core.model.Lot sm = new com.portfolioos.core.model.Lot("l4", "INF754K01TN5", "Edelweiss Momentum 50", today.minusMonths(6), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);
        com.portfolioos.core.model.Lot sc = new com.portfolioos.core.model.Lot("l5", "INF204K01K15", "Nippon Small Cap", today.minusMonths(6), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);
        com.portfolioos.core.model.Lot gd = new com.portfolioos.core.model.Lot("l6", "INF247L01BM8", "Motilal Gold Silver", today.minusMonths(6), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);
        com.portfolioos.core.model.Lot li = new com.portfolioos.core.model.Lot("l7", "INF205K01KR8", "Invesco Arbitrage", today.minusMonths(6), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", nav,
            "INF879O01027", nav,
            "INF109KC13X2", nav,
            "INF754K01TN5", nav,
            "INF204K01K15", nav,
            "INF247L01BM8", nav,
            "INF205K01KR8", nav
        );

        com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            List.of(lm, pp, sv, sm, sc, gd, li), navMap,
            new BigDecimal("25000.00"), new BigDecimal("25000.00"), // 0% drawdown
            null, null, today
        );

        assertNotNull(res);
        assertEquals("NONE", res.triggerType());
        assertEquals("NO_REBALANCE_REQUIRED", res.reasonCode());
        assertFalse(res.hasSellSide());
        repository.close();
    }
}
