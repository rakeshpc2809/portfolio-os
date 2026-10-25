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

    @Test
    void testNegativeUnitsThrowsIllegalArgument() {
        SimulationService.TradeSimulationRequest req = new SimulationService.TradeSimulationRequest(
            "INF109KC13X2",
            "ICICI Nifty200",
            new BigDecimal("-100.0"),
            new BigDecimal("15.0"),
            "2026-05-01",
            "DISPOSAL"
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> simulationService.simulateTrade(req));
        assertTrue(ex.getMessage().contains("positive unit quantity"), "Expected positive unit quantity message");
    }

    @Test
    void testZeroUnitsThrowsIllegalArgument() {
        SimulationService.TradeSimulationRequest req = new SimulationService.TradeSimulationRequest(
            "INF109KC13X2",
            "ICICI Nifty200",
            BigDecimal.ZERO,
            new BigDecimal("15.0"),
            "2026-05-01",
            "DISPOSAL"
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> simulationService.simulateTrade(req));
        assertTrue(ex.getMessage().contains("positive unit quantity"));
    }

    @Test
    void testNegativePriceThrowsIllegalArgument() {
        SimulationService.TradeSimulationRequest req = new SimulationService.TradeSimulationRequest(
            "INF109KC13X2",
            "ICICI Nifty200",
            new BigDecimal("100.0"),
            new BigDecimal("-15.0"),
            "2026-05-01",
            "DISPOSAL"
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> simulationService.simulateTrade(req));
        assertTrue(ex.getMessage().contains("positive price per unit"));
    }

    @Test
    void testBlankIsinThrowsIllegalArgument() {
        SimulationService.TradeSimulationRequest req = new SimulationService.TradeSimulationRequest(
            "   ",
            "ICICI Nifty200",
            new BigDecimal("100.0"),
            new BigDecimal("15.0"),
            "2026-05-01",
            "DISPOSAL"
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> simulationService.simulateTrade(req));
        assertTrue(ex.getMessage().contains("valid non-empty ISIN"));
    }

    @Test
    void testInvalidTradeTypeThrowsIllegalArgument() {
        SimulationService.TradeSimulationRequest req = new SimulationService.TradeSimulationRequest(
            "INF109KC13X2",
            "ICICI Nifty200",
            new BigDecimal("100.0"),
            new BigDecimal("15.0"),
            "2026-05-01",
            "INVALID_TYPE"
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> simulationService.simulateTrade(req));
        assertTrue(ex.getMessage().contains("tradeType of 'ACQUISITION' or 'DISPOSAL'"));
    }

    @Test
    void testGrandfatheredFmvDisposalSimulationUses2018Basis() {
        TaxEvent pre2018Acq = new TaxEvent(
            "EV_PRE_2018",
            "INF109KC13X2",
            "ICICI Nifty200",
            "INF109KC13X2",
            EventType.ACQUISITION,
            LocalDate.of(2017, 1, 1),
            new BigDecimal("100.0"),
            new BigDecimal("10.0"),
            new BigDecimal("1000.0"),
            "CAS_IMPORT",
            Instant.now()
        );
        Map<String, BigDecimal> fmvMap = Map.of("INF109KC13X2", new BigDecimal("25.0"));
        var fifoResult = new com.portfolioos.core.matcher.FifoMatcher().processEvents(List.of(pre2018Acq), fmvMap);
        var cachedState = new LedgerCacheService.CachedLedgerState(
            List.of(pre2018Acq),
            fifoResult,
            Map.of("INF109KC13X2", new BigDecimal("30.0")),
            "HASH_GF",
            System.currentTimeMillis(),
            "HEALTHY"
        );
        LedgerCacheService customCache = new LedgerCacheService(null) {
            @Override public CachedLedgerState getCachedState() { return cachedState; }
            @Override public Map<String, BigDecimal> getJan2018FmvMap() { return fmvMap; }
        };
        SimulationService sim = new SimulationService(customCache);
        SimulationService.TradeSimulationRequest req = new SimulationService.TradeSimulationRequest(
            "INF109KC13X2", "ICICI Nifty200", new BigDecimal("100.0"), new BigDecimal("30.0"), "2026-06-01", "DISPOSAL"
        );
        SimulationService.TradeSimulationResult res = sim.simulateTrade(req);
        // Cost basis should be max(10, min(25, 30)) * 100 = 2500.00
        // Realized LTCG should be (30 - 25) * 100 = 500.00 (not 2000.00!)
        assertEquals(new BigDecimal("500.00"), res.ltcgEquity());
    }

    @Test
    void testDebtSpecified50AASimulationTaxAndCashConservation() {
        TaxEvent debtAcq = new TaxEvent(
            "EV_DEBT_1",
            "INF209K01157",
            "Aditya Birla Sun Life Short Term Debt Fund",
            "INF209K01157",
            EventType.ACQUISITION,
            LocalDate.of(2023, 6, 1),
            new BigDecimal("100.0"),
            new BigDecimal("10.0"),
            new BigDecimal("1000.0"),
            "CAS_IMPORT",
            Instant.now()
        );
        var fifoResult = new com.portfolioos.core.matcher.FifoMatcher().processEvents(List.of(debtAcq));
        var cachedState = new LedgerCacheService.CachedLedgerState(
            List.of(debtAcq),
            fifoResult,
            Map.of("INF209K01157", new BigDecimal("15.0")),
            "HASH_DEBT",
            System.currentTimeMillis(),
            "HEALTHY"
        );
        LedgerCacheService customCache = new LedgerCacheService(null) {
            @Override public CachedLedgerState getCachedState() { return cachedState; }
        };
        SimulationService sim = new SimulationService(customCache);
        SimulationService.TradeSimulationRequest req = new SimulationService.TradeSimulationRequest(
            "INF209K01157", "Aditya Birla Sun Life Short Term Debt Fund", new BigDecimal("100.0"), new BigDecimal("15.0"), "2026-06-01", "DISPOSAL"
        );
        SimulationService.TradeSimulationResult res = sim.simulateTrade(req);
        assertTrue(res.estimatedTaxLiability().compareTo(BigDecimal.ZERO) > 0, "Debt 50AA must compute non-zero estimated tax liability");
        assertEquals(new BigDecimal("500.00"), res.slabRateGain());
        assertEquals(res.grossTradeAmount().subtract(res.estimatedTaxLiability()), res.postTradeNetWorth());
    }
}
