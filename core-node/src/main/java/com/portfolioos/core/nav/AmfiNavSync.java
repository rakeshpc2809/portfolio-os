package com.portfolioos.core.nav;

import com.portfolioos.core.persistence.DuckDbProjector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLConnection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AmfiNavSync {

    public record NavEntry(
        String schemeCode,
        String isin,
        String schemeName,
        BigDecimal nav,
        LocalDate date
    ) {}

    private static final long CACHE_TTL_MS = 6 * 3600 * 1000L;
    private static final Object lock = new Object();
    private static List<NavEntry> cachedNavs = null;
    private static long lastFetchTimeMs = 0L;
    private static final DuckDbProjector duckDbProjector = new DuckDbProjector();

    public List<NavEntry> parseAmfiFeed(String feedContent) {
        List<NavEntry> entries = new ArrayList<>();
        LocalDate today = LocalDate.now();

        String[] lines = feedContent.split("\\r?\\n");
        for (String line : lines) {
            String[] parts = line.split(";");
            if (parts.length >= 6) {
                String schemeCode = parts[0].trim();
                String isinGrowth = parts[1].trim();
                if (isinGrowth.isEmpty()) {
                    isinGrowth = null;
                }
                String schemeName = parts[3].trim();
                String navStr = parts[4].trim();

                try {
                    BigDecimal nav = new BigDecimal(navStr);
                    entries.add(new NavEntry(
                        schemeCode,
                        isinGrowth,
                        schemeName,
                        nav,
                        today
                    ));
                } catch (Exception e) {
                    // Skip headers or corrupted rows
                }
            }
        }
        return entries;
    }

    public List<NavEntry> fetchLatestNavsFromAmfi() {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (cachedNavs != null && (now - lastFetchTimeMs) < CACHE_TTL_MS) {
                return cachedNavs;
            }

            try {
                URI uri = new URI("https://www.amfiindia.com/spages/NAVAll.txt");
                URLConnection conn = uri.toURL().openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }

                List<NavEntry> parsed = parseAmfiFeed(sb.toString());
                if (!parsed.isEmpty()) {
                    cachedNavs = parsed;
                    lastFetchTimeMs = System.currentTimeMillis();
                }
                return parsed;
            } catch (Exception e) {
                System.err.println("AMFI fetch error: " + e.getMessage());
                return cachedNavs != null ? cachedNavs : new ArrayList<>();
            }
        }
    }

    public Map<String, BigDecimal> getNavMap() {
        List<NavEntry> entries = fetchLatestNavsFromAmfi();
        Map<String, BigDecimal> navMap = new HashMap<>();
        for (NavEntry entry : entries) {
            if (entry.isin() != null) {
                navMap.put(entry.isin(), entry.nav());
            }
        }
        
        // Persist daily NAV history to DuckDB nav_history
        duckDbProjector.saveNavHistoryBatch(navMap, LocalDate.now());
        
        return navMap;
    }
}
