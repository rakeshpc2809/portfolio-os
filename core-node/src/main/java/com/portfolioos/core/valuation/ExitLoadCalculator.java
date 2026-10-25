package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Computes exit-load-free units and market valuations per asset/lot.
 * Critical for capital deployment ladders (e.g. Invesco India Arbitrage Fund INF205K01KR8:
 * exit load free when holding_days >= 15; Parag Parikh Flexi Cap INF879O01027: holding_days >= 730).
 */
public class ExitLoadCalculator {

    public static final String ARBITRAGE_ISIN = "INF205K01KR8";
    public static final String PPFAS_FLEXICAP_ISIN = "INF879O01027";
    public static final String NIPPON_SMALLCAP_ISIN = "INF204K01K15";
    public static final String MOTILAL_GOLD_SILVER_ISIN = "INF247L01BM8";

    public static final int ARBITRAGE_EXIT_LOAD_DAYS = 15;
    public static final int PPFAS_EXIT_LOAD_DAYS = 730;
    public static final int NIPPON_SMALLCAP_EXIT_LOAD_DAYS = 365;

    private static final Map<String, Integer> SCHEME_EXIT_LOAD_DAYS = Map.of(
        ARBITRAGE_ISIN, 15,
        PPFAS_FLEXICAP_ISIN, 730,
        NIPPON_SMALLCAP_ISIN, 365,
        MOTILAL_GOLD_SILVER_ISIN, 15
    );

    public record ExitLoadSummary(
        String assetId,
        String assetName,
        BigDecimal totalUnits,
        BigDecimal exitLoadFreeUnits,
        BigDecimal lockedUnits,
        BigDecimal currentNav,
        BigDecimal totalValue,
        BigDecimal exitLoadFreeValue,
        BigDecimal lockedValue,
        double exitLoadFreePct,
        int exitLoadDaysRequired
    ) {}

    public static int getRequiredExitLoadDays(String assetId, String assetName) {
        if (assetId != null && SCHEME_EXIT_LOAD_DAYS.containsKey(assetId)) {
            return SCHEME_EXIT_LOAD_DAYS.get(assetId);
        }
        if (assetName != null) {
            String upper = assetName.toUpperCase();
            if (upper.contains("PARAG PARIKH") && upper.contains("FLEXI")) {
                return 730;
            }
            if (upper.contains("SMALL CAP") && !upper.contains("INDEX")) {
                return 365;
            }
            if (upper.contains("ARBITRAGE")) {
                return 15;
            }
            if (upper.contains("GOLD") || upper.contains("SILVER") || upper.contains("FOF")) {
                return 15;
            }
            if (upper.contains("LIQUID") || upper.contains("OVERNIGHT")) {
                return 7;
            }
        }
        return 0; // Default for direct passive index funds (0 exit load)
    }

    public static ExitLoadSummary calculateForAsset(
        String assetId,
        String assetName,
        List<Lot> lots,
        BigDecimal currentNav,
        LocalDate asOfDate
    ) {
        if (asOfDate == null) asOfDate = LocalDate.now();
        if (currentNav == null) currentNav = BigDecimal.ZERO;

        int requiredDays = getRequiredExitLoadDays(assetId, assetName);

        BigDecimal totalUnits = BigDecimal.ZERO;
        BigDecimal freeUnits = BigDecimal.ZERO;

        if (lots != null) {
            for (Lot lot : lots) {
                if (lot.remainingUnits().compareTo(BigDecimal.ZERO) <= 0) continue;
                totalUnits = totalUnits.add(lot.remainingUnits());
                long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), asOfDate);
                if (holdingDays >= requiredDays) {
                    freeUnits = freeUnits.add(lot.remainingUnits());
                }
            }
        }

        BigDecimal lockedUnits = totalUnits.subtract(freeUnits).max(BigDecimal.ZERO);
        BigDecimal totalValue = totalUnits.multiply(currentNav).setScale(2, RoundingMode.HALF_UP);
        BigDecimal freeValue = freeUnits.multiply(currentNav).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lockedValue = lockedUnits.multiply(currentNav).setScale(2, RoundingMode.HALF_UP);

        double freePct = totalUnits.compareTo(BigDecimal.ZERO) > 0
            ? freeUnits.divide(totalUnits, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
            : 100.0;

        return new ExitLoadSummary(
            assetId,
            assetName,
            totalUnits.setScale(3, RoundingMode.HALF_UP),
            freeUnits.setScale(3, RoundingMode.HALF_UP),
            lockedUnits.setScale(3, RoundingMode.HALF_UP),
            currentNav.setScale(4, RoundingMode.HALF_UP),
            totalValue,
            freeValue,
            lockedValue,
            freePct,
            requiredDays
        );
    }
}
