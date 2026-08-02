package com.portfolioos.core.matcher;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class TaxClassifier {

    private static final Map<String, AssetCategory> isinCategoryRegistry = new ConcurrentHashMap<>();

    static {
        // Pre-registered ISINs and Ticker Symbols
        isinCategoryRegistry.put("MAHKTECH", AssetCategory.INTERNATIONAL);
        isinCategoryRegistry.put("MON100", AssetCategory.INTERNATIONAL);
        isinCategoryRegistry.put("MASPTOP50", AssetCategory.INTERNATIONAL);
        isinCategoryRegistry.put("INF109KA1VY6", AssetCategory.INTERNATIONAL);
        isinCategoryRegistry.put("INF247L01793", AssetCategory.INTERNATIONAL);
        isinCategoryRegistry.put("GOLDBEES", AssetCategory.GOLD_SILVER);
        isinCategoryRegistry.put("SILVERBEES", AssetCategory.GOLD_SILVER);
    }

    private static final Pattern sgbPattern = Pattern.compile("(?:SGB|SOVEREIGN GOLD)", Pattern.CASE_INSENSITIVE);
    private static final Pattern debtPattern = Pattern.compile("(?:GILT|BOND|DEBT|LIQUID|OVERNIGHT|TREASURY)", Pattern.CASE_INSENSITIVE);
    private static final Pattern goldSilverPattern = Pattern.compile("(?:GOLD|SILVER)", Pattern.CASE_INSENSITIVE);
    private static final Pattern intlPattern = Pattern.compile("(?:NASDAQ|S&P|INTERNATIONAL|GLOBAL|US EQUITIES|MAHKTECH|HANG SENG|MON100|MASPTOP50|ASIA|EMERGING|CHINA)", Pattern.CASE_INSENSITIVE);
    private static final Pattern listedPattern = Pattern.compile("(?:ETF|BEES|MON100|MASPTOP50|MAHKTECH|NIFTY|SENSEX)", Pattern.CASE_INSENSITIVE);

    public static void registerAssetCategory(String isinOrAssetId, AssetCategory category) {
        isinCategoryRegistry.put(isinOrAssetId.toUpperCase(), category);
    }

    public static void registerAssetCategories(Map<String, AssetCategory> mappings) {
        mappings.forEach((key, cat) -> isinCategoryRegistry.put(key.toUpperCase(), cat));
    }

    public static AssetCategory detectCategory(String assetId, String assetName) {
        String idUpper = assetId.toUpperCase();
        String nameUpper = assetName.toUpperCase();

        // 1. Primary lookup: Explicit registry
        if (isinCategoryRegistry.containsKey(idUpper)) return isinCategoryRegistry.get(idUpper);
        if (isinCategoryRegistry.containsKey(nameUpper)) return isinCategoryRegistry.get(nameUpper);

        // 2. Secondary fallback: Regex heuristics
        if (sgbPattern.matcher(nameUpper).find()) return AssetCategory.SGB;
        if (debtPattern.matcher(nameUpper).find()) return AssetCategory.DEBT_SPECIFIED_50AA;
        if (goldSilverPattern.matcher(nameUpper).find()) return AssetCategory.GOLD_SILVER;
        if (intlPattern.matcher(nameUpper).find()) return AssetCategory.INTERNATIONAL;

        return AssetCategory.EQUITY;
    }

    public static boolean isListed(String assetId, String assetName) {
        String combined = (assetId + " " + assetName).toUpperCase();
        return listedPattern.matcher(combined).find();
    }

    public static TaxTerm classifyTaxTerm(AssetCategory category, long holdingDays, String fiscalYear, boolean isListed) {
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
        return switch (category) {
            case DEBT_SPECIFIED_50AA -> TaxTerm.SHORT_TERM; // Sec 50AA: Always Short-Term
            case EQUITY -> {
                if (holdingDays >= rules.equityLtcgThresholdDays()) {
                    yield TaxTerm.LONG_TERM;
                } else {
                    yield TaxTerm.SHORT_TERM;
                }
            }
            case GOLD_SILVER, INTERNATIONAL -> {
                // Per Finance Act 2024: Listed ETFs get 12-month (365d) threshold; unlisted FoFs get 24-month (730d)
                long threshold = isListed ? rules.equityLtcgThresholdDays() : rules.goldInternationalThresholdDays();
                if (holdingDays >= threshold) {
                    yield TaxTerm.LONG_TERM;
                } else {
                    yield TaxTerm.SHORT_TERM;
                }
            }
            case SGB -> {
                if (holdingDays >= rules.goldInternationalThresholdDays()) {
                    yield TaxTerm.LONG_TERM;
                } else {
                    yield TaxTerm.SHORT_TERM;
                }
            }
        };
    }
}
