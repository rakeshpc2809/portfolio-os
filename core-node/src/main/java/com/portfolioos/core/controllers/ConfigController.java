package com.portfolioos.core.controllers;

import com.portfolioos.core.rules.BucketConfigLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ConfigController {

    @GetMapping("/config/bucket-targets")
    public ResponseEntity<BucketConfigLoader.BucketRulesConfig> getBucketTargets() {
        return ResponseEntity.ok(BucketConfigLoader.loadConfig());
    }

    @PutMapping("/config/bucket-targets")
    public ResponseEntity<?> updateBucketTargets(@RequestBody Map<String, Object> req) {
        try {
            String effectiveFrom = (String) req.getOrDefault("effectiveFrom", req.get("effective_from"));
            List<Map<String, Object>> targetsList = (List<Map<String, Object>>) req.get("targets");

            if (targetsList == null || targetsList.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'targets' array in request body"));
            }

            List<BucketConfigLoader.BucketTargetConfig> newTargets = targetsList.stream().map(tMap -> {
                String bName = (String) tMap.get("bucket");
                double tPct = ((Number) tMap.get("targetPct") != null ? (Number) tMap.get("targetPct") : (Number) tMap.get("target_pct")).doubleValue();
                double bPct = ((Number) tMap.get("bandPct") != null ? (Number) tMap.get("bandPct") : (Number) tMap.get("band_pct")).doubleValue();

                List<BucketConfigLoader.PreferredFundConfig> prefFunds = new ArrayList<>();
                if (tMap.containsKey("preferredFunds") || tMap.containsKey("preferred_funds")) {
                    List<Map<String, Object>> pfList = (List<Map<String, Object>>) tMap.getOrDefault("preferredFunds", tMap.get("preferred_funds"));
                    for (Map<String, Object> pfMap : pfList) {
                        prefFunds.add(new BucketConfigLoader.PreferredFundConfig(
                            (String) pfMap.get("fundId"),
                            (String) pfMap.get("fundName"),
                            ((Number) pfMap.get("allocationWeight")).doubleValue()
                        ));
                    }
                } else {
                    prefFunds = BucketConfigLoader.getDefaultPreferredFundsForBucket(bName);
                }
                return new BucketConfigLoader.BucketTargetConfig(bName, tPct, bPct, prefFunds);
            }).toList();

            BucketConfigLoader.updateBucketTargets(newTargets, effectiveFrom);
            return ResponseEntity.ok(BucketConfigLoader.loadConfig());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to update bucket targets: " + e.getMessage()));
        }
    }

    @GetMapping("/rebalance/plan")
    public ResponseEntity<?> getRebalancePlanAlias(@RequestParam(value = "trigger", required = false, defaultValue = "INDUCED") String triggerType) {
        // Forwarding to SyncController endpoint logic
        return ResponseEntity.status(307).header("Location", "/api/v1/sync/rebalance/plan?trigger=" + triggerType).build();
    }
}
