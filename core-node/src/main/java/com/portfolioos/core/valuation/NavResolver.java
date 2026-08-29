package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;
import java.math.BigDecimal;
import java.util.Map;

public final class NavResolver {

    private NavResolver() {}

    public static BigDecimal requireValidNav(Map<String, BigDecimal> navMap, String assetId, String assetName, String context) {
        if (navMap == null || !navMap.containsKey(assetId) || navMap.get(assetId) == null || navMap.get(assetId).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(String.format(
                "CRITICAL VALUATION ERROR: Missing or invalid live NAV for asset ISIN: %s (%s) in %s.",
                assetId != null ? assetId : "UNKNOWN_ISIN",
                assetName != null ? assetName : "UNKNOWN_ASSET",
                context
            ));
        }
        return navMap.get(assetId);
    }

    public static BigDecimal requireValidNav(Map<String, BigDecimal> navMap, Lot lot, String context) {
        if (lot == null) {
            throw new IllegalStateException("CRITICAL VALUATION ERROR: Lot cannot be null in " + context + ".");
        }
        return requireValidNav(navMap, lot.assetId(), lot.assetName(), context);
    }
}
