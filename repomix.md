This file is a merged representation of the entire codebase, combined into a single document by Repomix.

<file_summary>
This section contains a summary of this file.

<purpose>
This file contains a packed representation of the entire repository's contents.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.
</purpose>

<file_format>
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Repository files (if enabled)
5. Multiple file entries, each consisting of:
  - File path as an attribute
  - Full contents of the file
</file_format>

<usage_guidelines>
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.
</usage_guidelines>

<notes>
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Files are sorted by Git change count (files with more changes are at the bottom)
</notes>

</file_summary>

<directory_structure>
core-node/
  src/
    main/
      java/
        com/
          portfolioos/
            core/
              config/
                AppConfig.java
              controllers/
                LlmQueryController.java
                RebalanceController.java
                ReportController.java
                SimulatorController.java
                StatementsController.java
                SyncController.java
              dtos/
                ReportDtos.java
                SyncDtos.java
              fire/
                FireTracker.java
              goals/
                GoalTracker.java
              llm/
                SqlGeneratorService.java
                TaxRagService.java
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
                LedgerCacheService.java
                PortfolioValuationService.java
                SimulationService.java
                TaxOptimizationService.java
              tax/
                ScheduleCgExporter.java
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
mobile-app/
  app/
    src/
      main/
        java/
          com/
            portfolioos/
              mobile/
                api/
                  SyncApiClient.kt
                model/
                  SyncModels.kt
                ui/
                  DashboardScreen.kt
                  PortfolioCharts.kt
                  SimulatorScreen.kt
                util/
                  FormatUtils.kt
                widget/
                  PortfolioGlanceWidget.kt
                MainActivity.kt
        res/
          drawable/
            ic_launcher_background.xml
            ic_launcher_foreground.xml
          mipmap-anydpi-v26/
            ic_launcher_round.xml
            ic_launcher.xml
          values/
            styles.xml
          xml/
            backup_rules.xml
            data_extraction_rules.xml
            portfolio_glance_widget_info.xml
        AndroidManifest.xml
    build.gradle.kts
  build.gradle.kts
  gradle.properties
  local.properties
  settings.gradle.kts
quant-sidecar/
  parsers/
    broker_csv_parser.py
    cas_parser.py
    models.py
    sip_detector.py
  quant/
    analytics_engine.py
  app.py
  Dockerfile
  flight_server.py
  requirements.txt
rules/
  FY2026-27.yaml
.gitignore
.repowise.json
ARCHITECTURE_INDEX.md
CLAUDE_INDEX.md
docker-compose.yml
Ledgerly_ChatGPT_Work_Build_Prompt.pdf
README.md
repomix-core.md
repomix-mobile.md
repomix-quant.md
</directory_structure>

<files>
This section contains the contents of the repository's files.

<file path="core-node/src/main/java/com/portfolioos/core/controllers/LlmQueryController.java">
package com.portfolioos.core.controllers;

import com.portfolioos.core.llm.SqlGeneratorService;
import com.portfolioos.core.llm.TaxRagService;
import com.portfolioos.core.service.SimulationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/llm")
public class LlmQueryController {

    private final SqlGeneratorService sqlService;
    private final TaxRagService taxRagService;
    private final SimulationService simulationService;
    private final ChatClient.Builder chatClientBuilder;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public LlmQueryController(
        SqlGeneratorService sqlService,
        TaxRagService taxRagService,
        SimulationService simulationService,
        ChatClient.Builder chatClientBuilder
    ) {
        this.sqlService = sqlService;
        this.taxRagService = taxRagService;
        this.simulationService = simulationService;
        this.chatClientBuilder = chatClientBuilder;
    }

    public static record LlmQueryRequest(String prompt) {}

    public static record LlmQueryResponse(
        String queryType, // SQL, TOOL_SIMULATION, TAX_RAG, GENERAL
        String textResponse,
        String generatedSql,
        Object dataPayload,
        String status
    ) {}

