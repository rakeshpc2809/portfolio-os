package com.portfolioos.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.portfolioos.core.crypto.CloudBundleCrypto;
import com.portfolioos.core.dtos.SyncDtos.UnidirectionalSyncSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@Service
public class CloudBundleExporter {

    private static final Logger log = LoggerFactory.getLogger(CloudBundleExporter.class);
    private static final String DEFAULT_BUNDLE_FILE = "data/portfolio_state.enc";

    private final PortfolioValuationService valuationService;
    private final ObjectMapper objectMapper;
    private final String syncSecretKey;

    @Autowired
    public CloudBundleExporter(
        PortfolioValuationService valuationService,
        ObjectMapper objectMapper,
        @Value("${portfolio.cloud.sync-secret-key:dev_secret_key_123}") String syncSecretKey
    ) {
        this.valuationService = valuationService;
        this.objectMapper = objectMapper.copy().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        this.syncSecretKey = syncSecretKey;
    }

    /**
     * Builds the zero-knowledge mobile state bundle (strictly ZERO folio_no or raw DB)
     * compresses, encrypts with AES-256-GCM + HKDF-SHA256, and writes to data/portfolio_state.enc.
     */
    public byte[] exportAndEncryptBundle(UnidirectionalSyncSnapshot snapshot) throws Exception {
        Map<String, Object> bundle = new LinkedHashMap<>();

        // 1. Core financial data
        bundle.put("version", "v4.1");
        bundle.put("exported_at", java.time.Instant.now().toString());
        bundle.put("sync_info", snapshot.syncInfo());
        bundle.put("holdings", snapshot.holdings());
        bundle.put("open_lots", snapshot.taxLots()); // FlatTaxLotDto has no folio_no
        bundle.put("net_worth_history", snapshot.netWorthHistory());
        bundle.put("rebalance_plan", snapshot.rebalancePlan());
        bundle.put("bucket_allocation", snapshot.bucketAllocation());
        bundle.put("daily_brief", snapshot.dailyBrief());
        bundle.put("ledger_hmac_root", snapshot.syncInfo() != null ? snapshot.syncInfo().ledgerHash() : "");

        // 2. Overlap analytics
        try {
            Map<String, Object> overlap = valuationService.getPortfolioOverlapAnalytics(null, null, false);
            bundle.put("overlap_report", overlap);
        } catch (Exception e) {
            log.warn("Could not attach overlap report to cloud bundle: {}", e.getMessage());
        }

        // 3. Action recommendations
        try {
            bundle.put("action_recommendations", valuationService.getActionRecommendations());
        } catch (Exception e) {
            log.warn("Could not attach action recommendations to cloud bundle: {}", e.getMessage());
        }

        // 4. HRP audit data
        try {
            File hrpFile = new File("data/hrp_audit.json");
            if (hrpFile.exists()) {
                String hrpJson = Files.readString(hrpFile.toPath(), StandardCharsets.UTF_8);
                bundle.put("hrp_audit", objectMapper.readValue(hrpJson, Map.class));
            }
        } catch (Exception e) {
            log.warn("Could not attach HRP audit to cloud bundle: {}", e.getMessage());
        }

        // 5. Serialize to JSON
        byte[] jsonBytes = objectMapper.writeValueAsBytes(bundle);

        // Security assertion: Zero folio_no in payload
        String jsonStr = new String(jsonBytes, StandardCharsets.UTF_8);
        if (jsonStr.contains("\"folio_no\"") || jsonStr.contains("\"folioNo\"")) {
            throw new SecurityException("CRITICAL: Privacy leak detected! folio_no must never be exported in cloud state bundle.");
        }

        // 6. Encrypt with AES-256-GCM
        byte[] encryptedBytes = CloudBundleCrypto.encryptBundle(jsonBytes, syncSecretKey);

        // 7. Write to default file location
        File targetFile = new File(DEFAULT_BUNDLE_FILE);
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(encryptedBytes);
        }
        log.info("Exported AEAD-encrypted cloud bundle: {} bytes -> {}", encryptedBytes.length, targetFile.getAbsolutePath());

        return encryptedBytes;
    }

    public byte[] getExistingEncryptedBundle() {
        File file = new File(DEFAULT_BUNDLE_FILE);
        if (file.exists() && file.length() > 0) {
            try {
                return Files.readAllBytes(file.toPath());
            } catch (Exception e) {
                log.error("Failed to read existing encrypted bundle: {}", e.getMessage());
            }
        }
        return null;
    }
}
