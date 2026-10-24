package com.portfolioos.core.integration;

import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.persistence.DuckDbProjector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DuckDbProjectorIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("DuckDB Projector correctly projects tax events and computes daily net worth trend")
    void testDuckDbEventProjectionAndTrend() {
        TaxEvent acq1 = new TaxEvent(
            "EV_DUCK_1",
            ISIN_CORE_PPFAS,
            "Parag Parikh Flexi Cap Fund",
            "PPFAS_1",
            EventType.ACQUISITION,
            LocalDate.of(2024, 1, 1),
            new BigDecimal("100.0"),
            new BigDecimal("50.0"),
            new BigDecimal("5000.0"),
            "CAS_IMPORT",
            Instant.now()
        );

        TaxEvent acq2 = new TaxEvent(
            "EV_DUCK_2",
            ISIN_LIQUID_ARBITRAGE,
            "Invesco India Arbitrage Fund",
            "INVESCO_1",
            EventType.ACQUISITION,
            LocalDate.of(2024, 1, 15),
            new BigDecimal("200.0"),
            new BigDecimal("25.0"),
            new BigDecimal("5000.0"),
            "CAS_IMPORT",
            Instant.now()
        );

        duckDbProjector.projectEvents(List.of(acq1, acq2));

        // Save NAV points
        duckDbProjector.saveLatestNavEntries(List.of(
            new com.portfolioos.core.nav.AmfiNavSync.NavEntry(null, ISIN_CORE_PPFAS, "PPFAS", new BigDecimal("55.0"), LocalDate.of(2024, 1, 20)),
            new com.portfolioos.core.nav.AmfiNavSync.NavEntry(null, ISIN_LIQUID_ARBITRAGE, "Arbitrage", new BigDecimal("26.0"), LocalDate.of(2024, 1, 20))
        ));

        List<DuckDbProjector.NetWorthPoint> trend = duckDbProjector.getDailyNetWorthTrend();
        assertNotNull(trend, "Daily net worth trend list must not be null");
        assertFalse(trend.isEmpty(), "Daily net worth trend must contain projected points");

        DuckDbProjector.NetWorthPoint latest = trend.get(trend.size() - 1);
        assertTrue(latest.valuation() > 0.0, "Projected valuation must be strictly positive");
        assertTrue(latest.invested() > 0.0, "Projected invested capital must be strictly positive");
    }

    @Test
    @DisplayName("DuckDB Projector correctly exports projected tables to Parquet")
    void testExportTableToParquet() throws Exception {
        java.io.File tempParquet = java.io.File.createTempFile("test_duckdb_export_", ".parquet");
        try {
            duckDbProjector.exportTableToParquet("projected_events", tempParquet);
            assertTrue(tempParquet.exists(), "Exported parquet file must exist");
            assertTrue(tempParquet.length() > 0, "Exported parquet file must have non-zero size");
        } finally {
            if (tempParquet.exists()) {
                tempParquet.delete();
            }
        }
    }
}

