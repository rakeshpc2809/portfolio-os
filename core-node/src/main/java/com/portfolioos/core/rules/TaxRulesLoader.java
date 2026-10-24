package com.portfolioos.core.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaxRulesLoader {

    private static final java.util.concurrent.ConcurrentHashMap<String, TaxRulesConfig> CACHED_CONFIGS = new java.util.concurrent.ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static TaxRulesConfig loadRules(String fiscalYear) {
        if (fiscalYear == null || fiscalYear.isBlank()) {
            fiscalYear = "2026-27";
        }

        TaxRulesConfig existing = CACHED_CONFIGS.get(fiscalYear);
        if (existing != null) {
            return existing;
        }

        String rulesDirEnv = System.getenv("RULES_DIR");
        List<File> fileLocations = new ArrayList<>();
        
        if (rulesDirEnv != null && !rulesDirEnv.isBlank()) {
            fileLocations.add(new File(rulesDirEnv, "FY" + fiscalYear + ".yaml"));
        }
        
        // Exact fiscal year rule search locations
        fileLocations.add(new File("rules/FY" + fiscalYear + ".yaml"));
        fileLocations.add(new File("../rules/FY" + fiscalYear + ".yaml"));
        fileLocations.add(new File("../../rules/FY" + fiscalYear + ".yaml"));
        fileLocations.add(new File("/app/rules/FY" + fiscalYear + ".yaml"));

        File ruleFile = null;
        for (File file : fileLocations) {
            if (file.exists()) {
                ruleFile = file;
                break;
            }
        }

        if (ruleFile == null) {
            System.err.println("Tax rules YAML missing for FY " + fiscalYear + ", using default Finance Act 2024 rules.");
            TaxRulesConfig fallback = new TaxRulesConfig(
                fiscalYear, 365L, new BigDecimal("0.125"), new BigDecimal("0.20"),
                new BigDecimal("125000"), 730L, new BigDecimal("0.125"), true
            );
            CACHED_CONFIGS.put(fiscalYear, fallback);
            return fallback;
        }

        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> data = mapper.readValue(ruleFile, Map.class);
            if (data == null) {
                throw new IllegalStateException("Empty or invalid YAML file at " + ruleFile.getAbsolutePath());
            }

            Map<String, Object> rulesMap = (Map<String, Object>) data.get("rules");
            if (rulesMap == null) {
                throw new IllegalStateException("Missing 'rules' root object in " + ruleFile.getAbsolutePath());
            }

            Map<String, Object> equityMap = (Map<String, Object>) rulesMap.get("equity_listed");
            if (equityMap == null) {
                throw new IllegalStateException("Missing 'equity_listed' section in " + ruleFile.getAbsolutePath());
            }

            Map<String, Object> goldMap = (Map<String, Object>) rulesMap.get("gold_silver_international");
            if (goldMap == null) {
                throw new IllegalStateException("Missing 'gold_silver_international' section in " + ruleFile.getAbsolutePath());
            }

            Map<String, Object> debtMap = (Map<String, Object>) rulesMap.get("specified_debt_fund");

            long eqMonths = ((Number) equityMap.getOrDefault("ltcg_threshold_months", 12)).longValue();
            BigDecimal eqExemption = new BigDecimal(equityMap.getOrDefault("annual_exemption", 125000).toString());
            BigDecimal eqLtcgRate = new BigDecimal(equityMap.getOrDefault("ltcg_rate", 0.125).toString());
            BigDecimal eqStcgRate = new BigDecimal(equityMap.getOrDefault("stcg_rate", 0.20).toString());

            long goldMonths = ((Number) goldMap.getOrDefault("ltcg_threshold_months", 24)).longValue();
            BigDecimal goldLtcgRate = new BigDecimal(goldMap.getOrDefault("ltcg_rate", 0.125).toString());

            boolean debtShortTerm = true;
            if (debtMap != null) {
                debtShortTerm = (Boolean) debtMap.getOrDefault("always_short_term", true);
            }

            TaxRulesConfig config = new TaxRulesConfig(
                fiscalYear,
                eqMonths * 365L / 12L,
                eqLtcgRate,
                eqStcgRate,
                eqExemption,
                goldMonths * 365L / 12L,
                goldLtcgRate,
                debtShortTerm
            );

            CACHED_CONFIGS.put(fiscalYear, config);
            return config;
        } catch (Exception e) {
            String errorMsg = "CRITICAL TAX CALCULATION ERROR: Failed to parse tax rules YAML from " + ruleFile.getAbsolutePath() + ": " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();
            throw new IllegalStateException(errorMsg, e);
        }
    }

    public static String detectFiscalYear(java.time.LocalDate date) {
        if (date == null) date = java.time.LocalDate.now();
        int year = date.getYear();
        int month = date.getMonthValue();
        if (month >= 4) {
            int nextYearShort = (year + 1) % 100;
            return String.format("%d-%02d", year, nextYearShort);
        } else {
            int currYearShort = year % 100;
            return String.format("%d-%02d", year - 1, currYearShort);
        }
    }

    public static com.portfolioos.core.util.Pair<java.time.LocalDate, java.time.LocalDate> getFiscalYearBounds(String fiscalYear) {
        if (fiscalYear == null || fiscalYear.isBlank()) {
            fiscalYear = detectFiscalYear(java.time.LocalDate.now());
        }
        String[] parts = fiscalYear.split("-");
        java.time.LocalDate now = java.time.LocalDate.now();
        int defaultStartYear = now.getMonthValue() >= 4 ? now.getYear() : now.getYear() - 1;
        int startYear = defaultStartYear;
        try {
            startYear = Integer.parseInt(parts[0].trim());
        } catch (Exception e) {
            // ignore
        }
        int endYear = startYear + 1;
        if (parts.length > 1 && parts[1].trim().length() == 2) {
            try {
                endYear = (startYear / 100) * 100 + Integer.parseInt(parts[1].trim());
            } catch (Exception e) {
                // ignore
            }
        }
        return new com.portfolioos.core.util.Pair<>(java.time.LocalDate.of(startYear, 4, 1), java.time.LocalDate.of(endYear, 3, 31));
    }
}
