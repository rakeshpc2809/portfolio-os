package com.portfolioos.core.valuation;

import com.portfolioos.core.config.BucketTargetsConfig;
import com.portfolioos.core.config.BucketTargetsConfig.BucketType;
import com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.persistence.MarketDataRepository;
import com.portfolioos.core.persistence.TriggerHistoryRepository;
import com.portfolioos.core.rules.BucketConfigLoader;
import com.portfolioos.core.rules.TaxRulesLoader;
import com.portfolioos.core.service.RebalancePlanEngine;
import com.portfolioos.core.service.RebalanceTriggerEvaluator;
import com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerResolution;
import com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RebalanceV30Test {

    private BucketTargetsConfig config;
    private TriggerHistoryRepository repository;
    private RebalanceTriggerEvaluator evaluator;

    @BeforeEach
    void setUp() {
        config = BucketConfigLoader.loadV24Config();
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
    @DisplayName("1. Gold underweight emits CASH_INFLOW_NEEDED with zero sells and directs bank cash to deficit")
    void testGoldUnderweightEmitsCashInflowNeededWithZeroSells() {
        LocalDate today = LocalDate.of(2026, 11, 20);
        BigDecimal nav = new BigDecimal("100.00");
        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", nav,
            "INF205K01QN0", nav,
            "INF109KC13X2", nav,
            "INF247L01BM8", nav
        );

        // Target: Core 50% (45-55), Satellite 30% (25-35), Liquid 10% (7.5-12.5), Gold 10% (7.5-15)
        // Set Gold to 6.0% (< 7.5% lower band), others in band:
        // Total = 1,000,000: Core 520k (52%), Satellite 310k (31%), Liquid 110k (11%), Gold 60k (6.0%)
        List<Lot> openLots = List.of(
            new Lot("core1", "INF109KC12U0", "ICICI LargeMidcap 250", today.minusMonths(14), new BigDecimal("5200"), new BigDecimal("5200"), nav, new BigDecimal("400000.00"), false, null),
            new Lot("sat1", "INF109KC13X2", "ICICI Value Discovery", today.minusMonths(14), new BigDecimal("3100"), new BigDecimal("3100"), nav, new BigDecimal("250000.00"), false, null),
            new Lot("liq1", "INF205K01QN0", "Invesco India Arbitrage Fund", today.minusMonths(2), new BigDecimal("1100"), new BigDecimal("1100"), nav, new BigDecimal("105000.00"), false, null),
            new Lot("gold1", "INF247L01BM8", "Motilal Oswal Gold and Silver ETFs FoF", today.minusMonths(14), new BigDecimal("600"), new BigDecimal("600"), nav, new BigDecimal("50000.00"), false, null)
        );

        BigDecimal benchmarkPeak = new BigDecimal("25000.00");
        BigDecimal benchmarkCurrent = new BigDecimal("25000.00"); // 0% drawdown

        TriggerResolution res = evaluator.getCurrentStatus(
            openLots, navMap, benchmarkCurrent, benchmarkPeak, null, null, today
        );

        assertEquals("CASH_INFLOW_NEEDED", res.triggerType(), "Underweight bucket breach must fire CASH_INFLOW_NEEDED");
        assertFalse(res.hasSellSide(), "CASH_INFLOW_NEEDED must not trigger automated sells");
        assertTrue(res.reasonLabel().contains("GOLD_SILVER"), "Reason label must identify underweight bucket");

        RebalancePlanDto plan = RebalancePlanEngine.buildPlan(
            openLots, List.of(), navMap, today, benchmarkCurrent, benchmarkPeak, null, "2026-27", "CASH_INFLOW_NEEDED", null, evaluator
        );

        assertNotNull(plan);
        assertEquals(0, BigDecimal.ZERO.compareTo(plan.sellSide().totalRequired()), "Sell side total required must be exactly 0");
        assertEquals(0, BigDecimal.ZERO.compareTo(plan.buySide().totalToInvest()), "Buy side total to invest must be exactly 0 (no phantom buys)");
        assertTrue(plan.reasoningNarrative().headline().contains("Cash Inflow Needed"), "Headline must announce Cash Inflow Needed");
        assertTrue(plan.reasoningNarrative().paragraphs().stream().anyMatch(p -> p.contains("0 sells")), "Narrative must explicitly confirm 0 sells");
        assertTrue(plan.reasoningNarrative().paragraphs().stream().anyMatch(p -> p.contains("GOLD_SILVER")), "Narrative must state exact deficit bucket");
    }

    @Test
    @DisplayName("2. Drawdown rung is cycle-latched and does not refire after 31 days until deeper rung or recovery")
    void testDrawdownRungDoesNotRefireAfter31Days() {
        LocalDate day0 = LocalDate.of(2026, 11, 20);
        BigDecimal nav = new BigDecimal("100.00");
        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", nav,
            "INF205K01QN0", nav
        );

        List<Lot> openLots = List.of(
            new Lot("c1", "INF109KC12U0", "ICICI LargeMidcap 250", day0.minusMonths(14), new BigDecimal("8500"), new BigDecimal("8500"), nav, new BigDecimal("700000.00"), false, null),
            new Lot("l1", "INF205K01QN0", "Invesco India Arbitrage Fund", day0.minusMonths(2), new BigDecimal("1500"), new BigDecimal("1500"), nav, new BigDecimal("140000.00"), false, null)
        );

        BigDecimal peak = new BigDecimal("30000.00");
        // Rung 1: -12.0% drawdown (crosses -10% threshold)
        BigDecimal currentTri = new BigDecimal("26400.00");

        TriggerResolution res1 = evaluator.evaluateAndRecord(
            "plan-1", openLots, navMap, currentTri, peak, null, null, day0
        );
        assertEquals("DRAWDOWN", res1.triggerType(), "First encounter with -12% drawdown must fire DRAWDOWN");
        assertEquals("TIER_10", res1.drawdownContext().armedTier());

        // Simulate execution confirmation at Rung 1 (Tier 10)
        RebalancePlanDto plan1 = RebalancePlanEngine.buildPlan(
            openLots, List.of(), navMap, day0, currentTri, peak, null, "2026-27", "DRAWDOWN", null, evaluator
        );
        RebalancePlanEngine.confirmPlanExecution(plan1, repository);
        assertEquals(10, repository.getActiveDrawdownMaxFiredTier(), "Confirmed execution must latch Tier 10 in DB");

        // 31 Days Later: Market still at -12% drawdown (same tier 10)
        LocalDate day31 = day0.plusDays(31);
        TriggerResolution res31 = evaluator.getCurrentStatus(
            openLots, navMap, currentTri, peak, null, null, day31
        );
        assertEquals("NONE", res31.triggerType(), "Drawdown rung must NOT refire after 30 days under cycle latching");
        assertEquals("DRAWDOWN_RUNG_ALREADY_DEPLOYED", res31.reasonCode());

        // Deeper plunge to -22% drawdown (crosses -20% Rung 2 threshold)
        BigDecimal deeperTri = new BigDecimal("23400.00");
        TriggerResolution resDeeper = evaluator.getCurrentStatus(
            openLots, navMap, deeperTri, peak, null, null, day31.plusDays(5)
        );
        assertEquals("DRAWDOWN", resDeeper.triggerType(), "Deeper drawdown crossing Rung 2 (-20%) must fire");
        assertEquals("TIER_20", resDeeper.drawdownContext().armedTier());

        // Confirm Tier 20 execution
        RebalancePlanDto plan2 = RebalancePlanEngine.buildPlan(
            openLots, List.of(), navMap, day31.plusDays(5), deeperTri, peak, null, "2026-27", "DRAWDOWN", null, evaluator
        );
        RebalancePlanEngine.confirmPlanExecution(plan2, repository);
        assertEquals(20, repository.getActiveDrawdownMaxFiredTier(), "Max fired rung must advance to 20");

        // Market recovers: drawdown < 5.0% (e.g. -3.0%, level 29,100 vs peak 30,000)
        // MarketDataRepository clears baseline and resets cycle
        repository.clearActiveDrawdownArbitrageBaseline();
        assertEquals(0, repository.getActiveDrawdownMaxFiredTier(), "Recovery clearing baseline must reset max_fired_tier to 0");
    }

    @Test
    @DisplayName("3. Drawdown allows Arbitrage STCG but respects 15-day exit load; never sells positive-gain non-liquid STCG")
    void testDrawdownAllowsArbitrageStcgButRespects15DayExitLoad() {
        LocalDate today = LocalDate.of(2026, 11, 20);
        BigDecimal nav = new BigDecimal("100.00");
        Map<String, BigDecimal> navMap = Map.of(
            "INF205K01QN0", nav,
            "INF109KC12U0", nav
        );

        // Arbitrage lots:
        // Lot A: 10 days old (< 15 days exit load)
        Lot arbUnderExitLoad = new Lot("arb-young", "INF205K01QN0", "Invesco India Arbitrage Fund", today.minusDays(10), new BigDecimal("100"), new BigDecimal("100"), nav, new BigDecimal("9800.00"), false, null);
        // Lot B: 25 days old (>= 15 days exit load, STCG holdingDays < 365, positive gain)
        Lot arbFreeOfLoad = new Lot("arb-mature", "INF205K01QN0", "Invesco India Arbitrage Fund", today.minusDays(25), new BigDecimal("100"), new BigDecimal("100"), nav, new BigDecimal("9800.00"), false, null);

        // Non-urgent Rung 1 drawdown (urgent = false):
        // 1. Lot under 15d exit load must NOT be sold
        RebalanceWaterfallEngine.WaterfallResult resA = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.LIQUID_BUFFER, new BigDecimal("10000.00"), List.of(arbUnderExitLoad), navMap,
            BigDecimal.ZERO, false, today, "2026-27", false, true, true
        );
        assertEquals(0, BigDecimal.ZERO.compareTo(resA.satisfiedAmount()), "Arbitrage lot < 15 days must be protected from exit load during Rung 1/2");

        // 2. Lot free of exit load with STCG IS sold during drawdown
        RebalanceWaterfallEngine.WaterfallResult resB = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.LIQUID_BUFFER, new BigDecimal("10000.00"), List.of(arbFreeOfLoad), navMap,
            BigDecimal.ZERO, false, today, "2026-27", false, true, true
        );
        assertEquals(0, new BigDecimal("10000.00").compareTo(resB.satisfiedAmount()), "Arbitrage STCG lot >= 15 days must be sold during drawdown");
        assertEquals("SHORT_TERM", resB.steps().get(0).taxTerm());

        // 3. Urgent Rung 3 (-30%, urgent = true) overrides exit load
        RebalanceWaterfallEngine.WaterfallResult resUrgent = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.LIQUID_BUFFER, new BigDecimal("10000.00"), List.of(arbUnderExitLoad), navMap,
            BigDecimal.ZERO, true, today, "2026-27", false, true, true
        );
        assertEquals(0, new BigDecimal("10000.00").compareTo(resUrgent.satisfiedAmount()), "Rung 3 (-30% urgent) must override exit load");

        // 4. Non-liquid equity positive-gain STCG lot is NEVER sold in automated trims
        Lot equityStcgGain = new Lot("eq-stcg", "INF109KC12U0", "ICICI LargeMidcap 250", today.minusDays(90), new BigDecimal("100"), new BigDecimal("100"), nav, new BigDecimal("8000.00"), false, null);
        RebalanceWaterfallEngine.WaterfallResult resEq = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("10000.00"), List.of(equityStcgGain), navMap,
            new BigDecimal("125000.00"), false, today, "2026-27", false, true, false
        );
        assertEquals(0, BigDecimal.ZERO.compareTo(resEq.satisfiedAmount()), "Positive-gain non-liquid STCG lots must NEVER be sold in automated trims");
    }

    @Test
    @DisplayName("4. Tax-aware trim sizing: 100% zero-tax trim up to exemption headroom, 50% of remaining excess if taxable")
    void testTaxAwareTrimSizing100PctZeroTaxAnd50PctTaxable() {
        LocalDate today = LocalDate.of(2026, 11, 20);
        BigDecimal nav = new BigDecimal("100.00");
        Map<String, BigDecimal> navMap = Map.of("INF109KC12U0", nav);

        // Lot 1: ₹20,000 value, cost ₹10,000 (costPerUnit = 50.00, nav = 100.00) -> LTCG = ₹10,000 (within ₹1.25L exemption -> ₹0 tax)
        Lot zeroTaxLot = new Lot("l1", "INF109KC12U0", "ICICI LargeMidcap 250", today.minusDays(400), new BigDecimal("200"), new BigDecimal("200"), new BigDecimal("50.00"), new BigDecimal("10000.00"), false, null);
        // Lot 2: ₹100,000 value, cost ₹50,000 (costPerUnit = 50.00, nav = 100.00) -> LTCG = ₹50,000 (taxable once exemption consumed)
        Lot taxableLot = new Lot("l2", "INF109KC12U0", "ICICI LargeMidcap 250", today.minusDays(400), new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("50.00"), new BigDecimal("50000.00"), false, null);

        BigDecimal excessVal = new BigDecimal("50000.00");
        BigDecimal initialHeadroom = new BigDecimal("10000.00"); // Exactly covers Lot 1 gain

        // Step A: allowTaxable = false -> trims Lot 1 completely (100% zero-tax)
        RebalanceWaterfallEngine.WaterfallResult stepA = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE, excessVal, List.of(zeroTaxLot, taxableLot), navMap,
            initialHeadroom, false, today, "2026-27", false, false, false
        );
        assertEquals(0, new BigDecimal("20000.00").compareTo(stepA.satisfiedAmount()), "Step A must satisfy exactly ₹20,000 at zero tax");
        assertEquals(0, BigDecimal.ZERO.compareTo(stepA.totalTaxDrag()), "Step A must produce ₹0 tax drag");

        // Step B: remaining excess = ₹30,000. Taxable trim fraction = 0.50 -> targets ₹15,000
        BigDecimal remExcess = excessVal.subtract(stepA.satisfiedAmount());
        assertEquals(0, new BigDecimal("30000.00").compareTo(remExcess));
        BigDecimal taxableTarget = remExcess.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, new BigDecimal("15000.00").compareTo(taxableTarget));

        RebalanceWaterfallEngine.WaterfallResult stepB = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE, taxableTarget, List.of(taxableLot), navMap,
            BigDecimal.ZERO, false, today, "2026-27", false, true, false
        );
        assertEquals(0, new BigDecimal("15000.00").compareTo(stepB.satisfiedAmount()), "Step B must satisfy 50% of remaining taxable excess (₹15,000)");

        BigDecimal totalTrimmed = stepA.satisfiedAmount().add(stepB.satisfiedAmount());
        assertEquals(0, new BigDecimal("35000.00").compareTo(totalTrimmed), "Total trimmed must equal ₹35,000 (100% of zero-tax + 50% of taxable excess)");
    }
}
