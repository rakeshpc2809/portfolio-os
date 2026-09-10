package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RebalanceEngineTest {

    @Test
    @DisplayName("RebalanceEngine calculates rebalance preview with valid live NAV")
    void testCalculateRebalancePreviewSuccess() {
        LocalDate acqDate = LocalDate.now().minusDays(400); // LTCG
        Lot lot = new Lot("L1", "INF879O01027", "Parag Parikh Flexi Cap", acqDate,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF879O01027", new BigDecimal("100"));
        RebalanceEngine.RebalancePreviewResult result = RebalanceEngine.calculateRebalancePreview(
            List.of(lot), navMap, new BigDecimal("5000"), new BigDecimal("125000"), "2026-27"
        );

        assertNotNull(result);
        assertEquals(0, new BigDecimal("5000").compareTo(result.targetRedemptionAmount()));
        assertEquals(0, new BigDecimal("5000").compareTo(result.actualRedemptionAmount()));
        assertEquals(1, result.selectedLots().size());
        assertEquals("INF879O01027", result.selectedLots().get(0).assetId());
        assertEquals(LiquidationTier.TIER_2_LTCG_EXEMPT_HEADROOM, result.selectedLots().get(0).tier());
    }

    @Test
    @DisplayName("Fail-Loud Invariant: RebalanceEngine throws IllegalStateException on missing NAV")
    void testRebalanceEngineThrowsOnMissingNav() {
        LocalDate acqDate = LocalDate.now().minusDays(400);
        Lot lot = new Lot("L1", "INF879O01027", "Parag Parikh Flexi Cap", acqDate,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Map<String, BigDecimal> emptyNavMap = Map.of();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            RebalanceEngine.calculateRebalancePreview(
                List.of(lot), emptyNavMap, new BigDecimal("5000"), new BigDecimal("125000"), "2026-27"
            )
        );
        assertTrue(ex.getMessage().contains("CRITICAL VALUATION ERROR"));
        assertTrue(ex.getMessage().contains("INF879O01027"));
    }

    @Test
    @DisplayName("6-Tier Waterfall: Strictly orders lots from Loss -> 112A Exempt -> Grandfathered Debt -> Taxable 112A -> Equity STCG -> 50AA Debt")
    void testSixTierLiquidationWaterfallOrdering() {
        LocalDate today = LocalDate.now();

        // 1. Loss Lot: Cost 100, NAV 80 (Loss = ₹2,000)
        Lot lossLot = new Lot("LOT_LOSS", "INF109K01EQ1", "ICICI Bluechip Equity", today.minusDays(500),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null);

        // 2. Equity LTCG (Within Headroom): Cost 50, NAV 100 (Gain = ₹5,000)
        Lot ltcgExemptLot = new Lot("LOT_LTCG_1", "INF109K01EQ2", "HDFC Midcap Equity", today.minusDays(400),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        // 3. Grandfathered Debt (Pre-Apr 2023, >24m): Cost 100, NAV 120 (Gain = ₹2,000)
        Lot gfDebtLot = new Lot("LOT_GF_DEBT", "INF109K01DB1", "SBI Short Term Debt Fund", LocalDate.of(2023, 1, 15),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null);

        // 4. Equity STCG: Cost 80, NAV 100 (Gain = ₹2,000, Holding = 100 days)
        Lot equityStcgLot = new Lot("LOT_EQ_STCG", "INF109K01EQ3", "Kotak Flexi Cap Fund", today.minusDays(100),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("80"), new BigDecimal("8000"), false, null);

        // 5. Section 50AA Debt (Post-Apr 2023): Cost 100, NAV 110 (Gain = ₹1,000)
        Lot sec50aaDebtLot = new Lot("LOT_50AA_DEBT", "INF109K01DB2", "Axis Corporate Debt Fund", LocalDate.of(2023, 8, 1),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109K01EQ1", new BigDecimal("80"),
            "INF109K01EQ2", new BigDecimal("100"),
            "INF109K01DB1", new BigDecimal("120"),
            "INF109K01EQ3", new BigDecimal("100"),
            "INF109K01DB2", new BigDecimal("110")
        );

        // Request targetAmount that spans across all 5 lots:
        // Loss lot value = 8,000
        // LTCG lot value = 10,000
        // GF Debt lot value = 12,000
        // STCG lot value = 10,000
        // 50AA Debt lot value = 11,000
        // Total = 51,000
        BigDecimal target = new BigDecimal("51000");
        BigDecimal exemptionRemaining = new BigDecimal("125000");

        RebalanceEngine.RebalancePreviewResult result = RebalanceEngine.calculateRebalancePreview(
            List.of(sec50aaDebtLot, equityStcgLot, gfDebtLot, ltcgExemptLot, lossLot), // Shuffle order intentionally
            navMap, target, exemptionRemaining, "2026-27"
        );

        assertNotNull(result);
        assertEquals(0, target.compareTo(result.actualRedemptionAmount()));
        assertEquals(5, result.selectedLots().size());

        // Assert strictly ordered tiers
        assertEquals("LOT_LOSS", result.selectedLots().get(0).lotId());
        assertEquals(LiquidationTier.TIER_1_LOSS_HARVEST, result.selectedLots().get(0).tier());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.selectedLots().get(0).estimatedTaxDrag()));

        assertEquals("LOT_LTCG_1", result.selectedLots().get(1).lotId());
        assertEquals(LiquidationTier.TIER_2_LTCG_EXEMPT_HEADROOM, result.selectedLots().get(1).tier());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.selectedLots().get(1).estimatedTaxDrag()));

        assertEquals("LOT_GF_DEBT", result.selectedLots().get(2).lotId());
        assertEquals(LiquidationTier.TIER_3_GRANDFATHERED_DEBT_LTCG, result.selectedLots().get(2).tier());
        // 2,000 gain * 12.5% = 250 tax drag
        assertEquals(0, new BigDecimal("250.00").compareTo(result.selectedLots().get(2).estimatedTaxDrag()));

        assertEquals("LOT_EQ_STCG", result.selectedLots().get(3).lotId());
        assertEquals(LiquidationTier.TIER_5_EQUITY_STCG, result.selectedLots().get(3).tier());
        // 2,000 gain * 20.0% = 400 tax drag
        assertEquals(0, new BigDecimal("400.00").compareTo(result.selectedLots().get(3).estimatedTaxDrag()));

        assertEquals("LOT_50AA_DEBT", result.selectedLots().get(4).lotId());
        assertEquals(LiquidationTier.TIER_6_SPECIFIED_50AA_DEBT, result.selectedLots().get(4).tier());
        // 1,000 gain * 30.0% slab = 300 tax drag
        assertEquals(0, new BigDecimal("300.00").compareTo(result.selectedLots().get(4).estimatedTaxDrag()));
    }

    @Test
    @DisplayName("Boundary Splitting: Lot straddling remaining exemption boundary cleanly splits into Tier 2 and Tier 4")
    void testExemptionHeadroomBoundarySplitting() {
        LocalDate today = LocalDate.now();

        // Single lot: 200 units at cost 50, NAV 100 -> Total proceeds = 20,000, Total gain = 10,000
        Lot ltcgLot = new Lot("LOT_SPLIT", "INF109K01EQ4", "Nippon Large Cap", today.minusDays(450),
            new BigDecimal("200"), new BigDecimal("200"), new BigDecimal("50"), new BigDecimal("10000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF109K01EQ4", new BigDecimal("100"));

        // Only ₹4,000 headroom remains.
        // Target redemption: ₹10,000 (100 units sold, total gain = ₹5,000)
        // Since gain ₹5,000 > headroom ₹4,000:
        // - Tier 2 slice: gain ₹4,000, units = 80, proceeds = ₹8,000, cost = ₹4,000, tax drag = ₹0.00
        // - Tier 4 slice: gain ₹1,000, units = 20, proceeds = ₹2,000, cost = ₹1,000, tax drag = ₹1,000 * 12.5% = ₹125.00
        BigDecimal target = new BigDecimal("10000");
        BigDecimal headroom = new BigDecimal("4000");

        RebalanceEngine.RebalancePreviewResult result = RebalanceEngine.calculateRebalancePreview(
            List.of(ltcgLot), navMap, target, headroom, "2026-27"
        );

        assertNotNull(result);
        assertEquals(0, target.compareTo(result.actualRedemptionAmount()));
        assertEquals(2, result.selectedLots().size());

        // Slice 1: Tier 2 (Exempt)
        RebalanceEngine.RebalanceLotSelection slice1 = result.selectedLots().get(0);
        assertEquals(LiquidationTier.TIER_2_LTCG_EXEMPT_HEADROOM, slice1.tier());
        assertEquals(0, new BigDecimal("80.0000").compareTo(slice1.unitsToSell()));
        assertEquals(0, new BigDecimal("8000.00").compareTo(slice1.redemptionProceeds()));
        assertEquals(0, new BigDecimal("4000.00").compareTo(slice1.estimatedGain()));
        assertEquals(0, BigDecimal.ZERO.compareTo(slice1.estimatedTaxDrag()));

        // Slice 2: Tier 4 (Taxable Section 112A LTCG)
        RebalanceEngine.RebalanceLotSelection slice2 = result.selectedLots().get(1);
        assertEquals(LiquidationTier.TIER_4_TAXABLE_112A_LTCG, slice2.tier());
        assertEquals(0, new BigDecimal("20.0000").compareTo(slice2.unitsToSell()));
        assertEquals(0, new BigDecimal("2000.00").compareTo(slice2.redemptionProceeds()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(slice2.estimatedGain()));
        assertEquals(0, new BigDecimal("125.00").compareTo(slice2.estimatedTaxDrag()));

        // Exact conservation check across slices
        assertEquals(0, target.compareTo(slice1.redemptionProceeds().add(slice2.redemptionProceeds())));
        assertEquals(0, new BigDecimal("100.0000").compareTo(slice1.unitsToSell().add(slice2.unitsToSell())));
        assertEquals(0, new BigDecimal("5000.00").compareTo(slice1.estimatedGain().add(slice2.estimatedGain())));
        assertEquals(0, new BigDecimal("4000.00").compareTo(result.ltcgExemptionHarvested()));
        assertEquals(0, new BigDecimal("125.00").compareTo(result.totalTaxDrag()));
    }

    @Test
    @DisplayName("Grandfathered Debt Distinction: Pre-April 2023 debt gets flat 12.5% vs Post-April 2023 gets 30% slab rate")
    void testGrandfatheredDebtDistinctionFrom50aa() {
        LocalDate today = LocalDate.now();

        Lot gfDebt = new Lot("GF_1", "INF109K01GF1", "ICICI Debt Gilt", LocalDate.of(2023, 2, 1),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null);

        Lot sec50aaDebt = new Lot("50AA_1", "INF109K01AA1", "ICICI Debt Liquid", LocalDate.of(2023, 5, 1),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109K01GF1", new BigDecimal("150"), // gain = 50 per unit -> total gain = 5,000 on 100 units
            "INF109K01AA1", new BigDecimal("150")  // gain = 50 per unit -> total gain = 5,000 on 100 units
        );

        // Liquidate GF Debt only (target 15,000)
        RebalanceEngine.RebalancePreviewResult resGf = RebalanceEngine.calculateRebalancePreview(
            List.of(gfDebt), navMap, new BigDecimal("15000"), BigDecimal.ZERO, "2026-27"
        );
        assertEquals(LiquidationTier.TIER_3_GRANDFATHERED_DEBT_LTCG, resGf.selectedLots().get(0).tier());
        // 5,000 gain * 12.5% = 625.00
        assertEquals(0, new BigDecimal("625.00").compareTo(resGf.totalTaxDrag()));

        // Liquidate 50AA Debt only (target 15,000)
        RebalanceEngine.RebalancePreviewResult res50aa = RebalanceEngine.calculateRebalancePreview(
            List.of(sec50aaDebt), navMap, new BigDecimal("15000"), BigDecimal.ZERO, "2026-27"
        );
        assertEquals(LiquidationTier.TIER_6_SPECIFIED_50AA_DEBT, res50aa.selectedLots().get(0).tier());
        // 5,000 gain * 30.0% = 1500.00
        assertEquals(0, new BigDecimal("1500.00").compareTo(res50aa.totalTaxDrag()));
    }

    @Test
    @DisplayName("Reservation Contract: reservedExemption explicitly nets out available headroom")
    void testReservedExemptionNetsOutAgainstHeadroom() {
        LocalDate today = LocalDate.now();
        Lot lot = new Lot("L_RES", "INF109K01EQ5", "Mirae Asset Large Cap", today.minusDays(500),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF109K01EQ5", new BigDecimal("100")); // Gain = 5,000

        BigDecimal totalRemaining = new BigDecimal("125000");
        BigDecimal reservedForHarvest = new BigDecimal("122000"); // Only 3,000 effective headroom left!

        RebalanceEngine.RebalancePreviewResult result = RebalanceEngine.calculateRebalancePreview(
            List.of(lot), navMap, new BigDecimal("10000"), totalRemaining, reservedForHarvest, "2026-27"
        );

        assertNotNull(result);
        assertEquals(0, reservedForHarvest.compareTo(result.reservedExemption()));
        assertNotNull(result.exemptionHeadroomCaveat());
        assertTrue(result.exemptionHeadroomCaveat().contains("122000"));

        // Gain is 5,000. Effective headroom is 3,000.
        // It must split into 3,000 Tier 2 and 2,000 Tier 4!
        assertEquals(2, result.selectedLots().size());
        assertEquals(LiquidationTier.TIER_2_LTCG_EXEMPT_HEADROOM, result.selectedLots().get(0).tier());
        assertEquals(0, new BigDecimal("3000.00").compareTo(result.selectedLots().get(0).estimatedGain()));

        assertEquals(LiquidationTier.TIER_4_TAXABLE_112A_LTCG, result.selectedLots().get(1).tier());
        assertEquals(0, new BigDecimal("2000.00").compareTo(result.selectedLots().get(1).estimatedGain()));
        // 2,000 * 12.5% = 250.00
        assertEquals(0, new BigDecimal("250.00").compareTo(result.selectedLots().get(1).estimatedTaxDrag()));
    }

    @Test
    @DisplayName("Non-Equity STCG: Short-term Gold/International/SGB routes to Tier 6 slab rate (30%+) rather than Tier 5 equity (20%)")
    void testNonEquityStcgRoutesToTier6SlabRate() {
        LocalDate today = LocalDate.now();

        // Motilal Oswal Nasdaq 100 FoF (International) held 100 days (STCG)
        Lot intlStcg = new Lot("LOT_INTL_STCG", "INF209K01IN1", "Motilal Oswal Nasdaq 100 FoF", today.minusDays(100),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF209K01IN1", new BigDecimal("100")); // Value = 10,000, Cost = 5,000, Gain = 5,000

        RebalanceEngine.RebalancePreviewResult result = RebalanceEngine.calculateRebalancePreview(
            List.of(intlStcg), navMap, new BigDecimal("10000"), BigDecimal.ZERO, "2026-27"
        );

        assertNotNull(result);
        assertEquals(1, result.selectedLots().size());
        assertEquals("LOT_INTL_STCG", result.selectedLots().get(0).lotId());
        // Must route to Tier 6 (Slab Rate), NOT Tier 5 (Equity STCG 20%)
        assertEquals(LiquidationTier.TIER_6_SPECIFIED_50AA_DEBT, result.selectedLots().get(0).tier());
        // 5,000 gain * 30.0% slab rate = 1,500.00 (understating at 20% = 1,000 is eliminated)
        assertEquals(0, new BigDecimal("1500.00").compareTo(result.selectedLots().get(0).estimatedTaxDrag()));
    }
}
