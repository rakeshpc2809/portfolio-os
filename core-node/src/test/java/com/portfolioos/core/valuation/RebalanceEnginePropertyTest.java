package com.portfolioos.core.valuation;

import com.portfolioos.core.config.BucketTargetsConfig;
import com.portfolioos.core.config.BucketTargetsConfig.BucketType;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.rules.BucketConfigLoader;
import com.portfolioos.core.service.RebalancePlanEngine;
import com.portfolioos.core.service.RebalanceTriggerEvaluator;
import com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType;
import com.portfolioos.core.valuation.RebalanceInvariantVerifier.*;
import com.portfolioos.core.valuation.RebalanceWaterfallEngine.RoutingResult;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Provide;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RebalanceEnginePropertyTest {

    private final BucketTargetsConfig config = BucketConfigLoader.loadV24Config();
    private final RebalanceInvariantVerifier verifier = new RebalanceInvariantVerifier();

    // =========================================================================
    // PROPERTY 1: 1,000-Portfolio Fuzzing of DRIFT & SCHEDULED Routing
    // Verifies: Conservation of Money, 15% Liquid Cap, ₹1,000 Dust Filter,
    //           and Zero Satellite Leakage when Core is trimmed.
    // =========================================================================
    @Property(tries = 1000, shrinking = ShrinkingMode.FULL)
    void fuzzDriftAndLegacyRoutingPassesAllInvariants(
            @ForAll("fuzzPortfolioStates") FuzzPortfolioState state) {

        boolean coreTrimmed = state.coreTrimPoolInr().compareTo(BigDecimal.ZERO) > 0;
        Map<BucketType, BigDecimal> targetsPct = getRegimeTargets(state.drawdownRung());

        RoutingResult result = RebalanceWaterfallEngine.routeProceedsV24(
            config,
            TriggerType.DRIFT,
            state.coreTrimPoolInr(),
            state.generalPoolInr(),
            state.effectiveActiveCorpusInr(),
            state.postSellValuesInr(),
            targetsPct
        );

        // Conservation of money: sum of buys + cashOverflow == total sell pool
        BigDecimal totalBuys = result.bucketBuysInr().values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPool = state.coreTrimPoolInr().add(state.generalPoolInr());
        assertEquals(
            0,
            totalPool.compareTo(totalBuys.add(result.cashOverflowInr())),
            "Conservation of money failed: pool=" + totalPool + ", buys=" + totalBuys + ", overflow=" + result.cashOverflowInr()
        );

        // Core trim pool must NEVER route to EQUITY_SATELLITE
        if (coreTrimmed && state.generalPoolInr().compareTo(BigDecimal.ZERO) == 0) {
            assertEquals(
                0,
                result.bucketBuysInr().get(BucketType.EQUITY_SATELLITE).compareTo(BigDecimal.ZERO),
                "Core trim pool must NEVER route a single paisa to EQUITY_SATELLITE"
            );
        }

        // Build post-trade values & synthetic verifiable plan
        VerifiablePlan plan = buildVerifiablePlan(
            TriggerType.DRIFT,
            false,
            coreTrimmed,
            state,
            result
        );

        verifier.assertAllInvariants(plan, config);
    }

    // =========================================================================
    // PROPERTY 2: 1,000-Portfolio Fuzzing of Crisis DRAWDOWN Deployment
    // Verifies: Strict Priority into Core & Satellite, 0% to Gold, Invariants
    // =========================================================================
    @Property(tries = 1000, shrinking = ShrinkingMode.FULL)
    void fuzzDrawdownCrisisRoutingPassesAllInvariants(
            @ForAll("fuzzPortfolioStates") FuzzPortfolioState state) {

        Map<BucketType, BigDecimal> targetsPct = getRegimeTargets(state.drawdownRung());
        boolean urgent = "TIER_15".equals(state.drawdownRung()) || "TIER_20".equals(state.drawdownRung());

        RoutingResult result = RebalanceWaterfallEngine.routeProceedsV24(
            config,
            TriggerType.DRAWDOWN,
            BigDecimal.ZERO,
            state.coreTrimPoolInr().add(state.generalPoolInr()),
            state.effectiveActiveCorpusInr(),
            state.postSellValuesInr(),
            targetsPct
        );

        assertEquals(
            0,
            result.bucketBuysInr().get(BucketType.GOLD_SILVER).compareTo(BigDecimal.ZERO),
            "Crisis DRAWDOWN deployment must route ₹0.00 to GOLD_SILVER"
        );

        VerifiablePlan plan = buildVerifiablePlan(
            TriggerType.DRAWDOWN,
            urgent,
            false,
            state,
            result
        );

        verifier.assertAllInvariants(plan, config);
    }

    // =========================================================================
    // PROPERTY 3: Extreme Overflow Stress Test (Sell Pool > All Deficits)
    // Proves Weak Spot #1 (Post-Allocation Cap Override) is permanently dead.
    // =========================================================================
    @Property(tries = 500, shrinking = ShrinkingMode.FULL)
    void fuzzExtremeSellPoolNeverBreaches15PercentLiquidCeiling(
            @ForAll("corpusArbitrary") BigDecimal corpusInr,
            @ForAll("excessPoolFraction") BigDecimal poolFraction) {

        // Start with Liquid already at 11% of corpus (only 4% headroom to 15% cap)
        Map<BucketType, BigDecimal> postSellValues = new EnumMap<>(BucketType.class);
        postSellValues.put(BucketType.EQUITY_CORE, scale(corpusInr, "0.48"));
        postSellValues.put(BucketType.EQUITY_SATELLITE, scale(corpusInr, "0.29"));
        postSellValues.put(BucketType.GOLD_SILVER, scale(corpusInr, "0.09"));
        postSellValues.put(BucketType.LIQUID_BUFFER, scale(corpusInr, "0.11"));

        BigDecimal massiveCoreTrimPool = corpusInr.multiply(poolFraction).setScale(2, RoundingMode.HALF_UP);

        RoutingResult result = RebalanceWaterfallEngine.routeProceedsV24(
            config,
            TriggerType.DRIFT,
            massiveCoreTrimPool,
            BigDecimal.ZERO,
            corpusInr,
            postSellValues,
            getRegimeTargets("NONE")
        );

        BigDecimal postTradeLiquidInr = postSellValues.get(BucketType.LIQUID_BUFFER)
            .add(result.bucketBuysInr().get(BucketType.LIQUID_BUFFER));
        BigDecimal maxAllowedLiquidInr = scale(corpusInr, "0.15");

        assertTrue(
            postTradeLiquidInr.compareTo(maxAllowedLiquidInr) <= 0,
            "Post-trade LIQUID_BUFFER (" + postTradeLiquidInr + ") exceeded 15% ceiling (" + maxAllowedLiquidInr + ")"
        );

        // Verify conservation of money holds via cashOverflow
        BigDecimal totalBuys = result.bucketBuysInr().values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(
            0,
            massiveCoreTrimPool.compareTo(totalBuys.add(result.cashOverflowInr())),
            "Exact paisa conservation failed under extreme overflow"
        );
    }

    // =========================================================================
    // DETERMINISTIC MUTATION TESTS: Proving the Firewall Catches Violations
    // =========================================================================

    @Test
    @DisplayName("Mutation Gate: Firewall trips and fails closed on ₹0.02 accounting leak")
    void firewallRejectsTwoPaisaAccountingLeak() {
        FuzzPortfolioState state = sampleValidState();
        RoutingResult result = RebalanceWaterfallEngine.routeProceedsV24(
            config, TriggerType.DRIFT, state.coreTrimPoolInr(), BigDecimal.ZERO,
            state.effectiveActiveCorpusInr(), state.postSellValuesInr(), getRegimeTargets("NONE")
        );

        // Tamper with cashOverflow by +₹0.02
        RoutingResult corrupted = new RoutingResult(
            result.bucketBuysInr(),
            result.cashOverflowInr().add(new BigDecimal("0.02"))
        );

        VerifiablePlan badPlan = buildVerifiablePlan(TriggerType.DRIFT, false, true, state, corrupted);
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> verifier.assertAllInvariants(badPlan, config)
        );
        assertTrue(ex.getMessage().contains("INVARIANT_1_VIOLATION"));
    }

    @Test
    @DisplayName("Mutation Gate: Firewall trips and fails closed on out-of-order RTA FIFO lot")
    void firewallRejectsOutOfOrderRtaFifoLotSelection() {
        LotOrder cherryPickedNewerLot = new LotOrder(
            "lot-cherry", "FOLIO_101", "INF109KC12U0",
            LocalDate.of(2025, 6, 15), // Sold June 2025 lot
            new BigDecimal("50000.00"),
            BigDecimal.ZERO,
            new BigDecimal("5000.00"),
            false
        );

        // Older lot from Jan 2024 still sitting unsold in the same Folio + ISIN!
        Map<String, List<LocalDate>> remainingUnsold = Map.of(
            "FOLIO_101::INF109KC12U0", List.of(LocalDate.of(2024, 1, 10))
        );

        VerifiablePlan badFifoPlan = new VerifiablePlan(
            TriggerType.DRIFT,
            false,
            true,
            new BigDecimal("1000000.00"),
            List.of(cherryPickedNewerLot),
            remainingUnsold,
            List.of(new BuyOrder(BucketType.GOLD_SILVER, "INF247L01BM8", new BigDecimal("50000.00"))),
            Map.of(BucketType.LIQUID_BUFFER, new BigDecimal("100000.00")),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("125000.00")
        );

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> verifier.assertAllInvariants(badFifoPlan, config)
        );
        assertTrue(ex.getMessage().contains("INVARIANT_2_VIOLATION"));
    }

    @Test
    @DisplayName("Mutation Gate: Firewall trips and fails closed on exit-load lot in non-urgent rebalance")
    void firewallRejectsExitLoadLotInNonUrgentRebalance() {
        LotOrder ppfasExitLoadLot = new LotOrder(
            "lot-ppfas", "FOLIO_PPFAS", "INF879O01027",
            LocalDate.of(2025, 3, 1), // 19 months old: LTCG, but inside PPFAS 730-day exit load!
            new BigDecimal("40000.00"),
            BigDecimal.ZERO,
            new BigDecimal("4000.00"),
            true // withinExitLoadWindow == true
        );

        VerifiablePlan badExitLoadPlan = new VerifiablePlan(
            TriggerType.DRIFT,
            false, // non-urgent (< 15% drawdown)
            true,
            new BigDecimal("1000000.00"),
            List.of(ppfasExitLoadLot),
            Map.of(),
            List.of(new BuyOrder(BucketType.GOLD_SILVER, "INF247L01BM8", new BigDecimal("40000.00"))),
            Map.of(BucketType.LIQUID_BUFFER, new BigDecimal("100000.00")),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("125000.00")
        );

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> verifier.assertAllInvariants(badExitLoadPlan, config)
        );
        assertTrue(ex.getMessage().contains("INVARIANT_5_VIOLATION"));
    }

    @Test
    @DisplayName("Mutation Gate: Firewall trips when Core trim proceeds leak into Satellite")
    void firewallRejectsCoreProceedsLeakedToSatellite() {
        VerifiablePlan badLeakedPlan = new VerifiablePlan(
            TriggerType.DRIFT,
            false,
            true, // coreTrimmed == true
            new BigDecimal("1000000.00"),
            List.of(new LotOrder("l1", "FOLIO_1", "INF109KC12U0", LocalDate.of(2024, 1, 1),
                new BigDecimal("50000.00"), BigDecimal.ZERO, BigDecimal.ZERO, false)),
            Map.of(),
            List.of(new BuyOrder(BucketType.EQUITY_SATELLITE, "INF109KC13X2", new BigDecimal("50000.00"))),
            Map.of(BucketType.LIQUID_BUFFER, new BigDecimal("100000.00")),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("125000.00")
        );

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> verifier.assertAllInvariants(badLeakedPlan, config)
        );
        assertTrue(ex.getMessage().contains("INVARIANT_4_VIOLATION"));
    }

    @Test
    @DisplayName("Mutation Gate: Firewall trips when LIQUID_BUFFER breaches 15% hard cap")
    void firewallRejectsLiquidHardCapBreach() {
        VerifiablePlan badCapPlan = new VerifiablePlan(
            TriggerType.DRIFT,
            false,
            false,
            new BigDecimal("1000000.00"), // ₹10,00,000 corpus
            List.of(new LotOrder("l1", "FOLIO_1", "INF109KC13X2", LocalDate.of(2024, 1, 1),
                new BigDecimal("50000.00"), BigDecimal.ZERO, BigDecimal.ZERO, false)),
            Map.of(),
            List.of(new BuyOrder(BucketType.LIQUID_BUFFER, "INF205K01KR8", new BigDecimal("50000.00"))),
            Map.of(BucketType.LIQUID_BUFFER, new BigDecimal("160000.00")), // ₹1,60,000 = 16% > 15% hard cap!
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("125000.00")
        );

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> verifier.assertAllInvariants(badCapPlan, config)
        );
        assertTrue(ex.getMessage().contains("INVARIANT_3_VIOLATION"));
    }

    @Test
    @DisplayName("End-to-End Test: buildPreviewPlan fails closed (throws IllegalStateException) on invariant breach")
    void previewPlanFailsClosedWhenInvariantViolated() {
        // Construct open lots with negative or corrupted state that would violate invariants
        LocalDate today = LocalDate.of(2026, 9, 15);
        BigDecimal nav = new BigDecimal("100.00");

        // Single lot with zero/non-positive units
        Lot badLot = new Lot("bad-1", "INF109KC12U0", "ICICI LargeMidcap", today, BigDecimal.ZERO, BigDecimal.ZERO, nav, BigDecimal.ZERO, false, null);

        // When corpus is zero, fail-closed invariant V4 trips immediately
        assertThrows(
            IllegalStateException.class,
            () -> RebalancePlanEngine.buildPreviewPlan(
                List.of(badLot), List.of(), Map.of("INF109KC12U0", nav), today,
                new BigDecimal("25000.00"), new BigDecimal("25000.00"), null, "2026-27", "DRIFT", null
            )
        );
    }

    // =========================================================================
    // JQWIK ARBITRARY GENERATORS
    // =========================================================================

    record FuzzPortfolioState(
        BigDecimal effectiveActiveCorpusInr,
        Map<BucketType, BigDecimal> postSellValuesInr,
        BigDecimal coreTrimPoolInr,
        BigDecimal generalPoolInr,
        String drawdownRung
    ) {}

    @Provide
    Arbitrary<FuzzPortfolioState> fuzzPortfolioStates() {
        Arbitrary<BigDecimal> corpus = corpusArbitrary();
        Arbitrary<Integer> corePct = Arbitraries.integers().between(25, 65);
        Arbitrary<Integer> satPct = Arbitraries.integers().between(10, 40);
        Arbitrary<Integer> goldPct = Arbitraries.integers().between(0, 15);
        Arbitrary<Integer> liqPct = Arbitraries.integers().between(0, 14); // Up to 14% pre-buy
        Arbitrary<Boolean> isCoreTrimOnly = Arbitraries.of(true, false);
        Arbitrary<String> rung = Arbitraries.of("NONE", "TIER_10", "TIER_15", "TIER_20");

        return Combinators.combine(corpus, corePct, satPct, goldPct, liqPct, isCoreTrimOnly, rung)
            .as((c, core, sat, gold, liq, coreOnly, tier) -> {
                int totalPct = Math.min(95, core + sat + gold + liq);
                double scaleFactor = totalPct / (double) Math.max(1, core + sat + gold + liq);

                BigDecimal coreVal = c.multiply(BigDecimal.valueOf(core * scaleFactor / 100.0))
                    .setScale(2, RoundingMode.HALF_UP);
                BigDecimal satVal = c.multiply(BigDecimal.valueOf(sat * scaleFactor / 100.0))
                    .setScale(2, RoundingMode.HALF_UP);
                BigDecimal goldVal = c.multiply(BigDecimal.valueOf(gold * scaleFactor / 100.0))
                    .setScale(2, RoundingMode.HALF_UP);
                BigDecimal liqVal = c.multiply(BigDecimal.valueOf(Math.min(14.0, liq * scaleFactor) / 100.0))
                    .setScale(2, RoundingMode.HALF_UP);

                BigDecimal sumPostSell = coreVal.add(satVal).add(goldVal).add(liqVal);
                BigDecimal totalSellPool = c.subtract(sumPostSell).max(BigDecimal.ZERO);

                BigDecimal corePool = coreOnly ? totalSellPool : BigDecimal.ZERO;
                BigDecimal genPool = coreOnly ? BigDecimal.ZERO : totalSellPool;

                Map<BucketType, BigDecimal> postSell = new EnumMap<>(BucketType.class);
                postSell.put(BucketType.EQUITY_CORE, coreVal);
                postSell.put(BucketType.EQUITY_SATELLITE, satVal);
                postSell.put(BucketType.GOLD_SILVER, goldVal);
                postSell.put(BucketType.LIQUID_BUFFER, liqVal);

                return new FuzzPortfolioState(c, postSell, corePool, genPool, tier);
            });
    }

    @Provide
    Arbitrary<BigDecimal> corpusArbitrary() {
        return Arbitraries.longs().between(50_000_00L, 10_00_00_000_00L)
            .map(paise -> BigDecimal.valueOf(paise, 2));
    }

    @Provide
    Arbitrary<BigDecimal> excessPoolFraction() {
        return Arbitraries.integers().between(5, 35)
            .map(pct -> BigDecimal.valueOf(pct).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
    }

    // =========================================================================
    // TEST HELPERS
    // =========================================================================

    private VerifiablePlan buildVerifiablePlan(
            TriggerType triggerType,
            boolean urgent,
            boolean coreTrimmed,
            FuzzPortfolioState state,
            RoutingResult result) {

        BigDecimal totalSellPool = state.coreTrimPoolInr().add(state.generalPoolInr());
        String soldIsin = coreTrimmed ? "INF109KC12U0" : "INF209K01164";
        List<LotOrder> soldLots = totalSellPool.compareTo(BigDecimal.ZERO) > 0
            ? List.of(new LotOrder(
                "lot-1", "FOLIO_1", soldIsin, LocalDate.of(2022, 1, 10),
                totalSellPool, new BigDecimal("1500.00"), new BigDecimal("25000.00"), false))
            : List.of();

        List<BuyOrder> buyOrders = new ArrayList<>();
        Map<BucketType, BigDecimal> postTradeValues = new EnumMap<>(state.postSellValuesInr());

        for (var entry : result.bucketBuysInr().entrySet()) {
            BucketType b = entry.getKey();
            BigDecimal amt = entry.getValue();
            if (amt.compareTo(BigDecimal.ZERO) > 0) {
                buyOrders.add(new BuyOrder(b, "DUMMY_" + b.name(), amt));
            }
            postTradeValues.put(b, postTradeValues.getOrDefault(b, BigDecimal.ZERO).add(amt));
        }

        BigDecimal summaryTax = soldLots.isEmpty() ? BigDecimal.ZERO : new BigDecimal("1500.00");

        return new VerifiablePlan(
            triggerType,
            urgent,
            coreTrimmed,
            state.effectiveActiveCorpusInr(),
            soldLots,
            Map.of(),
            buyOrders,
            postTradeValues,
            result.cashOverflowInr(),
            summaryTax,
            new BigDecimal("125000.00")
        );
    }

    private Map<BucketType, BigDecimal> getRegimeTargets(String rung) {
        Map<BucketType, BigDecimal> t = new EnumMap<>(BucketType.class);
        if ("TIER_20".equals(rung)) {
            t.put(BucketType.EQUITY_CORE, new BigDecimal("60.00"));
            t.put(BucketType.EQUITY_SATELLITE, new BigDecimal("30.00"));
            t.put(BucketType.GOLD_SILVER, new BigDecimal("10.00"));
            t.put(BucketType.LIQUID_BUFFER, new BigDecimal("0.00"));
        } else if ("TIER_15".equals(rung)) {
            t.put(BucketType.EQUITY_CORE, new BigDecimal("55.00"));
            t.put(BucketType.EQUITY_SATELLITE, new BigDecimal("30.00"));
            t.put(BucketType.GOLD_SILVER, new BigDecimal("10.00"));
            t.put(BucketType.LIQUID_BUFFER, new BigDecimal("5.00"));
        } else if ("TIER_10".equals(rung)) {
            t.put(BucketType.EQUITY_CORE, new BigDecimal("52.50"));
            t.put(BucketType.EQUITY_SATELLITE, new BigDecimal("30.00"));
            t.put(BucketType.GOLD_SILVER, new BigDecimal("10.00"));
            t.put(BucketType.LIQUID_BUFFER, new BigDecimal("7.50"));
        } else {
            t.put(BucketType.EQUITY_CORE, new BigDecimal("50.00"));
            t.put(BucketType.EQUITY_SATELLITE, new BigDecimal("30.00"));
            t.put(BucketType.GOLD_SILVER, new BigDecimal("10.00"));
            t.put(BucketType.LIQUID_BUFFER, new BigDecimal("10.00"));
        }
        return t;
    }

    private FuzzPortfolioState sampleValidState() {
        BigDecimal corpus = new BigDecimal("1000000.00");
        Map<BucketType, BigDecimal> postSell = new EnumMap<>(BucketType.class);
        postSell.put(BucketType.EQUITY_CORE, new BigDecimal("450000.00"));
        postSell.put(BucketType.EQUITY_SATELLITE, new BigDecimal("250000.00"));
        postSell.put(BucketType.GOLD_SILVER, new BigDecimal("90000.00"));
        postSell.put(BucketType.LIQUID_BUFFER, new BigDecimal("90000.00"));
        return new FuzzPortfolioState(
            corpus,
            postSell,
            new BigDecimal("120000.00"),
            BigDecimal.ZERO,
            "NONE"
        );
    }

    private BigDecimal scale(BigDecimal amount, String factor) {
        return amount.multiply(new BigDecimal(factor)).setScale(2, RoundingMode.HALF_UP);
    }
}

