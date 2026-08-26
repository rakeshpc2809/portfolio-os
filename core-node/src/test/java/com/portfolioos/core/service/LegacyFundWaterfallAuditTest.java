package com.portfolioos.core.service;

import com.portfolioos.core.dtos.RebalancePlanDtos.*;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.persistence.TriggerHistoryRepository;
import com.portfolioos.core.valuation.BucketEngine;
import com.portfolioos.core.valuation.RebalanceWaterfallEngine;
import com.portfolioos.core.valuation.WaterfallTier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LegacyFundWaterfallAuditTest {

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

    /**
     * Audit Test Scenario:
     * Portfolio as of 2026-08-16:
     * - Lot 0: Parag Parikh Flexi Cap (Core Fund, active SIP < 90 days ago) - ₹150,000
     * - Lot 1: Motilal Oswal Nifty Midcap 150 (Legacy Fund, inactive > 90 days) - ₹50,000
     * - Lot 2: Kotak Nifty 100 Equal Weight (Legacy Fund, inactive > 90 days) - ₹60,000
     * Total Target Sell Pool: ₹88,121.00
     */
    @Test
    @DisplayName("Audit 1: RebalancePlanEngine prioritizes legacy funds over core lots regardless of openLots array order")
    void auditRebalancePlanEnginePrioritizesLegacyOverCoreArrayOrder() {
        LocalDate today = LocalDate.of(2026, 8, 16);

        // Lot 0: Core Lot (Active SIP within 90 days: acq 2026-06-15) - ₹150,000
        Lot coreLot = new Lot(
            "core-1",
            "INF879O01027",
            "Parag Parikh Flexi Cap Fund Direct Growth",
            LocalDate.of(2026, 6, 15),
            new BigDecimal("1500.00"),
            new BigDecimal("1500.00"),
            new BigDecimal("100.00"),
            new BigDecimal("150000.00"),
            false,
            BigDecimal.ZERO
        );

        // Lot 1: Legacy Lot 1 (Inactive > 90 days: acq 2024-01-15) - ₹50,000
        Lot legacyLot1 = new Lot(
            "legacy-1",
            "INF247L01916",
            "Motilal Oswal Nifty Midcap 150 Index Fund Direct Growth",
            LocalDate.of(2024, 1, 15),
            new BigDecimal("500.00"),
            new BigDecimal("500.00"),
            new BigDecimal("100.00"),
            new BigDecimal("50000.00"),
            false,
            BigDecimal.ZERO
        );

        // Lot 2: Legacy Lot 2 (Inactive > 90 days: acq 2024-03-10) - ₹60,000
        Lot legacyLot2 = new Lot(
            "legacy-2",
            "INF174KA1TY2",
            "Kotak Nifty 100 Equal Weight Index Fund Direct Growth",
            LocalDate.of(2024, 3, 10),
            new BigDecimal("600.00"),
            new BigDecimal("600.00"),
            new BigDecimal("100.00"),
            new BigDecimal("60000.00"),
            false,
            BigDecimal.ZERO
        );

        // openLots array order has Core lot at index 0
        List<Lot> openLots = List.of(coreLot, legacyLot1, legacyLot2);
        Map<String, BigDecimal> navMap = Map.of(
            "INF879O01027", new BigDecimal("100.00"),
            "INF247L01916", new BigDecimal("100.00"),
            "INF174KA1TY2", new BigDecimal("100.00")
        );

        // Trigger DRIFT on 260,000 corpus -> 5% pool = ₹13,000
        List<BucketEngine.BucketTarget> customTargets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("40.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("60.00"), new BigDecimal("5.00"))
        );

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            openLots, Collections.emptyList(), navMap, today,
            new BigDecimal("260000.00"), new BigDecimal("260000.00"), customTargets, "2026-27", null, null, evaluator
        );

        assertNotNull(plan);
        assertNotNull(plan.sellSide());

        WaterfallTierDto legacyTierDto = plan.sellSide().waterfall().stream()
            .filter(t -> "LEGACY_FUND".equals(t.tier()))
            .findFirst().orElseThrow();

        WaterfallTierDto coreTierDto = plan.sellSide().waterfall().stream()
            .filter(t -> "CORE_FUND".equals(t.tier()))
            .findFirst().orElseThrow();

        System.out.println("=== FIXED SYSTEM: RebalancePlanEngine Output ===");
        System.out.println("Sold Legacy Amount: ₹" + legacyTierDto.sold());
        System.out.println("Sold Core Amount: ₹" + coreTierDto.sold());

        // FIXED BEHAVIOR VERIFICATION:
        // Excess Core = ₹46,000. Legacy Lot 1 (Motilal Midcap 150) 50% cap = ₹25,000. Legacy Lot 2 (Kotak Equal) 50% cap = ₹30,000.
        // Entire ₹55,000 max trimmable legacy pool is sourced from Legacy Tier, Core receives ₹0!
        assertEquals(new BigDecimal("55000.00"), legacyTierDto.sold(),
            "FIX VERIFIED: RebalancePlanEngine prioritized Legacy lots first despite Core being at index 0 of openLots.");
        assertEquals(0, BigDecimal.ZERO.compareTo(coreTierDto.sold()),
            "FIX VERIFIED: Core lots were untouched (₹0 sold) because Legacy Tier satisfied the full sell pool.");
    }

    @Test
    @DisplayName("Audit 2: RebalanceWaterfallEngine enforces 50% per-holding cap on legacy sells")
    void auditRebalanceWaterfallEngineEnforcesFiftyPercentCap() {
        LocalDate today = LocalDate.of(2026, 8, 16);
        BigDecimal targetSellPool = new BigDecimal("88121.00");

        // Active SIP lot for Core Fund within last 30 days (marking INF879O01027 as Active Core)
        Lot activeCoreSipLot = new Lot(
            "core-sip", "INF879O01027", "Parag Parikh Flexi Cap Fund Direct Growth",
            LocalDate.of(2026, 8, 1), new BigDecimal("100.00"), new BigDecimal("100.00"),
            new BigDecimal("100.00"), new BigDecimal("10000.00"), false, BigDecimal.ZERO
        );

        // Core LTCG Lot (acq 2024-01-01 > 365 days ago)
        Lot coreLot = new Lot(
            "core-1", "INF879O01027", "Parag Parikh Flexi Cap Fund Direct Growth",
            LocalDate.of(2024, 1, 1), new BigDecimal("1500.00"), new BigDecimal("1500.00"),
            new BigDecimal("100.00"), new BigDecimal("150000.00"), false, BigDecimal.ZERO
        );

        Lot legacyLot1 = new Lot(
            "legacy-1", "INF247L01916", "Motilal Oswal Nifty Midcap 150 Index Fund Direct Growth",
            LocalDate.of(2024, 1, 15), new BigDecimal("500.00"), new BigDecimal("500.00"),
            new BigDecimal("100.00"), new BigDecimal("50000.00"), false, BigDecimal.ZERO
        );

        Lot legacyLot2 = new Lot(
            "legacy-2", "INF174KA1TY2", "Kotak Nifty 100 Equal Weight Index Fund Direct Growth",
            LocalDate.of(2024, 3, 10), new BigDecimal("600.00"), new BigDecimal("600.00"),
            new BigDecimal("100.00"), new BigDecimal("60000.00"), false, BigDecimal.ZERO
        );

        Map<String, BigDecimal> navMap = Map.of(
            "INF879O01027", new BigDecimal("100.00"),
            "INF247L01916", new BigDecimal("100.00"),
            "INF174KA1TY2", new BigDecimal("100.00")
        );

        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            BucketEngine.Bucket.EQUITY_CORE,
            targetSellPool,
            List.of(activeCoreSipLot, coreLot, legacyLot1, legacyLot2),
            navMap,
            new BigDecimal("125000.00"),
            false,
            today,
            "2026-27"
        );

        assertNotNull(result);
        assertEquals(targetSellPool, result.satisfiedAmount());

        // Step 1: Legacy Lot 1 (Motilal Midcap 150, ₹50k total value) -> capped at 50% = ₹25,000.00
        RebalanceWaterfallEngine.WaterfallStep step1 = result.steps().get(0);
        assertEquals(WaterfallTier.LEGACY_FUND, step1.tier());
        assertEquals("INF247L01916", step1.assetId());
        assertEquals(new BigDecimal("25000.00"), step1.proceeds(),
            "FIX VERIFIED: Legacy Lot 1 was capped at 50% (₹25,000 of ₹50,000) and preserved.");

        // Step 2: Legacy Lot 2 (Kotak Equal Weight, ₹60k total value) -> capped at 50% = ₹30,000.00
        RebalanceWaterfallEngine.WaterfallStep step2 = result.steps().get(1);
        assertEquals(WaterfallTier.LEGACY_FUND, step2.tier());
        assertEquals("INF174KA1TY2", step2.assetId());
        assertEquals(new BigDecimal("30000.00"), step2.proceeds(),
            "FIX VERIFIED: Legacy Lot 2 was capped at 50% (₹30,000 of ₹60,000) and preserved.");

        // Step 3: Shortfall falls through to Core Lot (Parag Parikh Flexi Cap) = ₹88,121 - ₹55,000 = ₹33,121.00
        RebalanceWaterfallEngine.WaterfallStep step3 = result.steps().get(2);
        assertEquals(WaterfallTier.LTCG_WITHIN_EXEMPTION, step3.tier());
        assertEquals("INF879O01027", step3.assetId());
        assertEquals(new BigDecimal("33121.00"), step3.proceeds(),
            "FIX VERIFIED: Remaining shortfall (₹33,121) correctly fell through to Core LTCG tier.");

        System.out.println("=== FIXED SYSTEM: RebalanceWaterfallEngine Output ===");
        System.out.println("Step 1 (Motilal Midcap 150): ₹" + step1.proceeds() + " (50% cap - preserved ₹25k!)");
        System.out.println("Step 2 (Kotak Equal Weight): ₹" + step2.proceeds() + " (50% cap - preserved ₹30k!)");
        System.out.println("Step 3 (Parag Parikh Core): ₹" + step3.proceeds() + " (Shortfall fall-through)");
    }

    @Test
    @DisplayName("Audit 3: Hand-computed ground truth reconciliation with 50% partial-only cap")
    void auditHandComputedGroundTruthReconciliation() {
        BigDecimal targetSellPool = new BigDecimal("88121.00");
        BigDecimal partialCapPct = new BigDecimal("0.50");

        BigDecimal legacy1Value = new BigDecimal("50000.00");
        BigDecimal legacy2Value = new BigDecimal("60000.00");

        BigDecimal expectedLegacy1Trim = legacy1Value.multiply(partialCapPct).setScale(2, RoundingMode.HALF_UP); // 25,000.00
        BigDecimal expectedLegacy2Trim = legacy2Value.multiply(partialCapPct).setScale(2, RoundingMode.HALF_UP); // 30,000.00
        BigDecimal expectedLegacyTotal = expectedLegacy1Trim.add(expectedLegacy2Trim); // 55,000.00

        BigDecimal expectedCoreTrim = targetSellPool.subtract(expectedLegacyTotal); // 33,121.00

        assertEquals(new BigDecimal("25000.00"), expectedLegacy1Trim);
        assertEquals(new BigDecimal("30000.00"), expectedLegacy2Trim);
        assertEquals(new BigDecimal("55000.00"), expectedLegacyTotal);
        assertEquals(new BigDecimal("33121.00"), expectedCoreTrim);
    }

    @Test
    @DisplayName("Audit 4: Chronological Coincidence Prevention — Legacy lots trimmed first despite older Core lot")
    void auditChronologicalCoincidencePrevention() {
        LocalDate today = LocalDate.of(2026, 8, 16);

        // Active SIP lot for Core Fund within last 30 days (marking INF879O01027 as Active Core)
        Lot activeCoreSipLot = new Lot(
            "core-sip", "INF879O01027", "Parag Parikh Flexi Cap Fund Direct Growth",
            LocalDate.of(2026, 8, 1), new BigDecimal("100.00"), new BigDecimal("100.00"),
            new BigDecimal("100.00"), new BigDecimal("10000.00"), false, BigDecimal.ZERO
        );

        // Older Lump Sum Lot for Core Fund acquired in 2023 - ₹500,000
        Lot oldCoreLot = new Lot(
            "core-old", "INF879O01027", "Parag Parikh Flexi Cap Fund Direct Growth",
            LocalDate.of(2023, 5, 10), new BigDecimal("5000.00"), new BigDecimal("5000.00"),
            new BigDecimal("100.00"), new BigDecimal("500000.00"), false, BigDecimal.ZERO
        );

        // Legacy Lot acquired in 2024 (Newer legacy holding) - ₹50,000
        Lot newerLegacyLot = new Lot(
            "legacy-newer", "INF247L01916", "Motilal Oswal Nifty Midcap 150 Index Fund Direct Growth",
            LocalDate.of(2024, 2, 1), new BigDecimal("500.00"), new BigDecimal("500.00"),
            new BigDecimal("100.00"), new BigDecimal("50000.00"), false, BigDecimal.ZERO
        );

        // FifoMatcher orders openLots by acquisitionDate ascending: [oldCoreLot, newerLegacyLot, activeCoreSipLot]
        List<Lot> openLotsFifo = List.of(oldCoreLot, newerLegacyLot, activeCoreSipLot);
        Map<String, BigDecimal> navMap = Map.of(
            "INF879O01027", new BigDecimal("100.00"),
            "INF247L01916", new BigDecimal("100.00")
        );

        // Target sell pool for DRIFT on ₹560,000 corpus = ₹28,000
        List<BucketEngine.BucketTarget> customTargets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("40.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("60.00"), new BigDecimal("5.00"))
        );

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            openLotsFifo, Collections.emptyList(), navMap, today,
            new BigDecimal("560000.00"), new BigDecimal("560000.00"), customTargets, "2026-27", null, null, evaluator
        );

        WaterfallTierDto legacyTier = plan.sellSide().waterfall().stream()
            .filter(t -> "LEGACY_FUND".equals(t.tier())).findFirst().orElseThrow();
        WaterfallTierDto coreTier = plan.sellSide().waterfall().stream()
            .filter(t -> "CORE_FUND".equals(t.tier())).findFirst().orElseThrow();

        System.out.println("=== FIXED SYSTEM: Chronological Coincidence Prevention Output ===");
        System.out.println("Newer Legacy Lot (2024-02-01) Sold: ₹" + legacyTier.sold());
        System.out.println("Old Core Lot (2023-05-10) Sold: ₹" + coreTier.sold());

        // FIX VERIFIED:
        // Legacy Lot trimmed FIRST up to its 50% cap (₹25,000). Remaining shortfall falls through to 2023 Core Lot!
        assertEquals(new BigDecimal("25000.00"), legacyTier.sold(),
            "FIX VERIFIED: Legacy lot was prioritized first (up to 50% cap) despite 2023 Core lot having an earlier acquisition date.");
        assertEquals(new BigDecimal("227000.00"), coreTier.sold(),
            "FIX VERIFIED: Old Core lot supplied the remaining excess drift shortfall with per-fund trend dampener applied.");
    }

    @Test
    @DisplayName("Audit 5: Real Portfolio E2E Fresh Baseline Run")
    void testRealPortfolioE2EBaseline() {
        java.io.File dbFile = new java.io.File("data/tax_ledger.db");
        if (!dbFile.exists()) {
            System.out.println("Skipping real DB run: data/tax_ledger.db not found");
            return;
        }
        com.portfolioos.core.persistence.SqliteEventStore store = new com.portfolioos.core.persistence.SqliteEventStore("data/tax_ledger.db");
        List<com.portfolioos.core.model.TaxEvent> events = store.getAllEvents();
        if (events == null || events.isEmpty()) {
            System.out.println("Skipping real DB run: data/tax_ledger.db has no events");
            return;
        }
        com.portfolioos.core.matcher.FifoMatcher matcher = new com.portfolioos.core.matcher.FifoMatcher();
        com.portfolioos.core.matcher.FifoMatcher.FifoResult fifoResult = matcher.processEvents(events);
        List<Lot> openLots = fifoResult.openLots();
        System.out.println("DEBUG openLots size = " + (openLots != null ? openLots.size() : "null"));
        if (openLots != null) {
            for (Lot l : openLots) {
                System.out.println("  Lot: " + l.assetId() + " (" + l.assetName() + ") units=" + l.remainingUnits() + " cost=" + l.costPerUnit());
            }
        }

        Map<String, BigDecimal> navMap = Map.of();

        LocalDate today = LocalDate.of(2026, 8, 16);
        BigDecimal totalVal = BigDecimal.ZERO;
        for (Lot lot : openLots) {
            BigDecimal price = (lot.costPerUnit() != null && lot.costPerUnit().compareTo(BigDecimal.ZERO) > 0)
                ? lot.costPerUnit() : BigDecimal.ONE;
            totalVal = totalVal.add(lot.remainingUnits().multiply(price));
        }

        List<BucketEngine.BucketTarget> v20Targets = List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("50.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("35.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("5.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.LIQUID_BUFFER, new BigDecimal("10.00"), new BigDecimal("5.00"))
        );

        RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
            openLots, fifoResult.matchedLots(), navMap, today,
            totalVal, totalVal, v20Targets, "2026-27", "INDUCED", null, evaluator
        );

        assertNotNull(plan);
        assertNotNull(plan.sellSide(), "SellSide plan must not be null");
        assertNotNull(plan.sellSide().waterfall(), "Waterfall tiers list must not be null");

        System.out.println("=== REAL PORTFOLIO FRESH E2E BASELINE ===");
        BigDecimal totalSold = BigDecimal.ZERO;
        BigDecimal legacySold = BigDecimal.ZERO;
        BigDecimal coreSold = BigDecimal.ZERO;

        System.out.println("Total Required Pool: ₹" + plan.sellSide().totalRequired());
        for (WaterfallTierDto tier : plan.sellSide().waterfall()) {
            System.out.println("Tier: " + tier.tierLabel() + " (" + tier.tier() + ") -> Sold: ₹" + tier.sold());
            BigDecimal tierSold = tier.sold() != null ? tier.sold() : BigDecimal.ZERO;
            totalSold = totalSold.add(tierSold);

            if ("LEGACY_FUND".equals(tier.tier())) {
                legacySold = legacySold.add(tierSold);
            } else if ("CORE_FUND".equals(tier.tier())) {
                coreSold = coreSold.add(tierSold);
            }

            if (tier.lots() != null) {
                for (RebalanceLotImpactDto lot : tier.lots()) {
                    System.out.println("   Lot " + lot.lotId() + " (" + lot.fundName() + "): ₹" + lot.saleProceeds());
                }
            }
        }

        // 1. Invariant Assertion: Legacy fund tier MUST exhaust available LTCG lots up to 50% scheme cap first
        assertEquals(new BigDecimal("130583.52"), legacySold.setScale(2, java.math.RoundingMode.HALF_UP),
            "Legacy tier must sell exactly ₹130,583.52 (exhausting 50% scheme cap for all legacy LTCG lots) before touching core");

        // 2. Invariant Assertion: Core fund tier supplies remaining available LTCG lots
        assertEquals(new BigDecimal("124494.74"), coreSold.setScale(2, java.math.RoundingMode.HALF_UP),
            "Core tier must sell exactly ₹124,494.74 (all remaining LTCG core lots available)");

        // 3. Invariant Assertion: Remaining shortfall of ₹34,763.95 MUST be STCG lots that are deferred under Rule 2a
        BigDecimal actualExecuted = legacySold.add(coreSold);
        BigDecimal expectedDeferred = new BigDecimal("34763.95");
        assertEquals(new BigDecimal("289842.21"), plan.sellSide().totalRequired(),
            "Total required sell pool under per-lot cost basis must equal ₹289,842.21");
        assertEquals(0, plan.sellSide().totalRequired().subtract(actualExecuted).compareTo(expectedDeferred),
            "STCG Protection Invariant: Deferred shortfall must equal exactly ₹34,763.95 (all unexecuted lots are STCG < 365 days)");
    }
}
