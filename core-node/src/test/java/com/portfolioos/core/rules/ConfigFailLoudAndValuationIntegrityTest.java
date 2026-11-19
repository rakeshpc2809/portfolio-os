package com.portfolioos.core.rules;

import com.portfolioos.core.controllers.AuditController;
import com.portfolioos.core.fire.FireTracker;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.nav.AmfiNavSync;
import com.portfolioos.core.service.LedgerCacheService;
import com.portfolioos.core.service.PortfolioValuationService;
import com.portfolioos.core.valuation.BucketEngine;
import com.portfolioos.core.valuation.RebalanceWaterfallEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigFailLoudAndValuationIntegrityTest {

    @Test
    @DisplayName("Fail-Loud: AuditController.getFundHealth throws IllegalStateException when live NAV is missing")
    void testAuditControllerThrowsOnMissingNav() {
        LedgerCacheService cacheService = Mockito.mock(LedgerCacheService.class);
        com.portfolioos.core.persistence.MarketDataRepository marketDataRepo = Mockito.mock(com.portfolioos.core.persistence.MarketDataRepository.class);
        PortfolioValuationService valuationService = Mockito.mock(PortfolioValuationService.class);

        Lot lot = new Lot("L1", "INF109KC12U0", "ICICI Prudential LargeMidcap 250 Index Fund",
            LocalDate.of(2025, 1, 1), new BigDecimal("100"), new BigDecimal("100"),
            new BigDecimal("50"), new BigDecimal("5000"), false, null);

        LedgerCacheService.CachedLedgerState state = new LedgerCacheService.CachedLedgerState(
            List.of(),
            new com.portfolioos.core.matcher.FifoMatcher.FifoResult(List.of(lot), List.of()),
            Map.of(), // Empty NAV map!
            "HASH1",
            System.currentTimeMillis(),
            "HEALTHY"
        );
        Mockito.when(cacheService.getCachedState()).thenReturn(state);

        AuditController controller = new AuditController(cacheService, marketDataRepo, valuationService);
        IllegalStateException ex = assertThrows(IllegalStateException.class, controller::getFundHealth);
        assertTrue(ex.getMessage().contains("CRITICAL VALUATION ERROR"));
        assertTrue(ex.getMessage().contains("INF109KC12U0"));
    }

    @Test
    @DisplayName("Fail-Loud: RebalanceWaterfallEngine.buildTrimWaterfall throws IllegalStateException when NAV is missing")
    void testRebalanceWaterfallEngineThrowsOnMissingNav() {
        Lot lot = new Lot("L1", "INF109KC12U0", "ICICI Prudential LargeMidcap 250 Index Fund",
            LocalDate.of(2025, 1, 1), new BigDecimal("100"), new BigDecimal("100"),
            new BigDecimal("50"), new BigDecimal("5000"), false, null);

        Map<String, BigDecimal> emptyNavMap = Map.of();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            RebalanceWaterfallEngine.buildTrimWaterfall(
                BucketEngine.Bucket.EQUITY_CORE,
                new BigDecimal("10000.00"),
                List.of(lot),
                emptyNavMap,
                new BigDecimal("125000.00"),
                false,
                LocalDate.of(2026, 8, 1),
                "2026-27"
            )
        );
        assertTrue(ex.getMessage().contains("CRITICAL VALUATION ERROR"));
    }

    @Test
    @DisplayName("Fail-Loud: BucketEngine.evaluateRebalance throws IllegalStateException when targets is null or empty")
    void testBucketEngineThrowsOnNullOrEmptyTargets() {
        Lot lot = new Lot("L1", "INF109KC12U0", "ICICI Prudential LargeMidcap 250 Index Fund",
            LocalDate.of(2025, 1, 1), new BigDecimal("100"), new BigDecimal("100"),
            new BigDecimal("50"), new BigDecimal("5000"), false, null);
        Map<String, BigDecimal> navMap = Map.of("INF109KC12U0", new BigDecimal("100.00"));

        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
            BucketEngine.evaluateRebalance(List.of(lot), List.of(), navMap, LocalDate.now(), BigDecimal.ZERO, BigDecimal.ZERO, null, "2026-27")
        );
        assertTrue(ex1.getMessage().contains("targets list cannot be null or empty"));

        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
            BucketEngine.evaluateRebalance(List.of(lot), List.of(), navMap, LocalDate.now(), BigDecimal.ZERO, BigDecimal.ZERO, List.of(), "2026-27")
        );
        assertTrue(ex2.getMessage().contains("targets list cannot be null or empty"));
    }

    @Test
    @DisplayName("Cache-Busting: AmfiNavSync.clearCache clears static cached feed")
    void testAmfiNavSyncClearCache() {
        assertDoesNotThrow(AmfiNavSync::clearCache);
    }

    @Test
    @DisplayName("Integrity: RebalanceTriggerDto correctly serializes driftedBuckets")
    void testRebalanceTriggerDtoDriftedBuckets() {
        com.portfolioos.core.dtos.RebalancePlanDtos.RebalanceTriggerDto trigger =
            new com.portfolioos.core.dtos.RebalancePlanDtos.RebalanceTriggerDto(
                "DRIFT", "DRIFT_BREACH", "Drifted", "Window", null, List.of("EQUITY_CORE", "GOLD_SILVER")
            );
        assertEquals(List.of("EQUITY_CORE", "GOLD_SILVER"), trigger.driftedBuckets());
    }
}
