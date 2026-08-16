package com.portfolioos.core.service;

import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimulationServiceTest {

    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        TaxEvent acq = new TaxEvent(
            "EV_ACQ_1",
            "INF109KC13X2",
            "ICICI Nifty200",
            "INF109KC13X2",
            EventType.ACQUISITION,
            LocalDate.of(2024, 1, 1),
            new BigDecimal("1000.0"),
            new BigDecimal("10.0"),
            new BigDecimal("10000.0"),
            "CAS_IMPORT",
            Instant.now()
        );

        LedgerCacheService.CachedLedgerState cachedState = new LedgerCacheService.CachedLedgerState(
            List.of(acq),
            new com.portfolioos.core.matcher.FifoMatcher().processEvents(List.of(acq)),
            Map.of("INF109KC13X2", new BigDecimal("15.0")),
            "HASH_123",
            System.currentTimeMillis(),
            "HEALTHY"
        );

        LedgerCacheService mockCacheService = new LedgerCacheService(null) {
            @Override
            public CachedLedgerState getCachedState() {
                return cachedState;
            }
        };

        simulationService = new SimulationService(mockCacheService);
    }

    @Test
    void testEquityLtcgTaxDynamicFromRules() {
        TaxRulesConfig rules = TaxRulesLoader.loadRules("2026-27");
        assertNotNull(rules);

        SimulationService.TradeSimulationRequest req = new SimulationService.TradeSimulationRequest(
            "INF109KC13X2",
            "ICICI Nifty200",
            new BigDecimal("1000.0"),
            new BigDecimal("150.0"),
            "2026-06-01",
            "DISPOSAL"
        );

        SimulationService.TradeSimulationResult res = simulationService.simulateTrade(req);
        assertEquals("DISPOSAL", res.tradeType());
        assertTrue(res.ltcgEquity().compareTo(BigDecimal.ZERO) > 0);

        BigDecimal expectedTaxable = res.ltcgEquity().subtract(rules.equityExemptionLimit());
        BigDecimal expectedTax = expectedTaxable.multiply(rules.equityLtcgRate()).setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(expectedTax, res.estimatedTaxLiability());
    }

    @Test
    void testGoldSilverLtcgHeldOver24Months() {
        TaxEvent acqGold = new TaxEvent(
            "EV_GOLD_ACQ",
            "INF247L01BM8",
            "Gold FoF",
            "INF247L01BM8",
            EventType.ACQUISITION,
            LocalDate.of(2023, 1, 1),
            new BigDecimal("100.0"),
            new BigDecimal("100.0"),
            new BigDecimal("10000.0"),
            "CAS_IMPORT",
            Instant.now()
        );

        LedgerCacheService.CachedLedgerState goldState = new LedgerCacheService.CachedLedgerState(
            List.of(acqGold),
            new com.portfolioos.core.matcher.FifoMatcher().processEvents(List.of(acqGold)),
            Map.of("INF247L01BM8", new BigDecimal("150.0")),
            "HASH_GOLD",
            System.currentTimeMillis(),
            "HEALTHY"
        );

        SimulationService simService = new SimulationService(new LedgerCacheService(null) {
            @Override
            public CachedLedgerState getCachedState() {
                return goldState;
            }
        });

        SimulationService.TradeSimulationRequest req = new SimulationService.TradeSimulationRequest(
            "INF247L01BM8",
            "Gold FoF",
            new BigDecimal("100.0"),
            new BigDecimal("150.0"),
            "2026-05-01",
            "DISPOSAL"
        );

        SimulationService.TradeSimulationResult res = simService.simulateTrade(req);
        assertEquals("DISPOSAL", res.tradeType());
        BigDecimal expectedTax = new BigDecimal("5000.00").multiply(new BigDecimal("0.125")).setScale(2, java.math.RoundingMode.HALF_UP);
        assertEquals(expectedTax, res.estimatedTaxLiability());
    }

    @Test
    void testGoldSilverStcgHeldUnder24Months() {
        TaxEvent acqGoldShort = new TaxEvent(
            "EV_GOLD_SHORT",
            "INF247L01BM8",
            "Gold FoF",
            "INF247L01BM8",
            EventType.ACQUISITION,
            LocalDate.of(2026, 1, 1),
            new BigDecimal("100.0"),
            new BigDecimal("100.0"),
            new BigDecimal("10000.0"),
            "CAS_IMPORT",
            Instant.now()
        );

        LedgerCacheService.CachedLedgerState goldState = new LedgerCacheService.CachedLedgerState(
            List.of(acqGoldShort),
            new com.portfolioos.core.matcher.FifoMatcher().processEvents(List.of(acqGoldShort)),
            Map.of("INF247L01BM8", new BigDecimal("150.0")),
            "HASH_GOLD_SHORT",
            System.currentTimeMillis(),
            "HEALTHY"
        );

        SimulationService simService = new SimulationService(new LedgerCacheService(null) {
            @Override
            public CachedLedgerState getCachedState() {
                return goldState;
            }
        });

        SimulationService.TradeSimulationRequest req = new SimulationService.TradeSimulationRequest(
            "INF247L01BM8",
            "Gold FoF",
            new BigDecimal("100.0"),
            new BigDecimal("150.0"),
            "2026-05-01",
            "DISPOSAL"
        );

        SimulationService.TradeSimulationResult res = simService.simulateTrade(req);
        assertEquals("DISPOSAL", res.tradeType());
        assertTrue(res.slabRateGain().compareTo(new BigDecimal("5000.00")) == 0);
        assertEquals(res.slabRateGain(), res.debtGain(), "debtGain() alias must equal slabRateGain()");
        assertTrue(res.taxSummaryNotice().contains("SLAB_RATE — not computed"));
    }

    @Test
    void testRegressionNoHardcodedTaxLiterals() throws Exception {
        File simFile = new File("src/main/java/com/portfolioos/core/service/SimulationService.java");
        assertTrue(simFile.exists(), "SimulationService.java must exist");
        String content = Files.readString(simFile.toPath());

        assertFalse(content.contains("new BigDecimal(\"0.125\")"), "Must not contain hardcoded 0.125 rate literal");
        assertFalse(content.contains("new BigDecimal(\"0.20\")"), "Must not contain hardcoded 0.20 rate literal");
        assertFalse(content.contains("new BigDecimal(\"0.30\")"), "Must not contain hardcoded 0.30 rate literal");
        assertFalse(content.contains("new BigDecimal(\"0.3\")"), "Must not contain hardcoded 0.3 rate literal");
        assertTrue(content.contains("default -> throw new IllegalStateException"), "Must contain explicit default throw branch");
    }
}
