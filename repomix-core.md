This file is a merged representation of a subset of the codebase, containing files not matching ignore patterns, combined into a single document by Repomix.
The content has been processed where empty lines have been removed.

# File Summary

## Purpose
This file contains a packed representation of a subset of the repository's contents that is considered the most important context.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.

## File Format
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Repository files (if enabled)
5. Multiple file entries, each consisting of:
  a. A header with the file path (## File: path/to/file)
  b. The full contents of the file in a code block

## Usage Guidelines
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.

## Notes
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Files matching these patterns are excluded: target/**, .mvn/**, **/*.class, **/*.jar
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Empty lines have been removed from all files
- Files are sorted by Git change count (files with more changes are at the bottom)

# Directory Structure
```
src/
  main/
    java/
      com/
        portfolioos/
          core/
            config/
              AppConfig.java
            controllers/
              RebalanceController.java
              ReportController.java
              StatementsController.java
              SyncController.java
            dtos/
              ReportDtos.java
              SyncDtos.java
            fire/
              FireTracker.java
            goals/
              GoalTracker.java
            matcher/
              FifoMatcher.java
              TaxClassifier.java
            model/
              AssetCategory.java
              EventType.java
              Lot.java
              MatchedLot.java
              TaxEvent.java
              TaxTerm.java
            nav/
              AmfiNavSync.java
            persistence/
              DuckDbProjector.java
              SqliteEventStore.java
            ports/
              EventStorePort.java
            reconciliation/
              ReconciliationGate.java
            reporting/
              ExemptionTracker.java
              Itr2CsvExporter.java
              TaxReportExporter.java
            rpc/
              FlightRpcClient.java
            rules/
              TaxRulesConfig.java
              TaxRulesLoader.java
            security/
              SecurityConfig.java
              SecurityInterceptor.java
            service/
              PortfolioValuationService.java
              TaxOptimizationService.java
            util/
              Pair.java
            valuation/
              AntigravityEngine.java
              BucketEngine.java
              ConsolidationRebalanceEngine.java
              HarvestAdvisor.java
              RebalanceEngine.java
            xirr/
              CashFlow.java
              XirrEngine.java
            CoreApplication.java
    resources/
      static/
        src/
          js/
            modules/
              insurance.js
              portfolio.js
              tax.js
            api.js
            state.js
            utils.js
          app.js
          style.css
        index.html
      application.yml
build.gradle
Dockerfile
pom.xml
settings.gradle
```

# Files

## File: src/main/java/com/portfolioos/core/config/AppConfig.java
```java
package com.portfolioos.core.config;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.persistence.SqliteEventStore;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.rpc.FlightRpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class AppConfig {
    @Bean
    public EventStorePort eventStore(
        @Value("${sqlite.path:data/tax_ledger.db}") String dbPath
    ) {
        return new SqliteEventStore(dbPath);
    }
    @Bean
    public DuckDbProjector duckDbProjector(
        @Value("${duckdb.path:data/tax_ledger.duckdb}") String dbPath
    ) {
        return new DuckDbProjector(dbPath);
    }
    @Bean
    public FlightRpcClient flightRpcClient(
        @Value("${quant-sidecar.flight.host:quant-sidecar}") String host,
        @Value("${quant-sidecar.flight.port:8001}") int port
    ) {
        return new FlightRpcClient(host, port);
    }
}
```

## File: src/main/java/com/portfolioos/core/controllers/RebalanceController.java
```java
package com.portfolioos.core.controllers;
import com.portfolioos.core.dtos.ReportDtos.*;
import com.portfolioos.core.service.PortfolioValuationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
@RestController
@RequestMapping("/api/v1")
public class RebalanceController {
    private final PortfolioValuationService valuationService;
    public RebalanceController(PortfolioValuationService valuationService) {
        this.valuationService = valuationService;
    }
    @GetMapping({"/rebalance/bucket", "/portfolio/buckets/rebalance"})
    public ResponseEntity<BucketRebalanceResponse> getBucketRebalance(
        @RequestParam(value = "benchmarkCurrent", defaultValue = "24000.00") BigDecimal benchmarkCurrent,
        @RequestParam(value = "benchmarkRollingHigh", defaultValue = "25000.00") BigDecimal benchmarkRollingHigh,
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(valuationService.getBucketRebalance(benchmarkCurrent, benchmarkRollingHigh, fy));
    }
    @GetMapping({"/rebalance/preview", "/portfolio/rebalance-preview"})
    public ResponseEntity<RebalancePreviewDto> getRebalancePreview(
        @RequestParam(value = "amount", defaultValue = "100000") BigDecimal amount,
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(valuationService.getRebalancePreview(amount, fy));
    }
    @GetMapping({"/rebalance/consolidation", "/portfolio/consolidation-preview"})
    public ResponseEntity<ConsolidationPreviewResponse> getConsolidationPreview(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(valuationService.getConsolidationPreview(fy));
    }
    @GetMapping({"/goals/summary", "/portfolio/goals"})
    public ResponseEntity<GoalSummaryResponse> getGoalSummary() {
        return ResponseEntity.ok(valuationService.getGoalSummary());
    }
    @GetMapping({"/fire/summary", "/portfolio/fire"})
    public ResponseEntity<FireSummaryResponse> getFireSummary() {
        return ResponseEntity.ok(valuationService.getFireSummary());
    }
}
```

## File: src/main/java/com/portfolioos/core/controllers/ReportController.java
```java
package com.portfolioos.core.controllers;
import com.portfolioos.core.dtos.ReportDtos.*;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.reporting.TaxReportExporter;
import com.portfolioos.core.service.PortfolioValuationService;
import com.portfolioos.core.service.TaxOptimizationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
@RestController
@RequestMapping("/api/v1")
public class ReportController {
    private final PortfolioValuationService valuationService;
    private final TaxOptimizationService taxService;
    public ReportController(PortfolioValuationService valuationService, TaxOptimizationService taxService) {
        this.valuationService = valuationService;
        this.taxService = taxService;
    }
    @GetMapping({"/reports/summary", "/portfolio/summary"})
    public ResponseEntity<PortfolioSummaryResponse> getSummary(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(valuationService.getPortfolioSummary(fy));
    }
    @GetMapping({"/reports/holdings", "/portfolio/holdings"})
    public ResponseEntity<List<HoldingDetailDto>> getHoldings() {
        return ResponseEntity.ok(valuationService.getHoldings());
    }
    @GetMapping({"/reports/allocations/asset", "/portfolio/allocation"})
    public ResponseEntity<List<AssetAllocationEntry>> getAssetAllocation() {
        return ResponseEntity.ok(valuationService.getAssetAllocation());
    }
    @GetMapping({"/reports/allocations/category", "/portfolio/category-allocation"})
    public ResponseEntity<List<CategoryAllocationEntry>> getCategoryAllocation() {
        return ResponseEntity.ok(valuationService.getCategoryAllocation());
    }
    @GetMapping({"/reports/tax/exemption", "/tax/exemption-status"})
    public ResponseEntity<ExemptionTracker.ExemptionStatus> getExemptionStatus(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(taxService.getExemptionStatus(fy));
    }
    @GetMapping({"/reports/tax/itr2", "/tax/reports/itr2"})
    public ResponseEntity<TaxReportExporter.Itr2ScheduleCgReport> getItr2Report(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(taxService.generateItr2Report(fy));
    }
    @GetMapping({"/reports/tax/harvest", "/tax/harvest-opportunities"})
    public ResponseEntity<List<HarvestOpportunityDto>> getHarvestOpportunities() {
        return ResponseEntity.ok(taxService.getHarvestOpportunities());
    }
    @GetMapping({"/reports/tax/maturation", "/tax/maturation-ladder"})
    public ResponseEntity<List<MaturationLadderDto>> getMaturationLadder() {
        return ResponseEntity.ok(taxService.getMaturationLadder());
    }
    @GetMapping({"/reports/tax/realized", "/tax/realized-log"})
    public ResponseEntity<List<RealizedLogDto>> getRealizedLog(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(taxService.getRealizedLog(fy));
    }
    @GetMapping({"/reports/tax/itr2/csv", "/tax/export/itr2/zip"})
    public ResponseEntity<byte[]> downloadItr2Csv(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) throws IOException {
        Map<String, String> files = taxService.downloadItr2Files(fy);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                zos.putNextEntry(entry);
                zos.write(file.getValue().getBytes("UTF-8"));
                zos.closeEntry();
            }
        }
        byte[] zipBytes = baos.toByteArray();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"itr2_schedule_cg_" + fy + ".zip\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .contentLength(zipBytes.length)
            .body(zipBytes);
    }
}
```

## File: src/main/java/com/portfolioos/core/controllers/StatementsController.java
```java
package com.portfolioos.core.controllers;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.ports.EventStorePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/statements")
public class StatementsController {
    private final EventStorePort eventStore;
    private final DuckDbProjector duckDbProjector;
    private final RestClient restClient;
    public StatementsController(
        EventStorePort eventStore,
        DuckDbProjector duckDbProjector,
        @Value("${quant-sidecar.url:http://quant-sidecar:8000}") String sidecarUrl
    ) {
        this.eventStore = eventStore;
        this.duckDbProjector = duckDbProjector;
        this.restClient = RestClient.builder().baseUrl(sidecarUrl).build();
    }
    public record ParsedEventDto(
        String id,
        String assetId,
        String assetName,
        String isin,
        String eventType,
        String eventDate,
        BigDecimal units,
        BigDecimal pricePerUnit,
        BigDecimal grossAmount,
        String sourceDocumentId
    ) {}
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadStatement(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "password", required = false) String password
    ) {
        try {
            // Forward request to sidecar
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            // Convert file to ByteArrayResource for multipart formatting
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("file", fileResource);
            if (password != null && !password.isEmpty()) {
                body.add("password", password);
            }
            // POST to parser sidecar
            ResponseEntity<ParsedEventDto[]> response = restClient.post()
                .uri("/api/v1/parse")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .toEntity(ParsedEventDto[].class);
            ParsedEventDto[] dtoList = response.getBody();
            if (dtoList == null || dtoList.length == 0) {
                return ResponseEntity.ok(List.of());
            }
            // Convert to domain entities and append to event store
            List<TaxEvent> taxEvents = new java.util.ArrayList<>();
            for (ParsedEventDto dto : dtoList) {
                TaxEvent te = new TaxEvent(
                    dto.id() != null ? dto.id() : UUID.randomUUID().toString(),
                    dto.assetId(),
                    dto.assetName(),
                    dto.isin(),
                    EventType.valueOf(dto.eventType()),
                    LocalDate.parse(dto.eventDate()),
                    dto.units(),
                    dto.pricePerUnit(),
                    dto.grossAmount(),
                    dto.sourceDocumentId(),
                    Instant.now()
                );
                taxEvents.add(te);
            }
            // Write to SQLite
            eventStore.appendEvents(taxEvents);
            // Re-project events in DuckDB
            List<TaxEvent> allEvents = eventStore.getAllEvents();
            duckDbProjector.projectEvents(allEvents);
            return ResponseEntity.ok(dtoList);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("File reading failed: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Upload and parsing failed: " + e.getMessage());
        }
    }
}
```

## File: src/main/java/com/portfolioos/core/controllers/SyncController.java
```java
package com.portfolioos.core.controllers;
import com.portfolioos.core.dtos.SyncDtos.*;
import com.portfolioos.core.fire.FireTracker;
import com.portfolioos.core.goals.GoalTracker;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.nav.AmfiNavSync;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.valuation.AntigravityEngine;
import com.portfolioos.core.valuation.BucketEngine;
import com.portfolioos.core.valuation.HarvestAdvisor;
import com.portfolioos.core.xirr.CashFlow;
import com.portfolioos.core.xirr.XirrEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {
    private final EventStorePort eventStore;
    private final AmfiNavSync amfiSync = new AmfiNavSync();
    private final FifoMatcher fifoMatcher = new FifoMatcher();
    private final XirrEngine xirrEngine = new XirrEngine();
    public SyncController(EventStorePort eventStore) {
        this.eventStore = eventStore;
    }
    private static String detectFineBucket(String assetName) {
        String upper = assetName.toUpperCase();
        if (upper.contains("FLEXI")) return "Flexi Cap";
        if (upper.contains("LARGE") && upper.contains("MID")) return "Large & Midcap";
        if (upper.contains("MICROCAP") || upper.contains("MICRO")) return "Microcap";
        if (upper.contains("SMALL")) return "Small Cap";
        if (upper.contains("MIDCAP") || upper.contains("MID CAP")) return "Midcap";
        if (upper.contains("VALUE")) return "Factor Value Index";
        if (upper.contains("MOMENTUM") || upper.contains("QUALITY")) return "Factor Momentum Index";
        if (upper.contains("EQUAL WEIGHT") || upper.contains("EQUAL")) return "Equal Weight Index";
        if (upper.contains("HEALTHCARE") || upper.contains("TECH") || upper.contains("SECTOR")) return "Sectoral/Thematic";
        if (upper.contains("GOLD") || upper.contains("SGB") || upper.contains("SILVER")) return "Gold & Commodities";
        if (upper.contains("DEBT") || upper.contains("LIQUID") || upper.contains("BOND")) return "Debt & Liquid";
        return "Core Equity";
    }
    @GetMapping("/snapshot")
    public ResponseEntity<UnidirectionalSyncSnapshot> getSnapshot(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        FifoMatcher.FifoResult matchResult = fifoMatcher.processEvents(allEvents);
        List<Lot> openLots = matchResult.openLots();
        List<MatchedLot> matchedLots = matchResult.matchedLots();
        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> navMap = amfiSync.getNavMap();
        Locale inLocale = new Locale("en", "IN");
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(inLocale);
        // Compute ledger hash
        String ledgerRaw = allEvents.stream()
            .map(e -> e.id() + ":" + e.ingestedAt())
            .collect(Collectors.joining("|"));
        String ledgerHash = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(ledgerRaw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            ledgerHash = sb.toString();
        } catch (Exception ex) {
            ledgerHash = "default_hash";
        }
        // Calculate overall XIRR & Totals
        List<CashFlow> portfolioCashflows = new ArrayList<>();
        BigDecimal totalPortfolioCurrentVal = BigDecimal.ZERO;
        BigDecimal totalPortfolioInvested = BigDecimal.ZERO;
        for (Lot lot : openLots) {
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
            totalPortfolioCurrentVal = totalPortfolioCurrentVal.add(lot.remainingUnits().multiply(nav));
            totalPortfolioInvested = totalPortfolioInvested.add(lot.totalCostBasis());
        }
        for (TaxEvent event : allEvents) {
            if (event.eventType() == EventType.ACQUISITION || event.eventType() == EventType.SIP_INSTALMENT) {
                portfolioCashflows.add(new CashFlow(event.eventDate(), event.grossAmount().negate()));
            } else if (event.eventType() == EventType.DISPOSAL) {
                portfolioCashflows.add(new CashFlow(event.eventDate(), event.grossAmount()));
            }
        }
        portfolioCashflows.add(new CashFlow(today, totalPortfolioCurrentVal));
        double overallXirr = portfolioCashflows.size() >= 2 ? xirrEngine.calculateXirr(portfolioCashflows) : 0.0;
        BigDecimal unrealizedGain = totalPortfolioCurrentVal.subtract(totalPortfolioInvested);
        // Group open lots by asset for FlatHoldingDto
        Map<String, List<Lot>> groupedByAsset = openLots.stream().collect(Collectors.groupingBy(Lot::assetId));
        List<FlatHoldingDto> holdings = new ArrayList<>();
        for (Map.Entry<String, List<Lot>> entry : groupedByAsset.entrySet()) {
            String assetId = entry.getKey();
            List<Lot> lots = entry.getValue();
            String assetName = lots.get(0).assetName();
            BigDecimal totalUnits = lots.stream().map(Lot::remainingUnits).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCost = lots.stream().map(Lot::totalCostBasis).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgCost = totalUnits.compareTo(BigDecimal.ZERO) > 0 
                ? totalCost.divide(totalUnits, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            String bucket = detectFineBucket(assetName);
            // Holding XIRR calculation
            List<TaxEvent> assetEvents = allEvents.stream().filter(e -> e.assetId().equals(assetId)).toList();
            List<CashFlow> holdingCashflows = new ArrayList<>();
            for (TaxEvent event : assetEvents) {
                if (event.eventType() == EventType.ACQUISITION || event.eventType() == EventType.SIP_INSTALMENT) {
                    holdingCashflows.add(new CashFlow(event.eventDate(), event.grossAmount().negate()));
                } else if (event.eventType() == EventType.DISPOSAL) {
                    holdingCashflows.add(new CashFlow(event.eventDate(), event.grossAmount()));
                }
            }
            BigDecimal nav = navMap.getOrDefault(assetId, lots.get(0).costPerUnit());
            BigDecimal holdingCurVal = totalUnits.multiply(nav);
            holdingCashflows.add(new CashFlow(today, holdingCurVal));
            double holdingXirr = holdingCashflows.size() >= 2 ? xirrEngine.calculateXirr(holdingCashflows) : 0.0;
            holdings.add(new FlatHoldingDto(
                assetId,
                assetName,
                totalUnits.doubleValue(),
                avgCost.doubleValue(),
                BigDecimal.valueOf(holdingXirr).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                bucket,
                holdingCurVal.doubleValue(),
                totalCost.doubleValue(),
                currencyFormat.format(holdingCurVal),
                currencyFormat.format(totalCost)
            ));
        }
        // Construct FlatTaxLotDto
        List<FlatTaxLotDto> taxLots = new ArrayList<>();
        for (Lot lot : openLots) {
            AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
            boolean isListed = TaxClassifier.isListed(lot.assetId(), lot.assetName());
            long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
            com.portfolioos.core.model.TaxTerm taxTerm = TaxClassifier.classifyTaxTerm(category, holdingDays, fy, isListed);
            boolean isLongTerm = taxTerm == com.portfolioos.core.model.TaxTerm.LONG_TERM;
            String classification = switch (category) {
                case DEBT_SPECIFIED_50AA -> "SEC_50AA_DEBT";
                case EQUITY -> "SEC_112A_EQUITY";
                default -> category.name();
            };
            long daysToLtcg = isLongTerm ? 0L : Math.max(0L, 365L - holdingDays);
            taxLots.add(new FlatTaxLotDto(
                lot.assetId(),
                lot.acquisitionDate().toString(),
                lot.remainingUnits().doubleValue(),
                classification,
                isLongTerm,
                lot.isGrandfathered() ? lot.fmv20180131().doubleValue() : null,
                lot.costPerUnit().doubleValue(),
                holdingDays,
                daysToLtcg
            ));
        }
        // Generate Priority AI Radar Signals (Tax + Quant Engine Intelligence)
        List<RadarSignalDto> radarSignals = new ArrayList<>();
        // 1. Priority Tax Loss Harvesting Signals
        ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(matchedLots, fy);
        HarvestAdvisor.TaxHarvestResult harvestPlan = HarvestAdvisor.generateHarvestPlan(
            openLots, navMap, new BigDecimal(exStatus.exemptionUsed()), fy
        );
        Map<String, List<HarvestAdvisor.TaxHarvestRecommendation>> harvestByScheme = harvestPlan.recommendations().stream()
            .collect(Collectors.groupingBy(HarvestAdvisor.TaxHarvestRecommendation::assetName));
        List<RadarSignalDto> harvestSignals = new ArrayList<>();
        for (Map.Entry<String, List<HarvestAdvisor.TaxHarvestRecommendation>> entry : harvestByScheme.entrySet()) {
            String schemeName = entry.getKey();
            List<HarvestAdvisor.TaxHarvestRecommendation> recs = entry.getValue();
            BigDecimal totalHarvestGain = recs.stream()
                .map(HarvestAdvisor.TaxHarvestRecommendation::unrealizedLtcgGain)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalUnitsToSell = recs.stream()
                .map(HarvestAdvisor.TaxHarvestRecommendation::unitsToHarvest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            harvestSignals.add(new RadarSignalDto(
                "HARVEST",
                schemeName,
                "TAX-LOSS HARVEST OPPORTUNITY",
                "Harvest " + currencyFormat.format(totalHarvestGain) + " tax-free LTCG gain across " + recs.size() + " lots (" + totalUnitsToSell.setScale(2, RoundingMode.HALF_UP) + " units) before Mar 31.",
                "WARNING",
                "Priority Action"
            ));
        }
        harvestSignals.sort((a, b) -> b.description().compareTo(a.description()));
        radarSignals.addAll(harvestSignals.stream().limit(3).toList());
        // 2. Quant Engine Intelligence Factor Signals
        Map<String, String> assetNames = openLots.stream().collect(Collectors.toMap(Lot::assetId, Lot::assetName, (a, b) -> a));
        Map<String, List<Double>> assetReturnsMap = new HashMap<>();
        for (String assetId : assetNames.keySet()) {
            String name = assetNames.get(assetId);
            boolean isLowBeta = name.toLowerCase().contains("value") || name.toLowerCase().contains("equal");
            double betaMult = isLowBeta ? 0.42 : 1.10;
            double zBoost = isLowBeta ? 0.008 : 0.003;
            List<Double> simulatedReturns = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                simulatedReturns.add((Math.sin(i) * 0.005) + (betaMult * -0.002) + zBoost);
            }
            assetReturnsMap.put(assetId, simulatedReturns);
        }
        List<Double> marketReturns = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            marketReturns.add((Math.sin(i) * 0.005) - 0.003);
        }
        AntigravityEngine.AntigravitySummary antigravitySummary = AntigravityEngine.analyzePortfolioFactors(
            assetReturnsMap, assetNames, marketReturns, new BigDecimal("6.5")
        );
        for (AntigravityEngine.AssetFactorScore factorScore : antigravitySummary.allAssetScores()) {
            if (factorScore.downsideBeta().compareTo(new BigDecimal("0.75")) < 0) {
                radarSignals.add(new RadarSignalDto(
                    "QUANT_FACTOR",
                    factorScore.assetName(),
                    "QUANT INTELLIGENCE: DOWNSIDE CUSHION",
                    factorScore.assetName() + " has Downside Beta β = " + factorScore.downsideBeta() + ". Protects capital during market drops.",
                    "INFO",
                    "β = " + factorScore.downsideBeta()
                ));
            } else if (factorScore.zScore30d().compareTo(new BigDecimal("0.30")) > 0) {
                radarSignals.add(new RadarSignalDto(
                    "QUANT_FACTOR",
                    factorScore.assetName(),
                    "QUANT INTELLIGENCE: MOMENTUM OUTPERFORMER",
                    factorScore.assetName() + " displays 30-day Z-Score momentum +" + factorScore.zScore30d() + ". TWR 30d: +" + factorScore.twr30dPct() + "%.",
                    "INFO",
                    "Z = +" + factorScore.zScore30d()
                ));
            }
        }
        // Limit Quant Signals to top 3
        if (radarSignals.size() > 6) {
            radarSignals = new ArrayList<>(radarSignals.subList(0, 6));
        }
        long now = System.currentTimeMillis();
        SyncInfoDto syncInfo = new SyncInfoDto(
            now / 1000,
            ledgerHash,
            LocalDate.now().atStartOfDay().toString(),
            fy,
            BigDecimal.valueOf(overallXirr).setScale(2, RoundingMode.HALF_UP).doubleValue(),
            String.format("%.2f%%", overallXirr),
            totalPortfolioInvested.doubleValue(),
            totalPortfolioCurrentVal.doubleValue(),
            unrealizedGain.doubleValue(),
            currencyFormat.format(totalPortfolioCurrentVal),
            currencyFormat.format(totalPortfolioInvested),
            (unrealizedGain.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + currencyFormat.format(unrealizedGain)
        );
        return ResponseEntity.ok(new UnidirectionalSyncSnapshot(
            syncInfo, holdings, taxLots, radarSignals
        ));
    }
    @PostMapping("/pair")
    public ResponseEntity<PairResponseDto> pairDevice(
        @RequestBody PairRequestDto req
    ) {
        String token = "fintracker_jwt_" + req.device_id() + "_" + System.currentTimeMillis();
        return ResponseEntity.ok(new PairResponseDto(
            "SUCCESS",
            token,
            "my-fintracker-core"
        ));
    }
}
```

## File: src/main/java/com/portfolioos/core/dtos/ReportDtos.java
```java
package com.portfolioos.core.dtos;
import java.util.List;
import java.util.Map;
public class ReportDtos {
    public record PortfolioSummaryResponse(
        String totalInvested,
        String totalCurrentValue,
        String totalUnrealizedGain,
        String xirrPercentage,
        int activeHoldingCount,
        int staleNavCount
    ) {}
    public record AssetAllocationEntry(
        String assetId,
        String assetName,
        String investedValue,
        String currentValue,
        String percentage,
        boolean navStale
    ) {}
    public record CategoryAllocationEntry(
        String category,
        String categoryName,
        String investedValue,
        String currentValue,
        String percentage
    ) {}
    public record OpenLotDto(
        String lotId,
        String acquisitionDate,
        String remainingUnits,
        String costPerUnit,
        String totalCostBasis,
        String currentNav,
        String currentValue,
        String unrealizedGain,
        long holdingDays,
        long daysToLtcg,
        boolean isLtcg
    ) {}
    public record HoldingDetailDto(
        String assetId,
        String assetName,
        String category,
        String investedValue,
        String currentValue,
        String unrealizedGain,
        String unrealizedGainPct,
        String allocationPct,
        boolean navStale,
        List<OpenLotDto> lots
    ) {}
    public record HarvestOpportunityDto(
        String assetId,
        String assetName,
        String lotId,
        String remainingUnits,
        String potentialHarvestableLoss
    ) {}
    public record MaturationLadderDto(
        String assetId,
        String assetName,
        String lotId,
        String acquisitionDate,
        String remainingUnits,
        String totalCostBasis,
        String currentValue,
        String unrealizedGain,
        long holdingDays,
        long daysRemainingToLtcg,
        String targetLtcgDate
    ) {}
    public record RealizedLogDto(
        String matchId,
        String disposalDate,
        String acquisitionDate,
        String assetId,
        String assetName,
        String unitsMatched,
        String saleProceeds,
        String costBasis,
        String realizedGain,
        String taxTerm,
        long holdingPeriodDays
    ) {}
    public record BucketStatusDto(
        String bucket,
        String currentValue,
        String currentPct,
        String targetPct,
        String driftPct,
        boolean isDrifted
    ) {}
    public record RebalanceRecommendationDto(
        String assetId,
        String assetName,
        String bucket,
        String action,
        String amount,
        String triggerType,
        String estimatedTaxDrag,
        String taxTermSummary
    ) {}
    public record DrawdownStatusDto(
        String benchmarkName,
        String currentLevel,
        String rollingHigh,
        String drawdownPct,
        List<Integer> activeRungsFired,
        String recommendedBufferDeployPct
    ) {}
    public record BucketRebalanceResponse(
        List<BucketStatusDto> bucketStatuses,
        List<RebalanceRecommendationDto> recommendations,
        DrawdownStatusDto drawdownStatus,
        boolean calendarTriggerFired,
        boolean drawdownTriggerFired
    ) {}
    public record GoalAllocationDto(
        String holdingId,
        String holdingName,
        String goalTag,
        String allocatedAmount
    ) {}
    public record GoalSummaryResponse(
        String totalLiquidHoldings,
        String allocatedGoalsAmount,
        String unallocatedCash,
        Map<String, String> allocationsByGoal,
        List<GoalAllocationDto> goalAllocations
    ) {}
    public record FireScenarioDto(
        String id,
        String label,
        String monthlyExpenseToday,
        boolean active
    ) {}
    public record FireSummaryResponse(
        String activeScenarioLabel,
        String monthlyExpenseToday,
        String annualExpense,
        String requiredCorpus,
        String totalNetWorth,
        String epfBalance,
        String nonRetirementGoalAllocations,
        String fireInvestableNetWorth,
        String projectedCorpusAtTargetAge,
        int yearsRemaining,
        String status,
        String shortageOrSurplusAmount,
        boolean reviewDatePassed,
        List<FireScenarioDto> scenarios
    ) {}
    public record RebalanceLotDto(
        String assetName,
        String unitsToSell,
        String redemptionProceeds,
        String estimatedGain,
        String taxTerm,
        String estimatedTaxDrag
    ) {}
    public record RebalancePreviewDto(
        String targetRedemptionAmount,
        String actualRedemptionAmount,
        String totalEstimatedGain,
        String totalTaxDrag,
        String effectiveTaxRatePct,
        String ltcgExemptionHarvested,
        List<RebalanceLotDto> selectedLots
    ) {}
    public record PhasedOutAssetSummaryDto(
        String assetId,
        String assetName,
        String currentUnits,
        String currentValue,
        String totalCostBasis,
        String unrealizedGain,
        boolean isLtcg,
        String estimatedTaxDrag
    ) {}
    public record ExistingSipAllocationDto(
        String assetId,
        String assetName,
        String sipWeightPct,
        String deploymentAmount
    ) {}
    public record ConsolidationPreviewResponse(
        List<PhasedOutAssetSummaryDto> phasedOutAssets,
        String totalProceeds,
        String totalEstimatedGain,
        String totalTaxDrag,
        String ltcgExemptionHarvested,
        List<ExistingSipAllocationDto> proRataAllocations,
        boolean isRebalanceWindowOpen,
        String nextScheduledWindow
    ) {}
}
```

## File: src/main/java/com/portfolioos/core/dtos/SyncDtos.java
```java
package com.portfolioos.core.dtos;
import java.util.List;
public class SyncDtos {
    public record SyncInfoDto(
        long timestamp,
        String ledger_hash,
        String generated_at,
        String fiscal_year,
        double portfolio_xirr,
        String xirr_percentage,
        double total_invested,
        double current_value,
        double unrealized_gain,
        String formatted_current_value,
        String formatted_total_invested,
        String formatted_unrealized_gain
    ) {}
    public record FlatHoldingDto(
        String isin,
        String fund_name,
        double total_units,
        double avg_cost,
        double xirr,
        String asset_bucket,
        double current_value,
        double invested_value,
        String formatted_current_value,
        String formatted_invested_value
    ) {}
    public record FlatTaxLotDto(
        String isin,
        String buy_date,
        double units,
        String tax_classification,
        boolean is_long_term,
        Double grandfathered_nav,
        double cost_per_unit,
        long holding_days,
        long days_to_ltcg
    ) {}
    public record RadarSignalDto(
        String signal_type,
        String title,
        String subtitle,
        String description,
        String severity,
        String badge_text
    ) {}
    public record UnidirectionalSyncSnapshot(
        SyncInfoDto sync_info,
        List<FlatHoldingDto> holdings,
        List<FlatTaxLotDto> tax_lots,
        List<RadarSignalDto> radar_signals
    ) {}
    public record PairRequestDto(
        String device_id,
        String device_name
    ) {}
    public record PairResponseDto(
        String status,
        String token,
        String server_name
    ) {}
}
```

## File: src/main/java/com/portfolioos/core/fire/FireTracker.java
```java
package com.portfolioos.core.fire;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.goals.GoalTracker;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
public class FireTracker {
    public record FireScenario(
        String id,
        String label,
        BigDecimal monthlyExpenseToday,
        boolean active
    ) {}
    public static class FireProfile {
        private final int currentAge = 32;
        private final int targetRetirementAge = 45;
        private final BigDecimal swrPercent = new BigDecimal("3.0");
        private final BigDecimal epfBalance = BigDecimal.ZERO;
        private final int epfUnlockAge = 58;
        private final BigDecimal realReturnRatePct = new BigDecimal("6.0");
        private final BigDecimal monthlyContribution = new BigDecimal("75000.00");
        private final LocalDate nextReviewDate = LocalDate.parse("2027-03-31");
        private final List<FireScenario> scenarios = List.of(
            new FireScenario("scen_1", "Primary Expense Target", new BigDecimal("60000.00"), true),
            new FireScenario("scen_2", "Expanded Expense Target", new BigDecimal("90000.00"), false)
        );
        public int currentAge() { return currentAge; }
        public int targetRetirementAge() { return targetRetirementAge; }
        public BigDecimal swrPercent() { return swrPercent; }
        public BigDecimal epfBalance() { return epfBalance; }
        public int epfUnlockAge() { return epfUnlockAge; }
        public BigDecimal realReturnRatePct() { return realReturnRatePct; }
        public BigDecimal monthlyContribution() { return monthlyContribution; }
        public LocalDate nextReviewDate() { return nextReviewDate; }
        public List<FireScenario> scenarios() { return scenarios; }
    }
    public record FireSummary(
        String activeScenarioLabel,
        BigDecimal monthlyExpenseToday,
        BigDecimal annualExpense,
        BigDecimal requiredCorpus,
        BigDecimal totalNetWorth,
        BigDecimal epfBalance,
        BigDecimal nonRetirementGoalAllocations,
        BigDecimal fireInvestableNetWorth,
        BigDecimal projectedCorpusAtTargetAge,
        int yearsRemaining,
        String status, // "ON_TRACK" or "SHORT"
        BigDecimal shortageOrSurplusAmount,
        boolean reviewDatePassed,
        List<FireScenario> scenarios
    ) {}
    public static FireSummary calculateFireSummary(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        FireProfile profile,
        BigDecimal bankBalance
    ) {
        BigDecimal totalMFValue = BigDecimal.ZERO;
        for (Lot lot : openLots) {
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
            totalMFValue = totalMFValue.add(lot.remainingUnits().multiply(nav));
        }
        BigDecimal totalNetWorth = totalMFValue.add(bankBalance).add(profile.epfBalance());
        GoalTracker.GoalSummary goalSummary = GoalTracker.calculateGoalSummary(
            openLots, navMap, GoalTracker.DEFAULT_ALLOCATIONS, bankBalance
        );
        BigDecimal nonRetirementGoals = goalSummary.allocatedGoalsAmount();
        BigDecimal fireInvestableNetWorth = totalNetWorth.subtract(profile.epfBalance())
                                                     .subtract(nonRetirementGoals)
                                                     .max(BigDecimal.ZERO);
        FireScenario activeScenario = profile.scenarios().stream()
            .filter(FireScenario::active)
            .findFirst()
            .orElse(profile.scenarios().get(0));
        BigDecimal annualExpense = activeScenario.monthlyExpenseToday().multiply(new BigDecimal("12"));
        BigDecimal swrFraction = profile.swrPercent().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal requiredCorpus = BigDecimal.ZERO;
        if (swrFraction.compareTo(BigDecimal.ZERO) > 0) {
            requiredCorpus = annualExpense.divide(swrFraction, 2, RoundingMode.HALF_UP);
        }
        int yearsRemaining = Math.max(0, profile.targetRetirementAge() - profile.currentAge());
        double realRate = profile.realReturnRatePct().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP).doubleValue();
        double compoundFactor = Math.pow(1.0 + realRate, yearsRemaining);
        BigDecimal fvInvestable = fireInvestableNetWorth.multiply(BigDecimal.valueOf(compoundFactor));
        BigDecimal fvSips;
        if (realRate > 0.0) {
            double annualContribution = profile.monthlyContribution().multiply(new BigDecimal("12")).doubleValue();
            double fvAnnuity = annualContribution * ((compoundFactor - 1.0) / realRate);
            fvSips = BigDecimal.valueOf(fvAnnuity);
        } else {
            fvSips = profile.monthlyContribution().multiply(new BigDecimal("12")).multiply(BigDecimal.valueOf(yearsRemaining));
        }
        BigDecimal projectedCorpus = fvInvestable.add(fvSips).setScale(2, RoundingMode.HALF_UP);
        BigDecimal diff = projectedCorpus.subtract(requiredCorpus);
        boolean isOnTrack = diff.compareTo(BigDecimal.ZERO) >= 0;
        String status = isOnTrack ? "ON_TRACK" : "SHORT";
        boolean reviewDatePassed = !currentDate.isBefore(profile.nextReviewDate());
        return new FireSummary(
            activeScenario.label(),
            activeScenario.monthlyExpenseToday(),
            annualExpense,
            requiredCorpus,
            totalNetWorth.setScale(2, RoundingMode.HALF_UP),
            profile.epfBalance(),
            nonRetirementGoals,
            fireInvestableNetWorth.setScale(2, RoundingMode.HALF_UP),
            projectedCorpus,
            yearsRemaining,
            status,
            diff.abs().setScale(2, RoundingMode.HALF_UP),
            reviewDatePassed,
            profile.scenarios()
        );
    }
    public static FireSummary calculateFireSummary(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate
    ) {
        return calculateFireSummary(openLots, navMap, currentDate, new FireProfile(), BigDecimal.ZERO);
    }
}
```

## File: src/main/java/com/portfolioos/core/goals/GoalTracker.java
```java
package com.portfolioos.core.goals;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.valuation.BucketEngine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class GoalTracker {
    public enum GoalTag {
        EMERGENCY,
        BIKE,
        WEDDING,
        RETIREMENT,
        UNALLOCATED
    }
    public record GoalAllocation(
        String holdingId,
        String holdingName,
        GoalTag goalTag,
        BigDecimal allocatedAmount
    ) {}
    public record GoalSummary(
        BigDecimal totalLiquidHoldings,
        BigDecimal allocatedGoalsAmount,
        BigDecimal unallocatedCash,
        Map<GoalTag, BigDecimal> allocationsByGoal,
        List<GoalAllocation> goalAllocations
    ) {}
    public static final List<GoalAllocation> DEFAULT_ALLOCATIONS = List.of(
        new GoalAllocation("BANK_IDLE", "Bank Savings & Liquid Buffer", GoalTag.EMERGENCY, new BigDecimal("150000.00")),
        new GoalAllocation("BANK_IDLE", "Bank Savings & Liquid Buffer", GoalTag.BIKE, new BigDecimal("100000.00")),
        new GoalAllocation("BANK_IDLE", "Bank Savings & Liquid Buffer", GoalTag.WEDDING, new BigDecimal("100000.00"))
    );
    public static GoalSummary calculateGoalSummary(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        List<GoalAllocation> customAllocations,
        BigDecimal bankBalance
    ) {
        BigDecimal totalLiquidMF = BigDecimal.ZERO;
        for (Lot lot : openLots) {
            BucketEngine.Bucket bucket = BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName());
            if (bucket == BucketEngine.Bucket.LIQUID_BUFFER) {
                BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
                totalLiquidMF = totalLiquidMF.add(lot.remainingUnits().multiply(nav));
            }
        }
        BigDecimal totalLiquidHoldings = totalLiquidMF.add(bankBalance);
        Map<GoalTag, BigDecimal> allocatedMap = new HashMap<>();
        for (GoalTag tag : GoalTag.values()) {
            allocatedMap.put(tag, BigDecimal.ZERO);
        }
        BigDecimal totalAllocatedNonUnallocated = BigDecimal.ZERO;
        for (GoalAllocation alloc : customAllocations) {
            BigDecimal cur = allocatedMap.getOrDefault(alloc.goalTag(), BigDecimal.ZERO);
            allocatedMap.put(alloc.goalTag(), cur.add(alloc.allocatedAmount()));
            if (alloc.goalTag() != GoalTag.UNALLOCATED) {
                totalAllocatedNonUnallocated = totalAllocatedNonUnallocated.add(alloc.allocatedAmount());
            }
        }
        BigDecimal unallocatedCash = totalLiquidHoldings.subtract(totalAllocatedNonUnallocated).max(BigDecimal.ZERO);
        allocatedMap.put(GoalTag.UNALLOCATED, unallocatedCash);
        Map<GoalTag, BigDecimal> formattedAllocationsByGoal = new HashMap<>();
        for (Map.Entry<GoalTag, BigDecimal> entry : allocatedMap.entrySet()) {
            formattedAllocationsByGoal.put(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP));
        }
        return new GoalSummary(
            totalLiquidHoldings.setScale(2, RoundingMode.HALF_UP),
            totalAllocatedNonUnallocated.setScale(2, RoundingMode.HALF_UP),
            unallocatedCash.setScale(2, RoundingMode.HALF_UP),
            formattedAllocationsByGoal,
            customAllocations
        );
    }
    public static GoalSummary calculateGoalSummary(List<Lot> openLots, Map<String, BigDecimal> navMap) {
        return calculateGoalSummary(openLots, navMap, DEFAULT_ALLOCATIONS, BigDecimal.ZERO);
    }
}
```

## File: src/main/java/com/portfolioos/core/matcher/FifoMatcher.java
```java
package com.portfolioos.core.matcher;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.model.TaxTerm;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
public class FifoMatcher {
    public record FifoResult(List<Lot> openLots, List<MatchedLot> matchedLots) {}
    public FifoResult processEvents(List<TaxEvent> events) {
        List<TaxEvent> sortedEvents = new ArrayList<>(events);
        sortedEvents.sort(Comparator.comparing(TaxEvent::eventDate).thenComparing(TaxEvent::ingestedAt));
        List<Lot> openLotsQueue = new ArrayList<>();
        List<MatchedLot> matchedLots = new ArrayList<>();
        for (TaxEvent event : sortedEvents) {
            switch (event.eventType()) {
                case ACQUISITION, SIP_INSTALMENT, DIVIDEND_REINVEST -> {
                    openLotsQueue.add(new Lot(
                        UUID.randomUUID().toString(),
                        event.assetId(),
                        event.assetName(),
                        event.eventDate(),
                        event.units(),
                        event.units(),
                        event.pricePerUnit(),
                        event.grossAmount(),
                        false, // isGrandfathered - can be set based on date in a later step
                        BigDecimal.ZERO
                    ));
                }
                case BONUS -> {
                    openLotsQueue.add(new Lot(
                        UUID.randomUUID().toString(),
                        event.assetId(),
                        event.assetName(),
                        event.eventDate(),
                        event.units(),
                        event.units(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        false,
                        BigDecimal.ZERO
                    ));
                }
                case SPLIT -> {
                    BigDecimal splitRatio = event.units();
                    if (splitRatio.compareTo(BigDecimal.ZERO) > 0) {
                        for (int i = 0; i < openLotsQueue.size(); i++) {
                            Lot current = openLotsQueue.get(i);
                            if (current.assetId().equals(event.assetId())) {
                                BigDecimal newOriginal = current.originalUnits().multiply(splitRatio);
                                BigDecimal newRemaining = current.remainingUnits().multiply(splitRatio);
                                BigDecimal newCostPerUnit = BigDecimal.ZERO;
                                if (newRemaining.compareTo(BigDecimal.ZERO) > 0) {
                                    newCostPerUnit = current.totalCostBasis().divide(newRemaining, 4, RoundingMode.HALF_UP);
                                }
                                openLotsQueue.set(i, current.withRemainingUnitsAndCost(newRemaining, newCostPerUnit, current.totalCostBasis())
                                    .withAssetDetails(current.assetId(), current.assetName(), newOriginal, newRemaining, newCostPerUnit));
                            }
                        }
                    }
                }
                case DISPOSAL, SGB_MATURITY -> {
                    BigDecimal unitsToMatch = event.units();
                    boolean isSgbMaturity = event.eventType() == EventType.SGB_MATURITY;
                    int i = 0;
                    while (i < openLotsQueue.size() && unitsToMatch.compareTo(BigDecimal.ZERO) > 0) {
                        Lot currentLot = openLotsQueue.get(i);
                        if (!currentLot.assetId().equals(event.assetId()) || currentLot.remainingUnits().compareTo(BigDecimal.ZERO) <= 0) {
                            i++;
                            continue;
                        }
                        BigDecimal matchedUnits = unitsToMatch.min(currentLot.remainingUnits());
                        BigDecimal costBasisSlice = matchedUnits.multiply(currentLot.costPerUnit());
                        BigDecimal saleProceedsSlice = matchedUnits.multiply(event.pricePerUnit());
                        BigDecimal realizedGain = saleProceedsSlice.subtract(costBasisSlice);
                        long holdingDays = ChronoUnit.DAYS.between(currentLot.acquisitionDate(), event.eventDate());
                        AssetCategory category = TaxClassifier.detectCategory(event.assetId(), event.assetName());
                        boolean isListed = TaxClassifier.isListed(event.assetId(), event.assetName());
                        TaxTerm taxTerm = isSgbMaturity ? TaxTerm.EXEMPT 
                            : TaxClassifier.classifyTaxTerm(category, holdingDays, "2026-27", isListed);
                        matchedLots.add(new MatchedLot(
                            UUID.randomUUID().toString(),
                            event.id(),
                            currentLot.lotId(),
                            event.assetId(),
                            currentLot.acquisitionDate(),
                            event.eventDate(),
                            matchedUnits,
                            costBasisSlice,
                            saleProceedsSlice,
                            realizedGain,
                            holdingDays,
                            taxTerm,
                            category
                        ));
                        unitsToMatch = unitsToMatch.subtract(matchedUnits);
                        BigDecimal updatedRemaining = currentLot.remainingUnits().subtract(matchedUnits);
                        if (updatedRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                            openLotsQueue.remove(i);
                        } else {
                            openLotsQueue.set(i, currentLot.withRemainingUnitsAndCost(updatedRemaining, currentLot.costPerUnit(), currentLot.totalCostBasis()));
                            i++;
                        }
                    }
                }
                case MERGER -> {
                    // Corporate merger event
                    BigDecimal swapRatio = event.pricePerUnit().compareTo(BigDecimal.ZERO) > 0 ? event.pricePerUnit() : event.units();
                    for (int j = 0; j < openLotsQueue.size(); j++) {
                        Lot current = openLotsQueue.get(j);
                        if (current.assetId().equals(event.assetId())) {
                            BigDecimal newOriginal = swapRatio.compareTo(BigDecimal.ZERO) > 0 ? current.originalUnits().multiply(swapRatio) : current.originalUnits();
                            BigDecimal newRemaining = swapRatio.compareTo(BigDecimal.ZERO) > 0 ? current.remainingUnits().multiply(swapRatio) : current.remainingUnits();
                            BigDecimal newCostPerUnit = BigDecimal.ZERO;
                            if (newRemaining.compareTo(BigDecimal.ZERO) > 0) {
                                newCostPerUnit = current.totalCostBasis().divide(newRemaining, 4, RoundingMode.HALF_UP);
                            }
                            String newAssetId = (event.isin() != null && !event.isin().isBlank()) ? event.isin() : current.assetId();
                            String newAssetName = (event.assetName() != null && !event.assetName().isBlank()) ? event.assetName() : current.assetName();
                            openLotsQueue.set(j, current.withAssetDetails(newAssetId, newAssetName, newOriginal, newRemaining, newCostPerUnit));
                        }
                    }
                }
                case SGB_INTEREST -> {
                    // cash income, doesn't impact stock lots
                }
            }
        }
        return new FifoResult(openLotsQueue, matchedLots);
    }
}
```

## File: src/main/java/com/portfolioos/core/matcher/TaxClassifier.java
```java
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
```

## File: src/main/java/com/portfolioos/core/model/AssetCategory.java
```java
package com.portfolioos.core.model;
public enum AssetCategory {
    EQUITY,
    DEBT_SPECIFIED_50AA,
    GOLD_SILVER,
    INTERNATIONAL,
    SGB
}
```

## File: src/main/java/com/portfolioos/core/model/EventType.java
```java
package com.portfolioos.core.model;
public enum EventType {
    ACQUISITION,
    SIP_INSTALMENT,
    DISPOSAL,
    BONUS,
    SPLIT,
    DIVIDEND_REINVEST,
    SGB_INTEREST,
    SGB_MATURITY,
    MERGER
}
```

## File: src/main/java/com/portfolioos/core/model/Lot.java
```java
package com.portfolioos.core.model;
import java.math.BigDecimal;
import java.time.LocalDate;
public record Lot(
    String lotId,
    String assetId,
    String assetName,
    LocalDate acquisitionDate,
    BigDecimal originalUnits,
    BigDecimal remainingUnits,
    BigDecimal costPerUnit,
    BigDecimal totalCostBasis,
    boolean isGrandfathered,
    BigDecimal fmv20180131
) {
    public Lot withRemainingUnitsAndCost(BigDecimal remaining, BigDecimal cost, BigDecimal costBasis) {
        return new Lot(
            lotId, assetId, assetName, acquisitionDate, originalUnits,
            remaining, cost, costBasis, isGrandfathered, fmv20180131
        );
    }
    public Lot withAssetDetails(String newAssetId, String newAssetName, BigDecimal newOriginal, BigDecimal newRemaining, BigDecimal newCostPerUnit) {
        return new Lot(
            lotId, newAssetId, newAssetName, acquisitionDate, newOriginal,
            newRemaining, newCostPerUnit, totalCostBasis, isGrandfathered, fmv20180131
        );
    }
}
```

## File: src/main/java/com/portfolioos/core/model/MatchedLot.java
```java
package com.portfolioos.core.model;
import java.math.BigDecimal;
import java.time.LocalDate;
public record MatchedLot(
    String matchId,
    String disposalEventId,
    String lotId,
    String assetId,
    LocalDate acquisitionDate,
    LocalDate disposalDate,
    BigDecimal unitsMatched,
    BigDecimal costBasis,
    BigDecimal saleProceeds,
    BigDecimal realizedGain,
    long holdingPeriodDays,
    TaxTerm taxTerm,
    AssetCategory assetCategory
) {}
```

## File: src/main/java/com/portfolioos/core/model/TaxEvent.java
```java
package com.portfolioos.core.model;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
public record TaxEvent(
    String id,
    String assetId,
    String assetName,
    String isin,
    EventType eventType,
    LocalDate eventDate,
    BigDecimal units,
    BigDecimal pricePerUnit,
    BigDecimal grossAmount,
    String sourceDocumentId,
    Instant ingestedAt
) {
    public BigDecimal unitDelta() {
        return switch (eventType) {
            case DISPOSAL, SGB_MATURITY -> units.negate();
            case SGB_INTEREST -> BigDecimal.ZERO;
            default -> units;
        };
    }
}
```

## File: src/main/java/com/portfolioos/core/model/TaxTerm.java
```java
package com.portfolioos.core.model;
public enum TaxTerm {
    SHORT_TERM,
    LONG_TERM,
    EXEMPT
}
```

## File: src/main/java/com/portfolioos/core/nav/AmfiNavSync.java
```java
package com.portfolioos.core.nav;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLConnection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class AmfiNavSync {
    public record NavEntry(
        String schemeCode,
        String isin,
        String schemeName,
        BigDecimal nav,
        LocalDate date
    ) {}
    private static final long CACHE_TTL_MS = 6 * 3600 * 1000L; // 6 Hours cache TTL
    private static final Object lock = new Object();
    private static List<NavEntry> cachedNavs = null;
    private static long lastFetchTimeMs = 0L;
    public List<NavEntry> parseAmfiFeed(String feedContent) {
        List<NavEntry> entries = new ArrayList<>();
        LocalDate today = LocalDate.now();
        String[] lines = feedContent.split("\\r?\\n");
        for (String line : lines) {
            String[] parts = line.split(";");
            if (parts.length >= 6) {
                String schemeCode = parts[0].trim();
                String isinGrowth = parts[1].trim();
                if (isinGrowth.isEmpty()) {
                    isinGrowth = null;
                }
                String schemeName = parts[3].trim();
                String navStr = parts[4].trim();
                try {
                    BigDecimal nav = new BigDecimal(navStr);
                    entries.add(new NavEntry(
                        schemeCode,
                        isinGrowth,
                        schemeName,
                        nav,
                        today
                    ));
                } catch (Exception e) {
                    // Skip headers or corrupted rows
                }
            }
        }
        return entries;
    }
    public List<NavEntry> fetchLatestNavsFromAmfi() {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (cachedNavs != null && (now - lastFetchTimeMs) < CACHE_TTL_MS) {
                return cachedNavs;
            }
            try {
                URI uri = new URI("https://www.amfiindia.com/spages/NAVAll.txt");
                URLConnection conn = uri.toURL().openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
                List<NavEntry> parsed = parseAmfiFeed(sb.toString());
                if (!parsed.isEmpty()) {
                    cachedNavs = parsed;
                    lastFetchTimeMs = System.currentTimeMillis();
                }
                return parsed;
            } catch (Exception e) {
                System.err.println("AMFI fetch error: " + e.getMessage());
                return cachedNavs != null ? cachedNavs : new ArrayList<>();
            }
        }
    }
    public Map<String, BigDecimal> getNavMap() {
        List<NavEntry> entries = fetchLatestNavsFromAmfi();
        Map<String, BigDecimal> navMap = new HashMap<>();
        for (NavEntry entry : entries) {
            if (entry.isin() != null) {
                navMap.put(entry.isin(), entry.nav());
            }
        }
        return navMap;
    }
}
```

## File: src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java
```java
package com.portfolioos.core.persistence;
import com.portfolioos.core.model.TaxEvent;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
public class DuckDbProjector {
    private final String dbPath;
    private final String jdbcUrl;
    private Connection connection;
    public DuckDbProjector() {
        this(System.getenv("DUCKDB_PATH") != null && !System.getenv("DUCKDB_PATH").isBlank()
             ? System.getenv("DUCKDB_PATH") : "data/tax_ledger.duckdb");
    }
    public DuckDbProjector(String dbPath) {
        this.dbPath = dbPath;
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("DuckDB JDBC driver not found", e);
        }
        if (":memory:".equals(dbPath)) {
            jdbcUrl = "jdbc:duckdb:";
        } else {
            File file = new File(dbPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            jdbcUrl = "jdbc:duckdb:" + file.getAbsolutePath();
        }
        try {
            connection = DriverManager.getConnection(jdbcUrl);
            initReadSchema();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to DuckDB", e);
        }
    }
    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl);
        }
        return connection;
    }
    private void initReadSchema() {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS projected_events (" +
                "  id VARCHAR PRIMARY KEY," +
                "  asset_id VARCHAR NOT NULL," +
                "  asset_name VARCHAR NOT NULL," +
                "  isin VARCHAR," +
                "  event_type VARCHAR NOT NULL," +
                "  event_date VARCHAR NOT NULL," +
                "  units VARCHAR NOT NULL," +
                "  price_per_unit VARCHAR NOT NULL," +
                "  gross_amount VARCHAR NOT NULL," +
                "  source_document_id VARCHAR NOT NULL," +
                "  ingested_at VARCHAR NOT NULL" +
                ")"
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize DuckDB schema", e);
        }
    }
    public void projectEvents(List<TaxEvent> events) {
        try {
            Connection conn = getConnection();
            boolean wasAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DROP TABLE IF EXISTS projected_events");
                }
                initReadSchema();
                if (!events.isEmpty()) {
                    String insertSql = "INSERT INTO projected_events (id, asset_id, asset_name, isin, event_type, event_date, units, price_per_unit, gross_amount, source_document_id, ingested_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        Set<String> processedIds = new HashSet<>();
                        for (TaxEvent event : events) {
                            if (processedIds.contains(event.id())) {
                                continue;
                            }
                            processedIds.add(event.id());
                            insertStmt.setString(1, event.id());
                            insertStmt.setString(2, event.assetId());
                            insertStmt.setString(3, event.assetName());
                            insertStmt.setString(4, event.isin());
                            insertStmt.setString(5, event.eventType().name());
                            insertStmt.setString(6, event.eventDate().toString());
                            insertStmt.setString(7, event.units().toPlainString());
                            insertStmt.setString(8, event.pricePerUnit().toPlainString());
                            insertStmt.setString(9, event.grossAmount().toPlainString());
                            insertStmt.setString(10, event.sourceDocumentId());
                            insertStmt.setString(11, event.ingestedAt().toString());
                            insertStmt.executeUpdate();
                        }
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed to project events in DuckDB", e);
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("DuckDB transaction failure", e);
        }
    }
}
```

## File: src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java
```java
package com.portfolioos.core.persistence;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.ports.EventStorePort;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
public class SqliteEventStore implements EventStorePort {
    private final String dbPath;
    private final String jdbcUrl;
    private final String hmacSecret;
    private final Object lock = new Object();
    private Connection connection;
    public SqliteEventStore() {
        this(System.getenv("SQLITE_PATH") != null && !System.getenv("SQLITE_PATH").isBlank() 
             ? System.getenv("SQLITE_PATH") : "data/tax_ledger.db");
    }
    public SqliteEventStore(String dbPath) {
        this.dbPath = dbPath;
        String envSecret = System.getenv("LEDGER_HMAC_SECRET");
        if (envSecret == null || envSecret.isBlank()) {
            System.err.println("SECURITY WARNING: LEDGER_HMAC_SECRET environment variable is unset. Using default development secret.");
            this.hmacSecret = "fintracker-cachyos-default-key-2026";
        } else {
            this.hmacSecret = envSecret;
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found", e);
        }
        if (":memory:".equals(dbPath)) {
            jdbcUrl = "jdbc:sqlite::memory:";
        } else {
            File file = new File(dbPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
        }
        try {
            connection = DriverManager.getConnection(jdbcUrl);
            initSchema();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to SQLite database", e);
        }
    }
    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl);
        }
        return connection;
    }
    private void initSchema() {
        synchronized (lock) {
            try (Statement stmt = getConnection().createStatement()) {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS tax_events (" +
                    "  id TEXT PRIMARY KEY," +
                    "  asset_id TEXT NOT NULL," +
                    "  asset_name TEXT NOT NULL," +
                    "  isin TEXT," +
                    "  event_type TEXT NOT NULL," +
                    "  event_date TEXT NOT NULL," +
                    "  units TEXT NOT NULL," +
                    "  price_per_unit TEXT NOT NULL," +
                    "  gross_amount TEXT NOT NULL," +
                    "  source_document_id TEXT NOT NULL," +
                    "  ingested_at TEXT NOT NULL," +
                    "  previous_hash TEXT NOT NULL," +
                    "  event_hash TEXT NOT NULL" +
                    ")"
                );
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize SQLite schema", e);
            }
        }
    }
    @Override
    public String getLatestEventHash() {
        synchronized (lock) {
            String sql = "SELECT event_hash FROM tax_events ORDER BY ingested_at DESC, id DESC LIMIT 1";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("event_hash");
                }
                return "GENESIS";
            } catch (SQLException e) {
                throw new RuntimeException("Failed to fetch latest event hash", e);
            }
        }
    }
    private String toCanonicalString(BigDecimal val) {
        return val.setScale(8, RoundingMode.HALF_UP).toPlainString();
    }
    private String computeHash(String prevHash, TaxEvent event) {
        String raw = prevHash + "|" + event.id() + "|" + event.assetId() + "|" + event.eventType().name() + "|" +
                     event.eventDate().toString() + "|" + toCanonicalString(event.units()) + "|" +
                     toCanonicalString(event.grossAmount()) + "|" + event.sourceDocumentId();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] bytes = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }
    @Override
    public String appendEvent(TaxEvent event) {
        List<String> hashes = appendEvents(List.of(event));
        return hashes.isEmpty() ? null : hashes.get(0);
    }
    @Override
    public List<String> appendEvents(List<TaxEvent> events) {
        if (events.isEmpty()) return List.of();
        synchronized (lock) {
            List<String> hashes = new ArrayList<>();
            Connection conn;
            boolean wasAutoCommit;
            try {
                conn = getConnection();
                wasAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
            } catch (SQLException e) {
                throw new RuntimeException("Database error in transaction config", e);
            }
            String checkSql = "SELECT event_hash FROM tax_events WHERE asset_id = ? AND event_type = ? AND event_date = ? AND units = ? AND gross_amount = ? AND source_document_id = ? LIMIT 1";
            String insertSql = "INSERT INTO tax_events (id, asset_id, asset_name, isin, event_type, event_date, units, price_per_unit, gross_amount, source_document_id, ingested_at, previous_hash, event_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                String prevHash = getLatestEventHash();
                if (prevHash == null) prevHash = "GENESIS";
                for (TaxEvent event : events) {
                    checkStmt.setString(1, event.assetId());
                    checkStmt.setString(2, event.eventType().name());
                    checkStmt.setString(3, event.eventDate().toString());
                    checkStmt.setString(4, event.units().toPlainString());
                    checkStmt.setString(5, event.grossAmount().toPlainString());
                    checkStmt.setString(6, event.sourceDocumentId());
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            String existingHash = rs.getString("event_hash");
                            hashes.add(existingHash);
                            continue;
                        }
                    }
                    String eventHash = computeHash(prevHash, event);
                    insertStmt.setString(1, event.id());
                    insertStmt.setString(2, event.assetId());
                    insertStmt.setString(3, event.assetName());
                    insertStmt.setString(4, event.isin());
                    insertStmt.setString(5, event.eventType().name());
                    insertStmt.setString(6, event.eventDate().toString());
                    insertStmt.setString(7, event.units().toPlainString());
                    insertStmt.setString(8, event.pricePerUnit().toPlainString());
                    insertStmt.setString(9, event.grossAmount().toPlainString());
                    insertStmt.setString(10, event.sourceDocumentId());
                    insertStmt.setString(11, event.ingestedAt().toString());
                    insertStmt.setString(12, prevHash);
                    insertStmt.setString(13, eventHash);
                    insertStmt.executeUpdate();
                    hashes.add(eventHash);
                    prevHash = eventHash;
                }
                conn.commit();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // ignore rollback errors
                }
                throw new RuntimeException("Failed to commit transaction ledger", e);
            } finally {
                try {
                    conn.setAutoCommit(wasAutoCommit);
                } catch (SQLException e) {
                    // ignore autocommit reset errors
                }
            }
            return hashes;
        }
    }
    @Override
    public List<TaxEvent> getEventsForAsset(String assetId) {
        synchronized (lock) {
            List<TaxEvent> events = new ArrayList<>();
            String sql = "SELECT * FROM tax_events WHERE asset_id = ? ORDER BY event_date ASC, ingested_at ASC";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setString(1, assetId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        events.add(mapResultSetToTaxEvent(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to fetch events for asset " + assetId, e);
            }
            return events;
        }
    }
    @Override
    public List<TaxEvent> getAllEvents() {
        synchronized (lock) {
            List<TaxEvent> events = new ArrayList<>();
            String sql = "SELECT * FROM tax_events ORDER BY event_date ASC, ingested_at ASC";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    events.add(mapResultSetToTaxEvent(rs));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to fetch all events", e);
            }
            return events;
        }
    }
    @Override
    public boolean verifyLedgerIntegrity() {
        synchronized (lock) {
            String sql = "SELECT previous_hash, event_hash, id, asset_id, event_type, event_date, units, gross_amount, source_document_id FROM tax_events ORDER BY ingested_at ASC, id ASC";
            try (PreparedStatement stmt = getConnection().prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                String expectedPrevHash = "GENESIS";
                while (rs.next()) {
                    String actualPrevHash = rs.getString("previous_hash");
                    String actualEventHash = rs.getString("event_hash");
                    if (!actualPrevHash.equals(expectedPrevHash)) {
                        return false;
                    }
                    TaxEvent mockEvent = new TaxEvent(
                        rs.getString("id"),
                        rs.getString("asset_id"),
                        "", // assetName not needed for hash
                        null,
                        EventType.valueOf(rs.getString("event_type")),
                        LocalDate.parse(rs.getString("event_date")),
                        rs.getBigDecimal("units"),
                        BigDecimal.ZERO,
                        rs.getBigDecimal("gross_amount"),
                        rs.getString("source_document_id"),
                        null
                    );
                    String recomputedHash = computeHash(expectedPrevHash, mockEvent);
                    if (!recomputedHash.equals(actualEventHash)) {
                        return false;
                    }
                    expectedPrevHash = actualEventHash;
                }
                return true;
            } catch (SQLException e) {
                throw new RuntimeException("Ledger integrity verification failed", e);
            }
        }
    }
    @Override
    public void clearAllEvents() {
        synchronized (lock) {
            try (Statement stmt = getConnection().createStatement()) {
                stmt.execute("DELETE FROM tax_events");
            } catch (SQLException e) {
                throw new RuntimeException("Failed to clear ledger", e);
            }
        }
    }
    private TaxEvent mapResultSetToTaxEvent(ResultSet rs) throws SQLException {
        return new TaxEvent(
            rs.getString("id"),
            rs.getString("asset_id"),
            rs.getString("asset_name"),
            rs.getString("isin"),
            EventType.valueOf(rs.getString("event_type")),
            LocalDate.parse(rs.getString("event_date")),
            new BigDecimal(rs.getString("units")),
            new BigDecimal(rs.getString("price_per_unit")),
            new BigDecimal(rs.getString("gross_amount")),
            rs.getString("source_document_id"),
            Instant.parse(rs.getString("ingested_at"))
        );
    }
}
```

## File: src/main/java/com/portfolioos/core/ports/EventStorePort.java
```java
package com.portfolioos.core.ports;
import com.portfolioos.core.model.TaxEvent;
import java.util.List;
public interface EventStorePort {
    String appendEvent(TaxEvent event);
    List<String> appendEvents(List<TaxEvent> events);
    List<TaxEvent> getEventsForAsset(String assetId);
    List<TaxEvent> getAllEvents();
    boolean verifyLedgerIntegrity();
    void clearAllEvents();
    String getLatestEventHash();
}
```

## File: src/main/java/com/portfolioos/core/reconciliation/ReconciliationGate.java
```java
package com.portfolioos.core.reconciliation;
import com.portfolioos.core.model.TaxEvent;
import java.math.BigDecimal;
import java.util.List;
public class ReconciliationGate {
    public record ReconciliationResult(
        boolean isMatched,
        BigDecimal calculatedClosingUnits,
        BigDecimal declaredClosingUnits,
        BigDecimal delta,
        String errorMessage
    ) {}
    public static ReconciliationResult validateStatement(List<TaxEvent> events, BigDecimal declaredClosingUnits) {
        BigDecimal calculatedClosingUnits = BigDecimal.ZERO;
        for (TaxEvent event : events) {
            calculatedClosingUnits = calculatedClosingUnits.add(event.unitDelta());
        }
        BigDecimal delta = calculatedClosingUnits.subtract(declaredClosingUnits).abs();
        boolean isMatched = delta.compareTo(new BigDecimal("0.0001")) < 0;
        String errorMessage = null;
        if (!isMatched) {
            errorMessage = "Reconciliation Gate Failure: Calculated closing units (" + calculatedClosingUnits +
                           ") does not match declared closing units (" + declaredClosingUnits + "). Delta: " + delta;
        }
        return new ReconciliationResult(isMatched, calculatedClosingUnits, declaredClosingUnits, delta, errorMessage);
    }
}
```

## File: src/main/java/com/portfolioos/core/reporting/ExemptionTracker.java
```java
package com.portfolioos.core.reporting;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
public class ExemptionTracker {
    public record ExemptionStatus(
        String fiscalYear,
        String grossLtcg,
        String grossLtcl,
        String grossStcg,
        String grossStcl,
        String netStcg,
        String netLtcgBeforeExemption,
        String exemptionLimit,
        String exemptionUsed,
        String exemptionRemaining,
        String taxableLtcg
    ) {}
    public static ExemptionStatus calculateExemptionStatus(List<MatchedLot> matchedLots, String fiscalYear) {
        Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
        LocalDate startDate = bounds.first();
        LocalDate endDate = bounds.second();
        List<MatchedLot> stgLots = matchedLots.stream().filter(lot -> 
            lot.taxTerm() == TaxTerm.SHORT_TERM &&
            !lot.disposalDate().isBefore(startDate) &&
            !lot.disposalDate().isAfter(endDate)
        ).toList();
        // Section 112A exemption applies ONLY to equity assets
        List<MatchedLot> equityLtgLots = matchedLots.stream().filter(lot -> 
            lot.taxTerm() == TaxTerm.LONG_TERM &&
            lot.assetCategory() == AssetCategory.EQUITY &&
            !lot.disposalDate().isBefore(startDate) &&
            !lot.disposalDate().isAfter(endDate)
        ).toList();
        BigDecimal gST = BigDecimal.ZERO;
        BigDecimal lST = BigDecimal.ZERO;
        for (MatchedLot lot : stgLots) {
            if (lot.realizedGain().compareTo(BigDecimal.ZERO) > 0) {
                gST = gST.add(lot.realizedGain());
            } else {
                lST = lST.add(lot.realizedGain().abs());
            }
        }
        BigDecimal gLT = BigDecimal.ZERO;
        BigDecimal lLT = BigDecimal.ZERO;
        for (MatchedLot lot : equityLtgLots) {
            if (lot.realizedGain().compareTo(BigDecimal.ZERO) > 0) {
                gLT = gLT.add(lot.realizedGain());
            } else {
                lLT = lLT.add(lot.realizedGain().abs());
            }
        }
        // STCL offsets STCG first
        BigDecimal netStcg = gST.subtract(lST).max(BigDecimal.ZERO);
        BigDecimal remainingStcl = lST.subtract(gST).max(BigDecimal.ZERO);
        // LTCL offsets LTCG, remaining STCL offsets LTCG
        BigDecimal netLtcgBeforeExemption = gLT.subtract(lLT).subtract(remainingStcl).max(BigDecimal.ZERO);
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
        BigDecimal exemptionLimit = rules.equityExemptionLimit();
        BigDecimal exemptionUsed = netLtcgBeforeExemption.min(exemptionLimit);
        BigDecimal exemptionRemaining = exemptionLimit.subtract(exemptionUsed).max(BigDecimal.ZERO);
        BigDecimal taxableLtcg = netLtcgBeforeExemption.subtract(exemptionUsed).max(BigDecimal.ZERO);
        return new ExemptionStatus(
            fiscalYear,
            fmt(gLT),
            fmt(lLT),
            fmt(gST),
            fmt(lST),
            fmt(netStcg),
            fmt(netLtcgBeforeExemption),
            fmt(exemptionLimit),
            fmt(exemptionUsed),
            fmt(exemptionRemaining),
            fmt(taxableLtcg)
        );
    }
    private static String fmt(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    public static Pair<LocalDate, LocalDate> getFiscalYearBounds(String fiscalYear) {
        String[] parts = fiscalYear.split("-");
        int startYear = 2026;
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
        return new Pair<>(LocalDate.of(startYear, 4, 1), LocalDate.of(endYear, 3, 31));
    }
    public record Pair<A, B>(A first, B second) {}
}
```

## File: src/main/java/com/portfolioos/core/reporting/Itr2CsvExporter.java
```java
package com.portfolioos.core.reporting;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.util.Pair;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
public class Itr2CsvExporter {
    private static final LocalDate GRANDFATHER_CUTOFF = LocalDate.of(2018, 1, 31);
    public static Map<String, String> exportItr2ScheduleCg(List<MatchedLot> matchedLots, String fiscalYear, Map<String, String> assetNameMap) {
        Map<String, String> map = new HashMap<>();
        map.put("Schedule_112A.csv", generateSchedule112aCsv(matchedLots, fiscalYear, assetNameMap, Map.of()));
        map.put("Schedule_STCG.csv", generateScheduleCgStcgCsv(matchedLots, fiscalYear, assetNameMap));
        return map;
    }
    public static String generateSchedule112aCsv(
        List<MatchedLot> matchedLots,
        String fiscalYear,
        Map<String, String> assetNameMap,
        Map<String, BigDecimal> fmv2018Map
    ) {
        Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
        LocalDate startDate = bounds.first();
        LocalDate endDate = bounds.second();
        List<MatchedLot> ltcgLots = matchedLots.stream().filter(lot ->
            lot.taxTerm() == TaxTerm.LONG_TERM &&
            !lot.disposalDate().isBefore(startDate) &&
            !lot.disposalDate().isAfter(endDate)
        ).toList();
        StringBuilder sb = new StringBuilder();
        sb.append("ISIN Code,Name of Share/Unit,No. of Shares/Units,Full Value of Consideration,Cost of Acquisition,FMV as on 31-Jan-2018,Total Deductions,Balance Capital Gain\n");
        Map<String, List<MatchedLot>> grouped = ltcgLots.stream().collect(Collectors.groupingBy(MatchedLot::assetId));
        for (Map.Entry<String, List<MatchedLot>> entry : grouped.entrySet()) {
            String isin = entry.getKey();
            List<MatchedLot> lots = entry.getValue();
            String name = assetNameMap.getOrDefault(isin, isin);
            BigDecimal totalUnits = BigDecimal.ZERO;
            BigDecimal proceeds = BigDecimal.ZERO;
            BigDecimal actualCost = BigDecimal.ZERO;
            boolean isPre2018 = false;
            for (MatchedLot lot : lots) {
                totalUnits = totalUnits.add(lot.unitsMatched());
                proceeds = proceeds.add(lot.saleProceeds());
                actualCost = actualCost.add(lot.costBasis());
                if (lot.acquisitionDate().isBefore(GRANDFATHER_CUTOFF) || lot.acquisitionDate().isEqual(GRANDFATHER_CUTOFF)) {
                    isPre2018 = true;
                }
            }
            BigDecimal fmvJan2018 = fmv2018Map.getOrDefault(isin, actualCost);
            // Statutory Section 55(2)(ac) Formula:
            // Deemed Cost = max(Actual Cost, min(FMV on 31-Jan-2018, Sale Proceeds))
            BigDecimal deemedCost;
            if (isPre2018) {
                BigDecimal lowerBound = fmvJan2018.min(proceeds);
                deemedCost = actualCost.max(lowerBound);
            } else {
                deemedCost = actualCost;
            }
            BigDecimal gain = proceeds.subtract(deemedCost);
            BigDecimal displayFmv = isPre2018 ? fmvJan2018 : BigDecimal.ZERO;
            sb.append("\"").append(isin).append("\",\"")
              .append(name.replace("\"", "\"\"")).append("\",")
              .append(fmt(totalUnits)).append(",")
              .append(fmt(proceeds)).append(",")
              .append(fmt(deemedCost)).append(",")
              .append(fmt(displayFmv)).append(",")
              .append("0.00,")
              .append(fmt(gain)).append("\n");
        }
        return sb.toString();
    }
    public static String generateScheduleCgStcgCsv(List<MatchedLot> matchedLots, String fiscalYear, Map<String, String> assetNameMap) {
        Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
        LocalDate startDate = bounds.first();
        LocalDate endDate = bounds.second();
        List<MatchedLot> stcgLots = matchedLots.stream().filter(lot ->
            lot.taxTerm() == TaxTerm.SHORT_TERM &&
            !lot.disposalDate().isBefore(startDate) &&
            !lot.disposalDate().isAfter(endDate)
        ).toList();
        StringBuilder sb = new StringBuilder();
        sb.append("Section,Asset Type,Asset Name,Disposal Date,Sale Proceeds,Cost Basis,STCG Realized,Tax Rate\n");
        for (MatchedLot lot : stcgLots) {
            String name = assetNameMap.getOrDefault(lot.assetId(), lot.assetId());
            AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), name);
            String section = (category == AssetCategory.DEBT_SPECIFIED_50AA) ? "Sec 50AA" : "Sec 111A";
            String taxRate = (category == AssetCategory.DEBT_SPECIFIED_50AA) ? "Slab Rate" : "20%";
            sb.append("\"").append(section).append("\",\"")
              .append(category.name()).append("\",\"")
              .append(name.replace("\"", "\"\"")).append("\",")
              .append(lot.disposalDate()).append(",")
              .append(fmt(lot.saleProceeds())).append(",")
              .append(fmt(lot.costBasis())).append(",")
              .append(fmt(lot.realizedGain())).append(",\"")
              .append(taxRate).append("\"\n");
        }
        return sb.toString();
    }
    public static String generateScheduleFaCsv(List<TaxEvent> allEventsList) {
        StringBuilder sb = new StringBuilder();
        sb.append("Country Code,Foreign Entity Name,Address,Initial Investment (INR),Peak Value INR (Requires Statement Verification),Closing Balance (INR),Gross Amount Paid/Credited\n");
        List<TaxEvent> intlEvents = allEventsList.stream().filter(e ->
            TaxClassifier.detectCategory(e.assetId(), e.assetName()) == AssetCategory.INTERNATIONAL
        ).toList();
        Map<String, List<TaxEvent>> grouped = intlEvents.stream().collect(Collectors.groupingBy(TaxEvent::assetId));
        for (Map.Entry<String, List<TaxEvent>> entry : grouped.entrySet()) {
            String isin = entry.getKey();
            List<TaxEvent> events = entry.getValue();
            String name = events.get(0).assetName();
            BigDecimal initialCost = BigDecimal.ZERO;
            for (TaxEvent e : events) {
                if (e.eventType() == EventType.ACQUISITION) {
                    initialCost = initialCost.add(e.grossAmount());
                }
            }
            sb.append("\"US\",\"").append(name.replace("\"", "\"\"")).append("\",\"United States\",")
              .append(fmt(initialCost)).append(",\"VERIFY_PEAK_NAV\",")
              .append(fmt(initialCost)).append(",0.00\n");
        }
        return sb.toString();
    }
    private static String fmt(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private static Pair<LocalDate, LocalDate> getFiscalYearBounds(String fiscalYear) {
        String[] parts = fiscalYear.split("-");
        int startYear = 2026;
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
        return new Pair<>(LocalDate.of(startYear, 4, 1), LocalDate.of(endYear, 3, 31));
    }
}
```

## File: src/main/java/com/portfolioos/core/reporting/TaxReportExporter.java
```java
package com.portfolioos.core.reporting;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.util.Pair;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
public class TaxReportExporter {
    public record Itr2ScheduleCgReport(
        String fiscalYear,
        String totalSaleProceeds,
        String totalCostBasis,
        String totalRealizedStcg,
        String totalRealizedLtcg,
        String netTaxableStcg,
        String ltcgExemptionUsed,
        String netTaxableLtcg,
        int matchedLotCount
    ) {}
    public static Itr2ScheduleCgReport generateItr2Report(List<MatchedLot> matchedLots, String fiscalYear) {
        Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
        LocalDate startDate = bounds.first();
        LocalDate endDate = bounds.second();
        List<MatchedLot> fyLots = matchedLots.stream().filter(lot ->
            !lot.disposalDate().isBefore(startDate) &&
            !lot.disposalDate().isAfter(endDate)
        ).toList();
        BigDecimal totalSaleProceeds = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;
        BigDecimal totalStcg = BigDecimal.ZERO;
        BigDecimal totalLtcg = BigDecimal.ZERO;
        for (MatchedLot lot : fyLots) {
            totalSaleProceeds = totalSaleProceeds.add(lot.saleProceeds());
            totalCostBasis = totalCostBasis.add(lot.costBasis());
            if (lot.taxTerm() == TaxTerm.SHORT_TERM) {
                totalStcg = totalStcg.add(lot.realizedGain());
            } else if (lot.taxTerm() == TaxTerm.LONG_TERM) {
                totalLtcg = totalLtcg.add(lot.realizedGain());
            }
        }
        ExemptionTracker.ExemptionStatus exemptionStatus = ExemptionTracker.calculateExemptionStatus(fyLots, fiscalYear);
        return new Itr2ScheduleCgReport(
            fiscalYear,
            fmt(totalSaleProceeds),
            fmt(totalCostBasis),
            fmt(totalStcg),
            fmt(totalLtcg),
            exemptionStatus.netStcg(),
            exemptionStatus.exemptionUsed(),
            exemptionStatus.taxableLtcg(),
            fyLots.size()
        );
    }
    private static String fmt(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    public static Pair<LocalDate, LocalDate> getFiscalYearBounds(String fiscalYear) {
        String[] parts = fiscalYear.split("-");
        int startYear = 2026;
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
        return new Pair<>(LocalDate.of(startYear, 4, 1), LocalDate.of(endYear, 3, 31));
    }
}
```

## File: src/main/java/com/portfolioos/core/rpc/FlightRpcClient.java
```java
package com.portfolioos.core.rpc;
import org.apache.arrow.flight.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class FlightRpcClient {
    private final String host;
    private final int port;
    private final String flightUrl;
    private final BufferAllocator allocator;
    public FlightRpcClient() {
        this("quant-sidecar", 8001);
    }
    public FlightRpcClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.flightUrl = "grpc+tcp://" + host + ":" + port;
        this.allocator = new RootAllocator(Long.MAX_VALUE);
    }
    public FlightRpcClient(String flightUrl) {
        this.flightUrl = flightUrl;
        URI uri = URI.create(flightUrl.replace("grpc+tcp://", "http://"));
        this.host = uri.getHost() != null ? uri.getHost() : "quant-sidecar";
        this.port = uri.getPort() > 0 ? uri.getPort() : 8001;
        this.allocator = new RootAllocator(Long.MAX_VALUE);
    }
    public Map<String, Map<String, Object>> computeQuantMetrics(Map<String, List<Double>> fundNavSeries) {
        Map<String, Map<String, Object>> results = new HashMap<>();
        if (fundNavSeries.isEmpty()) {
            return results;
        }
        try {
            Location location = Location.forGrpcInsecure(host, port);
            try (FlightClient client = FlightClient.builder(allocator, location).build()) {
                // Connection test handshake
                Iterable<ActionType> actions = client.listActions();
            }
        } catch (Exception e) {
            System.err.println("Arrow Flight connection check: " + e.getMessage());
        }
        return results;
    }
}
```

## File: src/main/java/com/portfolioos/core/rules/TaxRulesConfig.java
```java
package com.portfolioos.core.rules;
import java.math.BigDecimal;
public record TaxRulesConfig(
    String fiscalYear,
    long equityLtcgThresholdDays,
    BigDecimal equityLtcgRate,
    BigDecimal equityStcgRate,
    BigDecimal equityExemptionLimit,
    long goldInternationalThresholdDays,
    BigDecimal goldInternationalLtcgRate,
    boolean debtAlwaysShortTerm
) {}
```

## File: src/main/java/com/portfolioos/core/rules/TaxRulesLoader.java
```java
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
        // Search locations
        fileLocations.add(new File("rules/FY" + fiscalYear + ".yaml"));
        fileLocations.add(new File("../rules/FY" + fiscalYear + ".yaml"));
        fileLocations.add(new File("../../rules/FY" + fiscalYear + ".yaml"));
        fileLocations.add(new File("/app/rules/FY" + fiscalYear + ".yaml"));
        fileLocations.add(new File("rules/FY2026-27.yaml"));
        fileLocations.add(new File("../rules/FY2026-27.yaml"));
        fileLocations.add(new File("/app/rules/FY2026-27.yaml"));
        File ruleFile = null;
        for (File file : fileLocations) {
            if (file.exists()) {
                ruleFile = file;
                break;
            }
        }
        if (ruleFile == null) {
            String msg = "CRITICAL: Could not locate tax rules YAML file for FY " + fiscalYear;
            System.err.println(msg);
            throw new IllegalStateException(msg);
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
                eqMonths * 30L, // approx 360/365 days
                eqLtcgRate,
                eqStcgRate,
                eqExemption,
                goldMonths * 30L, // approx 720/730 days
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
```

## File: src/main/java/com/portfolioos/core/security/SecurityConfig.java
```java
package com.portfolioos.core.security;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class SecurityConfig implements WebMvcConfigurer {
    private final SecurityInterceptor securityInterceptor;
    public SecurityConfig(SecurityInterceptor securityInterceptor) {
        this.securityInterceptor = securityInterceptor;
    }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(securityInterceptor)
            .addPathPatterns("/api/v1/**");
    }
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }
}
```

## File: src/main/java/com/portfolioos/core/security/SecurityInterceptor.java
```java
package com.portfolioos.core.security;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
@Component
public class SecurityInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = System.getenv("API_AUTH_TOKEN");
        if (token == null || token.trim().isEmpty()) {
            return true;
        }
        String clientHeader = request.getHeader("X-Api-Auth-Token");
        if (clientHeader == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                clientHeader = authHeader.substring(7);
            }
        }
        if (clientHeader == null) {
            clientHeader = request.getParameter("token");
        }
        if (!token.equals(clientHeader)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Unauthorized: Missing or invalid X-Api-Auth-Token header or token parameter.\"}");
            return false;
        }
        return true;
    }
}
```

## File: src/main/java/com/portfolioos/core/service/PortfolioValuationService.java
```java
package com.portfolioos.core.service;
import com.portfolioos.core.dtos.ReportDtos.*;
import com.portfolioos.core.fire.FireTracker;
import com.portfolioos.core.goals.GoalTracker;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.nav.AmfiNavSync;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.util.Pair;
import com.portfolioos.core.valuation.BucketEngine;
import com.portfolioos.core.valuation.ConsolidationRebalanceEngine;
import com.portfolioos.core.valuation.RebalanceEngine;
import com.portfolioos.core.xirr.CashFlow;
import com.portfolioos.core.xirr.XirrEngine;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
@Service
public class PortfolioValuationService {
    private final EventStorePort eventStore;
    private final AmfiNavSync amfiSync = new AmfiNavSync();
    private final FifoMatcher fifoMatcher = new FifoMatcher();
    private final XirrEngine xirrEngine = new XirrEngine();
    public PortfolioValuationService(EventStorePort eventStore) {
        this.eventStore = eventStore;
    }
    private String fmt(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    public PortfolioSummaryResponse getPortfolioSummary(String fy) {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        FifoMatcher.FifoResult matchResult = fifoMatcher.processEvents(allEvents);
        List<Lot> openLots = matchResult.openLots();
        Map<String, BigDecimal> navMap = amfiSync.getNavMap();
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;
        for (Lot lot : openLots) {
            totalInvested = totalInvested.add(lot.totalCostBasis());
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
            totalCurrentValue = totalCurrentValue.add(lot.remainingUnits().multiply(nav));
        }
        BigDecimal totalGain = totalCurrentValue.subtract(totalInvested);
        List<CashFlow> cashflows = new ArrayList<>();
        for (TaxEvent event : allEvents) {
            if (event.eventType() == EventType.ACQUISITION || event.eventType() == EventType.SIP_INSTALMENT) {
                cashflows.add(new CashFlow(event.eventDate(), event.grossAmount().negate()));
            } else if (event.eventType() == EventType.DISPOSAL || event.eventType() == EventType.SGB_MATURITY) {
                cashflows.add(new CashFlow(event.eventDate(), event.grossAmount()));
            }
        }
        cashflows.add(new CashFlow(LocalDate.now(), totalCurrentValue));
        double xirr = xirrEngine.calculateXirr(cashflows);
        long distinctAssetCount = openLots.stream().map(Lot::assetId).distinct().count();
        return new PortfolioSummaryResponse(
            fmt(totalInvested),
            fmt(totalCurrentValue),
            fmt(totalGain),
            String.format("%.2f%%", xirr),
            (int) distinctAssetCount,
            0
        );
    }
    public List<HoldingDetailDto> getHoldings() {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        FifoMatcher.FifoResult matchResult = fifoMatcher.processEvents(allEvents);
        List<Lot> openLots = matchResult.openLots();
        Map<String, BigDecimal> navMap = amfiSync.getNavMap();
        LocalDate today = LocalDate.now();
        BigDecimal totalCurrentValAll = BigDecimal.ZERO;
        Map<String, List<Lot>> grouped = openLots.stream().collect(Collectors.groupingBy(Lot::assetId));
        List<HoldingDetailDto> holdingDetails = new ArrayList<>();
        for (Map.Entry<String, List<Lot>> entry : grouped.entrySet()) {
            String assetId = entry.getKey();
            List<Lot> lots = entry.getValue();
            String assetName = lots.get(0).assetName();
            BigDecimal currentNav = navMap.getOrDefault(assetId, lots.get(0).costPerUnit());
            boolean isStale = !navMap.containsKey(assetId);
            String category = TaxClassifier.detectCategory(assetId, assetName).name();
            BigDecimal assetInvested = BigDecimal.ZERO;
            BigDecimal assetCurrentVal = BigDecimal.ZERO;
            List<OpenLotDto> lotDtos = new ArrayList<>();
            for (Lot lot : lots) {
                BigDecimal lotCurrentVal = lot.remainingUnits().multiply(currentNav);
                BigDecimal lotGain = lotCurrentVal.subtract(lot.totalCostBasis());
                assetInvested = assetInvested.add(lot.totalCostBasis());
                assetCurrentVal = assetCurrentVal.add(lotCurrentVal);
                long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
                long thresholdDays = category.equals("EQUITY") ? 365L : 730L;
                boolean isLtcg = holdingDays >= thresholdDays;
                long daysToLtcg = isLtcg ? 0L : (thresholdDays - holdingDays);
                lotDtos.add(new OpenLotDto(
                    lot.lotId(),
                    lot.acquisitionDate().toString(),
                    lot.remainingUnits().setScale(4, RoundingMode.HALF_UP).toPlainString(),
                    lot.costPerUnit().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    lot.totalCostBasis().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    currentNav.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    lotCurrentVal.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    lotGain.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    holdingDays,
                    daysToLtcg,
                    isLtcg
                ));
            }
            BigDecimal assetGain = assetCurrentVal.subtract(assetInvested);
            BigDecimal gainPct = BigDecimal.ZERO;
            if (assetInvested.compareTo(BigDecimal.ZERO) > 0) {
                gainPct = assetGain.divide(assetInvested, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            }
            totalCurrentValAll = totalCurrentValAll.add(assetCurrentVal);
            holdingDetails.add(new HoldingDetailDto(
                assetId,
                assetName,
                category,
                fmt(assetInvested),
                fmt(assetCurrentVal),
                fmt(assetGain),
                fmt(gainPct),
                "0.00",
                isStale,
                lotDtos
            ));
        }
        final BigDecimal finalTotalVal = totalCurrentValAll;
        return holdingDetails.stream().map(h -> {
            BigDecimal currVal = new BigDecimal(h.currentValue());
            BigDecimal allocPct = BigDecimal.ZERO;
            if (finalTotalVal.compareTo(BigDecimal.ZERO) > 0) {
                allocPct = currVal.divide(finalTotalVal, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            }
            return new HoldingDetailDto(
                h.assetId(),
                h.assetName(),
                h.category(),
                h.investedValue(),
                h.currentValue(),
                h.unrealizedGain(),
                h.unrealizedGainPct(),
                fmt(allocPct),
                h.navStale(),
                h.lots()
            );
        }).toList();
    }
    public List<AssetAllocationEntry> getAssetAllocation() {
        List<HoldingDetailDto> holdings = getHoldings();
        return holdings.stream().map(h -> new AssetAllocationEntry(
            h.assetId(),
            h.assetName(),
            h.investedValue(),
            h.currentValue(),
            h.allocationPct(),
            h.navStale()
        )).toList();
    }
    public List<CategoryAllocationEntry> getCategoryAllocation() {
        List<HoldingDetailDto> holdings = getHoldings();
        BigDecimal totalValue = BigDecimal.ZERO;
        for (HoldingDetailDto h : holdings) {
            totalValue = totalValue.add(new BigDecimal(h.currentValue()));
        }
        Map<String, List<HoldingDetailDto>> grouped = holdings.stream().collect(Collectors.groupingBy(HoldingDetailDto::category));
        List<CategoryAllocationEntry> categories = new ArrayList<>();
        for (Map.Entry<String, List<HoldingDetailDto>> entry : grouped.entrySet()) {
            String cat = entry.getKey();
            BigDecimal inv = BigDecimal.ZERO;
            BigDecimal curr = BigDecimal.ZERO;
            for (HoldingDetailDto h : entry.getValue()) {
                inv = inv.add(new BigDecimal(h.investedValue()));
                curr = curr.add(new BigDecimal(h.currentValue()));
            }
            BigDecimal pct = BigDecimal.ZERO;
            if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
                pct = curr.divide(totalValue, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            }
            categories.add(new CategoryAllocationEntry(
                cat, cat, fmt(inv), fmt(curr), fmt(pct)
            ));
        }
        return categories;
    }
    public RebalancePreviewDto getRebalancePreview(BigDecimal targetAmount, String fy) {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        FifoMatcher.FifoResult matchResult = fifoMatcher.processEvents(allEvents);
        List<Lot> openLots = matchResult.openLots();
        List<MatchedLot> matchedLots = matchResult.matchedLots();
        Map<String, BigDecimal> navMap = amfiSync.getNavMap();
        ExemptionTracker.ExemptionStatus status = ExemptionTracker.calculateExemptionStatus(matchedLots, fy);
        BigDecimal remExemption = new BigDecimal(status.exemptionRemaining());
        RebalanceEngine.RebalancePreviewResult result = RebalanceEngine.calculateRebalancePreview(
            openLots, navMap, targetAmount, remExemption, fy
        );
        List<RebalanceLotDto> selectedDtos = result.selectedLots().stream().map(s -> new RebalanceLotDto(
            s.assetName(),
            fmt(s.unitsToSell()),
            fmt(s.redemptionProceeds()),
            fmt(s.estimatedGain()),
            s.taxTerm(),
            fmt(s.estimatedTaxDrag())
        )).toList();
        return new RebalancePreviewDto(
            fmt(result.targetRedemptionAmount()),
            fmt(result.actualRedemptionAmount()),
            fmt(result.totalEstimatedGain()),
            fmt(result.totalTaxDrag()),
            String.format("%.2f%%", result.effectiveTaxRatePct()),
            fmt(result.ltcgExemptionHarvested()),
            selectedDtos
        );
    }
    public GoalSummaryResponse getGoalSummary() {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        List<Lot> openLots = fifoMatcher.processEvents(allEvents).openLots();
        Map<String, BigDecimal> navMap = amfiSync.getNavMap();
        GoalTracker.GoalSummary summary = GoalTracker.calculateGoalSummary(openLots, navMap);
        Map<String, String> allocationsByGoalStr = new HashMap<>();
        for (Map.Entry<GoalTracker.GoalTag, BigDecimal> entry : summary.allocationsByGoal().entrySet()) {
            allocationsByGoalStr.put(entry.getKey().name(), fmt(entry.getValue()));
        }
        List<GoalAllocationDto> allocDtos = summary.goalAllocations().stream().map(a -> new GoalAllocationDto(
            a.holdingId(),
            a.holdingName(),
            a.goalTag().name(),
            fmt(a.allocatedAmount())
        )).toList();
        return new GoalSummaryResponse(
            fmt(summary.totalLiquidHoldings()),
            fmt(summary.allocatedGoalsAmount()),
            fmt(summary.unallocatedCash()),
            allocationsByGoalStr,
            allocDtos
        );
    }
    public FireSummaryResponse getFireSummary() {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        List<Lot> openLots = fifoMatcher.processEvents(allEvents).openLots();
        Map<String, BigDecimal> navMap = amfiSync.getNavMap();
        FireTracker.FireSummary fire = FireTracker.calculateFireSummary(openLots, navMap, LocalDate.now());
        List<FireScenarioDto> scenarioDtos = fire.scenarios().stream().map(s -> new FireScenarioDto(
            s.id(),
            s.label(),
            fmt(s.monthlyExpenseToday()),
            s.active()
        )).toList();
        return new FireSummaryResponse(
            fire.activeScenarioLabel(),
            fmt(fire.monthlyExpenseToday()),
            fmt(fire.annualExpense()),
            fmt(fire.requiredCorpus()),
            fmt(fire.totalNetWorth()),
            fmt(fire.epfBalance()),
            fmt(fire.nonRetirementGoalAllocations()),
            fmt(fire.fireInvestableNetWorth()),
            fmt(fire.projectedCorpusAtTargetAge()),
            fire.yearsRemaining(),
            fire.status(),
            fmt(fire.shortageOrSurplusAmount()),
            fire.reviewDatePassed(),
            scenarioDtos
        );
    }
    public BucketRebalanceResponse getBucketRebalance(BigDecimal benchmarkCurrent, BigDecimal benchmarkRollingHigh, String fy) {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        List<Lot> openLots = fifoMatcher.processEvents(allEvents).openLots();
        Map<String, BigDecimal> navMap = amfiSync.getNavMap();
        BucketEngine.RebalanceEngineResult result = BucketEngine.evaluateRebalance(
            openLots, navMap, LocalDate.now(), benchmarkCurrent, benchmarkRollingHigh, BucketEngine.DEFAULT_TARGETS, fy
        );
        List<BucketStatusDto> statuses = result.bucketStatuses().stream().map(s -> new BucketStatusDto(
            s.bucket().name(),
            fmt(s.currentValue()),
            fmt(s.currentPct()),
            fmt(s.targetPct()),
            fmt(s.driftPct()),
            s.isDrifted()
        )).toList();
        List<RebalanceRecommendationDto> recommendations = result.recommendations().stream().map(r -> new RebalanceRecommendationDto(
            r.assetId(),
            r.assetName(),
            r.bucket().name(),
            r.action(),
            fmt(r.amount()),
            r.triggerType(),
            fmt(r.estimatedTaxDrag()),
            r.taxTermSummary()
        )).toList();
        BucketEngine.DrawdownStatus ds = result.drawdownStatus();
        DrawdownStatusDto dsDto = new DrawdownStatusDto(
            ds.benchmarkName(),
            fmt(ds.currentLevel()),
            fmt(ds.rollingHigh()),
            fmt(ds.drawdownPct()),
            ds.activeRungsFired(),
            fmt(ds.recommendedBufferDeployPct())
        );
        return new BucketRebalanceResponse(
            statuses, recommendations, dsDto, result.calendarTriggerFired(), result.drawdownTriggerFired()
        );
    }
    public ConsolidationPreviewResponse getConsolidationPreview(String fy) {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        List<Lot> openLots = fifoMatcher.processEvents(allEvents).openLots();
        List<MatchedLot> matchedLots = fifoMatcher.processEvents(allEvents).matchedLots();
        Map<String, BigDecimal> navMap = amfiSync.getNavMap();
        ExemptionTracker.ExemptionStatus status = ExemptionTracker.calculateExemptionStatus(matchedLots, fy);
        BigDecimal remExemption = new BigDecimal(status.exemptionRemaining());
        ConsolidationRebalanceEngine.ConsolidationPreviewResult result = ConsolidationRebalanceEngine.calculateConsolidation(
            openLots, navMap, LocalDate.now(), remExemption, fy
        );
        List<PhasedOutAssetSummaryDto> phaseOutDtos = result.phasedOutAssets().stream().map(p -> new PhasedOutAssetSummaryDto(
            p.assetId(),
            p.assetName(),
            p.currentUnits().setScale(3, RoundingMode.HALF_UP).toPlainString(),
            fmt(p.currentValue()),
            fmt(p.totalCostBasis()),
            fmt(p.unrealizedGain()),
            p.isLtcg(),
            fmt(p.estimatedTaxDrag())
        )).toList();
        List<ExistingSipAllocationDto> allocations = result.proRataAllocations().stream().map(a -> new ExistingSipAllocationDto(
            a.assetId(),
            a.assetName(),
            fmt(a.sipWeightPct()),
            fmt(a.deploymentAmount())
        )).toList();
        return new ConsolidationPreviewResponse(
            phaseOutDtos,
            fmt(result.totalProceeds()),
            fmt(result.totalEstimatedGain()),
            fmt(result.totalTaxDrag()),
            fmt(result.ltcgExemptionHarvested()),
            allocations,
            result.isRebalanceWindowOpen(),
            result.nextScheduledWindow()
        );
    }
}
```

## File: src/main/java/com/portfolioos/core/service/TaxOptimizationService.java
```java
package com.portfolioos.core.service;
import com.portfolioos.core.dtos.ReportDtos.*;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.nav.AmfiNavSync;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.reporting.Itr2CsvExporter;
import com.portfolioos.core.reporting.TaxReportExporter;
import com.portfolioos.core.valuation.HarvestAdvisor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Service
public class TaxOptimizationService {
    private final EventStorePort eventStore;
    private final AmfiNavSync amfiSync = new AmfiNavSync();
    private final FifoMatcher fifoMatcher = new FifoMatcher();
    public TaxOptimizationService(EventStorePort eventStore) {
        this.eventStore = eventStore;
    }
    private String fmt(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    public ExemptionTracker.ExemptionStatus getExemptionStatus(String fy) {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        List<MatchedLot> matchedLots = fifoMatcher.processEvents(allEvents).matchedLots();
        return ExemptionTracker.calculateExemptionStatus(matchedLots, fy);
    }
    public TaxReportExporter.Itr2ScheduleCgReport generateItr2Report(String fy) {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        List<MatchedLot> matchedLots = fifoMatcher.processEvents(allEvents).matchedLots();
        return TaxReportExporter.generateItr2Report(matchedLots, fy);
    }
    public List<HarvestOpportunityDto> getHarvestOpportunities() {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        List<Lot> openLots = fifoMatcher.processEvents(allEvents).openLots();
        Map<String, BigDecimal> navMap = amfiSync.getNavMap();
        // Assume zero exemption used so far for simple harvest opportunity advice
        HarvestAdvisor.TaxHarvestResult plan = HarvestAdvisor.generateHarvestPlan(
            openLots, navMap, BigDecimal.ZERO, "2026-27"
        );
        return plan.recommendations().stream().map(opp -> new HarvestOpportunityDto(
            opp.assetId(),
            opp.assetName(),
            opp.lotId(),
            opp.unitsToHarvest().setScale(4, RoundingMode.HALF_UP).toPlainString(),
            fmt(opp.unrealizedLtcgGain())
        )).toList();
    }
    public List<MaturationLadderDto> getMaturationLadder() {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        List<Lot> openLots = fifoMatcher.processEvents(allEvents).openLots();
        Map<String, BigDecimal> navMap = amfiSync.getNavMap();
        LocalDate today = LocalDate.now();
        List<MaturationLadderDto> ladder = new ArrayList<>();
        for (Lot lot : openLots) {
            AssetCategory cat = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
            boolean isListed = TaxClassifier.isListed(lot.assetId(), lot.assetName());
            long reqDays = (cat == AssetCategory.EQUITY || isListed) ? 365L : 730L;
            long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
            if (holdingDays < reqDays) {
                long daysRemaining = reqDays - holdingDays;
                LocalDate targetDate = today.plusDays(daysRemaining);
                BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
                BigDecimal currentVal = lot.remainingUnits().multiply(nav);
                BigDecimal gain = currentVal.subtract(lot.totalCostBasis());
                ladder.add(new MaturationLadderDto(
                    lot.assetId(),
                    lot.assetName(),
                    lot.lotId(),
                    lot.acquisitionDate().toString(),
                    lot.remainingUnits().setScale(4, RoundingMode.HALF_UP).toPlainString(),
                    fmt(lot.totalCostBasis()),
                    fmt(currentVal),
                    fmt(gain),
                    holdingDays,
                    daysRemaining,
                    targetDate.toString()
                ));
            }
        }
        ladder.sort((a, b) -> Long.compare(a.daysRemainingToLtcg(), b.daysRemainingToLtcg()));
        return ladder;
    }
    public List<RealizedLogDto> getRealizedLog(String fy) {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        List<MatchedLot> matchedLots = fifoMatcher.processEvents(allEvents).matchedLots();
        ExemptionTracker.Pair<LocalDate, LocalDate> bounds = ExemptionTracker.getFiscalYearBounds(fy);
        LocalDate startDate = bounds.first();
        LocalDate endDate = bounds.second();
        List<MatchedLot> fyLots = matchedLots.stream().filter(lot -> 
            !lot.disposalDate().isBefore(startDate) && !lot.disposalDate().isAfter(endDate)
        ).toList();
        Map<String, String> assetNameMap = allEvents.stream()
            .collect(Collectors.toMap(TaxEvent::assetId, TaxEvent::assetName, (a, b) -> a));
        return fyLots.stream().map(m -> new RealizedLogDto(
            m.matchId(),
            m.disposalDate().toString(),
            m.acquisitionDate().toString(),
            m.assetId(),
            assetNameMap.getOrDefault(m.assetId(), m.assetId()),
            m.unitsMatched().setScale(3, RoundingMode.HALF_UP).toPlainString(),
            fmt(m.saleProceeds()),
            fmt(m.costBasis()),
            fmt(m.realizedGain()),
            m.taxTerm().name(),
            m.holdingPeriodDays()
        )).toList();
    }
    public Map<String, String> downloadItr2Files(String fy) {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        List<MatchedLot> matchedLots = fifoMatcher.processEvents(allEvents).matchedLots();
        Map<String, String> assetNameMap = allEvents.stream()
            .collect(Collectors.toMap(TaxEvent::assetId, TaxEvent::assetName, (a, b) -> a));
        return Itr2CsvExporter.exportItr2ScheduleCg(matchedLots, fy, assetNameMap);
    }
}
```

## File: src/main/java/com/portfolioos/core/util/Pair.java
```java
package com.portfolioos.core.util;
public record Pair<A, B>(A first, B second) {}
```

## File: src/main/java/com/portfolioos/core/valuation/AntigravityEngine.java
```java
package com.portfolioos.core.valuation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class AntigravityEngine {
    public record AssetFactorScore(
        String assetId,
        String assetName,
        BigDecimal beta,
        BigDecimal downsideBeta,
        BigDecimal zScore30d,
        BigDecimal twr30dPct,
        BigDecimal twr90dPct,
        boolean isAntigravity,
        String recommendation
    ) {}
    public record AntigravitySummary(
        String marketBenchmarkName,
        BigDecimal marketDrawdownPct,
        boolean isMarketCorrection,
        List<AssetFactorScore> antigravityAssets,
        List<AssetFactorScore> allAssetScores
    ) {}
    public static BigDecimal calculateBeta(List<Double> assetReturns, List<Double> marketReturns) {
        if (assetReturns.size() < 2 || assetReturns.size() != marketReturns.size()) {
            return BigDecimal.ONE;
        }
        int n = assetReturns.size();
        double meanAsset = assetReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double meanMarket = marketReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double cov = 0.0;
        double varMarket = 0.0;
        for (int i = 0; i < n; i++) {
            double devAsset = assetReturns.get(i) - meanAsset;
            double devMarket = marketReturns.get(i) - meanMarket;
            cov += devAsset * devMarket;
            varMarket += devMarket * devMarket;
        }
        if (varMarket == 0.0) return BigDecimal.ONE;
        return BigDecimal.valueOf(cov / varMarket).setScale(2, RoundingMode.HALF_UP);
    }
    public static BigDecimal calculateDownsideBeta(List<Double> assetReturns, List<Double> marketReturns) {
        if (assetReturns.size() != marketReturns.size()) return BigDecimal.ONE;
        List<Double> downAsset = new ArrayList<>();
        List<Double> downMarket = new ArrayList<>();
        for (int i = 0; i < assetReturns.size(); i++) {
            double mRet = marketReturns.get(i);
            if (mRet < 0.0) {
                downAsset.add(assetReturns.get(i));
                downMarket.add(mRet);
            }
        }
        if (downAsset.size() < 2) return calculateBeta(assetReturns, marketReturns);
        double meanAsset = downAsset.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double meanMarket = downMarket.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double cov = 0.0;
        double varMarket = 0.0;
        for (int i = 0; i < downAsset.size(); i++) {
            double devAsset = downAsset.get(i) - meanAsset;
            double devMarket = downMarket.get(i) - meanMarket;
            cov += devAsset * devMarket;
            varMarket += devMarket * devMarket;
        }
        if (varMarket == 0.0) return BigDecimal.ONE;
        return BigDecimal.valueOf(cov / varMarket).setScale(2, RoundingMode.HALF_UP);
    }
    public static AntigravitySummary analyzePortfolioFactors(
        Map<String, List<Double>> assetReturnsMap,
        Map<String, String> assetNamesMap,
        List<Double> marketReturns,
        BigDecimal marketDrawdownPct
    ) {
        boolean isCorrection = marketDrawdownPct.compareTo(new BigDecimal("5.0")) >= 0;
        Map<String, Double> twr30dMap = new HashMap<>();
        List<Double> allTwr30d = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : assetReturnsMap.entrySet()) {
            List<Double> returns = entry.getValue();
            double twr = 0.0;
            if (!returns.isEmpty()) {
                int start = Math.max(0, returns.size() - 30);
                double compound = 1.0;
                for (int i = start; i < returns.size(); i++) {
                    compound *= (1.0 + returns.get(i));
                }
                twr = compound - 1.0;
            }
            twr30dMap.put(entry.getKey(), twr);
            allTwr30d.add(twr);
        }
        double meanTwr30d = allTwr30d.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double varianceSum = 0.0;
        for (double val : allTwr30d) {
            varianceSum += (val - meanTwr30d) * (val - meanTwr30d);
        }
        double stdDevTwr30d = (allTwr30d.size() > 1) ? Math.sqrt(varianceSum / (allTwr30d.size() - 1)) : 1.0;
        List<AssetFactorScore> scores = new ArrayList<>();
        List<AssetFactorScore> antigravityList = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : assetReturnsMap.entrySet()) {
            String assetId = entry.getKey();
            List<Double> returns = entry.getValue();
            BigDecimal beta = calculateBeta(returns, marketReturns);
            BigDecimal downsideBeta = calculateDownsideBeta(returns, marketReturns);
            double twr30 = twr30dMap.getOrDefault(assetId, 0.0);
            double zScore = (stdDevTwr30d > 0.0001) ? (twr30 - meanTwr30d) / stdDevTwr30d : 0.0;
            double twr90 = 0.0;
            if (!returns.isEmpty()) {
                int start = Math.max(0, returns.size() - 90);
                double compound = 1.0;
                for (int i = start; i < returns.size(); i++) {
                    compound *= (1.0 + returns.get(i));
                }
                twr90 = compound - 1.0;
            }
            BigDecimal twr30dBd = BigDecimal.valueOf(twr30 * 100.0).setScale(2, RoundingMode.HALF_UP);
            BigDecimal twr90dBd = BigDecimal.valueOf(twr90 * 100.0).setScale(2, RoundingMode.HALF_UP);
            BigDecimal zScoreBd = BigDecimal.valueOf(zScore).setScale(2, RoundingMode.HALF_UP);
            boolean isAntigravity = downsideBeta.compareTo(new BigDecimal("0.75")) < 0 
                                    && zScoreBd.compareTo(new BigDecimal("0.50")) > 0 
                                    && isCorrection;
            String recommendation;
            if (isAntigravity) {
                recommendation = "🚀 QUANT ANTIGRAVITY — Downside beta " + downsideBeta + " & Z-score +" + zScoreBd + ". Deploy dry powder here.";
                antigravityList.add(new AssetFactorScore(assetId, assetNamesMap.getOrDefault(assetId, assetId), beta, downsideBeta, zScoreBd, twr30dBd, twr90dBd, true, recommendation));
            } else if (downsideBeta.compareTo(new BigDecimal("0.75")) < 0) {
                recommendation = "Downside Cushion — Beta-minus " + downsideBeta + ".";
            } else if (zScoreBd.compareTo(new BigDecimal("0.50")) > 0) {
                recommendation = "Momentum Outperformer — Z-score +" + zScoreBd + ".";
            } else {
                recommendation = "Standard Market Beta.";
            }
            scores.add(new AssetFactorScore(
                assetId,
                assetNamesMap.getOrDefault(assetId, assetId),
                beta,
                downsideBeta,
                zScoreBd,
                twr30dBd,
                twr90dBd,
                isAntigravity,
                recommendation
            ));
        }
        return new AntigravitySummary(
            "Nifty 500 Index",
            marketDrawdownPct,
            isCorrection,
            antigravityList,
            scores
        );
    }
}
```

## File: src/main/java/com/portfolioos/core/valuation/BucketEngine.java
```java
package com.portfolioos.core.valuation;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
public class BucketEngine {
    public enum Bucket {
        EQUITY_CORE,
        EQUITY_SATELLITE,
        GOLD_SILVER,
        LIQUID_BUFFER
    }
    public record BucketTarget(
        Bucket bucket,
        BigDecimal targetPct,
        BigDecimal bandPct
    ) {}
    public record BucketStatus(
        Bucket bucket,
        BigDecimal currentValue,
        BigDecimal currentPct,
        BigDecimal targetPct,
        BigDecimal driftPct,
        boolean isDrifted
    ) {}
    public record RebalanceRecommendation(
        String assetId,
        String assetName,
        Bucket bucket,
        String action, // "BUY" or "SELL"
        BigDecimal amount,
        String triggerType,
        BigDecimal estimatedTaxDrag,
        String taxTermSummary
    ) {}
    public record DrawdownStatus(
        String benchmarkName,
        BigDecimal currentLevel,
        BigDecimal rollingHigh,
        BigDecimal drawdownPct,
        List<Integer> activeRungsFired,
        BigDecimal recommendedBufferDeployPct
    ) {}
    public record RebalanceEngineResult(
        List<BucketStatus> bucketStatuses,
        List<RebalanceRecommendation> recommendations,
        DrawdownStatus drawdownStatus,
        boolean calendarTriggerFired,
        boolean drawdownTriggerFired
    ) {}
    public static final List<BucketTarget> DEFAULT_TARGETS = List.of(
        new BucketTarget(Bucket.EQUITY_CORE, new BigDecimal("50.0"), new BigDecimal("5.0")),
        new BucketTarget(Bucket.EQUITY_SATELLITE, new BigDecimal("20.0"), new BigDecimal("5.0")),
        new BucketTarget(Bucket.GOLD_SILVER, new BigDecimal("15.0"), new BigDecimal("5.0")),
        new BucketTarget(Bucket.LIQUID_BUFFER, new BigDecimal("15.0"), new BigDecimal("5.0"))
    );
    public static Bucket classifyAssetToBucket(String assetId, String assetName) {
        String nameUpper = assetName.toUpperCase();
        AssetCategory category = TaxClassifier.detectCategory(assetId, assetName);
        if (category == AssetCategory.GOLD_SILVER || category == AssetCategory.SGB) {
            return Bucket.GOLD_SILVER;
        }
        if (nameUpper.contains("ARBITRAGE") || nameUpper.contains("LIQUID") ||
            nameUpper.contains("OVERNIGHT") || nameUpper.contains("TREASURY") ||
            category == AssetCategory.DEBT_SPECIFIED_50AA
        ) {
            return Bucket.LIQUID_BUFFER;
        }
        if (nameUpper.contains("SMALL") || nameUpper.contains("MICRO") || nameUpper.contains("SMALLCAP")) {
            return Bucket.EQUITY_SATELLITE;
        }
        return Bucket.EQUITY_CORE;
    }
    public static RebalanceEngineResult evaluateRebalance(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal benchmarkCurrent,
        BigDecimal benchmarkRollingHigh,
        List<BucketTarget> targets,
        String fiscalYear
    ) {
        BigDecimal totalPortfolioValue = BigDecimal.ZERO;
        Map<Bucket, BigDecimal> bucketValues = new HashMap<>();
        Map<Bucket, Map<String, List<Lot>>> bucketAssetLots = new HashMap<>();
        for (Bucket b : Bucket.values()) {
            bucketValues.put(b, BigDecimal.ZERO);
            bucketAssetLots.put(b, new HashMap<>());
        }
        for (Lot lot : openLots) {
            Bucket bucket = classifyAssetToBucket(lot.assetId(), lot.assetName());
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
            BigDecimal lotValue = lot.remainingUnits().multiply(nav);
            totalPortfolioValue = totalPortfolioValue.add(lotValue);
            bucketValues.put(bucket, bucketValues.get(bucket).add(lotValue));
            Map<String, List<Lot>> assetMap = bucketAssetLots.get(bucket);
            assetMap.computeIfAbsent(lot.assetId(), k -> new ArrayList<>()).add(lot);
        }
        Map<Bucket, BucketTarget> targetMap = new HashMap<>();
        for (BucketTarget t : targets) {
            targetMap.put(t.bucket(), t);
        }
        List<BucketStatus> bucketStatuses = new ArrayList<>();
        boolean calendarTriggerFired = false;
        int month = currentDate.getMonthValue();
        int day = currentDate.getDayOfMonth();
        boolean isCalendarReviewDate = (month == 3 && day >= 10 && day <= 20) || (month == 9 && day >= 10 && day <= 20);
        for (Bucket bucket : Bucket.values()) {
            BigDecimal curVal = bucketValues.get(bucket);
            BigDecimal curPct = BigDecimal.ZERO;
            if (totalPortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
                curPct = curVal.multiply(new BigDecimal("100")).divide(totalPortfolioValue, 2, RoundingMode.HALF_UP);
            }
            BucketTarget tgt = targetMap.getOrDefault(bucket, new BucketTarget(bucket, new BigDecimal("25.0"), new BigDecimal("5.0")));
            BigDecimal drift = curPct.subtract(tgt.targetPct());
            boolean isDrifted = drift.abs().compareTo(tgt.bandPct()) > 0;
            if (isCalendarReviewDate && isDrifted) {
                calendarTriggerFired = true;
            }
            bucketStatuses.add(new BucketStatus(
                bucket, curVal, curPct, tgt.targetPct(), drift, isDrifted
            ));
        }
        // Drawdown trigger
        BigDecimal drawdownPct = BigDecimal.ZERO;
        if (benchmarkRollingHigh.compareTo(BigDecimal.ZERO) > 0) {
            drawdownPct = benchmarkRollingHigh.subtract(benchmarkCurrent)
                .multiply(new BigDecimal("100"))
                .divide(benchmarkRollingHigh, 2, RoundingMode.HALF_UP);
        }
        List<Integer> activeRungs = new ArrayList<>();
        BigDecimal deployPct = BigDecimal.ZERO;
        if (drawdownPct.compareTo(new BigDecimal("20.0")) >= 0) {
            activeRungs.addAll(List.of(10, 15, 20));
            deployPct = new BigDecimal("100.0");
        } else if (drawdownPct.compareTo(new BigDecimal("15.0")) >= 0) {
            activeRungs.addAll(List.of(10, 15));
            deployPct = new BigDecimal("50.0");
        } else if (drawdownPct.compareTo(new BigDecimal("10.0")) >= 0) {
            activeRungs.add(10);
            deployPct = new BigDecimal("25.0");
        }
        boolean drawdownTriggerFired = !activeRungs.isEmpty();
        DrawdownStatus drawdownStatus = new DrawdownStatus(
            "Nifty 500", benchmarkCurrent, benchmarkRollingHigh, drawdownPct, activeRungs, deployPct
        );
        List<RebalanceRecommendation> recommendations = new ArrayList<>();
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
        if (drawdownTriggerFired) {
            BigDecimal liquidVal = bucketValues.get(Bucket.LIQUID_BUFFER);
            BigDecimal deployAmount = liquidVal.multiply(deployPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            if (deployAmount.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, List<Lot>> coreAssets = bucketAssetLots.get(Bucket.EQUITY_CORE);
                String targetAsset = !coreAssets.isEmpty() ? coreAssets.keySet().iterator().next() : "EQUITY_CORE_INDEX";
                String assetName = !coreAssets.isEmpty() ? coreAssets.get(targetAsset).get(0).assetName() : "LargeMidcap 250 Index Fund";
                recommendations.add(new RebalanceRecommendation(
                    targetAsset,
                    assetName,
                    Bucket.EQUITY_CORE,
                    "BUY",
                    deployAmount,
                    "MARKET_DRAWDOWN",
                    BigDecimal.ZERO,
                    "Deploy buffer during " + drawdownPct + "% market drawdown (Rungs: 10%, 15%, 20%)"
                ));
            }
        }
        for (BucketStatus status : bucketStatuses) {
            if (status.isDrifted()) {
                BigDecimal targetValue = totalPortfolioValue.multiply(status.targetPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                BigDecimal diffValue = status.currentValue().subtract(targetValue);
                if (diffValue.compareTo(BigDecimal.ZERO) > 0) {
                    Map<String, List<Lot>> bucketLots = bucketAssetLots.get(status.bucket());
                    if (bucketLots.isEmpty()) continue;
                    String firstAssetId = bucketLots.keySet().iterator().next();
                    List<Lot> firstLots = bucketLots.get(firstAssetId);
                    String assetName = firstLots.get(0).assetName();
                    BigDecimal estTaxDrag = BigDecimal.ZERO;
                    List<String> taxTerms = new ArrayList<>();
                    BigDecimal nav = navMap.getOrDefault(firstAssetId, firstLots.get(0).costPerUnit());
                    for (Lot lot : firstLots) {
                        AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
                        // Assume 365 holding threshold days to classify tax term for simple tax drag estimation
                        boolean isLtcg = TaxClassifier.classifyTaxTerm(category, 365, fiscalYear, true) == TaxTerm.LONG_TERM;
                        BigDecimal gain = nav.subtract(lot.costPerUnit()).multiply(lot.remainingUnits()).max(BigDecimal.ZERO);
                        if (gain.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal rate = isLtcg ? rules.equityLtcgRate() : rules.equityStcgRate();
                            estTaxDrag = estTaxDrag.add(gain.multiply(rate));
                            taxTerms.add(isLtcg ? "LTCG @ " + rules.equityLtcgRate().multiply(new BigDecimal("100")) + "%" 
                                                 : "STCG @ " + rules.equityStcgRate().multiply(new BigDecimal("100")) + "%");
                        }
                    }
                    recommendations.add(new RebalanceRecommendation(
                        firstAssetId,
                        assetName,
                        status.bucket(),
                        "SELL",
                        diffValue.abs(),
                        isCalendarReviewDate ? "CALENDAR" : "DRIFT_ALERT",
                        estTaxDrag.setScale(2, RoundingMode.HALF_UP),
                        taxTerms.stream().distinct().collect(Collectors.joining(", "))
                    ));
                } else if (diffValue.compareTo(BigDecimal.ZERO) < 0) {
                    Map<String, List<Lot>> bucketLots = bucketAssetLots.get(status.bucket());
                    String firstAssetId = !bucketLots.isEmpty() ? bucketLots.keySet().iterator().next() : "BUY_" + status.bucket().name();
                    String assetName = (!bucketLots.isEmpty() && bucketLots.containsKey(firstAssetId)) 
                        ? bucketLots.get(firstAssetId).get(0).assetName() : "Core Holding for " + status.bucket().name();
                    recommendations.add(new RebalanceRecommendation(
                        firstAssetId,
                        assetName,
                        status.bucket(),
                        "BUY",
                        diffValue.abs(),
                        isCalendarReviewDate ? "CALENDAR" : "DRIFT_ALERT",
                        BigDecimal.ZERO,
                        "No tax on purchases"
                    ));
                }
            }
        }
        return new RebalanceEngineResult(
            bucketStatuses, recommendations, drawdownStatus, calendarTriggerFired, drawdownTriggerFired
        );
    }
}
```

## File: src/main/java/com/portfolioos/core/valuation/ConsolidationRebalanceEngine.java
```java
package com.portfolioos.core.valuation;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class ConsolidationRebalanceEngine {
    public record ExistingSipAllocation(
        String assetId,
        String assetName,
        BigDecimal sipWeightPct,
        BigDecimal deploymentAmount
    ) {}
    public record PhasedOutAssetSummary(
        String assetId,
        String assetName,
        BigDecimal currentUnits,
        BigDecimal currentValue,
        BigDecimal totalCostBasis,
        BigDecimal unrealizedGain,
        boolean isLtcg,
        BigDecimal estimatedTaxDrag
    ) {}
    public record ConsolidationPreviewResult(
        List<PhasedOutAssetSummary> phasedOutAssets,
        BigDecimal totalProceeds,
        BigDecimal totalEstimatedGain,
        BigDecimal totalTaxDrag,
        BigDecimal ltcgExemptionHarvested,
        List<ExistingSipAllocation> proRataAllocations,
        boolean isRebalanceWindowOpen,
        String nextScheduledWindow
    ) {}
    private static final Map<String, Pair<String, BigDecimal>> CORE_SIP_WEIGHTS = new HashMap<>();
    static {
        CORE_SIP_WEIGHTS.put("NIFTY_LARGEMIDCAP_250", new Pair<>("Nifty LargeMidcap 250 Index Fund", new BigDecimal("33.0")));
        CORE_SIP_WEIGHTS.put("PARAG_PARIKH_FLEXI", new Pair<>("Parag Parikh Flexi Cap Fund", new BigDecimal("24.0")));
        CORE_SIP_WEIGHTS.put("ARBITRAGE_LIQUID", new Pair<>("Kotak Equity Arbitrage / Liquid Buffer", new BigDecimal("16.0")));
        CORE_SIP_WEIGHTS.put("NIFTY_VALUE_30", new Pair<>("Nifty200 Value 30 Index Fund", new BigDecimal("11.0")));
        CORE_SIP_WEIGHTS.put("NIFTY_MOMENTUM_50", new Pair<>("Nifty200 Momentum Quality 50 Index Fund", new BigDecimal("9.0")));
        CORE_SIP_WEIGHTS.put("NIFTY_SMALLCAP_250", new Pair<>("Nifty Smallcap 250 Index Fund", new BigDecimal("7.0")));
    }
    public static ConsolidationPreviewResult calculateConsolidation(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        LocalDate currentDate,
        BigDecimal remainingExemption,
        String fiscalYear
    ) {
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
        List<String> phaseOutKeywords = List.of("EQUAL", "MIDCAP150", "NIFTY100_EW", "MIDCAP_150");
        List<Lot> phaseOutLots = openLots.stream().filter(lot ->
            phaseOutKeywords.stream().anyMatch(kw -> 
                lot.assetId().toUpperCase().contains(kw) || lot.assetName().toUpperCase().contains(kw)
            )
        ).toList();
        BigDecimal totalProceeds = BigDecimal.ZERO;
        BigDecimal totalGain = BigDecimal.ZERO;
        BigDecimal totalTaxDrag = BigDecimal.ZERO;
        BigDecimal unusedExemption = remainingExemption;
        List<PhasedOutAssetSummary> phasedSummaries = new ArrayList<>();
        Map<String, List<Lot>> grouped = new HashMap<>();
        for (Lot lot : phaseOutLots) {
            grouped.computeIfAbsent(lot.assetId(), k -> new ArrayList<>()).add(lot);
        }
        for (Map.Entry<String, List<Lot>> entry : grouped.entrySet()) {
            String assetId = entry.getKey();
            List<Lot> lots = entry.getValue();
            String assetName = lots.get(0).assetName();
            BigDecimal totalUnits = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;
            LocalDate oldestAcq = null;
            for (Lot lot : lots) {
                totalUnits = totalUnits.add(lot.remainingUnits());
                totalCost = totalCost.add(lot.totalCostBasis());
                if (oldestAcq == null || lot.acquisitionDate().isBefore(oldestAcq)) {
                    oldestAcq = lot.acquisitionDate();
                }
            }
            BigDecimal nav = navMap.getOrDefault(assetId, BigDecimal.ZERO);
            if (nav.compareTo(BigDecimal.ZERO) == 0 && totalUnits.compareTo(BigDecimal.ZERO) > 0) {
                nav = totalCost.divide(totalUnits, 4, RoundingMode.HALF_UP);
            }
            BigDecimal curVal = totalUnits.multiply(nav);
            BigDecimal gain = curVal.subtract(totalCost);
            AssetCategory category = TaxClassifier.detectCategory(assetId, assetName);
            long holdingDays = ChronoUnit.DAYS.between(oldestAcq != null ? oldestAcq : currentDate, currentDate);
            long thresholdDays = switch (category) {
                case EQUITY -> rules.equityLtcgThresholdDays();
                case GOLD_SILVER, INTERNATIONAL, SGB -> rules.goldInternationalThresholdDays();
                case DEBT_SPECIFIED_50AA -> -1L;
            };
            boolean isLtcg = thresholdDays > 0 && holdingDays >= thresholdDays;
            BigDecimal taxDrag = BigDecimal.ZERO;
            if (gain.compareTo(BigDecimal.ZERO) > 0) {
                if (isLtcg) {
                    BigDecimal exemptPortion = gain.min(unusedExemption);
                    BigDecimal taxableGain = gain.subtract(exemptPortion);
                    unusedExemption = unusedExemption.subtract(exemptPortion).max(BigDecimal.ZERO);
                    taxDrag = taxableGain.multiply(rules.equityLtcgRate());
                } else {
                    taxDrag = gain.multiply(rules.equityStcgRate());
                }
            }
            totalProceeds = totalProceeds.add(curVal);
            totalGain = totalGain.add(gain);
            totalTaxDrag = totalTaxDrag.add(taxDrag);
            phasedSummaries.add(new PhasedOutAssetSummary(
                assetId, assetName, totalUnits, curVal, totalCost, gain, isLtcg, taxDrag
            ));
        }
        BigDecimal effectiveProceeds = totalProceeds.compareTo(BigDecimal.ZERO) > 0 ? totalProceeds : new BigDecimal("256200.00");
        List<ExistingSipAllocation> proRataAllocations = new ArrayList<>();
        for (Map.Entry<String, Pair<String, BigDecimal>> entry : CORE_SIP_WEIGHTS.entrySet()) {
            String id = entry.getKey();
            Pair<String, BigDecimal> pair = entry.getValue();
            BigDecimal weightPct = pair.second();
            BigDecimal deployAmt = effectiveProceeds.multiply(weightPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            proRataAllocations.add(new ExistingSipAllocation(
                id, pair.first(), weightPct, deployAmt
            ));
        }
        int month = currentDate.getMonthValue();
        boolean isWindowOpen = month == 3 || month == 9;
        String nextScheduled = (month <= 3) ? "March 31, " + currentDate.getYear() 
            : (month <= 9) ? "September 30, " + currentDate.getYear() 
            : "March 31, " + (currentDate.getYear() + 1);
        BigDecimal ltcgHarvested = remainingExemption.subtract(unusedExemption);
        return new ConsolidationPreviewResult(
            phasedSummaries,
            effectiveProceeds,
            totalGain,
            totalTaxDrag,
            ltcgHarvested,
            proRataAllocations,
            isWindowOpen,
            nextScheduled
        );
    }
    private static class Pair<A, B> {
        private final A first;
        private final B second;
        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
        public A first() { return first; }
        public B second() { return second; }
    }
}
```

## File: src/main/java/com/portfolioos/core/valuation/HarvestAdvisor.java
```java
package com.portfolioos.core.valuation;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
public class HarvestAdvisor {
    public record TaxHarvestRecommendation(
        String assetId,
        String assetName,
        String lotId,
        BigDecimal unitsToHarvest,
        BigDecimal redemptionProceeds,
        BigDecimal unrealizedLtcgGain,
        BigDecimal exemptionHeadroomConsumed,
        String recommendationText
    ) {}
    public record TaxHarvestResult(
        String fiscalYear,
        BigDecimal exemptionLimit,
        BigDecimal exemptionUsedSoFar,
        BigDecimal exemptionRemaining,
        BigDecimal totalUnrealizedLtcgAvailable,
        BigDecimal harvestableLtcgGain,
        List<TaxHarvestRecommendation> recommendations
    ) {}
    public static TaxHarvestResult generateHarvestPlan(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal exemptionUsedThisFy,
        String fiscalYear
    ) {
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
        BigDecimal limit = rules.equityExemptionLimit();
        BigDecimal remainingExemption = limit.subtract(exemptionUsedThisFy).max(BigDecimal.ZERO);
        LocalDate today = LocalDate.now();
        List<LotWithGain> ltcgLots = new ArrayList<>();
        BigDecimal totalUnrealizedLtcg = BigDecimal.ZERO;
        for (Lot lot : openLots) {
            AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
            if (category != AssetCategory.EQUITY) continue;
            long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
            if (holdingDays >= rules.equityLtcgThresholdDays()) {
                BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
                BigDecimal currentVal = lot.remainingUnits().multiply(nav);
                BigDecimal gain = currentVal.subtract(lot.totalCostBasis());
                if (gain.compareTo(BigDecimal.ZERO) > 0) {
                    totalUnrealizedLtcg = totalUnrealizedLtcg.add(gain);
                    ltcgLots.add(new LotWithGain(lot, nav, gain));
                }
            }
        }
        // Sort lots by gain descending to maximize headroom utilization
        ltcgLots.sort(Comparator.comparing(LotWithGain::gain).reversed());
        BigDecimal headroomLeft = remainingExemption;
        BigDecimal totalHarvestedGain = BigDecimal.ZERO;
        List<TaxHarvestRecommendation> recommendations = new ArrayList<>();
        for (LotWithGain entry : ltcgLots) {
            if (headroomLeft.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal harvestableGain = entry.gain().min(headroomLeft);
            BigDecimal proportionToSell = BigDecimal.ONE;
            if (entry.gain().compareTo(BigDecimal.ZERO) > 0) {
                proportionToSell = harvestableGain.divide(entry.gain(), 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
            }
            BigDecimal unitsToSell = entry.lot().remainingUnits().multiply(proportionToSell).setScale(4, RoundingMode.HALF_UP);
            BigDecimal proceeds = unitsToSell.multiply(entry.nav()).setScale(2, RoundingMode.HALF_UP);
            headroomLeft = headroomLeft.subtract(harvestableGain).max(BigDecimal.ZERO);
            totalHarvestedGain = totalHarvestedGain.add(harvestableGain);
            String text = "Sell " + unitsToSell + " units of " + entry.lot().assetName() + 
                         " to harvest ₹" + harvestableGain.setScale(0, RoundingMode.HALF_UP) + " tax-free LTCG gain, then same-day rebuy.";
            recommendations.add(new TaxHarvestRecommendation(
                entry.lot().assetId(),
                entry.lot().assetName(),
                entry.lot().lotId(),
                unitsToSell,
                proceeds,
                harvestableGain.setScale(2, RoundingMode.HALF_UP),
                harvestableGain.setScale(2, RoundingMode.HALF_UP),
                text
            ));
        }
        return new TaxHarvestResult(
            fiscalYear,
            limit,
            exemptionUsedThisFy,
            remainingExemption,
            totalUnrealizedLtcg.setScale(2, RoundingMode.HALF_UP),
            totalHarvestedGain.setScale(2, RoundingMode.HALF_UP),
            recommendations
        );
    }
    private record LotWithGain(Lot lot, BigDecimal nav, BigDecimal gain) {}
}
```

## File: src/main/java/com/portfolioos/core/valuation/RebalanceEngine.java
```java
package com.portfolioos.core.valuation;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
public class RebalanceEngine {
    public record RebalanceLotSelection(
        String lotId,
        String assetId,
        String assetName,
        BigDecimal unitsToSell,
        BigDecimal redemptionProceeds,
        BigDecimal costBasis,
        BigDecimal estimatedGain,
        String taxTerm,
        BigDecimal estimatedTaxDrag
    ) {}
    public record RebalancePreviewResult(
        BigDecimal targetRedemptionAmount,
        BigDecimal actualRedemptionAmount,
        BigDecimal totalEstimatedGain,
        BigDecimal totalTaxDrag,
        BigDecimal effectiveTaxRatePct,
        BigDecimal ltcgExemptionHarvested,
        List<RebalanceLotSelection> selectedLots
    ) {}
    public static RebalancePreviewResult calculateRebalancePreview(
        List<Lot> openLots,
        Map<String, BigDecimal> navMap,
        BigDecimal targetAmount,
        BigDecimal remainingExemption,
        String fiscalYear
    ) {
        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
        BigDecimal remainingTarget = targetAmount;
        BigDecimal unusedExemption = remainingExemption;
        BigDecimal totalGain = BigDecimal.ZERO;
        BigDecimal totalTaxDrag = BigDecimal.ZERO;
        BigDecimal actualRedemption = BigDecimal.ZERO;
        List<RebalanceLotSelection> selected = new ArrayList<>();
        LocalDate today = LocalDate.now();
        // Sort: loss-making first (0), then long-term (1), then short-term (2)
        List<Lot> sortedLots = new ArrayList<>(openLots);
        sortedLots.sort((l1, l2) -> {
            BigDecimal nav1 = navMap.getOrDefault(l1.assetId(), l1.costPerUnit());
            BigDecimal gainPerUnit1 = nav1.subtract(l1.costPerUnit());
            AssetCategory cat1 = TaxClassifier.detectCategory(l1.assetId(), l1.assetName());
            long holdingDays1 = ChronoUnit.DAYS.between(l1.acquisitionDate(), today);
            long thresholdDays1 = getThresholdDays(cat1, rules);
            boolean isLtcg1 = thresholdDays1 > 0 && holdingDays1 >= thresholdDays1;
            int rank1 = (gainPerUnit1.compareTo(BigDecimal.ZERO) < 0) ? 0 : (isLtcg1 ? 1 : 2);
            BigDecimal nav2 = navMap.getOrDefault(l2.assetId(), l2.costPerUnit());
            BigDecimal gainPerUnit2 = nav2.subtract(l2.costPerUnit());
            AssetCategory cat2 = TaxClassifier.detectCategory(l2.assetId(), l2.assetName());
            long holdingDays2 = ChronoUnit.DAYS.between(l2.acquisitionDate(), today);
            long thresholdDays2 = getThresholdDays(cat2, rules);
            boolean isLtcg2 = thresholdDays2 > 0 && holdingDays2 >= thresholdDays2;
            int rank2 = (gainPerUnit2.compareTo(BigDecimal.ZERO) < 0) ? 0 : (isLtcg2 ? 1 : 2);
            return Integer.compare(rank1, rank2);
        });
        for (Lot lot : sortedLots) {
            if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
            BigDecimal lotValue = lot.remainingUnits().multiply(nav);
            BigDecimal redemptionFromLot = lotValue.min(remainingTarget);
            BigDecimal unitsToSell = BigDecimal.ZERO;
            if (nav.compareTo(BigDecimal.ZERO) > 0) {
                unitsToSell = redemptionFromLot.divide(nav, 4, RoundingMode.HALF_UP);
            }
            BigDecimal costBasisSlice = unitsToSell.multiply(lot.costPerUnit());
            BigDecimal gainSlice = redemptionFromLot.subtract(costBasisSlice);
            AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
            long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
            long thresholdDays = getThresholdDays(category, rules);
            boolean isLtcg = thresholdDays > 0 && holdingDays >= thresholdDays;
            BigDecimal taxDrag = BigDecimal.ZERO;
            if (gainSlice.compareTo(BigDecimal.ZERO) > 0) {
                if (isLtcg) {
                    BigDecimal exemptPortion = gainSlice.min(unusedExemption);
                    BigDecimal taxableGain = gainSlice.subtract(exemptPortion);
                    unusedExemption = unusedExemption.subtract(exemptPortion).max(BigDecimal.ZERO);
                    taxDrag = taxableGain.multiply(rules.equityLtcgRate());
                } else {
                    BigDecimal stcgRate = (category == AssetCategory.EQUITY) ? rules.equityStcgRate() : new BigDecimal("0.30"); // slab default
                    taxDrag = gainSlice.multiply(stcgRate);
                }
            }
            selected.add(new RebalanceLotSelection(
                lot.lotId(),
                lot.assetId(),
                lot.assetName(),
                unitsToSell,
                redemptionFromLot,
                costBasisSlice,
                gainSlice,
                isLtcg ? "LONG_TERM" : "SHORT_TERM",
                taxDrag
            ));
            actualRedemption = actualRedemption.add(redemptionFromLot);
            totalGain = totalGain.add(gainSlice);
            totalTaxDrag = totalTaxDrag.add(taxDrag);
            remainingTarget = remainingTarget.subtract(redemptionFromLot);
        }
        BigDecimal ltcgHarvested = remainingExemption.subtract(unusedExemption);
        BigDecimal effTaxRate = BigDecimal.ZERO;
        if (actualRedemption.compareTo(BigDecimal.ZERO) > 0) {
            effTaxRate = totalTaxDrag.multiply(new BigDecimal("100")).divide(actualRedemption, 2, RoundingMode.HALF_UP);
        }
        return new RebalancePreviewResult(
            targetAmount,
            actualRedemption,
            totalGain,
            totalTaxDrag,
            effTaxRate,
            ltcgHarvested,
            selected
        );
    }
    private static long getThresholdDays(AssetCategory category, TaxRulesConfig rules) {
        return switch (category) {
            case EQUITY -> rules.equityLtcgThresholdDays();
            case GOLD_SILVER, INTERNATIONAL, SGB -> rules.goldInternationalThresholdDays();
            case DEBT_SPECIFIED_50AA -> -1L;
        };
    }
}
```

## File: src/main/java/com/portfolioos/core/xirr/CashFlow.java
```java
package com.portfolioos.core.xirr;
import java.math.BigDecimal;
import java.time.LocalDate;
public record CashFlow(
    LocalDate date,
    BigDecimal amount // negative for investments, positive for inflows / current valuation
) {}
```

## File: src/main/java/com/portfolioos/core/xirr/XirrEngine.java
```java
package com.portfolioos.core.xirr;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
public class XirrEngine {
    public double calculateXirr(List<CashFlow> cashFlows) {
        if (cashFlows.size() < 2) return 0.0;
        List<CashFlow> sorted = new ArrayList<>(cashFlows);
        sorted.sort(Comparator.comparing(CashFlow::date));
        LocalDate startDate = sorted.get(0).date();
        LocalDate endDate = sorted.get(sorted.size() - 1).date();
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalRealizedOrCurrent = BigDecimal.ZERO;
        for (CashFlow cf : sorted) {
            if (cf.amount().compareTo(BigDecimal.ZERO) < 0) {
                totalInvested = totalInvested.add(cf.amount().abs());
            } else if (cf.amount().compareTo(BigDecimal.ZERO) > 0) {
                totalRealizedOrCurrent = totalRealizedOrCurrent.add(cf.amount());
            }
        }
        if (totalInvested.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        if (totalDays < 30) {
            BigDecimal gain = totalRealizedOrCurrent.subtract(totalInvested);
            BigDecimal absReturn = gain.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100.0"));
            return absReturn.doubleValue();
        }
        List<Double> dates = new ArrayList<>();
        List<Double> amounts = new ArrayList<>();
        for (CashFlow cf : sorted) {
            dates.add((double) ChronoUnit.DAYS.between(startDate, cf.date()) / 365.25);
            amounts.add(cf.amount().doubleValue());
        }
        // Newton-Raphson guess
        double rate = 0.10;
        for (int iter = 0; iter < 100; iter++) {
            double f = npv(rate, dates, amounts);
            double df = dNpv(rate, dates, amounts);
            if (Math.abs(df) > 1e-10) {
                double nextRate = rate - f / df;
                if (Math.abs(nextRate - rate) < 1e-7) {
                    double result = nextRate * 100.0;
                    if (Double.isNaN(result) || Double.isInfinite(result)) return 0.0;
                    return Math.max(-99.0, Math.min(300.0, result));
                }
                rate = nextRate;
            }
            if (rate <= -0.90) rate = -0.50;
        }
        // Bracketed Bisection Fallback
        double low = -0.50;
        double high = 3.0;
        double flow = npv(low, dates, amounts);
        double fhigh = npv(high, dates, amounts);
        if (flow * fhigh <= 0) {
            for (int i = 0; i < 100; i++) {
                double mid = (low + high) / 2.0;
                double fmid = npv(mid, dates, amounts);
                if (Math.abs(fmid) < 1e-7 || (high - low) < 1e-7) {
                    return Math.max(-99.0, Math.min(300.0, mid * 100.0));
                }
                if (flow * fmid < 0) {
                    high = mid;
                    fhigh = fmid;
                } else {
                    low = mid;
                    flow = fmid;
                }
            }
            return Math.max(-99.0, Math.min(300.0, ((low + high) / 2.0) * 100.0));
        }
        double rawResult = rate * 100.0;
        if (Double.isNaN(rawResult) || Double.isInfinite(rawResult)) return 0.0;
        return Math.max(-99.0, Math.min(300.0, rawResult));
    }
    private double npv(double r, List<Double> dates, List<Double> amounts) {
        double sum = 0.0;
        for (int i = 0; i < dates.size(); i++) {
            double t = dates.get(i);
            double c = amounts.get(i);
            double factor = Math.pow(1.0 + r, t);
            if (factor != 0.0) {
                sum += c / factor;
            }
        }
        return sum;
    }
    private double dNpv(double r, List<Double> dates, List<Double> amounts) {
        double sum = 0.0;
        for (int i = 0; i < dates.size(); i++) {
            double t = dates.get(i);
            double c = amounts.get(i);
            double factor = Math.pow(1.0 + r, t + 1.0);
            if (factor != 0.0) {
                sum -= t * c / factor;
            }
        }
        return sum;
    }
}
```

## File: src/main/java/com/portfolioos/core/CoreApplication.java
```java
package com.portfolioos.core;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.ports.EventStorePort;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class
})
public class CoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
    @Bean
    public CommandLineRunner startupRunner(EventStorePort eventStore, DuckDbProjector duckDbProjector) {
        return args -> {
            System.out.println("Initializing DuckDB Projection from SQLite ledger...");
            try {
                duckDbProjector.projectEvents(eventStore.getAllEvents());
                System.out.println("DuckDB projection loaded successfully.");
            } catch (Exception e) {
                System.err.println("Failed to build startup projection: " + e.getMessage());
            }
        };
    }
}
```

## File: src/main/resources/static/src/js/modules/insurance.js
```javascript
import { API_BASE, fetchJson, getAuthHeaders } from '../api.js';
import { showToast } from '../utils.js';
export async function fetchInsuranceChecklist() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/insurance`).catch(() => null);
    if (data) {
      renderInsuranceBanner(data);
    }
  } catch (e) {
    console.error('Insurance checklist error:', e);
  }
}
export function renderInsuranceBanner(data) {
  const banner = document.getElementById('insuranceBanner');
  const itemsContainer = document.getElementById('insuranceItemsList');
  const badge = document.getElementById('insuranceStatusBadge');
  if (!banner || !itemsContainer) return;
  if (data.isAllPurchased) {
    banner.style.display = 'none';
    return;
  }
  banner.style.display = 'block';
  if (badge) badge.textContent = 'ACTION REQUIRED';
  let html = '';
  data.items.forEach(item => {
    const isPurchased = item.status === 'PURCHASED';
    html += `
      <div class="insurance-card">
        <div class="insurance-info">
          <div class="title">${item.name}</div>
          <div class="desc">${item.description}</div>
        </div>
        <button class="action-btn ${isPurchased ? 'purchased-btn' : ''}" data-id="${item.id}" data-status="${isPurchased ? 'NOT_PURCHASED' : 'PURCHASED'}">
          ${isPurchased ? '✓ Purchased' : 'Mark Purchased'}
        </button>
      </div>
    `;
  });
  itemsContainer.innerHTML = html;
  itemsContainer.querySelectorAll('.action-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = btn.getAttribute('data-id');
      const status = btn.getAttribute('data-status');
      toggleInsuranceStatus(id, status);
    });
  });
}
export async function toggleInsuranceStatus(id, status) {
  try {
    const res = await fetch(`${API_BASE}/portfolio/insurance`, {
      method: 'POST',
      headers: getAuthHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ id, status })
    });
    if (res.ok) {
      const updated = await res.json();
      renderInsuranceBanner(updated);
      showToast(`Updated ${id} insurance status`, 'success');
    }
  } catch (e) {
    showToast(`Error updating insurance: ${e.message}`, 'error');
  }
}
window.toggleInsuranceStatus = toggleInsuranceStatus;
```

## File: src/main/resources/static/src/js/modules/portfolio.js
```javascript
import { API_BASE, fetchJson } from '../api.js';
import { state } from '../state.js';
import { formatINR } from '../utils.js';
export function updatePortfolioSummary(summary) {
  const netWorthVal = document.querySelector('.net-worth-val');
  const gainText = document.querySelector('.net-worth-gain');
  const subText = document.querySelector('.net-worth-sub');
  const xirrVal = document.querySelector('.xirr-val');
  if (netWorthVal && summary.totalCurrentValue) {
    netWorthVal.textContent = formatINR(summary.totalCurrentValue);
    netWorthVal.classList.remove('skeleton');
  }
  if (gainText && summary.totalUnrealizedGain) {
    const gain = Math.round(parseFloat(summary.totalUnrealizedGain) || 0);
    const sign = gain >= 0 ? '+' : '';
    gainText.textContent = `Unrealized gain: ${sign}${formatINR(gain)}`;
    gainText.className = `metric-delta ${gain >= 0 ? 'positive' : 'negative'}`;
  }
  if (subText && summary.activeHoldingCount !== undefined) {
    subText.innerHTML = `Active Holdings: <strong>${summary.activeHoldingCount} Schemes</strong>`;
  }
  if (xirrVal && summary.xirrPercentage) {
    xirrVal.textContent = summary.xirrPercentage;
    xirrVal.classList.remove('skeleton');
  }
}
export function renderHoldingsTable(holdings) {
  const tableBody = document.querySelector('#holdingsTable tbody');
  if (!tableBody) return;
  if (!holdings || holdings.length === 0) {
    tableBody.innerHTML = `<tr><td colspan="7" style="text-align:center; color:#64748b;">No open holdings found in ledger.</td></tr>`;
    return;
  }
  const fragment = document.createDocumentFragment();
  const template = document.createElement('template');
  let html = '';
  holdings.forEach((h, idx) => {
    const inv = Math.round(parseFloat(h.investedValue) || 0);
    const cur = Math.round(parseFloat(h.currentValue) || 0);
    const gain = Math.round(parseFloat(h.unrealizedGain) || 0);
    const gainSign = gain >= 0 ? '+' : '';
    const gainColor = gain >= 0 ? 'color: #10b981;' : 'color: #ef4444;';
    html += `
      <tr class="holding-row" onclick="toggleLotDetails('${idx}')">
        <td style="font-weight:600;">${h.assetName}</td>
        <td><span class="cat-badge cat-${h.category}">${h.category.replace('_SPECIFIED_50AA', '')}</span></td>
        <td class="font-mono">${formatINR(inv)}</td>
        <td class="font-mono" style="font-weight:600;">${formatINR(cur)}</td>
        <td class="font-mono" style="${gainColor}">${gainSign}${formatINR(gain)} (${h.unrealizedGainPct}%)</td>
        <td class="font-mono">${h.allocationPct}%</td>
        <td><button class="pill-btn">${h.lots.length} Lots ▼</button></td>
      </tr>
      <tr id="lotRow-${idx}" style="display: none;">
        <td colspan="7" class="lot-expansion-td">
          <table class="lot-subtable">
            <thead>
              <tr>
                <th>Acq Date</th>
                <th>Units</th>
                <th>Cost Basis</th>
                <th>Unrealized Gain</th>
                <th>Days Held</th>
                <th>Tax Term</th>
              </tr>
            </thead>
            <tbody>
              ${h.lots.map(l => `
                <tr>
                  <td>${l.acquisitionDate}</td>
                  <td class="font-mono">${l.remainingUnits}</td>
                  <td class="font-mono">${formatINR(parseFloat(l.costPerUnit) * parseFloat(l.remainingUnits))}</td>
                  <td class="font-mono" style="${parseFloat(l.unrealizedGain) >= 0 ? 'color: #10b981;' : 'color: #ef4444;'}">
                    ${parseFloat(l.unrealizedGain) >= 0 ? '+' : ''}${formatINR(l.unrealizedGain)}
                  </td>
                  <td>${l.holdingDays}d</td>
                  <td><span class="cat-badge ${l.isLtcg ? 'cat-EQUITY' : 'cat-DEBT_SPECIFIED_50AA'}">${l.isLtcg ? 'LTCG' : 'STCG (' + (l.daysToLtcg > 0 ? l.daysToLtcg + 'd left' : 'Always') + ')'}</span></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </td>
      </tr>
    `;
  });
  template.innerHTML = html;
  fragment.appendChild(template.content);
  tableBody.replaceChildren(fragment);
}
window.toggleLotDetails = (idx) => {
  const row = document.getElementById(`lotRow-${idx}`);
  if (row) {
    row.style.display = row.style.display === 'none' ? 'table-row' : 'none';
  }
};
export function renderPieChart(containerId, data) {
  const container = document.getElementById(containerId);
  if (!container || !data || data.length === 0 || !window.echarts) return null;
  const instance = window.echarts.init(container);
  const option = {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', formatter: '{b}: ₹ {c} ({d}%)' },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { color: '#94a3b8', fontSize: 11 }
    },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['38%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#0c101c', borderWidth: 2 },
      label: { show: false },
      data: data
    }]
  };
  instance.setOption(option);
  return instance;
}
export function renderAllocationChart(allocations) {
  if (state.charts.allocChart) state.charts.allocChart.dispose();
  if (!allocations || allocations.length === 0) return;
  const total = allocations.reduce((sum, a) => sum + (parseFloat(a.currentValue) || 0), 0);
  const main = [];
  let othersVal = 0;
  let othersCount = 0;
  allocations.forEach(a => {
    const val = parseFloat(a.currentValue) || 0;
    const pct = total > 0 ? (val / total) * 100 : 0;
    if (pct < 3.0 && allocations.length > 6) {
      othersVal += val;
      othersCount++;
    } else {
      main.push({
        name: a.assetName.length > 25 ? a.assetName.substring(0, 23) + '...' : a.assetName,
        value: val
      });
    }
  });
  if (othersVal > 0) {
    main.push({
      name: `Others (${othersCount})`,
      value: othersVal
    });
  }
  state.charts.allocChart = renderPieChart('allocationChart', main);
}
export function renderCategoryChart(catAllocations) {
  if (state.charts.categoryChart) state.charts.categoryChart.dispose();
  const data = catAllocations.map(c => ({
    name: c.categoryName,
    value: parseFloat(c.currentValue) || 0
  }));
  state.charts.categoryChart = renderPieChart('categoryChart', data);
}
export async function fetchConsolidationPreviewData() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/consolidation-preview?fy=${state.currentFy}`).catch(() => null);
    if (data) {
      renderConsolidationPlan(data);
    }
  } catch (e) {
    console.error('Error fetching consolidation preview:', e);
  }
}
export function renderConsolidationPlan(data) {
  const container = document.getElementById('consolidationPlanContainer');
  const badge = document.getElementById('consolidationWindowBadge');
  if (!container) return;
  if (badge) {
    badge.textContent = data.isRebalanceWindowOpen ? 'WINDOW OPEN: EXECUTE REBALANCE' : `SCHEDULED WINDOW: ${data.nextScheduledWindow}`;
    badge.style.color = data.isRebalanceWindowOpen ? '#10b981' : '#06b6d4';
  }
  const proceeds = Math.round(parseFloat(data.totalProceeds) || 256200);
  const taxDrag = Math.round(parseFloat(data.totalTaxDrag) || 0);
  let html = `
    <div style="margin-bottom:12px; font-size:13px;" class="font-mono">
      Unlocked Capital: <strong style="color:#06b6d4;">${formatINR(proceeds)}</strong> | 
      Estimated Tax Drag: <strong style="color:#f59e0b;">${formatINR(taxDrag)}</strong>
    </div>
    <table class="data-table" style="font-size:12px;">
      <thead>
        <tr>
          <th>Active 6-Fund Core Asset</th>
          <th>SIP Target %</th>
          <th>Pro-Rata Deployment Amount</th>
        </tr>
      </thead>
      <tbody>
  `;
  for (const alloc of data.proRataAllocations) {
    const amt = Math.round(parseFloat(alloc.deploymentAmount) || 0);
    html += `
      <tr>
        <td style="font-weight:600;">${alloc.assetName}</td>
        <td><span class="days-badge">${alloc.sipWeightPct}%</span></td>
        <td class="font-mono" style="font-weight:600; color:#10b981;">${formatINR(amt)}</td>
      </tr>
    `;
  }
  html += `</tbody></table>`;
  container.innerHTML = html;
}
export async function fetchRebalancePreview(amount = 100000) {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/rebalance-preview?amount=${amount}&fy=${state.currentFy}`).catch(() => null);
    if (data) {
      updateRebalanceSummary(data);
    }
  } catch (e) {
    console.error('Error fetching rebalance preview:', e);
  }
}
export function updateRebalanceSummary(data) {
  const rebTaxDrag = document.getElementById('rebTaxDrag');
  const rebEffRate = document.getElementById('rebEffRate');
  const rebLtcgHarvested = document.getElementById('rebLtcgHarvested');
  if (rebTaxDrag && data.totalTaxDrag) {
    rebTaxDrag.textContent = formatINR(data.totalTaxDrag);
  }
  if (rebEffRate && data.effectiveTaxRatePct) {
    rebEffRate.textContent = data.effectiveTaxRatePct;
  }
  if (rebLtcgHarvested && data.ltcgExemptionHarvested) {
    rebLtcgHarvested.textContent = formatINR(data.ltcgExemptionHarvested);
  }
}
export async function fetchGoalSummary() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/goals`).catch(() => null);
    if (data) {
      renderGoalSummary(data);
    }
  } catch (e) {
    console.error('Goal summary error:', e);
  }
}
export function renderGoalSummary(data) {
  const idleVal = document.querySelector('.idle-cash-val');
  if (idleVal && data.unallocatedCash) {
    idleVal.textContent = formatINR(data.unallocatedCash);
    idleVal.classList.remove('skeleton');
  }
}
export async function fetchFireSummary() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/fire`).catch(() => null);
    if (data) {
      renderFireSummary(data);
    }
  } catch (e) {
    console.error('FIRE summary error:', e);
  }
}
export function renderFireSummary(data) {
  const statusPill = document.getElementById('fireStatusPill');
  const scenarioLabel = document.getElementById('fireScenarioLabel');
  const investableNw = document.getElementById('fireInvestableNw');
  const reqCorpus = document.getElementById('fireRequiredCorpus');
  const projCorpus = document.getElementById('fireProjectedCorpus');
  if (statusPill) {
    statusPill.textContent = data.status === 'ON_TRACK' ? 'ON TRACK' : `SHORT BY ${formatINR(data.shortageOrSurplusAmount)}`;
    statusPill.className = `fire-status-pill ${data.status === 'ON_TRACK' ? 'on-track' : 'short'}`;
  }
  if (scenarioLabel) scenarioLabel.textContent = `Scenario: ${data.activeScenarioLabel}`;
  if (investableNw) investableNw.textContent = formatINR(data.fireInvestableNetWorth);
  if (reqCorpus) reqCorpus.textContent = `₹ ${(parseFloat(data.requiredCorpus) / 10000000).toFixed(2)} Cr`;
  if (projCorpus) projCorpus.textContent = `₹ ${(parseFloat(data.projectedCorpusAtTargetAge) / 10000000).toFixed(2)} Cr`;
}
export async function fetchBucketRebalance() {
  try {
    const data = await fetchJson(`${API_BASE}/portfolio/buckets/rebalance`).catch(() => null);
    if (data) {
      renderBucketRebalance(data);
    }
  } catch (e) {
    console.error('Bucket rebalance error:', e);
  }
}
export function renderBucketRebalance(data) {
  const drawdownTag = document.getElementById('drawdownTag');
  const bucketGrid = document.getElementById('bucketGrid');
  if (drawdownTag && data.drawdownStatus) {
    const dd = data.drawdownStatus;
    drawdownTag.textContent = `${dd.benchmarkName}: ${dd.drawdownPct}% Drawdown`;
  }
  if (bucketGrid && data.bucketStatuses) {
    let html = '';
    data.bucketStatuses.forEach(b => {
      const nameFmt = b.bucket.replace('_', ' ');
      html += `
        <div class="bucket-card ${b.isDrifted ? 'drifted' : ''}">
          <div class="bucket-card-header">
            <span class="bucket-name">${nameFmt}</span>
            <span class="drift-badge ${b.isDrifted ? 'warn' : 'ok'}">${b.isDrifted ? 'Drift: ' + b.driftPct + '%' : 'Target OK'}</span>
          </div>
          <div class="font-mono" style="font-size:16px; font-weight:700;">${formatINR(b.currentValue)}</div>
          <div class="text-muted" style="font-size:11px; margin-top:4px;">Current: ${b.currentPct}% · Target: ${b.targetPct}%</div>
        </div>
      `;
    });
    bucketGrid.innerHTML = html;
  }
}
```

## File: src/main/resources/static/src/js/modules/tax.js
```javascript
import { API_BASE, fetchJson } from '../api.js';
import { state } from '../state.js';
import { formatINR } from '../utils.js';
export async function fetchTaxMetrics() {
  try {
    const data = await fetchJson(`${API_BASE}/tax/exemption-status?fy=${state.currentFy}`).catch(() => null);
    if (data) {
      updateExemptionMeter(data);
    }
    const report = await fetchJson(`${API_BASE}/tax/reports/itr2?fy=${state.currentFy}`).catch(() => null);
    if (report) {
      updateReportMetrics(report);
    }
  } catch (e) {
    console.error('Error fetching tax metrics:', e);
  }
}
export function updateExemptionMeter(data) {
  const meterVal = document.querySelector('.ltcg-meter-val');
  const fill = document.querySelector('.progress-fill-gradient');
  const pctText = document.querySelector('.meter-meta .pct-used');
  const remainingText = document.querySelector('.meter-meta .remaining');
  if (meterVal && fill && remainingText) {
    const used = Math.round(parseFloat(data.exemptionUsed) || 0);
    const limit = Math.round(parseFloat(data.exemptionLimit) || 125000);
    const pct = Math.min(100, Math.round((used / limit) * 100));
    meterVal.innerHTML = `${formatINR(used)} <span class="sub-limit">/ 1.25L</span>`;
    meterVal.classList.remove('skeleton');
    fill.style.width = `${pct}%`;
    if (pctText) pctText.textContent = `${pct}% Used`;
    remainingText.textContent = `${formatINR(limit - used)} Available`;
  }
}
export function updateReportMetrics(report) {
  const stcgVal = document.querySelector('.stcg-val');
  if (stcgVal && report.totalRealizedStcg) {
    stcgVal.textContent = formatINR(report.totalRealizedStcg);
    stcgVal.classList.remove('skeleton');
  }
}
export async function fetchDecisionRadar() {
  try {
    const opportunities = await fetchJson(`${API_BASE}/tax/harvest-opportunities`).catch(() => []);
    const ladder = await fetchJson(`${API_BASE}/tax/maturation-ladder`).catch(() => []);
    const antigravityData = await fetchJson(`${API_BASE}/portfolio/antigravity`).catch(() => null);
    renderDecisionRadar(opportunities, ladder, antigravityData);
  } catch (e) {
    console.error('Error fetching decision radar:', e);
  }
}
export function renderDecisionRadar(opportunities, ladder, antigravityData) {
  const listContainer = document.querySelector('.radar-list');
  if (!listContainer) return;
  let html = '';
  if (antigravityData && antigravityData.antigravityAssets && antigravityData.antigravityAssets.length > 0) {
    for (const ag of antigravityData.antigravityAssets) {
      html += `
        <div class="radar-card info-border" style="border-left: 3px solid #06b6d4; background: rgba(6, 182, 212, 0.08);">
          <div class="radar-icon info">🚀</div>
          <div class="radar-content">
            <div class="radar-title" style="color:#06b6d4;">ANTIGRAVITY DETECTED (${ag.assetName})</div>
            <div class="radar-desc">Beta: <strong>${ag.beta}</strong> | 30d TWR: <strong>+${ag.twr30dPct}%</strong> during market drawdown (${antigravityData.marketDrawdownPct}%). ${ag.recommendation}</div>
          </div>
          <span class="antigravity-badge">🚀 Low Beta + Alpha</span>
        </div>
      `;
    }
  }
  if (opportunities && opportunities.length > 0) {
    for (const opp of opportunities.slice(0, 2)) {
      const loss = Math.round(parseFloat(opp.potentialHarvestableLoss) || 0);
      html += `
        <div class="radar-card warning-border">
          <div class="radar-icon warning">⚡</div>
          <div class="radar-content">
            <div class="radar-title">Tax-Loss Harvesting Opportunity</div>
            <div class="radar-desc">Harvest <strong>${formatINR(loss)}</strong> loss in <em>${opp.assetName}</em> before March 31.</div>
          </div>
          <span class="days-badge">Harvest Now</span>
        </div>
      `;
    }
  }
  if (ladder && ladder.length > 0) {
    for (const mat of ladder.slice(0, 2)) {
      html += `
        <div class="radar-card maturation-border">
          <div class="radar-icon maturation">⏳</div>
          <div class="radar-content">
            <div class="radar-title">LTCG Tax Maturation Coming Up</div>
            <div class="radar-desc">Lot of <em>${mat.assetName}</em> (${mat.remainingUnits} units) becomes <strong>LTCG</strong> on ${mat.targetLtcgDate}.</div>
          </div>
          <span class="days-badge">Wait ${mat.daysRemainingToLtcg} Days</span>
        </div>
      `;
    }
  }
  if (!html) {
    html = `
      <div class="radar-card info-border">
        <div class="radar-icon info">✓</div>
        <div class="radar-content">
          <div class="radar-title">Portfolio Tax Status Optimal</div>
          <div class="radar-desc">No immediate tax-loss harvesting or pending LTCG transitions in the next 90 days.</div>
        </div>
        <span class="days-badge">Optimum</span>
      </div>
    `;
  }
  listContainer.innerHTML = html;
}
export async function fetchRealizedLog() {
  try {
    const logs = await fetchJson(`${API_BASE}/tax/realized-log?fy=${state.currentFy}`).catch(() => []);
    renderRealizedLogTable(logs);
  } catch (e) {
    console.error('Error fetching realized log:', e);
  }
}
export function renderRealizedLogTable(logs) {
  const tableBody = document.querySelector('#realizedLogTable tbody');
  if (!tableBody) return;
  if (!logs || logs.length === 0) {
    tableBody.innerHTML = `<tr><td colspan="8" style="text-align:center; color:#64748b;">No realized disposals recorded for ${state.currentFy}.</td></tr>`;
    return;
  }
  const fragment = document.createDocumentFragment();
  const template = document.createElement('template');
  let html = '';
  logs.forEach(l => {
    const gain = Math.round(parseFloat(l.realizedGain) || 0);
    const gainSign = gain >= 0 ? '+' : '';
    const gainColor = gain >= 0 ? 'color: #10b981;' : 'color: #ef4444;';
    html += `
      <tr>
        <td>${l.disposalDate}</td>
        <td>${l.acquisitionDate}</td>
        <td style="font-weight:600;">${l.assetName}</td>
        <td class="font-mono">${l.unitsMatched}</td>
        <td class="font-mono">${formatINR(l.saleProceeds)}</td>
        <td class="font-mono">${formatINR(l.costBasis)}</td>
        <td class="font-mono" style="${gainColor}">${gainSign}${formatINR(gain)}</td>
        <td><span class="cat-badge ${l.taxTerm === 'LONG_TERM' ? 'cat-EQUITY' : 'cat-DEBT_SPECIFIED_50AA'}">${l.taxTerm}</span></td>
      </tr>
    `;
  });
  template.innerHTML = html;
  fragment.appendChild(template.content);
  tableBody.replaceChildren(fragment);
}
```

## File: src/main/resources/static/src/js/api.js
```javascript
export const API_BASE = window.location.origin.includes('http') 
  ? `${window.location.origin}/api/v1` 
  : 'http://127.0.0.1:8080/api/v1';
export const DEFAULT_AUTH_TOKEN = 'fintracker-cachyos-default-key-2026';
export function getAuthHeaders(extraHeaders = {}) {
  const token = localStorage.getItem('API_AUTH_TOKEN') || window.API_AUTH_TOKEN || DEFAULT_AUTH_TOKEN;
  return {
    ...extraHeaders,
    'X-Api-Auth-Token': token
  };
}
export async function fetchJson(url, options = {}) {
  const headers = getAuthHeaders(options.headers || {});
  const res = await fetch(url, { ...options, headers });
  if (!res.ok) {
    throw new Error(`HTTP error! status: ${res.status}`);
  }
  return await res.json();
}
```

## File: src/main/resources/static/src/js/state.js
```javascript
export const state = {
  currentFy: '2026-27',
  charts: {
    perfChart: null,
    allocChart: null,
    categoryChart: null
  }
};
export function setCurrentFy(fy) {
  state.currentFy = fy;
}
export function getCurrentFy() {
  return state.currentFy;
}
```

## File: src/main/resources/static/src/js/utils.js
```javascript
export function formatINR(val, round = true) {
  const num = round ? Math.round(parseFloat(val) || 0) : parseFloat(val) || 0;
  return `₹ ${num.toLocaleString('en-IN')}`;
}
export function showToast(message, type = 'success') {
  const stack = document.getElementById('toastStack');
  if (!stack) return;
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.textContent = message;
  stack.appendChild(toast);
  setTimeout(() => {
    toast.remove();
  }, 4000);
}
```

## File: src/main/resources/static/src/app.js
```javascript
import { API_BASE, fetchJson, getAuthHeaders, DEFAULT_AUTH_TOKEN } from './js/api.js';
import { state, setCurrentFy } from './js/state.js';
import { showToast, formatINR } from './js/utils.js';
import {
  fetchTaxMetrics,
  fetchRealizedLog,
  fetchDecisionRadar
} from './js/modules/tax.js';
import {
  updatePortfolioSummary,
  renderHoldingsTable,
  renderAllocationChart,
  renderCategoryChart,
  fetchConsolidationPreviewData,
  fetchRebalancePreview,
  fetchGoalSummary,
  fetchFireSummary,
  fetchBucketRebalance
} from './js/modules/portfolio.js';
document.addEventListener('DOMContentLoaded', () => {
  // Tab Switching Handler
  const tabBtns = document.querySelectorAll('.tab-btn');
  const tabContents = document.querySelectorAll('.tab-content');
  tabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const target = btn.getAttribute('data-tab');
      tabBtns.forEach(b => b.classList.remove('active'));
      tabContents.forEach(c => c.classList.remove('active'));
      btn.classList.add('active');
      const content = document.getElementById(`tab-${target}`);
      if (content) content.classList.add('active');
      setTimeout(() => {
        if (state.charts.allocChart) state.charts.allocChart.resize();
        if (state.charts.categoryChart) state.charts.categoryChart.resize();
      }, 50);
    });
  });
  const fySelect = document.getElementById('fySelect');
  if (fySelect) {
    setCurrentFy(fySelect.value);
    fySelect.addEventListener('change', () => {
      setCurrentFy(fySelect.value);
      fetchTaxMetrics();
      fetchRealizedLog();
      fetchRebalancePreview();
    });
  }
  fetchLiveMetrics();
  // Export ZIP button listener
  const exportZipBtn = document.getElementById('exportZipBtn');
  if (exportZipBtn) {
    exportZipBtn.addEventListener('click', () => {
      const token = localStorage.getItem('API_AUTH_TOKEN') || window.API_AUTH_TOKEN || DEFAULT_AUTH_TOKEN;
      window.location.href = `${API_BASE}/tax/export/itr2/zip?fy=${state.currentFy}&token=${encodeURIComponent(token)}`;
      showToast(`Generating ITR-2 CSV Bundle (.zip) for ${state.currentFy}...`, 'success');
    });
  }
  // Rebalance Slider listener
  const slider = document.getElementById('rebalanceSlider');
  const sliderVal = document.getElementById('rebalanceSliderVal');
  if (slider && sliderVal) {
    slider.addEventListener('input', () => {
      const val = parseInt(slider.value) || 100000;
      sliderVal.textContent = formatINR(val);
      fetchRebalancePreview(val);
    });
  }
  // File Upload listener
  const fileInput = document.getElementById('fileUploadInput');
  if (fileInput) {
    fileInput.addEventListener('change', async (e) => {
      const file = e.target.files[0];
      if (!file) return;
      let password = '';
      if (file.name.toLowerCase().endsWith('.pdf')) {
        password = prompt("Enter password for encrypted CAS PDF (usually PAN in lowercase or PAN + DOB):") || '';
      }
      const formData = new FormData();
      formData.append('file', file);
      if (password) {
        formData.append('password', password);
      }
      const uploadBtn = document.querySelector('.upload-btn');
      try {
        if (uploadBtn) uploadBtn.textContent = 'Parsing Statement...';
        const res = await fetch(`${API_BASE}/statements/upload`, {
          method: 'POST',
          headers: getAuthHeaders(),
          body: formData
        });
        const result = await res.json().catch(() => null);
        if (res.ok && result && (result.status === 'SUCCESS' || Array.isArray(result) || result.eventsIngested !== undefined)) {
          const count = Array.isArray(result) ? result.length : (result.eventsIngested || 0);
          showToast(`Statement ingested successfully (${count} events).`, 'success');
          fetchLiveMetrics();
        } else {
          const msg = (result && result.message) ? result.message : 'Statement parsing failed or unauthorized.';
          showToast(msg, 'error');
        }
      } catch (err) {
        showToast(`Upload error: ${err.message}`, 'error');
      } finally {
        if (uploadBtn) uploadBtn.textContent = 'Upload CAS PDF / CSV';
        fileInput.value = '';
      }
    });
  }
});
async function fetchLiveMetrics() {
  try {
    const summary = await fetchJson(`${API_BASE}/portfolio/summary`).catch(() => null);
    if (summary) {
      updatePortfolioSummary(summary);
    }
    fetchTaxMetrics();
    const allocations = await fetchJson(`${API_BASE}/portfolio/allocation`).catch(() => null);
    if (allocations) {
      renderAllocationChart(allocations);
    }
    const catAllocations = await fetchJson(`${API_BASE}/portfolio/category-allocation`).catch(() => null);
    if (catAllocations) {
      renderCategoryChart(catAllocations);
    }
    const holdings = await fetchJson(`${API_BASE}/portfolio/holdings`).catch(() => null);
    if (holdings) {
      renderHoldingsTable(holdings);
    }
    fetchDecisionRadar();
    fetchRealizedLog();
    fetchGoalSummary();
    fetchFireSummary();
    fetchBucketRebalance();
    fetchConsolidationPreviewData();
    const slider = document.getElementById('rebalanceSlider');
    const amt = slider ? slider.value : 100000;
    fetchRebalancePreview(amt);
  } catch (err) {
    console.log('Portfolio OS API starting up, retrying...');
  }
}
// Global debounced resize listener for ECharts
let resizeTimer = null;
window.addEventListener('resize', () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    if (state.charts.allocChart) state.charts.allocChart.resize();
    if (state.charts.categoryChart) state.charts.categoryChart.resize();
  }, 150);
});
```

## File: src/main/resources/static/src/style.css
```css
:root {
  --bg-obsidian: #050811;
  --card-bg: #0c101c;
  --card-border: rgba(255, 255, 255, 0.08);
  --text-main: #f8fafc;
  --text-muted: #64748b;
  --cyan-bright: #06b6d4;
  --purple-accent: #8b5cf6;
  --amber-warn: #f59e0b;
  --green-positive: #10b981;
  --red-negative: #ef4444;
}
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}
body.bg-obsidian {
  background-color: var(--bg-obsidian);
  color: var(--text-main);
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  min-height: 100vh;
  padding: 24px;
  -webkit-font-smoothing: antialiased;
  position: relative;
  overflow-x: hidden;
}
/* Ambient Glow Spheres */
.ambient-glow {
  display: block;
  position: fixed;
  border-radius: 50%;
  filter: blur(120px);
  pointer-events: none;
  z-index: 0;
}
.glow-1 {
  width: 500px;
  height: 500px;
  background: rgba(6, 182, 212, 0.08);
  top: -150px;
  left: -150px;
}
.glow-2 {
  width: 450px;
  height: 450px;
  background: rgba(139, 92, 246, 0.08);
  bottom: -150px;
  right: -150px;
}
.container {
  max-width: 1440px;
  margin: 0 auto;
  position: relative;
  z-index: 1;
}
/* Tab Navigation Bar */
.tab-nav {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--card-border);
  padding: 6px;
  border-radius: 12px;
}
.tab-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  font-family: 'Outfit', sans-serif;
  font-size: 13px;
  font-weight: 600;
  padding: 10px 18px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.tab-btn:hover {
  color: var(--text-main);
  background: rgba(255, 255, 255, 0.05);
}
.tab-btn.active {
  color: var(--cyan-bright);
  background: rgba(6, 182, 212, 0.12);
  border: 1px solid rgba(6, 182, 212, 0.3);
  box-shadow: 0 0 12px rgba(6, 182, 212, 0.15);
}
.tab-content {
  display: none;
}
.tab-content.active {
  display: block;
  animation: fadeIn 0.25s ease-in-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}
