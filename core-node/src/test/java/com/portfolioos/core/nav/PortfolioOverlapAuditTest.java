package com.portfolioos.core.nav;

import com.portfolioos.core.persistence.DuckDbProjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioOverlapAuditTest {

    private DuckDbProjector projector;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("test_overlap.duckdb").toFile();
        projector = new DuckDbProjector(dbFile.getAbsolutePath());
        new NseIndexConstituentDownloader().seedStandardIndexConstituents(projector);
    }

    @Test
    @DisplayName("Structural Invariant: No two distinct canonical fund ISINs have duplicate holdings distributions")
    void testNoDuplicateFundHoldingsDistributions() throws Exception {
        // Collect holding fingerprint (sorted symbol:weight string) per fund_id
        Map<String, String> fundFingerprints = new HashMap<>();

        try (Connection conn = projector.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT fund_id, STRING_AGG(stock_symbol || ':' || CAST(ROUND(weight_pct, 2) AS VARCHAR), ',' ORDER BY stock_symbol) AS fingerprint " +
                 "FROM fund_holdings " +
                 "GROUP BY fund_id"
             )) {
            while (rs.next()) {
                String fundId = rs.getString("fund_id");
                String fingerprint = rs.getString("fingerprint");
                fundFingerprints.put(fundId, fingerprint);
            }
        }

        // List of distinct canonical ISINs in portfolio (excluding numeric AMFI scheme code aliases)
        List<String> canonicalIsins = fundFingerprints.keySet().stream()
            .filter(id -> id.startsWith("INF"))
            .toList();

        for (int i = 0; i < canonicalIsins.size(); i++) {
            for (int j = i + 1; j < canonicalIsins.size(); j++) {
                String isinA = canonicalIsins.get(i);
                String isinB = canonicalIsins.get(j);
                String fpA = fundFingerprints.get(isinA);
                String fpB = fundFingerprints.get(isinB);

                assertNotEquals(fpA, fpB,
                    String.format("CRITICAL STRUCTURAL ERROR: %s and %s share identical holdings distribution: %s", isinA, isinB, fpA));
            }
        }
    }

    @Test
    @DisplayName("Deterministic Point Assertions: Verified pairwise overlap percentages match mathematical expectations")
    void testPairwiseOverlapExactMathematicalValues() {
        // 1. PPFAS (INF879O01027) vs Nifty 200 Value 30 (INF109KC13X2)
        // If full factsheet parsed: HDFCBANK (7.82) + ICICIBANK (5.92) + POWERGRID (4.12) + COALINDIA (3.75) + NTPC (1.95) = 23.56% (5 stocks)
        // If fallback sample: HDFCBANK (7.45) + ICICIBANK (5.40) + COALINDIA (3.50) = 16.35% (3 stocks)
        Map<String, Object> ppfasVal30 = projector.getPairwiseFundOverlap("INF879O01027", "INF109KC13X2");
        double ppfasVal30Overlap = ((Number) ppfasVal30.get("overlap_percentage")).doubleValue();
        int ppfasVal30Count = ((Number) ppfasVal30.get("common_stock_count")).intValue();
        boolean hasPpfasFactsheet = new File("/app/data/factsheets/ppfas_flexicap_full.xlsx").exists()
            || new File("data/factsheets/ppfas_flexicap_full.xlsx").exists()
            || new File("../data/factsheets/ppfas_flexicap_full.xlsx").exists();
        if (hasPpfasFactsheet) {
            assertEquals(23.56, ppfasVal30Overlap, 0.01, "PPFAS vs Value 30 overlap with full factsheet must equal 23.56%");
            assertEquals(5, ppfasVal30Count, "PPFAS vs Value 30 with full factsheet must share 5 common stocks");
        } else {
            assertEquals(16.35, ppfasVal30Overlap, 0.01, "PPFAS vs Value 30 overlap with sample must equal 16.35%");
            assertEquals(3, ppfasVal30Count, "PPFAS vs Value 30 with sample must share 3 common stocks");
        }

        // 2. LargeMidcap 250 (INF109KC12U0) vs Nifty 200 Value 30 (INF109KC13X2)
        // Common: RELIANCE (6.85), HDFCBANK (6.42), ICICIBANK (5.10) = 18.37%
        Map<String, Object> lm250Val30 = projector.getPairwiseFundOverlap("INF109KC12U0", "INF109KC13X2");
        double lm250Val30Overlap = ((Number) lm250Val30.get("overlap_percentage")).doubleValue();
        int lm250Val30Count = ((Number) lm250Val30.get("common_stock_count")).intValue();
        assertEquals(18.37, lm250Val30Overlap, 0.01, "LargeMidcap 250 vs Value 30 overlap must exactly equal 18.37%");
        assertEquals(3, lm250Val30Count, "LargeMidcap 250 vs Value 30 must share exactly 3 common stocks");

        // 3. LargeMidcap 250 (INF109KC12U0) vs Multicap Mom Qual 50 (INF754K01TN5)
        // Common: BHARTIARTL (3.20), DIXON (1.80), PERSISTENT (1.75), COFORGE (1.65), TRENT (2.15) = 10.55%
        Map<String, Object> lm250Mq50 = projector.getPairwiseFundOverlap("INF109KC12U0", "INF754K01TN5");
        double lm250Mq50Overlap = ((Number) lm250Mq50.get("overlap_percentage")).doubleValue();
        int lm250Mq50Count = ((Number) lm250Mq50.get("common_stock_count")).intValue();
        assertEquals(10.55, lm250Mq50Overlap, 0.01, "LargeMidcap 250 vs Mom Qual 50 overlap must exactly equal 10.55%");
        assertEquals(5, lm250Mq50Count, "LargeMidcap 250 vs Mom Qual 50 must share exactly 5 common stocks");

        // 4. Gold and Silver FoF (INF247L01BM8) must have 0.0% overlap with all equity funds (re-regression guard)
        Map<String, Object> goldVal30 = projector.getPairwiseFundOverlap("INF247L01BM8", "INF109KC13X2");
        double goldVal30Overlap = ((Number) goldVal30.get("overlap_percentage")).doubleValue();
        assertEquals(0.0, goldVal30Overlap, "Gold and Silver FoF must have 0.0% overlap with Value 30");

        Map<String, Object> goldPpfas = projector.getPairwiseFundOverlap("INF247L01BM8", "INF879O01027");
        double goldPpfasOverlap = ((Number) goldPpfas.get("overlap_percentage")).doubleValue();
        assertEquals(0.0, goldPpfasOverlap, "Gold and Silver FoF must have 0.0% overlap with PPFAS");

        // 5. Debt Gilt Fund (INF109K018C5) must have 0.0% overlap with all equity funds
        Map<String, Object> giltVal30 = projector.getPairwiseFundOverlap("INF109K018C5", "INF109KC13X2");
        double giltVal30Overlap = ((Number) giltVal30.get("overlap_percentage")).doubleValue();
        assertEquals(0.0, giltVal30Overlap, "Gilt Fund must have 0.0% overlap with equity funds");
    }

    @Test
    @DisplayName("Stock Concentration Provenance: Audited-only default filters unverified funds & computes accurate telemetry")
    void testStockConcentrationsAuditedOnlyVsIncludeUnverified() {
        Map<String, Double> fundValuations = new HashMap<>();
        fundValuations.put("INF109KC13X2", 100000.0);  // Value 30 (Audited Index)
        fundValuations.put("INF879O01027", 150000.0);  // PPFAS (Audited Factsheet)
        fundValuations.put("INF277K011O1", 1000000.0); // Tata Small Cap (Unverified Sample, 10L AUM)

        // 1. Audited-only mode (Default: includeUnverified = false)
        Map<String, Object> auditedResult = projector.getPortfolioStockConcentrations(fundValuations, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> auditedTelemetry = (Map<String, Object>) auditedResult.get("coverage_telemetry");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> auditedStocks = (List<Map<String, Object>>) auditedResult.get("concentrations");

        assertNotNull(auditedTelemetry);
        assertEquals(1250000.0, ((Number) auditedTelemetry.get("total_equity_aum")).doubleValue(), 0.01);
        assertEquals(250000.0, ((Number) auditedTelemetry.get("audited_aum")).doubleValue(), 0.01);
        assertEquals(1000000.0, ((Number) auditedTelemetry.get("unverified_aum")).doubleValue(), 0.01);
        assertEquals(20.0, ((Number) auditedTelemetry.get("audited_coverage_pct")).doubleValue(), 0.1);
        assertEquals(false, auditedTelemetry.get("include_unverified"));
        assertFalse(auditedStocks.isEmpty());
        assertTrue(auditedStocks.stream().allMatch(s -> Boolean.TRUE.equals(s.get("is_audited"))));

        // 2. Opt-in mode (includeUnverified = true)
        Map<String, Object> fullResult = projector.getPortfolioStockConcentrations(fundValuations, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> fullTelemetry = (Map<String, Object>) fullResult.get("coverage_telemetry");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fullStocks = (List<Map<String, Object>>) fullResult.get("concentrations");

        assertNotNull(fullTelemetry);
        assertEquals(true, fullTelemetry.get("include_unverified"));
        assertFalse(fullStocks.isEmpty());
        // Tata Small Cap top holding (BASF with 31,000 INR exposure) should be #1 in full result but absent in audited-only
        boolean hasBasfInFull = fullStocks.stream().anyMatch(s -> "BASF".equals(s.get("stock_symbol")));
        boolean hasBasfInAudited = auditedStocks.stream().anyMatch(s -> "BASF".equals(s.get("stock_symbol")));
        assertTrue(hasBasfInFull, "BASF from unverified Tata Small Cap should be present when includeUnverified=true");
        assertFalse(hasBasfInAudited, "BASF from unverified Tata Small Cap must NOT be present when includeUnverified=false");
    }

    @Test
    @DisplayName("Pairwise Overlap: Correctly tags source_type and is_unverified_estimate flag")
    void testPairwiseOverlapProvenanceTags() {
        // Audited pair: Value 30 (NSE Index) vs PPFAS (Factsheet)
        Map<String, Object> auditedPair = projector.getPairwiseFundOverlap("INF879O01027", "INF109KC13X2");
        assertEquals("FACTSHEET_POI_PARSED", auditedPair.get("source_type_a"));
        assertEquals("NSE_INDEX_CONSTITUENTS", auditedPair.get("source_type_b"));
        assertEquals(false, auditedPair.get("is_unverified_estimate"));

        // Unverified pair: Tata Small Cap (Unverified) vs Value 30 (NSE Index)
        Map<String, Object> unverifiedPair = projector.getPairwiseFundOverlap("INF277K011O1", "INF109KC13X2");
        assertEquals("MANUAL_ESTIMATE_UNVERIFIED", unverifiedPair.get("source_type_a"));
        assertEquals("NSE_INDEX_CONSTITUENTS", unverifiedPair.get("source_type_b"));
        assertEquals(true, unverifiedPair.get("is_unverified_estimate"));
    }
}
