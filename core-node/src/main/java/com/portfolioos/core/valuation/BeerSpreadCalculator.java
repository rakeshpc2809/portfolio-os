package com.portfolioos.core.valuation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioos.core.dtos.RebalancePlanDtos.BeerSpreadContextDto;

import java.io.File;

public class BeerSpreadCalculator {

    public static final double SPREAD_EQUITY_EXPENSIVE_THRESHOLD = 2.50;

    public static BeerSpreadContextDto calculateCurrentSpread() {
        double gsecYield = 7.10;
        double niftyPe = 22.40;
        String asOfDate = "2026-08-31";
        boolean isFallback = true;
        String sourceStatus = "FALLBACK_CACHED";

        try {
            File[] searchLocations = new File[] {
                new File("data/market_indicators.json"),
                new File("../data/market_indicators.json"),
                new File("../../data/market_indicators.json")
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
                if (root.has("gsec_10y_yield_pct")) {
                    gsecYield = root.get("gsec_10y_yield_pct").asDouble();
                }
                if (root.has("nifty50_pe")) {
                    niftyPe = root.get("nifty50_pe").asDouble();
                }
                if (root.has("as_of_date")) {
                    asOfDate = root.get("as_of_date").asText();
                }
                if (root.has("is_fallback")) {
                    isFallback = root.get("is_fallback").asBoolean();
                }
                if (root.has("source_status")) {
                    sourceStatus = root.get("source_status").asText();
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: could not read market_indicators.json: " + e.getMessage());
        }

        return evaluateBeerSpread(gsecYield, niftyPe, asOfDate, isFallback, sourceStatus);
    }

    public static BeerSpreadContextDto evaluateBeerSpread(double gsecYield, double niftyPe, String asOfDate) {
        return evaluateBeerSpread(gsecYield, niftyPe, asOfDate, false, "LIVE_FETCH");
    }

    public static BeerSpreadContextDto evaluateBeerSpread(double gsecYield, double niftyPe, String asOfDate, boolean isFallback, String sourceStatus) {
        double earningsYield = niftyPe > 0 ? (1.0 / niftyPe) * 100.0 : 0.0;
        double spread = gsecYield - earningsYield;

        earningsYield = Math.round(earningsYield * 100.0) / 100.0;
        spread = Math.round(spread * 100.0) / 100.0;

        String valuationZone;
        if (spread > SPREAD_EQUITY_EXPENSIVE_THRESHOLD) {
            valuationZone = "EQUITY_EXPENSIVE";
        } else if (spread < 0.0) {
            valuationZone = "EQUITY_ATTRACTIVE";
        } else {
            valuationZone = "FAIR_VALUE";
        }

        return new BeerSpreadContextDto(
            gsecYield,
            niftyPe,
            earningsYield,
            spread,
            valuationZone,
            asOfDate != null ? asOfDate : "2026-08-31",
            isFallback,
            sourceStatus != null ? sourceStatus : "LIVE_FETCH"
        );
    }
}
