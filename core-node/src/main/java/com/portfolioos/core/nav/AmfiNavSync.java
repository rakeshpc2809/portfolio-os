package com.portfolioos.core.nav;

import org.springframework.stereotype.Component;

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

@Component
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

    public List<NavEntry> parseAmfiFeed(String feedContent) {
        List<NavEntry> entries = new ArrayList<>();
        LocalDate today = LocalDate.now();

        String[] lines = feedContent.split("\\r?\\n");
        for (String line : lines) {
            String[] parts = line.split(";");
            if (parts.length >= 5) {
                String schemeCode = parts[0].trim();
                String isin1 = parts.length > 1 ? parts[1].trim() : null;
                String isin2 = parts.length > 2 ? parts[2].trim() : null;
                String schemeName = parts.length > 3 ? parts[3].trim() : "";

                BigDecimal nav = null;
                for (int i = 4; i < parts.length; i++) {
                    try {
                        String token = parts[i].trim();
                        if (!token.isEmpty()) {
                            nav = new BigDecimal(token);
                            break;
                        }
                    } catch (Exception ignored) {}
                }

                if (nav != null) {
                    if (isin1 != null && !isin1.isEmpty() && !"-".equals(isin1)) {
                        entries.add(new NavEntry(schemeCode, isin1, schemeName, nav, today));
                    }
                    if (isin2 != null && !isin2.isEmpty() && !"-".equals(isin2) && !isin2.equalsIgnoreCase(isin1)) {
                        entries.add(new NavEntry(schemeCode, isin2, schemeName, nav, today));
                    }
                    if ((isin1 == null || isin1.isEmpty() || "-".equals(isin1)) && (isin2 == null || isin2.isEmpty() || "-".equals(isin2))) {
                        entries.add(new NavEntry(schemeCode, null, schemeName, nav, today));
                    }
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
                URI uri = new URI("https://portal.amfiindia.com/spages/NAVAll.txt");
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
            if (entry.isin() != null && entry.nav() != null) {
                navMap.put(entry.isin(), entry.nav());
            }
        }
        return navMap;
    }
}
