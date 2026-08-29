package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NavResolverTest {

    @Test
    void testRequireValidNavReturnsNavWhenPresentAndPositive() {
        Map<String, BigDecimal> navMap = Map.of("INF109KC13X2", new BigDecimal("125.50"));
        BigDecimal nav = NavResolver.requireValidNav(navMap, "INF109KC13X2", "ICICI Large & Mid Cap", "UnitTest");
        assertEquals(new BigDecimal("125.50"), nav);

        Lot lot = new Lot("L1", "INF109KC13X2", "ICICI Large & Mid Cap", LocalDate.now(), new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("1000"), false, null);
        BigDecimal lotNav = NavResolver.requireValidNav(navMap, lot, "UnitTestLot");
        assertEquals(new BigDecimal("125.50"), lotNav);
    }

    @Test
    void testRequireValidNavThrowsOnNullNavMap() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            NavResolver.requireValidNav(null, "INF109KC13X2", "ICICI Large & Mid Cap", "UnitTest")
        );
        assertTrue(ex.getMessage().contains("CRITICAL VALUATION ERROR"));
        assertTrue(ex.getMessage().contains("INF109KC13X2"));
    }

    @Test
    void testRequireValidNavThrowsOnMissingIsin() {
        Map<String, BigDecimal> navMap = Map.of("OTHER_ISIN", new BigDecimal("50.00"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            NavResolver.requireValidNav(navMap, "INF109KC13X2", "ICICI Large & Mid Cap", "UnitTest")
        );
        assertTrue(ex.getMessage().contains("Missing or invalid live NAV"));
        assertTrue(ex.getMessage().contains("INF109KC13X2"));
    }

    @Test
    void testRequireValidNavThrowsOnNullNavValue() {
        Map<String, BigDecimal> navMap = Collections.singletonMap("INF109KC13X2", null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            NavResolver.requireValidNav(navMap, "INF109KC13X2", "ICICI Large & Mid Cap", "UnitTest")
        );
        assertTrue(ex.getMessage().contains("Missing or invalid live NAV"));
    }

    @Test
    void testRequireValidNavThrowsOnZeroOrNegativeNav() {
        Map<String, BigDecimal> navMapZero = Map.of("INF109KC13X2", BigDecimal.ZERO);
        assertThrows(IllegalStateException.class, () ->
            NavResolver.requireValidNav(navMapZero, "INF109KC13X2", "ICICI Large & Mid Cap", "UnitTest")
        );

        Map<String, BigDecimal> navMapNegative = Map.of("INF109KC13X2", new BigDecimal("-10.0"));
        assertThrows(IllegalStateException.class, () ->
            NavResolver.requireValidNav(navMapNegative, "INF109KC13X2", "ICICI Large & Mid Cap", "UnitTest")
        );
    }

    @Test
    void testRequireValidNavThrowsOnNullLot() {
        Map<String, BigDecimal> navMap = Map.of("INF109KC13X2", new BigDecimal("125.50"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            NavResolver.requireValidNav(navMap, (Lot) null, "UnitTest")
        );
        assertTrue(ex.getMessage().contains("Lot cannot be null"));
    }
}
