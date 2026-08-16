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
        return com.portfolioos.core.valuation.BucketEngine.classifyAssetToBucket(assetId, assetName).name();
    }

    public static boolean isPreferredFund(String assetId) {
        if (assetId == null) return false;
        BucketTargetVersion version = getActiveVersion(LocalDate.now());
        if (version != null && version.targets() != null) {
            for (BucketTargetConfig target : version.targets()) {
                if (target.preferredFunds() != null) {
                    for (PreferredFundConfig fund : target.preferredFunds()) {
                        if (assetId.equalsIgnoreCase(fund.fundId())) {
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
        List<BucketTargetVersion> versions
    ) {}

    private static BucketRulesConfig cachedRules = null;

    public static synchronized BucketRulesConfig loadConfig() {
        if (cachedRules != null) {
            return cachedRules;
        }

        File ruleFile = findConfigFile();
        if (ruleFile == null || !ruleFile.exists()) {
            cachedRules = createDefaultConfig();
            saveConfigToDisk(cachedRules);
            return cachedRules;
        }

        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> data = mapper.readValue(ruleFile, Map.class);
            if (data == null || !data.containsKey("versions")) {
                cachedRules = createDefaultConfig();
                return cachedRules;
            }

            List<Map<String, Object>> verList = (List<Map<String, Object>>) data.get("versions");
            List<BucketTargetVersion> parsedVersions = new ArrayList<>();

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

            cachedRules = new BucketRulesConfig(parsedVersions);
            return cachedRules;
        } catch (Exception e) {
            System.err.println("Failed to load bucket_targets.yaml, falling back to defaults: " + e.getMessage());
            cachedRules = createDefaultConfig();
            return cachedRules;
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

        List<BucketEngine.BucketTarget> result = new ArrayList<>();
        for (BucketTargetConfig tc : activeVer.targets()) {
            BucketEngine.Bucket b;
            try {
                b = BucketEngine.Bucket.valueOf(tc.bucket());
            } catch (Exception e) {
                continue;
            }
            result.add(new BucketEngine.BucketTarget(
                b,
                BigDecimal.valueOf(tc.targetPct()).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(tc.bandPct()).setScale(2, RoundingMode.HALF_UP)
            ));
        }
        return result.isEmpty() ? BucketEngine.DEFAULT_TARGETS : result;
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

    public static List<PreferredFundConfig> getDefaultPreferredFundsForBucket(String bucketName) {
        if (bucketName == null) return List.of();
        switch (bucketName) {
            case "EQUITY_CORE" -> {
                return List.of(
                    new PreferredFundConfig("INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund", 0.50),
                    new PreferredFundConfig("INF879O01027", "Parag Parikh Flexi Cap Fund", 0.50)
                );
            }
            case "EQUITY_SATELLITE" -> {
                return List.of(
                    new PreferredFundConfig("INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund", 0.25),
                    new PreferredFundConfig("INF754K01TN5", "Edelweiss Nifty500 Multicap Momentum Quality 50 Index Fund", 0.25),
                    new PreferredFundConfig("INF204K01K15", "Nippon India Small Cap Fund", 0.25),
                    new PreferredFundConfig("INF247L01BQ9", "Motilal Oswal Nifty Microcap 250 Index Fund", 0.25)
                );
            }
            case "GOLD_SILVER" -> {
                return List.of(
                    new PreferredFundConfig("INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", 1.00)
                );
            }
            case "LIQUID_BUFFER" -> {
                return List.of(
                    new PreferredFundConfig("INF209K01157", "Invesco India Arbitrage Fund", 1.00)
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
            if (f.exists()) return f;
        }
        return locations.get(0);
    }

    private static BucketRulesConfig createDefaultConfig() {
        List<BucketTargetConfig> defaults = List.of(
            new BucketTargetConfig("EQUITY_CORE", 50.0, 5.0, 5.0, "CORE", getDefaultPreferredFundsForBucket("EQUITY_CORE")),
            new BucketTargetConfig("EQUITY_SATELLITE", 20.0, 5.0, 5.0, "SATELLITE", getDefaultPreferredFundsForBucket("EQUITY_SATELLITE")),
            new BucketTargetConfig("GOLD_SILVER", 15.0, 5.0, 12.0, "ACCUMULATOR", getDefaultPreferredFundsForBucket("GOLD_SILVER")),
            new BucketTargetConfig("LIQUID_BUFFER", 15.0, 5.0, 5.0, "ARBITRAGE", getDefaultPreferredFundsForBucket("LIQUID_BUFFER"))
        );
        return new BucketRulesConfig(List.of(
            new BucketTargetVersion("v1.0", "2024-01-01", defaults)
        ));
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
