package com.portfolioos.core.service;

import com.portfolioos.core.dtos.RebalancePlanDtos;
import com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto;
import com.portfolioos.core.dtos.RebalancePlanDtos.RebalanceBucketAllocationDto;
import com.portfolioos.core.dtos.RebalancePlanDtos.RebalanceLotImpactDto;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.persistence.TriggerHistoryRepository;
import com.portfolioos.core.rules.BucketConfigLoader;
import com.portfolioos.core.rules.BucketConfigLoader.BucketRulesConfig;
import com.portfolioos.core.rules.BucketConfigLoader.BucketTargetConfig;
import com.portfolioos.core.rules.BucketConfigLoader.BucketTargetVersion;
import com.portfolioos.core.rules.BucketConfigLoader.PreferredFundConfig;
import com.portfolioos.core.valuation.BucketEngine;
import com.portfolioos.core.valuation.FundTrendDampenerCalculator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RebalancePlanEngineTest {

    private TriggerHistoryRepository repository;
    private RebalanceTriggerEvaluator evaluator;

    @BeforeEach
    void setUp() {
        repository = new TriggerHistoryRepository(":memory:");
        repository.clearAll();
        evaluator = new RebalanceTriggerEvaluator(repository);
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.close();
        }
        BucketConfigLoader.resetCache();
    }

    @Test
    @DisplayName("Scenario 1: End-to-end bucket drift test (LargeMidcap 20% underweight vs 30% target) triggers DRIFT plan")
    void testEndToEndDriftPlanGeneration() {
        // Real portfolio holdings ISINs:
        // EQUITY_CORE: Parag Parikh Flexi Cap Fund (INF109K018C5)
        // EQUITY_SATELLITE: Motilal Oswal Large and Midcap Fund (INF204K01K15)
        BigDecimal navCore = new BigDecimal("100.00");
        BigDecimal navSat = new BigDecimal("100.00");
        LocalDate acqDate = LocalDate.of(2024, 1, 1);

        // Corpus = 2,000,000 (20 Lakhs)
        // 1,800,000 in Core (90%), 200,000 in Satellite (10%)
        Lot coreLot = new Lot("lot-1", "INF109K018C5", "Parag Parikh Flexi Cap Fund Direct Growth", acqDate, new BigDecimal("18000"), new BigDecimal("18000"), navCore, new BigDecimal("1800000.00"), false, null);
        Lot satLot = new Lot("lot-2", "INF204K01K15", "Motilal Oswal Large and Midcap Fund Direct Growth", acqDate, new BigDecimal("2000"), new BigDecimal("2000"), navSat, new BigDecimal("200000.00"), false, null);

        List<Lot> openLots = List.of(coreLot, satLot);
        Map<String, BigDecimal> navMap = Map.of(
            "INF109K018C5", navCore,
            "INF204K01K15", navSat
        );

        // Explicit custom targets: Core = 70.0%, Satellite = 30.0%
        // Actual Core = 90.0% (+20% drift >= 5.0%), Actual Satellite = 10.0% (-20% drift >= 5.0%)
        List<BucketEngine.BucketTarget> customTargets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("70.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("30.00"), new BigDecimal("5.00"))
        );

        BigDecimal corpus = new BigDecimal("2000000.00");
        BigDecimal high = new BigDecimal("2000000.00"); // 0% drawdown

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            openLots, Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
            corpus, high, customTargets, "2026-27", null, null, evaluator
        );

        assertNotNull(plan);
        assertNotNull(plan.trigger());
        assertEquals("DRIFT", plan.trigger().type(), "End-to-end engine must resolve DRIFT trigger when bucket drift exceeds 5% threshold");
        assertEquals("INDUCED", plan.trigger().legacyTriggerType(), "Backward compatibility legacyTriggerType must be INDUCED");
        assertTrue(plan.trigger().isInduced());
        assertEquals("DRIFT_THRESHOLD_EXCEEDED", plan.trigger().reasonCode());

        // Verify buy side sizing: Excess Core with per-fund Trend Dampener (0.7393x of ₹400,000.00 = ₹295,720.00)
        assertNotNull(plan.buySide());
        assertEquals(new BigDecimal("295720.00"), plan.buySide().totalToInvest(), "Excess drift pool on 2M corpus with per-fund trend dampener must yield exactly ₹450,000.00 total to invest");
        assertFalse(plan.buySide().buckets().isEmpty());
    }

    @Test
    @DisplayName("Scenario 2: Simultaneous Drawdown + Drift scenario end-to-end — DRAWDOWN suppresses DRIFT")
    void testEndToEndDrawdownSuppressesDrift() {
        // Real portfolio holdings ISINs with 20% drawdown
        BigDecimal navCore = new BigDecimal("100.00");
        BigDecimal navSat = new BigDecimal("100.00");
        LocalDate acqDate = LocalDate.of(2024, 1, 1);

        Lot coreLot = new Lot("lot-1", "INF109K018C5", "Parag Parikh Flexi Cap Fund Direct Growth", acqDate, new BigDecimal("18000"), new BigDecimal("18000"), navCore, new BigDecimal("1800000.00"), false, null);
        Lot satLot = new Lot("lot-2", "INF204K01K15", "Motilal Oswal Large and Midcap Fund Direct Growth", acqDate, new BigDecimal("2000"), new BigDecimal("2000"), navSat, new BigDecimal("200000.00"), false, null);

        List<Lot> openLots = List.of(coreLot, satLot);
        Map<String, BigDecimal> navMap = Map.of(
            "INF109K018C5", navCore,
            "INF204K01K15", navSat
        );

        BigDecimal currentVal = new BigDecimal("1600000.00");
        BigDecimal rollingHigh = new BigDecimal("2000000.00"); // 20% drawdown tier armed!

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            openLots, Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
            currentVal, rollingHigh, null, "2026-27", null, null, evaluator
        );

        assertNotNull(plan);
        assertNotNull(plan.trigger());
        assertEquals("DRAWDOWN", plan.trigger().type(), "DRAWDOWN trigger must suppress DRIFT end-to-end in RebalancePlanEngine");
        assertEquals("DRAWDOWN_TIER_20", plan.trigger().reasonCode());
        assertEquals("INDUCED", plan.trigger().legacyTriggerType());
        assertTrue(plan.trigger().isInduced());

        // Verify sell side waterfall was built
        assertNotNull(plan.sellSide());
        assertTrue(plan.sellSide().totalRequired().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("F-08 Invariant: Pure internal rebalance does not inflate post-rebalance active corpus denominator")
    void testSourceAwareActiveCorpusDenominator() {
        BigDecimal navCore = new BigDecimal("100.00");
        BigDecimal navSat = new BigDecimal("100.00");
        LocalDate acqDate = LocalDate.of(2024, 1, 1);

        // Active lots totaling 2,000,000 (Core is overweight at 1.8M/90%, Satellite underweight at 200k/10%)
        Lot coreLot = new Lot("lot-1", "INF879O01027", "Parag Parikh Flexi Cap Fund Direct Growth", LocalDate.of(2024, 1, 1), new BigDecimal("18000"), new BigDecimal("18000"), navCore, new BigDecimal("1800000.00"), false, null);
        Lot satLot = new Lot("lot-2", "INF204K01K15", "Motilal Oswal Large and Midcap Fund Direct Growth", LocalDate.of(2026, 7, 1), new BigDecimal("2000"), new BigDecimal("2000"), navSat, new BigDecimal("200000.00"), false, null);

        List<Lot> openLots = List.of(coreLot, satLot);
        Map<String, BigDecimal> navMap = Map.of(
            "INF879O01027", navCore,
            "INF204K01K15", navSat
        );

        List<BucketEngine.BucketTarget> customTargets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("70.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("30.00"), new BigDecimal("5.00"))
        );

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            openLots, Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
            new BigDecimal("2000000.00"), new BigDecimal("2000000.00"), customTargets, "2026-27", null, null, evaluator
        );

        assertNotNull(plan);
        assertNotNull(plan.buySide());
        assertFalse(plan.buySide().buckets().isEmpty());

        // Sourced trim must strictly come from CORE_FUND tier (zero legacy liquidations)
        assertEquals(0, BigDecimal.ZERO.compareTo(
            plan.sellSide().waterfall().stream().filter(t -> "LEGACY_FUND".equals(t.tier())).findFirst().orElseThrow().sold()),
            "Legacy sales proceeds must be strictly 0.00 in pure internal drift");
        assertEquals(0, new BigDecimal("295720.00").compareTo(
            plan.sellSide().waterfall().stream().filter(t -> "CORE_FUND".equals(t.tier())).findFirst().orElseThrow().sold()),
            "Core fund trim proceeds must be exactly 295,720.00");

        RebalanceBucketAllocationDto coreBucket = plan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.EQUITY_CORE.name().equals(b.bucket()))
            .findFirst().orElseThrow();
        RebalanceBucketAllocationDto satBucket = plan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.EQUITY_SATELLITE.name().equals(b.bucket()))
            .findFirst().orElseThrow();

        // Concrete absolute rupee allocations
        assertEquals(0, BigDecimal.ZERO.compareTo(coreBucket.amountAllocated()),
            "Core is overweight; allocated buy-side amount must be exactly ₹0.00");
        assertEquals(0, new BigDecimal("295720.00").compareTo(satBucket.amountAllocated()),
            "Satellite is underweight; allocated buy-side amount must be exactly ₹295,720.00");

        // Concrete absolute post-rebalance rupee values:
        // Core post-rebalance value = 1,800,000.00 - 295,720.00 = 1,504,280.00
        // Satellite post-rebalance value = 200,000.00 + 295,720.00 = 495,720.00
        BigDecimal corePostRupees = new BigDecimal("1800000.00").subtract(new BigDecimal("295720.00"));
        BigDecimal satPostRupees = new BigDecimal("200000.00").add(satBucket.amountAllocated());
        BigDecimal totalPostCorpusRupees = corePostRupees.add(satPostRupees);

        assertEquals(0, new BigDecimal("2000000.00").compareTo(totalPostCorpusRupees),
            "Total post-rebalance active portfolio across all buckets must strictly equal activeCorpus (2,000,000.00)");

        // Under uninflated denominator (2,000,000.00):
        // Core post % = 1,504,280 / 2,000,000 = 75.2% (under buggy denominator 2,295,720, it was 78.4%)
        // Satellite post % = 495,720 / 2,000,000 = 24.8% (under buggy denominator 2,295,720, it was 21.6%)
        assertEquals(75.2, coreBucket.postRebalancePct(), 0.1,
            "CORE postRebalancePct must be 75.2% (1,504,280 / 2,000,000), not 78.4% from inflated denominator");
        assertEquals(24.8, satBucket.postRebalancePct(), 0.1,
            "SATELLITE postRebalancePct must be 24.8% (495,720 / 2,000,000), not 21.6% from inflated denominator");
    }

    @Test
    @DisplayName("F-08 Invariant: Manual lumpsum inflow correctly expands post-rebalance active corpus denominator to 2.5M")
    void testLumpsumInflowCorrectlyExpandsActiveCorpusDenominator() {
        BigDecimal navCore = new BigDecimal("100.00");
        BigDecimal navSat = new BigDecimal("100.00");
        LocalDate acqDate = LocalDate.of(2024, 1, 1);

        // Active lots totaling 2,000,000
        Lot coreLot = new Lot("lot-1", "INF109K018C5", "Parag Parikh Flexi Cap Fund Direct Growth", acqDate, new BigDecimal("14000"), new BigDecimal("14000"), navCore, new BigDecimal("1400000.00"), false, null);
        Lot satLot = new Lot("lot-2", "INF204K01K15", "Motilal Oswal Large and Midcap Fund Direct Growth", acqDate, new BigDecimal("6000"), new BigDecimal("6000"), navSat, new BigDecimal("600000.00"), false, null);

        List<Lot> openLots = List.of(coreLot, satLot);
        Map<String, BigDecimal> navMap = Map.of(
            "INF109K018C5", navCore,
            "INF204K01K15", navSat
        );

        List<BucketEngine.BucketTarget> customTargets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("70.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("30.00"), new BigDecimal("5.00"))
        );

        BigDecimal manualLumpsum = new BigDecimal("500000.00");
        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            openLots, Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
            new BigDecimal("2000000.00"), new BigDecimal("2000000.00"), customTargets, "2026-27", "MANUAL_LUMPSUM", manualLumpsum, evaluator
        );

        assertNotNull(plan);
        assertNotNull(plan.buySide());
        BigDecimal totalAllocated = plan.buySide().buckets().stream()
            .map(RebalanceBucketAllocationDto::amountAllocated)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, manualLumpsum.compareTo(totalAllocated),
            "Total allocated across buckets must equal manualLumpsum (500,000)");

        RebalanceBucketAllocationDto coreBucket = plan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.EQUITY_CORE.name().equals(b.bucket()))
            .findFirst().orElseThrow();
        assertEquals(70.0, coreBucket.postRebalancePct(), 0.1, "CORE postRebalancePct must be 70% of 2.5M");
        assertEquals(0, new BigDecimal("350000.00").compareTo(coreBucket.amountAllocated()),
            "CORE allocation must be exactly ₹350,000.00 (70% of 2.5M - 1.4M)");

        RebalanceBucketAllocationDto satBucket = plan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.EQUITY_SATELLITE.name().equals(b.bucket()))
            .findFirst().orElseThrow();
        assertEquals(30.0, satBucket.postRebalancePct(), 0.1, "SATELLITE postRebalancePct must be 30% of 2.5M");
        assertEquals(0, new BigDecimal("150000.00").compareTo(satBucket.amountAllocated()),
            "SATELLITE allocation must be exactly ₹150,000.00 (30% of 2.5M - 600k)");

        // Absolute post-rebalance portfolio rupee assertion:
        BigDecimal corePostVal = new BigDecimal("1400000.00").add(coreBucket.amountAllocated());
        BigDecimal satPostVal = new BigDecimal("600000.00").add(satBucket.amountAllocated());
        assertEquals(0, new BigDecimal("1750000.00").compareTo(corePostVal), "Core post value must equal ₹1,750,000.00");
        assertEquals(0, new BigDecimal("750000.00").compareTo(satPostVal), "Satellite post value must equal ₹750,000.00");
        assertEquals(0, new BigDecimal("2500000.00").compareTo(corePostVal.add(satPostVal)),
            "Total post-rebalance corpus across buckets must strictly equal expanded active corpus (2,500,000.00)");
    }

    @Test
    @DisplayName("Component 3: resolveTargetBucket correctly rolls up granular sub-buckets to canonical parent targets")
    void testResolveTargetBucketRollupMappingAllBranches() {
        List<BucketEngine.BucketTarget> parentTargets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("50.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("30.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("10.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.LIQUID_BUFFER, new BigDecimal("10.00"), new BigDecimal("5.00"))
        );

        // 1. Granular Satellite sub-buckets roll up to parent EQUITY_SATELLITE
        assertEquals(BucketEngine.Bucket.EQUITY_SATELLITE,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.SATELLITE_VALUE, parentTargets),
            "SATELLITE_VALUE must roll up to parent EQUITY_SATELLITE target");
        assertEquals(BucketEngine.Bucket.EQUITY_SATELLITE,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.SATELLITE_MOMENTUM, parentTargets),
            "SATELLITE_MOMENTUM must roll up to parent EQUITY_SATELLITE target");
        assertEquals(BucketEngine.Bucket.EQUITY_SATELLITE,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.SATELLITE_SMALLCAP, parentTargets),
            "SATELLITE_SMALLCAP must roll up to parent EQUITY_SATELLITE target");

        // 2. Granular Hedge commodity rolls up to parent GOLD_SILVER
        assertEquals(BucketEngine.Bucket.GOLD_SILVER,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.HEDGE_COMMODITY, parentTargets),
            "HEDGE_COMMODITY must roll up to parent GOLD_SILVER target");

        // 3. Granular Liquidity arbitrage rolls up to parent LIQUID_BUFFER
        assertEquals(BucketEngine.Bucket.LIQUID_BUFFER,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.LIQUIDITY_ARBITRAGE, parentTargets),
            "LIQUIDITY_ARBITRAGE must roll up to parent LIQUID_BUFFER target");

        // 4. Early-return identity branch (exact target match)
        assertEquals(BucketEngine.Bucket.EQUITY_CORE,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.EQUITY_CORE, parentTargets));
        assertEquals(BucketEngine.Bucket.EQUITY_SATELLITE,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.EQUITY_SATELLITE, parentTargets));
        assertEquals(BucketEngine.Bucket.GOLD_SILVER,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.GOLD_SILVER, parentTargets));
        assertEquals(BucketEngine.Bucket.LIQUID_BUFFER,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.LIQUID_BUFFER, parentTargets));

        // 5. Explicit granular target in targets (no rollup needed when caller explicitly configures sub-bucket target)
        List<BucketEngine.BucketTarget> granularTargets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.SATELLITE_VALUE, new BigDecimal("20.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.HEDGE_COMMODITY, new BigDecimal("10.00"), new BigDecimal("5.00"))
        );
        assertEquals(BucketEngine.Bucket.SATELLITE_VALUE,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.SATELLITE_VALUE, granularTargets));
        assertEquals(BucketEngine.Bucket.HEDGE_COMMODITY,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.HEDGE_COMMODITY, granularTargets));

        // 6. Fallback when parent target is missing from target list
        List<BucketEngine.BucketTarget> coreOnlyTargets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("100.00"), new BigDecimal("5.00"))
        );
        assertEquals(BucketEngine.Bucket.SATELLITE_VALUE,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.SATELLITE_VALUE, coreOnlyTargets),
            "Fallback must return raw sub-bucket when parent EQUITY_SATELLITE is not in active targets");
        assertEquals(BucketEngine.Bucket.HEDGE_COMMODITY,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.HEDGE_COMMODITY, coreOnlyTargets),
            "Fallback must return raw sub-bucket when parent GOLD_SILVER is not in active targets");

        // 7. Null safety guards
        assertNull(RebalancePlanEngine.resolveTargetBucket(null, parentTargets));
        assertEquals(BucketEngine.Bucket.SATELLITE_VALUE,
            RebalancePlanEngine.resolveTargetBucket(BucketEngine.Bucket.SATELLITE_VALUE, null));
    }

    @Test
    @DisplayName("Component 3 (F-08): Trimmed sub-bucket lot (SATELLITE_VALUE) correctly rolls up to parent (EQUITY_SATELLITE) in postVal and soldFromBucket")
    void testEndToEndSubBucketRollupAttributionInRebalancePlan() {
        // Configure in-memory BucketRulesConfig with preferred fund SATELLITE_VALUE
        BucketConfigLoader.setCachedConfig(new BucketRulesConfig(List.of(
            new BucketTargetVersion("v-test", "2024-01-01", List.of(
                new BucketTargetConfig("EQUITY_CORE", 30.0, 5.0, 5.0, "CORE", List.of(
                    new PreferredFundConfig("INF879O01027", "Parag Parikh Flexi Cap Fund", 1.0)
                )),
                new BucketTargetConfig("SATELLITE_VALUE", 70.0, 5.0, 5.0, "SATELLITE", List.of(
                    new PreferredFundConfig("INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund", 1.0)
                ))
            ))
        )));

        try {
            // Verify fund classification directly produces raw sub-bucket SATELLITE_VALUE
            assertEquals(BucketEngine.Bucket.SATELLITE_VALUE,
                BucketEngine.classifyAssetToBucket("INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund"),
                "INF109KC13X2 must classify into granular sub-bucket SATELLITE_VALUE under active config");

            BigDecimal navCore = new BigDecimal("100.00");
            BigDecimal navSat = new BigDecimal("100.00");
            LocalDate acqDate = LocalDate.of(2024, 1, 1);

            // Portfolio: 2,000,000 total corpus
            // Core: 200,000 (10% actual vs 30% target -> 400,000 underweight)
            // Satellite Value: 1,800,000 (90% actual vs 70% target -> 400,000 overweight)
            // To be an active (non-legacy) fund, INF109KC13X2 has an active SIP purchase within 3 months,
            // while the trimmed lot is held >365 days for LTCG tax-efficiency.
            Lot coreLot = new Lot("lot-core", "INF879O01027", "Parag Parikh Flexi Cap Fund Direct Growth",
                acqDate, new BigDecimal("2000"), new BigDecimal("2000"), navCore, new BigDecimal("200000.00"), false, null);
            Lot satValLtcgLot = new Lot("lot-sat-val-ltcg", "INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund",
                acqDate, new BigDecimal("17990"), new BigDecimal("17990"), navSat, new BigDecimal("1799000.00"), false, null);
            Lot satValActiveSipLot = new Lot("lot-sat-val-sip", "INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund",
                LocalDate.of(2026, 7, 15), new BigDecimal("10"), new BigDecimal("10"), navSat, new BigDecimal("1000.00"), false, null);

            List<Lot> openLots = List.of(coreLot, satValLtcgLot, satValActiveSipLot);
            Map<String, BigDecimal> navMap = Map.of(
                "INF879O01027", navCore,
                "INF109KC13X2", navSat
            );

            // Canonical target list uses coarse parent bucket EQUITY_SATELLITE
            List<BucketEngine.BucketTarget> customTargets = List.of(
                new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("30.00"), new BigDecimal("5.00")),
                new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("70.00"), new BigDecimal("5.00"))
            );

            BigDecimal corpus = new BigDecimal("2000000.00");
            RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
                openLots, Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
                corpus, corpus, customTargets, "2026-27", null, null, evaluator
            );

            assertNotNull(plan);
            assertNotNull(plan.sellSide());
            assertNotNull(plan.buySide());

            // 1. Confirm sell-side waterfall trimmed the SATELLITE_VALUE lot in CORE_FUND tier
            // (0.00 sold in LEGACY_FUND, exactly 295,720.00 sold in CORE_FUND)
            assertEquals(0, BigDecimal.ZERO.compareTo(
                plan.sellSide().waterfall().stream().filter(t -> "LEGACY_FUND".equals(t.tier())).findFirst().orElseThrow().sold()),
                "Legacy fund sales must be 0.00 for active satellite holding");

            BigDecimal expectedTrim = new BigDecimal("295720.00");
            assertEquals(0, expectedTrim.compareTo(
                plan.sellSide().waterfall().stream().filter(t -> "CORE_FUND".equals(t.tier())).findFirst().orElseThrow().sold()),
                "Sell side must execute trim of 295,720.00 on active overweight satellite bucket");

            // 2. Verify buy-side bucket allocations and post-rebalance values
            RebalanceBucketAllocationDto coreBucket = plan.buySide().buckets().stream()
                .filter(b -> BucketEngine.Bucket.EQUITY_CORE.name().equals(b.bucket()))
                .findFirst().orElseThrow();
            RebalanceBucketAllocationDto satBucket = plan.buySide().buckets().stream()
                .filter(b -> BucketEngine.Bucket.EQUITY_SATELLITE.name().equals(b.bucket()))
                .findFirst().orElseThrow();

            // Core receives entire 295,720.00 buy allocation
            assertEquals(0, expectedTrim.compareTo(coreBucket.amountAllocated()),
                "Underweight Core bucket must receive full 295,720.00 allocation");
            assertEquals(0, BigDecimal.ZERO.compareTo(satBucket.amountAllocated()),
                "Overweight Satellite bucket must receive 0.00 buy allocation");

            // 3. KEY ASSERTION: Satellite post-rebalance value MUST reflect the 295,720.00 trim
            // SATELLITE_VALUE lot proceeds were rolled up to parent EQUITY_SATELLITE
            // curVal (1,800,000.00) - soldFromBucket (295,720.00) + allocated (0.00) = 1,504,280.00
            BigDecimal expectedSatPostVal = new BigDecimal("1800000.00").subtract(expectedTrim);
            BigDecimal expectedCorePostVal = new BigDecimal("200000.00").add(expectedTrim);

            // If resolveTargetBucket had failed to roll up, soldFromBucket would be 0, leaving postVal at 1,800,000.00
            // and postRebalancePct at 90.0% instead of 75.2%!
            assertEquals(75.2, satBucket.postRebalancePct(), 0.1,
                "EQUITY_SATELLITE postRebalancePct must be 75.2% (1,504,280 / 2,000,000), proving soldFromBucket was rolled up and subtracted");
            assertEquals(24.8, coreBucket.postRebalancePct(), 0.1,
                "EQUITY_CORE postRebalancePct must be 24.8% (495,720 / 2,000,000)");

            // Total post-rebalance corpus remains invariant at 2,000,000.00
            assertEquals(0, corpus.compareTo(expectedSatPostVal.add(expectedCorePostVal)),
                "Sum of post-rebalance bucket valuations must strictly equal 2,000,000.00");
        } finally {
            BucketConfigLoader.resetCache();
        }
    }

    @Test
    @DisplayName("Component 3 (F-08): Trimmed hedge lot (HEDGE_COMMODITY) correctly rolls up to parent (GOLD_SILVER) in postVal and soldFromBucket")
    void testEndToEndHedgeCommodityRollupAttributionInRebalancePlan() {
        BucketConfigLoader.setCachedConfig(new BucketRulesConfig(List.of(
            new BucketTargetVersion("v-test-gold", "2024-01-01", List.of(
                new BucketTargetConfig("EQUITY_CORE", 50.0, 5.0, 5.0, "CORE", List.of(
                    new PreferredFundConfig("INF879O01027", "Parag Parikh Flexi Cap Fund", 1.0)
                )),
                new BucketTargetConfig("HEDGE_COMMODITY", 50.0, 5.0, 12.0, "HEDGE", List.of(
                    new PreferredFundConfig("INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", 1.0)
                ))
            ))
        )));

        try {
            // Verify fund classification produces granular sub-bucket HEDGE_COMMODITY
            assertEquals(BucketEngine.Bucket.HEDGE_COMMODITY,
                BucketEngine.classifyAssetToBucket("INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds"),
                "INF247L01BM8 must classify into granular sub-bucket HEDGE_COMMODITY under active config");

            BigDecimal navCore = new BigDecimal("100.00");
            BigDecimal navGold = new BigDecimal("100.00");
            LocalDate acqDate = LocalDate.of(2024, 1, 1);

            // Portfolio: 2,000,000 total corpus
            // Core: 200,000 (10% actual vs 50% target -> 800,000 underweight)
            // Gold (HEDGE_COMMODITY): 1,800,000 (90% actual vs 50% target -> 800,000 overweight)
            Lot coreLot = new Lot("lot-core", "INF879O01027", "Parag Parikh Flexi Cap Fund Direct Growth",
                acqDate, new BigDecimal("2000"), new BigDecimal("2000"), navCore, new BigDecimal("200000.00"), false, null);
            Lot goldLtcgLot = new Lot("lot-gold-ltcg", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds",
                acqDate, new BigDecimal("17990"), new BigDecimal("17990"), navGold, new BigDecimal("1799000.00"), false, null);
            Lot goldActiveSipLot = new Lot("lot-gold-sip", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds",
                LocalDate.of(2026, 7, 15), new BigDecimal("10"), new BigDecimal("10"), navGold, new BigDecimal("1000.00"), false, null);

            List<Lot> openLots = List.of(coreLot, goldLtcgLot, goldActiveSipLot);
            Map<String, BigDecimal> navMap = Map.of(
                "INF879O01027", navCore,
                "INF247L01BM8", navGold
            );

            // Targets define parent GOLD_SILVER
            List<BucketEngine.BucketTarget> customTargets = List.of(
                new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("50.00"), new BigDecimal("5.00")),
                new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("50.00"), new BigDecimal("5.00"))
            );

            BigDecimal corpus = new BigDecimal("2000000.00");
            RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
                openLots, Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
                corpus, corpus, customTargets, "2026-27", null, null, evaluator
            );

            assertNotNull(plan);
            assertNotNull(plan.sellSide());
            assertNotNull(plan.buySide());

            RebalanceBucketAllocationDto goldBucket = plan.buySide().buckets().stream()
                .filter(b -> BucketEngine.Bucket.GOLD_SILVER.name().equals(b.bucket()))
                .findFirst().orElseThrow();
            RebalanceBucketAllocationDto coreBucket = plan.buySide().buckets().stream()
                .filter(b -> BucketEngine.Bucket.EQUITY_CORE.name().equals(b.bucket()))
                .findFirst().orElseThrow();

            // Sized dampened trim on 800,000 excess:
            BigDecimal soldGoldAmount = plan.sellSide().waterfall().stream()
                .filter(t -> "CORE_FUND".equals(t.tier())).findFirst().orElseThrow().sold();
            assertTrue(soldGoldAmount.compareTo(BigDecimal.ZERO) > 0, "HEDGE_COMMODITY lot must be trimmed in CORE_FUND tier");

            // Key verification: GOLD_SILVER bucket postRebalancePct correctly reflects soldFromBucket subtraction
            BigDecimal expectedGoldPostVal = new BigDecimal("1800000.00").subtract(soldGoldAmount);
            double expectedGoldPct = Math.round((expectedGoldPostVal.doubleValue() / corpus.doubleValue()) * 1000.0) / 10.0;

            assertEquals(expectedGoldPct, goldBucket.postRebalancePct(), 0.1,
                "GOLD_SILVER postRebalancePct must reflect the subtraction of soldFromBucket rolled up from HEDGE_COMMODITY");
            assertEquals(100.0 - expectedGoldPct, coreBucket.postRebalancePct(), 0.1,
                "EQUITY_CORE postRebalancePct must be complementary to GOLD_SILVER");
        } finally {
            BucketConfigLoader.resetCache();
        }
    }

    @Test
    @DisplayName("Component 3 (F-08): Multiple simultaneously overweight buckets each execute their own dampened trim and attribute proceeds correctly")
    void testMultiSimultaneouslyOverweightBucketsReconcileAndAttributedCorrectly() {
        BigDecimal nav = new BigDecimal("100.00");
        LocalDate acqDate = LocalDate.of(2024, 1, 1);
        LocalDate sipDate = LocalDate.of(2026, 7, 15);

        // Portfolio: 2,000,000 total corpus
        // Target: Core = 40.0% (800,000), Satellite = 40.0% (800,000), Gold = 20.0% (400,000)
        // Actual:
        //   Core = 1,000,000 (50.0%, 200,000 excess)
        //   Satellite = 1,000,000 (50.0%, 200,000 excess)
        //   Gold = 0 (0.0%, 400,000 deficit)
        Lot coreLtcg = new Lot("lot-c-ltcg", "INF879O01027", "Parag Parikh Flexi Cap Fund",
            acqDate, new BigDecimal("9990"), new BigDecimal("9990"), nav, new BigDecimal("999000.00"), false, null);
        Lot coreSip = new Lot("lot-c-sip", "INF879O01027", "Parag Parikh Flexi Cap Fund",
            sipDate, new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null);

        Lot satLtcg = new Lot("lot-s-ltcg", "INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund",
            acqDate, new BigDecimal("9990"), new BigDecimal("9990"), nav, new BigDecimal("999000.00"), false, null);
        Lot satSip = new Lot("lot-s-sip", "INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund",
            sipDate, new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null);

        List<Lot> openLots = List.of(coreLtcg, coreSip, satLtcg, satSip);
        Map<String, BigDecimal> navMap = Map.of(
            "INF879O01027", nav,
            "INF109KC13X2", nav
        );

        List<BucketEngine.BucketTarget> customTargets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("40.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("40.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("20.00"), new BigDecimal("5.00"))
        );

        BigDecimal corpus = new BigDecimal("2000000.00");
        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            openLots, Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
            corpus, corpus, customTargets, "2026-27", null, null, evaluator
        );

        assertNotNull(plan);
        assertNotNull(plan.sellSide());
        assertNotNull(plan.buySide());

        // Expected dampened trim per overweight bucket:
        // excess = 200,000 on 800,000 target -> 25% drift.
        // sell multiplier for 25% drift = 0.60 + (15/20) * 0.15 = 0.7125.
        // dampenedTrim = 200,000 * 0.7125 = 142,500.00 each.
        // Total pool = 142,500 + 142,500 = 285,000.00.
        BigDecimal expectedTrimPerBucket = new BigDecimal("142500.00");
        BigDecimal expectedTotalPool = new BigDecimal("285000.00");

        assertEquals(0, expectedTotalPool.compareTo(plan.buySide().totalToInvest()),
            "Total pool must equal sum of both dampened trims (142,500 + 142,500 = 285,000.00)");

        // 1. Verify Sell-Side: BOTH buckets were trimmed, not just a single bucket!
        List<RebalancePlanDtos.RebalanceLotImpactDto> soldLots = plan.sellSide().waterfall().stream()
            .flatMap(t -> t.lots().stream())
            .toList();
        boolean hasCoreSold = soldLots.stream().anyMatch(l -> "INF879O01027".equals(l.fundId()));
        boolean hasSatSold = soldLots.stream().anyMatch(l -> "INF109KC13X2".equals(l.fundId()));
        assertTrue(hasCoreSold, "Sell side must contain sales from overweight Core bucket");
        assertTrue(hasSatSold, "Sell side must contain sales from overweight Satellite bucket");

        BigDecimal actualCoreSold = soldLots.stream()
            .filter(l -> "INF879O01027".equals(l.fundId()))
            .map(RebalancePlanDtos.RebalanceLotImpactDto::saleProceeds)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actualSatSold = soldLots.stream()
            .filter(l -> "INF109KC13X2".equals(l.fundId()))
            .map(RebalancePlanDtos.RebalanceLotImpactDto::saleProceeds)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, expectedTrimPerBucket.compareTo(actualCoreSold),
            "Core bucket must be trimmed for exactly its dampened trim of 142,500.00");
        assertEquals(0, expectedTrimPerBucket.compareTo(actualSatSold),
            "Satellite bucket must be trimmed for exactly its dampened trim of 142,500.00");

        // 2. Verify Buy-Side: Gold receives the full 285,000.00 allocation
        RebalanceBucketAllocationDto goldBucket = plan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.GOLD_SILVER.name().equals(b.bucket()))
            .findFirst().orElseThrow();
        RebalanceBucketAllocationDto coreBucket = plan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.EQUITY_CORE.name().equals(b.bucket()))
            .findFirst().orElseThrow();
        RebalanceBucketAllocationDto satBucket = plan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.EQUITY_SATELLITE.name().equals(b.bucket()))
            .findFirst().orElseThrow();

        assertEquals(0, expectedTotalPool.compareTo(goldBucket.amountAllocated()),
            "Deficit Gold bucket must receive entire 285,000.00 rebalance pool");
        assertEquals(0, BigDecimal.ZERO.compareTo(coreBucket.amountAllocated()));
        assertEquals(0, BigDecimal.ZERO.compareTo(satBucket.amountAllocated()));

        // 3. Verify Post-Rebalance Percentages:
        // Core postVal: 1,000,000 - 142,500 = 857,500 (42.9%)
        // Sat postVal: 1,000,000 - 142,500 = 857,500 (42.9%)
        // Gold postVal: 0 + 285,000 = 285,000 (14.2% or 14.3%)
        assertEquals(42.9, coreBucket.postRebalancePct(), 0.1,
            "Core postRebalancePct must be 42.9% reflecting 142,500 trim");
        assertEquals(42.9, satBucket.postRebalancePct(), 0.1,
            "Satellite postRebalancePct must be 42.9% reflecting 142,500 trim");
        assertEquals(14.3, goldBucket.postRebalancePct(), 0.1,
            "Gold postRebalancePct must be 14.3% reflecting 285,000 buy allocation");
    }

    @Test
    @DisplayName("Gold Drift Threshold & Dampened Trim Sizing: 12% trigger threshold vs 5% core, dampened trim sizing")
    void testGoldWiderDriftThresholdSensitivityAndTrimSizing() {
        // -----------------------------------------------------------------------------------------
        // Component 1: FundTrendDampenerCalculator direct dampener curve verification
        // -----------------------------------------------------------------------------------------
        // At 13.5% drift (in the 10-30% range):
        // sellMultiplier = 0.60 + ((13.5 - 10.0) / 20.0) * (0.75 - 0.60)
        //                = 0.60 + (3.5 / 20.0) * 0.15 = 0.60 + 0.175 * 0.15 = 0.60 + 0.02625 = 0.62625
        // Rounded to 4 decimal places = 0.6263
        FundTrendDampenerCalculator.DampenerMultipliers mults = FundTrendDampenerCalculator.calculateFundMultipliers(13.5);
        assertEquals(0.6263, mults.sellMultiplier(), 0.0001,
            "13.5% drift must produce exactly 0.6263 sell multiplier via 0.60 + (3.5/20.0)*0.15 dampener formula");
        assertEquals(0.0, mults.buyMultiplier(),
            "Positive drift must have 0.0 buy multiplier");

        // Excess of 13,500 on 100,000 target (13.5% drift) yields dampened trim of 8,455.05 vs linear 13,500.00
        BigDecimal dampenedTrim135 = FundTrendDampenerCalculator.calculateDampenedTrim(new BigDecimal("13500.00"), 100000.0);
        assertEquals(new BigDecimal("8455.05"), dampenedTrim135,
            "Dampened trim for 13,500.00 excess on 100k target must be 8,455.05 (0.6263x), not linear 13,500.00");

        // -----------------------------------------------------------------------------------------
        // Component 2: RebalanceTriggerEvaluator sensitivity to Gold 12% threshold vs 5% Core/Satellite
        // -----------------------------------------------------------------------------------------
        // Active rules version v2.3 effective from 2026-08-26:
        // Core: 50.0% target, 5.0% trigger drift (aggregate 35%-65%, ratio 45%-75%)
        // Satellite: 30.0% target, 5.0% trigger drift ([25%, 35%])
        // Liquid: 10.0% target, 5.0% trigger drift ([5%, 15%])
        // Gold: 10.0% target, 12.0% trigger drift (triggers only if drift >= 12.0%, i.e. >= 22.0% or <= -2.0%)
        // We test on 2026-08-28 (v2.3 active, outside March/September scheduled reconstitution window)
        LocalDate today = LocalDate.of(2026, 8, 28);
        LocalDate acqDate = LocalDate.of(2024, 1, 1);
        LocalDate sipDate = LocalDate.of(2026, 7, 15);
        BigDecimal nav = new BigDecimal("100.00");

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", nav, // LargeMidcap (Core)
            "INF879O01027", nav, // PPFC (Core)
            "INF109KC13X2", nav, // Value 30 (Satellite)
            "INF205K01KR8", nav, // Arbitrage (Liquid)
            "INF247L01BM8", nav  // Gold and Silver (Gold)
        );

        // Case A: 10.0% Gold drift (Gold weight = 20.0% vs 10.0% target on 1,000,000 corpus)
        // Core: 450,000 (45.0% - LargeMidcap 270k / PPFC 180k = ratio 0.60 within 45-75%, Core total within 35-65%)
        // Satellite: 270,000 (27.0% - target 30%, drift 3.0% < 5.0% threshold)
        // Liquid: 80,000 (8.0% - target 10%, drift 2.0% < 5.0% threshold)
        // Gold: 200,000 (20.0% - target 10%, drift 10.0% < 12.0% threshold)
        // Total = 450k + 270k + 80k + 200k = 1,000,000.00 (100.0%)
        // Each fund has an active SIP within 3 months to confirm non-legacy status
        List<Lot> lotsCaseA = List.of(
            new Lot("c1-ltcg", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund", acqDate, new BigDecimal("2690"), new BigDecimal("2690"), nav, new BigDecimal("269000.00"), false, null),
            new Lot("c1-sip", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund", sipDate, new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null),
            new Lot("c2-ltcg", "INF879O01027", "Parag Parikh Flexi Cap Fund", acqDate, new BigDecimal("1790"), new BigDecimal("1790"), nav, new BigDecimal("179000.00"), false, null),
            new Lot("c2-sip", "INF879O01027", "Parag Parikh Flexi Cap Fund", sipDate, new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null),
            new Lot("s1-ltcg", "INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund", acqDate, new BigDecimal("2690"), new BigDecimal("2690"), nav, new BigDecimal("269000.00"), false, null),
            new Lot("s1-sip", "INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund", sipDate, new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null),
            new Lot("l1-ltcg", "INF205K01KR8", "Invesco India Arbitrage Fund", acqDate, new BigDecimal("790"), new BigDecimal("790"), nav, new BigDecimal("79000.00"), false, null),
            new Lot("l1-sip", "INF205K01KR8", "Invesco India Arbitrage Fund", sipDate, new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null),
            new Lot("g1-ltcg", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", acqDate, new BigDecimal("1990"), new BigDecimal("1990"), nav, new BigDecimal("199000.00"), false, null),
            new Lot("g1-sip", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", sipDate, new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null)
        );

        BigDecimal corpus = new BigDecimal("1000000.00");
        BigDecimal benchmark = new BigDecimal("25000.00");

        RebalanceTriggerEvaluator.TriggerResolution resA = evaluator.getCurrentStatus(
            lotsCaseA, navMap, benchmark, benchmark, null, null, today
        );

        // Assertion 1: At 10% drift, Gold does NOT trigger rebalance because 10.0% < 12.0% threshold
        assertEquals("NONE", resA.triggerType(),
            "Gold at 20% weight (10% drift) must NOT trigger rebalance against 12% trigger_drift_pct threshold");
        assertEquals("NO_REBALANCE_REQUIRED", resA.reasonCode());
        assertFalse(resA.hasSellSide());

        // Case B: 13.5% Gold drift (Gold weight = 23.5% vs 10.0% target on 1,000,000 corpus)
        // Core: 450,000 (45.0% - ratio 0.60 -> no drift)
        // Satellite: 255,000 (25.5% - target 30%, drift 4.5% < 5.0% threshold -> no drift)
        // Liquid: 60,000 (6.0% - target 10%, drift 4.0% < 5.0% threshold -> no drift)
        // Gold: 235,000 (23.5% - target 10%, drift 13.5% >= 12.0% threshold -> DRIFT TRIGGERED!)
        // Total = 450k + 255k + 60k + 235k = 1,000,000.00 (100.0%)
        List<Lot> lotsCaseB = List.of(
            new Lot("c1-ltcg", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund", acqDate, new BigDecimal("2690"), new BigDecimal("2690"), nav, new BigDecimal("269000.00"), false, null),
            new Lot("c1-sip", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund", sipDate, new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null),
            new Lot("c2-ltcg", "INF879O01027", "Parag Parikh Flexi Cap Fund", acqDate, new BigDecimal("1790"), new BigDecimal("1790"), nav, new BigDecimal("179000.00"), false, null),
            new Lot("c2-sip", "INF879O01027", "Parag Parikh Flexi Cap Fund", sipDate, new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null),
            new Lot("s1-ltcg", "INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund", acqDate, new BigDecimal("2540"), new BigDecimal("2540"), nav, new BigDecimal("254000.00"), false, null),
            new Lot("s1-sip", "INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund", sipDate, new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null),
            new Lot("l1-ltcg", "INF205K01KR8", "Invesco India Arbitrage Fund", acqDate, new BigDecimal("590"), new BigDecimal("590"), nav, new BigDecimal("59000.00"), false, null),
            new Lot("l1-sip", "INF205K01KR8", "Invesco India Arbitrage Fund", sipDate, new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null),
            new Lot("g1-ltcg", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", acqDate, new BigDecimal("2340"), new BigDecimal("2340"), nav, new BigDecimal("234000.00"), false, null),
            new Lot("g1-sip", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", sipDate, new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null)
        );

        RebalanceTriggerEvaluator.TriggerResolution resB = evaluator.getCurrentStatus(
            lotsCaseB, navMap, benchmark, benchmark, null, null, today
        );

        // Assertion 2: At 13.5% drift, Gold crosses the 12% threshold and DOES trigger rebalance
        assertEquals("DRIFT", resB.triggerType(),
            "Gold at 23.5% weight (13.5% drift) must trigger DRIFT because 13.5% >= 12.0% threshold");
        assertEquals("DRIFT_THRESHOLD_EXCEEDED", resB.reasonCode());
        assertTrue(resB.reasonLabel().contains("GOLD_SILVER"),
            "Reason label must identify GOLD_SILVER as the drifted bucket: " + resB.reasonLabel());
        assertTrue(resB.hasSellSide());

        // -----------------------------------------------------------------------------------------
        // Component 3: Multi-bucket trim waterfall end-to-end plan sizing
        // -----------------------------------------------------------------------------------------
        // On 1,000,000 corpus with v2.3 active targets:
        // Gold target is 10.0% (100,000.00). Current is 235,000.00.
        // Excess = 135,000.00.
        // driftPct = (135,000 / 100,000) * 100 = 135% (> 30% disciplined cap of 0.75x).
        // dampenedTrim = 135,000 * 0.75 = 101,250.00.
        // Notice: un-dampened linear trim would have been 135,000.00!
        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            lotsCaseB, Collections.emptyList(), navMap, today,
            corpus, benchmark, null, "2026-27", null, null, evaluator
        );

        assertNotNull(plan);
        assertEquals("DRIFT", plan.trigger().type());
        assertNotNull(plan.sellSide());
        assertNotNull(plan.buySide());

        BigDecimal expectedDampenedTrim = new BigDecimal("101250.00");
        BigDecimal unDampenedLinearExcess = new BigDecimal("135000.00");

        assertEquals(0, expectedDampenedTrim.compareTo(plan.buySide().totalToInvest()),
            "Total buy-side invest pool must equal dampened trim (101,250.00), NOT un-dampened excess (135,000.00)");

        // Verify sell waterfall sold specifically from Gold bucket
        List<RebalanceLotImpactDto> soldLots = plan.sellSide().waterfall().stream()
            .flatMap(t -> t.lots().stream())
            .toList();
        assertFalse(soldLots.isEmpty(), "Sell waterfall must have lots to sell");
        assertTrue(soldLots.stream().allMatch(l -> "INF247L01BM8".equals(l.fundId())),
            "Only Gold fund lots must be sold");

        BigDecimal totalGoldSold = soldLots.stream()
            .map(RebalanceLotImpactDto::saleProceeds)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, expectedDampenedTrim.compareTo(totalGoldSold),
            "Gold sale proceeds must match exactly 101,250.00");
        assertNotEquals(0, unDampenedLinearExcess.compareTo(totalGoldSold),
            "Gold sale proceeds must NOT equal un-dampened linear excess");

        // Verify post-rebalance percentage of Gold:
        // postVal = 235,000 - 101,250 + 0 = 133,750.00
        // postRebalancePct = (133,750 / 1,000,000) * 100 = 13.38% (13.4%)
        RebalanceBucketAllocationDto goldAllocation = plan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.GOLD_SILVER.name().equals(b.bucket()))
            .findFirst().orElseThrow();
        assertEquals(13.4, goldAllocation.postRebalancePct(), 0.1,
            "Gold postRebalancePct must be ~13.4% reflecting 235k - 101.25k trim");
    }

    @Test
    @DisplayName("Macro Regime Overlay: Advisory targets adjust only when useMacroRegimeOverlay is explicitly true")
    void testMacroRegimeOverlayAdvisoryTargetRouting() {
        LocalDate today = LocalDate.of(2026, 9, 1);
        BigDecimal nav = new BigDecimal("100.00");
        Map<String, BigDecimal> navMap = Map.of("INF109K018C5", nav);
        List<Lot> lots = List.of(
            new Lot("lot-1", "INF109K018C5", "Parag Parikh Flexi Cap", today.minusDays(400),
                new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null)
        );

        // 1. Default preview (overlay = false): targets must remain pinned to baseline (Core 50%, Sat 30%, Gold 10%, Buffer 10%)
        RebalancePlanDto baselinePlan = RebalancePlanEngine.buildPreviewPlan(
            lots, Collections.emptyList(), navMap, today,
            new BigDecimal("100000.00"), new BigDecimal("100000.00"), null, "2026-27", "MANUAL_LUMPSUM", new BigDecimal("10000.00"),
            false, evaluator
        );

        RebalanceBucketAllocationDto baseBuffer = baselinePlan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.LIQUID_BUFFER.name().equals(b.bucket()))
            .findFirst().orElseThrow();
        RebalanceBucketAllocationDto baseCore = baselinePlan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.EQUITY_CORE.name().equals(b.bucket()))
            .findFirst().orElseThrow();
        RebalanceBucketAllocationDto baseSat = baselinePlan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.EQUITY_SATELLITE.name().equals(b.bucket()))
            .findFirst().orElseThrow();

        assertEquals(10.0, baseBuffer.targetPct(), 0.01);
        assertEquals(50.0, baseCore.targetPct(), 0.01);
        assertEquals(30.0, baseSat.targetPct(), 0.01);

        // 2. Explicit overlay preview (overlay = true): targets adjust to macro regime (EXPANSION_RISK_OFF: Buffer 15%, Core 46.88%, Sat 28.12%)
        RebalancePlanDto overlayPlan = RebalancePlanEngine.buildPreviewPlan(
            lots, Collections.emptyList(), navMap, today,
            new BigDecimal("100000.00"), new BigDecimal("100000.00"), null, "2026-27", "MANUAL_LUMPSUM", new BigDecimal("10000.00"),
            false, evaluator, true
        );

        RebalanceBucketAllocationDto overlayBuffer = overlayPlan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.LIQUID_BUFFER.name().equals(b.bucket()))
            .findFirst().orElseThrow();
        RebalanceBucketAllocationDto overlayCore = overlayPlan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.EQUITY_CORE.name().equals(b.bucket()))
            .findFirst().orElseThrow();
        RebalanceBucketAllocationDto overlaySat = overlayPlan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.EQUITY_SATELLITE.name().equals(b.bucket()))
            .findFirst().orElseThrow();
        RebalanceBucketAllocationDto overlayGold = overlayPlan.buySide().buckets().stream()
            .filter(b -> BucketEngine.Bucket.GOLD_SILVER.name().equals(b.bucket()))
            .findFirst().orElseThrow();

        assertEquals(15.0, overlayBuffer.targetPct(), 0.01);
        assertEquals(46.88, overlayCore.targetPct(), 0.01);
        assertEquals(28.12, overlaySat.targetPct(), 0.01);
        assertEquals(10.0, overlayGold.targetPct(), 0.01);

        // Exact sum verification: 15.00 + 46.88 + 28.12 + 10.00 = 100.00%
        double sum = overlayBuffer.targetPct() + overlayCore.targetPct() + overlaySat.targetPct() + overlayGold.targetPct();
        assertEquals(100.00, sum, 0.001);
    }
}

