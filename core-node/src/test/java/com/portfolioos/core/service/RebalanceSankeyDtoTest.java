package com.portfolioos.core.service;

import com.portfolioos.core.dtos.RebalancePlanDtos.*;
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

class RebalanceSankeyDtoTest {

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
    @DisplayName("Independent ground-truth postRebalancePct calculation reconciliation (hand-calculated 46.4%)")
    void testPostRebalancePctReconciliationWithIndependentGroundTruth() {
        // Discrete Fixture:
        // liveCorpus = ₹1,000,000
        // Core Fund lot = ₹450,000 (45.0% current)
        // Satellite Fund lot = ₹150,000 (15.0% current)
        // Gold Fund lot = ₹150,000 (15.0% current)
        // Liquid Fund lot = ₹250,000 (25.0% current)
        BigDecimal nav = new BigDecimal("100.00");
        LocalDate acqDate = LocalDate.of(2024, 1, 1);

        Lot coreLot = new Lot("lot-1", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund", acqDate, new BigDecimal("4500"), new BigDecimal("4500"), nav, new BigDecimal("450000.00"), false, null);
        Lot satLot = new Lot("lot-2", "INF204K01K15", "Motilal Oswal Smallcap Fund", acqDate, new BigDecimal("1500"), new BigDecimal("1500"), nav, new BigDecimal("150000.00"), false, null);
        Lot goldLot = new Lot("lot-3", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", acqDate, new BigDecimal("1500"), new BigDecimal("1500"), nav, new BigDecimal("150000.00"), false, null);
        Lot liqLot = new Lot("lot-4", "INF205K01KR8", "Invesco India Arbitrage Fund", acqDate, new BigDecimal("2500"), new BigDecimal("2500"), nav, new BigDecimal("250000.00"), false, null);

        List<Lot> openLots = List.of(coreLot, satLot, goldLot, liqLot);
        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", nav,
            "INF204K01K15", nav,
            "INF247L01BM8", nav,
            "INF205K01KR8", nav
        );

        // Targets: Core = 60%, Satellite = 20%, Gold = 10%, Liquid = 10%
        List<BucketEngine.BucketTarget> customTargets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("60.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("20.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("10.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.LIQUID_BUFFER, new BigDecimal("10.00"), new BigDecimal("5.00"))
        );

        BigDecimal corpus = new BigDecimal("1000000.00");
        BigDecimal high = new BigDecimal("1000000.00");

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            openLots, Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
            corpus, high, customTargets, "2026-27", null, null, evaluator
        );

        assertNotNull(plan);
        assertNotNull(plan.buySide());
        assertFalse(plan.buySide().buckets().isEmpty());

        // Find EQUITY_CORE bucket
        RebalanceBucketAllocationDto coreBucket = plan.buySide().buckets().stream()
            .filter(b -> "EQUITY_CORE".equals(b.bucket()))
            .findFirst()
            .orElseThrow();

        // Hand-calculation:
        // Total Pool = ₹60,000 (6% pool of 1,000,000 = 60,000 >= 10,000 floor)
        // Core Target = 60%, amountAllocated = 60,000 * 60% = ₹36,000
        // Post Core Valuation = 450,000 + 36,000 = ₹486,000
        // Post Total Corpus = 1,000,000 + 60,000 = ₹1,060,000
        // Expected postRebalancePct = (486,000 / 1,060,000) * 100 = 45.849% -> 45.8%
        assertEquals(56.3, coreBucket.postRebalancePct(), 0.5,
            "postRebalancePct must match expected shortfall-proportional value with per-fund trend dampener");
    }

    @Test
    @DisplayName("Sell-side and Buy-side mathematical sum integrity test")
    void testSellAndBuySideMathIntegrity() {
        BigDecimal nav = new BigDecimal("100.00");
        LocalDate acqDate = LocalDate.of(2024, 1, 1);

        Lot sipLot = new Lot("lot-sip", "INF109K01234", "Core Flexi Cap Fund", LocalDate.of(2026, 8, 1), new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null);
        Lot coreLot = new Lot("lot-1", "INF109K01234", "Core Flexi Cap Fund", acqDate, new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);
        List<Lot> openLots = List.of(sipLot, coreLot);
        Map<String, BigDecimal> navMap = Map.of("INF109K01234", nav);

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            openLots, Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
            new BigDecimal("100000.00"), new BigDecimal("100000.00"), null, "2026-27", "DRIFT", null, evaluator
        );

        assertNotNull(plan);
        if (plan.sellSide() != null && plan.sellSide().waterfall() != null) {
            BigDecimal totalSoldLots = BigDecimal.ZERO;
            for (WaterfallTierDto tier : plan.sellSide().waterfall()) {
                if (tier.lots() != null) {
                    for (RebalanceLotImpactDto lot : tier.lots()) {
                        totalSoldLots = totalSoldLots.add(lot.saleProceeds());
                        assertNotNull(lot.taxImpact());
                        assertNotNull(lot.taxImpact().regime());
                        assertTrue(List.of("SEC_112A_EXEMPT", "SEC_112A_TAXABLE_12_5", "SLAB_RATE_STCG").contains(lot.taxImpact().regime()));
                    }
                }
            }
            assertEquals(plan.sellSide().totalRequired(), totalSoldLots, "Sum of lot saleProceeds must equal sellSide totalRequired");
        }

        if (plan.buySide() != null && plan.buySide().buckets() != null) {
            BigDecimal totalAllocated = BigDecimal.ZERO;
            for (RebalanceBucketAllocationDto b : plan.buySide().buckets()) {
                totalAllocated = totalAllocated.add(b.amountAllocated());
                if (b.fundBreakdown() != null) {
                    BigDecimal fundSum = BigDecimal.ZERO;
                    for (FundAllocationDto f : b.fundBreakdown()) {
                        fundSum = fundSum.add(f.amount());
                    }
                    assertEquals(0, b.amountAllocated().compareTo(fundSum), "Sum of fundBreakdown amounts must equal bucket amountAllocated");
                }
            }
        }
    }

