package com.portfolioos.core.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaxRulesLoader {

    private static TaxRulesConfig cachedConfig = null;

    @SuppressWarnings("unchecked")
    public static synchronized TaxRulesConfig loadRules(String fiscalYear) {
        if (fiscalYear == null || fiscalYear.isBlank()) {
            fiscalYear = "2026-27";
        }

        if (cachedConfig != null && fiscalYear.equals(cachedConfig.fiscalYear())) {
            return cachedConfig;
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
            String msg = "CRITICAL TAX COMPLIANCE ERROR: Could not locate required tax rules YAML file for FY " + fiscalYear;
            System.err.println(msg);
            throw new IllegalArgumentException(msg);
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
                eqMonths * 30L,
                eqLtcgRate,
                eqStcgRate,
                eqExemption,
                goldMonths * 30L,
                goldLtcgRate,
                debtShortTerm
            );

            cachedConfig = config;
            return config;
        } catch (Exception e) {
            String errorMsg = "CRITICAL TAX CALCULATION ERROR: Failed to parse tax rules YAML from " + ruleFile.getAbsolutePath() + ": " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();
            throw new IllegalStateException(errorMsg, e);
        }
    }
}
