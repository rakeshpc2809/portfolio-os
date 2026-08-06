package com.portfolioos.core.matcher;

import java.util.List;

public class FundTierClassifier {

    private static final List<String> LEGACY_KEYWORDS = List.of(
        "EQUAL", "MIDCAP150", "NIFTY100_EW", "MIDCAP_150"
    );

    public static boolean isLegacyFund(String assetId, String assetName) {
        if (assetId == null) assetId = "";
        if (assetName == null) assetName = "";
        String probe = (assetId + " " + assetName).toUpperCase();
        return LEGACY_KEYWORDS.stream().anyMatch(probe::contains);
    }
}
