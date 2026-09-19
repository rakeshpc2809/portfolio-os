package com.portfolioos.core.valuation;

import com.portfolioos.core.dtos.RebalancePlanDtos.GoldSilverContextDto;

import java.math.BigDecimal;
import java.util.Map;

public class GoldSilverRatioCalculator {

    public static final double RATIO_SILVER_UNDERVALUED_THRESHOLD = 80.0;
    public static final double RATIO_GOLD_UNDERVALUED_THRESHOLD = 65.0;
    public static final double STATUTORY_BENCHMARK_RATIO = 84.50;

    public static GoldSilverContextDto calculateRatio(Map<String, BigDecimal> navMap) {
        return calculateRatio(navMap, null);
    }

    public static GoldSilverContextDto calculateRatio(Map<String, BigDecimal> navMap, Map<String, java.time.LocalDate> navDateMap) {
        double goldNav = 0.0;
        double silverNav = 0.0;
        String goldKey = null;
        String silverKey = null;

        if (navMap != null) {
            for (Map.Entry<String, BigDecimal> entry : navMap.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().doubleValue() <= 0) continue;
                String key = entry.getKey().toUpperCase();

                // Check Gold ETF (filter out FoF / combined funds)
                if (key.equals("GOLDBEES") || key.equals("INF204KB17I5") || key.equals("INF204KB14I2") || key.equals("INF204K01H36") 
                        || key.equals("INF179KC1981") || key.equals("INF109KC1NT3") || (key.contains("GOLD") && key.contains("ETF") && !key.contains("FOF") && !key.contains("FUND OF FUND"))) {
                    if (goldNav == 0.0) {
                        goldNav = entry.getValue().doubleValue();
                        goldKey = entry.getKey();
                    }
                }

                // Check Silver ETF (filter out FoF / combined funds)
                if (key.equals("SILVERBEES") || key.equals("INF204KC1402") || key.equals("INF204KB18R3") 
                        || key.equals("INF179KC1DI2") || key.equals("INF109KC1Y56") || (key.contains("SILVER") && key.contains("ETF") && !key.contains("FOF") && !key.contains("FUND OF FUND"))) {
                    if (silverNav == 0.0) {
                        silverNav = entry.getValue().doubleValue();
                        silverKey = entry.getKey();
                    }
                }
            }
        }

        if (goldNav > 0 && silverNav > 0) {
            // Gold ETF represents 0.01g gold per unit; Silver ETF represents 1g silver per unit
            // Normalized 1g Gold to 1g Silver ratio = (goldNav * 100) / silverNav
            double normalizedGoldPricePerGram = goldNav >= 1000 ? goldNav : goldNav * 100.0;
            double normalizedSilverPricePerGram = silverNav;
            double ratio = normalizedGoldPricePerGram / normalizedSilverPricePerGram;

            if (navDateMap != null && goldKey != null && silverKey != null
                    && navDateMap.containsKey(goldKey) && navDateMap.containsKey(silverKey)
                    && navDateMap.get(goldKey) != null && navDateMap.get(silverKey) != null) {
                java.time.LocalDate gDate = navDateMap.get(goldKey);
                java.time.LocalDate sDate = navDateMap.get(silverKey);
                java.time.LocalDate maxDate = gDate.isAfter(sDate) ? gDate : sDate;
                return evaluateRatio(ratio, false, "LIVE_AMFI_ETF_SPOT", maxDate.toString());
            } else {
                return evaluateRatio(ratio, true, "STATUTORY_BENCHMARK_ESTIMATE", null);
            }
        } else {
            // When specific separate ETFs are absent from user ledger / offline test fixtures
            return evaluateRatio(STATUTORY_BENCHMARK_RATIO, true, "STATUTORY_BENCHMARK_ESTIMATE", null);
        }
    }

    public static GoldSilverContextDto evaluateRatio(double ratio) {
        return evaluateRatio(ratio, true, "STATUTORY_BENCHMARK_ESTIMATE", null);
    }

    public static GoldSilverContextDto evaluateRatio(double ratio, boolean isEstimated, String source, String asOfDate) {
        ratio = Math.round(ratio * 10.0) / 10.0;
        String signal;
        double goldTargetSplitPct;
        double silverTargetSplitPct;

        if (ratio >= RATIO_SILVER_UNDERVALUED_THRESHOLD) {
            signal = "SILVER_UNDERVALUED";
            goldTargetSplitPct = 40.0;
            silverTargetSplitPct = 60.0;
        } else if (ratio <= RATIO_GOLD_UNDERVALUED_THRESHOLD) {
            signal = "GOLD_UNDERVALUED";
            goldTargetSplitPct = 60.0;
            silverTargetSplitPct = 40.0;
        } else {
            signal = "NEUTRAL";
            goldTargetSplitPct = 50.0;
            silverTargetSplitPct = 50.0;
        }

        return new GoldSilverContextDto(
            ratio,
            signal,
            goldTargetSplitPct,
            silverTargetSplitPct,
            isEstimated,
            source != null ? source : "STATUTORY_BENCHMARK_ESTIMATE",
            asOfDate
        );
    }
}
