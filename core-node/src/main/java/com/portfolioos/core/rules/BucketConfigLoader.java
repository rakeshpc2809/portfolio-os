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

    public record BucketTargetConfig(
        String bucket,
        double targetPct,
        double bandPct
    ) {}

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
                    targetConfigs.add(new BucketTargetConfig(
                        (String) tMap.get("bucket"),
                        ((Number) tMap.get("target_pct")).doubleValue(),
                        ((Number) tMap.get("band_pct")).doubleValue()
                    ));
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
        
        // Find latest version with effectiveFrom <= targetDateStr
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
            sumPct += tc.targetPct();
        }

        if (!providedBuckets.containsAll(requiredBuckets)) {
            throw new IllegalArgumentException("All 4 buckets must be defined: " + requiredBuckets);
        }

        if (Math.abs(sumPct - 100.0) > 0.05) {
            throw new IllegalArgumentException(String.format("Bucket target percentages must sum to 100.0%% (provided sum: %.2f%%)", sumPct));
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
            new BucketTargetConfig("EQUITY_CORE", 50.0, 5.0),
            new BucketTargetConfig("EQUITY_SATELLITE", 20.0, 5.0),
            new BucketTargetConfig("GOLD_SILVER", 15.0, 5.0),
            new BucketTargetConfig("LIQUID_BUFFER", 15.0, 5.0)
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
