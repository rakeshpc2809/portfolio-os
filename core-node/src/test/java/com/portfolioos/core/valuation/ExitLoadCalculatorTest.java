package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExitLoadCalculatorTest {

    @Test
    @DisplayName("Arbitrage Fund INF879O01027: Lots older than 30 days are exit-load free; lots younger than 30 days are locked")
    void testArbitrageExitLoadCalculation() {
        LocalDate today = LocalDate.of(2026, 10, 23);
        String isin = ExitLoadCalculator.ARBITRAGE_ISIN;
        String name = "Invesco India Arbitrage Fund Direct Growth";
        BigDecimal nav = new BigDecimal("12.5000");

        // Lot 1: acquired 40 days ago -> exit-load free
        Lot lot1 = new Lot(
            "lot_arb_1",
            isin,
            name,
            today.minusDays(40),
            new BigDecimal("100.000"),
            new BigDecimal("100.000"),
            new BigDecimal("12.0000"),
            new BigDecimal("1200.00"),
            false,
            BigDecimal.ZERO
        );

        // Lot 2: acquired 10 days ago -> locked under exit load
        Lot lot2 = new Lot(
            "lot_arb_2",
            isin,
            name,
            today.minusDays(10),
            new BigDecimal("50.000"),
            new BigDecimal("50.000"),
            new BigDecimal("12.2000"),
            new BigDecimal("610.00"),
            false,
            BigDecimal.ZERO
        );

        ExitLoadCalculator.ExitLoadSummary summary = ExitLoadCalculator.calculateForAsset(
            isin,
            name,
            List.of(lot1, lot2),
            nav,
            today
        );

        assertEquals(new BigDecimal("150.000"), summary.totalUnits());
        assertEquals(new BigDecimal("100.000"), summary.exitLoadFreeUnits());
        assertEquals(new BigDecimal("50.000"), summary.lockedUnits());
        assertEquals(new BigDecimal("1875.00"), summary.totalValue());
        assertEquals(new BigDecimal("1250.00"), summary.exitLoadFreeValue());
        assertEquals(new BigDecimal("625.00"), summary.lockedValue());
        assertEquals(66.67, summary.exitLoadFreePct(), 0.01);
        assertEquals(15, summary.exitLoadDaysRequired());
    }

    @Test
    @DisplayName("Index Funds default to 0 exit load days: 100% of units are immediately exit-load free")
    void testIndexFundZeroExitLoad() {
        LocalDate today = LocalDate.of(2026, 10, 23);
        String isin = "INF109KC12U0";
        String name = "ICICI Prudential Nifty LargeMidcap 250 Index Fund Direct Growth";
        BigDecimal nav = new BigDecimal("25.0000");

        Lot recentLot = new Lot(
            "lot_idx_1",
            isin,
            name,
            today.minusDays(2),
            new BigDecimal("200.000"),
            new BigDecimal("200.000"),
            new BigDecimal("24.8000"),
            new BigDecimal("4960.00"),
            false,
            BigDecimal.ZERO
        );

        ExitLoadCalculator.ExitLoadSummary summary = ExitLoadCalculator.calculateForAsset(
            isin,
            name,
            List.of(recentLot),
            nav,
            today
        );

        assertEquals(new BigDecimal("200.000"), summary.totalUnits());
        assertEquals(new BigDecimal("200.000"), summary.exitLoadFreeUnits());
        assertEquals(new BigDecimal("0.000"), summary.lockedUnits());
        assertEquals(100.0, summary.exitLoadFreePct(), 0.01);
        assertEquals(0, summary.exitLoadDaysRequired());
    }

    @Test
    @DisplayName("Parag Parikh Flexi Cap INF879O01027: 730-day exit load window locks lots younger than 2 years")
    void testParagParikhFlexiCap730DayExitLoad() {
        LocalDate today = LocalDate.of(2026, 10, 23);
        String isin = ExitLoadCalculator.PPFAS_FLEXICAP_ISIN;
        String name = "Parag Parikh Flexi Cap Fund Direct Growth";
        BigDecimal nav = new BigDecimal("89.4088");

        // Lot 1: acquired 800 days ago -> free (> 730 days)
        Lot lot1 = new Lot(
            "lot_ppfas_old",
            isin,
            name,
            today.minusDays(800),
            new BigDecimal("1000.000"),
            new BigDecimal("1000.000"),
            new BigDecimal("60.0000"),
            new BigDecimal("60000.00"),
            false,
            BigDecimal.ZERO
        );

        // Lot 2: acquired 400 days ago -> locked (< 730 days)
        Lot lot2 = new Lot(
            "lot_ppfas_recent",
            isin,
            name,
            today.minusDays(400),
            new BigDecimal("500.000"),
            new BigDecimal("500.000"),
            new BigDecimal("75.0000"),
            new BigDecimal("37500.00"),
            false,
            BigDecimal.ZERO
        );

        ExitLoadCalculator.ExitLoadSummary summary = ExitLoadCalculator.calculateForAsset(
            isin,
            name,
            List.of(lot1, lot2),
            nav,
            today
        );

        assertEquals(new BigDecimal("1500.000"), summary.totalUnits());
        assertEquals(new BigDecimal("1000.000"), summary.exitLoadFreeUnits());
        assertEquals(new BigDecimal("500.000"), summary.lockedUnits());
        assertEquals(730, summary.exitLoadDaysRequired());
        assertEquals(66.67, summary.exitLoadFreePct(), 0.01);
    }
}
