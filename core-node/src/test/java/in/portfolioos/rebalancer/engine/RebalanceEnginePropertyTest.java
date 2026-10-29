package in.portfolioos.rebalancer.engine;

import in.portfolioos.rebalancer.config.BucketTargetsConfig;
import in.portfolioos.rebalancer.config.BucketTargetsConfig.BackstopSpec;
import in.portfolioos.rebalancer.config.BucketTargetsConfig.BucketSpec;
import in.portfolioos.rebalancer.config.BucketTargetsConfig.BucketType;
import in.portfolioos.rebalancer.config.BucketTargetsConfig.DampenerTier;
import in.portfolioos.rebalancer.config.BucketTargetsConfig.DrawdownRung;
import in.portfolioos.rebalancer.config.BucketTargetsConfig.GlobalInvariants;
import in.portfolioos.rebalancer.config.BucketTargetsConfig.SchemeSpec;
import in.portfolioos.rebalancer.config.BucketTargetsConfig.TaxParameters;
import in.portfolioos.rebalancer.engine.RebalanceInvariantVerifier.BuyOrder;
import in.portfolioos.rebalancer.engine.RebalanceInvariantVerifier.LotOrder;
import in.portfolioos.rebalancer.engine.RebalanceInvariantVerifier.VerifiablePlan;
import in.portfolioos.rebalancer.engine.RebalanceTriggerEvaluator.TriggerType;
import in.portfolioos.rebalancer.engine.RebalanceWaterfallEngine.RoutingResult;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Provide;
import net.jqwik.api.Property;
import net.jqwik.api.ShrinkingMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RebalanceEnginePropertyTest {

    private final BucketTargetsConfig config = buildCanonicalV24Config();
    private final RebalanceWaterfallEngine waterfallEngine = new RebalanceWaterfallEngine();
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

        RoutingResult result = waterfallEngine.routeProceeds(
            config,
            TriggerType.DRIFT,
            state.coreTrimPoolInr(),
            state.generalPoolInr(),
            state.effectiveActiveCorpusInr(),
            state.postSellValuesInr(),
            targetsPct
        );

        // Build post-trade values & synthetic verifiable plan
        VerifiablePlan plan = buildVerifiablePlan(
            TriggerType.DRIFT,
            false,
            coreTrimmed,
            state,
            result
        );

        // When Core is trimmed and General Pool is also active, isolate test to Core-only
        // if checking strict Core->Satellite prohibition, or verify pure Core pool routing:
        if (coreTrimmed && state.generalPoolInr().compareTo(BigDecimal.ZERO) == 0) {
            assertEquals(
                0,
                result.bucketBuysInr().get(BucketType.EQUITY_SATELLITE).compareTo(BigDecimal.ZERO),
                "Core trim pool must NEVER route a single paisa to EQUITY_SATELLITE"
            );
            verifier.assertAllInvariants(plan, config);
        } else if (!coreTrimmed) {
            verifier.assertAllInvariants(plan, config);
        }
    }

    // =========================================================================
    // PROPERTY 2: 1,000-Portfolio Fuzzing of Crisis DRAWDOWN Routing
    // Verifies: 0% leakage to GOLD_SILVER, strict Equity routing,
    //           and exact Paisa conservation across TIER_10, TIER_15, TIER_20.
    // =========================================================================
    @Property(tries = 1000, shrinking = ShrinkingMode.FULL)
    void fuzzDrawdownCrisisRoutingNeverLeaksToGold(
            @ForAll("fuzzPortfolioStates") FuzzPortfolioState state) {

        Map<BucketType, BigDecimal> targetsPct = getRegimeTargets(state.drawdownRung());
        boolean urgent = "TIER_15".equals(state.drawdownRung()) || "TIER_20".equals(state.drawdownRung());

        RoutingResult result = waterfallEngine.routeProceeds(
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

        RoutingResult result = waterfallEngine.routeProceeds(
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
    void firewallRejectsTwoPaisaAccountingLeak() {
        FuzzPortfolioState state = sampleValidState();
        RoutingResult result = waterfallEngine.routeProceeds(
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
    void firewallRejectsOutOfOrderRtaFifoLotSelection() {
        LotOrder cherryPickedNewerLot = new LotOrder(
            "FOLIO_101", "INF109KC12U0",
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
    void firewallRejectsExitLoadLotInNonUrgentRebalance() {
        LotOrder ppfasExitLoadLot = new LotOrder(
            "FOLIO_PPFAS", "INF879O01027",
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
        assertTrue(ex.getMessage().contains("INVARIANT_6_VIOLATION"));
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
                // Ensure sum of post-sell percentages is < 100% so a valid sell pool exists
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
        List<LotOrder> soldLots = totalSellPool.compareTo(BigDecimal.ZERO) > 0
            ? List.of(new LotOrder(
                "FOLIO_1", "INF109KC12U0", LocalDate.of(2022, 1, 10),
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
            Map.of("FOLIO_1::INF109KC12U0", List.of(LocalDate.of(2023, 5, 1))),
            buyOrders,
            postTradeValues,
            result.cashOverflowInr(),
            summaryTax,
            new BigDecimal("125000.00")
        );
    }

    private Map<BucketType, BigDecimal> getRegimeTargets(String tier) {
        Map<BucketType, BigDecimal> map = new EnumMap<>(BucketType.class);
        map.put(BucketType.EQUITY_SATELLITE, new BigDecimal("30.00"));
        map.put(BucketType.GOLD_SILVER, new BigDecimal("10.00"));
        switch (tier) {
            case "TIER_10" -> {
                map.put(BucketType.EQUITY_CORE, new BigDecimal("52.50"));
                map.put(BucketType.LIQUID_BUFFER, new BigDecimal("7.50"));
            }
            case "TIER_15" -> {
                map.put(BucketType.EQUITY_CORE, new BigDecimal("55.00"));
                map.put(BucketType.LIQUID_BUFFER, new BigDecimal("5.00"));
            }
            case "TIER_20" -> {
                map.put(BucketType.EQUITY_CORE, new BigDecimal("60.00"));
                map.put(BucketType.LIQUID_BUFFER, new BigDecimal("0.00"));
            }
            default -> {
                map.put(BucketType.EQUITY_CORE, new BigDecimal("50.00"));
                map.put(BucketType.LIQUID_BUFFER, new BigDecimal("10.00"));
            }
        }
        return map;
    }

    private FuzzPortfolioState sampleValidState() {
        BigDecimal corpus = new BigDecimal("1000000.00");
        Map<BucketType, BigDecimal> postSell = new EnumMap<>(BucketType.class);
        postSell.put(BucketType.EQUITY_CORE, new BigDecimal("530600.00"));
        postSell.put(BucketType.EQUITY_SATELLITE, new BigDecimal("270000.00"));
        postSell.put(BucketType.GOLD_SILVER, new BigDecimal("70000.00"));
        postSell.put(BucketType.LIQUID_BUFFER, new BigDecimal("70000.00"));
        return new FuzzPortfolioState(corpus, postSell, new BigDecimal("59400.00"), BigDecimal.ZERO, "NONE");
    }

    private static BigDecimal scale(BigDecimal base, String factor) {
        return base.multiply(new BigDecimal(factor)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BucketTargetsConfig buildCanonicalV24Config() {
        return new BucketTargetsConfig(
            "2.4.0",
            new GlobalInvariants(
                30,
                new BigDecimal("1000.00"),
                new BigDecimal("15.00"),
                "ATH_WATERMARK",
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                new BigDecimal("15.00"),
                List.of(3, 9),
                48
            ),
            new TaxParameters(
                new BigDecimal("125000.00"),
                new BigDecimal("0.125"),
                new BigDecimal("0.200"),
                new BigDecimal("0.125"),
                new BigDecimal("0.300"),
                new BigDecimal("0.04")
            ),
            List.of(
                new DrawdownRung("TIER_10", new BigDecimal("10.00"), new BigDecimal("7.50"), new BigDecimal("52.50"), new BigDecimal("0.25")),
                new DrawdownRung("TIER_15", new BigDecimal("15.00"), new BigDecimal("5.00"), new BigDecimal("55.00"), new BigDecimal("0.50")),
                new DrawdownRung("TIER_20", new BigDecimal("20.00"), new BigDecimal("0.00"), new BigDecimal("60.00"), new BigDecimal("1.00"))
            ),
            List.of(
                new DampenerTier(new BigDecimal("15.0"), new BigDecimal("0.40")),
                new DampenerTier(new BigDecimal("25.0"), new BigDecimal("0.66")),
                new DampenerTier(new BigDecimal("999.0"), new BigDecimal("0.75"))
            ),
            Map.of(
                BucketType.EQUITY_CORE, new BucketSpec(
                    new BigDecimal("50.00"), new BigDecimal("5.00"), new BigDecimal("5.00"), null,
                    List.of(new SchemeSpec("INF109KC12U0", "ICICI LargeMid 250", "ICICI", new BigDecimal("0.60"), 0, 365, 1))
                ),
                BucketType.EQUITY_SATELLITE, new BucketSpec(
                    new BigDecimal("30.00"), new BigDecimal("5.00"), new BigDecimal("5.00"), null,
                    List.of(new SchemeSpec("INF109KC13X2", "ICICI Value 30", "ICICI", BigDecimal.ONE, 0, 365, 1))
                ),
                BucketType.GOLD_SILVER, new BucketSpec(
                    new BigDecimal("10.00"), new BigDecimal("2.00"), new BigDecimal("12.00"),
                    new BackstopSpec(6, new BigDecimal("2.00"), new BigDecimal("0.50")),
                    List.of(new SchemeSpec("INF247L01BM8", "Motilal Gold Silver FoF", "MOTILAL", BigDecimal.ONE, 15, 730, 2))
                ),
                BucketType.LIQUID_BUFFER, new BucketSpec(
                    new BigDecimal("10.00"), new BigDecimal("2.50"), new BigDecimal("2.50"), null,
                    List.of(new SchemeSpec("INF205K01KR8", "Invesco Arbitrage", "INVESCO", BigDecimal.ONE, 15, 365, 1))
                )
            ),
            List.of("INF174KA1TY2", "INF247L01916", "INF247L01BQ9", "INF769K01ED6")
        );
    }
}