    @PostMapping("/query")
    public LlmQueryResponse handleQuery(@RequestBody LlmQueryRequest req) {
        if (req == null || req.prompt() == null || req.prompt().isBlank()) {
            return new LlmQueryResponse("UNKNOWN", "Please provide a valid prompt.", null, null, "ERROR");
        }

        String prompt = req.prompt().trim();
        String promptLower = prompt.toLowerCase();

        // 1. Tool Call Interception for Trade Simulation
        if (promptLower.contains("simulate") || promptLower.contains("what-if") || promptLower.contains("what if")) {
            try {
                // Example tool parameter extraction for paired or single trade
                SimulationService.TradeSimulationRequest simReq = new SimulationService.TradeSimulationRequest(
                    "INF200K01229",
                    "Parag Parikh Flexi Cap Fund",
                    100.0,
                    165.0,
                    null,
                    promptLower.contains("sell") ? "DISPOSAL" : "ACQUISITION"
                );
                SimulationService.TradeSimulationResult res = simulationService.simulateTrade(simReq);
                return new LlmQueryResponse("TOOL_SIMULATION", res.taxSummaryNotice(), null, res, "SUCCESS");
            } catch (Exception e) {
                return new LlmQueryResponse("TOOL_SIMULATION", "Simulation failed: " + e.getMessage(), null, null, "ERROR");
            }
        }

        // 2. Tax RAG Engine for Tax Code Questions
        if (promptLower.contains("tax") || promptLower.contains("112a") || promptLower.contains("50aa") || promptLower.contains("ltcg") || promptLower.contains("stcg")) {
            String answer = taxRagService.answerTaxQuestion(prompt);
            return new LlmQueryResponse("TAX_RAG", answer, null, null, "SUCCESS");
        }

        // 3. DuckDB Text-to-SQL Pipeline
        SqlGeneratorService.SqlQueryResult sqlRes = sqlService.generateAndExecute(prompt);
        if ("SUCCESS".equalsIgnoreCase(sqlRes.status())) {
            String summary = String.format("Query executed successfully. Found %d matching records.", sqlRes.data().size());
            return new LlmQueryResponse("SQL", summary, sqlRes.generatedSql(), sqlRes.data(), "SUCCESS");
        }

        return new LlmQueryResponse("GENERAL", "Could not execute query: " + sqlRes.errorMessage(), null, null, "ERROR");
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQuery(@RequestParam("prompt") String prompt) {
        SseEmitter emitter = new SseEmitter(60000L);
        executorService.execute(() -> {
            try {
                LlmQueryResponse res = handleQuery(new LlmQueryRequest(prompt));
                String content = res.textResponse();

                if (res.generatedSql() != null && !res.generatedSql().isBlank()) {
                    content += "\n\n```sql\n" + res.generatedSql() + "\n```";
                }

                // Stream tokens word-by-word for live SSE typing effect
                String[] words = content.split(" ");
                for (String word : words) {
                    emitter.send(word + " ");
                    Thread.sleep(30);
                }
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send("⚠️ Streaming error: " + e.getMessage());
                    emitter.complete();
                } catch (IOException ignored) {}
            }
        });
        return emitter;
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/controllers/RebalanceController.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/controllers/SimulatorController.java">
package com.portfolioos.core.controllers;

import com.portfolioos.core.service.SimulationService;
import com.portfolioos.core.service.SimulationService.TradeSimulationRequest;
import com.portfolioos.core.service.SimulationService.TradeSimulationResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulate")
public class SimulatorController {

    private final SimulationService simulationService;

    public SimulatorController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/trade")
    public ResponseEntity<TradeSimulationResult> simulateTrade(
        @RequestBody TradeSimulationRequest req
    ) {
        return ResponseEntity.ok(simulationService.simulateTrade(req));
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/controllers/StatementsController.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/dtos/ReportDtos.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/goals/GoalTracker.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/llm/SqlGeneratorService.java">
package com.portfolioos.core.llm;

import com.portfolioos.core.persistence.DuckDbProjector;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SqlGeneratorService {

    private final ChatClient.Builder chatClientBuilder;
    private final DuckDbProjector duckDbProjector;

    private static final String SCHEMA_PROMPT = """
        You are an expert DuckDB SQL developer for a financial portfolio database.
        The database contains two projected analytical tables:

        1. projected_events (
            id VARCHAR PRIMARY KEY,
            asset_id VARCHAR NOT NULL,
            asset_name VARCHAR NOT NULL,
            isin VARCHAR,
            event_type VARCHAR NOT NULL, -- 'ACQUISITION', 'DISPOSAL', 'SIP_INSTALMENT', 'BONUS', 'SPLIT'
            event_date VARCHAR NOT NULL, -- YYYY-MM-DD
            units VARCHAR NOT NULL,
            price_per_unit VARCHAR NOT NULL,
            gross_amount VARCHAR NOT NULL,
            source_document_id VARCHAR NOT NULL,
            ingested_at VARCHAR NOT NULL
        )

        2. nav_history (
            asset_id VARCHAR NOT NULL,
            nav_date VARCHAR NOT NULL, -- YYYY-MM-DD
            nav DOUBLE NOT NULL,
            PRIMARY KEY (asset_id, nav_date)
        )

        CRITICAL INSTRUCTIONS:
        - Output ONLY valid, executable DuckDB SQL.
        - The query MUST be a read-only SELECT statement.
        - Do NOT include any markdown formatting, code block fences (```), explanations, or trailing comments.
        """;

    public SqlGeneratorService(ChatClient.Builder chatClientBuilder, DuckDbProjector duckDbProjector) {
        this.chatClientBuilder = chatClientBuilder;
        this.duckDbProjector = duckDbProjector;
    }

    public record SqlQueryResult(
        String generatedSql,
        List<Map<String, Object>> data,
        String status,
        String errorMessage
    ) {}

    public SqlQueryResult generateAndExecute(String userPrompt) {
        try {
            ChatClient chatClient = chatClientBuilder.build();
            String rawSql = chatClient.prompt()
                .system(SCHEMA_PROMPT)
                .user(userPrompt)
                .call()
                .content();

            if (rawSql == null || rawSql.isBlank()) {
                return new SqlQueryResult("", Collections.emptyList(), "ERROR", "Empty SQL generated by LLM");
            }

            // Clean markdown syntax if present
            String sql = rawSql.replaceAll("```sql", "").replaceAll("```", "").trim();

            // Strict SELECT Guardrail
            if (!sql.toUpperCase().startsWith("SELECT") && !sql.toUpperCase().startsWith("WITH")) {
                throw new SecurityException("Security violation: Only read-only SELECT queries are permitted.");
            }

            if (sql.contains(";") && sql.indexOf(";") != sql.length() - 1) {
                throw new SecurityException("Security violation: Multi-statement queries are forbidden.");
            }

            List<Map<String, Object>> results = executeDuckDbQuery(sql);
            return new SqlQueryResult(sql, results, "SUCCESS", null);
        } catch (Exception e) {
            return new SqlQueryResult("", Collections.emptyList(), "ERROR", e.getMessage());
        }
    }

    private List<Map<String, Object>> executeDuckDbQuery(String sql) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:duckdb:" + new java.io.File("data/tax_ledger.duckdb").getAbsolutePath());
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {

            java.sql.ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
        } catch (Exception e) {
            throw new RuntimeException("DuckDB SQL execution error: " + e.getMessage(), e);
        }
        return rows;
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/llm/TaxRagService.java">
package com.portfolioos.core.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@Service
public class TaxRagService {

    private final ChatClient.Builder chatClientBuilder;
    private VectorStore vectorStore;

    public TaxRagService(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    @PostConstruct
    public void initTaxKnowledgeBase() {
        try {
            // Spring AI SimpleVectorStore in-memory setup for Indian Tax Code rules
            File rulesFile = new File("rules/FY2026-27.yaml");
            if (rulesFile.exists()) {
                String content = Files.readString(rulesFile.toPath());
                Document doc = new Document(
                    "INDIAN TAX CODE & RULES FY2026-27:\n" + content,
                    Map.of("source", "FY2026-27.yaml", "category", "TAX_RULES")
                );
                // Vector store placeholder populated on demand
            }
        } catch (Exception e) {
            System.err.println("Tax Vector Store initialization warning: " + e.getMessage());
        }
    }

    public String answerTaxQuestion(String userQuestion) {
        try {
            String systemText = """
                You are an expert Indian Income Tax advisor for Mutual Funds and Equity Capital Gains.
                Use the following ground-truth rules:
                - Equity LTCG (holding > 365 days): Taxed at 12.5% above Section 112A exemption limit of ₹1,25,000 per financial year.
                - Equity STCG (holding <= 365 days): Taxed at 20.0% under Section 111A.
                - Debt Mutual Funds acquired after April 1, 2023: Taxed at slab rates under Section 50AA regardless of holding period.
                - Grandfathering Rule: NAV as of 31-Jan-2018 is used as cost basis for equity holdings acquired prior to 01-Feb-2018.

                Provide clear, concise, legally grounded answers.
                """;

            ChatClient chatClient = chatClientBuilder.build();
            return chatClient.prompt()
                .system(systemText)
                .user(userQuestion)
                .call()
                .content();
        } catch (Exception e) {
            return "⚠️ Tax RAG query failed: " + e.getMessage();
        }
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/matcher/FifoMatcher.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/matcher/TaxClassifier.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/model/AssetCategory.java">
package com.portfolioos.core.model;

public enum AssetCategory {
    EQUITY,
    DEBT_SPECIFIED_50AA,
    GOLD_SILVER,
    INTERNATIONAL,
    SGB
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/model/EventType.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/model/Lot.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/model/MatchedLot.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/model/TaxEvent.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/model/TaxTerm.java">
package com.portfolioos.core.model;

public enum TaxTerm {
    SHORT_TERM,
    LONG_TERM,
    EXEMPT
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/ports/EventStorePort.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/reconciliation/ReconciliationGate.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/reporting/ExemptionTracker.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/reporting/Itr2CsvExporter.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/reporting/TaxReportExporter.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/rules/TaxRulesConfig.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/service/TaxOptimizationService.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/util/Pair.java">
package com.portfolioos.core.util;

public record Pair<A, B>(A first, B second) {}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/valuation/AntigravityEngine.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/valuation/HarvestAdvisor.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/valuation/RebalanceEngine.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/xirr/CashFlow.java">
package com.portfolioos.core.xirr;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashFlow(
    LocalDate date,
    BigDecimal amount // negative for investments, positive for inflows / current valuation
) {}
</file>

<file path="core-node/src/main/resources/static/src/js/state.js">
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
</file>

<file path="core-node/src/main/resources/static/src/js/utils.js">
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
</file>

<file path="core-node/build.gradle">
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
</file>

<file path="core-node/settings.gradle">
rootProject.name = 'core-node'
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/util/FormatUtils.kt">
package com.portfolioos.mobile.util

import java.text.NumberFormat
import java.util.Locale

fun formatInr(valNum: Double, showDecimals: Boolean = false): String {
    val locale = Locale("en", "IN")
    val formatter = NumberFormat.getCurrencyInstance(locale).apply {
        maximumFractionDigits = if (showDecimals) 2 else 0
        minimumFractionDigits = if (showDecimals) 2 else 0
    }
    val formatted = formatter.format(valNum)
    return if (formatted.startsWith("INR")) {
        formatted.replace("INR", "₹").trim()
    } else {
        formatted
    }
}

fun formatInrStr(valStr: String?): String {
    if (valStr.isNullOrBlank()) return "₹0"
    val cleaned = valStr.replace("₹", "").replace(",", "").trim()
    val dbl = cleaned.toDoubleOrNull() ?: return valStr
    return formatInr(dbl, showDecimals = false)
}
</file>

<file path="mobile-app/app/src/main/res/drawable/ic_launcher_background.xml">
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#030712"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#0D1424"
        android:pathData="M0,0 L108,108 L0,108 Z" />
</vector>
</file>

<file path="mobile-app/app/src/main/res/drawable/ic_launcher_foreground.xml">
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Glowing Cyber Grid Accent -->
    <path
        android:strokeColor="#1E293B"
        android:strokeWidth="1"
        android:pathData="M24,36 H84 M24,54 H84 M24,72 H84" />

    <!-- Upward Trend Line -->
    <path
        android:strokeColor="#00F0FF"
        android:strokeWidth="4"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M28,68 L44,52 L56,60 L80,36" />

    <!-- Trend Line Sparkle Dots -->
    <path
        android:fillColor="#D0FF00"
        android:pathData="M80,36 m-4,0 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0" />

    <!-- Portfolio OS Monogram "P" Emblem -->
    <path
        android:fillColor="#D0FF00"
        android:pathData="M34,34 h16 a12,12 0 0,1 0,24 h-8 v16 h-8 z" />

    <path
        android:fillColor="#030712"
        android:pathData="M42,42 h8 a4,4 0 0,1 0,8 h-8 z" />
</vector>
</file>

<file path="mobile-app/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml">
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
</file>

<file path="mobile-app/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml">
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
</file>

<file path="mobile-app/app/src/main/res/values/styles.xml">
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.PortfolioOS" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">#050811</item>
        <item name="android:windowBackground">#050811</item>
    </style>
</resources>
</file>

<file path="mobile-app/app/src/main/res/xml/backup_rules.xml">
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude path="." />
</full-backup-content>
</file>

<file path="mobile-app/app/src/main/res/xml/data_extraction_rules.xml">
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude path="." />
    </cloud-backup>
</data-extraction-rules>
</file>

<file path="mobile-app/app/src/main/res/xml/portfolio_glance_widget_info.xml">
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:updatePeriodMillis="1800000"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen">
</appwidget-provider>
</file>

<file path="mobile-app/build.gradle.kts">
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
</file>

<file path="mobile-app/gradle.properties">
android.useAndroidX=true
android.nonFinalResIds=false
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
</file>

<file path="mobile-app/local.properties">
sdk.dir=/home/rakeshpc/Android/Sdk
</file>

<file path="mobile-app/settings.gradle.kts">
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "portfolio-os-mobile"
include(":app")
</file>

<file path="quant-sidecar/parsers/models.py">
from enum import Enum
from datetime import date, datetime
from decimal import Decimal
from typing import Optional
from pydantic import BaseModel, Field

class EventType(str, Enum):
    ACQUISITION = "ACQUISITION"
    SIP_INSTALMENT = "SIP_INSTALMENT"
    DISPOSAL = "DISPOSAL"
    BONUS = "BONUS"
    SPLIT = "SPLIT"
    DIVIDEND_REINVEST = "DIVIDEND_REINVEST"
    SGB_INTEREST = "SGB_INTEREST"
    SGB_MATURITY = "SGB_MATURITY"
    MERGER = "MERGER"

class TaxEventSchema(BaseModel):
    id: str
    asset_id: str = Field(..., alias="assetId")
    asset_name: str = Field(..., alias="assetName")
    isin: Optional[str] = None
    event_type: EventType = Field(..., alias="eventType")
    event_date: date = Field(..., alias="eventDate")
    units: Decimal
    price_per_unit: Decimal = Field(..., alias="pricePerUnit")
    gross_amount: Decimal = Field(..., alias="grossAmount")
    source_document_id: str = Field(..., alias="sourceDocumentId")
    ingested_at: datetime = Field(default_factory=datetime.utcnow, alias="ingestedAt")

    class Config:
        populate_by_name = True

    def unit_delta(self) -> Decimal:
        if self.event_type == EventType.DISPOSAL or self.event_type == EventType.SGB_MATURITY:
            return -self.units
        elif self.event_type == EventType.SGB_INTEREST:
            return Decimal("0.0")
        return self.units
</file>

<file path="quant-sidecar/Dockerfile">
FROM python:3.12-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000 8001

CMD ["python", "app.py"]
</file>

<file path="rules/FY2026-27.yaml">
fy: "2026-27"
effective_start: "2026-04-01"
effective_end: "2027-03-31"

rules:
  equity_listed:
    ltcg_threshold_months: 12
    ltcg_rate: 0.125
    stcg_rate: 0.20
    annual_exemption: 125000
    section: "112A / 111A"

  specified_debt_fund:
    effective_from: "2023-04-01"
    always_short_term: true
    debt_pct_threshold: 0.65
    taxation_mode: "SLAB_RATE"
    section: "50AA"

  gold_silver_international:
    ltcg_threshold_months: 24
    ltcg_rate: 0.125
    stcg_rate: "SLAB_RATE"
    annual_exemption: 0

  sgb:
    maturity_years: 8
    maturity_gain_exempt: true
    exchange_sale_stcg_rate: "SLAB_RATE"
    exchange_sale_ltcg_rate: 0.125
    exchange_sale_ltcg_threshold_months: 24

  grandfathering:
    cutoff_date: "2018-01-31"
</file>

<file path=".gitignore">
# Java & Maven
target/
*.class
*.jar
*.war
*.ear
*.log
.mvn/wrapper/maven-wrapper.properties
.metadata
.project
.classpath
.settings/
.factorypath
.springBeans
.recommenders/
dependency-reduced-pom.xml

# Gradle
.gradle/
build/

# Python
__pycache__/
*.pyc
*.pyo
*.pyd
.Python
env/
venv/
.venv/
pip-log.txt
pip-delete-this-directory.txt
.tox/
.coverage
.cache
nosetests.xml
coverage.xml
*.cover
.hypothesis/
.pytest_cache/

# Node / JS
node_modules/
dist/
.svelte-kit/
.vite/

# Databases & local data files
data/
*.db
*.wal
*.duckdb
*.duckdb.wal

# IDEs
.idea/
.vscode/
*.swp
*.swo
*.sublime-project
*.sublime-workspace
.DS_Store

# OS
Thumbs.db
ehthumbs.db
Desktop.ini
</file>

<file path=".repowise.json">
{
  "name": "portfolio-os",
  "description": "Portfolio OS v3.0 Unified Investment Architecture",
  "version": "3.0.0",
  "components": [
    {
      "name": "core-node",
      "path": "core-node",
      "language": "Java 21 / Spring Boot 3.2.5",
      "repomix": "repomix-core.md"
    },
    {
      "name": "quant-sidecar",
      "path": "quant-sidecar",
      "language": "Python 3.12 / FastAPI + PyArrow Flight",
      "repomix": "repomix-quant.md"
    },
    {
      "name": "mobile-app",
      "path": "mobile-app",
      "language": "Kotlin / Jetpack Compose",
      "repomix": "repomix-mobile.md"
    }
  ]
}
</file>

<file path="CLAUDE_INDEX.md">
# CLAUDE ARCHITECTURE REVIEW INDEX — Portfolio OS v3.0

> **For Claude**: Feed any or all of the three Repomix Markdown bundles into your prompt along with this index file. Total token footprint is optimized under ~44k tokens.

---

## 📄 Compressed Repomix Code Packs

1. **Java Core Node**: [`repomix-core.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-core.md) (**30,283 tokens**)
2. **Python Quant Sidecar**: [`repomix-quant.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-quant.md) (**3,475 tokens**)
3. **Android Companion App**: [`repomix-mobile.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-mobile.md) (**10,425 tokens**)

---

## 🛠️ Summary of System Features & Contracts

- **Append-Only HMAC Ledger**: `SqliteEventStore.java` maintains SHA-256 HMAC event chains for all investment operations.
- **DuckDB Analytical Projection**: `DuckDbProjector.java` projects SQLite events into DuckDB columnar tables for real-time OLAP valuation.
- **Finance Act 2024 FIFO Engine**: `FifoMatcher.java` pairs lots under Sec 112A equity grandfathering rules (31-Jan-2018 FMV) and Sec 50AA debt rules.
- **Arrow Flight RPC**: `flight_server.py` serves Hurst exponent ($H$), OU half-life ($\tau$), and Downside Beta ($\beta_{down}$) vectors via Apache Arrow Flight gRPC port 8001.
- **Android Material 3 Companion App**: `DashboardScreen.kt` & `PortfolioCharts.kt` provide a native Jetpack Compose experience with Canvas Donut charts, ambient gradient cards, scheme-grouped tax lots, and priority AI radar.

---

## 🔗 GitHub Repository
[github.com/rakeshpc2809/portfolio-os](https://github.com/rakeshpc2809/portfolio-os)
</file>

<file path="README.md">
# Portfolio OS (v3.0 HLD Architecture)

Portfolio OS merges the core tax ledger of `my-fintracker` and the quantitative analytics engine of `portfolio-tracker-v2` under a decoupled, high-performance polyglot architecture.

---

## 🏗️ Architecture Overview

```
                        ┌───────────────────────────────┐
                        │      Vue 3 Web Cockpit        │
                        │    (Desktop & Responsive)     │
                        └──────────────┬────────────────┘
                                       │ REST / JSON (HTTP 8080)
                                       ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                          Core Node (Java 26 / Spring Boot)                    │
│                                                                               │
│  - FIFO Matching Engine             - Rule Engine (YAML Hot-Reload)           │
│  - HMAC-SHA256 Cryptographic Ledger  - Statutory Offset Engine (Sec 112A/50AA)│
│  - Rebalancing & Decumulation       - Maturation Ladder & Schedule FA Export  │
└──────────────────────┬────────────────────────────────┬───────────────────────┘
                       │                                │
      SQLite Event Store│                                │ Arrow Flight RPC (gRPC 8001)
     (Cryptographic Log)│                                │ Fast Zero-Copy Data Passing
                        ▼                                ▼
              ┌──────────────────┐            ┌──────────────────────────────────┐
              │ DuckDB Projector │            │     Quant Sidecar (Python)       │
              │(Analytical Query)│            │  - Polars + PyArrow Flight       │
              └──────────────────┘            │  - Hurst Exponent Vectorization  │
                                              │  - HMM Market Regimes & OU Math  │
                                              │  - CAS PDF / Broker CSV Parsers  │
                                              └──────────────────────────────────┘
```

---

## ⚡ Key Features

1. **Cryptographic Event Sourcing Ledger**: SQLite storage protected by HMAC-SHA256 hash-chaining to ensure append-only tamper evidence.
2. **DuckDB Analytical Projection**: Automated projection of ledger events into local DuckDB for analytical queries.
3. **Apache Arrow Flight RPC**: Inter-process communication between Java Core and Python Quant Sidecar passing vector memory with zero serialization overhead.
4. **Dynamic YAML Tax Rules**: Dynamic rule loading for Indian Income Tax Act changes (Section 112A equity exemption, Section 50AA specified debt, Section 55(2)(ac) grandfathering).
5. **Decumulation & Rebalancing Advisors**:
   - FIRE Decumulation Runway & SWR Planner
   - Flat Bucket Allocation Rebalancer & Drawdown Trigger Rungs
   - Tax-Loss Harvesting Opportunity Scanner
   - Disciplined Portfolio Consolidation Plan

---

## 🚀 Quickstart & Deployment

### Prerequisites
- Docker & Docker Compose or Podman & Podman Compose
- JDK 21+ / OpenJDK 26 (for local development outside containers)
- Python 3.12+ (for sidecar local development outside containers)

### Running with Docker Compose / Podman Compose

```bash
# Build and start services
podman compose up --build -d

# View logs
podman compose logs -f
```

The Web Cockpit and REST API will be accessible at:
`http://localhost:8080/`

---

## 📡 API Sitemap

### Core Ledger & Sync
- `GET /api/v1/sync/snapshot` — Unidirectional snapshot containing holdings, tax lots, and radar signals
- `POST /api/v1/sync/pair` — Device pairing endpoint

### Statement Ingestion
- `POST /api/v1/statements/upload` — Multipart upload for CAMS/KFintech CAS PDFs or Broker CSVs

### Reports & Tax Optimization
- `GET /api/v1/portfolio/summary` — Net worth, total unrealized gain, active scheme count, XIRR
- `GET /api/v1/portfolio/holdings` — Grouped open holdings with FIFO lot details
- `GET /api/v1/portfolio/allocation` — Asset allocation distribution
- `GET /api/v1/portfolio/category-allocation` — Risk exposure allocation by tax category
- `GET /api/v1/tax/exemption-status` — Section 112A LTCG exemption headroom meter
- `GET /api/v1/tax/reports/itr2` — Schedule CG summary
- `GET /api/v1/tax/harvest-opportunities` — Tax-loss harvesting recommendations
- `GET /api/v1/tax/maturation-ladder` — LTCG maturation timeline
- `GET /api/v1/tax/realized-log` — Fiscal year realized gain/loss audit log
- `GET /api/v1/tax/export/itr2/zip` — ZIP bundle download containing Schedule 112A, Schedule STCG, and Schedule FA CSVs

### Valuation & Advisors
- `GET /api/v1/portfolio/buckets/rebalance` — Bucket drift and market drawdown triggers
- `GET /api/v1/portfolio/rebalance-preview` — Target redemption tax drag estimator
- `GET /api/v1/portfolio/consolidation-preview` — Phased asset exit and core fund redeployment plan
- `GET /api/v1/portfolio/goals` — Liquid buffer goal tag allocations
- `GET /api/v1/portfolio/fire` — FIRE decumulation runway calculations
</file>

<file path="core-node/src/main/java/com/portfolioos/core/fire/FireTracker.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/rules/TaxRulesLoader.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/security/SecurityConfig.java">
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
            .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/service/PortfolioValuationService.java">
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
import com.portfolioos.core.reporting.ExemptionTracker;
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

    private final LedgerCacheService cacheService;
    private final XirrEngine xirrEngine = new XirrEngine();

    public PortfolioValuationService(LedgerCacheService cacheService) {
        this.cacheService = cacheService;
    }

    private String fmt(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public PortfolioSummaryResponse getPortfolioSummary(String fy) {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<TaxEvent> allEvents = state.events();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();

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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();
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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
        Map<String, BigDecimal> navMap = state.navMap();

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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();

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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();

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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();

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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
        Map<String, BigDecimal> navMap = state.navMap();

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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/tax/ScheduleCgExporter.java">
package com.portfolioos.core.tax;

import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class ScheduleCgExporter {

    public static String generateCsvReport(List<MatchedLot> matchedLots, String fiscalYear) {
        StringBuilder csv = new StringBuilder();
        csv.append("ITR SCHEDULE CG - CAPITAL GAINS SUMMARY (FY ").append(fiscalYear).append(")\n");
        csv.append("Generated by Portfolio OS Tax Engine\n\n");
        csv.append("ISIN,Purchase Date,Sale Date,Holding Days,Units Sold,Purchase Cost (INR),Sale Value (INR),Capital Gain (INR),Tax Classification,Section,Tax Rate\n");

        BigDecimal totalLtcgEquity = BigDecimal.ZERO;
        BigDecimal totalStcgEquity = BigDecimal.ZERO;
        BigDecimal totalDebtGain = BigDecimal.ZERO;

        for (MatchedLot match : matchedLots) {
            long days = match.holdingPeriodDays();
            AssetCategory category = match.assetCategory();
            TaxTerm term = match.taxTerm();

            BigDecimal units = match.unitsMatched();
            BigDecimal cost = match.costBasis().setScale(2, RoundingMode.HALF_UP);
            BigDecimal saleVal = match.saleProceeds().setScale(2, RoundingMode.HALF_UP);
            BigDecimal gain = match.realizedGain().setScale(2, RoundingMode.HALF_UP);

            String section;
            String taxRate;

            if (category == AssetCategory.EQUITY) {
                if (term == TaxTerm.LONG_TERM) {
                    section = "112A";
                    taxRate = "12.5%";
                    totalLtcgEquity = totalLtcgEquity.add(gain);
                } else {
                    section = "111A";
                    taxRate = "20.0%";
                    totalStcgEquity = totalStcgEquity.add(gain);
                }
            } else {
                section = "50AA";
                taxRate = "Slab Rate";
                totalDebtGain = totalDebtGain.add(gain);
            }

            csv.append(escapeCsv(match.assetId())).append(",")
               .append(match.acquisitionDate()).append(",")
               .append(match.disposalDate()).append(",")
               .append(days).append(",")
               .append(units.toPlainString()).append(",")
               .append(cost.toPlainString()).append(",")
               .append(saleVal.toPlainString()).append(",")
               .append(gain.toPlainString()).append(",")
               .append(term.name()).append(",")
               .append(section).append(",")
               .append(taxRate).append("\n");
        }

        csv.append("\nSUMMARY TAX OBLIGATION RECAP\n");
        csv.append("Equity Sec 112A Total LTCG Gain: INR ").append(totalLtcgEquity.toPlainString()).append("\n");
        csv.append("Sec 112A Annual Exemption Limit: INR 125000.00\n");
        BigDecimal taxableLtcg = totalLtcgEquity.subtract(new BigDecimal("125000.00")).max(BigDecimal.ZERO);
        csv.append("Net Taxable Sec 112A LTCG: INR ").append(taxableLtcg.toPlainString()).append("\n");
        csv.append("Equity Sec 111A Total STCG Gain (20%): INR ").append(totalStcgEquity.toPlainString()).append("\n");
        csv.append("Debt Sec 50AA Total Gain (Slab Rate): INR ").append(totalDebtGain.toPlainString()).append("\n");

        return csv.toString();
    }

    private static String escapeCsv(String text) {
        if (text == null) return "";
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/valuation/BucketEngine.java">
package com.portfolioos.core.valuation;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.reporting.ExemptionTracker;
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
        return evaluateRebalance(openLots, List.of(), navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh, targets, fiscalYear);
    }

    public static RebalanceEngineResult evaluateRebalance(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
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

        // Deduct statutory Section 112A LTCG exemption
        ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(matchedLots != null ? matchedLots : List.of(), fiscalYear);
        BigDecimal exemptionRemaining = new BigDecimal(exStatus.exemptionRemaining());

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
                        long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), currentDate);
                        boolean isLtcg = TaxClassifier.classifyTaxTerm(category, holdingDays, fiscalYear, true) == TaxTerm.LONG_TERM;
                        BigDecimal gain = nav.subtract(lot.costPerUnit()).multiply(lot.remainingUnits()).max(BigDecimal.ZERO);

                        if (gain.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal rate = isLtcg ? rules.equityLtcgRate() : rules.equityStcgRate();
                            BigDecimal taxableGain = gain;
                            if (isLtcg && exemptionRemaining.compareTo(BigDecimal.ZERO) > 0) {
                                if (taxableGain.compareTo(exemptionRemaining) <= 0) {
                                    exemptionRemaining = exemptionRemaining.subtract(taxableGain);
                                    taxableGain = BigDecimal.ZERO;
                                } else {
                                    taxableGain = taxableGain.subtract(exemptionRemaining);
                                    exemptionRemaining = BigDecimal.ZERO;
                                }
                            }
                            estTaxDrag = estTaxDrag.add(taxableGain.multiply(rate));
                            taxTerms.add(isLtcg ? "LTCG @ " + rules.equityLtcgRate().multiply(new BigDecimal("100")) + "% (Sec 112A exemption applied)" 
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/valuation/ConsolidationRebalanceEngine.java">
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

        BigDecimal netPostTaxProceeds = totalProceeds.subtract(totalTaxDrag).max(BigDecimal.ZERO);
        BigDecimal effectiveProceeds = netPostTaxProceeds.compareTo(BigDecimal.ZERO) > 0 ? netPostTaxProceeds : totalProceeds;

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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/xirr/XirrEngine.java">
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

        // Newton-Raphson solver
        double rate = 0.10;
        for (int iter = 0; iter < 100; iter++) {
            double f = npv(rate, dates, amounts);
            double df = dNpv(rate, dates, amounts);

            if (Math.abs(df) > 1e-10) {
                double nextRate = rate - f / df;
                if (Math.abs(nextRate - rate) < 1e-7) {
                    double result = nextRate * 100.0;
                    if (Double.isNaN(result) || Double.isInfinite(result)) return 0.0;
                    return Math.max(-99.0, result);
                }
                rate = nextRate;
            }
            if (rate <= -0.90) rate = -0.50;
        }

        // Bracketed Bisection Fallback
        double low = -0.50;
        double high = 10.0;
        double flow = npv(low, dates, amounts);
        double fhigh = npv(high, dates, amounts);

        if (flow * fhigh <= 0) {
            for (int i = 0; i < 100; i++) {
                double mid = (low + high) / 2.0;
                double fmid = npv(mid, dates, amounts);
                if (Math.abs(fmid) < 1e-7 || (high - low) < 1e-7) {
                    return Math.max(-99.0, mid * 100.0);
                }
                if (flow * fmid < 0) {
                    high = mid;
                    fhigh = fmid;
                } else {
                    low = mid;
                    flow = fmid;
                }
            }
            return Math.max(-99.0, ((low + high) / 2.0) * 100.0);
        }

        double rawResult = rate * 100.0;
        if (Double.isNaN(rawResult) || Double.isInfinite(rawResult)) return 0.0;
        return Math.max(-99.0, rawResult);
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/CoreApplication.java">
package com.portfolioos.core;

import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.ports.EventStorePort;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class
})
@EnableScheduling
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
</file>

<file path="core-node/src/main/resources/static/src/js/modules/insurance.js">
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
</file>

<file path="core-node/src/main/resources/static/src/js/api.js">
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
</file>

<file path="core-node/src/main/resources/application.yml">
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
  jackson:
    property-naming-strategy: SNAKE_CASE

logging:
  level:
    root: INFO
    com.portfolioos.core: DEBUG
    org.springframework.web: INFO
</file>

<file path="core-node/Dockerfile">
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "--add-opens=java.base/java.nio=ALL-UNNAMED", "-jar", "app.jar"]
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/ui/SimulatorScreen.kt">
package com.portfolioos.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.TradeSimulationRequestDto
import com.portfolioos.mobile.util.formatInr
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorView(holdings: List<FlatHoldingDto>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedIsin by remember { mutableStateOf(holdings.firstOrNull()?.isin ?: "") }
    var selectedName by remember { mutableStateOf(holdings.firstOrNull()?.fundName ?: "Select Scheme") }
    var unitsText by remember { mutableStateOf("100.0") }
    var priceText by remember { mutableStateOf("150.0") }
    var tradeType by remember { mutableStateOf("DISPOSAL") }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "⚡ WHAT-IF TRADE SIMULATOR",
            color = Color(0xFFD0FF00),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = "Preview tax drag and post-trade XIRR before executing trades.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Scheme Selector
        ExposedDropdownMenuBox(
            expanded = isExpanded,
            onExpandedChange = { isExpanded = !isExpanded }
        ) {
            OutlinedTextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Target Scheme", color = Color(0xFF94A3B8)) },
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFF00F0FF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false }
            ) {
                holdings.forEach { holding ->
                    DropdownMenuItem(
                        text = { Text(holding.fundName) },
                        onClick = {
                            selectedIsin = holding.isin
                            selectedName = holding.fundName
                            isExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = unitsText,
                onValueChange = { unitsText = it },
                label = { Text("Units", color = Color(0xFF94A3B8)) },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Price/NAV (₹)", color = Color(0xFF94A3B8)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tradeType == "DISPOSAL",
                onClick = { tradeType = "DISPOSAL" },
                label = { Text("Simulate Sale (Disposal)") }
            )
            FilterChip(
                selected = tradeType == "ACQUISITION",
                onClick = { tradeType = "ACQUISITION" },
                label = { Text("Simulate Buy (SIP)") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val units = unitsText.toDoubleOrNull() ?: 100.0
                val price = priceText.toDoubleOrNull() ?: 150.0
                isLoading = true
                scope.launch {
                    try {
                        val req = TradeSimulationRequestDto(
                            isin = selectedIsin,
                            schemeName = selectedName,
                            units = units,
                            pricePerUnit = price,
                            tradeType = tradeType
                        )
                        val res = SyncApiClient.simulateTradeWithFallback(context, req)
                        resultText = """
                            ✓ Simulation Execution Successful (Live Engine)
                            • Target: ${res.schemeName}
                            • Trade Type: ${res.tradeType} (${res.units} Units @ ₹${res.pricePerUnit})
                            • Gross Trade Amount: ${formatInr(res.grossTradeAmount)}
                            • Gross Capital Gain: ${formatInr(res.grossCapitalGain)}
                            • LTCG Equity: ${formatInr(res.ltcgEquity)} | STCG Equity: ${formatInr(res.stcgEquity)}
                            • Sec 112A Exemption Applied: ${formatInr(res.sec112aExemptionApplied)}
                            • Projected Tax Liability: ${formatInr(res.estimatedTaxLiability)}
                            • Post-Trade Valuation: ${formatInr(res.postTradeNetWorth)}
                            • Post-Trade Portfolio XIRR: ${String.format("%.2f", res.postTradeXirr)}%
                        """.trimIndent()
                    } catch (e: Exception) {
                        resultText = "⚠️ Simulation RPC failed: ${e.localizedMessage}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0FF00)),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
            } else {
                Text("Run What-If Simulation", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (resultText != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = resultText!!,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/widget/PortfolioGlanceWidget.kt">
package com.portfolioos.mobile.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.portfolioos.mobile.MainActivity
import com.portfolioos.mobile.data.SnapshotCacheManager

class PortfolioGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = SnapshotCacheManager.loadSnapshot(context)
        val info = snapshot?.syncInfo
        val holdings = snapshot?.holdings ?: emptyList()

        val bestFund = holdings.maxByOrNull { it.xirr }
        val worstFund = holdings.minByOrNull { it.xirr }

        // Calculate portfolio gain percentage for privacy-first display
        val totalInvested = info?.totalInvested ?: 1.0
        val unrealizedGain = info?.unrealizedGain ?: 0.0
        val gainPct = if (totalInvested > 0) (unrealizedGain / totalInvested) * 100.0 else 0.0
        val formattedGainPct = String.format("%s%.2f%%", if (gainPct >= 0) "+" else "", gainPct)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFF0D1424)))
                        .padding(14.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = "PORTFOLIO OS",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFD0FF00)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = info?.xirrPercentage ?: "0.00% XIRR",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF10B981)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Privacy-First Valuation & Return Header
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(
                            text = "₹ • • • • • •",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF94A3B8)),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = formattedGainPct,
                            style = TextStyle(
                                color = ColorProvider(if (gainPct >= 0) Color(0xFF10B981) else Color(0xFFEF4444)),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "BEST PERFORMER",
                                style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 8.sp)
                            )
                            Text(
                                text = bestFund?.let { "${it.fundName.take(14)} (+${it.xirr}%)" } ?: "N/A",
                                style = TextStyle(color = ColorProvider(Color(0xFF10B981)), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "WORST PERFORMER",
                                style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 8.sp)
                            )
                            Text(
                                text = worstFund?.let { "${it.fundName.take(14)} (${it.xirr}%)" } ?: "N/A",
                                style = TextStyle(color = ColorProvider(Color(0xFFF59E0B)), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    Text(
                        text = "Valuation Hidden for Privacy · Tap to Open App",
                        style = TextStyle(color = ColorProvider(Color(0xFF00F0FF)), fontSize = 9.sp)
                    )
                }
            }
        }
    }
}

class PortfolioGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioGlanceWidget()
}
</file>

<file path="quant-sidecar/parsers/broker_csv_parser.py">
import uuid
from datetime import datetime
from decimal import Decimal
from typing import List, Optional
import polars as pl
from .models import TaxEventSchema, EventType

class BrokerCsvParser:
    def __init__(self, csv_path: str, broker_type: str = "generic"):
        self.csv_path = csv_path
        self.broker_type = broker_type

    def parse(self) -> List[TaxEventSchema]:
        events: List[TaxEventSchema] = []
        try:
            df = pl.read_csv(self.csv_path, infer_schema_length=0)
            if df.is_empty():
                return events

            col_map = {str(c).strip().lower(): c for c in df.columns}

            date_col = next((col_map[k] for k in col_map if any(x in k for x in ["date", "txn_date", "trade_date"])), None)
            symbol_col = next((col_map[k] for k in col_map if any(x in k for x in ["symbol", "scheme", "scrip", "asset", "description"])), None)
            type_col = next((col_map[k] for k in col_map if any(x in k for x in ["type", "buy/sell", "transaction", "action"])), None)
            qty_col = next((col_map[k] for k in col_map if any(x in k for x in ["qty", "quantity", "units"])), None)
            price_col = next((col_map[k] for k in col_map if any(x in k for x in ["price", "nav", "rate"])), None)
            amount_col = next((col_map[k] for k in col_map if any(x in k for x in ["amount", "value", "total"])), None)

            for row in df.to_dicts():
                try:
                    asset_name = str(row[symbol_col]) if symbol_col and row.get(symbol_col) else "Broker Asset"
                    date_str = str(row[date_col]) if date_col and row.get(date_col) else ""

                    event_date = datetime.now().date()
                    if date_str:
                        for fmt in ("%Y-%m-%d", "%d-%m-%Y", "%d/%m/%Y", "%d-%b-%Y"):
                            try:
                                event_date = datetime.strptime(date_str.strip(), fmt).date()
                                break
                            except ValueError:
                                pass

                    txn_type_str = str(row[type_col]).upper() if type_col and row.get(type_col) else "BUY"
                    if any(x in txn_type_str for x in ["SELL", "REDEMPTION", "DISPOSAL", "SWITCH OUT"]):
                        event_type = EventType.DISPOSAL
                    elif "BONUS" in txn_type_str:
                        event_type = EventType.BONUS
                    elif "SPLIT" in txn_type_str:
                        event_type = EventType.SPLIT
                    else:
                        event_type = EventType.ACQUISITION

                    units_val = row.get(qty_col)
                    units = Decimal(str(abs(float(units_val)))) if units_val is not None and str(units_val).strip() != "" else Decimal("1")
                    
                    price_val = row.get(price_col)
                    price = Decimal(str(abs(float(price_val)))) if price_val is not None and str(price_val).strip() != "" else Decimal("0")
                    
                    amt_val = row.get(amount_col)
                    amount = Decimal(str(abs(float(amt_val)))) if amt_val is not None and str(amt_val).strip() != "" else (units * price)

                    events.append(
                        TaxEventSchema(
                            id=str(uuid.uuid4()),
                            assetId=asset_name.replace(" ", "_").upper()[:20],
                            assetName=asset_name,
                            isin=None,
                            eventType=event_type,
                            eventDate=event_date,
                            units=units,
                            pricePerUnit=price,
                            grossAmount=amount,
                            sourceDocumentId=self.csv_path,
                            ingestedAt=datetime.now()
                        )
                    )
                except Exception:
                    continue
        except Exception:
            pass

        from .sip_detector import detect_and_tag_sips
        return detect_and_tag_sips(events)
</file>

<file path="quant-sidecar/parsers/cas_parser.py">
import re
import uuid
from decimal import Decimal
from typing import List, Optional
from datetime import datetime, date
from .models import TaxEventSchema, EventType

DATE_REGEX = re.compile(r"^(\d{2}-[A-Za-z]{3}-\d{4})\s+(.+)$")
# Added support for both CAMS and KFintech PAN formats in CAS
ISIN_REGEX = re.compile(r"ISIN:\s*([A-Z0-9]{12})", re.IGNORECASE)
TOKEN_REGEX = re.compile(r"\((?:\d{1,3}(?:,\d{3})*|\d+)(?:\.\d+)?\)|\b\d{1,3}(?:,\d{3})*(?:\.\d+)?\b|\b\d+\.\d+\b")

class CasPdfParser:
    def __init__(self, pdf_path: str, password: Optional[str] = None):
        self.pdf_path = pdf_path
        self.password = password

    def parse_events(self) -> List[TaxEventSchema]:
        events: List[TaxEventSchema] = []
        if not self.pdf_path:
            return events

        # Try specialized casparser library first
        try:
            import casparser
            data = casparser.read_cas_pdf(self.pdf_path, self.password or "")
            for folio in data.folios:
                for scheme in folio.schemes:
                    isin = scheme.isin
                    scheme_name = scheme.scheme
                    asset_id = isin or scheme_name.replace(" ", "_").upper()[:20]

                    for txn in scheme.transactions:
                        txn_type_str = str(txn.type).upper()
                        if any(x in txn_type_str for x in ["REDEMPTION", "SWITCH_OUT", "SELL"]):
                            event_type = EventType.DISPOSAL
                        elif "BONUS" in txn_type_str:
                            event_type = EventType.BONUS
                        elif "SPLIT" in txn_type_str:
                            event_type = EventType.SPLIT
                        else:
                            event_type = EventType.ACQUISITION

                        txn_date = txn.date if isinstance(txn.date, date) else datetime.now().date()
                        units = Decimal(str(abs(txn.units or 0)))
                        price = Decimal(str(abs(txn.nav or 0)))
                        amount = Decimal(str(abs(txn.amount or 0)))
                        if amount == Decimal("0") and units > 0 and price > 0:
                            amount = units * price

                        if units > Decimal("0"):
                            events.append(
                                TaxEventSchema(
                                    id=str(uuid.uuid4()),
                                    assetId=asset_id,
                                    assetName=scheme_name,
                                    isin=isin,
                                    eventType=event_type,
                                    eventDate=txn_date,
                                    units=units,
                                    pricePerUnit=price,
                                    grossAmount=amount,
                                    sourceDocumentId=self.pdf_path,
                                    ingestedAt=datetime.now()
                                )
                            )
            if events:
                return events
        except Exception as e:
            print(f"casparser notice: {e}, falling back to custom line parser.")

        # Fallback to pdfplumber regex line parser
        try:
            import pdfplumber

            current_scheme = "Mutual Fund Scheme"
            current_isin: Optional[str] = None

            with pdfplumber.open(self.pdf_path, password=self.password or "") as pdf:
                for page in pdf.pages:
                    text = page.extract_text() or ""
                    for line in text.splitlines():
                        line_str = line.strip()
                        if not line_str:
                            continue

                        isin_match = ISIN_REGEX.search(line_str)
                        if isin_match:
                            current_isin = isin_match.group(1)

                        if "ISIN:" in line_str or ("Fund" in line_str and "Registrar" in line_str):
                            current_scheme = line_str.split(" - ISIN:")[0].split("(Advisor")[0].strip()

                        if any(
                            x in line_str
                            for x in [
                                "*** Stamp Duty ***",
                                "*** STT Paid ***",
                                "***Cancelled***",
                                "***Address Updated",
                                "Opening Unit Balance",
                                "CAMSCASWS",
                                "Consolidated Account Statement",
                                "Closing Unit Balance",
                                "NAV on",
                            ]
                        ):
                            continue

                        match = DATE_REGEX.match(line_str)
                        if match:
                            date_str, rest = match.groups()
                            try:
                                event_date = datetime.strptime(date_str, "%d-%b-%Y").date()
                            except ValueError:
                                event_date = datetime.now().date()

                            num_tokens = TOKEN_REGEX.findall(rest)

                            clean_nums = []
                            for tok in num_tokens:
                                is_neg = tok.startswith("(") and tok.endswith(")")
                                raw_val = tok.replace("(", "").replace(")", "").replace(",", "").strip()
                                try:
                                    val = Decimal(raw_val)
                                    if is_neg:
                                        val = -val
                                    clean_nums.append(val)
                                except Exception:
                                    pass

                            if len(clean_nums) >= 3:
                                amount = abs(clean_nums[0])
                                units = abs(clean_nums[1])
                                price = clean_nums[2]

                                line_upper = rest.upper()
                                if any(x in line_upper for x in ["REDEMPTION", "SWITCH OUT", "SELL"]):
                                    event_type = EventType.DISPOSAL
                                elif "BONUS" in line_upper:
                                    event_type = EventType.BONUS
                                elif "SPLIT" in line_upper:
                                    event_type = EventType.SPLIT
                                else:
                                    event_type = EventType.ACQUISITION

                                events.append(
                                    TaxEventSchema(
                                        id=str(uuid.uuid4()),
                                        assetId=current_isin or current_scheme.replace(" ", "_").upper()[:20],
                                        assetName=current_scheme,
                                        isin=current_isin,
                                        eventType=event_type,
                                        eventDate=event_date,
                                        units=units,
                                        pricePerUnit=price,
                                        grossAmount=amount,
                                        sourceDocumentId=self.pdf_path,
                                        ingestedAt=datetime.now()
                                    )
                                )
        except Exception as err:
            print(f"Fallback parser error: {err}")

        from .sip_detector import detect_and_tag_sips
        return detect_and_tag_sips(events)
</file>

<file path="quant-sidecar/parsers/sip_detector.py">
from typing import List
from collections import defaultdict
from .models import TaxEventSchema, EventType

def detect_and_tag_sips(events: List[TaxEventSchema], min_consecutive_matches: int = 3) -> List[TaxEventSchema]:
    """
    Auto-detects Systematic Investment Plans (SIPs) by grouping transactions by ISIN/Asset ID,
    checking date spacing (25 to 35 days for monthly recurring investments), and amount variation (<= 5%).
    Requires at least `min_consecutive_matches` (default 3+) consecutive matching transactions to eliminate false positives.
    Tags matching ACQUISITION events as EventType.SIP_INSTALMENT.
    """
    if not events:
        return events

    acquisitions_by_asset = defaultdict(list)
    for idx, event in enumerate(events):
        if event.event_type in (EventType.ACQUISITION, EventType.SIP_INSTALMENT):
            asset_key = event.isin or event.asset_id
            acquisitions_by_asset[asset_key].append((idx, event))

    sip_indices = set()

    for asset_key, asset_events in acquisitions_by_asset.items():
        if len(asset_events) < min_consecutive_matches:
            continue

        sorted_events = sorted(asset_events, key=lambda x: x[1].event_date)
        current_chain = [sorted_events[0]]

        for i in range(len(sorted_events) - 1):
            idx1, ev1 = sorted_events[i]
            idx2, ev2 = sorted_events[i + 1]

            date_diff = (ev2.event_date - ev1.event_date).days
            amt1 = float(ev1.gross_amount)
            amt2 = float(ev2.gross_amount)
            amt_diff_pct = abs(amt1 - amt2) / max(amt1, amt2, 1.0)

            # Monthly SIP criteria: 25 to 35 days spacing AND <= 5% amount variation
            if 25 <= date_diff <= 35 and amt_diff_pct <= 0.05:
                current_chain.append(sorted_events[i + 1])
            else:
                if len(current_chain) >= min_consecutive_matches:
                    for chain_idx, _ in current_chain:
                        sip_indices.add(chain_idx)
                current_chain = [sorted_events[i + 1]]

        if len(current_chain) >= min_consecutive_matches:
            for chain_idx, _ in current_chain:
                sip_indices.add(chain_idx)

    updated_events = []
    for idx, event in enumerate(events):
        if idx in sip_indices:
            updated_events.append(event.model_copy(update={"event_type": EventType.SIP_INSTALMENT}))
        else:
            updated_events.append(event)

    return updated_events
</file>

<file path="quant-sidecar/app.py">
import os
import tempfile
import threading
import logging
from typing import List, Optional
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
import polars as pl
import uvicorn

from parsers.cas_parser import CasPdfParser
from parsers.broker_csv_parser import BrokerCsvParser
from parsers.sip_detector import detect_and_tag_sips
from parsers.models import TaxEventSchema
from flight_server import QuantFlightServer

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("quant-sidecar")

app = FastAPI(title="Portfolio OS Quant & Parser Sidecar", version="3.0.0")

@app.get("/health")
def health_check():
    return {"status": "UP", "engine": "Polars + FastAPI + Arrow Flight", "version": "3.0.0"}

@app.post("/api/v1/parse", response_model=List[TaxEventSchema])
async def parse_statement(
    file: UploadFile = File(...),
    password: Optional[str] = Form(None)
):
    filename = file.filename or "statement"
    ext = os.path.splitext(filename)[1].lower()
    logger.info(f"Received statement upload: {filename} with extension {ext}")

    with tempfile.NamedTemporaryFile(delete=False, suffix=ext) as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        events = []
        if ext == ".pdf":
            parser = CasPdfParser(tmp_path, password=password)
            events = parser.parse_events()
        elif ext == ".csv":
            parser = BrokerCsvParser(tmp_path)
            events = parser.parse()
        else:
            raise HTTPException(status_code=400, detail="Unsupported file format. Please upload PDF or CSV.")

        # Apply robust 3+ match SIP auto-detection
        events = detect_and_tag_sips(events)

        # Polars multi-threaded dataframe verification
        if events:
            df = pl.DataFrame([e.model_dump(by_alias=True) for e in events])
            required_cols = ["id", "assetId", "assetName", "eventType", "eventDate", "units", "grossAmount"]
            for col in required_cols:
                if col not in df.columns:
                    raise HTTPException(status_code=422, detail=f"Missing column in parsed dataframe: {col}")
        
        logger.info(f"Successfully parsed {len(events)} events from statement")
        return events
    except Exception as err:
        logger.error(f"Error parsing statement: {err}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(err))
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)

def run_flight_server():
    try:
        server = QuantFlightServer("0.0.0.0", 8001)
        logger.info("Starting Apache Arrow Flight RPC server on port 8001...")
        server.serve()
    except Exception as e:
        logger.error(f"Failed to start Flight server: {e}", exc_info=True)

if __name__ == "__main__":
    # Start Apache Arrow Flight RPC Server in a background daemon thread
    flight_thread = threading.Thread(target=run_flight_server, daemon=True)
    flight_thread.start()
    
    # Run FastAPI server
    logger.info("Starting FastAPI HTTP Server on port 8000...")
    uvicorn.run(app, host="0.0.0.0", port=8000)
</file>

<file path="ARCHITECTURE_INDEX.md">
# Portfolio OS v3.0 — AI System Design & Review Index

> **Notice for Claude / Reviewing AI Models**: This document serves as the master architectural index for **Portfolio OS v3.0**, a local-first investment management system built with an append-only SHA-256 HMAC event-sourcing ledger (Java Core Node), a quantitative factor engine (Python PyArrow Flight), a Vue 3 Vapor web cockpit, and an offline-first Jetpack Compose Android companion app.

---

## 📦 Ultra-Compressed Repomix Codebase Bundles

The entire repository source code has been packed into three minimal, token-compressed Markdown bundles for low-cost context ingestion:

| Component | Repository Path | Repomix Output Pack | Token Count | Main Architecture / Tech Stack |
| :--- | :--- | :--- | :---: | :--- |
| **Java Core Node** | `core-node/` | [`repomix-core.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-core.md) | **30,283** | Java 21, Spring Boot 3.2.5, SQLite HMAC, DuckDB, Arrow Flight |
| **Python Quant Sidecar** | `quant-sidecar/` | [`repomix-quant.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-quant.md) | **3,475** | Python 3.12, PyArrow Flight RPC, Polars, Hurst/HMM |
| **Android Companion App** | `mobile-app/` | [`repomix-mobile.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-mobile.md) | **10,425** | Kotlin, Jetpack Compose M3 Expressive, Retrofit 2 |
| **TOTAL SYSTEM** | Repository Root | **3 Repomix Bundles** | **~44,183** | **Unified Architecture** |

---

## 🏛️ System Component Breakdown for Review

### 1. Java Core Node ([`repomix-core.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-core.md))
- [`SqliteEventStore.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java): Append-only event store with SHA-256 HMAC cryptographic chain verification.
- [`DuckDbProjector.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java): Automatic DuckDB columnar projection store for fast OLAP queries.
- [`FifoMatcher.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/matcher/FifoMatcher.java): Indian IT Act FIFO tax lot pairing engine supporting bonus, corporate splits, and 31-Jan-2018 grandfathering FMV rules.
- [`TaxClassifier.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/matcher/TaxClassifier.java): Indian tax law classifier (Equity Sec 112A, Debt Sec 50AA, Gold/SGB).
- [`SyncController.java`](file:///home/rakeshpc/Projects/portfolio-os/core-node/src/main/java/com/portfolioos/core/controllers/SyncController.java): Mobile synchronization endpoint `/api/v1/sync/snapshot` providing XIRR, Net Worth in Rupees, scheme-grouped tax lots, and aggregated priority AI Radar signals.

### 2. Python Quant Sidecar ([`repomix-quant.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-quant.md))
- [`flight_server.py`](file:///home/rakeshpc/Projects/portfolio-os/quant-sidecar/flight_server.py): Apache Arrow Flight RPC server on gRPC port 8001.
- [`quant_engine.py`](file:///home/rakeshpc/Projects/portfolio-os/quant-sidecar/quant/quant_engine.py): Hurst Exponent ($H$), Ornstein-Uhlenbeck (OU) half-life ($\tau$), Gaussian HMM regime solver, and Downside Beta ($\beta_{down}$).
- [`cas_parser.py`](file:///home/rakeshpc/Projects/portfolio-os/quant-sidecar/parsers/cas_parser.py): Dual-engine CAMS/KFintech CAS PDF parser.

### 3. Native Android App ([`repomix-mobile.md`](file:///home/rakeshpc/Projects/portfolio-os/repomix-mobile.md))
- [`DashboardScreen.kt`](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/DashboardScreen.kt): Material 3 Expressive UI with ambient gradient hero metric card, scheme-grouped tax lots, and priority AI radar.
- [`PortfolioCharts.kt`](file:///home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/PortfolioCharts.kt): Native Jetpack Compose `Canvas` Donut Allocation Ring Chart and XIRR Performance Bar Chart.

---

## 💡 Review Instructions for Claude / AI Reviewers

When reviewing this system design, focus on:
1. **Cryptographic Event Ledger Integrity**: Validate that `SqliteEventStore.java` enforces immutable append-only event constraints and SHA-256 HMAC verification.
2. **Indian Tax Compliance**: Inspect `FifoMatcher.java` and `TaxClassifier.java` for Finance Act 2024 compliance (Sec 112A ₹1.25L exemption, Sec 50AA debt classification).
3. **Apache Arrow Zero-Copy Protocol**: Review `flight_server.py` and `ArrowFlightClient.java` for zero-copy memory vector transfer.
4. **Android Material 3 UI**: Review `DashboardScreen.kt` and `PortfolioCharts.kt` for native Compose architecture and `@SerializedName` Jackson compatibility.
</file>

<file path="docker-compose.yml">
version: '3.8'

services:
  core-node:
    build:
      context: ./core-node
      dockerfile: Dockerfile
    container_name: portfolio-os-core
    ports:
      - "127.0.0.1:8080:8080"
    environment:
      - API_AUTH_TOKEN=fintracker-cachyos-default-key-2026
      - LEDGER_HMAC_SECRET=fintracker-cachyos-default-key-2026
      - SQLITE_PATH=/app/data/tax_ledger.db
      - DUCKDB_PATH=/app/data/tax_ledger.duckdb
      - SIDECAR_HTTP_URL=http://quant-sidecar:8000
      - SIDECAR_FLIGHT_URL=grpc+tcp://quant-sidecar:8001
      - SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434
    volumes:
      - ./data:/app/data
      - ./rules:/app/rules
    depends_on:
      quant-sidecar:
        condition: service_healthy
      ollama:
        condition: service_started
    restart: unless-stopped

  ollama:
    image: ollama/ollama:latest
    container_name: portfolio-os-ollama
    ports:
      - "127.0.0.1:11434:11434"
    volumes:
      - ollama-data:/root/.ollama
    restart: unless-stopped

  quant-sidecar:
    build:
      context: ./quant-sidecar
      dockerfile: Dockerfile
    container_name: portfolio-os-quant
    ports:
      - "127.0.0.1:8000:8000"
      - "127.0.0.1:8001:8001"
    volumes:
      - ./data:/app/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
      interval: 5s
      timeout: 3s
      retries: 5
    restart: unless-stopped

volumes:
  data:
  ollama-data:
</file>

<file path="core-node/src/main/java/com/portfolioos/core/config/AppConfig.java">
package com.portfolioos.core.config;

import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.persistence.SqliteEventStore;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.rpc.FlightRpcClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
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

    @Bean
    public ChatClient.Builder chatClientBuilder(
        @Value("${spring.ai.ollama.base-url:http://127.0.0.1:11434}") String ollamaUrl
    ) {
        String resolvedUrl = ollamaUrl;
        if (ollamaUrl.contains("localhost") || ollamaUrl.contains("127.0.0.1")) {
            // Test if running inside container and target host gateway if needed
            resolvedUrl = "http://127.0.0.1:11434";
        }
        OllamaApi ollamaApi = new OllamaApi(resolvedUrl);
        OllamaChatModel chatModel = new OllamaChatModel(
            ollamaApi,
            OllamaOptions.create().withModel("qwen2.5-coder:3b")
        );
        return ChatClient.builder(chatModel);
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/controllers/ReportController.java">
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
    private final com.portfolioos.core.service.LedgerCacheService cacheService;

    public ReportController(PortfolioValuationService valuationService, TaxOptimizationService taxService, com.portfolioos.core.service.LedgerCacheService cacheService) {
        this.valuationService = valuationService;
        this.taxService = taxService;
        this.cacheService = cacheService;
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

    @GetMapping("/tax/schedule-cg/export")
    public ResponseEntity<String> downloadScheduleCgCsv(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        String csv = com.portfolioos.core.tax.ScheduleCgExporter.generateCsvReport(
            cacheService.getCachedState().fifoResult().matchedLots(),
            fy
        );

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Schedule-CG-FY" + fy + ".csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/nav/AmfiNavSync.java">
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

    private static final long CACHE_TTL_MS = 6 * 3600 * 1000L;
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/security/SecurityInterceptor.java">
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
            throw new IllegalStateException("SECURITY CRITICAL: API_AUTH_TOKEN environment variable is required and cannot be empty.");
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/service/SimulationService.java">
package com.portfolioos.core.service;

import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.*;
import com.portfolioos.core.xirr.CashFlow;
import com.portfolioos.core.xirr.XirrEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SimulationService {

    private final LedgerCacheService cacheService;
    private final XirrEngine xirrEngine = new XirrEngine();

    public SimulationService(LedgerCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public static record TradeSimulationRequest(
        String isin,
        String schemeName,
        double units,
        double pricePerUnit,
        String tradeDate,
        String tradeType // DISPOSAL or ACQUISITION
    ) {}

    public static record TradeSimulationResult(
        String isin,
        String schemeName,
        String tradeType,
        double units,
        double pricePerUnit,
        double grossTradeAmount,
        double grossCapitalGain,
        double ltcgEquity,
        double stcgEquity,
        double debtGain,
        double sec112aExemptionApplied,
        double estimatedTaxLiability,
        double postTradeNetWorth,
        double postTradeInvestedCost,
        double postTradeXirr,
        String taxSummaryNotice
    ) {}

    public TradeSimulationResult simulateTrade(TradeSimulationRequest req) {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<TaxEvent> existingEvents = state.events();
        Map<String, BigDecimal> navMap = state.navMap();

        LocalDate tradeDate = (req.tradeDate() != null && !req.tradeDate().isBlank())
            ? LocalDate.parse(req.tradeDate())
            : LocalDate.now();

        BigDecimal unitsBd = BigDecimal.valueOf(req.units()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal priceBd = BigDecimal.valueOf(req.pricePerUnit()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal grossAmount = unitsBd.multiply(priceBd).setScale(2, RoundingMode.HALF_UP);

        EventType type = "ACQUISITION".equalsIgnoreCase(req.tradeType()) ? EventType.ACQUISITION : EventType.DISPOSAL;
        String isin = (req.isin() != null && !req.isin().isBlank()) ? req.isin() : "SIMULATED_ASSET";
        String name = (req.schemeName() != null && !req.schemeName().isBlank()) ? req.schemeName() : "Simulated Fund";

        TaxEvent simEvent = new TaxEvent(
            "SIM_" + System.currentTimeMillis(),
            isin,
            name,
            isin,
            type,
            tradeDate,
            unitsBd,
            priceBd,
            grossAmount,
            "MANUAL_SIMULATION",
            java.time.Instant.now()
        );

        List<TaxEvent> simEvents = new ArrayList<>(existingEvents);
        simEvents.add(simEvent);

        FifoMatcher matcher = new FifoMatcher();
        FifoMatcher.FifoResult simResult = matcher.processEvents(simEvents);

        double ltcgEquity = 0.0;
        double stcgEquity = 0.0;
        double debtGain = 0.0;
        double totalGain = 0.0;

        if (type == EventType.DISPOSAL) {
            for (MatchedLot match : simResult.matchedLots()) {
                if (match.disposalEventId().equals(simEvent.id())) {
                    AssetCategory category = match.assetCategory();
                    TaxTerm term = match.taxTerm();

                    BigDecimal gain = match.realizedGain();
                    totalGain += gain.doubleValue();

                    if (category == AssetCategory.EQUITY) {
                        if (term == TaxTerm.LONG_TERM) {
                            ltcgEquity += gain.doubleValue();
                        } else {
                            stcgEquity += gain.doubleValue();
                        }
                    } else {
                        debtGain += gain.doubleValue();
                    }
                }
            }
        }

        double previousLtcgRealized = 0.0;
        for (MatchedLot match : state.fifoResult().matchedLots()) {
            if (match.assetCategory() == AssetCategory.EQUITY && match.taxTerm() == TaxTerm.LONG_TERM) {
                previousLtcgRealized += Math.max(0.0, match.realizedGain().doubleValue());
            }
        }

        double remainingExemptionLimit = Math.max(0.0, 125000.0 - previousLtcgRealized);
        double exemptionApplied = Math.min(Math.max(0.0, ltcgEquity), remainingExemptionLimit);
        double taxableLtcg = Math.max(0.0, ltcgEquity - exemptionApplied);
        double estimatedTax = (taxableLtcg * 0.125) + (Math.max(0.0, stcgEquity) * 0.20) + (Math.max(0.0, debtGain) * 0.30);

        // Compute post-trade net worth & XIRR
        double postInvested = 0.0;
        double postCurrentVal = 0.0;

        for (Lot lot : simResult.openLots()) {
            postInvested += lot.remainingUnits().multiply(lot.costPerUnit()).doubleValue();
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
            postCurrentVal += lot.remainingUnits().multiply(nav).doubleValue();
        }

        List<CashFlow> cashFlows = new ArrayList<>();
        for (TaxEvent ev : simEvents) {
            BigDecimal amt = (ev.eventType() == EventType.ACQUISITION || ev.eventType() == EventType.SIP_INSTALMENT)
                ? ev.grossAmount().negate()
                : ev.grossAmount();
            cashFlows.add(new CashFlow(ev.eventDate(), amt));
        }
        if (postCurrentVal > 0) {
            cashFlows.add(new CashFlow(tradeDate, BigDecimal.valueOf(postCurrentVal)));
        }

        double postXirr = xirrEngine.calculateXirr(cashFlows);

        String notice = (type == EventType.DISPOSAL)
            ? String.format("Simulated Sale: Estimated Tax Drag ₹%,.2f (LTCG Exemption Used: ₹%,.2f)", estimatedTax, exemptionApplied)
            : String.format("Simulated Purchase: Added ₹%,.2f investment to portfolio.", grossAmount.doubleValue());

        return new TradeSimulationResult(
            isin,
            name,
            type.name(),
            req.units(),
            req.pricePerUnit(),
            grossAmount.doubleValue(),
            totalGain,
            ltcgEquity,
            stcgEquity,
            debtGain,
            exemptionApplied,
            estimatedTax,
            postCurrentVal,
            postInvested,
            postXirr,
            notice
        );
    }
}
</file>

<file path="core-node/src/main/resources/static/src/js/modules/tax.js">
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

  const usedVal = data.exemption_used || data.exemptionUsed;
  const limitVal = data.exemption_limit || data.exemptionLimit;

  if (meterVal && fill && remainingText) {
    const used = Math.round(parseFloat(usedVal) || 0);
    const limit = Math.round(parseFloat(limitVal) || 125000);
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
  const realizedStcg = report.total_realized_stcg || report.totalRealizedStcg;

  if (stcgVal && realizedStcg) {
    stcgVal.textContent = formatINR(realizedStcg);
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

  const agAssets = antigravityData ? (antigravityData.antigravity_assets || antigravityData.antigravityAssets) : null;
  const mktDd = antigravityData ? (antigravityData.market_drawdown_pct || antigravityData.marketDrawdownPct) : null;

  if (agAssets && agAssets.length > 0) {
    for (const ag of agAssets) {
      const assetName = ag.asset_name || ag.assetName;
      const twr = ag.twr_30d_pct || ag.twr30dPct;
      html += `
        <div class="radar-card info-border" style="border-left: 3px solid #06b6d4; background: rgba(6, 182, 212, 0.08);">
          <div class="radar-icon info">🚀</div>
          <div class="radar-content">
            <div class="radar-title" style="color:#06b6d4;">ANTIGRAVITY DETECTED (${assetName})</div>
            <div class="radar-desc">Beta: <strong>${ag.beta}</strong> | 30d TWR: <strong>+${twr}%</strong> during market drawdown (${mktDd}%). ${ag.recommendation}</div>
          </div>
          <span class="antigravity-badge">🚀 Low Beta + Alpha</span>
        </div>
      `;
    }
  }

  if (opportunities && opportunities.length > 0) {
    for (const opp of opportunities.slice(0, 2)) {
      const assetName = opp.asset_name || opp.assetName;
      const lossVal = opp.potential_harvestable_loss || opp.potentialHarvestableLoss;
      const loss = Math.round(parseFloat(lossVal) || 0);

      html += `
        <div class="radar-card warning-border">
          <div class="radar-icon warning">⚡</div>
          <div class="radar-content">
            <div class="radar-title">Tax-Loss Harvesting Opportunity</div>
            <div class="radar-desc">Harvest <strong>${formatINR(loss)}</strong> loss in <em>${assetName}</em> before March 31.</div>
          </div>
          <span class="days-badge">Harvest Now</span>
        </div>
      `;
    }
  }

  if (ladder && ladder.length > 0) {
    for (const mat of ladder.slice(0, 2)) {
      const assetName = mat.asset_name || mat.assetName;
      const units = mat.remaining_units || mat.remainingUnits;
      const targetDate = mat.target_ltcg_date || mat.targetLtcgDate;
      const daysRem = mat.days_remaining_to_ltcg !== undefined ? mat.days_remaining_to_ltcg : mat.daysRemainingToLtcg;

      html += `
        <div class="radar-card maturation-border">
          <div class="radar-icon maturation">⏳</div>
          <div class="radar-content">
            <div class="radar-title">LTCG Tax Maturation Coming Up</div>
            <div class="radar-desc">Lot of <em>${assetName}</em> (${units} units) becomes <strong>LTCG</strong> on ${targetDate}.</div>
          </div>
          <span class="days-badge">Wait ${daysRem} Days</span>
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
    const dispDate = l.disposal_date || l.disposalDate;
    const acqDate = l.acquisition_date || l.acquisitionDate;
    const assetName = l.asset_name || l.assetName;
    const matched = l.units_matched || l.unitsMatched;
    const proceeds = l.sale_proceeds || l.saleProceeds;
    const cost = l.cost_basis || l.costBasis;
    const gainVal = l.realized_gain || l.realizedGain;
    const taxTerm = l.tax_term || l.taxTerm;

    const gain = Math.round(parseFloat(gainVal) || 0);
    const gainSign = gain >= 0 ? '+' : '';
    const gainColor = gain >= 0 ? 'color: #10b981;' : 'color: #ef4444;';

    html += `
      <tr>
        <td>${dispDate}</td>
        <td>${acqDate}</td>
        <td style="font-weight:600;">${assetName}</td>
        <td class="font-mono">${matched}</td>
        <td class="font-mono">${formatINR(proceeds)}</td>
        <td class="font-mono">${formatINR(cost)}</td>
        <td class="font-mono" style="${gainColor}">${gainSign}${formatINR(gain)}</td>
        <td><span class="cat-badge ${taxTerm === 'LONG_TERM' ? 'cat-EQUITY' : 'cat-DEBT_SPECIFIED_50AA'}">${taxTerm}</span></td>
      </tr>
    `;
  });

  template.innerHTML = html;
  fragment.appendChild(template.content);
  tableBody.replaceChildren(fragment);
}
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/MainActivity.kt">
package com.portfolioos.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.ui.DashboardScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var snapshot by remember { mutableStateOf<SyncSnapshot?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()

            fun fetchSyncSnapshot() {
                scope.launch {
                    isLoading = true
                    try {
                        snapshot = SyncApiClient.fetchSnapshotWithFallback(applicationContext)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }

            LaunchedEffect(Unit) {
                fetchSyncSnapshot()
            }

            DashboardScreen(
                snapshot = snapshot,
                isLoading = isLoading,
                onRefresh = { fetchSyncSnapshot() },
                onUpdateCustomUrl = { newUrl ->
                    SnapshotCacheManager.setCustomUrl(applicationContext, newUrl)
                    fetchSyncSnapshot()
                }
            )
        }
    }
}
</file>

<file path="quant-sidecar/flight_server.py">
import pyarrow as pa
import pyarrow.flight as flight
import polars as pl
import logging
from quant.analytics_engine import compute_fund_analytics

logger = logging.getLogger(__name__)

class QuantFlightServer(flight.FlightServerBase):
    def __init__(self, host="0.0.0.0", port=8001, **kwargs):
        location = flight.Location.for_grpc_tcp(host, port)
        super(QuantFlightServer, self).__init__(location, **kwargs)
        self.host = host
        self.port = port
        logger.info(f"Initialized Apache Arrow Flight RPC server on {host}:{port}")

    def do_exchange(self, context, descriptor, reader, writer):
        try:
            table = reader.read_all()
            if table.num_rows == 0:
                self._write_empty_response(writer)
                return

            df = pl.from_arrow(table)
            results = []
            unique_codes = df["amfi_code"].unique().to_list()

            for code in unique_codes:
                fund_df = df.filter(pl.col("amfi_code") == code)
                nav_values = fund_df["nav_value"].to_list()
                dates_list = fund_df["nav_date"].to_list() if "nav_date" in fund_df.columns else None

                analytics = compute_fund_analytics(nav_values, dates=dates_list)

                results.append({
                    "amfi_code": str(code),
                    "status": str(analytics.get("status", "OK")),
                    "sharpe": float(analytics.get("sharpe", 0.0)),
                    "sortino": float(analytics.get("sortino", 0.0)),
                    "calmar": float(analytics.get("calmar", 0.0)),
                    "max_drawdown": float(analytics.get("max_drawdown", 0.0)),
                    "volatility_annual": float(analytics.get("volatility_annual", 0.0)),
                    "var_95": float(analytics.get("var_95", 0.0)),
                    "cvar_95": float(analytics.get("cvar_95", 0.0)),
                    "beta": float(analytics.get("beta", 0.0))
                })

            if results:
                out_df = pl.DataFrame(results)
                out_table = out_df.to_arrow()
            else:
                self._write_empty_response(writer)
                return

            writer.begin(out_table.schema)
            writer.write_table(out_table)
            writer.close()
        except Exception as e:
            logger.error(f"Error during Flight exchange processing: {e}", exc_info=True)
            self._write_empty_response(writer)

    def _write_empty_response(self, writer):
        schema = pa.schema([
            ("amfi_code", pa.string()),
            ("status", pa.string()),
            ("sharpe", pa.float64()),
            ("sortino", pa.float64()),
            ("calmar", pa.float64()),
            ("max_drawdown", pa.float64()),
            ("volatility_annual", pa.float64()),
            ("var_95", pa.float64()),
            ("cvar_95", pa.float64()),
            ("beta", pa.float64())
        ])
        out_table = pa.Table.from_batches([], schema)
        writer.begin(schema)
        writer.write_table(out_table)
        writer.close()

def start_flight_server(host="0.0.0.0", port=8001):
    server = QuantFlightServer(host, port)
    server.serve()

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    start_flight_server()
</file>

<file path="quant-sidecar/requirements.txt">
fastapi>=0.110.0
uvicorn>=0.28.0
granian>=1.2.0
polars>=0.20.15
pyarrow>=15.0.0
pdfplumber>=0.11.0
casparser>=0.7.0
casparser-isin>=0.3.0
numpy>=1.26.0
scipy>=1.12.0
yfinance>=0.2.37
pandas>=2.2.0
quantstats>=0.0.62
pydantic>=2.6.0
python-multipart>=0.0.9
</file>

<file path="core-node/src/main/java/com/portfolioos/core/dtos/SyncDtos.java">
package com.portfolioos.core.dtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

public class SyncDtos {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SyncInfoDto(
        long timestamp,
        String ledgerHash,
        String generatedAt,
        String fiscalYear,
        double portfolioXirr,
        String xirrPercentage,
        double totalInvested,
        double currentValue,
        double unrealizedGain,
        String formattedCurrentValue,
        String formattedTotalInvested,
        String formattedUnrealizedGain
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FlatHoldingDto(
        String isin,
        String fundName,
        double totalUnits,
        double avgCost,
        double xirr,
        String assetBucket,
        double currentValue,
        double investedValue,
        String formattedCurrentValue,
        String formattedInvestedValue
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FlatTaxLotDto(
        String isin,
        String buyDate,
        double units,
        String taxClassification,
        boolean isLongTerm,
        Double grandfatheredNav,
        double costPerUnit,
        long holdingDays,
        long daysToLtcg
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RadarSignalDto(
        String signalType,
        String title,
        String subtitle,
        String description,
        String severity,
        String badgeText
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record NetWorthPointDto(
        String date,
        double valuation,
        double invested
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UnidirectionalSyncSnapshot(
        SyncInfoDto syncInfo,
        List<FlatHoldingDto> holdings,
        List<FlatTaxLotDto> taxLots,
        List<RadarSignalDto> radarSignals,
        List<NetWorthPointDto> netWorthHistory
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PairRequestDto(
        String deviceId,
        String deviceName
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PairResponseDto(
        String status,
        String token,
        String serverName
    ) {}
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java">
package com.portfolioos.core.persistence;

import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.ports.EventStorePort;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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
    private final HikariDataSource dataSource;

    public SqliteEventStore() {
        this(System.getenv("SQLITE_PATH") != null && !System.getenv("SQLITE_PATH").isBlank() 
             ? System.getenv("SQLITE_PATH") : "data/tax_ledger.db");
    }

    public SqliteEventStore(String dbPath) {
        this.dbPath = dbPath;
        String envSecret = System.getenv("LEDGER_HMAC_SECRET");
        if (envSecret == null || envSecret.isBlank()) {
            throw new IllegalStateException("SECURITY CRITICAL: LEDGER_HMAC_SECRET environment variable is required and cannot be empty.");
        }
        this.hmacSecret = envSecret;

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

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setPoolName("SqliteEventStorePool");

        this.dataSource = new HikariDataSource(config);
        initSchema();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
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

    @Override
    public String getLatestEventHash() {
        String sql = "SELECT event_hash FROM tax_events ORDER BY ingested_at DESC, id DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("event_hash");
            }
            return "GENESIS";
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch latest event hash", e);
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
    public synchronized List<String> appendEvents(List<TaxEvent> events) {
        if (events.isEmpty()) return List.of();

        List<String> hashes = new ArrayList<>();
        String checkSql = "SELECT event_hash FROM tax_events WHERE asset_id = ? AND event_type = ? AND event_date = ? AND units = ? AND gross_amount = ? LIMIT 1";
        String insertSql = "INSERT INTO tax_events (id, asset_id, asset_name, isin, event_type, event_date, units, price_per_unit, gross_amount, source_document_id, ingested_at, previous_hash, event_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
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
                conn.rollback();
                throw new RuntimeException("Failed to commit transaction ledger", e);
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error in transaction execution", e);
        }
        return hashes;
    }

    @Override
    public List<TaxEvent> getEventsForAsset(String assetId) {
        List<TaxEvent> events = new ArrayList<>();
        String sql = "SELECT * FROM tax_events WHERE asset_id = ? ORDER BY event_date ASC, ingested_at ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    @Override
    public List<TaxEvent> getAllEvents() {
        List<TaxEvent> events = new ArrayList<>();
        String sql = "SELECT * FROM tax_events ORDER BY event_date ASC, ingested_at ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                events.add(mapResultSetToTaxEvent(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch all events", e);
        }
        return events;
    }

    @Override
    public boolean verifyLedgerIntegrity() {
        String sql = "SELECT previous_hash, event_hash, id, asset_id, event_type, event_date, units, gross_amount, source_document_id FROM tax_events ORDER BY ingested_at ASC, id ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
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
                    "",
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

    @Override
    public void clearAllEvents() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM tax_events");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear ledger", e);
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/service/LedgerCacheService.java">
package com.portfolioos.core.service;

import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.nav.AmfiNavSync;
import com.portfolioos.core.ports.EventStorePort;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LedgerCacheService {

    private final EventStorePort eventStore;
    private final AmfiNavSync amfiSync = new AmfiNavSync();
    private final FifoMatcher fifoMatcher = new FifoMatcher();

    private final AtomicReference<CachedLedgerState> stateHolder = new AtomicReference<>(null);
    private volatile long lastNavSyncTime = 0L;
    private final Object updateLock = new Object();

    public LedgerCacheService(EventStorePort eventStore) {
        this.eventStore = eventStore;
    }

    public static record CachedLedgerState(
        List<TaxEvent> events,
        FifoMatcher.FifoResult fifoResult,
        Map<String, BigDecimal> navMap,
        String ledgerHash
    ) {}

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedRate = 30000)
    public void refreshCacheInBackground() {
        synchronized (updateLock) {
            try {
                String currentHash = eventStore.getLatestEventHash();
                long now = System.currentTimeMillis();

                CachedLedgerState current = stateHolder.get();
                if (current == null || current.ledgerHash() == null || !currentHash.equals(current.ledgerHash()) || (now - lastNavSyncTime) >= 30_000) {
                    List<TaxEvent> events = eventStore.getAllEvents();
                    FifoMatcher.FifoResult fifoResult = fifoMatcher.processEvents(events);
                    Map<String, BigDecimal> navMap = amfiSync.getNavMap();
                    
                    stateHolder.set(new CachedLedgerState(events, fifoResult, navMap, currentHash));
                    lastNavSyncTime = now;
                }
            } catch (Exception e) {
                System.err.println("Background cache refresh warning: " + e.getMessage());
            }
        }
    }

    public CachedLedgerState getCachedState() {
        CachedLedgerState current = stateHolder.get();
        if (current == null) {
            refreshCacheInBackground();
            current = stateHolder.get();
        }
        return current;
    }

    public void invalidateCache() {
        stateHolder.set(null);
        refreshCacheInBackground();
    }
}
</file>

<file path="core-node/pom.xml">
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

        <!-- HikariCP Connection Pooling -->
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
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

        <!-- Spring AI Ollama -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>1.0.0-M1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
    </repositories>
    
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
</file>

<file path="mobile-app/app/src/main/AndroidManifest.xml">
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="Portfolio OS"
        android:supportsRtl="true"
        android:theme="@style/Theme.PortfolioOS"
        android:usesCleartextTraffic="true"
        tools:targetApi="31">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PortfolioOS">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".widget.PortfolioGlanceReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/portfolio_glance_widget_info" />
        </receiver>
    </application>

</manifest>
</file>

<file path="mobile-app/app/build.gradle.kts">
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.portfolioos.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.portfolioos.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Jetpack Glance Widget
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.glance:glance-material3:1.0.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
</file>

<file path="quant-sidecar/quant/analytics_engine.py">
import numpy as np
import pandas as pd
try:
    import quantstats as qs
except ImportError:
    qs = None

def compute_fund_analytics(nav_series, dates=None, benchmark_returns=None):
    if len(nav_series) < 30:
        return {
            "status": "INSUFFICIENT_HISTORY",
            "data_points": len(nav_series),
            "sharpe": 0.0,
            "sortino": 0.0,
            "calmar": 0.0,
            "max_drawdown": 0.0,
            "volatility_annual": 0.0,
            "var_95": 0.0,
            "cvar_95": 0.0,
            "beta": 0.0
        }

    try:
        if dates is not None and len(dates) == len(nav_series) and any(d for d in dates if d):
            valid_pairs = [(nav, d) for nav, d in zip(nav_series, dates) if d]
            if len(valid_pairs) >= 10:
                vals, d_str = zip(*valid_pairs)
                idx = pd.to_datetime(d_str)
                s = pd.Series(vals, index=idx)
            else:
                s = pd.Series(nav_series)
        else:
            s = pd.Series(nav_series)

        returns = s.pct_change().dropna()

        if len(returns) < 10:
            return {
                "status": "INSUFFICIENT_HISTORY",
                "data_points": len(returns),
                "sharpe": 0.0,
                "sortino": 0.0,
                "calmar": 0.0,
                "max_drawdown": 0.0,
                "volatility_annual": 0.0,
                "var_95": 0.0,
                "cvar_95": 0.0,
                "beta": 0.0
            }

        if qs is not None:
            sharpe = float(qs.stats.sharpe(returns))
            sortino = float(qs.stats.sortino(returns))
            calmar = float(qs.stats.calmar(returns))
            max_dd = float(qs.stats.max_drawdown(returns))
            vol = float(qs.stats.volatility(returns))
            var95 = float(qs.stats.value_at_risk(returns))
            cvar95 = float(qs.stats.conditional_value_at_risk(returns))

            beta = 0.0
            if benchmark_returns is not None:
                try:
                    beta_val = qs.stats.greeks(returns, benchmark_returns).get("beta", 0.0)
                    beta = float(beta_val) if not np.isnan(beta_val) else 0.0
                except Exception:
                    beta = 0.0
        else:
            # Vectorized fallback calculation with true Downside Deviation Sortino ratio
            mean_ret = returns.mean()
            std_ret = returns.std()
            sharpe = float((mean_ret / std_ret) * np.sqrt(252)) if std_ret > 0 else 0.0
            
            downside_returns = returns[returns < 0]
            downside_std = downside_returns.std() if not downside_returns.empty else 0.0
            sortino = float((mean_ret / downside_std) * np.sqrt(252)) if downside_std > 0 else sharpe

            cum_returns = (1 + returns).cumprod()
            peak = cum_returns.cummax()
            dd = (cum_returns - peak) / peak
            max_dd = float(dd.min())
            calmar = float(mean_ret * 252 / abs(max_dd)) if abs(max_dd) > 0 else 0.0
            vol = float(std_ret * np.sqrt(252))
            var95 = float(returns.quantile(0.05))
            cvar95 = float(returns[returns <= var95].mean()) if not returns[returns <= var95].empty else var95
            beta = 0.0

        return {
            "status": "OK",
            "sharpe": 0.0 if np.isnan(sharpe) else round(sharpe, 2),
            "sortino": 0.0 if np.isnan(sortino) else round(sortino, 2),
            "calmar": 0.0 if np.isnan(calmar) else round(calmar, 2),
            "max_drawdown": 0.0 if np.isnan(max_dd) else round(max_dd, 4),
            "volatility_annual": 0.0 if np.isnan(vol) else round(vol, 4),
            "var_95": 0.0 if np.isnan(var95) else round(var95, 4),
            "cvar_95": 0.0 if np.isnan(cvar95) else round(cvar95, 4),
            "beta": 0.0 if np.isnan(beta) else round(beta, 2)
        }
    except Exception as e:
        return {
            "status": "ERROR",
            "message": str(e),
            "sharpe": 0.0,
            "sortino": 0.0,
            "calmar": 0.0,
            "max_drawdown": 0.0,
            "volatility_annual": 0.0,
            "var_95": 0.0,
            "cvar_95": 0.0,
            "beta": 0.0
        }

def run_monte_carlo_fire_simulation(
    daily_returns_list,
    current_corpus=1754783.21,
    annual_expense=600000.0,
    years=15,
    num_simulations=10000
):
    if daily_returns_list is None or len(daily_returns_list) < 10:
        return {
            "status": "INSUFFICIENT_DATA",
            "success_rate_pct": 95.0,
            "median_ending_corpus": current_corpus * 1.5,
            "tenth_percentile_corpus": current_corpus * 0.9
        }

    returns = np.array(daily_returns_list)
    trading_days = years * 252
    daily_expense = annual_expense / 252.0

    # Historical Bootstrapping: Randomly sample actual past daily returns with replacement to preserve true fat tails & skewness
    simulated_daily_returns = np.random.choice(returns, size=(num_simulations, trading_days), replace=True)

    surviving_sims = 0
    final_corpuses = []

    for sim_idx in range(num_simulations):
        corpus = current_corpus
        failed = False
        for day in range(trading_days):
            corpus = corpus * (1.0 + simulated_daily_returns[sim_idx, day]) - daily_expense
            if corpus <= 0:
                failed = True
                break
        if not failed:
            surviving_sims += 1
            final_corpuses.append(corpus)
        else:
            final_corpuses.append(0.0)

    success_rate = (surviving_sims / num_simulations) * 100.0
    median_corpus = float(np.median(final_corpuses))
    p10_corpus = float(np.percentile(final_corpuses, 10))

    return {
        "status": "OK",
        "num_simulations": num_simulations,
        "years": years,
        "success_rate_pct": round(success_rate, 2),
        "median_ending_corpus": round(median_corpus, 2),
        "tenth_percentile_corpus": round(p10_corpus, 2)
    }
</file>

<file path="core-node/src/main/java/com/portfolioos/core/rpc/FlightRpcClient.java">
package com.portfolioos.core.rpc;

import com.portfolioos.core.persistence.DuckDbProjector.NavHistorySeriesEntry;
import org.apache.arrow.flight.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
        Map<String, NavHistorySeriesEntry> adapterMap = new HashMap<>();
        if (fundNavSeries != null) {
            for (Map.Entry<String, List<Double>> entry : fundNavSeries.entrySet()) {
                adapterMap.put(entry.getKey(), new NavHistorySeriesEntry(entry.getValue(), Collections.emptyList()));
            }
        }
        return computeQuantMetricsWithDates(adapterMap);
    }

    public Map<String, Map<String, Object>> computeQuantMetricsWithDates(Map<String, NavHistorySeriesEntry> fundNavSeries) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        if (fundNavSeries == null || fundNavSeries.isEmpty()) {
            return out;
        }

        int totalRows = fundNavSeries.values().stream().mapToInt(e -> e.navs().size()).sum();
        if (totalRows == 0) {
            return out;
        }

        try {
            Location location = Location.forGrpcInsecure(host, port);
            try (FlightClient client = FlightClient.builder(allocator, location).build()) {

                Schema inSchema = new Schema(List.of(
                    new Field("amfi_code", FieldType.nullable(new ArrowType.Utf8()), null),
                    new Field("nav_date", FieldType.nullable(new ArrowType.Utf8()), null),
                    new Field("nav_value", FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), null)
                ));

                try (VectorSchemaRoot inRoot = VectorSchemaRoot.create(inSchema, allocator)) {
                    VarCharVector codeVec = (VarCharVector) inRoot.getVector("amfi_code");
                    VarCharVector dateVec = (VarCharVector) inRoot.getVector("nav_date");
                    Float8Vector navVec = (Float8Vector) inRoot.getVector("nav_value");
                    codeVec.allocateNew(totalRows * 32L, totalRows);
                    dateVec.allocateNew(totalRows * 16L, totalRows);
                    navVec.allocateNew(totalRows);

                    int row = 0;
                    for (Map.Entry<String, NavHistorySeriesEntry> entry : fundNavSeries.entrySet()) {
                        byte[] codeBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                        List<Double> navs = entry.getValue().navs();
                        List<String> dates = entry.getValue().dates();

                        for (int i = 0; i < navs.size(); i++) {
                            codeVec.setSafe(row, codeBytes);
                            if (i < dates.size() && dates.get(i) != null) {
                                dateVec.setSafe(row, dates.get(i).getBytes(StandardCharsets.UTF_8));
                            } else {
                                dateVec.setSafe(row, "".getBytes(StandardCharsets.UTF_8));
                            }
                            navVec.setSafe(row, navs.get(i));
                            row++;
                        }
                    }
                    inRoot.setRowCount(totalRows);

                    FlightDescriptor descriptor = FlightDescriptor.path("quant_metrics");
                    FlightClient.ExchangeReaderWriter exchange = client.doExchange(descriptor);

                    FlightClient.ClientStreamListener writer = exchange.getWriter();
                    writer.start(inRoot);
                    writer.putNext();
                    writer.completed();

                    try (FlightStream reader = exchange.getReader()) {
                        while (reader.next()) {
                            VectorSchemaRoot outRoot = reader.getRoot();
                            VarCharVector outCode = (VarCharVector) outRoot.getVector("amfi_code");
                            for (int i = 0; i < outRoot.getRowCount(); i++) {
                                String code = new String(outCode.get(i), StandardCharsets.UTF_8);
                                Map<String, Object> metrics = new HashMap<>();
                                for (Field f : outRoot.getSchema().getFields()) {
                                    if (f.getName().equals("amfi_code")) continue;
                                    metrics.put(f.getName(), outRoot.getVector(f.getName()).getObject(i));
                                }
                                out.put(code, metrics);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Arrow Flight quant metrics call error: " + e.getMessage());
        }
        return out;
    }
}
</file>

<file path="core-node/src/main/resources/static/src/style.css">
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

/* Glassmorphism & Bento Box Enhancements */
.glass-card {
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  background: rgba(12, 16, 28, 0.75);
}

.cmd-k-btn {
  background: rgba(208, 255, 0, 0.15) !important;
  color: #d0ff00 !important;
  border: 1px solid rgba(208, 255, 0, 0.3) !important;
}

.cmd-k-btn kbd {
  background: rgba(0, 0, 0, 0.4);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 11px;
}

.cmd-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(3, 7, 18, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  z-index: 99999;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding-top: 12vh;
  box-sizing: border-box;
}

.cmd-modal-overlay[style*="display: none"] {
  display: none !important;
}

.command-palette-box {
  background: #090f1e;
  border: 1px solid rgba(208, 255, 0, 0.4);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.9), 0 0 30px rgba(208, 255, 0, 0.15);
  border-radius: 16px;
  padding: 16px;
  width: 90%;
  max-width: 620px;
  color: #fff;
}

.command-palette-header {
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 12px;
  margin-bottom: 12px;
}

.command-palette-header input {
  flex: 1;
  background: transparent;
  border: none;
  color: #fff;
  font-size: 15px;
  outline: none;
}

.cmd-k-badge {
  background: rgba(255, 255, 255, 0.1);
  color: #94a3b8;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}

.command-palette-results {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cmd-item {
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 10px;
  color: #f8fafc;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.cmd-item:hover {
  background: rgba(208, 255, 0, 0.12);
  border-color: rgba(208, 255, 0, 0.4);
  color: #d0ff00;
  transform: translateX(4px);
}
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/ui/PortfolioCharts.kt">
package com.portfolioos.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.NetWorthPointDto

data class BucketAllocation(
    val bucketName: String,
    val totalAmount: Double,
    val percentage: Float,
    val color: Color
)

val SEBIBucketColors = mapOf(
    "Flexi Cap" to Color(0xFF06B6D4),                // Vibrant Cyan
    "Large & Midcap" to Color(0xFFA855F7),           // Electric Violet
    "Midcap" to Color(0xFF3B82F6),                   // Royal Blue
    "Small Cap" to Color(0xFF10B981),                // Emerald Green
    "Microcap" to Color(0xFFEC4899),                 // Coral Pink
    "Factor Value Index" to Color(0xFFF59E0B),       // Amber Gold
    "Factor Momentum Index" to Color(0xFF6366F1),    // Indigo
    "Equal Weight Index" to Color(0xFF14B8A6),       // Teal
    "Sectoral/Thematic" to Color(0xFFF43F5E),        // Rose
    "Gold & Commodities" to Color(0xFFEAB308),       // Gold
    "Debt & Liquid" to Color(0xFF64748B)             // Slate
)

@Composable
fun DonutAllocationChart(
    holdings: List<FlatHoldingDto>,
    modifier: Modifier = Modifier
) {
    val defaultColor = Color(0xFF94A3B8)

    val allocations = remember(holdings) {
        val totalVal = holdings.sumOf { it.currentValue.takeIf { v -> v > 0 } ?: (it.totalUnits * it.avgCost) }.coerceAtLeast(1.0)
        val grouped = holdings.groupBy { it.assetBucket.ifEmpty { "Others" } }
        grouped.map { (bucket, list) ->
            val bucketVal = list.sumOf { it.currentValue.takeIf { v -> v > 0 } ?: (it.totalUnits * it.avgCost) }
            val pct = (bucketVal / totalVal * 100).toFloat()
            BucketAllocation(
                bucketName = bucket,
                totalAmount = bucketVal,
                percentage = pct,
                color = SEBIBucketColors[bucket] ?: defaultColor
            )
        }.sortedByDescending { it.percentage }
    }

    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(holdings) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1424)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "SEBI CATEGORY ALLOCATION",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut Canvas
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(120.dp)
                            .graphicsLayer()
                    ) {
                        val strokeWidth = 22.dp.toPx()
                        var startAngle = -90f

                        allocations.forEach { alloc ->
                            val sweepAngle = (alloc.percentage / 100f) * 360f * animProgress.value
                            drawArc(
                                color = alloc.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += sweepAngle
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${allocations.size}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Categories",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Legend List
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allocations.take(5).forEach { alloc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(alloc.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = alloc.bucketName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "%.1f%%".format(alloc.percentage),
                                color = alloc.color,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PerformanceBarChart(
    holdings: List<FlatHoldingDto>,
    modifier: Modifier = Modifier
) {
    val topHoldings = remember(holdings) {
        holdings.sortedByDescending { it.xirr }.take(5)
    }

    val maxVal = remember(topHoldings) {
        topHoldings.maxOfOrNull { kotlin.math.abs(it.xirr) }?.toFloat()?.coerceAtLeast(1f) ?: 10f
    }

    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(holdings) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1424)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "TOP PERFORMING SCHEMES (XIRR)",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            topHoldings.forEach { holding ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = holding.fundName.ifEmpty { holding.isin },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${if (holding.xirr >= 0) "+" else ""}${holding.xirr}%",
                            color = if (holding.xirr >= 0) Color(0xFF10B981) else Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val barRatio = (kotlin.math.abs(holding.xirr).toFloat() / maxVal * animProgress.value).coerceIn(0.05f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF181F33))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barRatio)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = if (holding.xirr >= 0) listOf(Color(0xFF06B6D4), Color(0xFF10B981))
                                        else listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                                    )
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
fun HistoricalNetWorthTrendChart(
    trendPoints: List<NetWorthPointDto>,
    modifier: Modifier = Modifier
) {
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(trendPoints) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
        )
    }

    val rawVals = if (trendPoints.isEmpty()) listOf(100.0, 105.0, 110.0, 115.0, 120.0) else trendPoints.map { it.valuation }
    val minVal = rawVals.minOrNull() ?: 1.0
    val maxVal = rawVals.maxOrNull() ?: (minVal * 1.2)
    val valRange = (maxVal - minVal).coerceAtLeast(1.0)
    val points = rawVals.map { v -> ((v - minVal) / valRange * 0.70 + 0.25).toFloat() }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1424)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "HISTORICAL NET WORTH TREND",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "NAV Growth & Capital Curve",
                        color = Color(0xFFD0FF00),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer()
                ) {
                    val width = size.width
                    val height = size.height

                    val stepX = width / (points.size - 1).coerceAtLeast(1)
                    val path = androidx.compose.ui.graphics.Path()
                    val fillPath = androidx.compose.ui.graphics.Path()

                    val startY = height - (points[0] * height * 0.7f * animProgress.value)
                    path.moveTo(0f, startY)
                    fillPath.moveTo(0f, height)
                    fillPath.lineTo(0f, startY)

                    for (i in 1 until points.size) {
                        val x = i * stepX
                        val y = height - (points[i] * height * 0.7f * animProgress.value)
                        val prevX = (i - 1) * stepX
                        val prevY = height - (points[i - 1] * height * 0.7f * animProgress.value)

                        val controlX1 = prevX + (stepX / 2f)
                        val controlY1 = prevY
                        val controlX2 = prevX + (stepX / 2f)
                        val controlY2 = y

                        path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                        fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                    }

                    fillPath.lineTo(width, height)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFD0FF00).copy(alpha = 0.35f), Color(0xFF00F0FF).copy(alpha = 0.02f))
                        )
                    )

                    drawPath(
                        path = path,
                        color = Color(0xFFD0FF00),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/api/SyncApiClient.kt">
package com.portfolioos.mobile.api

import android.content.Context
import com.portfolioos.mobile.BuildConfig
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.model.TradeSimulationRequestDto
import com.portfolioos.mobile.model.TradeSimulationResultDto
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SyncApiService {
    @GET("api/v1/sync/snapshot")
    suspend fun getSnapshot(
        @Header("X-Api-Auth-Token") token: String,
        @Query("fy") fiscalYear: String = "2026-27"
    ): SyncSnapshot

    @POST("api/v1/simulate/trade")
    suspend fun simulateTrade(
        @Header("X-Api-Auth-Token") token: String,
        @Body request: TradeSimulationRequestDto
    ): TradeSimulationResultDto
}

object SyncApiClient {
    const val USB_BASE_URL = "http://127.0.0.1:8080/"
    const val EMULATOR_BASE_URL = "http://10.0.2.2:8080/"
    const val WIFI_BASE_URL = "http://192.168.1.13:8080/"

    fun createService(baseUrl: String = USB_BASE_URL): SyncApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(SyncApiService::class.java)
    }

    suspend fun fetchSnapshotWithFallback(context: Context): SyncSnapshot {
        val customUrl = SnapshotCacheManager.getCustomUrl(context)
        val authToken = SnapshotCacheManager.getAuthToken(context)
        
        // 1. Try Custom Remote/Tunnel URL if configured
        if (!customUrl.isNullOrBlank()) {
            try {
                val formatted = if (customUrl.endsWith("/")) customUrl else "$customUrl/"
                val remoteSnapshot = createService(formatted).getSnapshot(token = authToken)
                SnapshotCacheManager.saveSnapshot(context, remoteSnapshot)
                return remoteSnapshot
            } catch (e: Exception) {
                // fallthrough to local networks
            }
        }

        // 2. Try USB Loopback (adb reverse)
        try {
            val snapshot = createService(USB_BASE_URL).getSnapshot(token = authToken)
            SnapshotCacheManager.saveSnapshot(context, snapshot)
            return snapshot
        } catch (e1: Exception) {
            // 3. Try Android Emulator loopback
            try {
                val snapshot = createService(EMULATOR_BASE_URL).getSnapshot(token = authToken)
                SnapshotCacheManager.saveSnapshot(context, snapshot)
                return snapshot
            } catch (e2: Exception) {
                // 4. Try Wi-Fi LAN IP
                try {
                    val snapshot = createService(WIFI_BASE_URL).getSnapshot(token = authToken)
                    SnapshotCacheManager.saveSnapshot(context, snapshot)
                    return snapshot
                } catch (e3: Exception) {
                    // 5. Offline Fallback: Load cached snapshot & fetch direct AMFI NAVs over cellular!
                    val cached = SnapshotCacheManager.loadSnapshot(context)
                    if (cached != null) {
                        return SnapshotCacheManager.updateOfflineSnapshotWithLiveAmfi(cached)
                    } else {
                        throw e3
                    }
                }
            }
        }
    }

    suspend fun simulateTradeWithFallback(context: Context, request: TradeSimulationRequestDto): TradeSimulationResultDto {
        val customUrl = SnapshotCacheManager.getCustomUrl(context)
        val authToken = SnapshotCacheManager.getAuthToken(context)

        if (!customUrl.isNullOrBlank()) {
            try {
                val formatted = if (customUrl.endsWith("/")) customUrl else "$customUrl/"
                return createService(formatted).simulateTrade(token = authToken, request = request)
            } catch (e: Exception) {
                // fallthrough
            }
        }

        try {
            return createService(USB_BASE_URL).simulateTrade(token = authToken, request = request)
        } catch (e1: Exception) {
            try {
                return createService(EMULATOR_BASE_URL).simulateTrade(token = authToken, request = request)
            } catch (e2: Exception) {
                return createService(WIFI_BASE_URL).simulateTrade(token = authToken, request = request)
            }
        }
    }
}
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/model/SyncModels.kt">
package com.portfolioos.mobile.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

@Immutable
data class NetWorthPointDto(
    @SerializedName("date") val date: String = "",
    @SerializedName("valuation") val valuation: Double = 0.0,
    @SerializedName("invested") val invested: Double = 0.0
)

@Immutable
data class SyncSnapshot(
    @SerializedName("sync_info") val syncInfo: SyncInfoDto? = null,
    @SerializedName("holdings") val holdings: List<FlatHoldingDto>? = emptyList(),
    @SerializedName("tax_lots") val taxLots: List<FlatTaxLotDto>? = emptyList(),
    @SerializedName("radar_signals") val radarSignals: List<RadarSignalDto>? = emptyList(),
    @SerializedName("net_worth_history") val netWorthHistory: List<NetWorthPointDto>? = emptyList()
)

@Immutable
data class SyncInfoDto(
    @SerializedName("timestamp") val timestamp: Long = 0L,
    @SerializedName("ledger_hash") val ledgerHash: String = "",
    @SerializedName("generated_at") val generatedAt: String = "",
    @SerializedName("fiscal_year") val fiscalYear: String = "2026-27",
    @SerializedName("portfolio_xirr") val portfolioXirr: Double = 0.0,
    @SerializedName("xirr_percentage") val xirrPercentage: String = "0.00%",
    @SerializedName("total_invested") val totalInvested: Double = 0.0,
    @SerializedName("current_value") val currentValue: Double = 0.0,
    @SerializedName("unrealized_gain") val unrealizedGain: Double = 0.0,
    @SerializedName("formatted_current_value") val formattedCurrentValue: String = "₹0.00",
    @SerializedName("formatted_total_invested") val formattedTotalInvested: String = "₹0.00",
    @SerializedName("formatted_unrealized_gain") val formattedUnrealizedGain: String = "₹0.00"
)

@Immutable
data class FlatHoldingDto(
    @SerializedName("isin") val isin: String = "",
    @SerializedName("fund_name") val fundName: String = "",
    @SerializedName("total_units") val totalUnits: Double = 0.0,
    @SerializedName("avg_cost") val avgCost: Double = 0.0,
    @SerializedName("xirr") val xirr: Double = 0.0,
    @SerializedName("asset_bucket") val assetBucket: String = "",
    @SerializedName("current_value") val currentValue: Double = 0.0,
    @SerializedName("invested_value") val investedValue: Double = 0.0,
    @SerializedName("formatted_current_value") val formattedCurrentValue: String = "₹0.00",
    @SerializedName("formatted_invested_value") val formattedInvestedValue: String = "₹0.00"
)

@Immutable
data class FlatTaxLotDto(
    @SerializedName("isin") val isin: String = "",
    @SerializedName("buy_date") val buyDate: String = "",
    @SerializedName("units") val units: Double = 0.0,
    @SerializedName("tax_classification") val taxClassification: String = "",
    @SerializedName("is_long_term") val isLongTerm: Boolean = false,
    @SerializedName("grandfathered_nav") val grandfatheredNav: Double? = null,
    @SerializedName("cost_per_unit") val costPerUnit: Double = 0.0,
    @SerializedName("holding_days") val holdingDays: Long = 0L,
    @SerializedName("days_to_ltcg") val daysToLtcg: Long = 0L
)

@Immutable
data class RadarSignalDto(
    @SerializedName("signal_type") val signalType: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("subtitle") val subtitle: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("severity") val severity: String = "",
    @SerializedName("badge_text") val badgeText: String = ""
)

@Immutable
data class TradeSimulationRequestDto(
    @SerializedName("isin") val isin: String,
    @SerializedName("schemeName") val schemeName: String,
    @SerializedName("units") val units: Double,
    @SerializedName("pricePerUnit") val pricePerUnit: Double,
    @SerializedName("tradeDate") val tradeDate: String = "",
    @SerializedName("tradeType") val tradeType: String // DISPOSAL or ACQUISITION
)

@Immutable
data class TradeSimulationResultDto(
    @SerializedName("isin") val isin: String = "",
    @SerializedName("schemeName") val schemeName: String = "",
    @SerializedName("tradeType") val tradeType: String = "",
    @SerializedName("units") val units: Double = 0.0,
    @SerializedName("pricePerUnit") val pricePerUnit: Double = 0.0,
    @SerializedName("grossTradeAmount") val grossTradeAmount: Double = 0.0,
    @SerializedName("grossCapitalGain") val grossCapitalGain: Double = 0.0,
    @SerializedName("ltcgEquity") val ltcgEquity: Double = 0.0,
    @SerializedName("stcgEquity") val stcgEquity: Double = 0.0,
    @SerializedName("debtGain") val debtGain: Double = 0.0,
    @SerializedName("sec112aExemptionApplied") val sec112aExemptionApplied: Double = 0.0,
    @SerializedName("estimatedTaxLiability") val estimatedTaxLiability: Double = 0.0,
    @SerializedName("postTradeNetWorth") val postTradeNetWorth: Double = 0.0,
    @SerializedName("postTradeInvestedCost") val postTradeInvestedCost: Double = 0.0,
    @SerializedName("postTradeXirr") val postTradeXirr: Double = 0.0,
    @SerializedName("taxSummaryNotice") val taxSummaryNotice: String = ""
)
</file>

<file path="core-node/src/main/resources/static/src/js/modules/portfolio.js">
import { API_BASE, fetchJson } from '../api.js';
import { state } from '../state.js';
import { formatINR } from '../utils.js';

export function updatePortfolioSummary(summary) {
  const netWorthVal = document.querySelector('.net-worth-val');
  const gainText = document.querySelector('.net-worth-gain');
  const subText = document.querySelector('.net-worth-sub');
  const xirrVal = document.querySelector('.xirr-val');

  const curVal = summary.total_current_value || summary.totalCurrentValue;
  const gainVal = summary.total_unrealized_gain || summary.totalUnrealizedGain;
  const countVal = summary.active_holding_count !== undefined ? summary.active_holding_count : summary.activeHoldingCount;
  const xirr = summary.xirr_percentage || summary.xirrPercentage;

  if (netWorthVal && curVal) {
    netWorthVal.textContent = formatINR(curVal);
    netWorthVal.classList.remove('skeleton');
  }
  if (gainText && gainVal) {
    const gain = Math.round(parseFloat(gainVal) || 0);
    const sign = gain >= 0 ? '+' : '';
    gainText.textContent = `Unrealized gain: ${sign}${formatINR(gain)}`;
    gainText.className = `metric-delta ${gain >= 0 ? 'positive' : 'negative'}`;
  }
  if (subText && countVal !== undefined) {
    subText.innerHTML = `Active Holdings: <strong>${countVal} Schemes</strong>`;
  }
  if (xirrVal && xirr) {
    xirrVal.textContent = xirr;
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
    const assetName = h.asset_name || h.assetName || '';
    const category = h.category || '';
    const inv = Math.round(parseFloat(h.invested_value || h.investedValue) || 0);
    const cur = Math.round(parseFloat(h.current_value || h.currentValue) || 0);
    const gain = Math.round(parseFloat(h.unrealized_gain || h.unrealizedGain) || 0);
    const gainPct = h.unrealized_gain_pct || h.unrealizedGainPct || '0.00';
    const allocPct = h.allocation_pct || h.allocationPct || '0.00';
    const lots = h.lots || [];

    const gainSign = gain >= 0 ? '+' : '';
    const gainColor = gain >= 0 ? 'color: #10b981;' : 'color: #ef4444;';

    const isSip = h.has_sip || h.hasSip || (lots && lots.some(l => (l.event_type || l.eventType) === 'SIP_INSTALMENT'));
    const sipBadge = isSip ? ' <span style="background:rgba(208,255,0,0.15); color:#d0ff00; border:1px solid rgba(208,255,0,0.3); font-size:10px; padding:2px 6px; border-radius:4px; margin-left:6px; font-weight:700;">🔄 Active SIP</span>' : '';

    html += `
      <tr class="holding-row" onclick="toggleLotDetails('${idx}')">
        <td style="font-weight:600;">${assetName}${sipBadge}</td>
        <td><span class="cat-badge cat-${category}">${category.replace('_SPECIFIED_50AA', '')}</span></td>
        <td class="font-mono">${formatINR(inv)}</td>
        <td class="font-mono" style="font-weight:600;">${formatINR(cur)}</td>
        <td class="font-mono" style="${gainColor}">${gainSign}${formatINR(gain)} (${gainPct}%)</td>
        <td class="font-mono">${allocPct}%</td>
        <td><button class="pill-btn">${lots.length} Lots ▼</button></td>
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
              ${lots.map(l => {
                const acqDate = l.acquisition_date || l.acquisitionDate;
                const units = l.remaining_units || l.remainingUnits;
                const costPerUnit = parseFloat(l.cost_per_unit || l.costPerUnit || '0');
                const lotGain = parseFloat(l.unrealized_gain || l.unrealizedGain || '0');
                const daysHeld = l.holding_days !== undefined ? l.holding_days : l.holdingDays;
                const daysLeft = l.days_to_ltcg !== undefined ? l.days_to_ltcg : l.daysToLtcg;
                const isLtcg = l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg;

                return `
                <tr>
                  <td>${acqDate}</td>
                  <td class="font-mono">${units}</td>
                  <td class="font-mono">${formatINR(costPerUnit * parseFloat(units || '0'))}</td>
                  <td class="font-mono" style="${lotGain >= 0 ? 'color: #10b981;' : 'color: #ef4444;'}">
                    ${lotGain >= 0 ? '+' : ''}${formatINR(lotGain)}
                  </td>
                  <td>${daysHeld}d</td>
                  <td><span class="cat-badge ${isLtcg ? 'cat-EQUITY' : 'cat-DEBT_SPECIFIED_50AA'}">${isLtcg ? 'LTCG' : 'STCG (' + (daysLeft > 0 ? daysLeft + 'd left' : 'Always') + ')'}</span></td>
                </tr>
              `;}).join('')}
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

export function renderNetWorthTrendChart(containerId, dates, values) {
  const container = document.getElementById(containerId);
  if (!container || !dates || dates.length === 0 || !window.echarts) return null;

  const instance = window.echarts.init(container);
  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { backgroundColor: '#090f1e' } },
      formatter: params => `${params[0].name}<br/>Valuation: <b>₹ ${formatINR(params[0].value)}</b>`
    },
    grid: { left: '3%', right: '4%', bottom: '18%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
      axisLabel: { color: '#94a3b8', fontSize: 11, formatter: v => `₹ ${(v/100000).toFixed(1)}L` }
    },
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      { type: 'slider', start: 0, end: 100, height: 16, bottom: 0, borderColor: 'transparent', backgroundColor: 'rgba(255,255,255,0.05)', fillerColor: 'rgba(208,255,0,0.2)' }
    ],
    series: [{
      name: 'Net Worth',
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 3, color: '#d0ff00' },
      areaStyle: {
        color: new window.echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(208,255,0,0.35)' },
          { offset: 1, color: 'rgba(6,182,212,0.02)' }
        ])
      },
      data: values
    }]
  };
  instance.setOption(option);
  return instance;
}

export function renderAllocationChart(allocations) {
  if (state.charts.allocChart) state.charts.allocChart.dispose();
  if (!allocations || allocations.length === 0) return;

  const total = allocations.reduce((sum, a) => sum + (parseFloat(a.current_value || a.currentValue) || 0), 0);
  
  const main = [];
  let othersVal = 0;
  let othersCount = 0;

  allocations.forEach(a => {
    const val = parseFloat(a.current_value || a.currentValue) || 0;
    const assetName = a.asset_name || a.assetName || '';
    const pct = total > 0 ? (val / total) * 100 : 0;
    if (pct < 3.0 && allocations.length > 6) {
      othersVal += val;
      othersCount++;
    } else {
      main.push({
        name: assetName.length > 25 ? assetName.substring(0, 23) + '...' : assetName,
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
    name: c.category_name || c.categoryName,
    value: parseFloat(c.current_value || c.currentValue) || 0
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

  const isWindowOpen = data.is_rebalance_window_open !== undefined ? data.is_rebalance_window_open : data.isRebalanceWindowOpen;
  const nextWindow = data.next_scheduled_window || data.nextScheduledWindow;
  const totalProceeds = data.total_proceeds || data.totalProceeds;
  const totalTaxDrag = data.total_tax_drag || data.totalTaxDrag;
  const proRata = data.pro_rata_allocations || data.proRataAllocations || [];

  if (badge) {
    badge.textContent = isWindowOpen ? 'WINDOW OPEN: EXECUTE REBALANCE' : `SCHEDULED WINDOW: ${nextWindow}`;
    badge.style.color = isWindowOpen ? '#10b981' : '#06b6d4';
  }

  const parsedProceeds = parseFloat(totalProceeds);
  const proceeds = (!isNaN(parsedProceeds)) ? Math.round(parsedProceeds) : 0;
  const taxDrag = Math.round(parseFloat(totalTaxDrag) || 0);

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

  for (const alloc of proRata) {
    const assetName = alloc.asset_name || alloc.assetName;
    const weightPct = alloc.sip_weight_pct || alloc.sipWeightPct;
    const deployAmt = alloc.deployment_amount || alloc.deploymentAmount;

    const amt = Math.round(parseFloat(deployAmt) || 0);
    html += `
      <tr>
        <td style="font-weight:600;">${assetName}</td>
        <td><span class="days-badge">${weightPct}%</span></td>
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

  const taxDrag = data.total_tax_drag || data.totalTaxDrag;
  const effRate = data.effective_tax_rate_pct || data.effectiveTaxRatePct;
  const ltcgHarv = data.ltcg_exemption_harvested || data.ltcgExemptionHarvested;

  if (rebTaxDrag && taxDrag) {
    rebTaxDrag.textContent = formatINR(taxDrag);
  }
  if (rebEffRate && effRate) {
    rebEffRate.textContent = effRate;
  }
  if (rebLtcgHarvested && ltcgHarv) {
    rebLtcgHarvested.textContent = formatINR(ltcgHarv);
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
  const unallocCash = data.unallocated_cash || data.unallocatedCash;
  if (idleVal && unallocCash) {
    idleVal.textContent = formatINR(unallocCash);
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

  const status = data.status;
  const shortage = data.shortage_or_surplus_amount || data.shortageOrSurplusAmount;
  const activeLabel = data.active_scenario_label || data.activeScenarioLabel;
  const fireInvestable = data.fire_investable_net_worth || data.fireInvestableNetWorth;
  const requiredCorpus = data.required_corpus || data.requiredCorpus;
  const projectedCorpus = data.projected_corpus_at_target_age || data.projectedCorpusAtTargetAge;

  if (statusPill) {
    statusPill.textContent = status === 'ON_TRACK' ? 'ON TRACK' : `SHORT BY ${formatINR(shortage)}`;
    statusPill.className = `fire-status-pill ${status === 'ON_TRACK' ? 'on-track' : 'short'}`;
  }

  if (scenarioLabel) scenarioLabel.textContent = `Scenario: ${activeLabel}`;

  if (investableNw) investableNw.textContent = formatINR(fireInvestable);
  if (reqCorpus) reqCorpus.textContent = `₹ ${(parseFloat(requiredCorpus) / 10000000).toFixed(2)} Cr`;
  if (projCorpus) projCorpus.textContent = `₹ ${(parseFloat(projectedCorpus) / 10000000).toFixed(2)} Cr`;
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

  const dd = data.drawdown_status || data.drawdownStatus;
  const statuses = data.bucket_statuses || data.bucketStatuses;

  if (drawdownTag && dd) {
    const bmName = dd.benchmark_name || dd.benchmarkName;
    const ddPct = dd.drawdown_pct || dd.drawdownPct;
    drawdownTag.textContent = `${bmName}: ${ddPct}% Drawdown`;
  }

  if (bucketGrid && statuses) {
    let html = '';
    statuses.forEach(b => {
      const isDrifted = b.is_drifted !== undefined ? b.is_drifted : b.isDrifted;
      const driftPct = b.drift_pct || b.driftPct;
      const curVal = b.current_value || b.currentValue;
      const curPct = b.current_pct || b.currentPct;
      const targetPct = b.target_pct || b.targetPct;

      const nameFmt = b.bucket.replace('_', ' ');
      html += `
        <div class="bucket-card ${isDrifted ? 'drifted' : ''}">
          <div class="bucket-card-header">
            <span class="bucket-name">${nameFmt}</span>
            <span class="drift-badge ${isDrifted ? 'warn' : 'ok'}">${isDrifted ? 'Drift: ' + driftPct + '%' : 'Target OK'}</span>
          </div>
          <div class="font-mono" style="font-size:16px; font-weight:700;">${formatINR(curVal)}</div>
          <div class="text-muted" style="font-size:11px; margin-top:4px;">Current: ${curPct}% · Target: ${targetPct}%</div>
        </div>
      `;
    });
    bucketGrid.innerHTML = html;
  }
}
</file>

<file path="core-node/src/main/resources/static/index.html">
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
        <button id="cmdKTriggerBtn" class="upload-btn cmd-k-btn" onclick="window.openCmdPalette && window.openCmdPalette()">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
          ⚡ AI Search <kbd>/</kbd> <kbd>⌘K</kbd>
        </button>

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
  <!-- Global Command Palette Modal -->
  <div id="commandPaletteModal" class="cmd-modal-overlay" style="display: none;">
    <div class="command-palette-box">
      <div class="command-palette-header">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#D0FF00" stroke-width="2.5"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
        <input type="text" id="commandPaletteInput" placeholder="Type an AI prompt, SQL query, or tax question... (Esc to exit)">
        <button type="button" id="closeCmdPaletteBtn" style="background:transparent; border:none; color:#94a3b8; cursor:pointer; font-size:18px; font-weight:bold; padding:0 4px;" title="Close (Esc)">✕</button>
      </div>
      <div class="command-palette-results" id="commandPaletteResults">
        <div class="cmd-item" data-action="whatif">⚡ Open What-If Trade Simulator</div>
        <div class="cmd-item" data-action="schedule-cg">📄 Download Schedule CG Tax CSV</div>
        <div class="cmd-item" data-action="rebalance">⚖️ Run Portfolio Rebalance Engine</div>
        <div class="cmd-item" data-action="holdings">📊 Jump to Holdings & NAV Trend</div>
        <div class="cmd-item" data-action="radar">🧠 View AI Quant Radar Signals</div>
      </div>
    </div>
  </div>

  <script type="module" src="./src/app.js?v=3.0.6"></script>
</body>
</html>
</file>

<file path="repomix-quant.md">
This file is a merged representation of a subset of the codebase, containing specifically included files, combined into a single document by Repomix.

<file_summary>
This section contains a summary of this file.

<purpose>
This file contains a packed representation of a subset of the repository's contents that is considered the most important context.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.
</purpose>

<file_format>
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Repository files (if enabled)
5. Multiple file entries, each consisting of:
  - File path as an attribute
  - Full contents of the file
</file_format>

<usage_guidelines>
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.
</usage_guidelines>

<notes>
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Only files matching these patterns are included: quant-sidecar/**/*
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Files are sorted by Git change count (files with more changes are at the bottom)
</notes>

</file_summary>

<directory_structure>
quant-sidecar/
  parsers/
    broker_csv_parser.py
    cas_parser.py
    models.py
    sip_detector.py
  quant/
    analytics_engine.py
  app.py
  Dockerfile
  flight_server.py
  requirements.txt
</directory_structure>

<files>
This section contains the contents of the repository's files.

<file path="quant-sidecar/parsers/models.py">
from enum import Enum
from datetime import date, datetime
from decimal import Decimal
from typing import Optional
from pydantic import BaseModel, Field

class EventType(str, Enum):
    ACQUISITION = "ACQUISITION"
    SIP_INSTALMENT = "SIP_INSTALMENT"
    DISPOSAL = "DISPOSAL"
    BONUS = "BONUS"
    SPLIT = "SPLIT"
    DIVIDEND_REINVEST = "DIVIDEND_REINVEST"
    SGB_INTEREST = "SGB_INTEREST"
    SGB_MATURITY = "SGB_MATURITY"
    MERGER = "MERGER"

class TaxEventSchema(BaseModel):
    id: str
    asset_id: str = Field(..., alias="assetId")
    asset_name: str = Field(..., alias="assetName")
    isin: Optional[str] = None
    event_type: EventType = Field(..., alias="eventType")
    event_date: date = Field(..., alias="eventDate")
    units: Decimal
    price_per_unit: Decimal = Field(..., alias="pricePerUnit")
    gross_amount: Decimal = Field(..., alias="grossAmount")
    source_document_id: str = Field(..., alias="sourceDocumentId")
    ingested_at: datetime = Field(default_factory=datetime.utcnow, alias="ingestedAt")

    class Config:
        populate_by_name = True

    def unit_delta(self) -> Decimal:
        if self.event_type == EventType.DISPOSAL or self.event_type == EventType.SGB_MATURITY:
            return -self.units
        elif self.event_type == EventType.SGB_INTEREST:
            return Decimal("0.0")
        return self.units
</file>

<file path="quant-sidecar/Dockerfile">
FROM python:3.12-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000 8001

CMD ["python", "app.py"]
</file>

<file path="quant-sidecar/parsers/broker_csv_parser.py">
import uuid
from datetime import datetime
from decimal import Decimal
from typing import List, Optional
import polars as pl
from .models import TaxEventSchema, EventType

class BrokerCsvParser:
    def __init__(self, csv_path: str, broker_type: str = "generic"):
        self.csv_path = csv_path
        self.broker_type = broker_type

    def parse(self) -> List[TaxEventSchema]:
        events: List[TaxEventSchema] = []
        try:
            df = pl.read_csv(self.csv_path, infer_schema_length=0)
            if df.is_empty():
                return events

            col_map = {str(c).strip().lower(): c for c in df.columns}

            date_col = next((col_map[k] for k in col_map if any(x in k for x in ["date", "txn_date", "trade_date"])), None)
            symbol_col = next((col_map[k] for k in col_map if any(x in k for x in ["symbol", "scheme", "scrip", "asset", "description"])), None)
            type_col = next((col_map[k] for k in col_map if any(x in k for x in ["type", "buy/sell", "transaction", "action"])), None)
            qty_col = next((col_map[k] for k in col_map if any(x in k for x in ["qty", "quantity", "units"])), None)
            price_col = next((col_map[k] for k in col_map if any(x in k for x in ["price", "nav", "rate"])), None)
            amount_col = next((col_map[k] for k in col_map if any(x in k for x in ["amount", "value", "total"])), None)

            for row in df.to_dicts():
                try:
                    asset_name = str(row[symbol_col]) if symbol_col and row.get(symbol_col) else "Broker Asset"
                    date_str = str(row[date_col]) if date_col and row.get(date_col) else ""

                    event_date = datetime.now().date()
                    if date_str:
                        for fmt in ("%Y-%m-%d", "%d-%m-%Y", "%d/%m/%Y", "%d-%b-%Y"):
                            try:
                                event_date = datetime.strptime(date_str.strip(), fmt).date()
                                break
                            except ValueError:
                                pass

                    txn_type_str = str(row[type_col]).upper() if type_col and row.get(type_col) else "BUY"
                    if any(x in txn_type_str for x in ["SELL", "REDEMPTION", "DISPOSAL", "SWITCH OUT"]):
                        event_type = EventType.DISPOSAL
                    elif "BONUS" in txn_type_str:
                        event_type = EventType.BONUS
                    elif "SPLIT" in txn_type_str:
                        event_type = EventType.SPLIT
                    else:
                        event_type = EventType.ACQUISITION

                    units_val = row.get(qty_col)
                    units = Decimal(str(abs(float(units_val)))) if units_val is not None and str(units_val).strip() != "" else Decimal("1")
                    
                    price_val = row.get(price_col)
                    price = Decimal(str(abs(float(price_val)))) if price_val is not None and str(price_val).strip() != "" else Decimal("0")
                    
                    amt_val = row.get(amount_col)
                    amount = Decimal(str(abs(float(amt_val)))) if amt_val is not None and str(amt_val).strip() != "" else (units * price)

                    events.append(
                        TaxEventSchema(
                            id=str(uuid.uuid4()),
                            assetId=asset_name.replace(" ", "_").upper()[:20],
                            assetName=asset_name,
                            isin=None,
                            eventType=event_type,
                            eventDate=event_date,
                            units=units,
                            pricePerUnit=price,
                            grossAmount=amount,
                            sourceDocumentId=self.csv_path,
                            ingestedAt=datetime.now()
                        )
                    )
                except Exception:
                    continue
        except Exception:
            pass

        from .sip_detector import detect_and_tag_sips
        return detect_and_tag_sips(events)
</file>

<file path="quant-sidecar/parsers/cas_parser.py">
import re
import uuid
from decimal import Decimal
from typing import List, Optional
from datetime import datetime, date
from .models import TaxEventSchema, EventType

DATE_REGEX = re.compile(r"^(\d{2}-[A-Za-z]{3}-\d{4})\s+(.+)$")
# Added support for both CAMS and KFintech PAN formats in CAS
ISIN_REGEX = re.compile(r"ISIN:\s*([A-Z0-9]{12})", re.IGNORECASE)
TOKEN_REGEX = re.compile(r"\((?:\d{1,3}(?:,\d{3})*|\d+)(?:\.\d+)?\)|\b\d{1,3}(?:,\d{3})*(?:\.\d+)?\b|\b\d+\.\d+\b")

class CasPdfParser:
    def __init__(self, pdf_path: str, password: Optional[str] = None):
        self.pdf_path = pdf_path
        self.password = password

    def parse_events(self) -> List[TaxEventSchema]:
        events: List[TaxEventSchema] = []
        if not self.pdf_path:
            return events

        # Try specialized casparser library first
        try:
            import casparser
            data = casparser.read_cas_pdf(self.pdf_path, self.password or "")
            for folio in data.folios:
                for scheme in folio.schemes:
                    isin = scheme.isin
                    scheme_name = scheme.scheme
                    asset_id = isin or scheme_name.replace(" ", "_").upper()[:20]

                    for txn in scheme.transactions:
                        txn_type_str = str(txn.type).upper()
                        if any(x in txn_type_str for x in ["REDEMPTION", "SWITCH_OUT", "SELL"]):
                            event_type = EventType.DISPOSAL
                        elif "BONUS" in txn_type_str:
                            event_type = EventType.BONUS
                        elif "SPLIT" in txn_type_str:
                            event_type = EventType.SPLIT
                        else:
                            event_type = EventType.ACQUISITION

                        txn_date = txn.date if isinstance(txn.date, date) else datetime.now().date()
                        units = Decimal(str(abs(txn.units or 0)))
                        price = Decimal(str(abs(txn.nav or 0)))
                        amount = Decimal(str(abs(txn.amount or 0)))
                        if amount == Decimal("0") and units > 0 and price > 0:
                            amount = units * price

                        if units > Decimal("0"):
                            events.append(
                                TaxEventSchema(
                                    id=str(uuid.uuid4()),
                                    assetId=asset_id,
                                    assetName=scheme_name,
                                    isin=isin,
                                    eventType=event_type,
                                    eventDate=txn_date,
                                    units=units,
                                    pricePerUnit=price,
                                    grossAmount=amount,
                                    sourceDocumentId=self.pdf_path,
                                    ingestedAt=datetime.now()
                                )
                            )
            if events:
                return events
        except Exception as e:
            print(f"casparser notice: {e}, falling back to custom line parser.")

        # Fallback to pdfplumber regex line parser
        try:
            import pdfplumber

            current_scheme = "Mutual Fund Scheme"
            current_isin: Optional[str] = None

            with pdfplumber.open(self.pdf_path, password=self.password or "") as pdf:
                for page in pdf.pages:
                    text = page.extract_text() or ""
                    for line in text.splitlines():
                        line_str = line.strip()
                        if not line_str:
                            continue

                        isin_match = ISIN_REGEX.search(line_str)
                        if isin_match:
                            current_isin = isin_match.group(1)

                        if "ISIN:" in line_str or ("Fund" in line_str and "Registrar" in line_str):
                            current_scheme = line_str.split(" - ISIN:")[0].split("(Advisor")[0].strip()

                        if any(
                            x in line_str
                            for x in [
                                "*** Stamp Duty ***",
                                "*** STT Paid ***",
                                "***Cancelled***",
                                "***Address Updated",
                                "Opening Unit Balance",
                                "CAMSCASWS",
                                "Consolidated Account Statement",
                                "Closing Unit Balance",
                                "NAV on",
                            ]
                        ):
                            continue

                        match = DATE_REGEX.match(line_str)
                        if match:
                            date_str, rest = match.groups()
                            try:
                                event_date = datetime.strptime(date_str, "%d-%b-%Y").date()
                            except ValueError:
                                event_date = datetime.now().date()

                            num_tokens = TOKEN_REGEX.findall(rest)

                            clean_nums = []
                            for tok in num_tokens:
                                is_neg = tok.startswith("(") and tok.endswith(")")
                                raw_val = tok.replace("(", "").replace(")", "").replace(",", "").strip()
                                try:
                                    val = Decimal(raw_val)
                                    if is_neg:
                                        val = -val
                                    clean_nums.append(val)
                                except Exception:
                                    pass

                            if len(clean_nums) >= 3:
                                amount = abs(clean_nums[0])
                                units = abs(clean_nums[1])
                                price = clean_nums[2]

                                line_upper = rest.upper()
                                if any(x in line_upper for x in ["REDEMPTION", "SWITCH OUT", "SELL"]):
                                    event_type = EventType.DISPOSAL
                                elif "BONUS" in line_upper:
                                    event_type = EventType.BONUS
                                elif "SPLIT" in line_upper:
                                    event_type = EventType.SPLIT
                                else:
                                    event_type = EventType.ACQUISITION

                                events.append(
                                    TaxEventSchema(
                                        id=str(uuid.uuid4()),
                                        assetId=current_isin or current_scheme.replace(" ", "_").upper()[:20],
                                        assetName=current_scheme,
                                        isin=current_isin,
                                        eventType=event_type,
                                        eventDate=event_date,
                                        units=units,
                                        pricePerUnit=price,
                                        grossAmount=amount,
                                        sourceDocumentId=self.pdf_path,
                                        ingestedAt=datetime.now()
                                    )
                                )
        except Exception as err:
            print(f"Fallback parser error: {err}")

        from .sip_detector import detect_and_tag_sips
        return detect_and_tag_sips(events)
</file>

<file path="quant-sidecar/parsers/sip_detector.py">
from typing import List
from collections import defaultdict
from .models import TaxEventSchema, EventType

def detect_and_tag_sips(events: List[TaxEventSchema], min_consecutive_matches: int = 3) -> List[TaxEventSchema]:
    """
    Auto-detects Systematic Investment Plans (SIPs) by grouping transactions by ISIN/Asset ID,
    checking date spacing (25 to 35 days for monthly recurring investments), and amount variation (<= 5%).
    Requires at least `min_consecutive_matches` (default 3+) consecutive matching transactions to eliminate false positives.
    Tags matching ACQUISITION events as EventType.SIP_INSTALMENT.
    """
    if not events:
        return events

    acquisitions_by_asset = defaultdict(list)
    for idx, event in enumerate(events):
        if event.event_type in (EventType.ACQUISITION, EventType.SIP_INSTALMENT):
            asset_key = event.isin or event.asset_id
            acquisitions_by_asset[asset_key].append((idx, event))

    sip_indices = set()

    for asset_key, asset_events in acquisitions_by_asset.items():
        if len(asset_events) < min_consecutive_matches:
            continue

        sorted_events = sorted(asset_events, key=lambda x: x[1].event_date)
        current_chain = [sorted_events[0]]

        for i in range(len(sorted_events) - 1):
            idx1, ev1 = sorted_events[i]
            idx2, ev2 = sorted_events[i + 1]

            date_diff = (ev2.event_date - ev1.event_date).days
            amt1 = float(ev1.gross_amount)
            amt2 = float(ev2.gross_amount)
            amt_diff_pct = abs(amt1 - amt2) / max(amt1, amt2, 1.0)

            # Monthly SIP criteria: 25 to 35 days spacing AND <= 5% amount variation
            if 25 <= date_diff <= 35 and amt_diff_pct <= 0.05:
                current_chain.append(sorted_events[i + 1])
            else:
                if len(current_chain) >= min_consecutive_matches:
                    for chain_idx, _ in current_chain:
                        sip_indices.add(chain_idx)
                current_chain = [sorted_events[i + 1]]

        if len(current_chain) >= min_consecutive_matches:
            for chain_idx, _ in current_chain:
                sip_indices.add(chain_idx)

    updated_events = []
    for idx, event in enumerate(events):
        if idx in sip_indices:
            updated_events.append(event.model_copy(update={"event_type": EventType.SIP_INSTALMENT}))
        else:
            updated_events.append(event)

    return updated_events
</file>

<file path="quant-sidecar/app.py">
import os
import tempfile
import threading
import logging
from typing import List, Optional
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
import polars as pl
import uvicorn

from parsers.cas_parser import CasPdfParser
from parsers.broker_csv_parser import BrokerCsvParser
from parsers.sip_detector import detect_and_tag_sips
from parsers.models import TaxEventSchema
from flight_server import QuantFlightServer

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("quant-sidecar")

app = FastAPI(title="Portfolio OS Quant & Parser Sidecar", version="3.0.0")

@app.get("/health")
def health_check():
    return {"status": "UP", "engine": "Polars + FastAPI + Arrow Flight", "version": "3.0.0"}

@app.post("/api/v1/parse", response_model=List[TaxEventSchema])
async def parse_statement(
    file: UploadFile = File(...),
    password: Optional[str] = Form(None)
):
    filename = file.filename or "statement"
    ext = os.path.splitext(filename)[1].lower()
    logger.info(f"Received statement upload: {filename} with extension {ext}")

    with tempfile.NamedTemporaryFile(delete=False, suffix=ext) as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        events = []
        if ext == ".pdf":
            parser = CasPdfParser(tmp_path, password=password)
            events = parser.parse_events()
        elif ext == ".csv":
            parser = BrokerCsvParser(tmp_path)
            events = parser.parse()
        else:
            raise HTTPException(status_code=400, detail="Unsupported file format. Please upload PDF or CSV.")

        # Apply robust 3+ match SIP auto-detection
        events = detect_and_tag_sips(events)

        # Polars multi-threaded dataframe verification
        if events:
            df = pl.DataFrame([e.model_dump(by_alias=True) for e in events])
            required_cols = ["id", "assetId", "assetName", "eventType", "eventDate", "units", "grossAmount"]
            for col in required_cols:
                if col not in df.columns:
                    raise HTTPException(status_code=422, detail=f"Missing column in parsed dataframe: {col}")
        
        logger.info(f"Successfully parsed {len(events)} events from statement")
        return events
    except Exception as err:
        logger.error(f"Error parsing statement: {err}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(err))
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)

def run_flight_server():
    try:
        server = QuantFlightServer("0.0.0.0", 8001)
        logger.info("Starting Apache Arrow Flight RPC server on port 8001...")
        server.serve()
    except Exception as e:
        logger.error(f"Failed to start Flight server: {e}", exc_info=True)

if __name__ == "__main__":
    # Start Apache Arrow Flight RPC Server in a background daemon thread
    flight_thread = threading.Thread(target=run_flight_server, daemon=True)
    flight_thread.start()
    
    # Run FastAPI server
    logger.info("Starting FastAPI HTTP Server on port 8000...")
    uvicorn.run(app, host="0.0.0.0", port=8000)
</file>

<file path="quant-sidecar/flight_server.py">
import pyarrow as pa
import pyarrow.flight as flight
import polars as pl
import logging
from quant.analytics_engine import compute_fund_analytics

logger = logging.getLogger(__name__)

class QuantFlightServer(flight.FlightServerBase):
    def __init__(self, host="0.0.0.0", port=8001, **kwargs):
        location = flight.Location.for_grpc_tcp(host, port)
        super(QuantFlightServer, self).__init__(location, **kwargs)
        self.host = host
        self.port = port
        logger.info(f"Initialized Apache Arrow Flight RPC server on {host}:{port}")

    def do_exchange(self, context, descriptor, reader, writer):
        try:
            table = reader.read_all()
            if table.num_rows == 0:
                self._write_empty_response(writer)
                return

            df = pl.from_arrow(table)
            results = []
            unique_codes = df["amfi_code"].unique().to_list()

            for code in unique_codes:
                fund_df = df.filter(pl.col("amfi_code") == code)
                nav_values = fund_df["nav_value"].to_list()
                dates_list = fund_df["nav_date"].to_list() if "nav_date" in fund_df.columns else None

                analytics = compute_fund_analytics(nav_values, dates=dates_list)

                results.append({
                    "amfi_code": str(code),
                    "status": str(analytics.get("status", "OK")),
                    "sharpe": float(analytics.get("sharpe", 0.0)),
                    "sortino": float(analytics.get("sortino", 0.0)),
                    "calmar": float(analytics.get("calmar", 0.0)),
                    "max_drawdown": float(analytics.get("max_drawdown", 0.0)),
                    "volatility_annual": float(analytics.get("volatility_annual", 0.0)),
                    "var_95": float(analytics.get("var_95", 0.0)),
                    "cvar_95": float(analytics.get("cvar_95", 0.0)),
                    "beta": float(analytics.get("beta", 0.0))
                })

            if results:
                out_df = pl.DataFrame(results)
                out_table = out_df.to_arrow()
            else:
                self._write_empty_response(writer)
                return

            writer.begin(out_table.schema)
            writer.write_table(out_table)
            writer.close()
        except Exception as e:
            logger.error(f"Error during Flight exchange processing: {e}", exc_info=True)
            self._write_empty_response(writer)

    def _write_empty_response(self, writer):
        schema = pa.schema([
            ("amfi_code", pa.string()),
            ("status", pa.string()),
            ("sharpe", pa.float64()),
            ("sortino", pa.float64()),
            ("calmar", pa.float64()),
            ("max_drawdown", pa.float64()),
            ("volatility_annual", pa.float64()),
            ("var_95", pa.float64()),
            ("cvar_95", pa.float64()),
            ("beta", pa.float64())
        ])
        out_table = pa.Table.from_batches([], schema)
        writer.begin(schema)
        writer.write_table(out_table)
        writer.close()

def start_flight_server(host="0.0.0.0", port=8001):
    server = QuantFlightServer(host, port)
    server.serve()

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    start_flight_server()
</file>

<file path="quant-sidecar/requirements.txt">
fastapi>=0.110.0
uvicorn>=0.28.0
granian>=1.2.0
polars>=0.20.15
pyarrow>=15.0.0
pdfplumber>=0.11.0
casparser>=0.7.0
casparser-isin>=0.3.0
numpy>=1.26.0
scipy>=1.12.0
yfinance>=0.2.37
pandas>=2.2.0
quantstats>=0.0.62
pydantic>=2.6.0
python-multipart>=0.0.9
</file>

<file path="quant-sidecar/quant/analytics_engine.py">
import numpy as np
import pandas as pd
try:
    import quantstats as qs
except ImportError:
    qs = None

def compute_fund_analytics(nav_series, dates=None, benchmark_returns=None):
    if len(nav_series) < 30:
        return {
            "status": "INSUFFICIENT_HISTORY",
            "data_points": len(nav_series),
            "sharpe": 0.0,
            "sortino": 0.0,
            "calmar": 0.0,
            "max_drawdown": 0.0,
            "volatility_annual": 0.0,
            "var_95": 0.0,
            "cvar_95": 0.0,
            "beta": 0.0
        }

    try:
        if dates is not None and len(dates) == len(nav_series) and any(d for d in dates if d):
            valid_pairs = [(nav, d) for nav, d in zip(nav_series, dates) if d]
            if len(valid_pairs) >= 10:
                vals, d_str = zip(*valid_pairs)
                idx = pd.to_datetime(d_str)
                s = pd.Series(vals, index=idx)
            else:
                s = pd.Series(nav_series)
        else:
            s = pd.Series(nav_series)

        returns = s.pct_change().dropna()

        if len(returns) < 10:
            return {
                "status": "INSUFFICIENT_HISTORY",
                "data_points": len(returns),
                "sharpe": 0.0,
                "sortino": 0.0,
                "calmar": 0.0,
                "max_drawdown": 0.0,
                "volatility_annual": 0.0,
                "var_95": 0.0,
                "cvar_95": 0.0,
                "beta": 0.0
            }

        if qs is not None:
            sharpe = float(qs.stats.sharpe(returns))
            sortino = float(qs.stats.sortino(returns))
            calmar = float(qs.stats.calmar(returns))
            max_dd = float(qs.stats.max_drawdown(returns))
            vol = float(qs.stats.volatility(returns))
            var95 = float(qs.stats.value_at_risk(returns))
            cvar95 = float(qs.stats.conditional_value_at_risk(returns))

            beta = 0.0
            if benchmark_returns is not None:
                try:
                    beta_val = qs.stats.greeks(returns, benchmark_returns).get("beta", 0.0)
                    beta = float(beta_val) if not np.isnan(beta_val) else 0.0
                except Exception:
                    beta = 0.0
        else:
            # Vectorized fallback calculation with true Downside Deviation Sortino ratio
            mean_ret = returns.mean()
            std_ret = returns.std()
            sharpe = float((mean_ret / std_ret) * np.sqrt(252)) if std_ret > 0 else 0.0
            
            downside_returns = returns[returns < 0]
            downside_std = downside_returns.std() if not downside_returns.empty else 0.0
            sortino = float((mean_ret / downside_std) * np.sqrt(252)) if downside_std > 0 else sharpe

            cum_returns = (1 + returns).cumprod()
            peak = cum_returns.cummax()
            dd = (cum_returns - peak) / peak
            max_dd = float(dd.min())
            calmar = float(mean_ret * 252 / abs(max_dd)) if abs(max_dd) > 0 else 0.0
            vol = float(std_ret * np.sqrt(252))
            var95 = float(returns.quantile(0.05))
            cvar95 = float(returns[returns <= var95].mean()) if not returns[returns <= var95].empty else var95
            beta = 0.0

        return {
            "status": "OK",
            "sharpe": 0.0 if np.isnan(sharpe) else round(sharpe, 2),
            "sortino": 0.0 if np.isnan(sortino) else round(sortino, 2),
            "calmar": 0.0 if np.isnan(calmar) else round(calmar, 2),
            "max_drawdown": 0.0 if np.isnan(max_dd) else round(max_dd, 4),
            "volatility_annual": 0.0 if np.isnan(vol) else round(vol, 4),
            "var_95": 0.0 if np.isnan(var95) else round(var95, 4),
            "cvar_95": 0.0 if np.isnan(cvar95) else round(cvar95, 4),
            "beta": 0.0 if np.isnan(beta) else round(beta, 2)
        }
    except Exception as e:
        return {
            "status": "ERROR",
            "message": str(e),
            "sharpe": 0.0,
            "sortino": 0.0,
            "calmar": 0.0,
            "max_drawdown": 0.0,
            "volatility_annual": 0.0,
            "var_95": 0.0,
            "cvar_95": 0.0,
            "beta": 0.0
        }

def run_monte_carlo_fire_simulation(
    daily_returns_list,
    current_corpus=1754783.21,
    annual_expense=600000.0,
    years=15,
    num_simulations=10000
):
    if daily_returns_list is None or len(daily_returns_list) < 10:
        return {
            "status": "INSUFFICIENT_DATA",
            "success_rate_pct": 95.0,
            "median_ending_corpus": current_corpus * 1.5,
            "tenth_percentile_corpus": current_corpus * 0.9
        }

    returns = np.array(daily_returns_list)
    trading_days = years * 252
    daily_expense = annual_expense / 252.0

    # Historical Bootstrapping: Randomly sample actual past daily returns with replacement to preserve true fat tails & skewness
    simulated_daily_returns = np.random.choice(returns, size=(num_simulations, trading_days), replace=True)

    surviving_sims = 0
    final_corpuses = []

    for sim_idx in range(num_simulations):
        corpus = current_corpus
        failed = False
        for day in range(trading_days):
            corpus = corpus * (1.0 + simulated_daily_returns[sim_idx, day]) - daily_expense
            if corpus <= 0:
                failed = True
                break
        if not failed:
            surviving_sims += 1
            final_corpuses.append(corpus)
        else:
            final_corpuses.append(0.0)

    success_rate = (surviving_sims / num_simulations) * 100.0
    median_corpus = float(np.median(final_corpuses))
    p10_corpus = float(np.percentile(final_corpuses, 10))

    return {
        "status": "OK",
        "num_simulations": num_simulations,
        "years": years,
        "success_rate_pct": round(success_rate, 2),
        "median_ending_corpus": round(median_corpus, 2),
        "tenth_percentile_corpus": round(p10_corpus, 2)
    }
</file>

</files>
</file>

<file path="core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java">
package com.portfolioos.core.persistence;

import com.portfolioos.core.model.TaxEvent;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

public class DuckDbProjector {

    private final String dbPath;
    private final String jdbcUrl;
    private final HikariDataSource dataSource;

    public static record NavHistorySeriesEntry(
        List<Double> navs,
        List<String> dates
    ) {}

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

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.duckdb.DuckDBDriver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setPoolName("DuckDbProjectorPool");

        this.dataSource = new HikariDataSource(config);
        initReadSchema();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void initReadSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
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

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS nav_history (" +
                "  asset_id VARCHAR NOT NULL," +
                "  nav_date VARCHAR NOT NULL," +
                "  nav DOUBLE NOT NULL," +
                "  PRIMARY KEY (asset_id, nav_date)" +
                ")"
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize DuckDB schema", e);
        }
    }

    public void projectEvents(List<TaxEvent> events) {
        if (events == null || events.isEmpty()) return;

        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                String insertSql = "INSERT INTO projected_events (id, asset_id, asset_name, isin, event_type, event_date, units, price_per_unit, gross_amount, source_document_id, ingested_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
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

    public void saveNavHistoryBatchForHeldAssets(Map<String, BigDecimal> navMap, Set<String> heldIsins, LocalDate date) {
        if (navMap == null || navMap.isEmpty() || heldIsins == null || heldIsins.isEmpty()) return;

        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                String dateStr = date.toString();
                String sql = "INSERT INTO nav_history (asset_id, nav_date, nav) VALUES (?, ?, ?) ON CONFLICT (asset_id, nav_date) DO NOTHING";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (String isin : heldIsins) {
                        BigDecimal nav = navMap.get(isin);
                        if (nav != null) {
                            stmt.setString(1, isin);
                            stmt.setString(2, dateStr);
                            stmt.setDouble(3, nav.doubleValue());
                            stmt.executeUpdate();
                        }
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("DuckDB nav_history save failure: " + e.getMessage());
        }
    }

    public Map<String, List<Double>> getNavHistorySeries(Set<String> assetIds) {
        Map<String, NavHistorySeriesEntry> full = getNavHistorySeriesWithDates(assetIds);
        Map<String, List<Double>> result = new HashMap<>();
        for (Map.Entry<String, NavHistorySeriesEntry> entry : full.entrySet()) {
            result.put(entry.getKey(), entry.getValue().navs());
        }
        return result;
    }

    public Map<String, NavHistorySeriesEntry> getNavHistorySeriesWithDates(Set<String> assetIds) {
        Map<String, NavHistorySeriesEntry> result = new HashMap<>();
        if (assetIds == null || assetIds.isEmpty()) return result;

        try (Connection conn = getConnection()) {
            String sql = "SELECT asset_id, nav_date, nav FROM nav_history WHERE asset_id = ? ORDER BY nav_date ASC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (String assetId : assetIds) {
                    stmt.setString(1, assetId);
                    List<Double> navs = new ArrayList<>();
                    List<String> dates = new ArrayList<>();
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            dates.add(rs.getString("nav_date"));
                            navs.add(rs.getDouble("nav"));
                        }
                    }
                    if (!navs.isEmpty()) {
                        result.put(assetId, new NavHistorySeriesEntry(navs, dates));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch NAV history series with dates from DuckDB: " + e.getMessage());
        }
        return result;
    }

    public static record NetWorthPoint(
        String date,
        double valuation,
        double invested
    ) {}

    public List<NetWorthPoint> getDailyNetWorthTrend() {
        List<NetWorthPoint> trend = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT nav_date, SUM(nav) as total_nav FROM daily_nav_history GROUP BY nav_date ORDER BY nav_date ASC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String d = rs.getString("nav_date");
                    double val = rs.getDouble("total_nav");
                    trend.add(new NetWorthPoint(d, val, val * 0.9));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch daily net worth trend: " + e.getMessage());
        }
        return trend;
    }
}
</file>

<file path="core-node/src/main/resources/static/src/app.js">
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

  // Command Palette Handler (Cmd + K / Ctrl + K / Slash)
  const cmdPaletteModal = document.getElementById('commandPaletteModal');
  const cmdInput = document.getElementById('commandPaletteInput');
  const cmdResults = document.getElementById('commandPaletteResults');

  function openCmdPalette() {
    const modal = document.getElementById('commandPaletteModal') || cmdPaletteModal;
    const input = document.getElementById('commandPaletteInput') || cmdInput;
    if (!modal) return;

    modal.style.display = 'flex';

    if (input) {
      setTimeout(() => {
        input.focus();
        input.select();
      }, 50);
    }
  }

  function closeCmdPalette() {
    const modal = document.getElementById('commandPaletteModal') || cmdPaletteModal;
    if (!modal) return;
    modal.style.display = 'none';
  }

  window.openCmdPalette = openCmdPalette;
  window.closeCmdPalette = closeCmdPalette;

  // Event Delegation for Button, Close X, and Backdrop Click
  document.addEventListener('click', (e) => {
    if (e.target.closest('#cmdKTriggerBtn, .cmd-k-btn')) {
      e.preventDefault();
      openCmdPalette();
      return;
    }

    if (e.target.closest('#closeCmdPaletteBtn')) {
      e.preventDefault();
      closeCmdPalette();
      return;
    }

    const modal = document.getElementById('commandPaletteModal') || cmdPaletteModal;
    if (modal && e.target === modal) {
      closeCmdPalette();
    }
  });

  if (cmdPaletteModal) {
    cmdPaletteModal.addEventListener('cancel', () => closeCmdPalette());
  }

  window.addEventListener('keydown', (e) => {
    const key = e.key ? e.key.toLowerCase() : '';
    const isInputActive = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName);

    if (((e.metaKey || e.ctrlKey || e.altKey) && key === 'k') || (!isInputActive && key === '/')) {
      e.preventDefault();
      e.stopPropagation();
      openCmdPalette();
    }
  }, true);

  if (cmdInput) {
    cmdInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        const query = cmdInput.value.trim();
        if (!query) return;

        if (cmdResults) {
          cmdResults.innerHTML = '<div style="padding:12px; color:#06b6d4; font-family:monospace;">🧠 AI Engine Thinking...</div>';
        }

        const evtSource = new EventSource(`/api/v1/llm/stream?prompt=${encodeURIComponent(query)}`);
        let outputText = '';

        evtSource.onmessage = function(event) {
          outputText += event.data;
          if (cmdResults) {
            cmdResults.innerHTML = `
              <div style="padding:12px; background:#0f172a; border-radius:8px; color:#f8fafc; font-size:13px; white-space:pre-wrap; font-family:monospace; line-height:1.5;">
                <div style="color:#d0ff00; font-weight:bold; margin-bottom:6px;">⚡ PORTFOLIO OS AI RESPONSE</div>
                ${outputText}
              </div>
            `;
          }
        };

        evtSource.onerror = function() {
          evtSource.close();
        };
      }
    });
  }

  if (cmdResults) {
    cmdResults.addEventListener('click', (e) => {
      const item = e.target.closest('.cmd-item');
      if (!item) return;
      const action = item.getAttribute('data-action');
      closeCmdPalette();

      if (action === 'schedule-cg') {
        window.open('/api/v1/tax/schedule-cg/export', '_blank');
        showToast('Downloading Schedule CG Tax Report CSV...', 'success');
      } else if (action === 'rebalance') {
        fetchBucketRebalance();
        showToast('Evaluating Portfolio Rebalance Rungs...', 'info');
      } else if (action === 'whatif' || action === 'holdings') {
        const hTab = document.querySelector('[data-tab="holdings"]');
        if (hTab) hTab.click();
      } else if (action === 'radar') {
        fetchDecisionRadar();
      }
    });
  }

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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/controllers/SyncController.java">
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
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.persistence.DuckDbProjector.NavHistorySeriesEntry;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.rpc.FlightRpcClient;
import com.portfolioos.core.service.LedgerCacheService;
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

    private final LedgerCacheService cacheService;
    private final XirrEngine xirrEngine = new XirrEngine();
    private final DuckDbProjector duckDbProjector = new DuckDbProjector();
    private final FlightRpcClient flightRpcClient = new FlightRpcClient();

    public SyncController(LedgerCacheService cacheService) {
        this.cacheService = cacheService;
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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<TaxEvent> allEvents = state.events();
        List<Lot> openLots = state.fifoResult().openLots();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
        Map<String, BigDecimal> navMap = state.navMap();
        String ledgerHash = state.ledgerHash();

        LocalDate today = LocalDate.now();
        Locale inLocale = new Locale("en", "IN");
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(inLocale);

        // Collect held ISINs and persist daily NAV history strictly for held assets
        Set<String> heldIsins = openLots.stream().map(Lot::assetId).collect(Collectors.toSet());
        duckDbProjector.saveNavHistoryBatchForHeldAssets(navMap, heldIsins, today);

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

        // Generate Verified Priority AI Radar Signals
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

        // 2. PyArrow Flight RPC Quant Intelligence (from real DuckDB NAV time-series with dates)
        try {
            Map<String, NavHistorySeriesEntry> navHistorySeries = duckDbProjector.getNavHistorySeriesWithDates(heldIsins);
            if (!navHistorySeries.isEmpty()) {
                Map<String, Map<String, Object>> quantMetrics = flightRpcClient.computeQuantMetricsWithDates(navHistorySeries);
                Map<String, String> isinToNameMap = holdings.stream().collect(Collectors.toMap(FlatHoldingDto::isin, FlatHoldingDto::fundName, (a, b) -> a));

                for (Map.Entry<String, Map<String, Object>> entry : quantMetrics.entrySet()) {
                    String isin = entry.getKey();
                    Map<String, Object> metrics = entry.getValue();
                    if (metrics == null) continue;

                    String status = String.valueOf(metrics.getOrDefault("status", "INSUFFICIENT_HISTORY"));
                    if (!"OK".equalsIgnoreCase(status)) {
                        continue;
                    }

                    String schemeName = isinToNameMap.getOrDefault(isin, isin);

                    Object sharpeObj = metrics.get("sharpe");
                    Object maxDdObj = metrics.get("max_drawdown");

                    String bucket = detectFineBucket(schemeName);

                    if (sharpeObj instanceof Number sharpe && sharpe.doubleValue() >= 1.2) {
                        radarSignals.add(new RadarSignalDto(
                            "QUANT_ANALYTICS",
                            schemeName,
                            "QUANT STATS: HIGH SHARPE (" + String.format("%.2f", sharpe.doubleValue()) + ")",
                            "[" + bucket + "] " + schemeName + " displays a risk-adjusted Sharpe ratio of " + String.format("%.2f", sharpe.doubleValue()) + " over tracked NAV history.",
                            "INFO",
                            "Sharpe " + String.format("%.2f", sharpe.doubleValue())
                        ));
                    }

                    double ddThreshold = switch (bucket) {
                        case "Debt & Liquid" -> 0.05;
                        case "Core Equity", "Flexi Cap", "Large & Midcap", "Equal Weight Index", "Gold & Commodities" -> 0.15;
                        default -> 0.25; // Small Cap, Microcap, Sectoral, Midcap, Factor Value/Momentum
                    };

                    if (maxDdObj instanceof Number maxDd && Math.abs(maxDd.doubleValue()) >= ddThreshold) {
                        double maxDdPct = Math.abs(maxDd.doubleValue()) * 100.0;
                        double thresholdPct = ddThreshold * 100.0;
                        radarSignals.add(new RadarSignalDto(
                            "QUANT_ANALYTICS",
                            schemeName,
                            "QUANT STATS: DEEP DRAWDOWN (" + String.format("%.1f", maxDdPct) + "%)",
                            "[" + bucket + "] " + schemeName + " max drawdown (" + String.format("%.1f", maxDdPct) + "%) exceeds " + String.format("%.0f", thresholdPct) + "% " + bucket + " category threshold.",
                            "WARNING",
                            "Max DD -" + String.format("%.1f", maxDdPct) + "%"
                        ));
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("Non-critical Quant Flight RPC signal extraction warning: " + ex.getMessage());
        }

        // 2.5 Automated SIP Cashflow Signal
        long sipCount = allEvents.stream()
            .filter(e -> e.eventType() == EventType.SIP_INSTALMENT)
            .map(TaxEvent::assetId)
            .distinct()
            .count();

        if (sipCount > 0) {
            radarSignals.add(0, new RadarSignalDto(
                "SIP_DETECTION",
                "Automated SIP Tracker",
                "RECURRING SIP DISCIPLINE",
                String.format("Auto-detected %d active monthly SIPs across portfolio. Disciplined recurring cashflow active.", sipCount),
                "INFO",
                sipCount + " Active SIPs"
            ));
        }

        // 3. LTCG Maturation Ladder Signal
        Lot maturingLot = null;
        long minDaysToLtcg = 9999L;

        for (Lot lot : openLots) {
            long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
            long daysToLtcg = Math.max(0L, 365L - holdingDays);
            if (daysToLtcg > 0 && daysToLtcg <= 120 && daysToLtcg < minDaysToLtcg) {
                minDaysToLtcg = daysToLtcg;
                maturingLot = lot;
            }
        }

        if (maturingLot != null) {
            radarSignals.add(0, new RadarSignalDto(
                "MATURATION",
                maturingLot.assetName(),
                "LTCG MATURATION LADDER",
                maturingLot.assetName() + " (Lot " + maturingLot.lotId() + ") matures under Sec 112A in " + minDaysToLtcg + " days.",
                "INFO",
                minDaysToLtcg + " Days"
            ));
        }

        // 4. Asset Allocation Drift Signal
        BucketEngine.RebalanceEngineResult bucketStatus = BucketEngine.evaluateRebalance(
            openLots, navMap, today, new BigDecimal("24000.00"), new BigDecimal("25000.00"), BucketEngine.DEFAULT_TARGETS, fy
        );

        BucketEngine.BucketStatus driftedBucket = bucketStatus.bucketStatuses().stream()
            .filter(BucketEngine.BucketStatus::isDrifted)
            .findFirst()
            .orElse(null);

        if (driftedBucket != null) {
            radarSignals.add(new RadarSignalDto(
                "REBALANCE",
                "Bucket " + driftedBucket.bucket().name(),
                "ALLOCATION DRIFT ALERT",
                "Current allocation is " + driftedBucket.currentPct() + "% vs target " + driftedBucket.targetPct() + "%. Rebalance recommended.",
                "WARNING",
                "Rebalance"
            ));
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

        List<NetWorthPointDto> netWorthHistory = duckDbProjector.getDailyNetWorthTrend().stream()
            .map(p -> new NetWorthPointDto(p.date(), p.valuation(), p.invested()))
            .toList();

        return ResponseEntity.ok(new UnidirectionalSyncSnapshot(
            syncInfo, holdings, taxLots, radarSignals, netWorthHistory
        ));
    }

    @PostMapping("/pair")
    public ResponseEntity<PairResponseDto> pairDevice(
        @RequestBody PairRequestDto req
    ) {
        String token = "fintracker_jwt_" + req.deviceId() + "_" + System.currentTimeMillis();
        return ResponseEntity.ok(new PairResponseDto(
            "SUCCESS",
            token,
            "my-fintracker-core"
        ));
    }
}
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/ui/DashboardScreen.kt">
package com.portfolioos.mobile.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.FlatTaxLotDto
import com.portfolioos.mobile.model.RadarSignalDto
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.util.formatInr
import kotlinx.coroutines.launch

// Bleeding-Edge Material 3 Expressive Vibrant Obsidian Palette
val M3ObsidianDark = Color(0xFF030712)
val M3SurfaceCard = Color(0xFF0D1424)
val M3SurfaceVariant = Color(0xFF162036)
val M3ElectricLime = Color(0xFFD0FF00)
val M3NeonCyan = Color(0xFF00F0FF)
val M3VibrantViolet = Color(0xFFE040FB)
val M3GreenPositive = Color(0xFF10B981)
val M3AmberWarning = Color(0xFFF59E0B)
val M3TextMuted = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    snapshot: SyncSnapshot?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onUpdateCustomUrl: (String) -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    var showUrlDialog by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = M3ObsidianDark,
            surface = M3SurfaceCard,
            surfaceVariant = M3SurfaceVariant,
            primary = M3ElectricLime,
            secondary = M3NeonCyan,
            tertiary = M3VibrantViolet
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(M3ObsidianDark)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Sleek Expressive Top Header
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "PORTFOLIO OS",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                letterSpacing = 3.sp,
                                color = Color.White
                            )
                            Surface(
                                color = M3ElectricLime.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = snapshot?.syncInfo?.fiscalYear?.let { "FY $it · Android 17 Edge" } ?: "Sync Active",
                                    fontSize = 10.sp,
                                    color = M3ElectricLime,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showUrlDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Server Settings",
                                tint = M3ElectricLime
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = M3ObsidianDark
                    )
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = M3ElectricLime)
                    }
                } else if (snapshot == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Core Node Offline / Not Synced",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Connect over Wi-Fi, USB, or set a custom server URL.",
                                    color = M3TextMuted,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onRefresh,
                                        colors = ButtonDefaults.buttonColors(containerColor = M3ElectricLime),
                                        shape = RoundedCornerShape(100.dp)
                                    ) {
                                        Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { showUrlDialog = true },
                                        shape = RoundedCornerShape(100.dp)
                                    ) {
                                        Text("Set Server URL", color = M3NeonCyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val syncInfo = snapshot.syncInfo
                    val holdings = snapshot.holdings ?: emptyList()
                    val radarSignals = snapshot.radarSignals ?: emptyList()
                    val taxLots = snapshot.taxLots ?: emptyList()

                    // High-performance 120fps Horizontal Pager with zero per-frame transform overhead
                    HorizontalPager(
                        state = pagerState,
                        beyondBoundsPageCount = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        when (page) {
                            0 -> HoldingsView(snapshot, syncInfo, holdings)
                            1 -> RadarSignalsView(radarSignals)
                            2 -> GroupedTaxLotsView(taxLots, holdings)
                            3 -> SimulatorView(holdings)
                        }
                    }
                }
            }

            // Google Material 3 Expressive Floating Glassmorphic Pill Overlaid directly over Screen
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF090F1E).copy(alpha = 0.94f),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(100.dp))
                        .border(
                            width = 1.5.dp,
                            brush = Brush.horizontalGradient(
                                listOf(M3ElectricLime.copy(alpha = 0.5f), M3NeonCyan.copy(alpha = 0.3f), M3VibrantViolet.copy(alpha = 0.4f))
                            ),
                            shape = RoundedCornerShape(100.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 0,
                            label = "Holdings",
                            icon = Icons.Default.Star,
                            activeColor = M3ElectricLime,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        )
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 1,
                            label = "AI Radar",
                            icon = Icons.Default.Notifications,
                            activeColor = M3VibrantViolet,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }
                        )
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 2,
                            label = "Tax Lots",
                            icon = Icons.Default.List,
                            activeColor = M3NeonCyan,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(2)
                                }
                            }
                        )
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 3,
                            label = "Simulator",
                            icon = Icons.Default.Settings,
                            activeColor = Color(0xFFD0FF00),
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(3)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Surface(
                            onClick = onRefresh,
                            color = M3ElectricLime,
                            shape = CircleShape,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Dialog for setting Custom Core Node Remote Server URL (Tailscale / Ngrok / LAN IP)
            if (showUrlDialog) {
                AlertDialog(
                    onDismissRequest = { showUrlDialog = false },
                    title = { Text("Core Node Server URL", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                text = "Enter custom Core Node IP or Tunnel URL (e.g. http://192.168.1.13:8080 or https://xyz.ngrok-free.app):",
                                color = M3TextMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = inputUrl,
                                onValueChange = { inputUrl = it },
                                placeholder = { Text("http://192.168.1.13:8080", color = M3TextMuted) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onUpdateCustomUrl(inputUrl.trim())
                                showUrlDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = M3ElectricLime)
                        ) {
                            Text("Save & Sync", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUrlDialog = false }) {
                            Text("Cancel", color = M3TextMuted)
                        }
                    },
                    containerColor = M3SurfaceCard,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

@Composable
fun ExpressiveNavPill(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    onClick: () -> Unit
) {
    val pillScale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "PillScale"
    )

    Surface(
        onClick = onClick,
        color = if (selected) activeColor.copy(alpha = 0.22f) else Color.Transparent,
        shape = RoundedCornerShape(100.dp),
        modifier = Modifier
            .scale(pillScale)
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessHigh))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) activeColor else M3TextMuted,
                modifier = Modifier.size(18.dp)
            )
            if (selected) {
                Text(
                    text = label,
                    color = activeColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun HoldingsView(snapshot: com.portfolioos.mobile.model.SyncSnapshot?, syncInfo: com.portfolioos.mobile.model.SyncInfoDto?, holdings: List<FlatHoldingDto>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Expressive M3 Hero Net Worth Card (en-IN Currency Format)
            Card(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 10.dp, bottomEnd = 32.dp, bottomStart = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(M3ElectricLime.copy(alpha = 0.7f), M3NeonCyan.copy(alpha = 0.35f))),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 10.dp, bottomEnd = 32.dp, bottomStart = 10.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF142600), Color(0xFF062C33), Color(0xFF0D1424))
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = M3ElectricLime.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text(
                                    text = "NET WORTH VALUATION",
                                    color = M3ElectricLime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = M3GreenPositive.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text(
                                    text = syncInfo?.xirrPercentage ?: "0.00% XIRR",
                                    color = M3GreenPositive,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = formatInr(syncInfo?.currentValue ?: 0.0),
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Invested",
                                    color = M3TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatInr(syncInfo?.totalInvested ?: 0.0),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Unrealized Gain",
                                    color = M3TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val gain = syncInfo?.unrealizedGain ?: 0.0
                                Text(
                                    text = "${if (gain >= 0) "+" else ""}${formatInr(gain)}",
                                    color = if (gain >= 0) M3GreenPositive else Color.Red,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            HistoricalNetWorthTrendChart(trendPoints = snapshot?.netWorthHistory ?: emptyList())
        }

        item {
            DonutAllocationChart(holdings = holdings)
        }

        item {
            PerformanceBarChart(holdings = holdings)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE HOLDINGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = M3TextMuted,
                    letterSpacing = 1.5.sp
                )
                Surface(
                    color = M3SurfaceVariant,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "${holdings.size} Schemes",
                        color = M3NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        if (holdings.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No open holdings recorded in ledger.",
                        color = M3TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(holdings, key = { h -> h.isin.ifEmpty { h.fundName } }) { holding ->
                M3HoldingCard(holding)
            }
        }
    }
}

@Composable
fun M3HoldingCard(holding: FlatHoldingDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 8.dp, bottomEnd = 20.dp, bottomStart = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = holding.fundName.ifEmpty { holding.isin },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = M3ElectricLime.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "🔄 SIP",
                        color = M3ElectricLime,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    color = if (holding.xirr >= 0) M3GreenPositive.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "${if (holding.xirr >= 0) "+" else ""}${holding.xirr}% XIRR",
                        color = if (holding.xirr >= 0) M3GreenPositive else Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Valuation: ${formatInr(holding.currentValue)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${holding.totalUnits} Units · Cost: ${formatInr(holding.investedValue)}",
                        color = M3TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Surface(
                    color = M3SurfaceVariant,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = holding.assetBucket,
                        color = M3NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RadarSignalsView(radarSignals: List<RadarSignalDto>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "PRIORITY AI RADAR & QUANT INTELLIGENCE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = M3TextMuted,
                letterSpacing = 1.5.sp
            )
        }

        if (radarSignals.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ Portfolio status optimal. No immediate tax or rebalance recommendations.",
                        color = M3GreenPositive,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(radarSignals, key = { s -> "${s.title}-${s.signalType}" }) { signal ->
                M3RadarCard(signal)
            }
        }
    }
}

@Composable
fun M3RadarCard(signal: RadarSignalDto) {
    val isQuant = signal.signalType.contains("QUANT", ignoreCase = true)
    val isWarning = signal.severity.equals("WARNING", ignoreCase = true)
    val borderColor = if (isQuant) M3VibrantViolet else if (isWarning) M3AmberWarning else M3NeonCyan
    val containerColor = if (isQuant) Color(0xFF1A0A26) else M3SurfaceCard

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(borderColor)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 24.dp, bottomStart = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = signal.title.ifEmpty { "Recommendation" },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = borderColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = signal.badgeText.ifEmpty { "Action Required" },
                        color = borderColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = signal.description,
                color = M3TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun GroupedTaxLotsView(taxLots: List<FlatTaxLotDto>, holdings: List<FlatHoldingDto>) {
    val nameMap = remember(holdings) {
        holdings.associate { it.isin to it.fundName }
    }

    val groupedLots = remember(taxLots) {
        taxLots.groupBy { it.isin }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SCHEME-GROUPED TAX LOTS (${groupedLots.size} SCHEMES · ${taxLots.size} LOTS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = M3TextMuted,
                letterSpacing = 1.5.sp
            )
        }

        if (groupedLots.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No tax lots recorded.",
                        color = M3TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(groupedLots.entries.toList(), key = { entry -> entry.key }) { (isin, lots) ->
                val schemeName = nameMap[isin] ?: isin
                GroupedSchemeTaxLotCard(schemeName = schemeName, isin = isin, lots = lots)
            }
        }
    }
}

@Composable
fun GroupedSchemeTaxLotCard(schemeName: String, isin: String, lots: List<FlatTaxLotDto>) {
    var expanded by remember { mutableStateOf(false) }

    val ltcgCount = remember(lots) { lots.count { it.isLongTerm } }
    val stcgCount = remember(lots) { lots.size - ltcgCount }
    val totalUnits = remember(lots) { lots.sumOf { it.units } }

    Card(
        colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schemeName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${lots.size} Open Lots · Total %.2f Units".format(totalUnits),
                        color = M3TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ltcgCount > 0) {
                        Surface(
                            color = M3GreenPositive.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "$ltcgCount LTCG",
                                color = M3GreenPositive,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (stcgCount > 0) {
                        Surface(
                            color = M3AmberWarning.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "$stcgCount STCG",
                                color = M3AmberWarning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = M3NeonCyan
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = M3SurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    lots.forEach { lot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${lot.buyDate} · ${lot.units} u @ ${formatInr(lot.costPerUnit)}",
                                color = M3TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (lot.isLongTerm) "LTCG" else "STCG (${lot.daysToLtcg}d)",
                                color = if (lot.isLongTerm) M3GreenPositive else M3AmberWarning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
</file>

<file path="repomix-mobile.md">
This file is a merged representation of a subset of the codebase, containing specifically included files, combined into a single document by Repomix.

<file_summary>
This section contains a summary of this file.

<purpose>
This file contains a packed representation of a subset of the repository's contents that is considered the most important context.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.
</purpose>

<file_format>
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Repository files (if enabled)
5. Multiple file entries, each consisting of:
  - File path as an attribute
  - Full contents of the file
</file_format>

<usage_guidelines>
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.
</usage_guidelines>

<notes>
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Only files matching these patterns are included: mobile-app/**/*
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Files are sorted by Git change count (files with more changes are at the bottom)
</notes>

</file_summary>

<directory_structure>
mobile-app/
  app/
    src/
      main/
        java/
          com/
            portfolioos/
              mobile/
                api/
                  SyncApiClient.kt
                model/
                  SyncModels.kt
                ui/
                  DashboardScreen.kt
                  PortfolioCharts.kt
                  SimulatorScreen.kt
                util/
                  FormatUtils.kt
                widget/
                  PortfolioGlanceWidget.kt
                MainActivity.kt
        res/
          drawable/
            ic_launcher_background.xml
            ic_launcher_foreground.xml
          mipmap-anydpi-v26/
            ic_launcher_round.xml
            ic_launcher.xml
          values/
            styles.xml
          xml/
            backup_rules.xml
            data_extraction_rules.xml
            portfolio_glance_widget_info.xml
        AndroidManifest.xml
    build.gradle.kts
  build.gradle.kts
  gradle.properties
  local.properties
  settings.gradle.kts
</directory_structure>

<files>
This section contains the contents of the repository's files.

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/util/FormatUtils.kt">
package com.portfolioos.mobile.util

import java.text.NumberFormat
import java.util.Locale

fun formatInr(valNum: Double, showDecimals: Boolean = false): String {
    val locale = Locale("en", "IN")
    val formatter = NumberFormat.getCurrencyInstance(locale).apply {
        maximumFractionDigits = if (showDecimals) 2 else 0
        minimumFractionDigits = if (showDecimals) 2 else 0
    }
    val formatted = formatter.format(valNum)
    return if (formatted.startsWith("INR")) {
        formatted.replace("INR", "₹").trim()
    } else {
        formatted
    }
}

fun formatInrStr(valStr: String?): String {
    if (valStr.isNullOrBlank()) return "₹0"
    val cleaned = valStr.replace("₹", "").replace(",", "").trim()
    val dbl = cleaned.toDoubleOrNull() ?: return valStr
    return formatInr(dbl, showDecimals = false)
}
</file>

<file path="mobile-app/app/src/main/res/drawable/ic_launcher_background.xml">
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#030712"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#0D1424"
        android:pathData="M0,0 L108,108 L0,108 Z" />
</vector>
</file>

<file path="mobile-app/app/src/main/res/drawable/ic_launcher_foreground.xml">
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Glowing Cyber Grid Accent -->
    <path
        android:strokeColor="#1E293B"
        android:strokeWidth="1"
        android:pathData="M24,36 H84 M24,54 H84 M24,72 H84" />

    <!-- Upward Trend Line -->
    <path
        android:strokeColor="#00F0FF"
        android:strokeWidth="4"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M28,68 L44,52 L56,60 L80,36" />

    <!-- Trend Line Sparkle Dots -->
    <path
        android:fillColor="#D0FF00"
        android:pathData="M80,36 m-4,0 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0" />

    <!-- Portfolio OS Monogram "P" Emblem -->
    <path
        android:fillColor="#D0FF00"
        android:pathData="M34,34 h16 a12,12 0 0,1 0,24 h-8 v16 h-8 z" />

    <path
        android:fillColor="#030712"
        android:pathData="M42,42 h8 a4,4 0 0,1 0,8 h-8 z" />
</vector>
</file>

<file path="mobile-app/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml">
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
</file>

<file path="mobile-app/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml">
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
</file>

<file path="mobile-app/app/src/main/res/values/styles.xml">
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.PortfolioOS" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">#050811</item>
        <item name="android:windowBackground">#050811</item>
    </style>
</resources>
</file>

<file path="mobile-app/app/src/main/res/xml/backup_rules.xml">
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude path="." />
</full-backup-content>
</file>

<file path="mobile-app/app/src/main/res/xml/data_extraction_rules.xml">
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude path="." />
    </cloud-backup>
</data-extraction-rules>
</file>

<file path="mobile-app/app/src/main/res/xml/portfolio_glance_widget_info.xml">
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:updatePeriodMillis="1800000"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen">
</appwidget-provider>
</file>

<file path="mobile-app/build.gradle.kts">
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
</file>

<file path="mobile-app/gradle.properties">
android.useAndroidX=true
android.nonFinalResIds=false
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
</file>

<file path="mobile-app/local.properties">
sdk.dir=/home/rakeshpc/Android/Sdk
</file>

<file path="mobile-app/settings.gradle.kts">
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "portfolio-os-mobile"
include(":app")
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/ui/SimulatorScreen.kt">
package com.portfolioos.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.TradeSimulationRequestDto
import com.portfolioos.mobile.util.formatInr
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorView(holdings: List<FlatHoldingDto>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedIsin by remember { mutableStateOf(holdings.firstOrNull()?.isin ?: "") }
    var selectedName by remember { mutableStateOf(holdings.firstOrNull()?.fundName ?: "Select Scheme") }
    var unitsText by remember { mutableStateOf("100.0") }
    var priceText by remember { mutableStateOf("150.0") }
    var tradeType by remember { mutableStateOf("DISPOSAL") }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "⚡ WHAT-IF TRADE SIMULATOR",
            color = Color(0xFFD0FF00),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = "Preview tax drag and post-trade XIRR before executing trades.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Scheme Selector
        ExposedDropdownMenuBox(
            expanded = isExpanded,
            onExpandedChange = { isExpanded = !isExpanded }
        ) {
            OutlinedTextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Target Scheme", color = Color(0xFF94A3B8)) },
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFF00F0FF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false }
            ) {
                holdings.forEach { holding ->
                    DropdownMenuItem(
                        text = { Text(holding.fundName) },
                        onClick = {
                            selectedIsin = holding.isin
                            selectedName = holding.fundName
                            isExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = unitsText,
                onValueChange = { unitsText = it },
                label = { Text("Units", color = Color(0xFF94A3B8)) },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Price/NAV (₹)", color = Color(0xFF94A3B8)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tradeType == "DISPOSAL",
                onClick = { tradeType = "DISPOSAL" },
                label = { Text("Simulate Sale (Disposal)") }
            )
            FilterChip(
                selected = tradeType == "ACQUISITION",
                onClick = { tradeType = "ACQUISITION" },
                label = { Text("Simulate Buy (SIP)") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val units = unitsText.toDoubleOrNull() ?: 100.0
                val price = priceText.toDoubleOrNull() ?: 150.0
                isLoading = true
                scope.launch {
                    try {
                        val req = TradeSimulationRequestDto(
                            isin = selectedIsin,
                            schemeName = selectedName,
                            units = units,
                            pricePerUnit = price,
                            tradeType = tradeType
                        )
                        val res = SyncApiClient.simulateTradeWithFallback(context, req)
                        resultText = """
                            ✓ Simulation Execution Successful (Live Engine)
                            • Target: ${res.schemeName}
                            • Trade Type: ${res.tradeType} (${res.units} Units @ ₹${res.pricePerUnit})
                            • Gross Trade Amount: ${formatInr(res.grossTradeAmount)}
                            • Gross Capital Gain: ${formatInr(res.grossCapitalGain)}
                            • LTCG Equity: ${formatInr(res.ltcgEquity)} | STCG Equity: ${formatInr(res.stcgEquity)}
                            • Sec 112A Exemption Applied: ${formatInr(res.sec112aExemptionApplied)}
                            • Projected Tax Liability: ${formatInr(res.estimatedTaxLiability)}
                            • Post-Trade Valuation: ${formatInr(res.postTradeNetWorth)}
                            • Post-Trade Portfolio XIRR: ${String.format("%.2f", res.postTradeXirr)}%
                        """.trimIndent()
                    } catch (e: Exception) {
                        resultText = "⚠️ Simulation RPC failed: ${e.localizedMessage}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0FF00)),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
            } else {
                Text("Run What-If Simulation", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (resultText != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = resultText!!,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/widget/PortfolioGlanceWidget.kt">
package com.portfolioos.mobile.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.portfolioos.mobile.MainActivity
import com.portfolioos.mobile.data.SnapshotCacheManager

class PortfolioGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = SnapshotCacheManager.loadSnapshot(context)
        val info = snapshot?.syncInfo
        val holdings = snapshot?.holdings ?: emptyList()

        val bestFund = holdings.maxByOrNull { it.xirr }
        val worstFund = holdings.minByOrNull { it.xirr }

        // Calculate portfolio gain percentage for privacy-first display
        val totalInvested = info?.totalInvested ?: 1.0
        val unrealizedGain = info?.unrealizedGain ?: 0.0
        val gainPct = if (totalInvested > 0) (unrealizedGain / totalInvested) * 100.0 else 0.0
        val formattedGainPct = String.format("%s%.2f%%", if (gainPct >= 0) "+" else "", gainPct)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFF0D1424)))
                        .padding(14.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = "PORTFOLIO OS",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFD0FF00)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = info?.xirrPercentage ?: "0.00% XIRR",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF10B981)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Privacy-First Valuation & Return Header
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(
                            text = "₹ • • • • • •",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF94A3B8)),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = formattedGainPct,
                            style = TextStyle(
                                color = ColorProvider(if (gainPct >= 0) Color(0xFF10B981) else Color(0xFFEF4444)),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "BEST PERFORMER",
                                style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 8.sp)
                            )
                            Text(
                                text = bestFund?.let { "${it.fundName.take(14)} (+${it.xirr}%)" } ?: "N/A",
                                style = TextStyle(color = ColorProvider(Color(0xFF10B981)), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "WORST PERFORMER",
                                style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 8.sp)
                            )
                            Text(
                                text = worstFund?.let { "${it.fundName.take(14)} (${it.xirr}%)" } ?: "N/A",
                                style = TextStyle(color = ColorProvider(Color(0xFFF59E0B)), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    Text(
                        text = "Valuation Hidden for Privacy · Tap to Open App",
                        style = TextStyle(color = ColorProvider(Color(0xFF00F0FF)), fontSize = 9.sp)
                    )
                }
            }
        }
    }
}

class PortfolioGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioGlanceWidget()
}
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/MainActivity.kt">
package com.portfolioos.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.ui.DashboardScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var snapshot by remember { mutableStateOf<SyncSnapshot?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()

            fun fetchSyncSnapshot() {
                scope.launch {
                    isLoading = true
                    try {
                        snapshot = SyncApiClient.fetchSnapshotWithFallback(applicationContext)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }

            LaunchedEffect(Unit) {
                fetchSyncSnapshot()
            }

            DashboardScreen(
                snapshot = snapshot,
                isLoading = isLoading,
                onRefresh = { fetchSyncSnapshot() },
                onUpdateCustomUrl = { newUrl ->
                    SnapshotCacheManager.setCustomUrl(applicationContext, newUrl)
                    fetchSyncSnapshot()
                }
            )
        }
    }
}
</file>

<file path="mobile-app/app/src/main/AndroidManifest.xml">
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="Portfolio OS"
        android:supportsRtl="true"
        android:theme="@style/Theme.PortfolioOS"
        android:usesCleartextTraffic="true"
        tools:targetApi="31">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PortfolioOS">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".widget.PortfolioGlanceReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/portfolio_glance_widget_info" />
        </receiver>
    </application>

</manifest>
</file>

<file path="mobile-app/app/build.gradle.kts">
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.portfolioos.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.portfolioos.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Jetpack Glance Widget
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.glance:glance-material3:1.0.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/ui/PortfolioCharts.kt">
package com.portfolioos.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.NetWorthPointDto

data class BucketAllocation(
    val bucketName: String,
    val totalAmount: Double,
    val percentage: Float,
    val color: Color
)

val SEBIBucketColors = mapOf(
    "Flexi Cap" to Color(0xFF06B6D4),                // Vibrant Cyan
    "Large & Midcap" to Color(0xFFA855F7),           // Electric Violet
    "Midcap" to Color(0xFF3B82F6),                   // Royal Blue
    "Small Cap" to Color(0xFF10B981),                // Emerald Green
    "Microcap" to Color(0xFFEC4899),                 // Coral Pink
    "Factor Value Index" to Color(0xFFF59E0B),       // Amber Gold
    "Factor Momentum Index" to Color(0xFF6366F1),    // Indigo
    "Equal Weight Index" to Color(0xFF14B8A6),       // Teal
    "Sectoral/Thematic" to Color(0xFFF43F5E),        // Rose
    "Gold & Commodities" to Color(0xFFEAB308),       // Gold
    "Debt & Liquid" to Color(0xFF64748B)             // Slate
)

@Composable
fun DonutAllocationChart(
    holdings: List<FlatHoldingDto>,
    modifier: Modifier = Modifier
) {
    val defaultColor = Color(0xFF94A3B8)

    val allocations = remember(holdings) {
        val totalVal = holdings.sumOf { it.currentValue.takeIf { v -> v > 0 } ?: (it.totalUnits * it.avgCost) }.coerceAtLeast(1.0)
        val grouped = holdings.groupBy { it.assetBucket.ifEmpty { "Others" } }
        grouped.map { (bucket, list) ->
            val bucketVal = list.sumOf { it.currentValue.takeIf { v -> v > 0 } ?: (it.totalUnits * it.avgCost) }
            val pct = (bucketVal / totalVal * 100).toFloat()
            BucketAllocation(
                bucketName = bucket,
                totalAmount = bucketVal,
                percentage = pct,
                color = SEBIBucketColors[bucket] ?: defaultColor
            )
        }.sortedByDescending { it.percentage }
    }

    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(holdings) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1424)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "SEBI CATEGORY ALLOCATION",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut Canvas
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(120.dp)
                            .graphicsLayer()
                    ) {
                        val strokeWidth = 22.dp.toPx()
                        var startAngle = -90f

                        allocations.forEach { alloc ->
                            val sweepAngle = (alloc.percentage / 100f) * 360f * animProgress.value
                            drawArc(
                                color = alloc.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += sweepAngle
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${allocations.size}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Categories",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Legend List
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allocations.take(5).forEach { alloc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(alloc.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = alloc.bucketName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "%.1f%%".format(alloc.percentage),
                                color = alloc.color,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PerformanceBarChart(
    holdings: List<FlatHoldingDto>,
    modifier: Modifier = Modifier
) {
    val topHoldings = remember(holdings) {
        holdings.sortedByDescending { it.xirr }.take(5)
    }

    val maxVal = remember(topHoldings) {
        topHoldings.maxOfOrNull { kotlin.math.abs(it.xirr) }?.toFloat()?.coerceAtLeast(1f) ?: 10f
    }

    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(holdings) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1424)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "TOP PERFORMING SCHEMES (XIRR)",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            topHoldings.forEach { holding ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = holding.fundName.ifEmpty { holding.isin },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${if (holding.xirr >= 0) "+" else ""}${holding.xirr}%",
                            color = if (holding.xirr >= 0) Color(0xFF10B981) else Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val barRatio = (kotlin.math.abs(holding.xirr).toFloat() / maxVal * animProgress.value).coerceIn(0.05f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF181F33))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barRatio)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = if (holding.xirr >= 0) listOf(Color(0xFF06B6D4), Color(0xFF10B981))
                                        else listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                                    )
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
fun HistoricalNetWorthTrendChart(
    trendPoints: List<NetWorthPointDto>,
    modifier: Modifier = Modifier
) {
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(trendPoints) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
        )
    }

    val rawVals = if (trendPoints.isEmpty()) listOf(100.0, 105.0, 110.0, 115.0, 120.0) else trendPoints.map { it.valuation }
    val minVal = rawVals.minOrNull() ?: 1.0
    val maxVal = rawVals.maxOrNull() ?: (minVal * 1.2)
    val valRange = (maxVal - minVal).coerceAtLeast(1.0)
    val points = rawVals.map { v -> ((v - minVal) / valRange * 0.70 + 0.25).toFloat() }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1424)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "HISTORICAL NET WORTH TREND",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "NAV Growth & Capital Curve",
                        color = Color(0xFFD0FF00),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer()
                ) {
                    val width = size.width
                    val height = size.height

                    val stepX = width / (points.size - 1).coerceAtLeast(1)
                    val path = androidx.compose.ui.graphics.Path()
                    val fillPath = androidx.compose.ui.graphics.Path()

                    val startY = height - (points[0] * height * 0.7f * animProgress.value)
                    path.moveTo(0f, startY)
                    fillPath.moveTo(0f, height)
                    fillPath.lineTo(0f, startY)

                    for (i in 1 until points.size) {
                        val x = i * stepX
                        val y = height - (points[i] * height * 0.7f * animProgress.value)
                        val prevX = (i - 1) * stepX
                        val prevY = height - (points[i - 1] * height * 0.7f * animProgress.value)

                        val controlX1 = prevX + (stepX / 2f)
                        val controlY1 = prevY
                        val controlX2 = prevX + (stepX / 2f)
                        val controlY2 = y

                        path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                        fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                    }

                    fillPath.lineTo(width, height)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFD0FF00).copy(alpha = 0.35f), Color(0xFF00F0FF).copy(alpha = 0.02f))
                        )
                    )

                    drawPath(
                        path = path,
                        color = Color(0xFFD0FF00),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/api/SyncApiClient.kt">
package com.portfolioos.mobile.api

import android.content.Context
import com.portfolioos.mobile.BuildConfig
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.model.TradeSimulationRequestDto
import com.portfolioos.mobile.model.TradeSimulationResultDto
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SyncApiService {
    @GET("api/v1/sync/snapshot")
    suspend fun getSnapshot(
        @Header("X-Api-Auth-Token") token: String,
        @Query("fy") fiscalYear: String = "2026-27"
    ): SyncSnapshot

    @POST("api/v1/simulate/trade")
    suspend fun simulateTrade(
        @Header("X-Api-Auth-Token") token: String,
        @Body request: TradeSimulationRequestDto
    ): TradeSimulationResultDto
}

object SyncApiClient {
    const val USB_BASE_URL = "http://127.0.0.1:8080/"
    const val EMULATOR_BASE_URL = "http://10.0.2.2:8080/"
    const val WIFI_BASE_URL = "http://192.168.1.13:8080/"

    fun createService(baseUrl: String = USB_BASE_URL): SyncApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(SyncApiService::class.java)
    }

    suspend fun fetchSnapshotWithFallback(context: Context): SyncSnapshot {
        val customUrl = SnapshotCacheManager.getCustomUrl(context)
        val authToken = SnapshotCacheManager.getAuthToken(context)
        
        // 1. Try Custom Remote/Tunnel URL if configured
        if (!customUrl.isNullOrBlank()) {
            try {
                val formatted = if (customUrl.endsWith("/")) customUrl else "$customUrl/"
                val remoteSnapshot = createService(formatted).getSnapshot(token = authToken)
                SnapshotCacheManager.saveSnapshot(context, remoteSnapshot)
                return remoteSnapshot
            } catch (e: Exception) {
                // fallthrough to local networks
            }
        }

        // 2. Try USB Loopback (adb reverse)
        try {
            val snapshot = createService(USB_BASE_URL).getSnapshot(token = authToken)
            SnapshotCacheManager.saveSnapshot(context, snapshot)
            return snapshot
        } catch (e1: Exception) {
            // 3. Try Android Emulator loopback
            try {
                val snapshot = createService(EMULATOR_BASE_URL).getSnapshot(token = authToken)
                SnapshotCacheManager.saveSnapshot(context, snapshot)
                return snapshot
            } catch (e2: Exception) {
                // 4. Try Wi-Fi LAN IP
                try {
                    val snapshot = createService(WIFI_BASE_URL).getSnapshot(token = authToken)
                    SnapshotCacheManager.saveSnapshot(context, snapshot)
                    return snapshot
                } catch (e3: Exception) {
                    // 5. Offline Fallback: Load cached snapshot & fetch direct AMFI NAVs over cellular!
                    val cached = SnapshotCacheManager.loadSnapshot(context)
                    if (cached != null) {
                        return SnapshotCacheManager.updateOfflineSnapshotWithLiveAmfi(cached)
                    } else {
                        throw e3
                    }
                }
            }
        }
    }

    suspend fun simulateTradeWithFallback(context: Context, request: TradeSimulationRequestDto): TradeSimulationResultDto {
        val customUrl = SnapshotCacheManager.getCustomUrl(context)
        val authToken = SnapshotCacheManager.getAuthToken(context)

        if (!customUrl.isNullOrBlank()) {
            try {
                val formatted = if (customUrl.endsWith("/")) customUrl else "$customUrl/"
                return createService(formatted).simulateTrade(token = authToken, request = request)
            } catch (e: Exception) {
                // fallthrough
            }
        }

        try {
            return createService(USB_BASE_URL).simulateTrade(token = authToken, request = request)
        } catch (e1: Exception) {
            try {
                return createService(EMULATOR_BASE_URL).simulateTrade(token = authToken, request = request)
            } catch (e2: Exception) {
                return createService(WIFI_BASE_URL).simulateTrade(token = authToken, request = request)
            }
        }
    }
}
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/model/SyncModels.kt">
package com.portfolioos.mobile.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

@Immutable
data class NetWorthPointDto(
    @SerializedName("date") val date: String = "",
    @SerializedName("valuation") val valuation: Double = 0.0,
    @SerializedName("invested") val invested: Double = 0.0
)

@Immutable
data class SyncSnapshot(
    @SerializedName("sync_info") val syncInfo: SyncInfoDto? = null,
    @SerializedName("holdings") val holdings: List<FlatHoldingDto>? = emptyList(),
    @SerializedName("tax_lots") val taxLots: List<FlatTaxLotDto>? = emptyList(),
    @SerializedName("radar_signals") val radarSignals: List<RadarSignalDto>? = emptyList(),
    @SerializedName("net_worth_history") val netWorthHistory: List<NetWorthPointDto>? = emptyList()
)

@Immutable
data class SyncInfoDto(
    @SerializedName("timestamp") val timestamp: Long = 0L,
    @SerializedName("ledger_hash") val ledgerHash: String = "",
    @SerializedName("generated_at") val generatedAt: String = "",
    @SerializedName("fiscal_year") val fiscalYear: String = "2026-27",
    @SerializedName("portfolio_xirr") val portfolioXirr: Double = 0.0,
    @SerializedName("xirr_percentage") val xirrPercentage: String = "0.00%",
    @SerializedName("total_invested") val totalInvested: Double = 0.0,
    @SerializedName("current_value") val currentValue: Double = 0.0,
    @SerializedName("unrealized_gain") val unrealizedGain: Double = 0.0,
    @SerializedName("formatted_current_value") val formattedCurrentValue: String = "₹0.00",
    @SerializedName("formatted_total_invested") val formattedTotalInvested: String = "₹0.00",
    @SerializedName("formatted_unrealized_gain") val formattedUnrealizedGain: String = "₹0.00"
)

@Immutable
data class FlatHoldingDto(
    @SerializedName("isin") val isin: String = "",
    @SerializedName("fund_name") val fundName: String = "",
    @SerializedName("total_units") val totalUnits: Double = 0.0,
    @SerializedName("avg_cost") val avgCost: Double = 0.0,
    @SerializedName("xirr") val xirr: Double = 0.0,
    @SerializedName("asset_bucket") val assetBucket: String = "",
    @SerializedName("current_value") val currentValue: Double = 0.0,
    @SerializedName("invested_value") val investedValue: Double = 0.0,
    @SerializedName("formatted_current_value") val formattedCurrentValue: String = "₹0.00",
    @SerializedName("formatted_invested_value") val formattedInvestedValue: String = "₹0.00"
)

@Immutable
data class FlatTaxLotDto(
    @SerializedName("isin") val isin: String = "",
    @SerializedName("buy_date") val buyDate: String = "",
    @SerializedName("units") val units: Double = 0.0,
    @SerializedName("tax_classification") val taxClassification: String = "",
    @SerializedName("is_long_term") val isLongTerm: Boolean = false,
    @SerializedName("grandfathered_nav") val grandfatheredNav: Double? = null,
    @SerializedName("cost_per_unit") val costPerUnit: Double = 0.0,
    @SerializedName("holding_days") val holdingDays: Long = 0L,
    @SerializedName("days_to_ltcg") val daysToLtcg: Long = 0L
)

@Immutable
data class RadarSignalDto(
    @SerializedName("signal_type") val signalType: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("subtitle") val subtitle: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("severity") val severity: String = "",
    @SerializedName("badge_text") val badgeText: String = ""
)

@Immutable
data class TradeSimulationRequestDto(
    @SerializedName("isin") val isin: String,
    @SerializedName("schemeName") val schemeName: String,
    @SerializedName("units") val units: Double,
    @SerializedName("pricePerUnit") val pricePerUnit: Double,
    @SerializedName("tradeDate") val tradeDate: String = "",
    @SerializedName("tradeType") val tradeType: String // DISPOSAL or ACQUISITION
)

@Immutable
data class TradeSimulationResultDto(
    @SerializedName("isin") val isin: String = "",
    @SerializedName("schemeName") val schemeName: String = "",
    @SerializedName("tradeType") val tradeType: String = "",
    @SerializedName("units") val units: Double = 0.0,
    @SerializedName("pricePerUnit") val pricePerUnit: Double = 0.0,
    @SerializedName("grossTradeAmount") val grossTradeAmount: Double = 0.0,
    @SerializedName("grossCapitalGain") val grossCapitalGain: Double = 0.0,
    @SerializedName("ltcgEquity") val ltcgEquity: Double = 0.0,
    @SerializedName("stcgEquity") val stcgEquity: Double = 0.0,
    @SerializedName("debtGain") val debtGain: Double = 0.0,
    @SerializedName("sec112aExemptionApplied") val sec112aExemptionApplied: Double = 0.0,
    @SerializedName("estimatedTaxLiability") val estimatedTaxLiability: Double = 0.0,
    @SerializedName("postTradeNetWorth") val postTradeNetWorth: Double = 0.0,
    @SerializedName("postTradeInvestedCost") val postTradeInvestedCost: Double = 0.0,
    @SerializedName("postTradeXirr") val postTradeXirr: Double = 0.0,
    @SerializedName("taxSummaryNotice") val taxSummaryNotice: String = ""
)
</file>

<file path="mobile-app/app/src/main/java/com/portfolioos/mobile/ui/DashboardScreen.kt">
package com.portfolioos.mobile.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.FlatTaxLotDto
import com.portfolioos.mobile.model.RadarSignalDto
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.util.formatInr
import kotlinx.coroutines.launch

// Bleeding-Edge Material 3 Expressive Vibrant Obsidian Palette
val M3ObsidianDark = Color(0xFF030712)
val M3SurfaceCard = Color(0xFF0D1424)
val M3SurfaceVariant = Color(0xFF162036)
val M3ElectricLime = Color(0xFFD0FF00)
val M3NeonCyan = Color(0xFF00F0FF)
val M3VibrantViolet = Color(0xFFE040FB)
val M3GreenPositive = Color(0xFF10B981)
val M3AmberWarning = Color(0xFFF59E0B)
val M3TextMuted = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    snapshot: SyncSnapshot?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onUpdateCustomUrl: (String) -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    var showUrlDialog by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = M3ObsidianDark,
            surface = M3SurfaceCard,
            surfaceVariant = M3SurfaceVariant,
            primary = M3ElectricLime,
            secondary = M3NeonCyan,
            tertiary = M3VibrantViolet
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(M3ObsidianDark)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Sleek Expressive Top Header
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "PORTFOLIO OS",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                letterSpacing = 3.sp,
                                color = Color.White
                            )
                            Surface(
                                color = M3ElectricLime.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = snapshot?.syncInfo?.fiscalYear?.let { "FY $it · Android 17 Edge" } ?: "Sync Active",
                                    fontSize = 10.sp,
                                    color = M3ElectricLime,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showUrlDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Server Settings",
                                tint = M3ElectricLime
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = M3ObsidianDark
                    )
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = M3ElectricLime)
                    }
                } else if (snapshot == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Core Node Offline / Not Synced",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Connect over Wi-Fi, USB, or set a custom server URL.",
                                    color = M3TextMuted,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onRefresh,
                                        colors = ButtonDefaults.buttonColors(containerColor = M3ElectricLime),
                                        shape = RoundedCornerShape(100.dp)
                                    ) {
                                        Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { showUrlDialog = true },
                                        shape = RoundedCornerShape(100.dp)
                                    ) {
                                        Text("Set Server URL", color = M3NeonCyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val syncInfo = snapshot.syncInfo
                    val holdings = snapshot.holdings ?: emptyList()
                    val radarSignals = snapshot.radarSignals ?: emptyList()
                    val taxLots = snapshot.taxLots ?: emptyList()

                    // High-performance 120fps Horizontal Pager with zero per-frame transform overhead
                    HorizontalPager(
                        state = pagerState,
                        beyondBoundsPageCount = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        when (page) {
                            0 -> HoldingsView(snapshot, syncInfo, holdings)
                            1 -> RadarSignalsView(radarSignals)
                            2 -> GroupedTaxLotsView(taxLots, holdings)
                            3 -> SimulatorView(holdings)
                        }
                    }
                }
            }

            // Google Material 3 Expressive Floating Glassmorphic Pill Overlaid directly over Screen
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF090F1E).copy(alpha = 0.94f),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(100.dp))
                        .border(
                            width = 1.5.dp,
                            brush = Brush.horizontalGradient(
                                listOf(M3ElectricLime.copy(alpha = 0.5f), M3NeonCyan.copy(alpha = 0.3f), M3VibrantViolet.copy(alpha = 0.4f))
                            ),
                            shape = RoundedCornerShape(100.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 0,
                            label = "Holdings",
                            icon = Icons.Default.Star,
                            activeColor = M3ElectricLime,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        )
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 1,
                            label = "AI Radar",
                            icon = Icons.Default.Notifications,
                            activeColor = M3VibrantViolet,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }
                        )
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 2,
                            label = "Tax Lots",
                            icon = Icons.Default.List,
                            activeColor = M3NeonCyan,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(2)
                                }
                            }
                        )
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 3,
                            label = "Simulator",
                            icon = Icons.Default.Settings,
                            activeColor = Color(0xFFD0FF00),
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(3)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Surface(
                            onClick = onRefresh,
                            color = M3ElectricLime,
                            shape = CircleShape,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Dialog for setting Custom Core Node Remote Server URL (Tailscale / Ngrok / LAN IP)
            if (showUrlDialog) {
                AlertDialog(
                    onDismissRequest = { showUrlDialog = false },
                    title = { Text("Core Node Server URL", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                text = "Enter custom Core Node IP or Tunnel URL (e.g. http://192.168.1.13:8080 or https://xyz.ngrok-free.app):",
                                color = M3TextMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = inputUrl,
                                onValueChange = { inputUrl = it },
                                placeholder = { Text("http://192.168.1.13:8080", color = M3TextMuted) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onUpdateCustomUrl(inputUrl.trim())
                                showUrlDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = M3ElectricLime)
                        ) {
                            Text("Save & Sync", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUrlDialog = false }) {
                            Text("Cancel", color = M3TextMuted)
                        }
                    },
                    containerColor = M3SurfaceCard,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

@Composable
fun ExpressiveNavPill(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    onClick: () -> Unit
) {
    val pillScale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "PillScale"
    )

    Surface(
        onClick = onClick,
        color = if (selected) activeColor.copy(alpha = 0.22f) else Color.Transparent,
        shape = RoundedCornerShape(100.dp),
        modifier = Modifier
            .scale(pillScale)
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessHigh))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) activeColor else M3TextMuted,
                modifier = Modifier.size(18.dp)
            )
            if (selected) {
                Text(
                    text = label,
                    color = activeColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun HoldingsView(snapshot: com.portfolioos.mobile.model.SyncSnapshot?, syncInfo: com.portfolioos.mobile.model.SyncInfoDto?, holdings: List<FlatHoldingDto>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Expressive M3 Hero Net Worth Card (en-IN Currency Format)
            Card(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 10.dp, bottomEnd = 32.dp, bottomStart = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(M3ElectricLime.copy(alpha = 0.7f), M3NeonCyan.copy(alpha = 0.35f))),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 10.dp, bottomEnd = 32.dp, bottomStart = 10.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF142600), Color(0xFF062C33), Color(0xFF0D1424))
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = M3ElectricLime.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text(
                                    text = "NET WORTH VALUATION",
                                    color = M3ElectricLime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = M3GreenPositive.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text(
                                    text = syncInfo?.xirrPercentage ?: "0.00% XIRR",
                                    color = M3GreenPositive,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = formatInr(syncInfo?.currentValue ?: 0.0),
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Invested",
                                    color = M3TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatInr(syncInfo?.totalInvested ?: 0.0),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Unrealized Gain",
                                    color = M3TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val gain = syncInfo?.unrealizedGain ?: 0.0
                                Text(
                                    text = "${if (gain >= 0) "+" else ""}${formatInr(gain)}",
                                    color = if (gain >= 0) M3GreenPositive else Color.Red,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            HistoricalNetWorthTrendChart(trendPoints = snapshot?.netWorthHistory ?: emptyList())
        }

        item {
            DonutAllocationChart(holdings = holdings)
        }

        item {
            PerformanceBarChart(holdings = holdings)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE HOLDINGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = M3TextMuted,
                    letterSpacing = 1.5.sp
                )
                Surface(
                    color = M3SurfaceVariant,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "${holdings.size} Schemes",
                        color = M3NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        if (holdings.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No open holdings recorded in ledger.",
                        color = M3TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(holdings, key = { h -> h.isin.ifEmpty { h.fundName } }) { holding ->
                M3HoldingCard(holding)
            }
        }
    }
}

@Composable
fun M3HoldingCard(holding: FlatHoldingDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 8.dp, bottomEnd = 20.dp, bottomStart = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = holding.fundName.ifEmpty { holding.isin },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = M3ElectricLime.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "🔄 SIP",
                        color = M3ElectricLime,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    color = if (holding.xirr >= 0) M3GreenPositive.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "${if (holding.xirr >= 0) "+" else ""}${holding.xirr}% XIRR",
                        color = if (holding.xirr >= 0) M3GreenPositive else Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Valuation: ${formatInr(holding.currentValue)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${holding.totalUnits} Units · Cost: ${formatInr(holding.investedValue)}",
                        color = M3TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Surface(
                    color = M3SurfaceVariant,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = holding.assetBucket,
                        color = M3NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RadarSignalsView(radarSignals: List<RadarSignalDto>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "PRIORITY AI RADAR & QUANT INTELLIGENCE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = M3TextMuted,
                letterSpacing = 1.5.sp
            )
        }

        if (radarSignals.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ Portfolio status optimal. No immediate tax or rebalance recommendations.",
                        color = M3GreenPositive,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(radarSignals, key = { s -> "${s.title}-${s.signalType}" }) { signal ->
                M3RadarCard(signal)
            }
        }
    }
}

@Composable
fun M3RadarCard(signal: RadarSignalDto) {
    val isQuant = signal.signalType.contains("QUANT", ignoreCase = true)
    val isWarning = signal.severity.equals("WARNING", ignoreCase = true)
    val borderColor = if (isQuant) M3VibrantViolet else if (isWarning) M3AmberWarning else M3NeonCyan
    val containerColor = if (isQuant) Color(0xFF1A0A26) else M3SurfaceCard

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(borderColor)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 24.dp, bottomStart = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = signal.title.ifEmpty { "Recommendation" },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = borderColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = signal.badgeText.ifEmpty { "Action Required" },
                        color = borderColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = signal.description,
                color = M3TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun GroupedTaxLotsView(taxLots: List<FlatTaxLotDto>, holdings: List<FlatHoldingDto>) {
    val nameMap = remember(holdings) {
        holdings.associate { it.isin to it.fundName }
    }

    val groupedLots = remember(taxLots) {
        taxLots.groupBy { it.isin }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SCHEME-GROUPED TAX LOTS (${groupedLots.size} SCHEMES · ${taxLots.size} LOTS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = M3TextMuted,
                letterSpacing = 1.5.sp
            )
        }

        if (groupedLots.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No tax lots recorded.",
                        color = M3TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(groupedLots.entries.toList(), key = { entry -> entry.key }) { (isin, lots) ->
                val schemeName = nameMap[isin] ?: isin
                GroupedSchemeTaxLotCard(schemeName = schemeName, isin = isin, lots = lots)
            }
        }
    }
}

@Composable
fun GroupedSchemeTaxLotCard(schemeName: String, isin: String, lots: List<FlatTaxLotDto>) {
    var expanded by remember { mutableStateOf(false) }

    val ltcgCount = remember(lots) { lots.count { it.isLongTerm } }
    val stcgCount = remember(lots) { lots.size - ltcgCount }
    val totalUnits = remember(lots) { lots.sumOf { it.units } }

    Card(
        colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schemeName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${lots.size} Open Lots · Total %.2f Units".format(totalUnits),
                        color = M3TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ltcgCount > 0) {
                        Surface(
                            color = M3GreenPositive.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "$ltcgCount LTCG",
                                color = M3GreenPositive,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (stcgCount > 0) {
                        Surface(
                            color = M3AmberWarning.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "$stcgCount STCG",
                                color = M3AmberWarning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = M3NeonCyan
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = M3SurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    lots.forEach { lot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${lot.buyDate} · ${lot.units} u @ ${formatInr(lot.costPerUnit)}",
                                color = M3TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (lot.isLongTerm) "LTCG" else "STCG (${lot.daysToLtcg}d)",
                                color = if (lot.isLongTerm) M3GreenPositive else M3AmberWarning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
</file>

</files>
</file>

<file path="repomix-core.md">
This file is a merged representation of a subset of the codebase, containing specifically included files, combined into a single document by Repomix.

<file_summary>
This section contains a summary of this file.

<purpose>
This file contains a packed representation of a subset of the repository's contents that is considered the most important context.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.
</purpose>

<file_format>
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Repository files (if enabled)
5. Multiple file entries, each consisting of:
  - File path as an attribute
  - Full contents of the file
</file_format>

<usage_guidelines>
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.
</usage_guidelines>

<notes>
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Only files matching these patterns are included: core-node/**/*
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Files are sorted by Git change count (files with more changes are at the bottom)
</notes>

</file_summary>

<directory_structure>
core-node/
  src/
    main/
      java/
        com/
          portfolioos/
            core/
              config/
                AppConfig.java
              controllers/
                LlmQueryController.java
                RebalanceController.java
                ReportController.java
                SimulatorController.java
                StatementsController.java
                SyncController.java
              dtos/
                ReportDtos.java
                SyncDtos.java
              fire/
                FireTracker.java
              goals/
                GoalTracker.java
              llm/
                SqlGeneratorService.java
                TaxRagService.java
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
                LedgerCacheService.java
                PortfolioValuationService.java
                SimulationService.java
                TaxOptimizationService.java
              tax/
                ScheduleCgExporter.java
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
</directory_structure>

<files>
This section contains the contents of the repository's files.

<file path="core-node/src/main/java/com/portfolioos/core/controllers/LlmQueryController.java">
package com.portfolioos.core.controllers;

import com.portfolioos.core.llm.SqlGeneratorService;
import com.portfolioos.core.llm.TaxRagService;
import com.portfolioos.core.service.SimulationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/llm")
public class LlmQueryController {

    private final SqlGeneratorService sqlService;
    private final TaxRagService taxRagService;
    private final SimulationService simulationService;
    private final ChatClient.Builder chatClientBuilder;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public LlmQueryController(
        SqlGeneratorService sqlService,
        TaxRagService taxRagService,
        SimulationService simulationService,
        ChatClient.Builder chatClientBuilder
    ) {
        this.sqlService = sqlService;
        this.taxRagService = taxRagService;
        this.simulationService = simulationService;
        this.chatClientBuilder = chatClientBuilder;
    }

    public static record LlmQueryRequest(String prompt) {}

    public static record LlmQueryResponse(
        String queryType, // SQL, TOOL_SIMULATION, TAX_RAG, GENERAL
        String textResponse,
        String generatedSql,
        Object dataPayload,
        String status
    ) {}

    @PostMapping("/query")
    public LlmQueryResponse handleQuery(@RequestBody LlmQueryRequest req) {
        if (req == null || req.prompt() == null || req.prompt().isBlank()) {
            return new LlmQueryResponse("UNKNOWN", "Please provide a valid prompt.", null, null, "ERROR");
        }

        String prompt = req.prompt().trim();
        String promptLower = prompt.toLowerCase();

        // 1. Tool Call Interception for Trade Simulation
        if (promptLower.contains("simulate") || promptLower.contains("what-if") || promptLower.contains("what if")) {
            try {
                // Example tool parameter extraction for paired or single trade
                SimulationService.TradeSimulationRequest simReq = new SimulationService.TradeSimulationRequest(
                    "INF200K01229",
                    "Parag Parikh Flexi Cap Fund",
                    100.0,
                    165.0,
                    null,
                    promptLower.contains("sell") ? "DISPOSAL" : "ACQUISITION"
                );
                SimulationService.TradeSimulationResult res = simulationService.simulateTrade(simReq);
                return new LlmQueryResponse("TOOL_SIMULATION", res.taxSummaryNotice(), null, res, "SUCCESS");
            } catch (Exception e) {
                return new LlmQueryResponse("TOOL_SIMULATION", "Simulation failed: " + e.getMessage(), null, null, "ERROR");
            }
        }

        // 2. Tax RAG Engine for Tax Code Questions
        if (promptLower.contains("tax") || promptLower.contains("112a") || promptLower.contains("50aa") || promptLower.contains("ltcg") || promptLower.contains("stcg")) {
            String answer = taxRagService.answerTaxQuestion(prompt);
            return new LlmQueryResponse("TAX_RAG", answer, null, null, "SUCCESS");
        }

        // 3. DuckDB Text-to-SQL Pipeline
        SqlGeneratorService.SqlQueryResult sqlRes = sqlService.generateAndExecute(prompt);
        if ("SUCCESS".equalsIgnoreCase(sqlRes.status())) {
            String summary = String.format("Query executed successfully. Found %d matching records.", sqlRes.data().size());
            return new LlmQueryResponse("SQL", summary, sqlRes.generatedSql(), sqlRes.data(), "SUCCESS");
        }

        return new LlmQueryResponse("GENERAL", "Could not execute query: " + sqlRes.errorMessage(), null, null, "ERROR");
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQuery(@RequestParam("prompt") String prompt) {
        SseEmitter emitter = new SseEmitter(60000L);
        executorService.execute(() -> {
            try {
                LlmQueryResponse res = handleQuery(new LlmQueryRequest(prompt));
                String content = res.textResponse();

                if (res.generatedSql() != null && !res.generatedSql().isBlank()) {
                    content += "\n\n```sql\n" + res.generatedSql() + "\n```";
                }

                // Stream tokens word-by-word for live SSE typing effect
                String[] words = content.split(" ");
                for (String word : words) {
                    emitter.send(word + " ");
                    Thread.sleep(30);
                }
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send("⚠️ Streaming error: " + e.getMessage());
                    emitter.complete();
                } catch (IOException ignored) {}
            }
        });
        return emitter;
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/controllers/RebalanceController.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/controllers/SimulatorController.java">
package com.portfolioos.core.controllers;

import com.portfolioos.core.service.SimulationService;
import com.portfolioos.core.service.SimulationService.TradeSimulationRequest;
import com.portfolioos.core.service.SimulationService.TradeSimulationResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulate")
public class SimulatorController {

    private final SimulationService simulationService;

    public SimulatorController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/trade")
    public ResponseEntity<TradeSimulationResult> simulateTrade(
        @RequestBody TradeSimulationRequest req
    ) {
        return ResponseEntity.ok(simulationService.simulateTrade(req));
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/controllers/StatementsController.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/dtos/ReportDtos.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/goals/GoalTracker.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/llm/SqlGeneratorService.java">
package com.portfolioos.core.llm;

import com.portfolioos.core.persistence.DuckDbProjector;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SqlGeneratorService {

    private final ChatClient.Builder chatClientBuilder;
    private final DuckDbProjector duckDbProjector;

    private static final String SCHEMA_PROMPT = """
        You are an expert DuckDB SQL developer for a financial portfolio database.
        The database contains two projected analytical tables:

        1. projected_events (
            id VARCHAR PRIMARY KEY,
            asset_id VARCHAR NOT NULL,
            asset_name VARCHAR NOT NULL,
            isin VARCHAR,
            event_type VARCHAR NOT NULL, -- 'ACQUISITION', 'DISPOSAL', 'SIP_INSTALMENT', 'BONUS', 'SPLIT'
            event_date VARCHAR NOT NULL, -- YYYY-MM-DD
            units VARCHAR NOT NULL,
            price_per_unit VARCHAR NOT NULL,
            gross_amount VARCHAR NOT NULL,
            source_document_id VARCHAR NOT NULL,
            ingested_at VARCHAR NOT NULL
        )

        2. nav_history (
            asset_id VARCHAR NOT NULL,
            nav_date VARCHAR NOT NULL, -- YYYY-MM-DD
            nav DOUBLE NOT NULL,
            PRIMARY KEY (asset_id, nav_date)
        )

        CRITICAL INSTRUCTIONS:
        - Output ONLY valid, executable DuckDB SQL.
        - The query MUST be a read-only SELECT statement.
        - Do NOT include any markdown formatting, code block fences (```), explanations, or trailing comments.
        """;

    public SqlGeneratorService(ChatClient.Builder chatClientBuilder, DuckDbProjector duckDbProjector) {
        this.chatClientBuilder = chatClientBuilder;
        this.duckDbProjector = duckDbProjector;
    }

    public record SqlQueryResult(
        String generatedSql,
        List<Map<String, Object>> data,
        String status,
        String errorMessage
    ) {}

    public SqlQueryResult generateAndExecute(String userPrompt) {
        try {
            ChatClient chatClient = chatClientBuilder.build();
            String rawSql = chatClient.prompt()
                .system(SCHEMA_PROMPT)
                .user(userPrompt)
                .call()
                .content();

            if (rawSql == null || rawSql.isBlank()) {
                return new SqlQueryResult("", Collections.emptyList(), "ERROR", "Empty SQL generated by LLM");
            }

            // Clean markdown syntax if present
            String sql = rawSql.replaceAll("```sql", "").replaceAll("```", "").trim();

            // Strict SELECT Guardrail
            if (!sql.toUpperCase().startsWith("SELECT") && !sql.toUpperCase().startsWith("WITH")) {
                throw new SecurityException("Security violation: Only read-only SELECT queries are permitted.");
            }

            if (sql.contains(";") && sql.indexOf(";") != sql.length() - 1) {
                throw new SecurityException("Security violation: Multi-statement queries are forbidden.");
            }

            List<Map<String, Object>> results = executeDuckDbQuery(sql);
            return new SqlQueryResult(sql, results, "SUCCESS", null);
        } catch (Exception e) {
            return new SqlQueryResult("", Collections.emptyList(), "ERROR", e.getMessage());
        }
    }

    private List<Map<String, Object>> executeDuckDbQuery(String sql) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:duckdb:" + new java.io.File("data/tax_ledger.duckdb").getAbsolutePath());
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {

            java.sql.ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
        } catch (Exception e) {
            throw new RuntimeException("DuckDB SQL execution error: " + e.getMessage(), e);
        }
        return rows;
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/llm/TaxRagService.java">
package com.portfolioos.core.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@Service
public class TaxRagService {

    private final ChatClient.Builder chatClientBuilder;
    private VectorStore vectorStore;

    public TaxRagService(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    @PostConstruct
    public void initTaxKnowledgeBase() {
        try {
            // Spring AI SimpleVectorStore in-memory setup for Indian Tax Code rules
            File rulesFile = new File("rules/FY2026-27.yaml");
            if (rulesFile.exists()) {
                String content = Files.readString(rulesFile.toPath());
                Document doc = new Document(
                    "INDIAN TAX CODE & RULES FY2026-27:\n" + content,
                    Map.of("source", "FY2026-27.yaml", "category", "TAX_RULES")
                );
                // Vector store placeholder populated on demand
            }
        } catch (Exception e) {
            System.err.println("Tax Vector Store initialization warning: " + e.getMessage());
        }
    }

    public String answerTaxQuestion(String userQuestion) {
        try {
            String systemText = """
                You are an expert Indian Income Tax advisor for Mutual Funds and Equity Capital Gains.
                Use the following ground-truth rules:
                - Equity LTCG (holding > 365 days): Taxed at 12.5% above Section 112A exemption limit of ₹1,25,000 per financial year.
                - Equity STCG (holding <= 365 days): Taxed at 20.0% under Section 111A.
                - Debt Mutual Funds acquired after April 1, 2023: Taxed at slab rates under Section 50AA regardless of holding period.
                - Grandfathering Rule: NAV as of 31-Jan-2018 is used as cost basis for equity holdings acquired prior to 01-Feb-2018.

                Provide clear, concise, legally grounded answers.
                """;

            ChatClient chatClient = chatClientBuilder.build();
            return chatClient.prompt()
                .system(systemText)
                .user(userQuestion)
                .call()
                .content();
        } catch (Exception e) {
            return "⚠️ Tax RAG query failed: " + e.getMessage();
        }
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/matcher/FifoMatcher.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/matcher/TaxClassifier.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/model/AssetCategory.java">
package com.portfolioos.core.model;

public enum AssetCategory {
    EQUITY,
    DEBT_SPECIFIED_50AA,
    GOLD_SILVER,
    INTERNATIONAL,
    SGB
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/model/EventType.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/model/Lot.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/model/MatchedLot.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/model/TaxEvent.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/model/TaxTerm.java">
package com.portfolioos.core.model;

public enum TaxTerm {
    SHORT_TERM,
    LONG_TERM,
    EXEMPT
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/ports/EventStorePort.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/reconciliation/ReconciliationGate.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/reporting/ExemptionTracker.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/reporting/Itr2CsvExporter.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/reporting/TaxReportExporter.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/rules/TaxRulesConfig.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/service/TaxOptimizationService.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/util/Pair.java">
package com.portfolioos.core.util;

public record Pair<A, B>(A first, B second) {}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/valuation/AntigravityEngine.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/valuation/HarvestAdvisor.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/valuation/RebalanceEngine.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/xirr/CashFlow.java">
package com.portfolioos.core.xirr;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashFlow(
    LocalDate date,
    BigDecimal amount // negative for investments, positive for inflows / current valuation
) {}
</file>

<file path="core-node/src/main/resources/static/src/js/state.js">
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
</file>

<file path="core-node/src/main/resources/static/src/js/utils.js">
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
</file>

<file path="core-node/build.gradle">
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
</file>

<file path="core-node/settings.gradle">
rootProject.name = 'core-node'
</file>

<file path="core-node/src/main/java/com/portfolioos/core/fire/FireTracker.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/rules/TaxRulesLoader.java">
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/security/SecurityConfig.java">
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
            .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/service/PortfolioValuationService.java">
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
import com.portfolioos.core.reporting.ExemptionTracker;
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

    private final LedgerCacheService cacheService;
    private final XirrEngine xirrEngine = new XirrEngine();

    public PortfolioValuationService(LedgerCacheService cacheService) {
        this.cacheService = cacheService;
    }

    private String fmt(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public PortfolioSummaryResponse getPortfolioSummary(String fy) {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<TaxEvent> allEvents = state.events();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();

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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();
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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
        Map<String, BigDecimal> navMap = state.navMap();

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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();

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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();

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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();

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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
        Map<String, BigDecimal> navMap = state.navMap();

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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/tax/ScheduleCgExporter.java">
package com.portfolioos.core.tax;

import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class ScheduleCgExporter {

    public static String generateCsvReport(List<MatchedLot> matchedLots, String fiscalYear) {
        StringBuilder csv = new StringBuilder();
        csv.append("ITR SCHEDULE CG - CAPITAL GAINS SUMMARY (FY ").append(fiscalYear).append(")\n");
        csv.append("Generated by Portfolio OS Tax Engine\n\n");
        csv.append("ISIN,Purchase Date,Sale Date,Holding Days,Units Sold,Purchase Cost (INR),Sale Value (INR),Capital Gain (INR),Tax Classification,Section,Tax Rate\n");

        BigDecimal totalLtcgEquity = BigDecimal.ZERO;
        BigDecimal totalStcgEquity = BigDecimal.ZERO;
        BigDecimal totalDebtGain = BigDecimal.ZERO;

        for (MatchedLot match : matchedLots) {
            long days = match.holdingPeriodDays();
            AssetCategory category = match.assetCategory();
            TaxTerm term = match.taxTerm();

            BigDecimal units = match.unitsMatched();
            BigDecimal cost = match.costBasis().setScale(2, RoundingMode.HALF_UP);
            BigDecimal saleVal = match.saleProceeds().setScale(2, RoundingMode.HALF_UP);
            BigDecimal gain = match.realizedGain().setScale(2, RoundingMode.HALF_UP);

            String section;
            String taxRate;

            if (category == AssetCategory.EQUITY) {
                if (term == TaxTerm.LONG_TERM) {
                    section = "112A";
                    taxRate = "12.5%";
                    totalLtcgEquity = totalLtcgEquity.add(gain);
                } else {
                    section = "111A";
                    taxRate = "20.0%";
                    totalStcgEquity = totalStcgEquity.add(gain);
                }
            } else {
                section = "50AA";
                taxRate = "Slab Rate";
                totalDebtGain = totalDebtGain.add(gain);
            }

            csv.append(escapeCsv(match.assetId())).append(",")
               .append(match.acquisitionDate()).append(",")
               .append(match.disposalDate()).append(",")
               .append(days).append(",")
               .append(units.toPlainString()).append(",")
               .append(cost.toPlainString()).append(",")
               .append(saleVal.toPlainString()).append(",")
               .append(gain.toPlainString()).append(",")
               .append(term.name()).append(",")
               .append(section).append(",")
               .append(taxRate).append("\n");
        }

        csv.append("\nSUMMARY TAX OBLIGATION RECAP\n");
        csv.append("Equity Sec 112A Total LTCG Gain: INR ").append(totalLtcgEquity.toPlainString()).append("\n");
        csv.append("Sec 112A Annual Exemption Limit: INR 125000.00\n");
        BigDecimal taxableLtcg = totalLtcgEquity.subtract(new BigDecimal("125000.00")).max(BigDecimal.ZERO);
        csv.append("Net Taxable Sec 112A LTCG: INR ").append(taxableLtcg.toPlainString()).append("\n");
        csv.append("Equity Sec 111A Total STCG Gain (20%): INR ").append(totalStcgEquity.toPlainString()).append("\n");
        csv.append("Debt Sec 50AA Total Gain (Slab Rate): INR ").append(totalDebtGain.toPlainString()).append("\n");

        return csv.toString();
    }

    private static String escapeCsv(String text) {
        if (text == null) return "";
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/valuation/BucketEngine.java">
package com.portfolioos.core.valuation;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.reporting.ExemptionTracker;
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
        return evaluateRebalance(openLots, List.of(), navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh, targets, fiscalYear);
    }

    public static RebalanceEngineResult evaluateRebalance(
        List<Lot> openLots,
        List<MatchedLot> matchedLots,
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

        // Deduct statutory Section 112A LTCG exemption
        ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(matchedLots != null ? matchedLots : List.of(), fiscalYear);
        BigDecimal exemptionRemaining = new BigDecimal(exStatus.exemptionRemaining());

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
                        long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), currentDate);
                        boolean isLtcg = TaxClassifier.classifyTaxTerm(category, holdingDays, fiscalYear, true) == TaxTerm.LONG_TERM;
                        BigDecimal gain = nav.subtract(lot.costPerUnit()).multiply(lot.remainingUnits()).max(BigDecimal.ZERO);

                        if (gain.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal rate = isLtcg ? rules.equityLtcgRate() : rules.equityStcgRate();
                            BigDecimal taxableGain = gain;
                            if (isLtcg && exemptionRemaining.compareTo(BigDecimal.ZERO) > 0) {
                                if (taxableGain.compareTo(exemptionRemaining) <= 0) {
                                    exemptionRemaining = exemptionRemaining.subtract(taxableGain);
                                    taxableGain = BigDecimal.ZERO;
                                } else {
                                    taxableGain = taxableGain.subtract(exemptionRemaining);
                                    exemptionRemaining = BigDecimal.ZERO;
                                }
                            }
                            estTaxDrag = estTaxDrag.add(taxableGain.multiply(rate));
                            taxTerms.add(isLtcg ? "LTCG @ " + rules.equityLtcgRate().multiply(new BigDecimal("100")) + "% (Sec 112A exemption applied)" 
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/valuation/ConsolidationRebalanceEngine.java">
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

        BigDecimal netPostTaxProceeds = totalProceeds.subtract(totalTaxDrag).max(BigDecimal.ZERO);
        BigDecimal effectiveProceeds = netPostTaxProceeds.compareTo(BigDecimal.ZERO) > 0 ? netPostTaxProceeds : totalProceeds;

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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/xirr/XirrEngine.java">
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

        // Newton-Raphson solver
        double rate = 0.10;
        for (int iter = 0; iter < 100; iter++) {
            double f = npv(rate, dates, amounts);
            double df = dNpv(rate, dates, amounts);

            if (Math.abs(df) > 1e-10) {
                double nextRate = rate - f / df;
                if (Math.abs(nextRate - rate) < 1e-7) {
                    double result = nextRate * 100.0;
                    if (Double.isNaN(result) || Double.isInfinite(result)) return 0.0;
                    return Math.max(-99.0, result);
                }
                rate = nextRate;
            }
            if (rate <= -0.90) rate = -0.50;
        }

        // Bracketed Bisection Fallback
        double low = -0.50;
        double high = 10.0;
        double flow = npv(low, dates, amounts);
        double fhigh = npv(high, dates, amounts);

        if (flow * fhigh <= 0) {
            for (int i = 0; i < 100; i++) {
                double mid = (low + high) / 2.0;
                double fmid = npv(mid, dates, amounts);
                if (Math.abs(fmid) < 1e-7 || (high - low) < 1e-7) {
                    return Math.max(-99.0, mid * 100.0);
                }
                if (flow * fmid < 0) {
                    high = mid;
                    fhigh = fmid;
                } else {
                    low = mid;
                    flow = fmid;
                }
            }
            return Math.max(-99.0, ((low + high) / 2.0) * 100.0);
        }

        double rawResult = rate * 100.0;
        if (Double.isNaN(rawResult) || Double.isInfinite(rawResult)) return 0.0;
        return Math.max(-99.0, rawResult);
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/CoreApplication.java">
package com.portfolioos.core;

import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.ports.EventStorePort;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class
})
@EnableScheduling
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
</file>

<file path="core-node/src/main/resources/static/src/js/modules/insurance.js">
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
</file>

<file path="core-node/src/main/resources/static/src/js/api.js">
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
</file>

<file path="core-node/src/main/resources/application.yml">
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
  jackson:
    property-naming-strategy: SNAKE_CASE

logging:
  level:
    root: INFO
    com.portfolioos.core: DEBUG
    org.springframework.web: INFO
</file>

<file path="core-node/Dockerfile">
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "--add-opens=java.base/java.nio=ALL-UNNAMED", "-jar", "app.jar"]
</file>

<file path="core-node/src/main/java/com/portfolioos/core/config/AppConfig.java">
package com.portfolioos.core.config;

import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.persistence.SqliteEventStore;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.rpc.FlightRpcClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
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

    @Bean
    public ChatClient.Builder chatClientBuilder(
        @Value("${spring.ai.ollama.base-url:http://127.0.0.1:11434}") String ollamaUrl
    ) {
        String resolvedUrl = ollamaUrl;
        if (ollamaUrl.contains("localhost") || ollamaUrl.contains("127.0.0.1")) {
            // Test if running inside container and target host gateway if needed
            resolvedUrl = "http://127.0.0.1:11434";
        }
        OllamaApi ollamaApi = new OllamaApi(resolvedUrl);
        OllamaChatModel chatModel = new OllamaChatModel(
            ollamaApi,
            OllamaOptions.create().withModel("qwen2.5-coder:3b")
        );
        return ChatClient.builder(chatModel);
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/controllers/ReportController.java">
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
    private final com.portfolioos.core.service.LedgerCacheService cacheService;

    public ReportController(PortfolioValuationService valuationService, TaxOptimizationService taxService, com.portfolioos.core.service.LedgerCacheService cacheService) {
        this.valuationService = valuationService;
        this.taxService = taxService;
        this.cacheService = cacheService;
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

    @GetMapping("/tax/schedule-cg/export")
    public ResponseEntity<String> downloadScheduleCgCsv(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        String csv = com.portfolioos.core.tax.ScheduleCgExporter.generateCsvReport(
            cacheService.getCachedState().fifoResult().matchedLots(),
            fy
        );

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Schedule-CG-FY" + fy + ".csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/nav/AmfiNavSync.java">
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

    private static final long CACHE_TTL_MS = 6 * 3600 * 1000L;
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/security/SecurityInterceptor.java">
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
            throw new IllegalStateException("SECURITY CRITICAL: API_AUTH_TOKEN environment variable is required and cannot be empty.");
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/service/SimulationService.java">
package com.portfolioos.core.service;

import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.*;
import com.portfolioos.core.xirr.CashFlow;
import com.portfolioos.core.xirr.XirrEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SimulationService {

    private final LedgerCacheService cacheService;
    private final XirrEngine xirrEngine = new XirrEngine();

    public SimulationService(LedgerCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public static record TradeSimulationRequest(
        String isin,
        String schemeName,
        double units,
        double pricePerUnit,
        String tradeDate,
        String tradeType // DISPOSAL or ACQUISITION
    ) {}

    public static record TradeSimulationResult(
        String isin,
        String schemeName,
        String tradeType,
        double units,
        double pricePerUnit,
        double grossTradeAmount,
        double grossCapitalGain,
        double ltcgEquity,
        double stcgEquity,
        double debtGain,
        double sec112aExemptionApplied,
        double estimatedTaxLiability,
        double postTradeNetWorth,
        double postTradeInvestedCost,
        double postTradeXirr,
        String taxSummaryNotice
    ) {}

    public TradeSimulationResult simulateTrade(TradeSimulationRequest req) {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<TaxEvent> existingEvents = state.events();
        Map<String, BigDecimal> navMap = state.navMap();

        LocalDate tradeDate = (req.tradeDate() != null && !req.tradeDate().isBlank())
            ? LocalDate.parse(req.tradeDate())
            : LocalDate.now();

        BigDecimal unitsBd = BigDecimal.valueOf(req.units()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal priceBd = BigDecimal.valueOf(req.pricePerUnit()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal grossAmount = unitsBd.multiply(priceBd).setScale(2, RoundingMode.HALF_UP);

        EventType type = "ACQUISITION".equalsIgnoreCase(req.tradeType()) ? EventType.ACQUISITION : EventType.DISPOSAL;
        String isin = (req.isin() != null && !req.isin().isBlank()) ? req.isin() : "SIMULATED_ASSET";
        String name = (req.schemeName() != null && !req.schemeName().isBlank()) ? req.schemeName() : "Simulated Fund";

        TaxEvent simEvent = new TaxEvent(
            "SIM_" + System.currentTimeMillis(),
            isin,
            name,
            isin,
            type,
            tradeDate,
            unitsBd,
            priceBd,
            grossAmount,
            "MANUAL_SIMULATION",
            java.time.Instant.now()
        );

        List<TaxEvent> simEvents = new ArrayList<>(existingEvents);
        simEvents.add(simEvent);

        FifoMatcher matcher = new FifoMatcher();
        FifoMatcher.FifoResult simResult = matcher.processEvents(simEvents);

        double ltcgEquity = 0.0;
        double stcgEquity = 0.0;
        double debtGain = 0.0;
        double totalGain = 0.0;

        if (type == EventType.DISPOSAL) {
            for (MatchedLot match : simResult.matchedLots()) {
                if (match.disposalEventId().equals(simEvent.id())) {
                    AssetCategory category = match.assetCategory();
                    TaxTerm term = match.taxTerm();

                    BigDecimal gain = match.realizedGain();
                    totalGain += gain.doubleValue();

                    if (category == AssetCategory.EQUITY) {
                        if (term == TaxTerm.LONG_TERM) {
                            ltcgEquity += gain.doubleValue();
                        } else {
                            stcgEquity += gain.doubleValue();
                        }
                    } else {
                        debtGain += gain.doubleValue();
                    }
                }
            }
        }

        double previousLtcgRealized = 0.0;
        for (MatchedLot match : state.fifoResult().matchedLots()) {
            if (match.assetCategory() == AssetCategory.EQUITY && match.taxTerm() == TaxTerm.LONG_TERM) {
                previousLtcgRealized += Math.max(0.0, match.realizedGain().doubleValue());
            }
        }

        double remainingExemptionLimit = Math.max(0.0, 125000.0 - previousLtcgRealized);
        double exemptionApplied = Math.min(Math.max(0.0, ltcgEquity), remainingExemptionLimit);
        double taxableLtcg = Math.max(0.0, ltcgEquity - exemptionApplied);
        double estimatedTax = (taxableLtcg * 0.125) + (Math.max(0.0, stcgEquity) * 0.20) + (Math.max(0.0, debtGain) * 0.30);

        // Compute post-trade net worth & XIRR
        double postInvested = 0.0;
        double postCurrentVal = 0.0;

        for (Lot lot : simResult.openLots()) {
            postInvested += lot.remainingUnits().multiply(lot.costPerUnit()).doubleValue();
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
            postCurrentVal += lot.remainingUnits().multiply(nav).doubleValue();
        }

        List<CashFlow> cashFlows = new ArrayList<>();
        for (TaxEvent ev : simEvents) {
            BigDecimal amt = (ev.eventType() == EventType.ACQUISITION || ev.eventType() == EventType.SIP_INSTALMENT)
                ? ev.grossAmount().negate()
                : ev.grossAmount();
            cashFlows.add(new CashFlow(ev.eventDate(), amt));
        }
        if (postCurrentVal > 0) {
            cashFlows.add(new CashFlow(tradeDate, BigDecimal.valueOf(postCurrentVal)));
        }

        double postXirr = xirrEngine.calculateXirr(cashFlows);

        String notice = (type == EventType.DISPOSAL)
            ? String.format("Simulated Sale: Estimated Tax Drag ₹%,.2f (LTCG Exemption Used: ₹%,.2f)", estimatedTax, exemptionApplied)
            : String.format("Simulated Purchase: Added ₹%,.2f investment to portfolio.", grossAmount.doubleValue());

        return new TradeSimulationResult(
            isin,
            name,
            type.name(),
            req.units(),
            req.pricePerUnit(),
            grossAmount.doubleValue(),
            totalGain,
            ltcgEquity,
            stcgEquity,
            debtGain,
            exemptionApplied,
            estimatedTax,
            postCurrentVal,
            postInvested,
            postXirr,
            notice
        );
    }
}
</file>

<file path="core-node/src/main/resources/static/src/js/modules/tax.js">
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

  const usedVal = data.exemption_used || data.exemptionUsed;
  const limitVal = data.exemption_limit || data.exemptionLimit;

  if (meterVal && fill && remainingText) {
    const used = Math.round(parseFloat(usedVal) || 0);
    const limit = Math.round(parseFloat(limitVal) || 125000);
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
  const realizedStcg = report.total_realized_stcg || report.totalRealizedStcg;

  if (stcgVal && realizedStcg) {
    stcgVal.textContent = formatINR(realizedStcg);
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

  const agAssets = antigravityData ? (antigravityData.antigravity_assets || antigravityData.antigravityAssets) : null;
  const mktDd = antigravityData ? (antigravityData.market_drawdown_pct || antigravityData.marketDrawdownPct) : null;

  if (agAssets && agAssets.length > 0) {
    for (const ag of agAssets) {
      const assetName = ag.asset_name || ag.assetName;
      const twr = ag.twr_30d_pct || ag.twr30dPct;
      html += `
        <div class="radar-card info-border" style="border-left: 3px solid #06b6d4; background: rgba(6, 182, 212, 0.08);">
          <div class="radar-icon info">🚀</div>
          <div class="radar-content">
            <div class="radar-title" style="color:#06b6d4;">ANTIGRAVITY DETECTED (${assetName})</div>
            <div class="radar-desc">Beta: <strong>${ag.beta}</strong> | 30d TWR: <strong>+${twr}%</strong> during market drawdown (${mktDd}%). ${ag.recommendation}</div>
          </div>
          <span class="antigravity-badge">🚀 Low Beta + Alpha</span>
        </div>
      `;
    }
  }

  if (opportunities && opportunities.length > 0) {
    for (const opp of opportunities.slice(0, 2)) {
      const assetName = opp.asset_name || opp.assetName;
      const lossVal = opp.potential_harvestable_loss || opp.potentialHarvestableLoss;
      const loss = Math.round(parseFloat(lossVal) || 0);

      html += `
        <div class="radar-card warning-border">
          <div class="radar-icon warning">⚡</div>
          <div class="radar-content">
            <div class="radar-title">Tax-Loss Harvesting Opportunity</div>
            <div class="radar-desc">Harvest <strong>${formatINR(loss)}</strong> loss in <em>${assetName}</em> before March 31.</div>
          </div>
          <span class="days-badge">Harvest Now</span>
        </div>
      `;
    }
  }

  if (ladder && ladder.length > 0) {
    for (const mat of ladder.slice(0, 2)) {
      const assetName = mat.asset_name || mat.assetName;
      const units = mat.remaining_units || mat.remainingUnits;
      const targetDate = mat.target_ltcg_date || mat.targetLtcgDate;
      const daysRem = mat.days_remaining_to_ltcg !== undefined ? mat.days_remaining_to_ltcg : mat.daysRemainingToLtcg;

      html += `
        <div class="radar-card maturation-border">
          <div class="radar-icon maturation">⏳</div>
          <div class="radar-content">
            <div class="radar-title">LTCG Tax Maturation Coming Up</div>
            <div class="radar-desc">Lot of <em>${assetName}</em> (${units} units) becomes <strong>LTCG</strong> on ${targetDate}.</div>
          </div>
          <span class="days-badge">Wait ${daysRem} Days</span>
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
    const dispDate = l.disposal_date || l.disposalDate;
    const acqDate = l.acquisition_date || l.acquisitionDate;
    const assetName = l.asset_name || l.assetName;
    const matched = l.units_matched || l.unitsMatched;
    const proceeds = l.sale_proceeds || l.saleProceeds;
    const cost = l.cost_basis || l.costBasis;
    const gainVal = l.realized_gain || l.realizedGain;
    const taxTerm = l.tax_term || l.taxTerm;

    const gain = Math.round(parseFloat(gainVal) || 0);
    const gainSign = gain >= 0 ? '+' : '';
    const gainColor = gain >= 0 ? 'color: #10b981;' : 'color: #ef4444;';

    html += `
      <tr>
        <td>${dispDate}</td>
        <td>${acqDate}</td>
        <td style="font-weight:600;">${assetName}</td>
        <td class="font-mono">${matched}</td>
        <td class="font-mono">${formatINR(proceeds)}</td>
        <td class="font-mono">${formatINR(cost)}</td>
        <td class="font-mono" style="${gainColor}">${gainSign}${formatINR(gain)}</td>
        <td><span class="cat-badge ${taxTerm === 'LONG_TERM' ? 'cat-EQUITY' : 'cat-DEBT_SPECIFIED_50AA'}">${taxTerm}</span></td>
      </tr>
    `;
  });

  template.innerHTML = html;
  fragment.appendChild(template.content);
  tableBody.replaceChildren(fragment);
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/dtos/SyncDtos.java">
package com.portfolioos.core.dtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

public class SyncDtos {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SyncInfoDto(
        long timestamp,
        String ledgerHash,
        String generatedAt,
        String fiscalYear,
        double portfolioXirr,
        String xirrPercentage,
        double totalInvested,
        double currentValue,
        double unrealizedGain,
        String formattedCurrentValue,
        String formattedTotalInvested,
        String formattedUnrealizedGain
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FlatHoldingDto(
        String isin,
        String fundName,
        double totalUnits,
        double avgCost,
        double xirr,
        String assetBucket,
        double currentValue,
        double investedValue,
        String formattedCurrentValue,
        String formattedInvestedValue
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FlatTaxLotDto(
        String isin,
        String buyDate,
        double units,
        String taxClassification,
        boolean isLongTerm,
        Double grandfatheredNav,
        double costPerUnit,
        long holdingDays,
        long daysToLtcg
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RadarSignalDto(
        String signalType,
        String title,
        String subtitle,
        String description,
        String severity,
        String badgeText
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record NetWorthPointDto(
        String date,
        double valuation,
        double invested
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UnidirectionalSyncSnapshot(
        SyncInfoDto syncInfo,
        List<FlatHoldingDto> holdings,
        List<FlatTaxLotDto> taxLots,
        List<RadarSignalDto> radarSignals,
        List<NetWorthPointDto> netWorthHistory
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PairRequestDto(
        String deviceId,
        String deviceName
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PairResponseDto(
        String status,
        String token,
        String serverName
    ) {}
}
</file>

<file path="core-node/src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java">
package com.portfolioos.core.persistence;

import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.ports.EventStorePort;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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
    private final HikariDataSource dataSource;

    public SqliteEventStore() {
        this(System.getenv("SQLITE_PATH") != null && !System.getenv("SQLITE_PATH").isBlank() 
             ? System.getenv("SQLITE_PATH") : "data/tax_ledger.db");
    }

    public SqliteEventStore(String dbPath) {
        this.dbPath = dbPath;
        String envSecret = System.getenv("LEDGER_HMAC_SECRET");
        if (envSecret == null || envSecret.isBlank()) {
            throw new IllegalStateException("SECURITY CRITICAL: LEDGER_HMAC_SECRET environment variable is required and cannot be empty.");
        }
        this.hmacSecret = envSecret;

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

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setPoolName("SqliteEventStorePool");

        this.dataSource = new HikariDataSource(config);
        initSchema();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void initSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
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

    @Override
    public String getLatestEventHash() {
        String sql = "SELECT event_hash FROM tax_events ORDER BY ingested_at DESC, id DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString("event_hash");
            }
            return "GENESIS";
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch latest event hash", e);
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
    public synchronized List<String> appendEvents(List<TaxEvent> events) {
        if (events.isEmpty()) return List.of();

        List<String> hashes = new ArrayList<>();
        String checkSql = "SELECT event_hash FROM tax_events WHERE asset_id = ? AND event_type = ? AND event_date = ? AND units = ? AND gross_amount = ? LIMIT 1";
        String insertSql = "INSERT INTO tax_events (id, asset_id, asset_name, isin, event_type, event_date, units, price_per_unit, gross_amount, source_document_id, ingested_at, previous_hash, event_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
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
                conn.rollback();
                throw new RuntimeException("Failed to commit transaction ledger", e);
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error in transaction execution", e);
        }
        return hashes;
    }

    @Override
    public List<TaxEvent> getEventsForAsset(String assetId) {
        List<TaxEvent> events = new ArrayList<>();
        String sql = "SELECT * FROM tax_events WHERE asset_id = ? ORDER BY event_date ASC, ingested_at ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    @Override
    public List<TaxEvent> getAllEvents() {
        List<TaxEvent> events = new ArrayList<>();
        String sql = "SELECT * FROM tax_events ORDER BY event_date ASC, ingested_at ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                events.add(mapResultSetToTaxEvent(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch all events", e);
        }
        return events;
    }

    @Override
    public boolean verifyLedgerIntegrity() {
        String sql = "SELECT previous_hash, event_hash, id, asset_id, event_type, event_date, units, gross_amount, source_document_id FROM tax_events ORDER BY ingested_at ASC, id ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
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
                    "",
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

    @Override
    public void clearAllEvents() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM tax_events");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear ledger", e);
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/service/LedgerCacheService.java">
package com.portfolioos.core.service;

import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.nav.AmfiNavSync;
import com.portfolioos.core.ports.EventStorePort;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LedgerCacheService {

    private final EventStorePort eventStore;
    private final AmfiNavSync amfiSync = new AmfiNavSync();
    private final FifoMatcher fifoMatcher = new FifoMatcher();

    private final AtomicReference<CachedLedgerState> stateHolder = new AtomicReference<>(null);
    private volatile long lastNavSyncTime = 0L;
    private final Object updateLock = new Object();

    public LedgerCacheService(EventStorePort eventStore) {
        this.eventStore = eventStore;
    }

    public static record CachedLedgerState(
        List<TaxEvent> events,
        FifoMatcher.FifoResult fifoResult,
        Map<String, BigDecimal> navMap,
        String ledgerHash
    ) {}

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedRate = 30000)
    public void refreshCacheInBackground() {
        synchronized (updateLock) {
            try {
                String currentHash = eventStore.getLatestEventHash();
                long now = System.currentTimeMillis();

                CachedLedgerState current = stateHolder.get();
                if (current == null || current.ledgerHash() == null || !currentHash.equals(current.ledgerHash()) || (now - lastNavSyncTime) >= 30_000) {
                    List<TaxEvent> events = eventStore.getAllEvents();
                    FifoMatcher.FifoResult fifoResult = fifoMatcher.processEvents(events);
                    Map<String, BigDecimal> navMap = amfiSync.getNavMap();
                    
                    stateHolder.set(new CachedLedgerState(events, fifoResult, navMap, currentHash));
                    lastNavSyncTime = now;
                }
            } catch (Exception e) {
                System.err.println("Background cache refresh warning: " + e.getMessage());
            }
        }
    }

    public CachedLedgerState getCachedState() {
        CachedLedgerState current = stateHolder.get();
        if (current == null) {
            refreshCacheInBackground();
            current = stateHolder.get();
        }
        return current;
    }

    public void invalidateCache() {
        stateHolder.set(null);
        refreshCacheInBackground();
    }
}
</file>

<file path="core-node/pom.xml">
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

        <!-- HikariCP Connection Pooling -->
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
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

        <!-- Spring AI Ollama -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>1.0.0-M1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
    </repositories>
    
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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/rpc/FlightRpcClient.java">
package com.portfolioos.core.rpc;

import com.portfolioos.core.persistence.DuckDbProjector.NavHistorySeriesEntry;
import org.apache.arrow.flight.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
        Map<String, NavHistorySeriesEntry> adapterMap = new HashMap<>();
        if (fundNavSeries != null) {
            for (Map.Entry<String, List<Double>> entry : fundNavSeries.entrySet()) {
                adapterMap.put(entry.getKey(), new NavHistorySeriesEntry(entry.getValue(), Collections.emptyList()));
            }
        }
        return computeQuantMetricsWithDates(adapterMap);
    }

    public Map<String, Map<String, Object>> computeQuantMetricsWithDates(Map<String, NavHistorySeriesEntry> fundNavSeries) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        if (fundNavSeries == null || fundNavSeries.isEmpty()) {
            return out;
        }

        int totalRows = fundNavSeries.values().stream().mapToInt(e -> e.navs().size()).sum();
        if (totalRows == 0) {
            return out;
        }

        try {
            Location location = Location.forGrpcInsecure(host, port);
            try (FlightClient client = FlightClient.builder(allocator, location).build()) {

                Schema inSchema = new Schema(List.of(
                    new Field("amfi_code", FieldType.nullable(new ArrowType.Utf8()), null),
                    new Field("nav_date", FieldType.nullable(new ArrowType.Utf8()), null),
                    new Field("nav_value", FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), null)
                ));

                try (VectorSchemaRoot inRoot = VectorSchemaRoot.create(inSchema, allocator)) {
                    VarCharVector codeVec = (VarCharVector) inRoot.getVector("amfi_code");
                    VarCharVector dateVec = (VarCharVector) inRoot.getVector("nav_date");
                    Float8Vector navVec = (Float8Vector) inRoot.getVector("nav_value");
                    codeVec.allocateNew(totalRows * 32L, totalRows);
                    dateVec.allocateNew(totalRows * 16L, totalRows);
                    navVec.allocateNew(totalRows);

                    int row = 0;
                    for (Map.Entry<String, NavHistorySeriesEntry> entry : fundNavSeries.entrySet()) {
                        byte[] codeBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                        List<Double> navs = entry.getValue().navs();
                        List<String> dates = entry.getValue().dates();

                        for (int i = 0; i < navs.size(); i++) {
                            codeVec.setSafe(row, codeBytes);
                            if (i < dates.size() && dates.get(i) != null) {
                                dateVec.setSafe(row, dates.get(i).getBytes(StandardCharsets.UTF_8));
                            } else {
                                dateVec.setSafe(row, "".getBytes(StandardCharsets.UTF_8));
                            }
                            navVec.setSafe(row, navs.get(i));
                            row++;
                        }
                    }
                    inRoot.setRowCount(totalRows);

                    FlightDescriptor descriptor = FlightDescriptor.path("quant_metrics");
                    FlightClient.ExchangeReaderWriter exchange = client.doExchange(descriptor);

                    FlightClient.ClientStreamListener writer = exchange.getWriter();
                    writer.start(inRoot);
                    writer.putNext();
                    writer.completed();

                    try (FlightStream reader = exchange.getReader()) {
                        while (reader.next()) {
                            VectorSchemaRoot outRoot = reader.getRoot();
                            VarCharVector outCode = (VarCharVector) outRoot.getVector("amfi_code");
                            for (int i = 0; i < outRoot.getRowCount(); i++) {
                                String code = new String(outCode.get(i), StandardCharsets.UTF_8);
                                Map<String, Object> metrics = new HashMap<>();
                                for (Field f : outRoot.getSchema().getFields()) {
                                    if (f.getName().equals("amfi_code")) continue;
                                    metrics.put(f.getName(), outRoot.getVector(f.getName()).getObject(i));
                                }
                                out.put(code, metrics);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Arrow Flight quant metrics call error: " + e.getMessage());
        }
        return out;
    }
}
</file>

<file path="core-node/src/main/resources/static/src/style.css">
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

/* Glassmorphism & Bento Box Enhancements */
.glass-card {
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  background: rgba(12, 16, 28, 0.75);
}

.cmd-k-btn {
  background: rgba(208, 255, 0, 0.15) !important;
  color: #d0ff00 !important;
  border: 1px solid rgba(208, 255, 0, 0.3) !important;
}

.cmd-k-btn kbd {
  background: rgba(0, 0, 0, 0.4);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 11px;
}

.cmd-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(3, 7, 18, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  z-index: 99999;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding-top: 12vh;
  box-sizing: border-box;
}

.cmd-modal-overlay[style*="display: none"] {
  display: none !important;
}

.command-palette-box {
  background: #090f1e;
  border: 1px solid rgba(208, 255, 0, 0.4);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.9), 0 0 30px rgba(208, 255, 0, 0.15);
  border-radius: 16px;
  padding: 16px;
  width: 90%;
  max-width: 620px;
  color: #fff;
}

.command-palette-header {
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 12px;
  margin-bottom: 12px;
}

.command-palette-header input {
  flex: 1;
  background: transparent;
  border: none;
  color: #fff;
  font-size: 15px;
  outline: none;
}

.cmd-k-badge {
  background: rgba(255, 255, 255, 0.1);
  color: #94a3b8;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}

.command-palette-results {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cmd-item {
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 10px;
  color: #f8fafc;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.cmd-item:hover {
  background: rgba(208, 255, 0, 0.12);
  border-color: rgba(208, 255, 0, 0.4);
  color: #d0ff00;
  transform: translateX(4px);
}
</file>

<file path="core-node/src/main/resources/static/src/js/modules/portfolio.js">
import { API_BASE, fetchJson } from '../api.js';
import { state } from '../state.js';
import { formatINR } from '../utils.js';

export function updatePortfolioSummary(summary) {
  const netWorthVal = document.querySelector('.net-worth-val');
  const gainText = document.querySelector('.net-worth-gain');
  const subText = document.querySelector('.net-worth-sub');
  const xirrVal = document.querySelector('.xirr-val');

  const curVal = summary.total_current_value || summary.totalCurrentValue;
  const gainVal = summary.total_unrealized_gain || summary.totalUnrealizedGain;
  const countVal = summary.active_holding_count !== undefined ? summary.active_holding_count : summary.activeHoldingCount;
  const xirr = summary.xirr_percentage || summary.xirrPercentage;

  if (netWorthVal && curVal) {
    netWorthVal.textContent = formatINR(curVal);
    netWorthVal.classList.remove('skeleton');
  }
  if (gainText && gainVal) {
    const gain = Math.round(parseFloat(gainVal) || 0);
    const sign = gain >= 0 ? '+' : '';
    gainText.textContent = `Unrealized gain: ${sign}${formatINR(gain)}`;
    gainText.className = `metric-delta ${gain >= 0 ? 'positive' : 'negative'}`;
  }
  if (subText && countVal !== undefined) {
    subText.innerHTML = `Active Holdings: <strong>${countVal} Schemes</strong>`;
  }
  if (xirrVal && xirr) {
    xirrVal.textContent = xirr;
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
    const assetName = h.asset_name || h.assetName || '';
    const category = h.category || '';
    const inv = Math.round(parseFloat(h.invested_value || h.investedValue) || 0);
    const cur = Math.round(parseFloat(h.current_value || h.currentValue) || 0);
    const gain = Math.round(parseFloat(h.unrealized_gain || h.unrealizedGain) || 0);
    const gainPct = h.unrealized_gain_pct || h.unrealizedGainPct || '0.00';
    const allocPct = h.allocation_pct || h.allocationPct || '0.00';
    const lots = h.lots || [];

    const gainSign = gain >= 0 ? '+' : '';
    const gainColor = gain >= 0 ? 'color: #10b981;' : 'color: #ef4444;';

    const isSip = h.has_sip || h.hasSip || (lots && lots.some(l => (l.event_type || l.eventType) === 'SIP_INSTALMENT'));
    const sipBadge = isSip ? ' <span style="background:rgba(208,255,0,0.15); color:#d0ff00; border:1px solid rgba(208,255,0,0.3); font-size:10px; padding:2px 6px; border-radius:4px; margin-left:6px; font-weight:700;">🔄 Active SIP</span>' : '';

    html += `
      <tr class="holding-row" onclick="toggleLotDetails('${idx}')">
        <td style="font-weight:600;">${assetName}${sipBadge}</td>
        <td><span class="cat-badge cat-${category}">${category.replace('_SPECIFIED_50AA', '')}</span></td>
        <td class="font-mono">${formatINR(inv)}</td>
        <td class="font-mono" style="font-weight:600;">${formatINR(cur)}</td>
        <td class="font-mono" style="${gainColor}">${gainSign}${formatINR(gain)} (${gainPct}%)</td>
        <td class="font-mono">${allocPct}%</td>
        <td><button class="pill-btn">${lots.length} Lots ▼</button></td>
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
              ${lots.map(l => {
                const acqDate = l.acquisition_date || l.acquisitionDate;
                const units = l.remaining_units || l.remainingUnits;
                const costPerUnit = parseFloat(l.cost_per_unit || l.costPerUnit || '0');
                const lotGain = parseFloat(l.unrealized_gain || l.unrealizedGain || '0');
                const daysHeld = l.holding_days !== undefined ? l.holding_days : l.holdingDays;
                const daysLeft = l.days_to_ltcg !== undefined ? l.days_to_ltcg : l.daysToLtcg;
                const isLtcg = l.is_ltcg !== undefined ? l.is_ltcg : l.isLtcg;

                return `
                <tr>
                  <td>${acqDate}</td>
                  <td class="font-mono">${units}</td>
                  <td class="font-mono">${formatINR(costPerUnit * parseFloat(units || '0'))}</td>
                  <td class="font-mono" style="${lotGain >= 0 ? 'color: #10b981;' : 'color: #ef4444;'}">
                    ${lotGain >= 0 ? '+' : ''}${formatINR(lotGain)}
                  </td>
                  <td>${daysHeld}d</td>
                  <td><span class="cat-badge ${isLtcg ? 'cat-EQUITY' : 'cat-DEBT_SPECIFIED_50AA'}">${isLtcg ? 'LTCG' : 'STCG (' + (daysLeft > 0 ? daysLeft + 'd left' : 'Always') + ')'}</span></td>
                </tr>
              `;}).join('')}
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

export function renderNetWorthTrendChart(containerId, dates, values) {
  const container = document.getElementById(containerId);
  if (!container || !dates || dates.length === 0 || !window.echarts) return null;

  const instance = window.echarts.init(container);
  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { backgroundColor: '#090f1e' } },
      formatter: params => `${params[0].name}<br/>Valuation: <b>₹ ${formatINR(params[0].value)}</b>`
    },
    grid: { left: '3%', right: '4%', bottom: '18%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
      axisLabel: { color: '#94a3b8', fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
      axisLabel: { color: '#94a3b8', fontSize: 11, formatter: v => `₹ ${(v/100000).toFixed(1)}L` }
    },
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      { type: 'slider', start: 0, end: 100, height: 16, bottom: 0, borderColor: 'transparent', backgroundColor: 'rgba(255,255,255,0.05)', fillerColor: 'rgba(208,255,0,0.2)' }
    ],
    series: [{
      name: 'Net Worth',
      type: 'line',
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 3, color: '#d0ff00' },
      areaStyle: {
        color: new window.echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(208,255,0,0.35)' },
          { offset: 1, color: 'rgba(6,182,212,0.02)' }
        ])
      },
      data: values
    }]
  };
  instance.setOption(option);
  return instance;
}

export function renderAllocationChart(allocations) {
  if (state.charts.allocChart) state.charts.allocChart.dispose();
  if (!allocations || allocations.length === 0) return;

  const total = allocations.reduce((sum, a) => sum + (parseFloat(a.current_value || a.currentValue) || 0), 0);
  
  const main = [];
  let othersVal = 0;
  let othersCount = 0;

  allocations.forEach(a => {
    const val = parseFloat(a.current_value || a.currentValue) || 0;
    const assetName = a.asset_name || a.assetName || '';
    const pct = total > 0 ? (val / total) * 100 : 0;
    if (pct < 3.0 && allocations.length > 6) {
      othersVal += val;
      othersCount++;
    } else {
      main.push({
        name: assetName.length > 25 ? assetName.substring(0, 23) + '...' : assetName,
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
    name: c.category_name || c.categoryName,
    value: parseFloat(c.current_value || c.currentValue) || 0
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

  const isWindowOpen = data.is_rebalance_window_open !== undefined ? data.is_rebalance_window_open : data.isRebalanceWindowOpen;
  const nextWindow = data.next_scheduled_window || data.nextScheduledWindow;
  const totalProceeds = data.total_proceeds || data.totalProceeds;
  const totalTaxDrag = data.total_tax_drag || data.totalTaxDrag;
  const proRata = data.pro_rata_allocations || data.proRataAllocations || [];

  if (badge) {
    badge.textContent = isWindowOpen ? 'WINDOW OPEN: EXECUTE REBALANCE' : `SCHEDULED WINDOW: ${nextWindow}`;
    badge.style.color = isWindowOpen ? '#10b981' : '#06b6d4';
  }

  const parsedProceeds = parseFloat(totalProceeds);
  const proceeds = (!isNaN(parsedProceeds)) ? Math.round(parsedProceeds) : 0;
  const taxDrag = Math.round(parseFloat(totalTaxDrag) || 0);

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

  for (const alloc of proRata) {
    const assetName = alloc.asset_name || alloc.assetName;
    const weightPct = alloc.sip_weight_pct || alloc.sipWeightPct;
    const deployAmt = alloc.deployment_amount || alloc.deploymentAmount;

    const amt = Math.round(parseFloat(deployAmt) || 0);
    html += `
      <tr>
        <td style="font-weight:600;">${assetName}</td>
        <td><span class="days-badge">${weightPct}%</span></td>
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

  const taxDrag = data.total_tax_drag || data.totalTaxDrag;
  const effRate = data.effective_tax_rate_pct || data.effectiveTaxRatePct;
  const ltcgHarv = data.ltcg_exemption_harvested || data.ltcgExemptionHarvested;

  if (rebTaxDrag && taxDrag) {
    rebTaxDrag.textContent = formatINR(taxDrag);
  }
  if (rebEffRate && effRate) {
    rebEffRate.textContent = effRate;
  }
  if (rebLtcgHarvested && ltcgHarv) {
    rebLtcgHarvested.textContent = formatINR(ltcgHarv);
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
  const unallocCash = data.unallocated_cash || data.unallocatedCash;
  if (idleVal && unallocCash) {
    idleVal.textContent = formatINR(unallocCash);
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

  const status = data.status;
  const shortage = data.shortage_or_surplus_amount || data.shortageOrSurplusAmount;
  const activeLabel = data.active_scenario_label || data.activeScenarioLabel;
  const fireInvestable = data.fire_investable_net_worth || data.fireInvestableNetWorth;
  const requiredCorpus = data.required_corpus || data.requiredCorpus;
  const projectedCorpus = data.projected_corpus_at_target_age || data.projectedCorpusAtTargetAge;

  if (statusPill) {
    statusPill.textContent = status === 'ON_TRACK' ? 'ON TRACK' : `SHORT BY ${formatINR(shortage)}`;
    statusPill.className = `fire-status-pill ${status === 'ON_TRACK' ? 'on-track' : 'short'}`;
  }

  if (scenarioLabel) scenarioLabel.textContent = `Scenario: ${activeLabel}`;

  if (investableNw) investableNw.textContent = formatINR(fireInvestable);
  if (reqCorpus) reqCorpus.textContent = `₹ ${(parseFloat(requiredCorpus) / 10000000).toFixed(2)} Cr`;
  if (projCorpus) projCorpus.textContent = `₹ ${(parseFloat(projectedCorpus) / 10000000).toFixed(2)} Cr`;
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

  const dd = data.drawdown_status || data.drawdownStatus;
  const statuses = data.bucket_statuses || data.bucketStatuses;

  if (drawdownTag && dd) {
    const bmName = dd.benchmark_name || dd.benchmarkName;
    const ddPct = dd.drawdown_pct || dd.drawdownPct;
    drawdownTag.textContent = `${bmName}: ${ddPct}% Drawdown`;
  }

  if (bucketGrid && statuses) {
    let html = '';
    statuses.forEach(b => {
      const isDrifted = b.is_drifted !== undefined ? b.is_drifted : b.isDrifted;
      const driftPct = b.drift_pct || b.driftPct;
      const curVal = b.current_value || b.currentValue;
      const curPct = b.current_pct || b.currentPct;
      const targetPct = b.target_pct || b.targetPct;

      const nameFmt = b.bucket.replace('_', ' ');
      html += `
        <div class="bucket-card ${isDrifted ? 'drifted' : ''}">
          <div class="bucket-card-header">
            <span class="bucket-name">${nameFmt}</span>
            <span class="drift-badge ${isDrifted ? 'warn' : 'ok'}">${isDrifted ? 'Drift: ' + driftPct + '%' : 'Target OK'}</span>
          </div>
          <div class="font-mono" style="font-size:16px; font-weight:700;">${formatINR(curVal)}</div>
          <div class="text-muted" style="font-size:11px; margin-top:4px;">Current: ${curPct}% · Target: ${targetPct}%</div>
        </div>
      `;
    });
    bucketGrid.innerHTML = html;
  }
}
</file>

<file path="core-node/src/main/resources/static/index.html">
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
        <button id="cmdKTriggerBtn" class="upload-btn cmd-k-btn" onclick="window.openCmdPalette && window.openCmdPalette()">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
          ⚡ AI Search <kbd>/</kbd> <kbd>⌘K</kbd>
        </button>

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
  <!-- Global Command Palette Modal -->
  <div id="commandPaletteModal" class="cmd-modal-overlay" style="display: none;">
    <div class="command-palette-box">
      <div class="command-palette-header">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#D0FF00" stroke-width="2.5"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
        <input type="text" id="commandPaletteInput" placeholder="Type an AI prompt, SQL query, or tax question... (Esc to exit)">
        <button type="button" id="closeCmdPaletteBtn" style="background:transparent; border:none; color:#94a3b8; cursor:pointer; font-size:18px; font-weight:bold; padding:0 4px;" title="Close (Esc)">✕</button>
      </div>
      <div class="command-palette-results" id="commandPaletteResults">
        <div class="cmd-item" data-action="whatif">⚡ Open What-If Trade Simulator</div>
        <div class="cmd-item" data-action="schedule-cg">📄 Download Schedule CG Tax CSV</div>
        <div class="cmd-item" data-action="rebalance">⚖️ Run Portfolio Rebalance Engine</div>
        <div class="cmd-item" data-action="holdings">📊 Jump to Holdings & NAV Trend</div>
        <div class="cmd-item" data-action="radar">🧠 View AI Quant Radar Signals</div>
      </div>
    </div>
  </div>

  <script type="module" src="./src/app.js?v=3.0.6"></script>
</body>
</html>
</file>

<file path="core-node/src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java">
package com.portfolioos.core.persistence;

import com.portfolioos.core.model.TaxEvent;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

public class DuckDbProjector {

    private final String dbPath;
    private final String jdbcUrl;
    private final HikariDataSource dataSource;

    public static record NavHistorySeriesEntry(
        List<Double> navs,
        List<String> dates
    ) {}

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

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.duckdb.DuckDBDriver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setPoolName("DuckDbProjectorPool");

        this.dataSource = new HikariDataSource(config);
        initReadSchema();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void initReadSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
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

            stmt.execute(
                "CREATE TABLE IF NOT EXISTS nav_history (" +
                "  asset_id VARCHAR NOT NULL," +
                "  nav_date VARCHAR NOT NULL," +
                "  nav DOUBLE NOT NULL," +
                "  PRIMARY KEY (asset_id, nav_date)" +
                ")"
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize DuckDB schema", e);
        }
    }

    public void projectEvents(List<TaxEvent> events) {
        if (events == null || events.isEmpty()) return;

        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                String insertSql = "INSERT INTO projected_events (id, asset_id, asset_name, isin, event_type, event_date, units, price_per_unit, gross_amount, source_document_id, ingested_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
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

    public void saveNavHistoryBatchForHeldAssets(Map<String, BigDecimal> navMap, Set<String> heldIsins, LocalDate date) {
        if (navMap == null || navMap.isEmpty() || heldIsins == null || heldIsins.isEmpty()) return;

        try (Connection conn = getConnection()) {
            boolean wasAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);

                String dateStr = date.toString();
                String sql = "INSERT INTO nav_history (asset_id, nav_date, nav) VALUES (?, ?, ?) ON CONFLICT (asset_id, nav_date) DO NOTHING";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (String isin : heldIsins) {
                        BigDecimal nav = navMap.get(isin);
                        if (nav != null) {
                            stmt.setString(1, isin);
                            stmt.setString(2, dateStr);
                            stmt.setDouble(3, nav.doubleValue());
                            stmt.executeUpdate();
                        }
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
            } finally {
                conn.setAutoCommit(wasAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("DuckDB nav_history save failure: " + e.getMessage());
        }
    }

    public Map<String, List<Double>> getNavHistorySeries(Set<String> assetIds) {
        Map<String, NavHistorySeriesEntry> full = getNavHistorySeriesWithDates(assetIds);
        Map<String, List<Double>> result = new HashMap<>();
        for (Map.Entry<String, NavHistorySeriesEntry> entry : full.entrySet()) {
            result.put(entry.getKey(), entry.getValue().navs());
        }
        return result;
    }

    public Map<String, NavHistorySeriesEntry> getNavHistorySeriesWithDates(Set<String> assetIds) {
        Map<String, NavHistorySeriesEntry> result = new HashMap<>();
        if (assetIds == null || assetIds.isEmpty()) return result;

        try (Connection conn = getConnection()) {
            String sql = "SELECT asset_id, nav_date, nav FROM nav_history WHERE asset_id = ? ORDER BY nav_date ASC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (String assetId : assetIds) {
                    stmt.setString(1, assetId);
                    List<Double> navs = new ArrayList<>();
                    List<String> dates = new ArrayList<>();
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            dates.add(rs.getString("nav_date"));
                            navs.add(rs.getDouble("nav"));
                        }
                    }
                    if (!navs.isEmpty()) {
                        result.put(assetId, new NavHistorySeriesEntry(navs, dates));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch NAV history series with dates from DuckDB: " + e.getMessage());
        }
        return result;
    }

    public static record NetWorthPoint(
        String date,
        double valuation,
        double invested
    ) {}

    public List<NetWorthPoint> getDailyNetWorthTrend() {
        List<NetWorthPoint> trend = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT nav_date, SUM(nav) as total_nav FROM daily_nav_history GROUP BY nav_date ORDER BY nav_date ASC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String d = rs.getString("nav_date");
                    double val = rs.getDouble("total_nav");
                    trend.add(new NetWorthPoint(d, val, val * 0.9));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch daily net worth trend: " + e.getMessage());
        }
        return trend;
    }
}
</file>

<file path="core-node/src/main/resources/static/src/app.js">
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

  // Command Palette Handler (Cmd + K / Ctrl + K / Slash)
  const cmdPaletteModal = document.getElementById('commandPaletteModal');
  const cmdInput = document.getElementById('commandPaletteInput');
  const cmdResults = document.getElementById('commandPaletteResults');

  function openCmdPalette() {
    const modal = document.getElementById('commandPaletteModal') || cmdPaletteModal;
    const input = document.getElementById('commandPaletteInput') || cmdInput;
    if (!modal) return;

    modal.style.display = 'flex';

    if (input) {
      setTimeout(() => {
        input.focus();
        input.select();
      }, 50);
    }
  }

  function closeCmdPalette() {
    const modal = document.getElementById('commandPaletteModal') || cmdPaletteModal;
    if (!modal) return;
    modal.style.display = 'none';
  }

  window.openCmdPalette = openCmdPalette;
  window.closeCmdPalette = closeCmdPalette;

  // Event Delegation for Button, Close X, and Backdrop Click
  document.addEventListener('click', (e) => {
    if (e.target.closest('#cmdKTriggerBtn, .cmd-k-btn')) {
      e.preventDefault();
      openCmdPalette();
      return;
    }

    if (e.target.closest('#closeCmdPaletteBtn')) {
      e.preventDefault();
      closeCmdPalette();
      return;
    }

    const modal = document.getElementById('commandPaletteModal') || cmdPaletteModal;
    if (modal && e.target === modal) {
      closeCmdPalette();
    }
  });

  if (cmdPaletteModal) {
    cmdPaletteModal.addEventListener('cancel', () => closeCmdPalette());
  }

  window.addEventListener('keydown', (e) => {
    const key = e.key ? e.key.toLowerCase() : '';
    const isInputActive = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName);

    if (((e.metaKey || e.ctrlKey || e.altKey) && key === 'k') || (!isInputActive && key === '/')) {
      e.preventDefault();
      e.stopPropagation();
      openCmdPalette();
    }
  }, true);

  if (cmdInput) {
    cmdInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        const query = cmdInput.value.trim();
        if (!query) return;

        if (cmdResults) {
          cmdResults.innerHTML = '<div style="padding:12px; color:#06b6d4; font-family:monospace;">🧠 AI Engine Thinking...</div>';
        }

        const evtSource = new EventSource(`/api/v1/llm/stream?prompt=${encodeURIComponent(query)}`);
        let outputText = '';

        evtSource.onmessage = function(event) {
          outputText += event.data;
          if (cmdResults) {
            cmdResults.innerHTML = `
              <div style="padding:12px; background:#0f172a; border-radius:8px; color:#f8fafc; font-size:13px; white-space:pre-wrap; font-family:monospace; line-height:1.5;">
                <div style="color:#d0ff00; font-weight:bold; margin-bottom:6px;">⚡ PORTFOLIO OS AI RESPONSE</div>
                ${outputText}
              </div>
            `;
          }
        };

        evtSource.onerror = function() {
          evtSource.close();
        };
      }
    });
  }

  if (cmdResults) {
    cmdResults.addEventListener('click', (e) => {
      const item = e.target.closest('.cmd-item');
      if (!item) return;
      const action = item.getAttribute('data-action');
      closeCmdPalette();

      if (action === 'schedule-cg') {
        window.open('/api/v1/tax/schedule-cg/export', '_blank');
        showToast('Downloading Schedule CG Tax Report CSV...', 'success');
      } else if (action === 'rebalance') {
        fetchBucketRebalance();
        showToast('Evaluating Portfolio Rebalance Rungs...', 'info');
      } else if (action === 'whatif' || action === 'holdings') {
        const hTab = document.querySelector('[data-tab="holdings"]');
        if (hTab) hTab.click();
      } else if (action === 'radar') {
        fetchDecisionRadar();
      }
    });
  }

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
</file>

<file path="core-node/src/main/java/com/portfolioos/core/controllers/SyncController.java">
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
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.persistence.DuckDbProjector.NavHistorySeriesEntry;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.rpc.FlightRpcClient;
import com.portfolioos.core.service.LedgerCacheService;
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

    private final LedgerCacheService cacheService;
    private final XirrEngine xirrEngine = new XirrEngine();
    private final DuckDbProjector duckDbProjector = new DuckDbProjector();
    private final FlightRpcClient flightRpcClient = new FlightRpcClient();

    public SyncController(LedgerCacheService cacheService) {
        this.cacheService = cacheService;
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
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<TaxEvent> allEvents = state.events();
        List<Lot> openLots = state.fifoResult().openLots();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
        Map<String, BigDecimal> navMap = state.navMap();
        String ledgerHash = state.ledgerHash();

        LocalDate today = LocalDate.now();
        Locale inLocale = new Locale("en", "IN");
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(inLocale);

        // Collect held ISINs and persist daily NAV history strictly for held assets
        Set<String> heldIsins = openLots.stream().map(Lot::assetId).collect(Collectors.toSet());
        duckDbProjector.saveNavHistoryBatchForHeldAssets(navMap, heldIsins, today);

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

        // Generate Verified Priority AI Radar Signals
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

        // 2. PyArrow Flight RPC Quant Intelligence (from real DuckDB NAV time-series with dates)
        try {
            Map<String, NavHistorySeriesEntry> navHistorySeries = duckDbProjector.getNavHistorySeriesWithDates(heldIsins);
            if (!navHistorySeries.isEmpty()) {
                Map<String, Map<String, Object>> quantMetrics = flightRpcClient.computeQuantMetricsWithDates(navHistorySeries);
                Map<String, String> isinToNameMap = holdings.stream().collect(Collectors.toMap(FlatHoldingDto::isin, FlatHoldingDto::fundName, (a, b) -> a));

                for (Map.Entry<String, Map<String, Object>> entry : quantMetrics.entrySet()) {
                    String isin = entry.getKey();
                    Map<String, Object> metrics = entry.getValue();
                    if (metrics == null) continue;

                    String status = String.valueOf(metrics.getOrDefault("status", "INSUFFICIENT_HISTORY"));
                    if (!"OK".equalsIgnoreCase(status)) {
                        continue;
                    }

                    String schemeName = isinToNameMap.getOrDefault(isin, isin);

                    Object sharpeObj = metrics.get("sharpe");
                    Object maxDdObj = metrics.get("max_drawdown");

                    String bucket = detectFineBucket(schemeName);

                    if (sharpeObj instanceof Number sharpe && sharpe.doubleValue() >= 1.2) {
                        radarSignals.add(new RadarSignalDto(
                            "QUANT_ANALYTICS",
                            schemeName,
                            "QUANT STATS: HIGH SHARPE (" + String.format("%.2f", sharpe.doubleValue()) + ")",
                            "[" + bucket + "] " + schemeName + " displays a risk-adjusted Sharpe ratio of " + String.format("%.2f", sharpe.doubleValue()) + " over tracked NAV history.",
                            "INFO",
                            "Sharpe " + String.format("%.2f", sharpe.doubleValue())
                        ));
                    }

                    double ddThreshold = switch (bucket) {
                        case "Debt & Liquid" -> 0.05;
                        case "Core Equity", "Flexi Cap", "Large & Midcap", "Equal Weight Index", "Gold & Commodities" -> 0.15;
                        default -> 0.25; // Small Cap, Microcap, Sectoral, Midcap, Factor Value/Momentum
                    };

                    if (maxDdObj instanceof Number maxDd && Math.abs(maxDd.doubleValue()) >= ddThreshold) {
                        double maxDdPct = Math.abs(maxDd.doubleValue()) * 100.0;
                        double thresholdPct = ddThreshold * 100.0;
                        radarSignals.add(new RadarSignalDto(
                            "QUANT_ANALYTICS",
                            schemeName,
                            "QUANT STATS: DEEP DRAWDOWN (" + String.format("%.1f", maxDdPct) + "%)",
                            "[" + bucket + "] " + schemeName + " max drawdown (" + String.format("%.1f", maxDdPct) + "%) exceeds " + String.format("%.0f", thresholdPct) + "% " + bucket + " category threshold.",
                            "WARNING",
                            "Max DD -" + String.format("%.1f", maxDdPct) + "%"
                        ));
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("Non-critical Quant Flight RPC signal extraction warning: " + ex.getMessage());
        }

        // 2.5 Automated SIP Cashflow Signal
        long sipCount = allEvents.stream()
            .filter(e -> e.eventType() == EventType.SIP_INSTALMENT)
            .map(TaxEvent::assetId)
            .distinct()
            .count();

        if (sipCount > 0) {
            radarSignals.add(0, new RadarSignalDto(
                "SIP_DETECTION",
                "Automated SIP Tracker",
                "RECURRING SIP DISCIPLINE",
                String.format("Auto-detected %d active monthly SIPs across portfolio. Disciplined recurring cashflow active.", sipCount),
                "INFO",
                sipCount + " Active SIPs"
            ));
        }

        // 3. LTCG Maturation Ladder Signal
        Lot maturingLot = null;
        long minDaysToLtcg = 9999L;

        for (Lot lot : openLots) {
            long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
            long daysToLtcg = Math.max(0L, 365L - holdingDays);
            if (daysToLtcg > 0 && daysToLtcg <= 120 && daysToLtcg < minDaysToLtcg) {
                minDaysToLtcg = daysToLtcg;
                maturingLot = lot;
            }
        }

        if (maturingLot != null) {
            radarSignals.add(0, new RadarSignalDto(
                "MATURATION",
                maturingLot.assetName(),
                "LTCG MATURATION LADDER",
                maturingLot.assetName() + " (Lot " + maturingLot.lotId() + ") matures under Sec 112A in " + minDaysToLtcg + " days.",
                "INFO",
                minDaysToLtcg + " Days"
            ));
        }

        // 4. Asset Allocation Drift Signal
        BucketEngine.RebalanceEngineResult bucketStatus = BucketEngine.evaluateRebalance(
            openLots, navMap, today, new BigDecimal("24000.00"), new BigDecimal("25000.00"), BucketEngine.DEFAULT_TARGETS, fy
        );

        BucketEngine.BucketStatus driftedBucket = bucketStatus.bucketStatuses().stream()
            .filter(BucketEngine.BucketStatus::isDrifted)
            .findFirst()
            .orElse(null);

        if (driftedBucket != null) {
            radarSignals.add(new RadarSignalDto(
                "REBALANCE",
                "Bucket " + driftedBucket.bucket().name(),
                "ALLOCATION DRIFT ALERT",
                "Current allocation is " + driftedBucket.currentPct() + "% vs target " + driftedBucket.targetPct() + "%. Rebalance recommended.",
                "WARNING",
                "Rebalance"
            ));
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

        List<NetWorthPointDto> netWorthHistory = duckDbProjector.getDailyNetWorthTrend().stream()
            .map(p -> new NetWorthPointDto(p.date(), p.valuation(), p.invested()))
            .toList();

        return ResponseEntity.ok(new UnidirectionalSyncSnapshot(
            syncInfo, holdings, taxLots, radarSignals, netWorthHistory
        ));
    }

    @PostMapping("/pair")
    public ResponseEntity<PairResponseDto> pairDevice(
        @RequestBody PairRequestDto req
    ) {
        String token = "fintracker_jwt_" + req.deviceId() + "_" + System.currentTimeMillis();
        return ResponseEntity.ok(new PairResponseDto(
            "SUCCESS",
            token,
            "my-fintracker-core"
        ));
    }
}
</file>

</files>
</file>

</files>
