package com.portfolioos.core.service;

import com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.persistence.TriggerHistoryRepository;
import com.portfolioos.core.valuation.BucketEngine;

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

        // Verify buy side sizing: 5% pool on 2,000,000 corpus = ₹100,000.00
        assertNotNull(plan.buySide());
        assertEquals(new BigDecimal("100000.00"), plan.buySide().totalToInvest(), "5% pool on 2M corpus must yield exactly ₹100,000.00 total to invest");
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
}
