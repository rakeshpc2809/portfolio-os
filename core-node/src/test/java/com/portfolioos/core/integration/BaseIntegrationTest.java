package com.portfolioos.core.integration;

import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.service.LedgerCacheService;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected EventStorePort eventStore;

    @Autowired
    protected DuckDbProjector duckDbProjector;

    @Autowired
    protected LedgerCacheService ledgerCacheService;

    public static final String AUTH_TOKEN = "dev_secret_key_123";

    // Canonical confirmed ISINs
    public static final String ISIN_CORE_PPFAS = "INF209K01164";
    public static final String ISIN_CORE_LARGEMID = "INF109KC12U0";
    public static final String ISIN_SATELLITE_MIDCAP = "INF247L01676";
    public static final String ISIN_SATELLITE_SMALLCAP = "INF204K01X36";
    public static final String ISIN_GOLD_SILVER = "INF247L01AA3";
    public static final String ISIN_LIQUID_ARBITRAGE = "INF205K01KR8"; // Invesco Arbitrage canonical
    public static final String ISIN_LEGACY_HEALTHCARE = "INF769K01EZ5"; // Legacy holding for Step 0 waterfall test

    private static final String TEST_SQLITE_PATH = "target/test-db/test_tax_ledger.db";
    private static final String TEST_DUCKDB_PATH = "target/test-db/test_tax_ledger.duckdb";

    @BeforeAll
    static void initTestDir() {
        File dir = new File("target/test-db");
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @DynamicPropertySource
    static void configureDynamicProperties(DynamicPropertyRegistry registry) {
        initTestDir();
        registry.add("sqlite.path", () -> TEST_SQLITE_PATH);
        registry.add("duckdb.path", () -> TEST_DUCKDB_PATH);
    }

    protected synchronized void seedCanonicalPortfolioState() {
        eventStore.clearAllEvents();
        List<TaxEvent> events = new ArrayList<>();
        Map<String, BigDecimal> navMap = new HashMap<>();

        // 1. Core Equity: Parag Parikh Flexi Cap (INF209K01164) -> Target 50%
        events.add(new TaxEvent(
            "EV_CORE_1",
            ISIN_CORE_PPFAS,
            "Parag Parikh Flexi Cap Fund - Direct Plan Growth",
            "PPFAS_FOLIO_1",
            EventType.ACQUISITION,
            LocalDate.of(2024, 1, 15),
            new BigDecimal("5000.0"),
            new BigDecimal("50.0"),
            new BigDecimal("250000.0"),
            "CAS_IMPORT",
            Instant.now()
        ));
        navMap.put(ISIN_CORE_PPFAS, new BigDecimal("70.0"));

        // 2. Satellite Equity: Motilal Midcap 150 (INF247L01676) -> Target 30%
        events.add(new TaxEvent(
            "EV_SAT_1",
            ISIN_SATELLITE_MIDCAP,
            "Motilal Oswal Nifty Midcap 150 Index Fund Direct Plan Growth",
            "MOTILAL_FOLIO_1",
            EventType.ACQUISITION,
            LocalDate.of(2024, 2, 1),
            new BigDecimal("3000.0"),
            new BigDecimal("40.0"),
            new BigDecimal("120000.0"),
            "CAS_IMPORT",
            Instant.now()
        ));
        navMap.put(ISIN_SATELLITE_MIDCAP, new BigDecimal("60.0"));

        // 3. Gold & Silver: Motilal Gold & Silver Passive FoF (INF247L01AA3) -> Target 10%
        events.add(new TaxEvent(
            "EV_GOLD_1",
            ISIN_GOLD_SILVER,
            "Motilal Oswal Gold and Silver Passive Fund of Funds Direct Growth",
            "MOTILAL_GOLD_FOLIO",
            EventType.ACQUISITION,
            LocalDate.of(2024, 3, 1),
            new BigDecimal("5000.0"),
            new BigDecimal("12.0"),
            new BigDecimal("60000.0"),
            "CAS_IMPORT",
            Instant.now()
        ));
        navMap.put(ISIN_GOLD_SILVER, new BigDecimal("14.0"));

        // 4. Liquid Buffer: Invesco India Arbitrage Fund (INF205K01KR8) -> Target 10%
        events.add(new TaxEvent(
            "EV_LIQ_1",
            ISIN_LIQUID_ARBITRAGE,
            "Invesco India Arbitrage Fund - Direct Plan Growth",
            "INVESCO_FOLIO_1",
            EventType.ACQUISITION,
            LocalDate.of(2024, 4, 1),
            new BigDecimal("2000.0"),
            new BigDecimal("30.0"),
            new BigDecimal("60000.0"),
            "CAS_IMPORT",
            Instant.now()
        ));
        navMap.put(ISIN_LIQUID_ARBITRAGE, new BigDecimal("35.0"));

        // 5. Legacy Fund: Mirae Asset Healthcare Fund (INF769K01EZ5) -> For Step 0 Waterfall test
        events.add(new TaxEvent(
            "EV_LEGACY_1",
            ISIN_LEGACY_HEALTHCARE,
            "Mirae Asset Healthcare Fund - Direct Plan Growth",
            "MIRAE_FOLIO_1",
            EventType.ACQUISITION,
            LocalDate.of(2023, 5, 10),
            new BigDecimal("1000.0"),
            new BigDecimal("25.0"),
            new BigDecimal("25000.0"),
            "CAS_IMPORT",
            Instant.now()
        ));
        navMap.put(ISIN_LEGACY_HEALTHCARE, new BigDecimal("30.0"));

        eventStore.appendEvents(events);
        duckDbProjector.projectEvents(events);

        FifoMatcher matcher = new FifoMatcher();
        FifoMatcher.FifoResult fifoResult = matcher.processEvents(events);

        LedgerCacheService.CachedLedgerState state = new LedgerCacheService.CachedLedgerState(
            events,
            fifoResult,
            navMap,
            eventStore.getLatestEventHash(),
            System.currentTimeMillis(),
            "HEALTHY"
        );
        ledgerCacheService.setCachedState(state);
    }
}
