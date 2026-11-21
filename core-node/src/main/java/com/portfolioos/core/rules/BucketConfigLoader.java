package com.portfolioos.core.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.portfolioos.core.valuation.BucketEngine;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

public class BucketConfigLoader {

    public record PreferredFundConfig(
        String fundId,
        String fundName,
        double allocationWeight
    ) {}

    public record BucketTargetConfig(
        String bucket,
        double targetPct,
        double bandPct,
        double triggerDriftPct,
        String strategy,
        List<PreferredFundConfig> preferredFunds
    ) {
        public BucketTargetConfig(String bucket, double targetPct, double bandPct, List<PreferredFundConfig> preferredFunds) {
            this(bucket, targetPct, bandPct, bandPct, "", preferredFunds);
        }

        public double driftTriggerPct() {
            return triggerDriftPct;
        }
    }

    public static String mapAssetToBucket(String assetId, String assetName) {
        String pref = getPreferredBucketForAsset(assetId, assetName);
        if (pref != null) return pref;
        return com.portfolioos.core.valuation.BucketEngine.classifyAssetToBucket(assetId, assetName).name();
    }

    public static String getPreferredBucketForAsset(String assetId, String assetName) {
        if (assetId == null && assetName == null) return null;

        BucketTargetVersion version = getActiveVersion(LocalDate.now());
        if (version != null && version.targets() != null) {
            for (BucketTargetConfig target : version.targets()) {
                if (target.preferredFunds() != null) {
                    for (PreferredFundConfig fund : target.preferredFunds()) {
                        if (assetId != null && fund.fundId() != null && assetId.equalsIgnoreCase(fund.fundId())) {
                            return target.bucket();
                        }
                        if (assetName != null && fund.fundName() != null &&
                            assetName.toUpperCase().contains(fund.fundName().toUpperCase())) {
                            return target.bucket();
                        }
                    }
                }
            }
        }

        if (assetId != null) {
            String idUpper = assetId.toUpperCase();
            if (idUpper.startsWith("NIFTY_LARGEMIDCAP") || idUpper.contains("LARGEMIDCAP")) {
                return "EQUITY_CORE";
            }
        }

        if (assetName != null) {
            String nameUpper = assetName.toUpperCase();
            if (nameUpper.contains("LARGE AND MIDCAP") || nameUpper.contains("LARGEMIDCAP")) {
                return "EQUITY_CORE";
            }
        }
        return null;
    }

