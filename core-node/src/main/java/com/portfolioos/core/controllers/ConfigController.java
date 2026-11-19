package com.portfolioos.core.controllers;

import com.portfolioos.core.rules.BucketConfigLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ConfigController {

    @GetMapping({"/config/bucket-targets", "/sync/config/bucket-targets"})
    public ResponseEntity<BucketConfigLoader.BucketRulesConfig> getBucketTargets() {
        return ResponseEntity.ok(BucketConfigLoader.loadConfig());
    }

    @GetMapping("/rebalance/plan")
    public ResponseEntity<?> getRebalancePlanAlias(@RequestParam(value = "trigger", required = false, defaultValue = "INDUCED") String triggerType) {
        // Forwarding to SyncController endpoint logic
        String encodedTrigger = URLEncoder.encode(triggerType != null ? triggerType : "INDUCED", StandardCharsets.UTF_8);
        return ResponseEntity.status(307).header("Location", "/api/v1/sync/rebalance/plan?trigger=" + encodedTrigger).build();
    }
}
