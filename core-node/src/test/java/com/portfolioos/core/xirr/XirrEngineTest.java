package com.portfolioos.core.xirr;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XirrEngineTest {

    @Test
    void testXirrCalculationSimpleReturn() {
        XirrEngine engine = new XirrEngine();

        CashFlow cf1 = new CashFlow(LocalDate.of(2023, 1, 1), new BigDecimal("-100000.00"));
        CashFlow cf2 = new CashFlow(LocalDate.of(2024, 1, 1), new BigDecimal("112000.00"));

        double xirr = engine.calculateXirr(List.of(cf1, cf2));
        assertTrue(xirr > 11.5 && xirr < 12.5, "XIRR should be approx 12.0%");
    }

    @Test
    void testXirrShortDurationReturnsAbsoluteGain() {
        XirrEngine engine = new XirrEngine();

        CashFlow cf1 = new CashFlow(LocalDate.of(2024, 1, 1), new BigDecimal("-100000.00"));
        CashFlow cf2 = new CashFlow(LocalDate.of(2024, 1, 15), new BigDecimal("105000.00"));

        double xirr = engine.calculateXirr(List.of(cf1, cf2));
        assertEquals(5.0, xirr, 0.01, "Short duration <30 days should return absolute return (5%)");
    }

    @Test
    void testXirrNullOrInsufficientFlows() {
        XirrEngine engine = new XirrEngine();

        assertEquals(0.0, engine.calculateXirr(null));
        assertEquals(0.0, engine.calculateXirr(List.of()));
        assertEquals(0.0, engine.calculateXirr(List.of(new CashFlow(LocalDate.now(), new BigDecimal("-100")))));
    }
}