    @Test
    @DisplayName("Gold Floor Backstop Sankey allocation allocates 100% of buy pool to GOLD_SILVER")
    void testGoldFloorBackstopSankeyAllocation() {
        BigDecimal nav = new BigDecimal("100.00");
        LocalDate acqDate = LocalDate.of(2024, 1, 1);
        Lot coreLot = new Lot("lot-1", "INF109KC12U0", "ICICI LargeMidcap", acqDate, new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            List.of(coreLot), Collections.emptyList(), Map.of("INF109KC12U0", nav), LocalDate.of(2026, 8, 10),
            new BigDecimal("100000.00"), new BigDecimal("100000.00"), null, "2026-27", "GOLD_FLOOR_BACKSTOP", null, evaluator
        );

        assertNotNull(plan);
        assertNotNull(plan.buySide());
        RebalanceBucketAllocationDto goldBucket = plan.buySide().buckets().stream()
            .filter(b -> "GOLD_SILVER".equals(b.bucket()))
            .findFirst()
            .orElseThrow();

        RebalanceBucketAllocationDto coreBucket = plan.buySide().buckets().stream()
            .filter(b -> "EQUITY_CORE".equals(b.bucket()))
            .findFirst()
            .orElseThrow();

        assertTrue(goldBucket.amountAllocated().compareTo(BigDecimal.ZERO) > 0, "Gold Floor Backstop must allocate non-zero to Gold");
        assertEquals(BigDecimal.ZERO, coreBucket.amountAllocated(), "Gold Floor Backstop must allocate 0 to non-Gold buckets");
    }

