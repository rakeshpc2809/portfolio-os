package com.portfolioos.core.nav;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioos.core.persistence.DuckDbProjector;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MfApiNavDownloader {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final Map<String, Long> isinToSchemeCodeMap = new HashMap<>();
    private boolean isMasterListLoaded = false;

    public MfApiNavDownloader() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    private synchronized void loadMasterListIfNecessary() {
        if (isMasterListLoaded) return;
        try {
            String masterUrl = "https://api.mfapi.in/mf";
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(masterUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode array = objectMapper.readTree(resp.body());
                if (array.isArray()) {
                    for (JsonNode item : array) {
                        long code = item.get("schemeCode").asLong();
                        JsonNode ig = item.get("isinGrowth");
                        JsonNode idiv = item.get("isinDivReinvestment");
                        if (ig != null && !ig.isNull() && !ig.asText().isBlank()) {
                            isinToSchemeCodeMap.put(ig.asText().trim(), code);
                        }
                        if (idiv != null && !idiv.isNull() && !idiv.asText().isBlank()) {
                            isinToSchemeCodeMap.put(idiv.asText().trim(), code);
                        }
                    }
                    isMasterListLoaded = true;
                    System.out.println("MfApiNavDownloader: Loaded master scheme list with " + isinToSchemeCodeMap.size() + " ISIN mappings.");
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load MFAPI master scheme list: " + e.getMessage());
        }
    }

    public void downloadHistoricalNavsForIsin(String isin, DuckDbProjector projector) {
        if (isin == null || isin.isBlank()) return;
        loadMasterListIfNecessary();

        Long schemeCode = isinToSchemeCodeMap.get(isin.trim());
        if (schemeCode == null) {
            System.err.println("No MFAPI scheme code found for ISIN " + isin);
            return;
        }

        try {
            // Fetch daily NAV history
            String navUrl = "https://api.mfapi.in/mf/" + schemeCode;
            HttpRequest navReq = HttpRequest.newBuilder()
                .uri(URI.create(navUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<String> navResp = httpClient.send(navReq, HttpResponse.BodyHandlers.ofString());
            if (navResp.statusCode() != 200) return;

            JsonNode navTree = objectMapper.readTree(navResp.body());
            JsonNode dataNode = navTree.get("data");
            if (dataNode == null || !dataNode.isArray()) return;

            int count = 0;
            for (JsonNode row : dataNode) {
                try {
                    String dateStr = row.get("date").asText();
                    BigDecimal navVal = new BigDecimal(row.get("nav").asText());
                    LocalDate date = LocalDate.parse(dateStr, DD_MM_YYYY);
                    projector.saveNavHistoryBatchForHeldAssets(
                        Map.of(isin, navVal),
                        Set.of(isin),
                        date
                    );
                    count++;
                } catch (Exception ignored) {}
            }
            System.out.println("Successfully backfilled " + count + " MFAPI historical NAV records for ISIN " + isin + " (Scheme " + schemeCode + ")");
        } catch (Exception e) {
            System.err.println("MFAPI historical NAV backfill error for ISIN " + isin + ": " + e.getMessage());
        }
    }

    public void downloadBenchmarkData(String benchmarkId, long schemeCode, DuckDbProjector projector) {
        try {
            String navUrl = "https://api.mfapi.in/mf/" + schemeCode;
            HttpRequest navReq = HttpRequest.newBuilder()
                .uri(URI.create(navUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> navResp = httpClient.send(navReq, HttpResponse.BodyHandlers.ofString());
            if (navResp.statusCode() != 200) return;

            JsonNode navTree = objectMapper.readTree(navResp.body());
            JsonNode dataNode = navTree.get("data");
            if (dataNode == null || !dataNode.isArray()) return;

            Map<String, Double> levels = new HashMap<>();
            for (JsonNode row : dataNode) {
                try {
                    String dateStr = row.get("date").asText();
                    double navVal = Double.parseDouble(row.get("nav").asText());
                    LocalDate date = LocalDate.parse(dateStr, DD_MM_YYYY);
                    levels.put(date.toString(), navVal);
                } catch (Exception ignored) {}
            }
            projector.saveBenchmarkLevels(benchmarkId, levels);
            System.out.println("Successfully ingested " + levels.size() + " benchmark level records for " + benchmarkId + " (Scheme " + schemeCode + ")");
        } catch (Exception e) {
            System.err.println("MFAPI benchmark download error for " + benchmarkId + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        DuckDbProjector projector = new DuckDbProjector();
        MfApiNavDownloader downloader = new MfApiNavDownloader();
        List<String> isins = List.of(
            "INF754K01TN5", "INF109K018C5", "INF109K016B1", "INF109KC12U0", "INF109KC13X2",
            "INF109K018M4", "INF205K01KR8", "INF174KA1TY2", "INF769K01ED6", "INF247L01916",
            "INF247L01BQ9", "INF247L01BM8", "INF204K01H36", "INF204K01K15", "INF204K01G52",
            "INF879O01027", "INF200K01UJ5", "INF200K01RA0", "INF277K011O1"
        );
        System.out.println("Starting MfApiNavDownloader verification across 19 holdings ISINs...");
        for (String isin : isins) {
            downloader.downloadHistoricalNavsForIsin(isin, projector);
        }
        downloader.downloadBenchmarkData("NIFTY_50_TRI", 120716, projector);
        downloader.downloadBenchmarkData("NIFTY_500_TRI", 147648, projector);
        projector.checkpoint();
        System.out.println("MfApiNavDownloader verification complete.");
    }
}
