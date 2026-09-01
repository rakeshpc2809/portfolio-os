package com.portfolioos.core.nav;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioos.core.model.AssetCategory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AmfiTerFetcher {

    public record TerMetadata(double expenseRatio, String terStatus, String terAsOfDate) {}

    private static final Map<String, TerMetadata> cache = new ConcurrentHashMap<>();
    private static String globalAsOfDate = "Aug 2026";
    private static boolean isLoaded = false;

    public static synchronized void loadCacheIfNecessary() {
        if (isLoaded) return;
        try {
            File[] searchLocations = new File[] {
                new File("data/amfi_ter_cache.json"),
                new File("../data/amfi_ter_cache.json"),
                new File("../../data/amfi_ter_cache.json")
            };

            File targetFile = null;
            for (File f : searchLocations) {
                if (f.exists()) {
                    targetFile = f;
                    break;
                }
            }

            if (targetFile != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(targetFile);
                if (root.has("as_of_date")) {
                    globalAsOfDate = root.get("as_of_date").asText();
                }
                JsonNode schemes = root.get("schemes");
                if (schemes != null && schemes.isObject()) {
                    schemes.fields().forEachRemaining(entry -> {
                        String isin = entry.getKey();
                        JsonNode node = entry.getValue();
                        double ter = node.has("ter") ? node.get("ter").asDouble() : 0.20;
                        String status = node.has("status") ? node.get("status").asText() : "OPTIMAL";
                        cache.put(isin.toUpperCase(), new TerMetadata(ter, status, globalAsOfDate));
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: could not load amfi_ter_cache.json: " + e.getMessage());
        }
        isLoaded = true;
    }

    public static TerMetadata resolveTer(String assetId, String assetName, AssetCategory category) {
        loadCacheIfNecessary();

        if (assetId != null && cache.containsKey(assetId.toUpperCase())) {
            return cache.get(assetId.toUpperCase());
        }

        // Category-based SEBI Direct plan statutory benchmark defaults
        double defaultTer = switch (category) {
            case EQUITY -> 0.20;
            case DEBT_SPECIFIED_50AA -> 0.30;
            case GOLD_SILVER -> 0.12;
            case INTERNATIONAL -> 0.40;
            case SGB -> 0.00;
        };

        return new TerMetadata(defaultTer, "OPTIMAL", globalAsOfDate);
    }
}