    public static boolean isPreferredFund(String assetId) {
        if (assetId == null) return false;
        if (assetId.startsWith("NIFTY_LARGEMIDCAP") || assetId.startsWith("PPFAS") || assetId.startsWith("VALUE_30") || assetId.startsWith("MOMENTUM") || assetId.startsWith("SMALL_CAP") || assetId.startsWith("GOLD") || assetId.startsWith("ARBITRAGE")) {
            return true;
        }
        BucketTargetVersion version = getActiveVersion(LocalDate.now());
        if (version != null && version.targets() != null) {
            for (BucketTargetConfig target : version.targets()) {
                if (target.preferredFunds() != null) {
                    for (PreferredFundConfig fund : target.preferredFunds()) {
                        if (assetId.equalsIgnoreCase(fund.fundId()) ||
                            (fund.fundName() != null && assetId.equalsIgnoreCase(fund.fundName()))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static Double getPreferredFundWeight(String assetId) {
        if (assetId == null) return null;
        BucketTargetVersion version = getActiveVersion(LocalDate.now());
        if (version != null && version.targets() != null) {
            for (BucketTargetConfig target : version.targets()) {
                if (target.preferredFunds() != null) {
                    for (PreferredFundConfig fund : target.preferredFunds()) {
                        if (assetId.equalsIgnoreCase(fund.fundId()) ||
                            (fund.fundName() != null && assetId.equalsIgnoreCase(fund.fundName()))) {
                            return fund.allocationWeight();
                        }
                    }
                }
            }
        }
        return null;
    }

    public record LegacyLiquidationCandidate(
        String isin,
        String fundName,
        String reason
    ) {}

    public record BucketTargetVersion(
        String versionId,
        String effectiveFrom, // YYYY-MM-DD
        List<BucketTargetConfig> targets
    ) {}

    public record BucketRulesConfig(
        String configSource,
        String configFilePath,
        List<BucketTargetVersion> versions,
        List<LegacyLiquidationCandidate> legacyCandidates
    ) {
        public BucketRulesConfig(List<BucketTargetVersion> versions) {
            this("YAML_FILE", "rules/bucket_targets.yaml", versions, List.of());
        }

        public BucketRulesConfig(String configSource, String configFilePath, List<BucketTargetVersion> versions) {
            this(configSource, configFilePath, versions, List.of());
        }
    }

    private static BucketRulesConfig cachedRules = null;
    private static com.portfolioos.core.config.BucketTargetsConfig cachedV24Config = null;

    public static synchronized void resetCache() {
        cachedRules = null;
        cachedV24Config = null;
    }

    public static synchronized com.portfolioos.core.config.BucketTargetsConfig loadV24Config() {
        if (cachedV24Config != null) {
            return cachedV24Config;
        }

        File ruleFile = findConfigFile();
        if (ruleFile == null || !ruleFile.exists()) {
            throw new IllegalStateException("CRITICAL CONFIG ERROR: File rules/bucket_targets.yaml not found at any search location!");
        }

        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> data = mapper.readValue(ruleFile, Map.class);
            if (data == null) {
                throw new IllegalStateException("CRITICAL CONFIG ERROR: YAML file " + ruleFile.getAbsolutePath() + " is empty!");
            }

            cachedV24Config = loadV24ConfigFromMap(data);
            return cachedV24Config;
        } catch (Exception e) {
            if (e instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new IllegalStateException("CRITICAL CONFIG ERROR: Failed to load v2.4 bucket_targets.yaml: " + e.getMessage(), e);
        }
    }

    public static com.portfolioos.core.config.BucketTargetsConfig loadV24ConfigFromMap(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalStateException("CRITICAL CONFIG ERROR: Configuration map cannot be null!");
        }

        String version = String.valueOf(data.getOrDefault("version", "3.0.0"));

        // Global Invariants
        Map<String, Object> giMap = (Map<String, Object>) data.get("global_invariants");
        if (giMap == null) {
            throw new IllegalStateException("Missing 'global_invariants' section in bucket_targets.yaml");
        }
        int cooldownDays = ((Number) giMap.getOrDefault("rebalance_cooldown_days", 30)).intValue();
        BigDecimal minTradeAmount = new BigDecimal(String.valueOf(giMap.getOrDefault("min_trade_amount_inr", "1000.00")));
        BigDecimal liquidHardCap = new BigDecimal(String.valueOf(giMap.getOrDefault("liquid_hard_cap_pct", "15.00")));
        String ddAnchorMode = String.valueOf(giMap.getOrDefault("drawdown_anchor_mode", "ATH_WATERMARK"));
        BigDecimal ddHoldMin = new BigDecimal(String.valueOf(giMap.getOrDefault("drawdown_hold_zone_min_pct", "5.00")));
        BigDecimal ddHoldMax = new BigDecimal(String.valueOf(giMap.getOrDefault("drawdown_hold_zone_max_pct", "10.00")));
        BigDecimal urgentDdThresh = new BigDecimal(String.valueOf(giMap.getOrDefault("urgent_drawdown_threshold_pct", "30.00")));
        List<?> rawSchedMonths = (List<?>) giMap.getOrDefault("scheduled_months", List.of(3));
        List<Integer> schedMonths = rawSchedMonths.stream().map(m -> ((Number) m).intValue()).toList();

        if (!giMap.containsKey("taxable_trim_fraction") || giMap.get("taxable_trim_fraction") == null) {
            throw new IllegalStateException("CRITICAL CONFIG ERROR: Missing required 'taxable_trim_fraction' in global_invariants of bucket_targets.yaml");
        }
        BigDecimal taxableTrimFraction = new BigDecimal(String.valueOf(giMap.get("taxable_trim_fraction")));

        var globalInvariants = new com.portfolioos.core.config.BucketTargetsConfig.GlobalInvariants(
            cooldownDays, minTradeAmount, liquidHardCap, ddAnchorMode, ddHoldMin, ddHoldMax, urgentDdThresh, schedMonths, taxableTrimFraction
        );

        // Tax Parameters
        Map<String, Object> tpMap = (Map<String, Object>) data.get("tax_parameters");
        if (tpMap == null) {
            throw new IllegalStateException("Missing 'tax_parameters' section in bucket_targets.yaml");
        }
        BigDecimal ltcgExempt = new BigDecimal(String.valueOf(tpMap.getOrDefault("ltcg_exemption_limit_inr", "125000.00")));
        BigDecimal eqLtcg = new BigDecimal(String.valueOf(tpMap.getOrDefault("equity_ltcg_base_rate", "0.125")));
        BigDecimal eqStcg = new BigDecimal(String.valueOf(tpMap.getOrDefault("equity_stcg_base_rate", "0.200")));
        BigDecimal fofLtcg = new BigDecimal(String.valueOf(tpMap.getOrDefault("fof_ltcg_base_rate", "0.125")));
        BigDecimal debt50aa = new BigDecimal(String.valueOf(tpMap.getOrDefault("debt_50aa_slab_base_rate", "0.300")));
        BigDecimal cess = new BigDecimal(String.valueOf(tpMap.getOrDefault("health_education_cess_rate", "0.04")));

        var taxParameters = new com.portfolioos.core.config.BucketTargetsConfig.TaxParameters(
            ltcgExempt, eqLtcg, eqStcg, fofLtcg, debt50aa, cess
        );

        // Drawdown Rungs
        List<Map<String, Object>> rungsRaw = (List<Map<String, Object>>) data.getOrDefault("drawdown_rungs", List.of());
        List<com.portfolioos.core.config.BucketTargetsConfig.DrawdownRung> drawdownRungs = new ArrayList<>();
        for (Map<String, Object> r : rungsRaw) {
            drawdownRungs.add(new com.portfolioos.core.config.BucketTargetsConfig.DrawdownRung(
                String.valueOf(r.get("tier")),
                new BigDecimal(String.valueOf(r.get("drawdown_pct"))),
                new BigDecimal(String.valueOf(r.get("liquid_target_pct"))),
                new BigDecimal(String.valueOf(r.get("core_target_pct"))),
                new BigDecimal(String.valueOf(r.get("liquid_deploy_fraction")))
            ));
        }

        // Buckets
        Map<String, Object> bMap = (Map<String, Object>) data.get("buckets");
        if (bMap == null) {
            throw new IllegalStateException("Missing 'buckets' section in bucket_targets.yaml");
        }
        Map<com.portfolioos.core.config.BucketTargetsConfig.BucketType, com.portfolioos.core.config.BucketTargetsConfig.BucketSpec> buckets = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : bMap.entrySet()) {
            com.portfolioos.core.config.BucketTargetsConfig.BucketType bType =
                com.portfolioos.core.config.BucketTargetsConfig.BucketType.valueOf(entry.getKey().toUpperCase());
            Map<String, Object> bsData = (Map<String, Object>) entry.getValue();

            if (bsData == null || bsData.get("target_pct") == null || bsData.get("band_lower_pct") == null || bsData.get("band_upper_pct") == null) {
                throw new IllegalStateException("CRITICAL CONFIG ERROR: Missing required target/band specs for bucket " + entry.getKey());
            }

            BigDecimal targetPct = new BigDecimal(String.valueOf(bsData.get("target_pct")));
            BigDecimal bandLower = new BigDecimal(String.valueOf(bsData.get("band_lower_pct")));
            BigDecimal bandUpper = new BigDecimal(String.valueOf(bsData.get("band_upper_pct")));

            // Intra Bucket Routing
            com.portfolioos.core.config.BucketTargetsConfig.IntraBucketRoutingSpec intraSpec = null;
            if (bsData.containsKey("intra_bucket_routing")) {
                Map<String, Object> ibrData = (Map<String, Object>) bsData.get("intra_bucket_routing");
                boolean remediatePassive = Boolean.parseBoolean(String.valueOf(ibrData.getOrDefault("remediate_on_passive_flow_only", "true")));
                String primaryIsin = String.valueOf(ibrData.get("primary_isin"));
                BigDecimal primaryRatio = new BigDecimal(String.valueOf(ibrData.getOrDefault("primary_target_ratio", "0.60")));
                BigDecimal ratioBand = new BigDecimal(String.valueOf(ibrData.getOrDefault("ratio_band", "0.06")));
                intraSpec = new com.portfolioos.core.config.BucketTargetsConfig.IntraBucketRoutingSpec(remediatePassive, primaryIsin, primaryRatio, ratioBand);
            }

            // Schemes
            List<Map<String, Object>> schList = (List<Map<String, Object>>) bsData.getOrDefault("schemes", List.of());
            List<com.portfolioos.core.config.BucketTargetsConfig.SchemeSpec> schemes = new ArrayList<>();
            for (Map<String, Object> s : schList) {
                schemes.add(new com.portfolioos.core.config.BucketTargetsConfig.SchemeSpec(
                    String.valueOf(s.get("isin")),
                    String.valueOf(s.get("name")),
                    String.valueOf(s.get("amc_code")),
                    new BigDecimal(String.valueOf(s.get("weight_within_bucket"))),
                    ((Number) s.getOrDefault("exit_load_days", 0)).intValue(),
                    ((Number) s.getOrDefault("ltcg_holding_days", 365)).intValue(),
                    ((Number) s.getOrDefault("settlement_days", 1)).intValue()
                ));
            }

            buckets.put(bType, new com.portfolioos.core.config.BucketTargetsConfig.BucketSpec(
                targetPct, bandLower, bandUpper, intraSpec, schemes
            ));
        }

        for (var requiredType : List.of(
            com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_CORE,
            com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_SATELLITE,
            com.portfolioos.core.config.BucketTargetsConfig.BucketType.GOLD_SILVER,
            com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER
        )) {
            if (!buckets.containsKey(requiredType)) {
                throw new IllegalStateException("CRITICAL CONFIG ERROR: Missing required bucket configuration for " + requiredType);
            }
        }

        // Legacy blacklist
        List<?> blRaw = (List<?>) data.getOrDefault("legacy_isin_blacklist", List.of());
        List<String> legacyBlacklist = blRaw.stream().map(Object::toString).toList();

        return new com.portfolioos.core.config.BucketTargetsConfig(
            version, globalInvariants, taxParameters, drawdownRungs, buckets, legacyBlacklist
        );
    }

    public static synchronized void setCachedConfig(BucketRulesConfig config) {
        cachedRules = config;
    }

    public static synchronized BucketRulesConfig loadConfig() {
        if (cachedRules != null) {
            return cachedRules;
        }

        File ruleFile = findConfigFile();
        if (ruleFile == null || !ruleFile.exists()) {
            throw new IllegalStateException("CRITICAL CONFIG ERROR: File rules/bucket_targets.yaml not found at any search location!");
        }

        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> data = mapper.readValue(ruleFile, Map.class);
            if (data == null) {
                throw new IllegalStateException("CRITICAL CONFIG ERROR: YAML file " + ruleFile.getAbsolutePath() + " is empty!");
            }

            System.out.println("CONFIG_SOURCE_AUDIT: [YAML_FILE] Successfully loaded active configuration from " + ruleFile.getAbsolutePath());
            List<BucketTargetVersion> parsedVersions = new ArrayList<>();

            if (data.containsKey("portfolio")) {
                Map<String, Object> portMap = (Map<String, Object>) data.get("portfolio");
                String vId = "v" + portMap.getOrDefault("version", "2.3");
                String effFrom = "2026-08-26";
                Map<String, Object> bucketsMap = (Map<String, Object>) portMap.get("buckets");
                List<BucketTargetConfig> targetConfigs = new ArrayList<>();

                if (bucketsMap != null) {
                    for (Map.Entry<String, Object> entry : bucketsMap.entrySet()) {
                        String bKey = entry.getKey();
                        Map<String, Object> bData = (Map<String, Object>) entry.getValue();

                        double tPct = ((Number) bData.getOrDefault("target_weight", 0.10)).doubleValue() * 100.0;

                        Map<String, Object> bands = (Map<String, Object>) bData.get("drift_bands");
                        double minW = (bands != null && bands.containsKey("min_weight"))
                            ? ((Number) bands.get("min_weight")).doubleValue() * 100.0 : tPct - 5.0;
                        double maxW = (bands != null && bands.containsKey("max_weight"))
                            ? ((Number) bands.get("max_weight")).doubleValue() * 100.0 : tPct + 5.0;

                        double bPct = Math.max(Math.abs(tPct - minW), Math.abs(maxW - tPct));
                        double tdPct = bPct;

                        List<PreferredFundConfig> prefFunds = new ArrayList<>();

                        if (bData.containsKey("funds")) {
                            Map<String, Object> fundsMap = (Map<String, Object>) bData.get("funds");
                            for (Map.Entry<String, Object> fEntry : fundsMap.entrySet()) {
                                Map<String, Object> fData = (Map<String, Object>) fEntry.getValue();
                                String isin = (String) fData.get("isin");
                                double subW = ((Number) fData.getOrDefault("target_sub_weight", 0.5)).doubleValue();
                                String fName = fEntry.getKey().equals("largemid_250") ? "ICICI Prudential Nifty LargeMidcap 250 Index Fund" : "Parag Parikh Flexi Cap Fund";
                                prefFunds.add(new PreferredFundConfig(isin, fName, subW));
                            }
                        } else if (bData.containsKey("fund_isin")) {
                            String isin = (String) bData.get("fund_isin");
                            String fName = getFundNameByIsin(isin);
                            prefFunds.add(new PreferredFundConfig(isin, fName, 1.0));
                        } else {
                            prefFunds = getDefaultPreferredFundsForBucket(bKey);
                        }

                        targetConfigs.add(new BucketTargetConfig(bKey, tPct, bPct, tdPct, bKey, prefFunds));
                    }
                }
                parsedVersions.add(new BucketTargetVersion(vId, effFrom, targetConfigs));
            } else if (data.containsKey("versions")) {
                List<Map<String, Object>> verList = (List<Map<String, Object>>) data.get("versions");

                for (Map<String, Object> vMap : verList) {
                    String vId = (String) vMap.getOrDefault("version_id", "v1.0");
                    String effFrom = (String) vMap.getOrDefault("effective_from", "2024-01-01");
                    List<Map<String, Object>> tList = (List<Map<String, Object>>) vMap.get("targets");
                    List<BucketTargetConfig> targetConfigs = new ArrayList<>();

                    for (Map<String, Object> tMap : tList) {
                        String bName = (String) tMap.get("bucket");
                        double tPct = ((Number) tMap.get("target_pct")).doubleValue();
                        double bPct = ((Number) tMap.get("band_pct")).doubleValue();
                        
                        double tdPct = tMap.containsKey("drift_trigger_pct")
                            ? ((Number) tMap.get("drift_trigger_pct")).doubleValue()
                            : (tMap.containsKey("trigger_drift_pct")
                                ? ((Number) tMap.get("trigger_drift_pct")).doubleValue()
                                : bPct);
                        String strat = (String) tMap.getOrDefault("strategy", "");

                        List<PreferredFundConfig> prefFunds = new ArrayList<>();
                        if (tMap.containsKey("preferred_funds")) {
                            List<Map<String, Object>> pfList = (List<Map<String, Object>>) tMap.get("preferred_funds");
                            for (Map<String, Object> pfMap : pfList) {
                                prefFunds.add(new PreferredFundConfig(
                                    (String) pfMap.get("fund_id"),
                                    (String) pfMap.get("fund_name"),
                                    ((Number) pfMap.get("allocation_weight")).doubleValue()
                                ));
                            }
                        } else {
                            prefFunds = getDefaultPreferredFundsForBucket(bName);
                        }

                        targetConfigs.add(new BucketTargetConfig(bName, tPct, bPct, tdPct, strat, prefFunds));
                    }
                    parsedVersions.add(new BucketTargetVersion(vId, effFrom, targetConfigs));
                }
            } else {
                throw new IllegalStateException("CRITICAL CONFIG ERROR: YAML file at " + ruleFile.getAbsolutePath() + " does not contain 'portfolio' or 'versions' block");
            }

            List<LegacyLiquidationCandidate> legacyCandidates = new ArrayList<>();
            if (data.containsKey("legacy_liquidation_candidates")) {
                Object rawLeg = data.get("legacy_liquidation_candidates");
                if (rawLeg instanceof List<?> legList) {
                    for (Object item : legList) {
                        if (item instanceof Map<?, ?> rawMap) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> lMap = (Map<String, Object>) rawMap;
                            String isin = lMap.get("isin") != null ? lMap.get("isin").toString() : null;
                            String fName = lMap.get("fund_name") != null ? lMap.get("fund_name").toString() : "";
                            String reason = lMap.get("reason") != null ? lMap.get("reason").toString() : "";
                            legacyCandidates.add(new LegacyLiquidationCandidate(isin, fName, reason));
                        }
                    }
                }
            }

            cachedRules = new BucketRulesConfig("YAML_FILE", ruleFile.getAbsolutePath(), parsedVersions, legacyCandidates);
            return cachedRules;
        } catch (Exception e) {
            if (e instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new IllegalStateException("CRITICAL CONFIG ERROR: Failed to load rules/bucket_targets.yaml: " + e.getMessage(), e);
        }
    }

    public static List<LegacyLiquidationCandidate> getLegacyLiquidationCandidates() {
        BucketRulesConfig config = loadConfig();
        return config != null && config.legacyCandidates() != null ? config.legacyCandidates() : List.of();
    }


    public static List<BucketEngine.BucketTarget> getActiveBucketTargets(LocalDate date) {
        BucketRulesConfig config = loadConfig();
        if (config == null || config.versions() == null || config.versions().isEmpty()) {
            throw new IllegalStateException("CRITICAL CONFIG ERROR: No bucket target versions configured in rules/bucket_targets.yaml");
        }

        String targetDateStr = (date != null ? date : LocalDate.now()).toString();
        
        BucketTargetVersion activeVer = config.versions().stream()
            .filter(v -> v.effectiveFrom().compareTo(targetDateStr) <= 0)
            .max(Comparator.comparing(BucketTargetVersion::effectiveFrom))
            .orElse(config.versions().get(0));

        Map<BucketEngine.Bucket, BigDecimal> targetMap = new LinkedHashMap<>();
        Map<BucketEngine.Bucket, BigDecimal> bandMap = new LinkedHashMap<>();

        for (BucketTargetConfig tc : activeVer.targets()) {
            BucketEngine.Bucket b = null;
            try {
                b = BucketEngine.Bucket.valueOf(tc.bucket().toUpperCase());
            } catch (Exception e) {
                // fall through
            }
            if (b == null) {
                switch (tc.bucket().toLowerCase()) {
                    case "core", "equity_core" -> b = BucketEngine.Bucket.EQUITY_CORE;
                    case "satellite_value", "satellite_momentum", "satellite_smallcap", "equity_satellite", "satellite" -> b = BucketEngine.Bucket.EQUITY_SATELLITE;
                    case "hedge_commodity", "gold_silver", "gold" -> b = BucketEngine.Bucket.GOLD_SILVER;
                    case "liquidity_arbitrage", "liquid_buffer", "arbitrage" -> b = BucketEngine.Bucket.LIQUID_BUFFER;
                }
            } else if (b == BucketEngine.Bucket.SATELLITE_VALUE || b == BucketEngine.Bucket.SATELLITE_MOMENTUM || b == BucketEngine.Bucket.SATELLITE_SMALLCAP) {
                b = BucketEngine.Bucket.EQUITY_SATELLITE;
            } else if (b == BucketEngine.Bucket.HEDGE_COMMODITY) {
                b = BucketEngine.Bucket.GOLD_SILVER;
            } else if (b == BucketEngine.Bucket.LIQUIDITY_ARBITRAGE) {
                b = BucketEngine.Bucket.LIQUID_BUFFER;
            }

            if (b != null) {
                BigDecimal tVal = BigDecimal.valueOf(tc.targetPct()).setScale(2, RoundingMode.HALF_UP);
                targetMap.merge(b, tVal, BigDecimal::add);
                // Use triggerDriftPct (e.g. 12.0% for GOLD_SILVER accumulator) when configured, else bandPct
                double effectiveBand = tc.triggerDriftPct() > 0 ? tc.triggerDriftPct() : tc.bandPct();
                BigDecimal bVal = (effectiveBand > 0 && b != BucketEngine.Bucket.EQUITY_SATELLITE)
                    ? BigDecimal.valueOf(effectiveBand).setScale(2, RoundingMode.HALF_UP)
                    : new BigDecimal("5.00");
                bandMap.put(b, bVal);
            }
        }

        List<BucketEngine.BucketTarget> result = new ArrayList<>();
        com.portfolioos.core.config.BucketTargetsConfig v24Config = null;
        try {
            v24Config = loadV24Config();
        } catch (Exception ignored) {}

        for (Map.Entry<BucketEngine.Bucket, BigDecimal> entry : targetMap.entrySet()) {
            BucketEngine.Bucket b = entry.getKey();
            com.portfolioos.core.config.BucketTargetsConfig.BucketType bType = switch (b) {
                case EQUITY_CORE -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_CORE;
                case EQUITY_SATELLITE, SATELLITE_VALUE, SATELLITE_MOMENTUM, SATELLITE_SMALLCAP -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.EQUITY_SATELLITE;
                case GOLD_SILVER, HEDGE_COMMODITY -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.GOLD_SILVER;
                case LIQUID_BUFFER, LIQUIDITY_ARBITRAGE -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.LIQUID_BUFFER;
                case LEGACY_HOLDINGS -> com.portfolioos.core.config.BucketTargetsConfig.BucketType.LEGACY_HOLDINGS;
            };
            var spec = (v24Config != null && v24Config.buckets() != null) ? v24Config.buckets().get(bType) : null;
            BigDecimal bandLower = spec != null ? spec.bandLowerPct() : bandMap.getOrDefault(b, new BigDecimal("5.00"));
            BigDecimal bandUpper = spec != null ? spec.bandUpperPct() : bandMap.getOrDefault(b, new BigDecimal("5.00"));

            result.add(new BucketEngine.BucketTarget(
                b,
                entry.getValue(),
                bandMap.getOrDefault(b, new BigDecimal("5.00")),
                bandLower,
                bandUpper
            ));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("CRITICAL CONFIG ERROR: No valid bucket targets parsed from active version in rules/bucket_targets.yaml");
        }
        return result;
    }

    public static Map<String, Map<String, Double>> getSipAllocations() {
        return getSipAllocations(LocalDate.now());
    }

    public static Map<String, Map<String, Double>> getSipAllocations(LocalDate date) {
        BucketTargetVersion version = getActiveVersion(date);
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();

        if (version != null && version.targets() != null) {
            for (BucketTargetConfig target : version.targets()) {
                double bucketTargetFrac = target.targetPct() / 100.0;
                Map<String, Double> fundSipWeights = new LinkedHashMap<>();

                if (target.preferredFunds() != null) {
                    for (PreferredFundConfig fund : target.preferredFunds()) {
                        double overallSipWeight = bucketTargetFrac * fund.allocationWeight();
                        fundSipWeights.put(fund.fundId(), overallSipWeight);
                    }
                }
                result.put(target.bucket(), fundSipWeights);
            }
        }
        return result;
    }

    public static Map<String, Double> getRenormalizedSipAllocations(LocalDate date) {
        Map<String, Map<String, Double>> fullAlloc = getSipAllocations(date);
        Map<String, Double> nonGoldAlloc = new LinkedHashMap<>();
        double totalWeight = 0.0;

        for (Map.Entry<String, Map<String, Double>> bucketEntry : fullAlloc.entrySet()) {
            if ("GOLD_SILVER".equalsIgnoreCase(bucketEntry.getKey())) {
                continue; // Gold is dampener-driven, excluded from flat monthly SIP
            }
            for (Map.Entry<String, Double> fundEntry : bucketEntry.getValue().entrySet()) {
                nonGoldAlloc.put(fundEntry.getKey(), fundEntry.getValue());
                totalWeight += fundEntry.getValue();
            }
        }

        Map<String, Double> renormalized = new LinkedHashMap<>();
        if (totalWeight > 0.0) {
            for (Map.Entry<String, Double> entry : nonGoldAlloc.entrySet()) {
                renormalized.put(entry.getKey(), entry.getValue() / totalWeight);
            }
        }
        return renormalized;
    }

    public static BucketTargetVersion getActiveVersion(LocalDate date) {
        BucketRulesConfig config = loadConfig();
        String targetDateStr = (date != null ? date : LocalDate.now()).toString();
        return config.versions().stream()
            .filter(v -> v.effectiveFrom().compareTo(targetDateStr) <= 0)
            .max(Comparator.comparing(BucketTargetVersion::effectiveFrom))
            .orElse(config.versions().get(0));
    }

    private static String getFundNameByIsin(String isin) {
        if (isin == null) return "Unknown Fund";
        return switch (isin.toUpperCase()) {
            case "INF109KC12U0" -> "ICICI Prudential Nifty LargeMidcap 250 Index Fund";
            case "INF879O01027" -> "Parag Parikh Flexi Cap Fund";
            case "INF109KC13X2" -> "ICICI Prudential Nifty200 Value 30 Index Fund";
            case "INF754K01TN5" -> "Edelweiss Nifty500 Multicap Momentum Quality 50 Index Fund";
            case "INF204K01K15" -> "Nippon India Small Cap Fund";
            case "INF247L01BM8" -> "Motilal Oswal Gold and Silver Passive Fund of Funds";
            case "INF205K01KR8" -> "Invesco India Arbitrage Fund";
            default -> "Mutual Fund (" + isin + ")";
        };
    }

    public static List<PreferredFundConfig> getDefaultPreferredFundsForBucket(String bucketName) {
        if (bucketName == null) return List.of();
        switch (bucketName.toUpperCase()) {
            case "EQUITY_CORE", "CORE" -> {
                return List.of(
                    new PreferredFundConfig("INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund", 0.60),
                    new PreferredFundConfig("INF879O01027", "Parag Parikh Flexi Cap Fund", 0.40)
                );
            }
            case "EQUITY_SATELLITE" -> {
                return List.of(
                    new PreferredFundConfig("INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund", 0.33),
                    new PreferredFundConfig("INF754K01TN5", "Edelweiss Nifty500 Multicap Momentum Quality 50 Index Fund", 0.33),
                    new PreferredFundConfig("INF204K01K15", "Nippon India Small Cap Fund", 0.34)
                );
            }
            case "SATELLITE_VALUE" -> {
                return List.of(
                    new PreferredFundConfig("INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund", 1.00)
                );
            }
            case "SATELLITE_MOMENTUM" -> {
                return List.of(
                    new PreferredFundConfig("INF754K01TN5", "Edelweiss Nifty500 Multicap Momentum Quality 50 Index Fund", 1.00)
                );
            }
            case "SATELLITE_SMALLCAP" -> {
                return List.of(
                    new PreferredFundConfig("INF204K01K15", "Nippon India Small Cap Fund", 1.00)
                );
            }
            case "GOLD_SILVER", "HEDGE_COMMODITY" -> {
                return List.of(
                    new PreferredFundConfig("INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", 1.00)
                );
            }
            case "LIQUID_BUFFER", "LIQUIDITY_ARBITRAGE" -> {
                return List.of(
                    new PreferredFundConfig("INF205K01KR8", "Invesco India Arbitrage Fund", 1.00)
                );
            }
            default -> {
                return List.of();
            }
        }
    }

    private static File findConfigFile() {
        String rulesDirEnv = System.getenv("RULES_DIR");
        List<File> locations = new ArrayList<>();
        if (rulesDirEnv != null && !rulesDirEnv.isBlank()) {
            locations.add(new File(rulesDirEnv, "bucket_targets.yaml"));
        }
        locations.add(new File("rules/bucket_targets.yaml"));
        locations.add(new File("../rules/bucket_targets.yaml"));
        locations.add(new File("../../rules/bucket_targets.yaml"));
        locations.add(new File("/app/rules/bucket_targets.yaml"));

        for (File f : locations) {
            if (f.exists()) {
                System.out.println("BucketConfigLoader: Loaded config from " + f.getAbsolutePath());
                return f;
            }
        }
        return locations.get(0);
    }
}
