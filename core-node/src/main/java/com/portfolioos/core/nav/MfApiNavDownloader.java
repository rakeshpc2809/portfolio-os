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

    public MfApiNavDownloader() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    public void downloadHistoricalNavsForIsin(String isin, DuckDbProjector projector) {
        if (isin == null || isin.isBlank()) return;
        try {
            // Search scheme code by ISIN
            String searchUrl = "https://api.mfapi.in/mf/search?q=" + isin;
            HttpRequest searchReq = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<String> searchResp = httpClient.send(searchReq, HttpResponse.BodyHandlers.ofString());
            if (searchResp.statusCode() != 200) return;

            JsonNode searchTree = objectMapper.readTree(searchResp.body());
            if (!searchTree.isArray() || searchTree.isEmpty()) return;

            long schemeCode = searchTree.get(0).get("schemeCode").asLong();

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

            Map<String, BigDecimal> navBatch = new HashMap<>();
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
                } catch (Exception ignored) {}
            }
            System.out.println("Successfully backfilled MFAPI historical NAVs for ISIN " + isin);
        } catch (Exception e) {
            System.err.println("MFAPI historical NAV backfill error for ISIN " + isin + ": " + e.getMessage());
        }
    }
}
