package com.portfolioos.core.service;

import com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.persistence.TriggerHistoryRepository;
import com.portfolioos.core.rules.BucketConfigLoader;
import com.portfolioos.core.valuation.BucketEngine;
import com.portfolioos.core.valuation.RebalanceWaterfallEngine;
import com.portfolioos.core.valuation.RebalanceWaterfallEngine.TrimDestinationAllocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RebalanceExecutionServiceTest {

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
    @DisplayName("Fixture 1: Path A — Small Cap surge (13%) + Gold deficit (7%): full trim routes to Gold")
    void testPathA_SmallCapSurge_RoutesToGold() {
        BigDecimal portfolioTotal = new BigDecimal("1000000.00");
        // Small Cap target 10% (100k), actual 13% (130k) -> Overweight by 3% (30k)
        BigDecimal trimAmount = new BigDecimal("30000.00");

        // Gold target 10% (100k), actual 7% (70k) -> Deficit 30k (3%)
        BigDecimal goldValue = new BigDecimal("70000.00");
        BigDecimal goldTarget = new BigDecimal("100000.00");

        // Arbitrage target 10% (100k), actual 100k
        BigDecimal arbValue = new BigDecimal("100000.00");
        BigDecimal arbTarget = new BigDecimal("100000.00");

        // Core target 50% (500k), actual 500k
        BigDecimal coreValue = new BigDecimal("500000.00");
        BigDecimal coreTarget = new BigDecimal("500000.00");

        TrimDestinationAllocation alloc = RebalanceWaterfallEngine.routeTrimProceeds(
            trimAmount, goldValue, goldTarget, arbValue, arbTarget, coreValue, coreTarget, portfolioTotal
        );

        assertNotNull(alloc);
        assertEquals(new BigDecimal("30000.00"), alloc.toGold(), "Full 30,000 trim must route to Gold to satisfy its 30,000 deficit");
        assertEquals(new BigDecimal("0.00"), alloc.toArbTarget(), "Zero to Arbitrage target fill");
        assertEquals(new BigDecimal("0.00"), alloc.toCore(), "Zero to Core fill");
        assertEquals(new BigDecimal("0.00"), alloc.toArbTerminal(), "Zero to Arbitrage terminal sink");
        assertEquals(new BigDecimal("0.00"), alloc.toCashOverflow(), "Zero to cash overflow");
    }

    @Test
    @DisplayName("Fixture 2: Path B1 — Small Cap surge (13%) + Arbitrage deficit (9%) + Core deficit (48%): 1% to Arbitrage, 2% to Core")
    void testPathB1_SmallCapSurge_ArbitrageAndCoreDeficits() {
        BigDecimal portfolioTotal = new BigDecimal("1000000.00");
        // Small Cap surge (13%) -> Trim amount = 30,000 (3%)
        BigDecimal trimAmount = new BigDecimal("30000.00");

        // Gold target 10% (100k), actual 100k (deficit 0)
        BigDecimal goldValue = new BigDecimal("100000.00");
        BigDecimal goldTarget = new BigDecimal("100000.00");

        // Arbitrage target 10% (100k), actual 90k (deficit 10k = 1%)
        BigDecimal arbValue = new BigDecimal("90000.00");
        BigDecimal arbTarget = new BigDecimal("100000.00");

        // Core target 50% (500k), actual 480k (deficit 20k = 2%)
        BigDecimal coreValue = new BigDecimal("480000.00");
        BigDecimal coreTarget = new BigDecimal("500000.00");

        TrimDestinationAllocation alloc = RebalanceWaterfallEngine.routeTrimProceeds(
            trimAmount, goldValue, goldTarget, arbValue, arbTarget, coreValue, coreTarget, portfolioTotal
        );

        assertNotNull(alloc);
        assertEquals(new BigDecimal("0.00"), alloc.toGold(), "Step 1 (Gold): 0 (deficit 0)");
        assertEquals(new BigDecimal("10000.00"), alloc.toArbTarget(), "Step 2 (Arbitrage target fill): 10,000 (1%)");
        assertEquals(new BigDecimal("20000.00"), alloc.toCore(), "Step 3 (Core deficit fill): 20,000 (2%)");
        assertEquals(new BigDecimal("0.00"), alloc.toArbTerminal(), "Step 4 (Arbitrage terminal sink): 0");
        assertEquals(new BigDecimal("0.00"), alloc.toCashOverflow(), "Step 5 (Cash overflow): 0");
    }

    @Test
    @DisplayName("Fixture 3: Path B2 — Small Cap surge (13%), all deficits zero, Arbitrage at 10%: full 3% to Arbitrage terminal sink (lands at 13% <= 15% cap)")
    void testPathB2_SmallCapSurge_ArbitrageTerminalSink() {
        BigDecimal portfolioTotal = new BigDecimal("1000000.00");
        // Small Cap surge (13%) -> Trim amount = 30,000 (3%)
        BigDecimal trimAmount = new BigDecimal("30000.00");

        // Deficits zero
        BigDecimal goldValue = new BigDecimal("100000.00");
        BigDecimal goldTarget = new BigDecimal("100000.00");

        BigDecimal arbValue = new BigDecimal("100000.00");
        BigDecimal arbTarget = new BigDecimal("100000.00");

        BigDecimal coreValue = new BigDecimal("500000.00");
        BigDecimal coreTarget = new BigDecimal("500000.00");

        TrimDestinationAllocation alloc = RebalanceWaterfallEngine.routeTrimProceeds(
            trimAmount, goldValue, goldTarget, arbValue, arbTarget, coreValue, coreTarget, portfolioTotal
        );

        assertNotNull(alloc);
        assertEquals(new BigDecimal("0.00"), alloc.toGold(), "Step 1 (Gold): 0");
        assertEquals(new BigDecimal("0.00"), alloc.toArbTarget(), "Step 2 (Arbitrage target fill): 0");
        assertEquals(new BigDecimal("0.00"), alloc.toCore(), "Step 3 (Core deficit fill): 0");
        assertEquals(new BigDecimal("30000.00"), alloc.toArbTerminal(), "Step 4 (Arbitrage terminal sink): 30,000 (3%)");
        assertEquals(new BigDecimal("0.00"), alloc.toCashOverflow(), "Step 5 (Cash overflow): 0");

        BigDecimal arbFinal = arbValue.add(alloc.toArbTarget()).add(alloc.toArbTerminal());
        assertEquals(new BigDecimal("130000.00"), arbFinal, "Arbitrage final value lands at 130,000 (13% <= 15% ceiling cap)");
    }

    @Test
    @DisplayName("Fixture 4: Path B3 (Overflow) — Arbitrage already at 14%, large 4% trim, all deficits zero: 1% fills Arbitrage to 15%, remaining 3% to cash overflow")
    void testPathB3_ArbitrageTerminalSinkOverflowToCash() {
        BigDecimal portfolioTotal = new BigDecimal("1000000.00");
        // Large trim amount = 40,000 (4%)
        BigDecimal trimAmount = new BigDecimal("40000.00");

        // Deficits zero
        BigDecimal goldValue = new BigDecimal("100000.00");
        BigDecimal goldTarget = new BigDecimal("100000.00");

        // Arbitrage at 140,000 (14%), 15% cap = 150,000 -> Headroom = 10,000 (1%)
        BigDecimal arbValue = new BigDecimal("140000.00");
        BigDecimal arbTarget = new BigDecimal("100000.00");

        BigDecimal coreValue = new BigDecimal("500000.00");
        BigDecimal coreTarget = new BigDecimal("500000.00");

        TrimDestinationAllocation alloc = RebalanceWaterfallEngine.routeTrimProceeds(
            trimAmount, goldValue, goldTarget, arbValue, arbTarget, coreValue, coreTarget, portfolioTotal
        );

        assertNotNull(alloc);
        assertEquals(new BigDecimal("0.00"), alloc.toGold(), "Step 1 (Gold): 0");
        assertEquals(new BigDecimal("0.00"), alloc.toArbTarget(), "Step 2 (Arbitrage target fill): 0");
        assertEquals(new BigDecimal("0.00"), alloc.toCore(), "Step 3 (Core deficit fill): 0");
        assertEquals(new BigDecimal("10000.00"), alloc.toArbTerminal(), "Step 4 (Arbitrage terminal sink): 10,000 (1% filling headroom to 15% cap)");
        assertEquals(new BigDecimal("30000.00"), alloc.toCashOverflow(), "Step 5 (Cash overflow): remaining 30,000 (3%) overflow to UNALLOCATED_CASH");

        BigDecimal arbFinal = arbValue.add(alloc.toArbTarget()).add(alloc.toArbTerminal());
        assertEquals(new BigDecimal("150000.00"), arbFinal, "Arbitrage final value hits exactly 150,000 (15% hard ceiling cap)");
    }

    @Test
    @DisplayName("Fixture 5a: Core aggregate band fires independently when Core is 70% total (70% > 65% max)")
    void testCoreAggregateBandFiresIndependently() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        // Total Corpus = 1,000,000. Core = 700,000 (70% total > 65% aggregate max)
        // Core internal split: LargeMid = 420,000 (60%), PPFC = 280,000 (40%) -> Ratio 0.60 (within 0.45-0.75)
        Lot lmLot = new Lot("lot-lm", "INF109KC12U0", "ICICI LargeMidcap 250", today.minusDays(400), new BigDecimal("4200"), new BigDecimal("4200"), new BigDecimal("100.00"), new BigDecimal("420000.00"), false, null);
        Lot ppfcLot = new Lot("lot-ppfc", "INF879O01027", "Parag Parikh Flexi Cap", today.minusDays(400), new BigDecimal("2800"), new BigDecimal("2800"), new BigDecimal("100.00"), new BigDecimal("280000.00"), false, null);
        Lot valLot = new Lot("lot-val", "INF109KC13X2", "ICICI Value 30", today.minusDays(400), new BigDecimal("3000"), new BigDecimal("3000"), new BigDecimal("100.00"), new BigDecimal("300000.00"), false, null);

        List<Lot> openLots = List.of(lmLot, ppfcLot, valLot);
        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("100.00"),
            "INF879O01027", new BigDecimal("100.00"),
            "INF109KC13X2", new BigDecimal("100.00")
        );

        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            openLots, navMap, new BigDecimal("1000.00"), new BigDecimal("1000.00"), null, null, today
        );

        assertNotNull(res);
        assertEquals("DRIFT", res.triggerType());
        assertTrue(res.driftedBuckets().contains("CORE_AGGREGATE_BREACH"), "CORE_AGGREGATE_BREACH must fire when Core aggregate is 70% (>65%)");
        assertFalse(res.driftedBuckets().contains("CORE_INTERNAL_CIRCUIT_BREAKER"), "CORE_INTERNAL_CIRCUIT_BREAKER must NOT fire when internal ratio is 60:40");
    }

    @Test
    @DisplayName("Fixture 5b: Core internal circuit breaker ratio fires when LargeMid ratio is 80% (0.80 > 0.75) even if Core aggregate total is exactly 50%")
    void testCoreCircuitBreakerFiresIndependently() {
        LocalDate today = LocalDate.of(2026, 8, 26);
        // Total Corpus = 1,000,000. Core = 500,000 (50% total, exactly on target)
        // Core internal split: LargeMid = 400,000 (80%), PPFC = 100,000 (20%) -> Ratio 0.80 > 0.75 max ratio!
        Lot lmLot = new Lot("lot-lm", "INF109KC12U0", "ICICI LargeMidcap 250", today.minusDays(400), new BigDecimal("4000"), new BigDecimal("4000"), new BigDecimal("100.00"), new BigDecimal("400000.00"), false, null);
        Lot ppfcLot = new Lot("lot-ppfc", "INF879O01027", "Parag Parikh Flexi Cap", today.minusDays(400), new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100.00"), new BigDecimal("100000.00"), false, null);

        // Satellites filling remaining 50%
        Lot valLot = new Lot("lot-val", "INF109KC13X2", "ICICI Value 30", today.minusDays(400), new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100.00"), new BigDecimal("100000.00"), false, null);
        Lot momLot = new Lot("lot-mom", "INF754K01TN5", "Edelweiss Momentum", today.minusDays(400), new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100.00"), new BigDecimal("100000.00"), false, null);
        Lot scLot = new Lot("lot-sc", "INF204K01K15", "Nippon Small Cap", today.minusDays(400), new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100.00"), new BigDecimal("100000.00"), false, null);
        Lot goldLot = new Lot("lot-gold", "INF247L01BM8", "Motilal Gold FoF", today.minusDays(400), new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100.00"), new BigDecimal("100000.00"), false, null);
        Lot arbLot = new Lot("lot-arb", "INF205K01KR8", "Invesco Arbitrage", today.minusDays(400), new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100.00"), new BigDecimal("100000.00"), false, null);

        List<Lot> openLots = List.of(lmLot, ppfcLot, valLot, momLot, scLot, goldLot, arbLot);
        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("100.00"),
            "INF879O01027", new BigDecimal("100.00"),
            "INF109KC13X2", new BigDecimal("100.00"),
            "INF754K01TN5", new BigDecimal("100.00"),
            "INF204K01K15", new BigDecimal("100.00"),
            "INF247L01BM8", new BigDecimal("100.00"),
            "INF205K01KR8", new BigDecimal("100.00")
        );

        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            openLots, navMap, new BigDecimal("1000.00"), new BigDecimal("1000.00"), null, null, today
        );

        assertNotNull(res);
        assertEquals("DRIFT", res.triggerType());
        assertTrue(res.driftedBuckets().contains("CORE_INTERNAL_CIRCUIT_BREAKER"), "CORE_INTERNAL_CIRCUIT_BREAKER must fire when LargeMid ratio is 0.80 (>0.75)");
        assertFalse(res.driftedBuckets().contains("CORE_AGGREGATE_BREACH"), "CORE_AGGREGATE_BREACH must NOT fire when Core aggregate total is exactly 50%");
    }

    @Test
    @DisplayName("Fixture 6: Cooldown source-keyed — plan containing BUY actions only does not trigger/reset sell cooldown; plan with SELL action does")
    void testCooldownSourceKeyed() {
        LocalDate today = LocalDate.of(2026, 8, 26);

        // Gold Floor Backstop trigger (BUY-only action)
        repository.recordExecution("plan-buy-1", "GOLD_FLOOR_BACKSTOP", "GOLD_FLOOR_BACKSTOP_TRIGGERED", today.atStartOfDay(), false, true, "{}");

        // Verify sell cooldown remains INACTIVE (0 sell executions recorded)
        Optional<java.time.LocalDateTime> lastSellOpt = repository.getLastSellSideFiringDate();
        assertTrue(lastSellOpt.isEmpty(), "BUY-only execution must NOT set last sell side firing date");

        // Sell execution (DRIFT trigger with SELL action)
        repository.recordExecution("plan-sell-1", "DRIFT", "DRIFT_THRESHOLD_EXCEEDED", today.atStartOfDay(), true, true, "{}");

        // Verify sell cooldown is now ACTIVE
        Optional<java.time.LocalDateTime> lastSellAfter = repository.getLastSellSideFiringDate();
        assertTrue(lastSellAfter.isPresent(), "SELL action execution MUST record sell side firing date");
    }

    @Test
    @DisplayName("Fixture 7: Cooldown cross-cycle — floor breach sell followed 10 days later by Small Cap ceiling breach is suppressed by 30-day sell cooldown")
    void testCooldownCrossCycleSuppression() {
        LocalDate day0 = LocalDate.of(2026, 8, 1);
        LocalDate day10 = LocalDate.of(2026, 8, 11);

        // Day 0: Arbitrage sell execution to fund floor breach
        repository.recordExecution("plan-floor-sell", "DRIFT", "ARBITRAGE_FLOOR_BREACH", day0.atStartOfDay(), true, false, "{}");

        // Day 10 (10 days later): Small Cap ceiling breach (13%)
        // Total Corpus = 1,000,000. Small Cap = 130,000 (13% > 11.5% ceiling), Gold = 100,000 (10%), Core = 770,000 (77%)
        Lot scLot = new Lot("lot-sc", "INF204K01K15", "Nippon Small Cap", day10.minusDays(400), new BigDecimal("1300"), new BigDecimal("1300"), new BigDecimal("100.00"), new BigDecimal("130000.00"), false, null);
        Lot goldLot = new Lot("lot-gold", "INF247L01BM8", "Motilal Gold FoF", day10.minusDays(400), new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100.00"), new BigDecimal("100000.00"), false, null);
        Lot coreLot = new Lot("lot-core", "INF109KC12U0", "ICICI LargeMidcap", day10.minusDays(400), new BigDecimal("7700"), new BigDecimal("7700"), new BigDecimal("100.00"), new BigDecimal("770000.00"), false, null);

        List<Lot> openLots = List.of(scLot, goldLot, coreLot);
        Map<String, BigDecimal> navMap = Map.of(
            "INF204K01K15", new BigDecimal("100.00"),
            "INF247L01BM8", new BigDecimal("100.00"),
            "INF109KC12U0", new BigDecimal("100.00")
        );

        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            openLots, navMap, new BigDecimal("1000.00"), new BigDecimal("1000.00"), null, null, day10
        );

        assertNotNull(res);
        assertTrue(res.sellCooldownActive(), "Sell cooldown must be active on Day 10 (10 days since Day 0 sell)");
        assertEquals("DRIFT_BLOCKED_BY_COOLDOWN", res.reasonCode(), "Small Cap ceiling breach must be suppressed with DRIFT_BLOCKED_BY_COOLDOWN on Day 10");
        assertEquals("NONE", res.triggerType(), "Trigger type must be NONE when sell rebalance is blocked by active cooldown");
    }
}
