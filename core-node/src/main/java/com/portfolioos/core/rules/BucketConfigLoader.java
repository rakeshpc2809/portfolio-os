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

    public record BucketTargetVersion(
        String versionId,
        String effectiveFrom, // YYYY-MM-DD
        List<BucketTargetConfig> targets
    ) {}

    public record BucketRulesConfig(
        String configSource,
        String configFilePath,
        List<BucketTargetVersion> versions
    ) {
        public BucketRulesConfig(List<BucketTargetVersion> versions) {
            this("YAML_FILE", "rules/bucket_targets.yaml", versions);
        }
    }

    private static BucketRulesConfig cachedRules = null;

    public static synchronized void resetCache() {
        cachedRules = null;
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
                        
                        double tdPct = tMap.containsKey("trigger_drift_pct") 
                            ? ((Number) tMap.get("trigger_drift_pct")).doubleValue() 
                            : bPct;
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

            cachedRules = new BucketRulesConfig("YAML_FILE", ruleFile.getAbsolutePath(), parsedVersions);
            return cachedRules;
        } catch (Exception e) {
            if (e instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new IllegalStateException("CRITICAL CONFIG ERROR: Failed to load rules/bucket_targets.yaml: " + e.getMessage(), e);
        }
    }

    public static List<BucketEngine.BucketTarget> getActiveBucketTargets(LocalDate date) {
        BucketRulesConfig config = loadConfig();
        if (config == null || config.versions().isEmpty()) {
            return BucketEngine.DEFAULT_TARGETS;
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
                // Canonical aggregate drift tolerance is 5.00% across all top-level evaluation buckets
                BigDecimal bVal = (tc.bandPct() > 0 && b != BucketEngine.Bucket.EQUITY_SATELLITE)
                    ? BigDecimal.valueOf(tc.bandPct()).setScale(2, RoundingMode.HALF_UP)
                    : new BigDecimal("5.00");
                bandMap.put(b, bVal);
            }
        }

        List<BucketEngine.BucketTarget> result = new ArrayList<>();
        for (Map.Entry<BucketEngine.Bucket, BigDecimal> entry : targetMap.entrySet()) {
            result.add(new BucketEngine.BucketTarget(
                entry.getKey(),
                entry.getValue(),
                bandMap.getOrDefault(entry.getKey(), new BigDecimal("5.00"))
            ));
        }
        return result.isEmpty() ? BucketEngine.DEFAULT_TARGETS : result;
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

    public static synchronized void updateBucketTargets(List<BucketTargetConfig> newTargets, String effectiveFrom) {
        validateNewTargets(newTargets);

        String effDate = (effectiveFrom != null && !effectiveFrom.isBlank()) ? effectiveFrom : LocalDate.now().toString();
        BucketRulesConfig currentConfig = loadConfig();
        List<BucketTargetVersion> versions = new ArrayList<>(currentConfig.versions());

        String newVersionId = "v" + (versions.size() + 1) + ".0";
        versions.add(new BucketTargetVersion(newVersionId, effDate, newTargets));

        BucketRulesConfig updatedConfig = new BucketRulesConfig(versions);
        cachedRules = updatedConfig;
        saveConfigToDisk(updatedConfig);
    }

    public static void validateNewTargets(List<BucketTargetConfig> newTargets) {
        if (newTargets == null || newTargets.isEmpty()) {
            throw new IllegalArgumentException("Bucket targets list cannot be empty");
        }

        Set<String> requiredBuckets = Set.of("EQUITY_CORE", "EQUITY_SATELLITE", "GOLD_SILVER", "LIQUID_BUFFER");
        Set<String> providedBuckets = new HashSet<>();

        double sumPct = 0.0;
        for (BucketTargetConfig tc : newTargets) {
            if (tc.bucket() == null || !requiredBuckets.contains(tc.bucket())) {
                throw new IllegalArgumentException("Invalid bucket name: " + tc.bucket() + ". Allowed: " + requiredBuckets);
            }
            providedBuckets.add(tc.bucket());

            if (tc.targetPct() < 0.0 || tc.targetPct() > 100.0) {
                throw new IllegalArgumentException("Target percentage for " + tc.bucket() + " must be between 0.0% and 100.0%");
            }
            if (tc.bandPct() < 1.0 || tc.bandPct() > 20.0) {
                throw new IllegalArgumentException("Band tolerance for " + tc.bucket() + " must be between 1.0% and 20.0%");
            }
            if (tc.triggerDriftPct() < 0.0 || tc.triggerDriftPct() > 100.0) {
                throw new IllegalArgumentException("Trigger drift percentage for " + tc.bucket() + " must be between 0.0% and 100.0%");
            }
            sumPct += tc.targetPct();
        }

        if (!providedBuckets.containsAll(requiredBuckets)) {
            throw new IllegalArgumentException("All 4 buckets must be defined: " + requiredBuckets);
        }

        if (Math.abs(sumPct - 100.0) > 0.05) {
            throw new IllegalArgumentException(String.format("Bucket target percentages must sum to 100.0%% (provided sum: %.2f%%)", sumPct));
        }
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


    private static void saveConfigToDisk(BucketRulesConfig config) {
        try {
            File targetFile = findConfigFile();
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> root = new LinkedHashMap<>();
            List<Map<String, Object>> verList = new ArrayList<>();

            for (BucketTargetVersion v : config.versions()) {
                Map<String, Object> vMap = new LinkedHashMap<>();
                vMap.put("version_id", v.versionId());
                vMap.put("effective_from", v.effectiveFrom());

                List<Map<String, Object>> tList = new ArrayList<>();
                for (BucketTargetConfig tc : v.targets()) {
                    Map<String, Object> tMap = new LinkedHashMap<>();
                    tMap.put("bucket", tc.bucket());
                    tMap.put("target_pct", tc.targetPct());
                    tMap.put("band_pct", tc.bandPct());
                    tMap.put("trigger_drift_pct", tc.triggerDriftPct());
                    tMap.put("strategy", tc.strategy());

                    if (tc.preferredFunds() != null && !tc.preferredFunds().isEmpty()) {
                        List<Map<String, Object>> pfList = new ArrayList<>();
                        for (PreferredFundConfig pf : tc.preferredFunds()) {
                            Map<String, Object> pfMap = new LinkedHashMap<>();
                            pfMap.put("fund_id", pf.fundId());
                            pfMap.put("fund_name", pf.fundName());
                            pfMap.put("allocation_weight", pf.allocationWeight());
                            pfList.add(pfMap);
                        }
                        tMap.put("preferred_funds", pfList);
                    }

                    tList.add(tMap);
                }
                vMap.put("targets", tList);
                verList.add(vMap);
            }

            root.put("versions", verList);
            mapper.writeValue(targetFile, root);
        } catch (Exception e) {
            System.err.println("Failed to write bucket_targets.yaml: " + e.getMessage());
        }
    }
}
