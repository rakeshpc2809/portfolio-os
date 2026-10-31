package com.portfolioos.core.valuation;

import com.portfolioos.core.config.BucketTargetsConfig;
import com.portfolioos.core.config.BucketTargetsConfig.BucketType;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.persistence.TriggerHistoryRepository;
import com.portfolioos.core.rules.BucketConfigLoader;
import com.portfolioos.core.service.RebalancePlanEngine;
import com.portfolioos.core.service.RebalanceTriggerEvaluator;
import com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerResolution;
import com.portfolioos.core.service.RebalanceTriggerEvaluator.TriggerType;
import com.portfolioos.core.valuation.RebalanceInvariantVerifier.*;
import com.portfolioos.core.valuation.RebalanceWaterfallEngine.RoutingResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RebalanceV24RegressionTest {

    private BucketTargetsConfig config;
    private TriggerHistoryRepository repository;
    private RebalanceTriggerEvaluator evaluator;
    private RebalanceInvariantVerifier verifier;

    @BeforeEach
    void setUp() {
        config = BucketConfigLoader.loadV24Config();
        repository = new TriggerHistoryRepository(":memory:");
        repository.clearAll();
        evaluator = new RebalanceTriggerEvaluator(repository);
        verifier = new RebalanceInvariantVerifier();
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.close();
        }
    }

    // =========================================================================
    // V1: Sub-₹1,000 Dust Buy Order Rejection (or Negative Buy Order Rejection)
    // =========================================================================
    @Test
    @DisplayName("V1: Verifier strictly rejects buy orders below ₹1,000 threshold or negative buys")
    void testV1DustAndNegativeBuyRejection() {
        VerifiablePlan dustPlan = new VerifiablePlan(
            TriggerType.DRIFT, false, false,
            new BigDecimal("1000000.00"),
            List.of(new LotOrder("F1", "INF109KC12U0", LocalDate.now().minusDays(400), new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO, false)),
            Map.of(),
            List.of(
                new BuyOrder(BucketType.EQUITY_CORE, "INF109KC12U0", new BigDecimal("9500.00")),
                new BuyOrder(BucketType.EQUITY_SATELLITE, "INF109KC13X2", new BigDecimal("500.00")) // < 1,000 dust!
            ),
            Map.of(BucketType.LIQUID_BUFFER, new BigDecimal("100000.00")),
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("125000.00")
        );

        IllegalStateException exDust = assertThrows(IllegalStateException.class, () ->
            verifier.assertAllInvariants(dustPlan, config)
        );
        assertTrue(exDust.getMessage().contains("INVARIANT_V1_VIOLATION"));
        assertTrue(exDust.getMessage().contains("Buy order below min threshold"));

        VerifiablePlan negativePlan = new VerifiablePlan(
            TriggerType.DRIFT, false, false,
            new BigDecimal("1000000.00"),
            List.of(new LotOrder("F1", "INF109KC12U0", LocalDate.now().minusDays(400), new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO, false)),
            Map.of(),
            List.of(new BuyOrder(BucketType.EQUITY_CORE, "INF109KC12U0", new BigDecimal("-1000.00"))),
            Map.of(BucketType.LIQUID_BUFFER, new BigDecimal("100000.00")),
            new BigDecimal("11000.00"), BigDecimal.ZERO, new BigDecimal("125000.00")
        );

        IllegalStateException exNeg = assertThrows(IllegalStateException.class, () ->
            verifier.assertAllInvariants(negativePlan, config)
        );
        assertTrue(exNeg.getMessage().contains("INVARIANT_V1_VIOLATION"));
        assertTrue(exNeg.getMessage().contains("Negative buy order detected"));
    }

    // =========================================================================
    // V2: Pre-trade Lot Snapshot & Out-of-Order FIFO Rejection
    // =========================================================================
    @Test
    @DisplayName("V2: Verifier detects phantom sold lot or out-of-order RTA FIFO violation")
    void testV2PhantomLotAndFifoBreachRejection() {
        LocalDate d1 = LocalDate.of(2024, 1, 10);
        LocalDate d2 = LocalDate.of(2024, 5, 20);

        List<PreTradeLotSnapshot> preTradeSnapshots = List.of(
            new PreTradeLotSnapshot("LOT-1", "FOLIO-1", "INF109KC12U0", d1),
            new PreTradeLotSnapshot("LOT-2", "FOLIO-1", "INF109KC12U0", d2)
        );

        // Phantom lot test (LOT-999 does not exist in pre-trade snapshot)
        VerifiablePlan phantomPlan = new VerifiablePlan(
            TriggerType.DRIFT, false, false,
            new BigDecimal("1000000.00"),
            List.of(new LotOrder("LOT-999", "FOLIO-1", "INF109KC12U0", d1, new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO, false)),
            Map.of(),
            List.of(new BuyOrder(BucketType.EQUITY_CORE, "INF109KC12U0", new BigDecimal("10000.00"))),
            Map.of(BucketType.LIQUID_BUFFER, new BigDecimal("100000.00")),
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("125000.00")
        );
        IllegalStateException exPhantom = assertThrows(IllegalStateException.class, () ->
            verifier.assertAllInvariants(phantomPlan, preTradeSnapshots, config)
        );
        assertTrue(exPhantom.getMessage().contains("INVARIANT_V2_VIOLATION"));
        assertTrue(exPhantom.getMessage().contains("LOT-999"));

        // Out-of-order FIFO: LOT-2 sold while older LOT-1 remains unsold
        VerifiablePlan fifoBreachedPlan = new VerifiablePlan(
            TriggerType.DRIFT, false, false,
            new BigDecimal("1000000.00"),
            List.of(new LotOrder("LOT-2", "FOLIO-1", "INF109KC12U0", d2, new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO, false)),
            Map.of(), // verifier derives FIFO ordering directly from preTradeSnapshots
            List.of(new BuyOrder(BucketType.EQUITY_CORE, "INF109KC12U0", new BigDecimal("10000.00"))),
            Map.of(BucketType.LIQUID_BUFFER, new BigDecimal("100000.00")),
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("125000.00")
        );
        IllegalStateException exFifo = assertThrows(IllegalStateException.class, () ->
            verifier.assertAllInvariants(fifoBreachedPlan, preTradeSnapshots, config)
        );
        assertTrue(exFifo.getMessage().contains("INVARIANT_2_VIOLATION"));
        assertTrue(exFifo.getMessage().contains("RTA FIFO breached"));
    }

    // =========================================================================
    // V3: Dynamic Derivation of coreTrimmed from Config Schemes
    // =========================================================================
    @Test
    @DisplayName("V3: Verifier dynamically derives coreTrimmed if sold lot is in EQUITY_CORE schemes, rejecting Satellite buys")
    void testV3DynamicCoreTrimmedDerivation() {
        VerifiablePlan plan = new VerifiablePlan(
            TriggerType.DRIFT, false,
            false, // plan claim: coreTrimmed = false!
            new BigDecimal("1000000.00"),
            List.of(new LotOrder("F1", "INF109KC12U0", LocalDate.now().minusDays(400), new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO, false)), // but INF109KC12U0 is LargeMid 250 (EQUITY_CORE)
            Map.of(),
            List.of(new BuyOrder(BucketType.EQUITY_SATELLITE, "INF109KC13X2", new BigDecimal("10000.00"))), // routes to satellite!
            Map.of(BucketType.LIQUID_BUFFER, new BigDecimal("100000.00")),
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("125000.00")
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            verifier.assertAllInvariants(plan, config)
        );
        assertTrue(ex.getMessage().contains("INVARIANT_4_VIOLATION"));
        assertTrue(ex.getMessage().contains("EQUITY_CORE proceeds routed into EQUITY_SATELLITE"));
    }

    // =========================================================================
    // V4: Non-Positive Effective Corpus Rejection
    // =========================================================================
    @Test
    @DisplayName("V4: Verifier fails closed when effectiveActiveCorpus is <= 0")
    void testV4NonPositiveCorpusRejection() {
        VerifiablePlan zeroCorpusPlan = new VerifiablePlan(
            TriggerType.DRIFT, false, false,
            BigDecimal.ZERO,
            List.of(), Map.of(), List.of(),
            Map.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            verifier.assertAllInvariants(zeroCorpusPlan, config)
        );
        assertTrue(ex.getMessage().contains("INVARIANT_V4_VIOLATION"));
    }

    // =========================================================================
    // R1: Monotonic 15% Liquid Buffer Cap Enforcement
    // =========================================================================
    @Test
    @DisplayName("R1: Monotonic liquid cap: reject trade that buys Liquid and pushes it beyond 15%")
    void testR1MonotonicLiquidCap() {
        // Effective corpus: 1,000,000 -> 15% cap is 150,000
        // Liquid post-trade is 160,000 (16%) with a positive liquid buy
        VerifiablePlan plan = new VerifiablePlan(
            TriggerType.DRIFT, false, false,
            new BigDecimal("1000000.00"),
            List.of(new LotOrder("F1", "INF247L01BM8", LocalDate.now().minusDays(800), new BigDecimal("20000.00"), BigDecimal.ZERO, BigDecimal.ZERO, false)),
            Map.of(),
            List.of(new BuyOrder(BucketType.LIQUID_BUFFER, "INF205K01KR8", new BigDecimal("20000.00"))),
            Map.of(BucketType.LIQUID_BUFFER, new BigDecimal("160000.00")), // 16% > 15%
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("125000.00")
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            verifier.assertAllInvariants(plan, config)
        );
        assertTrue(ex.getMessage().contains("INVARIANT_3_VIOLATION"));
        assertTrue(ex.getMessage().contains("LIQUID_BUFFER breached 15% hard cap"));
    }

    // =========================================================================
    // R2: Mixed-Pool Satellite Prohibition
    // =========================================================================
    @Test
    @DisplayName("R2: Waterfall routeProceedsV24 enforces zero satellite buys when Core trim pool is present")
    void testR2MixedPoolSatelliteProhibition() {
        BigDecimal corpus = new BigDecimal("1000000.00");
        BigDecimal coreTrim = new BigDecimal("50000.00");
        BigDecimal generalPool = new BigDecimal("70000.00");

        Map<BucketType, BigDecimal> postSellValues = Map.of(
            BucketType.EQUITY_CORE, new BigDecimal("500000.00"),
            BucketType.EQUITY_SATELLITE, new BigDecimal("200000.00"), // deficit of 100,000!
            BucketType.GOLD_SILVER, new BigDecimal("100000.00"),
            BucketType.LIQUID_BUFFER, new BigDecimal("100000.00")
        );

        Map<BucketType, BigDecimal> targetWeights = Map.of(
            BucketType.EQUITY_CORE, new BigDecimal("50.00"),
            BucketType.EQUITY_SATELLITE, new BigDecimal("30.00"),
            BucketType.GOLD_SILVER, new BigDecimal("10.00"),
            BucketType.LIQUID_BUFFER, new BigDecimal("10.00")
        );

        RoutingResult result = RebalanceWaterfallEngine.routeProceedsV24(
            config,
            TriggerType.DRIFT,
            coreTrim,
            generalPool,
            corpus,
            postSellValues,
            targetWeights
        );

        // CoreTrim must NOT leak into Satellite despite 100,000 deficit!
        assertEquals(0, result.bucketBuysInr().get(BucketType.EQUITY_SATELLITE).compareTo(BigDecimal.ZERO),
            "Satellite buys MUST be 0.00 when Core is trimmed");

        List<BuyOrder> buyOrders = result.bucketBuysInr().entrySet().stream()
            .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
            .map(e -> new BuyOrder(e.getKey(), "SCHEME-" + e.getKey(), e.getValue()))
            .toList();

        BigDecimal summaryTax = new BigDecimal("1000.00");
        List<LotOrder> soldLots = List.of(
            new LotOrder("L1", "FOLIO1", "INF109KC12U0", LocalDate.now().minusDays(400), coreTrim, summaryTax, BigDecimal.ZERO, false),
            new LotOrder("L2", "FOLIO1", "INF247L01BQ9", LocalDate.now().minusDays(500), generalPool, BigDecimal.ZERO, BigDecimal.ZERO, false)
        );

        Map<BucketType, BigDecimal> postTradeVals = new EnumMap<>(BucketType.class);
        postSellValues.forEach((b, v) -> {
            postTradeVals.put(b, v.add(result.bucketBuysInr().getOrDefault(b, BigDecimal.ZERO)));
        });

        VerifiablePlan plan = new VerifiablePlan(
            TriggerType.DRIFT, false, true,
            corpus,
            soldLots,
            Map.of(),
            buyOrders,
            postTradeVals,
            result.cashOverflowInr(),
            summaryTax,
            new BigDecimal("125000.00")
        );

        assertDoesNotThrow(() -> verifier.assertAllInvariants(plan, config));
    }

    // =========================================================================
    // E1: Scheduled Trigger Idempotency per (Year, Month)
    // =========================================================================
    @Test
    @DisplayName("E1: Scheduled trigger fires once per (Year, Month) and is suppressed after recorded execution")
    void testE1ScheduledTriggerIdempotency() {
        LocalDate septDate = LocalDate.of(2026, 9, 15);
        BigDecimal nav = new BigDecimal("100.00");

        List<Lot> openLots = List.of(
            new Lot("l1", "INF109KC12U0", "ICICI LargeMidcap 250", septDate.minusMonths(6), new BigDecimal("3000"), new BigDecimal("3000"), nav, new BigDecimal("300000.00"), false, null),
            new Lot("l2", "INF879O01027", "Parag Parikh Flexi Cap", septDate.minusMonths(6), new BigDecimal("2000"), new BigDecimal("2000"), nav, new BigDecimal("200000.00"), false, null),
            new Lot("l3", "INF109KC13X2", "ICICI Value 30", septDate.minusMonths(6), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null),
            new Lot("l4", "INF754K01TN5", "Edelweiss Momentum 50", septDate.minusMonths(6), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null),
            new Lot("l5", "INF204K01K15", "Nippon Small Cap", septDate.minusMonths(6), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null),
            new Lot("l6", "INF247L01BM8", "Motilal Gold Silver", septDate.minusMonths(6), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null),
            new Lot("l7", "INF205K01KR8", "Invesco Arbitrage", septDate.minusMonths(6), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null)
        );
        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", nav,
            "INF879O01027", nav,
            "INF109KC13X2", nav,
            "INF754K01TN5", nav,
            "INF204K01K15", nav,
            "INF247L01BM8", nav,
            "INF205K01KR8", nav
        );

        // First run in September 2026 -> Fires SCHEDULED
        TriggerResolution res1 = evaluator.getCurrentStatus(
            openLots, navMap, new BigDecimal("25000.00"), new BigDecimal("25000.00"), null, null, septDate
        );
        assertEquals("SCHEDULED", res1.triggerType());

        // Record execution in repository for September 2026
        repository.recordScheduledRunExecution(2026, 9, septDate.atStartOfDay());

        // Second run on September 16 -> Suppressed by idempotency (returns NONE)
        TriggerResolution res2 = evaluator.getCurrentStatus(
            openLots, navMap, new BigDecimal("25000.00"), new BigDecimal("25000.00"), null, null, septDate.plusDays(1)
        );
        assertEquals("NONE", res2.triggerType(), "Scheduled trigger must not fire twice in the same (Year, Month)");
    }

    // =========================================================================
    // E2: Blackout Ceiling Math: (48 + 23) / 24 = 2 Days
    // =========================================================================
    @Test
    @DisplayName("E2: NSE reconstitution blackout suppresses scheduled trigger within last 48h (Sep 29, Sep 30)")
    void testE2BlackoutCeilingMath() {
        BigDecimal nav = new BigDecimal("100.00");
        List<Lot> openLots = List.of(
            new Lot("l1", "INF109KC12U0", "ICICI LargeMidcap 250", LocalDate.of(2025, 1, 1), new BigDecimal("3000"), new BigDecimal("3000"), nav, new BigDecimal("300000.00"), false, null),
            new Lot("l2", "INF879O01027", "Parag Parikh Flexi Cap", LocalDate.of(2025, 1, 1), new BigDecimal("2000"), new BigDecimal("2000"), nav, new BigDecimal("200000.00"), false, null),
            new Lot("l3", "INF109KC13X2", "ICICI Value 30", LocalDate.of(2025, 1, 1), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null),
            new Lot("l4", "INF754K01TN5", "Edelweiss Momentum 50", LocalDate.of(2025, 1, 1), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null),
            new Lot("l5", "INF204K01K15", "Nippon Small Cap", LocalDate.of(2025, 1, 1), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null),
            new Lot("l6", "INF247L01BM8", "Motilal Gold Silver", LocalDate.of(2025, 1, 1), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null),
            new Lot("l7", "INF205K01KR8", "Invesco Arbitrage", LocalDate.of(2025, 1, 1), new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null)
        );
        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", nav,
            "INF879O01027", nav,
            "INF109KC13X2", nav,
            "INF754K01TN5", nav,
            "INF204K01K15", nav,
            "INF247L01BM8", nav,
            "INF205K01KR8", nav
        );

        // September 30 -> 0 days left before Sep 30 -> inside 48h (2-day) blackout window!
        LocalDate sept30 = LocalDate.of(2026, 9, 30);
        TriggerResolution resBlackout = evaluator.getCurrentStatus(
            openLots, navMap, new BigDecimal("25000.00"), new BigDecimal("25000.00"), null, null, sept30
        );
        assertEquals("NONE", resBlackout.triggerType(), "Scheduled trigger must be paused inside NSE blackout window");

        // September 25 -> 5 days left -> outside blackout window -> fires SCHEDULED
        LocalDate sept25 = LocalDate.of(2026, 9, 25);
        TriggerResolution resAllowed = evaluator.getCurrentStatus(
            openLots, navMap, new BigDecimal("25000.00"), new BigDecimal("25000.00"), null, null, sept25
        );
        assertEquals("SCHEDULED", resAllowed.triggerType());
    }

    // =========================================================================
    // E3: Config Ascending Sort Invariant
    // =========================================================================
    @Test
    @DisplayName("E3: Drawdown rungs and dampener tiers are strictly sorted ascending in BucketTargetsConfig constructor")
    void testE3ConfigAscendingSort() {
        for (int i = 0; i < config.drawdownRungs().size() - 1; i++) {
            BigDecimal curr = config.drawdownRungs().get(i).drawdownPct();
            BigDecimal next = config.drawdownRungs().get(i + 1).drawdownPct();
            assertTrue(curr.compareTo(next) < 0, "Drawdown rungs must be sorted ascending");
        }
        for (int i = 0; i < config.dampenerTiers().size() - 1; i++) {
            BigDecimal curr = config.dampenerTiers().get(i).maxRelativeExcessPct();
            BigDecimal next = config.dampenerTiers().get(i + 1).maxRelativeExcessPct();
            assertTrue(curr.compareTo(next) < 0, "Dampener tiers must be sorted ascending");
        }
    }

    // =========================================================================
    // E4: Scoped Feed Degradation (Null Benchmark skips only DRAWDOWN)
    // =========================================================================
    @Test
    @DisplayName("E4: Null benchmark skips DRAWDOWN branch while DRIFT evaluation continues cleanly")
    void testE4ScopedFeedDegradation() {
        BigDecimal nav = new BigDecimal("100.00");
        LocalDate today = LocalDate.of(2026, 8, 1);

        // Create drifted portfolio: Core is 80% (> 55% upper bound)
        List<Lot> openLots = List.of(
            new Lot("l1", "INF109KC12U0", "ICICI LargeMidcap 250", today.minusMonths(6), new BigDecimal("8000"), new BigDecimal("8000"), nav, new BigDecimal("800000.00"), false, null),
            new Lot("l2", "INF205K01KR8", "Invesco Arbitrage", today.minusMonths(6), new BigDecimal("2000"), new BigDecimal("2000"), nav, new BigDecimal("200000.00"), false, null)
        );
        Map<String, BigDecimal> navMap = Map.of("INF109KC12U0", nav, "INF205K01KR8", nav);

        // evaluate with allowScopedFeedDegradation = true
        TriggerResolution res = evaluator.getCurrentStatus(
            openLots, navMap, null, null, null, null, today, true
        );

        assertNotNull(res);
        assertEquals("DRIFT", res.triggerType(), "DRIFT must still evaluate and fire even when benchmark is null under scoped feed degradation");
        assertTrue(res.driftedBuckets().contains("CORE_AGGREGATE_BREACH"));
    }

    // =========================================================================
    // E5: Persisted ATH Watermark Integrity & Dynamic Advancement
    // =========================================================================
    @Test
    @DisplayName("E5: Persisted ATH watermark <= 0 throws IllegalStateException")
    void testE5WatermarkNonPositiveIntegrityCheck() {
        // Non-positive watermark in DB
        repository.setNiftyTriWatermark(BigDecimal.ZERO);
        IllegalStateException exZero = assertThrows(IllegalStateException.class, () ->
            evaluator.getCurrentStatus(
                List.of(), Map.of(), new BigDecimal("25000.00"), new BigDecimal("25000.00"), null, null, LocalDate.now()
            )
        );
        assertTrue(exZero.getMessage().contains("Stored Nifty TRI watermark <= 0"));
    }

    @Test
    @DisplayName("E5: Stored watermark 10% below current TRI advances without throwing (new peak advancement)")
    void testStoredWatermarkTenPercentBelowCurrentAdvancesWatermark() {
        // Stored watermark = 22,500.00 (10% below current TRI = 25,000.00)
        repository.setNiftyTriWatermark(new BigDecimal("22500.00"));
        BigDecimal currentTri = new BigDecimal("25000.00");
        BigDecimal rollingHigh = new BigDecimal("25000.00");

        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            List.of(), Map.of(), currentTri, rollingHigh, null, null, LocalDate.now()
        );

        assertNotNull(res);
        assertEquals(0, BigDecimal.ZERO.compareTo(BigDecimal.valueOf(res.drawdownContext().currentDrawdownPct())),
            "Current TRI is at all-time high, drawdown must be 0%");
        assertEquals(0, currentTri.compareTo(res.drawdownContext().rollingHighValue()),
            "Effective high must advance to current TRI peak of 25,000.00");

        // Verify watermark in repository was advanced and persisted as 25,000.00
        assertEquals(new BigDecimal("25000.00"), repository.getNiftyTriWatermark().orElseThrow());
    }

    // =========================================================================
    // H1: Harmful Loss Harvest Gate
    // =========================================================================
    @Test
    @DisplayName("H1: Harmful loss harvest gate only harvests loss lot if bucket trimmed or net taxable gains exist")
    void testH1HarmfulLossHarvestGate() {
        // Condition 1: Step 0 legacy is always harvested
        assertTrue(RebalanceWaterfallEngine.shouldHarvestLossLot(true, false, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("125000.00")));

        // Condition 2: Bucket is being trimmed -> harvested
        assertTrue(RebalanceWaterfallEngine.shouldHarvestLossLot(false, true, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("125000.00")));

        // Condition 3: Net STCG > 0 -> harvested to offset 20% tax
        assertTrue(RebalanceWaterfallEngine.shouldHarvestLossLot(false, false, new BigDecimal("5000.00"), BigDecimal.ZERO, new BigDecimal("125000.00")));

        // Condition 4: Net LTCG exceeds 1.25L exemption -> harvested to offset 12.5% tax
        assertTrue(RebalanceWaterfallEngine.shouldHarvestLossLot(false, false, BigDecimal.ZERO, new BigDecimal("150000.00"), new BigDecimal("125000.00")));

        // Blocked condition: No STCG, and LTCG (50k) is within 1.25L exemption -> DO NOT harvest loss to burn exemption!
        assertFalse(RebalanceWaterfallEngine.shouldHarvestLossLot(false, false, BigDecimal.ZERO, new BigDecimal("50000.00"), new BigDecimal("125000.00")));
    }

    // =========================================================================
    // Edge Cases: Invariant 1 with Zero Sells & Gold Backstop Dust Routing
    // =========================================================================
    @Test
    @DisplayName("Invariant 1: Phantom buys with zero sells in DRIFT plan are rejected by Conservation of Money")
    void testZeroSellsWithPhantomBuyRejected() {
        VerifiablePlan phantomBuyPlan = new VerifiablePlan(
            TriggerType.DRIFT, false, false,
            new BigDecimal("1000000.00"),
            List.of(), // 0 sold lots!
            Map.of(),
            List.of(new BuyOrder(BucketType.EQUITY_CORE, "INF109KC12U0", new BigDecimal("5000.00"))), // ₹5,000 phantom buy
            Map.of(BucketType.LIQUID_BUFFER, new BigDecimal("100000.00")),
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("125000.00")
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            verifier.assertAllInvariants(phantomBuyPlan, config)
        );
        assertTrue(ex.getMessage().contains("INVARIANT_1_VIOLATION"));
        assertTrue(ex.getMessage().contains("Conservation of money failed"));
    }

    @Test
    @DisplayName("Gold Floor Backstop: sub-₹1,000 dust pool routes to cash overflow and does not violate Invariant V1")
    void testGoldFloorBackstopSubMinOrderRoutesToOverflow() {
        RoutingResult result = RebalanceWaterfallEngine.routeProceedsV24(
            config,
            TriggerType.GOLD_FLOOR_BACKSTOP,
            BigDecimal.ZERO,
            new BigDecimal("800.00"), // ₹800 sub-minOrder dust!
            new BigDecimal("1000000.00"),
            Map.of(
                BucketType.EQUITY_CORE, new BigDecimal("500000.00"),
                BucketType.EQUITY_SATELLITE, new BigDecimal("300000.00"),
                BucketType.GOLD_SILVER, new BigDecimal("80000.00"),
                BucketType.LIQUID_BUFFER, new BigDecimal("100000.00")
            ),
            Map.of(
                BucketType.EQUITY_CORE, new BigDecimal("50.00"),
                BucketType.EQUITY_SATELLITE, new BigDecimal("30.00"),
                BucketType.GOLD_SILVER, new BigDecimal("10.00"),
                BucketType.LIQUID_BUFFER, new BigDecimal("10.00")
            )
        );

        // Sub-₹1,000 order must NOT be in bucketBuys
        assertEquals(0, result.bucketBuysInr().get(BucketType.GOLD_SILVER).compareTo(BigDecimal.ZERO),
            "Gold buy must be 0 for sub-₹1,000 dust pool");
        assertEquals(0, new BigDecimal("800.00").compareTo(result.cashOverflowInr()),
            "Pool of 800 must route to cashOverflow");

        List<BuyOrder> buyOrders = result.bucketBuysInr().entrySet().stream()
            .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
            .map(e -> new BuyOrder(e.getKey(), "DUMMY", e.getValue()))
            .toList();

        VerifiablePlan plan = new VerifiablePlan(
            TriggerType.GOLD_FLOOR_BACKSTOP, false, false,
            new BigDecimal("1000000.00"),
            List.of(),
            Map.of(),
            buyOrders, // empty buy orders
            Map.of(BucketType.LIQUID_BUFFER, new BigDecimal("100000.00")),
            result.cashOverflowInr(),
            BigDecimal.ZERO,
            new BigDecimal("125000.00")
        );

        assertDoesNotThrow(() -> verifier.assertAllInvariants(plan, config));
    }
}
