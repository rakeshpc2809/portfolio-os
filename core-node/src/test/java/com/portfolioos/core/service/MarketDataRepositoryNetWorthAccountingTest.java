package com.portfolioos.core.service;

import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.persistence.MarketDataRepository;
import com.portfolioos.core.persistence.MarketDataRepository.NetWorthPoint;
import com.portfolioos.core.persistence.SqliteEventStore;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataRepositoryNetWorthAccountingTest {

    private HikariDataSource dataSource;
    private SqliteEventStore eventStore;
    private MarketDataRepository marketDataRepository;
    private Path tempDbPath;

    @BeforeEach
    void setUp() throws Exception {
        tempDbPath = Files.createTempFile("market_data_test_", ".db");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDbPath.toAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA busy_timeout=5000;");

        dataSource = new HikariDataSource(config);
        eventStore = new SqliteEventStore(dataSource, "test-secret-key-12345");
        marketDataRepository = new MarketDataRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        if (tempDbPath != null) {
            try {
                Files.deleteIfExists(tempDbPath);
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("Verify net active capital accounting: DISPOSAL sale proceeds drop total_invested, and subsequent ACQUISITION nets to exactly 0.00 change across rebalance pair")
    void testRebalanceTradePairNetActiveCapitalAccounting() {
        // 1. Setup NAV history for asset_1 and asset_2 across dates
        marketDataRepository.saveLatestNavEntries(List.of(
            new com.portfolioos.core.nav.AmfiNavSync.NavEntry(null, "asset_1", "Legacy Fund", new BigDecimal("10.0"), LocalDate.parse("2026-01-01"))
        ));
        marketDataRepository.saveLatestNavEntries(List.of(
            new com.portfolioos.core.nav.AmfiNavSync.NavEntry(null, "asset_1", "Legacy Fund", new BigDecimal("12.0"), LocalDate.parse("2026-06-01"))
        ));
        marketDataRepository.saveLatestNavEntries(List.of(
            new com.portfolioos.core.nav.AmfiNavSync.NavEntry(null, "asset_1", "Legacy Fund", new BigDecimal("12.0"), LocalDate.parse("2026-06-02")),
            new com.portfolioos.core.nav.AmfiNavSync.NavEntry(null, "asset_2", "New Fund", new BigDecimal("15.0"), LocalDate.parse("2026-06-02"))
        ));

        // 2. Initial ACQUISITION on 2026-01-01 (Rs 1,00,000 invested, 10,000 units @ Rs 10.0)
        TaxEvent e1 = new TaxEvent(
            "evt-1", "asset_1", "Legacy Fund", "asset_1",
            EventType.ACQUISITION, LocalDate.parse("2026-01-01"),
            new BigDecimal("10000.00"), new BigDecimal("10.00"), new BigDecimal("100000.00"),
            "doc-1", Instant.parse("2026-01-01T10:00:00Z")
        );
        eventStore.appendEvents(List.of(e1));
        marketDataRepository.invalidateTrendCache();

        List<NetWorthPoint> initialTrend = marketDataRepository.getDailyNetWorthTrend();
        assertFalse(initialTrend.isEmpty());
        NetWorthPoint pointJan1 = initialTrend.stream()
            .filter(p -> p.date().equals("2026-01-01"))
            .findFirst()
            .orElseThrow();
        assertEquals(100000.00, pointJan1.invested(), 0.01, "Initial invested capital should be 100,000.00");

        // 3. Synthetic DISPOSAL on 2026-06-01:
        // Sale Proceeds: Rs 88,121.00 (7,343.4167 units @ Rs 12.0)
        TaxEvent eDisposal = new TaxEvent(
            "evt-disp-1", "asset_1", "Legacy Fund", "asset_1",
            EventType.DISPOSAL, LocalDate.parse("2026-06-01"),
            new BigDecimal("7343.4167"), new BigDecimal("12.00"), new BigDecimal("88121.00"),
            "doc-rebalance-sell", Instant.parse("2026-06-01T10:00:00Z")
        );
        eventStore.appendEvents(List.of(eDisposal));
        marketDataRepository.invalidateTrendCache();

        // Intermediate check: total_invested drops by sale proceeds (100,000 - 88,121 = 11,879)
        List<NetWorthPoint> intermediateTrend = marketDataRepository.getDailyNetWorthTrend();
        NetWorthPoint pointJun1 = intermediateTrend.stream()
            .filter(p -> p.date().equals("2026-06-01"))
            .findFirst()
            .orElseThrow();

        assertEquals(11879.00, pointJun1.invested(), 0.01,
            "Intermediate state: when DISPOSAL fires before ACQUISITION, total_invested must drop by full sale proceeds (100,000 - 88,121 = 11,879.00)");

        // 4. Subsequent ACQUISITION on 2026-06-02:
        // Reinvest full proceeds Rs 88,121.00 into Target Fund (asset_2)
        TaxEvent eAcquisition = new TaxEvent(
            "evt-acq-2", "asset_2", "Target Fund", "asset_2",
            EventType.ACQUISITION, LocalDate.parse("2026-06-02"),
            new BigDecimal("5874.7333"), new BigDecimal("15.00"), new BigDecimal("88121.00"),
            "doc-rebalance-buy", Instant.parse("2026-06-02T10:00:00Z")
        );
        eventStore.appendEvents(List.of(eAcquisition));
        marketDataRepository.invalidateTrendCache();

        // Final check: total_invested returns to 100,000.00
        List<NetWorthPoint> finalTrend = marketDataRepository.getDailyNetWorthTrend();
        NetWorthPoint pointJun2 = finalTrend.stream()
            .filter(p -> p.date().equals("2026-06-02"))
            .findFirst()
            .orElseThrow();

        assertEquals(100000.00, pointJun2.invested(), 0.001,
            "Final state: Net active capital change across rebalance trade pair (-88,121 sale proceeds + 88,121 buy gross amount) must equal EXACTLY 0.00");
    }

    @Test
    @DisplayName("Verify getBenchmarkLevelMetrics returns null on missing benchmark without fallback to INF109KC12U0")
    void testBenchmarkLevelMetricsReturnsNullWhenMissingWithoutFallback() {
        // Pre-condition: benchmark_history is empty, but nav_history contains INF109KC12U0
        marketDataRepository.saveLatestNavEntries(List.of(
            new com.portfolioos.core.nav.AmfiNavSync.NavEntry(null, "INF109KC12U0", "ICICI LargeMid 250", new BigDecimal("150.0"), LocalDate.parse("2026-01-01"))
        ));

        // Must NOT fallback to INF109KC12U0; must return null
        MarketDataRepository.BenchmarkLevelMetrics metrics = marketDataRepository.getBenchmarkLevelMetrics("NIFTY_500_TRI");
        assertNull(metrics, "Missing benchmark must return null immediately; silent fallback to INF109KC12U0 is prohibited");

        // Now save actual benchmark levels
        marketDataRepository.saveBenchmarkLevels("NIFTY_500_TRI", Map.of(
            "2026-01-01", 24000.0,
            "2026-01-02", 24500.0
        ));

        MarketDataRepository.BenchmarkLevelMetrics savedMetrics = marketDataRepository.getBenchmarkLevelMetrics("NIFTY_500_TRI");
        assertNotNull(savedMetrics);
        assertEquals(24500.0, savedMetrics.currentLevel());
        assertEquals(24500.0, savedMetrics.rollingHigh());
    }

    @Test
    @DisplayName("Verify NIFTY_500_TRI benchmark ingestion advances nifty_tri_watermark in TriggerHistoryRepository")
    void testBenchmarkIngestionAdvancesWatermark() {
        com.portfolioos.core.persistence.TriggerHistoryRepository triggerRepo = marketDataRepository.getTriggerHistoryRepository();
        assertNotNull(triggerRepo);

        // 1. Initial state: no watermark in DB
        assertTrue(triggerRepo.getNiftyTriWatermark().isEmpty());

        // 2. Ingest first benchmark batch (peak 25,000)
        marketDataRepository.saveBenchmarkLevels("NIFTY_500_TRI", Map.of(
            "2026-10-01", 24000.0,
            "2026-10-02", 25000.0,
            "2026-10-03", 24500.0
        ));

        // Watermark must advance to 25,000
        Optional<BigDecimal> wm1 = triggerRepo.getNiftyTriWatermark();
        assertTrue(wm1.isPresent());
        assertEquals(0, new BigDecimal("25000.0").compareTo(wm1.get()));

        // 3. Ingest drawdown batch (peak 23,000 - lower than current watermark)
        marketDataRepository.saveBenchmarkLevels("NIFTY_500_TRI", Map.of(
            "2026-10-04", 23000.0,
            "2026-10-05", 22500.0
        ));

        // Watermark must NOT decrease
        Optional<BigDecimal> wm2 = triggerRepo.getNiftyTriWatermark();
        assertTrue(wm2.isPresent());
        assertEquals(0, new BigDecimal("25000.0").compareTo(wm2.get()));

        // 4. Ingest new all-time high batch (peak 26,500)
        marketDataRepository.saveBenchmarkLevels("NIFTY_500_TRI", Map.of(
            "2026-10-06", 26500.0
        ));

        // Watermark must advance to 26,500
        Optional<BigDecimal> wm3 = triggerRepo.getNiftyTriWatermark();
        assertTrue(wm3.isPresent());
        assertEquals(0, new BigDecimal("26500.0").compareTo(wm3.get()));
    }
}
