package com.portfolioos.core.valuation;

import com.portfolioos.core.config.BucketTargetsConfig;
import com.portfolioos.core.config.BucketTargetsConfig.BucketType;
import com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto;
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
import net.jqwik.api.statistics.Statistics;
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
    // PROPERTY 4: 200-Portfolio End-to-End Fuzzing of buildPreviewPlan
    // Verifies: Randomized multi-scheme portfolios passing buildPreviewPlan generate
    //           valid non-empty planDigest, enforce Conservation of Money, Min Order Floor,
    //           15% Liquid Cap, Exit-Load Rules, and R2 Core/Satellite prohibitions.
    // =========================================================================
    @Property(tries = 200, shrinking = ShrinkingMode.FULL)
    void fuzzEndToEndBuildPreviewPlanPassesAllInvariants(
            @ForAll("fuzzMultiSchemeOpenLots") List<Lot> lots,
            @ForAll("fuzzDrawdownRungs") String drawdownRung) {

        LocalDate today = LocalDate.of(2026, 10, 15);
        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("145.25"),
            "INF879O01027", new BigDecimal("78.40"),
            "INF109KC13X2", new BigDecimal("55.60"),
            "INF247L01916", new BigDecimal("32.10"),
            "INF247L01BM8", new BigDecimal("22.80"),
            "INF205K01KR8", new BigDecimal("105.15"),
            "INF209K01164", new BigDecimal("64.50")
        );

        BigDecimal liveCorpus = BigDecimal.ZERO;
        for (Lot lot : lots) {
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), new BigDecimal("100.00"));
            liveCorpus = liveCorpus.add(lot.remainingUnits().multiply(nav));
        }

        BigDecimal rollingHigh = liveCorpus;
        if ("TIER_10".equals(drawdownRung)) {
            rollingHigh = liveCorpus.divide(new BigDecimal("0.89"), 2, RoundingMode.HALF_UP);
        } else if ("TIER_15".equals(drawdownRung)) {
            rollingHigh = liveCorpus.divide(new BigDecimal("0.84"), 2, RoundingMode.HALF_UP);
        } else if ("TIER_20".equals(drawdownRung)) {
            rollingHigh = liveCorpus.divide(new BigDecimal("0.79"), 2, RoundingMode.HALF_UP);
        }

        RebalanceTriggerEvaluator isolatedEvaluator = new RebalanceTriggerEvaluator(null);
        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            lots,
            List.of(),
            navMap,
            today,
            liveCorpus,
            rollingHigh,
            null,
            "2026-27",
            "AUTO",
            null,
            false,
            isolatedEvaluator
        );

        assertNotNull(plan, "Preview plan must not be null");
        assertNotNull(plan.planDigest(), "Plan digest must not be null");
        assertFalse(plan.planDigest().isEmpty(), "Plan digest must not be empty");

        boolean hasSells = plan.sellSide() != null && plan.sellSide().waterfall() != null &&
            plan.sellSide().waterfall().stream().anyMatch(w -> w.sold() != null && w.sold().compareTo(BigDecimal.ZERO) > 0);
        boolean hasBuys = plan.buySide() != null && plan.buySide().buckets() != null &&
            plan.buySide().buckets().stream().anyMatch(b -> b.amountAllocated() != null && b.amountAllocated().compareTo(BigDecimal.ZERO) > 0);

        // Collect execution statistics via Jqwik
        Statistics.label("rebalance_trigger").collect(plan.trigger() != null ? plan.trigger().type() : "NULL");
        Statistics.label("has_sells").collect(hasSells);
        Statistics.label("has_buys").collect(hasBuys);

        // Invariant 1: Conservation of Money down to 1 paisa (when sells occur)
        if (hasSells) {
            BigDecimal totalSells = plan.sellSide().waterfall().stream()
                .map(w -> w.sold() != null ? w.sold() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalBuys = plan.buySide().buckets() != null
                ? plan.buySide().buckets().stream()
                    .map(b -> b.amountAllocated() != null ? b.amountAllocated() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                : BigDecimal.ZERO;
            BigDecimal cashOverflow = plan.buySide().cashOverflowInr() != null ? plan.buySide().cashOverflowInr() : BigDecimal.ZERO;
            BigDecimal diff = totalSells.subtract(totalBuys.add(cashOverflow)).abs();
            assertTrue(diff.compareTo(new BigDecimal("0.02")) <= 0,
                "Conservation of money failed: sells=" + totalSells + ", buys=" + totalBuys + ", overflow=" + cashOverflow);
        }

        // Invariant V1: Every scheme-level buy order is strictly >= ₹1,000 (or 0)
        BigDecimal minOrder = new BigDecimal("1000.00");
        if (plan.buySide() != null && plan.buySide().buckets() != null) {
            for (var b : plan.buySide().buckets()) {
                if (b.fundBreakdown() != null) {
                    for (var f : b.fundBreakdown()) {
                        if (f.amount() != null && f.amount().compareTo(BigDecimal.ZERO) > 0) {
                            assertTrue(
                                f.amount().compareTo(minOrder) >= 0,
                                "Scheme buy allocation below ₹1,000 min order: " + f.amount() + " for " + f.fundName()
                            );
                        }
                    }
                }
            }
        }

        // Invariant 3: 15% Liquid Cap ceiling (enforced when liquid buffer buys are allocated)
        if (plan.buySide() != null && plan.buySide().buckets() != null) {
            for (var b : plan.buySide().buckets()) {
                if (com.portfolioos.core.valuation.BucketEngine.Bucket.LIQUID_BUFFER.name().equals(b.bucket())) {
                    if (b.amountAllocated() != null && b.amountAllocated().compareTo(BigDecimal.ZERO) > 0) {
                        assertTrue(b.postRebalancePct() <= 15.01,
                            "Liquid buffer postRebalancePct exceeded 15% hard ceiling: " + b.postRebalancePct());
                    }
                }
            }
        }

        // Invariant 5: Exit-load exclusion during non-urgent rebalances (<15% drawdown)
        boolean urgent = com.portfolioos.core.common.PortfolioConstants.isUrgentRebalance(
            plan.trigger() != null && plan.trigger().drawdownContext() != null
                ? plan.trigger().drawdownContext().currentDrawdownPct()
                : 0.0
        );
        if (!urgent && plan.sellSide() != null && plan.sellSide().waterfall() != null) {
            for (var tier : plan.sellSide().waterfall()) {
                if (tier.lots() != null) {
                    for (var l : tier.lots()) {
                        int reqDays = ExitLoadCalculator.getRequiredExitLoadDays(l.fundId(), l.fundName());
                        assertTrue(l.holdingDays() >= reqDays,
                            "Exit load lot was sold during non-urgent rebalance: " + l.fundId() + " (held " + l.holdingDays() + " < " + reqDays + ")");
                    }
                }
            }
        }

        // R2 Prohibition: Zero satellite buy allocations when Core bucket is trimmed
        Set<String> coreIsins = (config != null && config.buckets() != null && config.buckets().containsKey(BucketType.EQUITY_CORE))
            ? config.buckets().get(BucketType.EQUITY_CORE).schemes().stream()
                .map(BucketTargetsConfig.SchemeSpec::isin).collect(java.util.stream.Collectors.toSet())
            : Set.of("INF109KC12U0", "INF879O01027");

        boolean coreTrimmed = plan.sellSide() != null && plan.sellSide().waterfall() != null &&
            plan.sellSide().waterfall().stream()
                .filter(w -> w.lots() != null)
                .flatMap(w -> w.lots().stream())
                .anyMatch(l -> coreIsins.contains(l.fundId()));

        if (coreTrimmed && plan.buySide() != null && plan.buySide().buckets() != null) {
            for (var b : plan.buySide().buckets()) {
                if (com.portfolioos.core.valuation.BucketEngine.Bucket.EQUITY_SATELLITE.name().equals(b.bucket())) {
                    assertEquals(0, BigDecimal.ZERO.compareTo(b.amountAllocated()),
                        "Satellite buy allocation must be 0 when Core is trimmed");
                }
            }
        }
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

    @Provide
    Arbitrary<String> fuzzDrawdownRungs() {
        return Arbitraries.of("NONE", "TIER_10", "TIER_15", "TIER_20");
    }

    @Provide
    Arbitrary<List<Lot>> fuzzMultiSchemeOpenLots() {
        LocalDate today = LocalDate.of(2026, 10, 15);
        LocalDate ltcgDate = today.minusDays(500);
        LocalDate stcgDate = today.minusDays(90);
        LocalDate lossDate = today.minusDays(450);
        LocalDate exitLoadDate = today.minusDays(20);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("145.25"), // LargeMidcap (Core)
            "INF879O01027", new BigDecimal("78.40"),  // PPFC (Core)
            "INF109KC13X2", new BigDecimal("55.60"),  // Value 30 (Satellite)
            "INF247L01916", new BigDecimal("32.10"),  // Midcap 150 (Satellite)
            "INF247L01BM8", new BigDecimal("22.80"),  // Gold FoF
            "INF205K01KR8", new BigDecimal("105.15"), // Arbitrage (Liquid)
            "INF209K01164", new BigDecimal("64.50")   // Legacy Holding
        );

        Arbitrary<Integer> coreLtcgUnits = Arbitraries.integers().between(100, 3000);
        Arbitrary<Integer> coreStcgUnits = Arbitraries.integers().between(50, 1000);
        Arbitrary<Integer> coreLossUnits = Arbitraries.integers().between(50, 1000);
        Arbitrary<Integer> satLtcgUnits = Arbitraries.integers().between(100, 2500);
        Arbitrary<Integer> goldUnits = Arbitraries.integers().between(100, 2000);
        Arbitrary<Integer> liqUnits = Arbitraries.integers().between(100, 2000);
        Arbitrary<Integer> legacyUnits = Arbitraries.integers().between(0, 1000);

        return Combinators.combine(coreLtcgUnits, coreStcgUnits, coreLossUnits, satLtcgUnits, goldUnits, liqUnits, legacyUnits)
            .as((cU1, cU2, cU3, sU, gU, lU, legU) -> {
                List<Lot> list = new ArrayList<>();
                BigDecimal navC1 = navMap.get("INF109KC12U0");
                BigDecimal navC2 = navMap.get("INF879O01027");
                BigDecimal navS1 = navMap.get("INF109KC13X2");
                BigDecimal navS2 = navMap.get("INF247L01916");
                BigDecimal navG = navMap.get("INF247L01BM8");
                BigDecimal navL = navMap.get("INF205K01KR8");
                BigDecimal navLeg = navMap.get("INF209K01164");

                // Core 1: LTCG Gain lot (cost = 70% of nav)
                list.add(new Lot("c1-ltcg", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
                    ltcgDate, BigDecimal.valueOf(cU1), BigDecimal.valueOf(cU1), navC1,
                    BigDecimal.valueOf(cU1).multiply(navC1).multiply(new BigDecimal("0.70")).setScale(2, RoundingMode.HALF_UP), false, null));

                // Core 1: Loss lot (cost = 135% of nav)
                list.add(new Lot("c1-loss", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund",
                    lossDate, BigDecimal.valueOf(cU3), BigDecimal.valueOf(cU3), navC1,
                    BigDecimal.valueOf(cU3).multiply(navC1).multiply(new BigDecimal("1.35")).setScale(2, RoundingMode.HALF_UP), false, null));

                // Core 2: Exit-load active lot (holding 20 days < 730 days for PPFC)
                list.add(new Lot("c2-exitload", "INF879O01027", "Parag Parikh Flexi Cap Fund",
                    exitLoadDate, BigDecimal.valueOf(cU2), BigDecimal.valueOf(cU2), navC2,
                    BigDecimal.valueOf(cU2).multiply(navC2).multiply(new BigDecimal("0.95")).setScale(2, RoundingMode.HALF_UP), false, null));

                // Satellite 1: STCG Gain lot (holding 90 days < 365)
                list.add(new Lot("s1-stcg", "INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund",
                    stcgDate, BigDecimal.valueOf(sU), BigDecimal.valueOf(sU), navS1,
                    BigDecimal.valueOf(sU).multiply(navS1).multiply(new BigDecimal("0.85")).setScale(2, RoundingMode.HALF_UP), false, null));

                // Satellite 2: LTCG lot
                list.add(new Lot("s2-ltcg", "INF247L01916", "Motilal Oswal Nifty Midcap 150 Index Fund",
                    ltcgDate, BigDecimal.valueOf(sU), BigDecimal.valueOf(sU), navS2,
                    BigDecimal.valueOf(sU).multiply(navS2).multiply(new BigDecimal("0.80")).setScale(2, RoundingMode.HALF_UP), false, null));

                // Gold: LTCG lot
                list.add(new Lot("g1-ltcg", "INF247L01BM8", "Motilal Oswal Gold and Silver ETFs FoF",
                    ltcgDate, BigDecimal.valueOf(gU), BigDecimal.valueOf(gU), navG,
                    BigDecimal.valueOf(gU).multiply(navG).multiply(new BigDecimal("0.75")).setScale(2, RoundingMode.HALF_UP), false, null));

                // Liquid Buffer: Arbitrage lot
                list.add(new Lot("l1-liq", "INF205K01KR8", "Invesco India Arbitrage Fund",
                    ltcgDate, BigDecimal.valueOf(lU), BigDecimal.valueOf(lU), navL,
                    BigDecimal.valueOf(lU).multiply(navL).multiply(new BigDecimal("0.98")).setScale(2, RoundingMode.HALF_UP), false, null));

                // Legacy lot (if legU > 0)
                if (legU > 0) {
                    list.add(new Lot("leg-1", "INF209K01164", "Aditya Birla Sun Life Frontline Equity Fund",
                        ltcgDate, BigDecimal.valueOf(legU), BigDecimal.valueOf(legU), navLeg,
                        BigDecimal.valueOf(legU).multiply(navLeg).multiply(new BigDecimal("0.80")).setScale(2, RoundingMode.HALF_UP), false, null));
                }

                return list;
            });
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

