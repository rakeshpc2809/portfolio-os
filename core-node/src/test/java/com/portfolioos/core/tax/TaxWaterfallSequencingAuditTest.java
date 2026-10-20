package com.portfolioos.core.tax;

import com.portfolioos.core.model.Lot;
import com.portfolioos.core.valuation.LiquidationTier;
import com.portfolioos.core.valuation.RebalanceEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TaxWaterfallSequencingAuditTest {

    @Test
    @DisplayName("5.3 Invariant: Waterfall sequencing across mixed lots strictly prioritizes lowest tax drag")
    void testWaterfallSequencingAcrossMixedLots() {
        LocalDate today = LocalDate.now();

        // 1. Loss Lot (STCG loss): Cost 120, NAV 100 -> Unrealized loss ₹2,000 (0 tax drag)
        Lot stcgLossLot = new Lot(
            "LOT_STCG_LOSS", "INF109K01EQ_L", "ICICI Prudential Loss Lot", today.minusDays(90),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("120"), new BigDecimal("12000"), false, null
        );

        // 2. Sec 112A LTCG Lot within headroom: Cost 50, NAV 100 -> Unrealized gain ₹5,000 (0 tax drag)
        Lot ltcgHeadroomLot = new Lot(
            "LOT_112A_HEADROOM", "INF109K01EQ_H", "Parag Parikh Flexi Cap", today.minusDays(450),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("5000"), false, null
        );

        // 3. Grandfathered Debt Lot (pre-2023, >24m): Cost 100, NAV 120 -> Gain ₹2,000 @ 12.5% = ₹250
        Lot gfDebtLot = new Lot(
            "LOT_GF_DEBT", "INF109K01DB_GF", "SBI Short Term Debt", LocalDate.of(2023, 1, 10),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null
        );

        // 4. Equity STCG Gain Lot: Cost 80, NAV 100 -> Gain ₹2,000 @ 20% = ₹400
        Lot stcgGainLot = new Lot(
            "LOT_STCG_GAIN", "INF109K01EQ_G", "Kotak Flexicap Equity", today.minusDays(120),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("80"), new BigDecimal("8000"), false, null
        );

        // 5. Sec 50AA Debt Lot: Cost 100, NAV 110 -> Gain ₹1,000 @ 30% slab = ₹300
        Lot sec50aaDebtLot = new Lot(
            "LOT_50AA_DEBT", "INF109K01DB_50AA", "HDFC Corporate Debt", LocalDate.of(2023, 7, 1),
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null
        );

        Map<String, BigDecimal> navMap = Map.of(
            "INF109K01EQ_L", new BigDecimal("100"),
            "INF109K01EQ_H", new BigDecimal("100"),
            "INF109K01DB_GF", new BigDecimal("120"),
            "INF109K01EQ_G", new BigDecimal("100"),
            "INF109K01DB_50AA", new BigDecimal("110")
        );

        // Total redemption required to consume all 5 lots:
        // STCG loss lot: 10,000
        // LTCG headroom lot: 10,000
        // GF debt lot: 12,000
        // STCG gain lot: 10,000
        // 50AA debt lot: 11,000
        // Total = 53,000
        BigDecimal targetAmount = new BigDecimal("53000");
        BigDecimal exemptionHeadroom = new BigDecimal("125000");

        RebalanceEngine.RebalancePreviewResult result = RebalanceEngine.calculateRebalancePreview(
            List.of(sec50aaDebtLot, stcgGainLot, gfDebtLot, ltcgHeadroomLot, stcgLossLot), // intentionally disordered
            navMap,
            targetAmount,
            exemptionHeadroom,
            BigDecimal.ZERO,
            "2026-27",
            true
        );

        assertNotNull(result);
        assertEquals(5, result.selectedLots().size());

        // Step 1: Capital Loss lot is liquidated first (0 tax drag)
        assertEquals("LOT_STCG_LOSS", result.selectedLots().get(0).lotId());
        assertEquals(LiquidationTier.TIER_1_LOSS_HARVEST, result.selectedLots().get(0).tier());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.selectedLots().get(0).estimatedTaxDrag()));

        // Step 2: Sec 112A LTCG lot within headroom is liquidated second (0 tax drag)
        assertEquals("LOT_112A_HEADROOM", result.selectedLots().get(1).lotId());
        assertEquals(LiquidationTier.TIER_2_LTCG_EXEMPT_HEADROOM, result.selectedLots().get(1).tier());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.selectedLots().get(1).estimatedTaxDrag()));

        // Step 3: Grandfathered Debt lot (12.5% rate) is liquidated third
        assertEquals("LOT_GF_DEBT", result.selectedLots().get(2).lotId());
        assertEquals(LiquidationTier.TIER_3_GRANDFATHERED_DEBT_LTCG, result.selectedLots().get(2).tier());

        // Step 4: Equity STCG gain lot (20% rate) is liquidated fourth
        assertEquals("LOT_STCG_GAIN", result.selectedLots().get(3).lotId());
        assertEquals(LiquidationTier.TIER_5_EQUITY_STCG, result.selectedLots().get(3).tier());

        // Step 5: Sec 50AA debt lot (30% marginal slab rate) is liquidated last
        assertEquals("LOT_50AA_DEBT", result.selectedLots().get(4).lotId());
        assertEquals(LiquidationTier.TIER_6_SPECIFIED_50AA_DEBT, result.selectedLots().get(4).tier());
    }

    @Test
    @DisplayName("5.3 Invariant: Sec 112A lot exceeding headroom splits into Tier 2 (exempt) and Tier 4 (taxable 12.5%)")
    void testSec112aLotExceedingHeadroomSplitsProperly() {
        LocalDate today = LocalDate.now();

        // 200 units @ cost 50, current NAV 100 -> Total proceeds ₹20,000, Total gain ₹10,000
        Lot largeLtcgLot = new Lot(
            "LOT_LARGE_LTCG", "INF109K01EQ_LARGE", "Nippon India Large Cap", today.minusDays(400),
            new BigDecimal("200"), new BigDecimal("200"), new BigDecimal("50"), new BigDecimal("10000"), false, null
        );

        Map<String, BigDecimal> navMap = Map.of("INF109K01EQ_LARGE", new BigDecimal("100"));

        // Only ₹4,000 exemption headroom remaining
        BigDecimal exemptionHeadroom = new BigDecimal("4000");
        BigDecimal targetAmount = new BigDecimal("20000");

        RebalanceEngine.RebalancePreviewResult result = RebalanceEngine.calculateRebalancePreview(
            List.of(largeLtcgLot),
            navMap,
            targetAmount,
            exemptionHeadroom,
            BigDecimal.ZERO,
            "2026-27",
            true
        );

        assertNotNull(result);
        assertEquals(2, result.selectedLots().size());

        // Slice 1: Tier 2 (Exempt headroom) -> ₹4,000 gain (80 units, ₹8,000 proceeds, 0 tax drag)
        RebalanceEngine.RebalanceLotSelection exemptSlice = result.selectedLots().get(0);
        assertEquals("LOT_LARGE_LTCG", exemptSlice.lotId());
        assertEquals(LiquidationTier.TIER_2_LTCG_EXEMPT_HEADROOM, exemptSlice.tier());
        assertEquals(0, new BigDecimal("4000.00").compareTo(exemptSlice.estimatedGain()));
        assertEquals(0, BigDecimal.ZERO.compareTo(exemptSlice.estimatedTaxDrag()));

        // Slice 2: Tier 4 (Taxable Sec 112A @ 12.5%) -> ₹6,000 gain (120 units, ₹12,000 proceeds)
        RebalanceEngine.RebalanceLotSelection taxableSlice = result.selectedLots().get(1);
        assertEquals("LOT_LARGE_LTCG", taxableSlice.lotId());
        assertEquals(LiquidationTier.TIER_4_TAXABLE_112A_LTCG, taxableSlice.tier());
        assertEquals(0, new BigDecimal("6000.00").compareTo(taxableSlice.estimatedGain()));
        // 6000 * 12.5% = ₹750.00 tax drag
        assertEquals(0, new BigDecimal("750.00").compareTo(taxableSlice.estimatedTaxDrag()));
    }
}