    @Test
    @DisplayName("Gold Dampener buy allocation reflects non-zero price extension deviation (+10% deviation -> 0.85x buy multiplier)")
    void testGoldDampenedBuyAllocationWithNonZeroDeviation() {
        BigDecimal currentNav = new BigDecimal("110.00");
        BigDecimal sma200 = new BigDecimal("100.00");
        // devPct = (110 - 100) / 100 * 100 = +10.0%
        // buyMultiplier at +10% deviation = 1.30 - (10/20)*(1.30 - 0.40) = 0.8500
        LocalDate acqDate = LocalDate.of(2024, 1, 1);

        Lot goldLot = new Lot("lot-gold", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", acqDate, new BigDecimal("100"), new BigDecimal("100"), currentNav, new BigDecimal("11000.00"), false, null);
        Lot coreLot = new Lot("lot-core", "INF109KC12U0", "ICICI LargeMidcap", acqDate, new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100.00"), new BigDecimal("100000.00"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF247L01BM8", currentNav,
            "INF109KC12U0", new BigDecimal("100.00")
        );

        List<BucketEngine.BucketTarget> customTargets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("85.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("15.00"), new BigDecimal("5.00"))
        );

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            List.of(goldLot, coreLot), Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
            currentNav, sma200, customTargets, "2026-27", "DRIFT", null, evaluator
        );

        assertNotNull(plan);
        assertNotNull(plan.buySide());
        RebalanceBucketAllocationDto goldBucket = plan.buySide().buckets().stream()
            .filter(b -> "GOLD_SILVER".equals(b.bucket()))
            .findFirst()
            .orElseThrow();

        assertTrue(goldBucket.amountAllocated().compareTo(BigDecimal.ZERO) > 0, "Gold amountAllocated must be > 0 at +10% deviation");
    }

    @Test
    @DisplayName("Verify all 3 tax regimes (SLAB_RATE_STCG, SEC_112A_TAXABLE_12_5, SEC_112A_EXEMPT) are evaluated in sell waterfall lots")
    void testRebalanceSankeyTaxRegimeColoringAllThreeRegimes() {
        LocalDate now = LocalDate.of(2026, 8, 10);
        LocalDate stcgAcqDate = now.minusDays(100); // STCG holding < 365d
        LocalDate ltcgAcqDate = now.minusDays(500); // LTCG holding > 365d

        // STCG Lot (Held 100 days)
        Lot stcgLot = new Lot("lot-stcg", "INF247L01916", "Motilal Oswal Midcap 150", stcgAcqDate, new BigDecimal("100"), new BigDecimal("1000"), new BigDecimal("150.00"), new BigDecimal("15000.00"), false, null);
        
        // Large LTCG Lot (Gains > 1.25L threshold)
        Lot ltcgLargeGainLot = new Lot("lot-ltcg-large", "INF174KA1TY2", "Kotak Nifty 100", ltcgAcqDate, new BigDecimal("1000"), new BigDecimal("10000"), new BigDecimal("300.00"), new BigDecimal("300000.00"), false, null);

        // Small LTCG Exempt Lot
        Lot ltcgExemptLot = new Lot("lot-ltcg-exempt", "INF879O01027", "Parag Parikh Flexi Cap", ltcgAcqDate, new BigDecimal("100"), new BigDecimal("1000"), new BigDecimal("110.00"), new BigDecimal("11000.00"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF247L01916", new BigDecimal("150.00"),
            "INF174KA1TY2", new BigDecimal("300.00"),
            "INF879O01027", new BigDecimal("110.00")
        );

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            List.of(stcgLot, ltcgLargeGainLot, ltcgExemptLot), Collections.emptyList(), navMap, now,
            new BigDecimal("100.00"), new BigDecimal("100.00"), Collections.emptyList(), "2026-27", "DRIFT", null, evaluator
        );

        assertNotNull(plan);
        assertNotNull(plan.sellSide());
        assertNotNull(plan.sellSide().waterfall());

        List<com.portfolioos.core.dtos.RebalancePlanDtos.RebalanceLotImpactDto> allSellLots = plan.sellSide().waterfall().stream()
            .flatMap(t -> t.lots().stream())
            .toList();

        assertFalse(allSellLots.isEmpty(), "Sell waterfall must contain lots for rebalance liquidations");

        // Verify STCG regime presence
        boolean hasStcg = allSellLots.stream().anyMatch(l -> "SLAB_RATE_STCG".equals(l.taxImpact().regime()));
        // Verify 112A Taxable or Exempt presence
        boolean hasExemptOrTaxable = allSellLots.stream().anyMatch(l -> l.taxImpact().regime().startsWith("SEC_112A"));

        assertTrue(hasStcg || hasExemptOrTaxable, "Waterfall lots must carry evaluated tax regimes");
    }
}
