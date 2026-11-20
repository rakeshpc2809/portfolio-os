package com.portfolioos.core.service;

import com.portfolioos.core.common.PortfolioConstants;
import com.portfolioos.core.dtos.RebalancePlanDtos.RebalanceTriggerDto;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.persistence.TriggerHistoryRepository;
import com.portfolioos.core.rules.BucketConfigLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RebalanceTriggerEvaluatorTest {

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
    @DisplayName("getCurrentStatus called twice in a row produces zero DB writes")
    void testGetCurrentStatusZeroSideEffects() {
        assertEquals(0, repository.getRecordCount(), "Initial DB must be empty");

        RebalanceTriggerEvaluator.TriggerResolution res1 = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("20000.00"), new BigDecimal("25000.00"), // 20% drawdown
            null, null, LocalDate.of(2026, 8, 1)
        );
        assertEquals("DRAWDOWN", res1.triggerType());
        assertEquals(0, repository.getRecordCount(), "getCurrentStatus call 1 must produce 0 DB side-effects");

        RebalanceTriggerEvaluator.TriggerResolution res2 = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("20000.00"), new BigDecimal("25000.00"),
            null, null, LocalDate.of(2026, 8, 1)
        );
        assertEquals("DRAWDOWN", res2.triggerType());
        assertEquals(0, repository.getRecordCount(), "getCurrentStatus call 2 must produce 0 DB side-effects");
    }

    @Test
    @DisplayName("Fail-Loud Invariant: RebalanceTriggerEvaluator throws IllegalStateException on null or non-positive benchmark inputs")
    void testNullBenchmarkThrowsException() {
        List<Lot> openLots = List.of(
            new Lot("l1", "INF109KC12U0", "ICICI LargeMidcap 250", LocalDate.of(2025, 1, 1),
                new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100000"), false, BigDecimal.ZERO)
        );
        Map<String, BigDecimal> navMap = Map.of("INF109KC12U0", new BigDecimal("150"));

        IllegalStateException exNullBoth = assertThrows(IllegalStateException.class, () ->
            evaluator.getCurrentStatus(
                openLots, navMap,
                null, null,
                null, null, LocalDate.of(2026, 8, 1)
            )
        );
        assertTrue(exNullBoth.getMessage().contains("CRITICAL BENCHMARK FAILURE"));

        IllegalStateException exNullHigh = assertThrows(IllegalStateException.class, () ->
            evaluator.getCurrentStatus(
                openLots, navMap,
                new BigDecimal("25000.00"), null,
                null, null, LocalDate.of(2026, 8, 1)
            )
        );
        assertTrue(exNullHigh.getMessage().contains("CRITICAL BENCHMARK FAILURE"));
        assertTrue(exNullHigh.getMessage().contains("52-week peak"));

        IllegalStateException exZeroCurr = assertThrows(IllegalStateException.class, () ->
            evaluator.getCurrentStatus(
                openLots, navMap,
                BigDecimal.ZERO, new BigDecimal("25000.00"),
                null, null, LocalDate.of(2026, 8, 1)
            )
        );
        assertTrue(exZeroCurr.getMessage().contains("CRITICAL BENCHMARK FAILURE"));
        assertTrue(exZeroCurr.getMessage().contains("live level"));
    }

    @Test
    @DisplayName("Cycle-latched drawdown blocks refiring same or lower rung within active cycle")
    void testSellCooldownBlocksDrawdown() {
        LocalDate firstDate = LocalDate.of(2026, 8, 1);
        repository.recordDrawdownCycleExecution(new BigDecimal("100000.00"), 20);

        LocalDate testDate = LocalDate.of(2026, 8, 10); // 9 days later
        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("20000.00"), new BigDecimal("25000.00"),
            null, null, testDate
        );

        assertEquals("NONE", res.triggerType());
        assertEquals("DRAWDOWN_RUNG_ALREADY_DEPLOYED", res.reasonCode());
    }

    @Test
    @DisplayName("CASH_INFLOW_NEEDED fires on underweight bucket despite active 30-day sell cooldown")
    void testGoldFloorBackstopBypassesSellCooldown() {
        LocalDate lastSellDate = LocalDate.of(2026, 11, 20);
        repository.recordExecution("plan-1", "DRIFT", "DRIFT", lastSellDate.atStartOfDay(), true, false, "", 0);

        LocalDate testDate = LocalDate.of(2026, 11, 25); // 5 days after sell (< 30 days)
        // Gold at 5% (underweight vs 10% target, band 2.5% -> lower threshold 7.5%)
        Lot satLot = new Lot("lot-sat", "INF109KC13X2", "ICICI Value 30", testDate.minusDays(400), new BigDecimal("3000"), new BigDecimal("3000"), new BigDecimal("100.00"), new BigDecimal("300000.00"), false, null);
        Lot coreLot = new Lot("lot-core", "INF109KC12U0", "ICICI LargeMidcap", testDate.minusDays(400), new BigDecimal("5500"), new BigDecimal("5500"), new BigDecimal("100.00"), new BigDecimal("550000.00"), false, null);
        Lot goldLot = new Lot("lot-gold", "INF247L01BM8", "Motilal Gold FoF", testDate.minusDays(400), new BigDecimal("500"), new BigDecimal("500"), new BigDecimal("100.00"), new BigDecimal("50000.00"), false, null);
        Lot arbLot = new Lot("lot-arb", "INF205K01KR8", "Invesco Arbitrage", testDate.minusDays(400), new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100.00"), new BigDecimal("100000.00"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC13X2", new BigDecimal("100.00"),
            "INF109KC12U0", new BigDecimal("100.00"),
            "INF247L01BM8", new BigDecimal("100.00"),
            "INF205K01KR8", new BigDecimal("100.00")
        );

        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            List.of(satLot, coreLot, goldLot, arbLot), navMap,
            new BigDecimal("25000.00"), new BigDecimal("25000.00"), // No drawdown
            null, null, testDate
        );

        assertTrue(res.sellCooldownActive(), "Sell cooldown should be active");
        assertEquals("CASH_INFLOW_NEEDED", res.triggerType(), "Cash inflow needed fires for underweight bucket without triggering sells");
        assertFalse(res.hasSellSide());
    }

    @Test
    @DisplayName("getLastGoldBuyDate contract queries has_gold_buy = 1 across all trigger types and ignores has_gold_buy = 0")
    void testLastGoldBuyDateQueryContract() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 10, 0);
        repository.recordExecution("p1", "DRIFT", "DRIFT", t1, true, true, "", 0);

        assertTrue(repository.getLastGoldBuyDate().isPresent());
        assertEquals(t1, repository.getLastGoldBuyDate().get());

        LocalDateTime t2 = LocalDateTime.of(2026, 5, 1, 10, 0);
        repository.recordExecution("p2", "DRAWDOWN", "DRAWDOWN_TIER_15", t2, true, true, "", 15);

        assertEquals(t2, repository.getLastGoldBuyDate().get(), "getLastGoldBuyDate must return latest timestamp where has_gold_buy = 1");

        // Add later row t3 with has_gold_buy = 0
        LocalDateTime t3 = LocalDateTime.of(2026, 6, 1, 10, 0);
        repository.recordExecution("p3", "DRAWDOWN", "DRAWDOWN_TIER_20", t3, true, false, "", 20);

        // Must STILL return t2 (May 1), proving WHERE has_gold_buy = 1 filter is load-bearing!
        assertEquals(t2, repository.getLastGoldBuyDate().get(), "Must ignore later row t3 because has_gold_buy = 0");
    }

    @Test
    @DisplayName("Priority suppression: DRAWDOWN suppresses DRIFT and SCHEDULED")
    void testPrioritySuppressionDrawdownOverDriftAndScheduled() {
        // Drawdown active (20% drawdown) + March 15 window (scheduled month)
        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("20000.00"), new BigDecimal("25000.00"), // 20% drawdown
            null, null, LocalDate.of(2026, 3, 15)
        );

        assertEquals("DRAWDOWN", res.triggerType(), "DRAWDOWN must win over DRIFT and SCHEDULED");
        assertEquals("DRAWDOWN_TIER_20", res.reasonCode());
        assertTrue(res.hasSellSide());
    }

    @Test
    @DisplayName("Priority suppression: DRIFT suppresses SCHEDULED")
    void testPrioritySuppressionDriftOverScheduled() {
        // No drawdown (current == high) + March 15 window + drifted Small Cap bucket (13% > 11.5%)
        Lot scLot = new Lot("lot-sc", "INF204K01K15", "Nippon Small Cap", LocalDate.of(2026, 3, 15).minusDays(400), new BigDecimal("1300"), new BigDecimal("1300"), new BigDecimal("100.00"), new BigDecimal("130000.00"), false, null);
        Lot coreLot = new Lot("lot-core", "INF109KC12U0", "ICICI LargeMidcap", LocalDate.of(2026, 3, 15).minusDays(400), new BigDecimal("8700"), new BigDecimal("8700"), new BigDecimal("100.00"), new BigDecimal("870000.00"), false, null);

        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            List.of(scLot, coreLot), Map.of("INF204K01K15", new BigDecimal("100.00"), "INF109KC12U0", new BigDecimal("100.00")),
            new BigDecimal("25000.00"), new BigDecimal("25000.00"), // 0% drawdown
            null, null, LocalDate.of(2026, 3, 15)
        );

        assertEquals("DRIFT", res.triggerType(), "DRIFT must win over SCHEDULED when drawdown is zero");
        assertEquals("DRIFT_THRESHOLD_EXCEEDED", res.reasonCode());
        assertTrue(res.hasSellSide());
    }

    @Test
    @DisplayName("RebalanceTriggerDto constructor populates legacyTriggerType as INDUCED for DRAWDOWN/DRIFT")
    void testRebalanceTriggerDtoBackwardCompatibility() {
        RebalanceTriggerDto dto1 = new RebalanceTriggerDto("DRAWDOWN", "DRAWDOWN_TIER_15", "15% Drawdown", "Window", null);
        assertEquals("DRAWDOWN", dto1.type());
        assertEquals("INDUCED", dto1.legacyTriggerType());
        assertTrue(dto1.isInduced());

        RebalanceTriggerDto dto2 = new RebalanceTriggerDto("DRIFT", "DRIFT_THRESHOLD_EXCEEDED", "Drift Exceeded", "Window", null);
        assertEquals("DRIFT", dto2.type());
        assertEquals("INDUCED", dto2.legacyTriggerType());
        assertTrue(dto2.isInduced());

        RebalanceTriggerDto dto3 = new RebalanceTriggerDto("SCHEDULED", "SCHEDULED_RECONSTITUTION", "Scheduled", "Window", null);
        assertEquals("SCHEDULED", dto3.type());
        assertEquals("SCHEDULED", dto3.legacyTriggerType());
        assertFalse(dto3.isInduced());
    }

    @Test
    @DisplayName("Fail-Loud Invariant: RebalanceTriggerEvaluator throws IllegalStateException if live NAV is missing")
    void testGetCurrentStatusThrowsOnMissingNav() {
        Lot lot = new Lot("l1", "INF109KC12U0", "ICICI LargeMidcap 250", LocalDate.of(2025, 1, 1),
            new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100000"), false, BigDecimal.ZERO);

        Map<String, BigDecimal> emptyNavMap = Map.of();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            evaluator.getCurrentStatus(
                List.of(lot), emptyNavMap,
                new BigDecimal("20000.00"), new BigDecimal("25000.00"),
                null, null, LocalDate.of(2026, 8, 1)
            )
        );
        assertTrue(ex.getMessage().contains("CRITICAL VALUATION ERROR"));
        assertTrue(ex.getMessage().contains("INF109KC12U0"));
    }

    @Test
    @DisplayName("F2 Test: Drift sell trigger is blocked by active 30-day sell cooldown")
    void testGoldFloorBackstopFiresEvenWhenDriftBlockedByCooldown() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        LocalDate lastSellDate = today.minusDays(10); // 10 days ago -> 30-day sell cooldown is ACTIVE!

        // Record prior sell to activate sell cooldown
        repository.recordExecution("plan-prior-sell", "DRIFT", "CORE_DRIFT_TRIM", lastSellDate.atStartOfDay(), true, false, "{}", 0);

        // Portfolio setup with Satellite bucket severely drifted (35% target + 5% band -> actual 45% -> DRIFT trigger)
        Lot satLot = new Lot("lot-sat", "INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund",
            today.minusDays(400), new BigDecimal("4500"), new BigDecimal("4500"), new BigDecimal("100.00"), new BigDecimal("450000.00"), false, null);
        Lot coreLot = new Lot("lot-core", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
            today.minusDays(400), new BigDecimal("4500"), new BigDecimal("4500"), new BigDecimal("100.00"), new BigDecimal("450000.00"), false, null);
        Lot goldLot = new Lot("lot-gold", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds",
            today.minusDays(400), new BigDecimal("500"), new BigDecimal("500"), new BigDecimal("100.00"), new BigDecimal("50000.00"), false, null);
        Lot arbLot = new Lot("lot-arb", "INF205K01KR8", "Invesco India Arbitrage Fund",
            today.minusDays(400), new BigDecimal("500"), new BigDecimal("500"), new BigDecimal("100.00"), new BigDecimal("50000.00"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC13X2", new BigDecimal("100.00"),
            "INF109KC12U0", new BigDecimal("100.00"),
            "INF247L01BM8", new BigDecimal("100.00"),
            "INF205K01KR8", new BigDecimal("100.00")
        );

        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            List.of(satLot, coreLot, goldLot, arbLot), navMap, new BigDecimal("1000.00"), new BigDecimal("1000.00"), null, null, today
        );

        assertEquals("NONE", res.triggerType(),
            "Drift sell must be suppressed when 30-day sell cooldown is active");
        assertEquals("DRIFT_BLOCKED_BY_COOLDOWN", res.reasonCode(),
            "Suppressed drift reasonCode must be retained so sell audit trail is not erased");
        assertFalse(res.hasSellSide());
    }

    @Test
    @DisplayName("F8: Deeper drawdown rung escalation bypasses cycle latching and active 30-day sell cooldown")
    void testDrawdownEscalationBypassesSellCooldown() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        LocalDate lastSellDate = today.minusDays(10); // 10 days ago (< 30 days)

        // Step 1: Record a prior 10% drawdown execution 10 days ago
        repository.recordDrawdownCycleExecution(new BigDecimal("100000.00"), 10);
        repository.recordExecution("plan-dd-10", "DRAWDOWN", "DRAWDOWN_TIER_10", lastSellDate.atStartOfDay(), true, false, "{}", 10);

        // Step 2: Market drops deeper to 20% drawdown (curr = 8000, high = 10000 -> 20.00% DD)
        RebalanceTriggerEvaluator.TriggerResolution resEscalated = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("8000.00"), new BigDecimal("10000.00"),
            null, null, today
        );

        // F8 Invariant: Rung 20 > Rung 10, so cooldown is bypassed and DRAWDOWN_TIER_20 fires!
        assertEquals("DRAWDOWN", resEscalated.triggerType(), "Drawdown tier escalation (10% -> 20%) must bypass 30-day sell cooldown");
        assertEquals("DRAWDOWN_TIER_20", resEscalated.reasonCode());
        assertTrue(resEscalated.hasSellSide());

        // Step 3: Record the 20% execution
        repository.recordDrawdownCycleExecution(new BigDecimal("100000.00"), 20);
        repository.recordExecution("plan-dd-20", "DRAWDOWN", "DRAWDOWN_TIER_20", today.atStartOfDay(), true, false, "{}", 20);

        // Step 4: 2 days later, market is still at 20% drawdown (same rung, not escalated)
        LocalDate twoDaysLater = today.plusDays(2);
        RebalanceTriggerEvaluator.TriggerResolution resBlocked = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("8000.00"), new BigDecimal("10000.00"),
            null, null, twoDaysLater
        );

        // Same rung (20 == 20, not deeper) is cycle-latched
        assertEquals("NONE", resBlocked.triggerType());
        assertEquals("DRAWDOWN_RUNG_ALREADY_DEPLOYED", resBlocked.reasonCode());
        assertFalse(resBlocked.hasSellSide());

        // Step 5: Market crashes deeper to 30% drawdown (curr = 7000, high = 10000 -> 30.00% DD)
        RebalanceTriggerEvaluator.TriggerResolution resCapitulation = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("7000.00"), new BigDecimal("10000.00"),
            null, null, twoDaysLater
        );

        // Rung 30 > Rung 20, so cooldown is bypassed AGAIN for tier 30!
        assertEquals("DRAWDOWN", resCapitulation.triggerType(), "Drawdown tier escalation (20% -> 30%) must bypass 30-day sell cooldown");
        assertEquals("DRAWDOWN_TIER_30", resCapitulation.reasonCode());
        assertTrue(resCapitulation.hasSellSide());
    }

    @Test
    @DisplayName("F10: PortfolioConstants.calculateDrawdownPct clamps negative drawdowns and rounds to 2 decimals HALF_UP")
    void testDrawdownCalculationClampingAndRounding() {
        // currentVal >= rollingHigh -> strictly 0.0
        assertEquals(0.0, PortfolioConstants.calculateDrawdownPct(new BigDecimal("10500.00"), new BigDecimal("10000.00")));
        assertEquals(0.0, PortfolioConstants.calculateDrawdownPct(new BigDecimal("10000.00"), new BigDecimal("10000.00")));

        // Null / negative benchmark disarm in raw calculator
        assertEquals(0.0, PortfolioConstants.calculateDrawdownPct(null, new BigDecimal("10000.00")));
        assertEquals(0.0, PortfolioConstants.calculateDrawdownPct(new BigDecimal("9000.00"), null));
        assertEquals(0.0, PortfolioConstants.calculateDrawdownPct(BigDecimal.ZERO, new BigDecimal("10000.00")));

        // requireValidDrawdownPct fail-loud checks
        assertThrows(IllegalStateException.class, () ->
            PortfolioConstants.requireValidDrawdownPct(null, new BigDecimal("10000.00"), "Nifty 500 TRI"));
        assertThrows(IllegalStateException.class, () ->
            PortfolioConstants.requireValidDrawdownPct(new BigDecimal("9000.00"), null, "Nifty 500 TRI"));
        assertThrows(IllegalStateException.class, () ->
            PortfolioConstants.requireValidDrawdownPct(BigDecimal.ZERO, new BigDecimal("10000.00"), "Nifty 500 TRI"));
        assertThrows(IllegalStateException.class, () ->
            PortfolioConstants.requireValidDrawdownPct(new BigDecimal("9000.00"), BigDecimal.ZERO, "Nifty 500 TRI"));
        assertEquals(10.0, PortfolioConstants.requireValidDrawdownPct(new BigDecimal("9000.00"), new BigDecimal("10000.00"), "Nifty 500 TRI"));

        // Exact 2-decimal HALF_UP rounding
        // 10000 - 8999 = 1001 -> 1001/10000 = 10.01%
        assertEquals(10.01, PortfolioConstants.calculateDrawdownPct(new BigDecimal("8999.00"), new BigDecimal("10000.00")));

        // Discriminating 9.96% drawdown case:
        // high = 10000.00, curr = 9004.00 -> diff = 996.00 -> 9.96%
        // Under old 1dp rounding (Math.round(dd * 10.0) / 10.0), 9.96% was rounded to 10.0% and spuriously armed TIER_10.
        // Under unified 2dp rounding, 9.96% remains 9.96%, strictly < 10.00%, preventing spurious TIER_10 arming!
        assertEquals(9.96, PortfolioConstants.calculateDrawdownPct(new BigDecimal("9004.00"), new BigDecimal("10000.00")));

        com.portfolioos.core.persistence.TriggerHistoryRepository memoryRepo = new com.portfolioos.core.persistence.TriggerHistoryRepository(":memory:");
        memoryRepo.recordExecution("plan-gold", "SCHEDULED", "CONFIRMED_EXECUTION", LocalDate.of(2026, 8, 25).atStartOfDay(), false, true, "{}", 0);
        RebalanceTriggerEvaluator evaluator = new RebalanceTriggerEvaluator(memoryRepo);
        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("9004.00"), new BigDecimal("10000.00"),
            null, null, LocalDate.of(2026, 8, 26)
        );
        assertEquals("NONE", res.triggerType(), "9.96% drawdown must NOT arm TIER_10 rebalance under 2dp precision");
        assertEquals("NONE", res.drawdownContext().armedTier(), "armedTier must remain NONE at 9.96% DD");
        assertEquals(9.96, res.drawdownContext().currentDrawdownPct());
    }
}
