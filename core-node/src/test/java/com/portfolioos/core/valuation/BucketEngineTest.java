package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BucketEngineTest {

    @Test
    @DisplayName("BucketEngine calculates bucket allocation with valid live NAV")
    void testEvaluateRebalanceSuccess() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        Lot coreLot = new Lot("lot-1", "INF109KC12U0", "ICICI LargeMidcap 250", date,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF109KC12U0", new BigDecimal("150"));
        BucketEngine.RebalanceEngineResult result = BucketEngine.evaluateRebalance(
            List.of(coreLot), List.of(), navMap, date, BigDecimal.ZERO, BigDecimal.ZERO, BucketEngine.DEFAULT_TARGETS, "2026-27"
        );

        assertNotNull(result);
        assertEquals(5, result.bucketStatuses().size());
        BucketEngine.BucketStatus coreStatus = result.bucketStatuses().stream()
            .filter(b -> b.bucket() == BucketEngine.Bucket.EQUITY_CORE)
            .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("15000").compareTo(coreStatus.currentValue()));
    }

    @Test
    @DisplayName("Fail-Loud Invariant: BucketEngine throws IllegalStateException on missing NAV")
    void testEvaluateRebalanceThrowsOnMissingNav() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        Lot coreLot = new Lot("lot-1", "INF109KC12U0", "ICICI LargeMidcap 250", date,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null);

        Map<String, BigDecimal> emptyNavMap = Map.of();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            BucketEngine.evaluateRebalance(
                List.of(coreLot), List.of(), emptyNavMap, date, BigDecimal.ZERO, BigDecimal.ZERO, BucketEngine.DEFAULT_TARGETS, "2026-27"
            )
        );
        assertTrue(ex.getMessage().contains("CRITICAL VALUATION ERROR"));
        assertTrue(ex.getMessage().contains("INF109KC12U0"));
    }
}
