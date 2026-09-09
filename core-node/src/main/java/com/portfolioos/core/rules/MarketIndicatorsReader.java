package com.portfolioos.core.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MarketIndicatorsReader {

    private static final double DEFAULT_GSEC_10Y_YIELD_PCT = 7.10;
    private static final double DEFAULT_NIFTY50_PE = 22.40;
    private static final String DEFAULT_SOURCE_NOTES = "10Y G-Sec: CCIL Benchmark / RBI DBIE; Nifty 50 PE: NSE Daily Indices Disclosures";

    public record MarketIndicators(
        LocalDate asOfDate,
        double gsec10yYieldPct,
        double nifty50Pe,
        boolean isFallback,
        String sourceStatus,
        String sourceNotes
    ) {}

    public MarketIndicators readIndicators() {
        List<File> searchPaths = new ArrayList<>();
        String envDataDir = System.getenv("DATA_DIR");
        if (envDataDir != null && !envDataDir.isBlank()) {
            searchPaths.add(new File(envDataDir, "market_indicators.json"));
        }
        searchPaths.add(new File("data/market_indicators.json"));
        searchPaths.add(new File("../data/market_indicators.json"));
        searchPaths.add(new File("../../data/market_indicators.json"));
        searchPaths.add(new File("/app/data/market_indicators.json"));

        File targetFile = null;
        for (File path : searchPaths) {
            if (path.exists() && path.isFile()) {
                targetFile = path;
                break;
            }
        }

        if (targetFile == null) {
            return new MarketIndicators(
                LocalDate.now(),
                DEFAULT_GSEC_10Y_YIELD_PCT,
                DEFAULT_NIFTY50_PE,
                true,
                "STATUTORY_BENCHMARK_FALLBACK",
                DEFAULT_SOURCE_NOTES
            );
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(targetFile, Map.class);
            if (data == null || data.isEmpty()) {
                throw new IllegalStateException("Empty indicators file");
            }

            LocalDate asOf = data.containsKey("as_of_date") && data.get("as_of_date") != null
                ? LocalDate.parse(data.get("as_of_date").toString())
                : LocalDate.now();

            double gsec = data.containsKey("gsec_10y_yield_pct") && data.get("gsec_10y_yield_pct") != null
                ? ((Number) data.get("gsec_10y_yield_pct")).doubleValue()
                : DEFAULT_GSEC_10Y_YIELD_PCT;

            double pe = data.containsKey("nifty50_pe") && data.get("nifty50_pe") != null
                ? ((Number) data.get("nifty50_pe")).doubleValue()
                : DEFAULT_NIFTY50_PE;

            boolean isFallback = data.containsKey("is_fallback") && data.get("is_fallback") != null
                ? Boolean.parseBoolean(data.get("is_fallback").toString())
                : false;

            String status = data.containsKey("source_status") && data.get("source_status") != null
                ? data.get("source_status").toString()
                : "LIVE_INDICATOR";

            String notes = data.containsKey("source_notes") && data.get("source_notes") != null
                ? data.get("source_notes").toString()
                : DEFAULT_SOURCE_NOTES;

            return new MarketIndicators(asOf, gsec, pe, isFallback, status, notes);
        } catch (Exception e) {
            System.err.println("Failed to read market indicators from " + targetFile.getAbsolutePath() + ": " + e.getMessage());
            return new MarketIndicators(
                LocalDate.now(),
                DEFAULT_GSEC_10Y_YIELD_PCT,
                DEFAULT_NIFTY50_PE,
                true,
                "FALLBACK_ERROR",
                DEFAULT_SOURCE_NOTES
            );
        }
    }
}