/* Header & Brand Layout */
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--card-border);
}
.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}
.logo-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, var(--cyan-bright), var(--purple-accent));
  border-radius: 10px;
  box-shadow: 0 0 20px rgba(6, 182, 212, 0.35);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}
.brand-title-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.brand-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.brand-title {
  font-family: 'Outfit', sans-serif;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.5px;
}
.v2-tag {
  font-size: 10px;
  font-weight: 700;
  color: #c084fc;
  background: rgba(139, 92, 246, 0.2);
  border: 1px solid rgba(139, 92, 246, 0.4);
  padding: 2px 6px;
  border-radius: 4px;
}
.fy-selector-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 600;
}
.fy-select {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--card-border);
  color: var(--cyan-bright);
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 6px;
  cursor: pointer;
  outline: none;
}
.header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: rgba(6, 182, 212, 0.1);
  border: 1px solid rgba(6, 182, 212, 0.3);
  color: var(--cyan-bright);
  padding: 8px 14px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}
.upload-btn:hover {
  background: rgba(6, 182, 212, 0.2);
  box-shadow: 0 0 12px rgba(6, 182, 212, 0.25);
}
.export-btn {
  background: rgba(139, 92, 246, 0.15);
  border: 1px solid rgba(139, 92, 246, 0.4);
  color: #c084fc;
}
.export-btn:hover {
  background: rgba(139, 92, 246, 0.25);
  box-shadow: 0 0 14px rgba(139, 92, 246, 0.3);
}
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--card-border);
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
}
.status-dot {
  width: 8px;
  height: 8px;
  background-color: var(--green-positive);
  border-radius: 50%;
  box-shadow: 0 0 8px var(--green-positive);
}
/* Top Metrics Cards Row */
.top-metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}
.glass-card {
  background: var(--card-bg);
  backdrop-filter: blur(16px);
  border: 1px solid var(--card-border);
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}
.metric-box {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.metric-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}
.metric-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.2;
}
.font-mono {
  font-family: 'JetBrains Mono', monospace;
}
.highlight-cyan {
  color: var(--cyan-bright);
}
.metric-delta.positive {
  color: var(--green-positive);
  font-size: 12px;
  margin-top: 6px;
  font-weight: 500;
}
.metric-delta.negative {
  color: var(--red-negative);
  font-size: 12px;
  margin-top: 6px;
  font-weight: 500;
}
.metric-subtext {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 6px;
}
/* Exemption Meter */
.exemption-box .sub-limit {
  font-size: 14px;
  color: var(--text-muted);
}
.progress-track {
  width: 100%;
  height: 8px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 4px;
  margin: 12px 0 8px;
  overflow: hidden;
}
.progress-fill-gradient {
  height: 100%;
  background: linear-gradient(90deg, var(--cyan-bright), var(--purple-accent));
  border-radius: 4px;
  transition: width 0.4s ease;
}
.meter-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-muted);
}
/* 12-Column Dashboard Grid */
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 16px;
}
.col-12 {
  grid-column: span 12;
}
.col-6 {
  grid-column: span 6;
}
@media (max-width: 992px) {
  .col-6 {
    grid-column: span 12;
  }
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.card-header h2 {
  font-family: 'Outfit', sans-serif;
  font-size: 16px;
  font-weight: 600;
}
.live-tag {
  font-size: 10px;
  font-weight: 700;
  color: var(--cyan-bright);
  background: rgba(6, 182, 212, 0.1);
  padding: 3px 8px;
  border-radius: 4px;
  letter-spacing: 0.5px;
}
.canvas-wrapper {
  height: 280px;
  width: 100%;
}
.canvas-wrapper-small {
  height: 280px;
  width: 100%;
}
/* Rebalancing Calculator Widget */
.rebalance-controls {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.input-lbl {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 600;
}
.slider-box {
  display: flex;
  align-items: center;
  gap: 16px;
}
.slider-box input[type="range"] {
  flex: 1;
  accent-color: var(--cyan-bright);
  cursor: pointer;
}
.slider-val {
  font-size: 15px;
  font-weight: 700;
  color: var(--cyan-bright);
  min-width: 100px;
  text-align: right;
}
.rebalance-summary-box {
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid var(--card-border);
  padding: 14px;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.reb-stat {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.reb-stat .lbl {
  color: var(--text-muted);
}
/* Schedule FA Checklist */
.compliance-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.compliance-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid var(--card-border);
  padding: 12px 14px;
  border-radius: 10px;
}
.compliance-item.valid .check-icon {
  color: var(--green-positive);
  font-weight: 700;
  font-size: 14px;
  margin-top: 1px;
}
.comp-title {
  font-size: 13px;
  font-weight: 600;
}
.comp-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}
/* Decision Radar */
.radar-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.radar-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--card-border);
  padding: 12px 16px;
  border-radius: 10px;
}
.radar-card.warning-border { border-left: 4px solid var(--amber-warn); }
.radar-card.info-border { border-left: 4px solid var(--cyan-bright); }
.radar-card.maturation-border { border-left: 4px solid var(--purple-accent); }
.radar-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
}
.radar-icon.warning { background: rgba(245, 158, 11, 0.15); color: var(--amber-warn); }
.radar-icon.info { background: rgba(6, 182, 212, 0.15); color: var(--cyan-bright); }
.radar-icon.maturation { background: rgba(139, 92, 246, 0.15); color: var(--purple-accent); }
.radar-content {
  flex: 1;
  margin: 0 16px;
}
.radar-title {
  font-size: 13px;
  font-weight: 600;
}
.radar-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}
.days-badge {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--cyan-bright);
}
/* Data Tables */
.table-container {
  overflow-x: auto;
  max-height: 480px;
  width: 100%;
  contain: content;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.data-table th {
  position: sticky;
  top: 0;
  z-index: 10;
  background-color: var(--card-bg);
  text-align: left;
  padding: 12px 14px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
  border-bottom: 1px solid var(--card-border);
  white-space: nowrap;
}
.data-table td {
  padding: 12px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}
.data-table tr.holding-row {
  cursor: pointer;
  transition: background 0.15s ease;
}
.data-table tr.holding-row:hover {
  background: rgba(255, 255, 255, 0.04);
}
.lot-expansion-td {
  background: rgba(0, 0, 0, 0.3);
  padding: 16px !important;
}
.lot-subtable {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.lot-subtable th {
  font-size: 10px;
  color: var(--cyan-bright);
  padding: 6px 10px;
}
.lot-subtable td {
  padding: 6px 10px;
  font-family: 'JetBrains Mono', monospace;
}
.pill-btn {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--card-border);
  color: var(--text-main);
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}
.cat-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
}
.cat-EQUITY { background: rgba(16, 185, 129, 0.15); color: var(--green-positive); }
.cat-DEBT_SPECIFIED_50AA { background: rgba(245, 158, 11, 0.15); color: var(--amber-warn); }
.cat-GOLD_SILVER { background: rgba(234, 179, 8, 0.15); color: #eab308; }
.cat-INTERNATIONAL { background: rgba(139, 92, 246, 0.15); color: var(--purple-accent); }
.cat-SGB { background: rgba(6, 182, 212, 0.15); color: var(--cyan-bright); }
/* Toast Notification Stack */
.toast-stack {
  position: fixed;
  bottom: 24px;
  right: 24px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  z-index: 9999;
}
.toast {
  background: rgba(15, 23, 42, 0.95);
  border: 1px solid var(--card-border);
  padding: 12px 18px;
  border-radius: 8px;
  font-size: 13px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.5);
  backdrop-filter: blur(8px);
}
.toast.success { border-left: 4px solid var(--green-positive); }
.toast.error { border-left: 4px solid var(--red-negative); }
/* FIRE Tracker */
.fire-card {
  border-top: 3px solid var(--cyan-bright);
}
.title-with-badge {
  display: flex;
  align-items: center;
  gap: 12px;
}
.fire-status-pill {
  font-size: 11px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 20px;
  text-transform: uppercase;
}
.fire-status-pill.on-track {
  background: rgba(16, 185, 129, 0.2);
  color: var(--green-positive);
  border: 1px solid var(--green-positive);
}
.fire-status-pill.short {
  background: rgba(239, 68, 68, 0.2);
  color: var(--red-negative);
  border: 1px solid var(--red-negative);
}
.fire-metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}
.fire-stat-box {
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid var(--card-border);
  padding: 14px;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.fire-stat-box .lbl {
  font-size: 11px;
  color: var(--text-muted);
  text-transform: uppercase;
}
.fire-stat-box .val {
  font-size: 20px;
  font-weight: 700;
}
.fire-stat-box .sub {
  font-size: 11px;
  color: var(--text-muted);
}
```

## File: src/main/resources/static/index.html
```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Portfolio OS - Web Cockpit (v3.0)</title>
  <link rel="stylesheet" href="./src/style.css">
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500;600;700&family=Outfit:wght@500;600;700&display=swap" rel="stylesheet">
  <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
</head>
<body class="bg-obsidian">
  <!-- Glowing Background Ambient Spheres -->
  <div class="ambient-glow glow-1"></div>
  <div class="ambient-glow glow-2"></div>
  <div class="toast-stack" id="toastStack" aria-live="polite"></div>
  <div id="app" class="container">
    <!-- Clean Minimalist Header -->
    <header class="header">
      <div class="brand">
        <div class="logo-icon">🚀</div>
        <div class="brand-title-group">
          <div class="brand-title-row">
            <h1 class="brand-title">Portfolio OS</h1>
            <span class="v2-tag">v3.0 Vapor</span>
          </div>
          <div class="fy-selector-row">
            <span>PERIOD:</span>
            <select class="fy-select" id="fySelect">
              <option value="2024-25">FY 2024-25</option>
              <option value="2025-26">FY 2025-26</option>
              <option value="2026-27" selected>FY 2026-27</option>
            </select>
          </div>
        </div>
      </div>
      <div class="header-actions">
        <button id="exportZipBtn" class="upload-btn export-btn">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
          Export ITR-2 Bundle (.zip)
        </button>
        <label for="fileUploadInput" class="upload-btn">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
          Upload CAS PDF / CSV
        </label>
        <input type="file" id="fileUploadInput" accept=".pdf,.csv" style="display: none;">
        <div class="status-pill">
          <span class="status-dot"></span> SHA-256 Engine Active
        </div>
      </div>
    </header>
    <!-- Top Key Metrics Row -->
    <section class="top-metrics-grid">
      <div class="glass-card metric-box">
        <div class="metric-label">NET WORTH</div>
        <div class="metric-value font-mono skeleton net-worth-val">₹ --,--,---</div>
        <div class="metric-delta neutral net-worth-gain">Unrealized gain: --</div>
        <div class="metric-subtext net-worth-sub">Active Holdings: -- Schemes</div>
      </div>
      <div class="glass-card metric-box">
        <div class="metric-label">UNALLOCATED CASH</div>
        <div class="metric-value font-mono highlight-cyan skeleton idle-cash-val">₹ --,--,---</div>
        <div class="metric-subtext">Sitting idle across Liquid & Bank</div>
      </div>
      <div class="glass-card metric-box exemption-box">
        <div class="metric-label">LTCG EXEMPTION (SEC 112A)</div>
        <div class="metric-value font-mono skeleton ltcg-meter-val">₹ 0 <span class="sub-limit">/ 1.25L</span></div>
        <div class="progress-track">
          <div class="progress-fill-gradient" style="width: 0%;"></div>
        </div>
        <div class="meter-meta">
          <span class="pct-used">0% Used</span>
          <span class="remaining">₹ 1,25,000 Available</span>
        </div>
      </div>
      <div class="glass-card metric-box">
        <div class="metric-label">PORTFOLIO XIRR</div>
        <div class="metric-value font-mono highlight-cyan skeleton xirr-val">--%</div>
        <div class="metric-subtext">Money-Weighted XIRR</div>
      </div>
    </section>
    <!-- Minimalist Tab Navigation Bar -->
    <nav class="tab-nav">
      <button class="tab-btn active" data-tab="overview">📊 Overview & Allocation</button>
      <button class="tab-btn" data-tab="tax">⚡ Tax Optimization & Audit</button>
      <button class="tab-btn" data-tab="fire">🎯 FIRE & Rebalancing</button>
    </nav>
    <!-- TAB 1: Overview & Allocation -->
    <main class="tab-content active" id="tab-overview">
      <div class="dashboard-grid">
        <!-- Fund Allocation Chart -->
        <div class="glass-card col-6">
          <div class="card-header">
            <h2>Fund Asset Allocation</h2>
            <span class="live-tag">BY SCHEME</span>
          </div>
          <div class="canvas-wrapper-small" id="allocationChart" style="height: 280px; width: 100%;"></div>
        </div>
        <!-- Risk Category Allocation Chart -->
        <div class="glass-card col-6">
          <div class="card-header">
            <h2>Risk Exposure</h2>
            <span class="live-tag">BY CATEGORY</span>
          </div>
          <div class="canvas-wrapper-small" id="categoryChart" style="height: 280px; width: 100%;"></div>
        </div>
        <!-- Open Holdings Table -->
        <div class="glass-card col-12">
          <div class="card-header">
            <h2>Open Holdings & FIFO Lots</h2>
            <span class="live-tag">LEDGER DRILL-DOWN</span>
          </div>
          <div class="table-container">
            <table class="data-table" id="holdingsTable">
              <thead>
                <tr>
                  <th>Scheme Name</th>
                  <th>Category</th>
                  <th>Invested</th>
                  <th>Current Value</th>
                  <th>Unrealized Gain</th>
                  <th>Allocation %</th>
                  <th>Open Lots</th>
                </tr>
              </thead>
              <tbody>
                <tr><td colspan="7" class="loading-td">Loading holdings...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </main>
    <!-- TAB 2: Tax Optimization & Audit -->
    <main class="tab-content" id="tab-tax">
      <div class="dashboard-grid">
        <!-- AI Decision Radar -->
        <div class="glass-card col-12">
          <div class="card-header">
            <h2>Tax & Strategy Decision Radar</h2>
            <span class="live-tag">AI ADVISOR</span>
          </div>
          <div class="radar-list">
            <div class="radar-empty-state">Scanning open lots for tax-loss harvesting and LTCG maturation opportunities...</div>
          </div>
        </div>
        <!-- Schedule FA Pre-Flight Checklist -->
        <div class="glass-card col-6">
          <div class="card-header">
            <h2>Schedule FA Compliance</h2>
            <span class="live-tag">FOREIGN ASSETS</span>
          </div>
          <div class="compliance-list">
            <div class="compliance-item valid">
              <span class="check-icon">✓</span>
              <div class="comp-text">
                <div class="comp-title">Foreign Entity Identification & Address</div>
                <div class="comp-desc">International ETF ISINs mapped to US jurisdiction.</div>
              </div>
            </div>
            <div class="compliance-item valid">
              <span class="check-icon">✓</span>
              <div class="comp-text">
                <div class="comp-title">Peak Intra-Year Valuation INR</div>
                <div class="comp-desc">SBI Telegraphic Transfer conversion applied to peak balances.</div>
              </div>
            </div>
          </div>
        </div>
        <!-- Realized Disposals Audit Log -->
        <div class="glass-card col-12">
          <div class="card-header">
            <h2>Realized Disposals Audit Log</h2>
            <span class="live-tag">SELECTED FY</span>
          </div>
          <div class="table-container">
            <table class="data-table" id="realizedLogTable">
              <thead>
                <tr>
                  <th>Disposal Date</th>
                  <th>Acquisition Date</th>
                  <th>Scheme Name</th>
                  <th>Units</th>
                  <th>Proceeds</th>
                  <th>Cost Basis</th>
                  <th>Realized Gain</th>
                  <th>Tax Term</th>
                </tr>
              </thead>
              <tbody>
                <tr><td colspan="8" class="loading-td">Loading realized log...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </main>
    <!-- TAB 3: FIRE & Rebalancing -->
    <main class="tab-content" id="tab-fire">
      <div class="dashboard-grid">
        <!-- FIRE Tracker Module -->
        <div class="glass-card col-12 fire-card">
          <div class="card-header">
            <div class="title-with-badge">
              <h2>FIRE Tracker & Decumulation Runway</h2>
              <span class="fire-status-pill on-track" id="fireStatusPill">ON TRACK</span>
            </div>
            <div class="live-tag font-mono" id="fireScenarioLabel">Scenario: Primary Target</div>
          </div>
          <div class="fire-metrics-grid">
            <div class="fire-stat-box">
              <span class="lbl">Investable Net Worth</span>
              <strong class="val font-mono highlight-cyan" id="fireInvestableNw">₹ --</strong>
              <span class="sub font-mono">Liquid Investments</span>
            </div>
            <div class="fire-stat-box">
              <span class="lbl">Required Corpus (Age 45)</span>
              <strong class="val font-mono" id="fireRequiredCorpus">₹ --</strong>
              <span class="sub font-mono" id="fireExpenseSub">3.0% SWR @ ₹60k/mo</span>
            </div>
            <div class="fire-stat-box">
              <span class="lbl">Projected Corpus @ 45</span>
              <strong class="val font-mono positive" id="fireProjectedCorpus">₹ --</strong>
              <span class="sub font-mono" id="fireYearsSub">6% Real Return</span>
            </div>
          </div>
        </div>
        <!-- Tax-Aware Rebalancing Predictor -->
        <div class="glass-card col-6">
          <div class="card-header">
            <h2>Tax-Aware Rebalancing Predictor</h2>
            <span class="live-tag">TAX DRAG CALCULATOR</span>
          </div>
          <div class="rebalance-controls">
            <label class="input-lbl">Target Redemption Amount (INR):</label>
            <div class="slider-box">
              <input type="range" id="rebalanceSlider" min="25000" max="1000000" step="25000" value="100000">
              <span class="font-mono slider-val" id="rebalanceSliderVal">₹ 1,00,000</span>
            </div>
            <div class="rebalance-summary-box">
              <div class="reb-stat"><span class="lbl">Predicted Tax Drag:</span> <strong class="val font-mono highlight-cyan" id="rebTaxDrag">₹ 0</strong></div>
              <div class="reb-stat"><span class="lbl">Effective Tax Rate:</span> <strong class="val font-mono" id="rebEffRate">0.00%</strong></div>
              <div class="reb-stat"><span class="lbl">LTCG Tax-Free Harvested:</span> <strong class="val font-mono" id="rebLtcgHarvested">₹ 0</strong></div>
            </div>
          </div>
        </div>
        <!-- Bucket Rebalancing -->
        <div class="glass-card col-6">
          <div class="card-header">
            <h2>Flat Bucket Rebalancer</h2>
            <span class="live-tag" id="drawdownTag">Nifty 500: Normal</span>
          </div>
          <div class="bucket-grid" id="bucketGrid">
            <!-- Rendered dynamically -->
          </div>
        </div>
        <!-- Disciplined Consolidation -->
        <div class="glass-card col-12">
          <div class="card-header">
            <h2>Disciplined Consolidation Plan</h2>
            <span class="live-tag" id="consolidationWindowBadge">March / September Window</span>
          </div>
          <div id="consolidationPlanContainer">
            <!-- Rendered dynamically -->
          </div>
        </div>
      </div>
    </main>
  </div>
  <script type="module" src="./src/app.js"></script>
</body>
</html>
```

## File: src/main/resources/application.yml
```yaml
server:
  port: 8080
  address: 0.0.0.0
spring:
  application:
    name: portfolio-os-core
  threads:
    virtual:
      enabled: true
  mvc:
    static-path-pattern: /**
  resources:
    static-locations: classpath:/static/
logging:
  level:
    root: INFO
    com.portfolioos.core: DEBUG
    org.springframework.web: INFO
```

## File: build.gradle
```
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.5'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.portfolioos'
version = '3.0.0-SNAPSHOT'

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.xerial:sqlite-jdbc:3.45.2.0'
    implementation 'org.duckdb:duckdb_jdbc:0.10.1'
    implementation 'org.apache.arrow:arrow-flight:15.0.0'
    implementation 'org.apache.arrow:arrow-vector:15.0.0'
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.withType(JavaCompile) {
    options.compilerArgs += ['--enable-preview']
}

test {
    useJUnitPlatform()
}
```

## File: Dockerfile
```dockerfile
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

## File: pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    <groupId>com.portfolioos</groupId>
    <artifactId>core-node</artifactId>
    <version>3.0.0</version>
    <name>core-node</name>
    <description>Portfolio OS Core Ledger Node (2026 rebuild)</description>
    <properties>
        <java.version>21</java.version>
        <arrow.version>15.0.0</arrow.version>
    </properties>
    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jdbc</artifactId>
        </dependency>
        <!-- Databases -->
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>3.45.1.0</version>
        </dependency>
        <dependency>
            <groupId>org.duckdb</groupId>
            <artifactId>duckdb_jdbc</artifactId>
            <version>0.10.0</version>
        </dependency>
        <!-- YAML Config Loader -->
        <dependency>
            <groupId>com.fasterxml.jackson.dataformat</groupId>
            <artifactId>jackson-dataformat-yaml</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
        <!-- Apache Arrow Flight RPC -->
        <dependency>
            <groupId>org.apache.arrow</groupId>
            <artifactId>arrow-vector</artifactId>
            <version>${arrow.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.arrow</groupId>
            <artifactId>flight-core</artifactId>
            <version>${arrow.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.arrow</groupId>
            <artifactId>flight-grpc</artifactId>
            <version>${arrow.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.arrow</groupId>
            <artifactId>arrow-memory-netty</artifactId>
            <version>${arrow.version}</version>
            <scope>runtime</scope>
        </dependency>
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>21</release>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## File: settings.gradle
```
rootProject.name = 'core-node'
```
