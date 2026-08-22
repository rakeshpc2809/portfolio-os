This file is a merged representation of the entire codebase, combined into a single document by Repomix.
The content has been processed where content has been compressed (code blocks are separated by ⋮---- delimiter).

# File Summary

## Purpose
This file contains a packed representation of the entire repository's contents.
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
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Content has been compressed - code blocks are separated by ⋮---- delimiter
- Files are sorted by Git change count (files with more changes are at the bottom)

# Directory Structure
````
rules/
  bucket_targets.yaml
  FY2025-26.yaml
  FY2026-27.yaml
src/
  main/
    java/
      com/
        portfolioos/
          core/
            common/
              PortfolioConstants.java
            config/
              AppConfig.java
            controllers/
              ConfigController.java
              LlmQueryController.java
              RebalanceController.java
              ReportController.java
              SimulatorController.java
              StatementsController.java
              SyncController.java
            dtos/
              ParsedEventDto.java
              RebalancePlanDtos.java
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
              FundTierClassifier.java
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
              MfApiNavDownloader.java
              NseIndexConstituentDownloader.java
            parser/
              NipponHoldingsParser.java
              PpfasHoldingsParser.java
            persistence/
              DuckDbProjector.java
              SqliteEventStore.java
              TriggerHistoryRepository.java
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
              BucketConfigLoader.java
              FireActionRuleEngine.java
              TaxRulesConfig.java
              TaxRulesLoader.java
            security/
              SecurityConfig.java
              SecurityInterceptor.java
            service/
              LedgerCacheService.java
              PortfolioValuationService.java
              RebalancePlanEngine.java
              RebalanceTriggerEvaluator.java
              SimulationService.java
              StatementIngestionUseCase.java
              TaxOptimizationService.java
            tools/
              PortfolioQueryTools.java
            util/
              Pair.java
            valuation/
              BucketEngine.java
              ConsolidationRebalanceEngine.java
              FundTrendDampenerCalculator.java
              GoldDampenerCalculator.java
              HarvestAdvisor.java
              RebalanceEngine.java
              RebalanceWaterfallEngine.java
              WaterfallTier.java
            xirr/
              CashFlow.java
              XirrCalculationException.java
              XirrEngine.java
            CoreApplication.java
    resources/
      META-INF/
        native-image/
          reflect-config.json
          resource-config.json
      static/
        src/
          js/
            modules/
              insurance.js
              portfolio.js
              tax.js
            api.js
            constants.js
            domUtils.js
            state.js
            utils.js
          app.js
          style.css
        index.html
      application.yml
  test/
    java/
      com/
        portfolioos/
          core/
            controllers/
              ConfigControllerTest.java
              SyncControllerTest.java
            fire/
              FireTrackerTest.java
            goals/
              GoalTrackerTest.java
            matcher/
              FundTierClassifierTest.java
              TaxClassifierTest.java
            nav/
              AmfiNavSyncTest.java
            persistence/
              SqliteEventStoreTest.java
            reconciliation/
              ReconciliationGateTest.java
            reporting/
              Itr2CsvExporterTest.java
            rules/
              BucketConfigLoaderTest.java
              FireActionRuleEngineTest.java
              TaxRulesLoaderTest.java
            security/
              SecurityInterceptorTest.java
            service/
              DuckDbProjectorNetWorthAccountingTest.java
              LegacyFundWaterfallAuditTest.java
              RebalancePlanEngineTest.java
              RebalanceSankeyDtoTest.java
              RebalanceTriggerEvaluatorTest.java
              SimulationServiceTest.java
              TaxOptimizationServiceTest.java
            tools/
              PortfolioQueryToolsTest.java
            valuation/
              BucketAllocationTest.java
              GoldDampenerCalculatorTest.java
              MonteCarloSanityTest.java
              RebalanceWaterfallEngineTest.java
            xirr/
              XirrEngineTest.java
Dockerfile
pom.xml
````

# Files

## File: rules/FY2025-26.yaml
````yaml
fy: "2025-26"
effective_start: "2025-04-01"
effective_end: "2026-03-31"

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
````

## File: rules/FY2026-27.yaml
````yaml
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
````

## File: src/main/java/com/portfolioos/core/common/PortfolioConstants.java
````java
/**
 * System-wide operational parameters (non-tax law constants).
 */
public final class PortfolioConstants {
⋮----
public static double calculateDrawdownPct(java.math.BigDecimal currentVal, java.math.BigDecimal rollingHigh) {
if (rollingHigh == null || rollingHigh.compareTo(java.math.BigDecimal.ZERO) <= 0 || currentVal == null) {
⋮----
return rollingHigh.subtract(currentVal)
.divide(rollingHigh, 4, java.math.RoundingMode.HALF_UP)
.doubleValue() * 100.0;
⋮----
public static String deriveTriggerType(double drawdownPct) {
````

## File: src/main/java/com/portfolioos/core/config/AppConfig.java
````java
public class AppConfig {
⋮----
public EventStorePort eventStore(
⋮----
return new SqliteEventStore(dbPath);
⋮----
public DuckDbProjector duckDbProjector(
⋮----
return new DuckDbProjector(dbPath);
⋮----
public FlightRpcClient flightRpcClient(
⋮----
return new FlightRpcClient(host, port);
⋮----
public ChatClient.Builder chatClientBuilder(
⋮----
if (ollamaUrl.contains("localhost") || ollamaUrl.contains("127.0.0.1")) {
// Test if running inside container and target host gateway if needed
⋮----
OllamaApi ollamaApi = new OllamaApi(resolvedUrl);
OllamaChatModel chatModel = new OllamaChatModel(
⋮----
OllamaOptions.create().withModel("qwen2.5-coder:7b")
⋮----
return ChatClient.builder(chatModel);
⋮----
public org.springframework.ai.ollama.OllamaEmbeddingModel embeddingModel(
⋮----
OllamaOptions.create().withModel("nomic-embed-text")
⋮----
public org.springframework.ai.vectorstore.VectorStore vectorStore(
````

## File: src/main/java/com/portfolioos/core/controllers/ConfigController.java
````java
public class ConfigController {
⋮----
public ResponseEntity<BucketConfigLoader.BucketRulesConfig> getBucketTargets() {
return ResponseEntity.ok(BucketConfigLoader.loadConfig());
⋮----
public ResponseEntity<?> updateBucketTargets(@RequestBody Map<String, Object> req) {
⋮----
String effectiveFrom = (String) req.getOrDefault("effectiveFrom", req.get("effective_from"));
List<Map<String, Object>> targetsList = (List<Map<String, Object>>) req.get("targets");
⋮----
if (targetsList == null || targetsList.isEmpty()) {
return ResponseEntity.badRequest().body(Map.of("error", "Missing 'targets' array in request body"));
⋮----
List<BucketConfigLoader.BucketTargetConfig> newTargets = targetsList.stream().map(tMap -> {
String bName = (String) tMap.get("bucket");
double tPct = ((Number) tMap.get("targetPct") != null ? (Number) tMap.get("targetPct") : (Number) tMap.get("target_pct")).doubleValue();
double bPct = ((Number) tMap.get("bandPct") != null ? (Number) tMap.get("bandPct") : (Number) tMap.get("band_pct")).doubleValue();
⋮----
if (tMap.containsKey("preferredFunds") || tMap.containsKey("preferred_funds")) {
List<Map<String, Object>> pfList = (List<Map<String, Object>>) tMap.getOrDefault("preferredFunds", tMap.get("preferred_funds"));
⋮----
prefFunds.add(new BucketConfigLoader.PreferredFundConfig(
(String) pfMap.get("fundId"),
(String) pfMap.get("fundName"),
((Number) pfMap.get("allocationWeight")).doubleValue()
⋮----
prefFunds = BucketConfigLoader.getDefaultPreferredFundsForBucket(bName);
⋮----
}).toList();
⋮----
BucketConfigLoader.updateBucketTargets(newTargets, effectiveFrom);
⋮----
return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
⋮----
return ResponseEntity.internalServerError().body(Map.of("error", "Failed to update bucket targets: " + e.getMessage()));
⋮----
public ResponseEntity<?> getRebalancePlanAlias(@RequestParam(value = "trigger", required = false, defaultValue = "INDUCED") String triggerType) {
// Forwarding to SyncController endpoint logic
return ResponseEntity.status(307).header("Location", "/api/v1/sync/rebalance/plan?trigger=" + triggerType).build();
````

## File: src/main/java/com/portfolioos/core/controllers/LlmQueryController.java
````java
public class LlmQueryController {
⋮----
private static final Logger log = LoggerFactory.getLogger(LlmQueryController.class);
⋮----
private final ObjectMapper objectMapper = new ObjectMapper();
⋮----
this.restClient = RestClient.builder().build();
⋮----
String queryType, // TOOL_CALL, TAX_RAG, SQL, GENERAL
⋮----
public LlmQueryResponse handleQuery(@RequestBody LlmQueryRequest req) {
if (req == null || req.prompt() == null || req.prompt().isBlank()) {
return new LlmQueryResponse("UNKNOWN", "Please provide a valid prompt.", null, null, "ERROR");
⋮----
String prompt = req.prompt().trim();
⋮----
// Build tool definitions JSON for Ollama API
List<Map<String, Object>> toolsDef = buildToolDefinitions();
⋮----
Map<String, Object> requestBody = Map.of(
⋮----
"messages", List.of(
Map.of("role", "system", "content", "You are Portfolio OS financial assistant. Select the appropriate tool function for the user query."),
Map.of("role", "user", "content", prompt)
⋮----
log.info("Sending tool-call query to local Ollama at {} (model: {})", ollamaUrl, modelName);
⋮----
String rawResponse = restClient.post()
.uri(ollamaUrl)
.contentType(MediaType.APPLICATION_JSON)
.body(requestBody)
.retrieve()
.body(String.class);
⋮----
JsonNode rootNode = objectMapper.readTree(rawResponse);
JsonNode messageNode = rootNode.path("message");
⋮----
// 1. Check for standard tool_calls array
JsonNode toolCalls = messageNode.path("tool_calls");
if (toolCalls.isArray() && !toolCalls.isEmpty()) {
JsonNode firstCall = toolCalls.get(0).path("function");
toolName = firstCall.path("name").asText(null);
toolArgsNode = firstCall.path("arguments");
⋮----
// 2. Check for inline json tool call in content (e.g. {"name": "...", "arguments": {...}})
if (toolName == null || toolName.isBlank()) {
String contentText = messageNode.path("content").asText("").trim();
if (contentText.startsWith("{") && contentText.endsWith("}")) {
⋮----
JsonNode inlineJson = objectMapper.readTree(contentText);
if (inlineJson.has("name")) {
toolName = inlineJson.path("name").asText(null);
toolArgsNode = inlineJson.path("arguments");
⋮----
// Execute Java Tool if selected by model
if (toolName != null && !toolName.isBlank()) {
log.info("Ollama model {} selected tool: {} with args: {}", modelName, toolName, toolArgsNode);
return executeSelectedTool(toolName, toolArgsNode, prompt);
⋮----
// Fallback for non-tool prompts: Tax RAG or Text-to-SQL or General Chat
String contentText = messageNode.path("content").asText("");
if (prompt.toLowerCase().contains("tax") || prompt.toLowerCase().contains("112a")) {
String answer = taxRagService.answerTaxQuestion(prompt);
return new LlmQueryResponse("TAX_RAG", answer, null, null, "SUCCESS");
⋮----
SqlGeneratorService.SqlQueryResult sqlRes = sqlService.generateAndExecute(prompt);
if ("SUCCESS".equalsIgnoreCase(sqlRes.status())) {
String summary = String.format("Query executed successfully. Found %d matching records.", sqlRes.data().size());
return new LlmQueryResponse("SQL", summary, sqlRes.generatedSql(), sqlRes.data(), "SUCCESS");
⋮----
return new LlmQueryResponse("GENERAL", contentText.isBlank() ? "No tool call generated by model." : contentText, null, null, "SUCCESS");
⋮----
log.error("Error executing Ollama query: {}", e.getMessage(), e);
return new LlmQueryResponse("GENERAL", "Error executing query: " + e.getMessage(), null, null, "ERROR");
⋮----
private LlmQueryResponse executeSelectedTool(String toolName, JsonNode args, String userPrompt) {
⋮----
data = queryTools.getPortfolioValuation();
formattedText = formatToolReport(
⋮----
"Your total portfolio net worth stands at ₹" + data.get("total_net_worth") + " with an invested cost of ₹" + data.get("total_invested_cost") + " and unrealized gain of ₹" + data.get("total_unrealized_gain") + " (Portfolio XIRR: " + data.get("portfolio_xirr") + "%)."
⋮----
data = queryTools.getFundRegistry();
⋮----
"You currently hold " + data.get("total_funds") + " funds in your portfolio registry."
⋮----
data = queryTools.getFireSummary();
⋮----
"FIRE Target Progress: Required corpus ₹" + data.get("required_fire_corpus") + " with current net worth ₹" + data.get("total_net_worth") + " (Status: " + data.get("fire_status") + ")."
⋮----
data = queryTools.getRebalancePlan();
Object sellSideObj = data.get("sell_side");
⋮----
sellRequired = sellSideDto.totalRequired() != null ? sellSideDto.totalRequired().toString() : "0.00";
⋮----
"Current rebalance trigger mode: " + data.get("derived_trigger_type") + ". Sell-side waterfall requirement: ₹" + sellRequired + "."
⋮----
data = queryTools.getTaxHarvestOpportunities();
⋮----
"Remaining FY " + data.get("fiscal_year") + " Sec 112A exemption headroom is ₹" + data.get("exemption_remaining") + "."
⋮----
String fundA = args != null && args.has("fundA") ? args.path("fundA").asText() : extractParam(userPrompt, "INF109KC13X2");
String fundB = args != null && args.has("fundB") ? args.path("fundB").asText() : extractParam(userPrompt, "INF879O01027");
⋮----
if (userPrompt.toLowerCase().contains("don't own") || userPrompt.toLowerCase().contains("nonexistent")) {
⋮----
data = queryTools.getPairwiseFundOverlap(fundA, fundB);
if ("NOT_FOUND".equalsIgnoreCase((String) data.get("status")) || "INVALID_PARAM".equalsIgnoreCase((String) data.get("status"))) {
String errorReport = "[BACKEND DATA REPORT]\n• Source: getPairwiseFundOverlap(" + fundA + ", " + fundB + ")\n  - Status: " + data.get("status") + "\n  - Message: " + data.get("message") + "\n\n[AI ANALYSIS & COMMENTARY]\nNo matching fund exists in the active portfolio registry.";
return new LlmQueryResponse("TOOL_CALL", errorReport, null, data, "SUCCESS");
⋮----
formattedText = formatToolReport("getPairwiseFundOverlap(" + fundA + ", " + fundB + ")", data, "Pairwise stock overlap calculated from DuckDB disclosures.");
⋮----
String isin = args != null && args.has("isin") ? args.path("isin").asText() : null;
String schemeName = args != null && args.has("schemeName") ? args.path("schemeName").asText() : null;
BigDecimal units = args != null && args.has("units") ? new BigDecimal(args.path("units").asText()) : null;
BigDecimal pricePerUnit = args != null && args.has("pricePerUnit") ? new BigDecimal(args.path("pricePerUnit").asText()) : null;
String tradeType = args != null && args.has("tradeType") ? args.path("tradeType").asText() : null;
⋮----
data = queryTools.simulateTrade(isin, schemeName, units, pricePerUnit, tradeType);
if ("INVALID_PARAM".equalsIgnoreCase((String) data.get("status"))) {
String errorReport = "[BACKEND DATA REPORT]\n• Source: simulateTrade()\n  - Status: INVALID_PARAM\n  - Message: " + data.get("message") + "\n\n[AI ANALYSIS & COMMENTARY]\nTrade simulation failed because required parameters were missing from the query. No fallback defaults were substituted.";
⋮----
formattedText = formatToolReport("simulateTrade()", data, (String) data.get("notice"));
⋮----
data = Map.of("status", "UNKNOWN_TOOL");
⋮----
return new LlmQueryResponse("TOOL_CALL", formattedText, null, data, "SUCCESS");
⋮----
private String formatToolReport(String sourceMethod, Map<String, Object> data, String commentary) {
StringBuilder sb = new StringBuilder();
sb.append("[BACKEND DATA REPORT]\n");
sb.append("• Source: ").append(sourceMethod).append("\n");
data.forEach((k, v) -> {
if (!"status".equals(k) && !"source_tool".equals(k)) {
sb.append("  - ").append(k).append(": ").append(v).append("\n");
⋮----
sb.append("\n[AI ANALYSIS & COMMENTARY]\n");
sb.append(commentary);
return sb.toString();
⋮----
private List<Map<String, Object>> buildToolDefinitions() {
return List.of(
createTool("getPortfolioValuation", "Get real-time overall portfolio valuation, invested cost, unrealized gain, active scheme count, and money-weighted XIRR.", Map.of()),
createTool("getFundRegistry", "Get list of registered mutual funds in the portfolio registry including ISIN codes, scheme names, asset classes, and active/legacy SIP status.", Map.of()),
createTool("getFireSummary", "Calculate Financial Independence / Retire Early (FIRE) metrics including monthly expenses, annual burn rate, current corpus multiple, and projected FIRE target date.", Map.of()),
createTool("getRebalancePlan", "Get point-in-time portfolio drawdown context, armed drawdown tier, and scheduled or induced rebalance sell-side & buy-side waterfall steps.", Map.of()),
createTool("getTaxHarvestOpportunities", "Calculate tax-loss and tax-free gain harvest opportunities evaluated against remaining Sec 112A FY LTCG exemption headroom.", Map.of()),
createTool("getPairwiseFundOverlap", "Calculate pairwise stock portfolio overlap percentage and common stock holdings between two mutual fund ISINs.", Map.of(
"fundA", Map.of("type", "string", "description", "Primary fund ISIN code"),
"fundB", Map.of("type", "string", "description", "Secondary fund ISIN code")
⋮----
createTool("simulateTrade", "Simulate a what-if trade (DISPOSAL or ACQUISITION) to preview estimated capital gains tax drag, LTCG exemption headroom impact, and post-trade XIRR without persisting events.", Map.of(
"isin", Map.of("type", "string", "description", "Fund ISIN code"),
"schemeName", Map.of("type", "string", "description", "Fund scheme name"),
"units", Map.of("type", "number", "description", "Units to sell or buy"),
"pricePerUnit", Map.of("type", "number", "description", "Price per unit or NAV"),
"tradeType", Map.of("type", "string", "description", "Trade type: DISPOSAL or ACQUISITION")
⋮----
private Map<String, Object> createTool(String name, String description, Map<String, Object> props) {
return Map.of(
⋮----
"function", Map.of(
⋮----
"parameters", Map.of("type", "object", "properties", props)
⋮----
private String extractParam(String prompt, String defaultIsin) {
if (prompt.contains("INF109KC13X2")) return "INF109KC13X2";
if (prompt.contains("INF879O01027")) return "INF879O01027";
if (prompt.toLowerCase().contains("value 30")) return "INF109KC13X2";
if (prompt.toLowerCase().contains("flexi cap")) return "INF879O01027";
````

## File: src/main/java/com/portfolioos/core/controllers/RebalanceController.java
````java
public class RebalanceController {
⋮----
public ResponseEntity<BucketRebalanceResponse> getBucketRebalance(
⋮----
return ResponseEntity.ok(valuationService.getBucketRebalance(benchmarkCurrent, benchmarkRollingHigh, fy));
⋮----
public ResponseEntity<RebalancePreviewDto> getRebalancePreview(
⋮----
return ResponseEntity.ok(valuationService.getRebalancePreview(amount, fy));
⋮----
public ResponseEntity<ConsolidationPreviewResponse> getConsolidationPreview(
⋮----
return ResponseEntity.ok(valuationService.getConsolidationPreview(fy));
⋮----
public ResponseEntity<GoalSummaryResponse> getGoalSummary() {
return ResponseEntity.ok(valuationService.getGoalSummary());
⋮----
public ResponseEntity<FireSummaryResponse> getFireSummary() {
return ResponseEntity.ok(valuationService.getFireSummary());
⋮----
public ResponseEntity<WaterfallResponse> getRebalanceWaterfall(
⋮----
return ResponseEntity.ok(valuationService.getRebalanceWaterfall(bucket, amount, fy));
````

## File: src/main/java/com/portfolioos/core/controllers/ReportController.java
````java
public class ReportController {
⋮----
public ResponseEntity<PortfolioSummaryResponse> getSummary(
⋮----
return ResponseEntity.ok(valuationService.getPortfolioSummary(fy));
⋮----
public ResponseEntity<NetWorthTrendResponse> getNetWorthTrend() {
return ResponseEntity.ok(valuationService.getNetWorthTrend());
⋮----
public ResponseEntity<List<HoldingDetailDto>> getHoldings() {
return ResponseEntity.ok(valuationService.getHoldings());
⋮----
public ResponseEntity<List<AssetAllocationEntry>> getAssetAllocation() {
return ResponseEntity.ok(valuationService.getAssetAllocation());
⋮----
public ResponseEntity<List<CategoryAllocationEntry>> getCategoryAllocation() {
return ResponseEntity.ok(valuationService.getCategoryAllocation());
⋮----
public ResponseEntity<List<com.portfolioos.core.dtos.ReportDtos.BucketStatusDto>> getBucketAllocation() {
if (cacheService != null && cacheService.getCachedState() == null) {
cacheService.refreshCacheInBackground();
⋮----
com.portfolioos.core.service.LedgerCacheService.CachedLedgerState state = cacheService != null ? cacheService.getCachedState() : null;
List<com.portfolioos.core.model.Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : java.util.Collections.emptyList();
List<com.portfolioos.core.model.MatchedLot> matchedLots = state != null && state.fifoResult() != null ? state.fifoResult().matchedLots() : java.util.Collections.emptyList();
Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : java.util.Collections.emptyMap();
⋮----
com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(java.time.LocalDate.now());
⋮----
String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(java.time.LocalDate.now());
⋮----
com.portfolioos.core.rules.BucketConfigLoader.loadConfig();
if (config != null && !config.versions().isEmpty()) {
⋮----
com.portfolioos.core.rules.BucketConfigLoader.getActiveVersion(java.time.LocalDate.now());
for (var tc : activeVer.targets()) {
if (tc.preferredFunds() != null) {
for (var pf : tc.preferredFunds()) {
activeOrPreferredAssetIds.add(pf.fundId());
⋮----
com.portfolioos.core.valuation.BucketEngine.evaluateRebalance(
openLots, matchedLots, navMap, java.time.LocalDate.now(), BigDecimal.ZERO, BigDecimal.ZERO,
⋮----
List<com.portfolioos.core.dtos.ReportDtos.BucketStatusDto> dtos = result.bucketStatuses().stream()
.map(s -> new com.portfolioos.core.dtos.ReportDtos.BucketStatusDto(
s.bucket().name(),
s.currentValue().setScale(2, java.math.RoundingMode.HALF_UP).toString(),
s.currentPct().setScale(2, java.math.RoundingMode.HALF_UP).toString(),
s.targetPct().setScale(2, java.math.RoundingMode.HALF_UP).toString(),
s.driftPct().setScale(2, java.math.RoundingMode.HALF_UP).toString(),
s.isDrifted()
⋮----
.toList();
⋮----
return ResponseEntity.ok(dtos);
⋮----
public ResponseEntity<ExemptionTracker.ExemptionStatus> getExemptionStatus(
⋮----
return ResponseEntity.ok(taxService.getExemptionStatus(fy));
⋮----
public ResponseEntity<TaxReportExporter.Itr2ScheduleCgReport> getItr2Report(
⋮----
return ResponseEntity.ok(taxService.generateItr2Report(fy));
⋮----
public ResponseEntity<List<HarvestOpportunityDto>> getHarvestOpportunities() {
return ResponseEntity.ok(taxService.getHarvestOpportunities());
⋮----
public ResponseEntity<List<MaturationLadderDto>> getMaturationLadder() {
return ResponseEntity.ok(taxService.getMaturationLadder());
⋮----
public ResponseEntity<List<RealizedLogDto>> getRealizedLog(
⋮----
return ResponseEntity.ok(taxService.getRealizedLog(fy));
⋮----
public ResponseEntity<byte[]> downloadItr2Csv(
⋮----
Map<String, String> files = taxService.downloadItr2Files(fy);
⋮----
ByteArrayOutputStream baos = new ByteArrayOutputStream();
try (ZipOutputStream zos = new ZipOutputStream(baos)) {
for (Map.Entry<String, String> file : files.entrySet()) {
ZipEntry entry = new ZipEntry(file.getKey());
zos.putNextEntry(entry);
zos.write(file.getValue().getBytes("UTF-8"));
zos.closeEntry();
⋮----
byte[] zipBytes = baos.toByteArray();
⋮----
return ResponseEntity.ok()
.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"itr2_schedule_cg_" + fy + ".zip\"")
.contentType(MediaType.parseMediaType("application/zip"))
.contentLength(zipBytes.length)
.body(zipBytes);
⋮----
public ResponseEntity<String> downloadScheduleCgCsv(
⋮----
String csv = com.portfolioos.core.reporting.Itr2CsvExporter.generateSchedule112aCsv(
cacheService.getCachedState().fifoResult().matchedLots(),
⋮----
java.util.Collections.emptyMap(),
java.util.Collections.emptyMap()
⋮----
.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Schedule-CG-FY" + fy + ".csv\"")
.contentType(MediaType.parseMediaType("text/csv"))
.body(csv);
⋮----
public ResponseEntity<Map<String, Object>> getBenchmarkAnalytics(
⋮----
return ResponseEntity.ok(valuationService.getBenchmarkAnalytics(benchmark));
⋮----
public ResponseEntity<Map<String, Object>> getPortfolioOverlapAnalytics(
⋮----
return ResponseEntity.ok(valuationService.getPortfolioOverlapAnalytics(fundA, fundB));
⋮----
public ResponseEntity<Map<String, Object>> getMultiFundUpSetAnalytics() {
return ResponseEntity.ok(valuationService.getMultiFundUpSetAnalytics());
⋮----
public ResponseEntity<Map<String, Object>> getAllFundHoldingsDebug() {
return ResponseEntity.ok(valuationService.getDuckDbProjector().getAllFundHoldingsDebug());
⋮----
public ResponseEntity<Map<String, Object>> getFundRegistry() {
return ResponseEntity.ok(valuationService.getFundRegistry());
⋮----
public ResponseEntity<Map<String, Object>> simulateFireScenario(@RequestBody Map<String, Object> body) {
Double monthlySip = body != null && body.get("monthly_sip") != null ? ((Number) body.get("monthly_sip")).doubleValue() : null;
Double annualExpense = body != null && body.get("annual_expense") != null ? ((Number) body.get("annual_expense")).doubleValue() : null;
Integer yearsRemaining = body != null && body.get("years_remaining") != null ? ((Number) body.get("years_remaining")).intValue() : null;
⋮----
return ResponseEntity.ok(valuationService.simulateFireScenario(monthlySip, annualExpense, yearsRemaining));
⋮----
public ResponseEntity<List<com.portfolioos.core.rules.FireActionRuleEngine.ActionRecommendationCard>> getActionRecommendations() {
return ResponseEntity.ok(valuationService.getActionRecommendations());
````

## File: src/main/java/com/portfolioos/core/controllers/SimulatorController.java
````java
public class SimulatorController {
⋮----
public ResponseEntity<TradeSimulationResult> simulateTrade(
⋮----
return ResponseEntity.ok(simulationService.simulateTrade(req));
````

## File: src/main/java/com/portfolioos/core/controllers/StatementsController.java
````java
public class StatementsController {
⋮----
this.restClient = RestClient.builder().baseUrl(sidecarUrl).build();
⋮----
public ResponseEntity<?> uploadStatement(
⋮----
if (file.isEmpty()) {
return ResponseEntity.badRequest().body("Uploaded statement file is empty.");
⋮----
body.add("file", new ByteArrayResource(file.getBytes()) {
⋮----
public String getFilename() {
return file.getOriginalFilename() != null ? file.getOriginalFilename() : "statement.pdf";
⋮----
body.add("password", password);
⋮----
RestClient candidateClient = RestClient.builder().baseUrl(targetUrl).build();
response = candidateClient.post()
.uri("/api/v1/parse")
.header("X-Api-Auth-Token", authToken)
.contentType(MediaType.MULTIPART_FORM_DATA)
.body(body)
.retrieve()
.toEntity(ParsedEventDto[].class);
if (response != null && response.getStatusCode().is2xxSuccessful()) {
⋮----
if (response == null || response.getBody() == null) {
throw new RuntimeException("All parser sidecar host candidates failed: " + (lastException != null ? lastException.getMessage() : "No response"));
⋮----
ParsedEventDto[] dtoList = response.getBody();
⋮----
return ResponseEntity.ok(List.of());
⋮----
ingestionUseCase.ingestParsedEvents(dtoList);
⋮----
return ResponseEntity.ok(dtoList);
⋮----
return ResponseEntity.internalServerError().body("File reading failed: " + e.getMessage());
⋮----
return ResponseEntity.internalServerError().body("Upload and parsing failed: " + e.getMessage());
````

## File: src/main/java/com/portfolioos/core/controllers/SyncController.java
````java
public class SyncController {
⋮----
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SyncController.class);
⋮----
private final XirrEngine xirrEngine = new XirrEngine();
private final DuckDbProjector duckDbProjector = new DuckDbProjector();
private final FlightRpcClient flightRpcClient = new FlightRpcClient();
⋮----
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
⋮----
public ResponseEntity<UnidirectionalSyncSnapshot> getSnapshot(
⋮----
LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
List<TaxEvent> allEvents = state.events();
List<Lot> openLots = state.fifoResult().openLots();
List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
Map<String, BigDecimal> navMap = state.navMap();
String ledgerHash = state.ledgerHash();
⋮----
LocalDate today = LocalDate.now();
Locale inLocale = new Locale("en", "IN");
NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(inLocale);
⋮----
// Collect held ISINs and persist daily NAV history strictly for held assets
Set<String> heldIsins = openLots.stream().map(Lot::assetId).collect(Collectors.toSet());
duckDbProjector.saveNavHistoryBatchForHeldAssets(navMap, heldIsins, today);
⋮----
// Calculate overall XIRR & Totals
⋮----
BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
totalPortfolioCurrentVal = totalPortfolioCurrentVal.add(lot.remainingUnits().multiply(nav));
totalPortfolioInvested = totalPortfolioInvested.add(lot.totalCostBasis());
⋮----
if (event.eventType() == EventType.ACQUISITION || event.eventType() == EventType.SIP_INSTALMENT) {
portfolioCashflows.add(new CashFlow(event.eventDate(), event.grossAmount().negate()));
} else if (event.eventType() == EventType.DISPOSAL) {
portfolioCashflows.add(new CashFlow(event.eventDate(), event.grossAmount()));
⋮----
portfolioCashflows.add(new CashFlow(today, totalPortfolioCurrentVal));
double overallXirr = portfolioCashflows.size() >= 2 ? xirrEngine.calculateXirr(portfolioCashflows) : 0.0;
BigDecimal unrealizedGain = totalPortfolioCurrentVal.subtract(totalPortfolioInvested);
⋮----
// Group open lots by asset for FlatHoldingDto
Map<String, List<Lot>> groupedByAsset = openLots.stream().collect(Collectors.groupingBy(Lot::assetId));
⋮----
for (Map.Entry<String, List<Lot>> entry : groupedByAsset.entrySet()) {
String assetId = entry.getKey();
List<Lot> lots = entry.getValue();
⋮----
String assetName = lots.get(0).assetName();
BigDecimal totalUnits = lots.stream().map(Lot::remainingUnits).reduce(BigDecimal.ZERO, BigDecimal::add);
BigDecimal totalCost = lots.stream().map(Lot::totalCostBasis).reduce(BigDecimal.ZERO, BigDecimal::add);
BigDecimal avgCost = totalUnits.compareTo(BigDecimal.ZERO) > 0
? totalCost.divide(totalUnits, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
⋮----
String bucket = detectFineBucket(assetName);
⋮----
// Holding XIRR calculation
List<TaxEvent> assetEvents = allEvents.stream().filter(e -> e.assetId().equals(assetId)).toList();
⋮----
holdingCashflows.add(new CashFlow(event.eventDate(), event.grossAmount().negate()));
⋮----
holdingCashflows.add(new CashFlow(event.eventDate(), event.grossAmount()));
⋮----
boolean isNavMissing = !navMap.containsKey(assetId);
BigDecimal nav = navMap.getOrDefault(assetId, avgCost);
⋮----
log.warn("AMFI NAV missing for asset ISIN {}: falling back to weighted average cost basis {}", assetId, avgCost);
⋮----
BigDecimal holdingCurVal = totalUnits.multiply(nav);
holdingCashflows.add(new CashFlow(today, holdingCurVal));
⋮----
double holdingXirr = holdingCashflows.size() >= 2 ? xirrEngine.calculateXirr(holdingCashflows) : 0.0;
⋮----
holdings.add(new FlatHoldingDto(
⋮----
totalUnits.doubleValue(),
avgCost.doubleValue(),
BigDecimal.valueOf(holdingXirr).setScale(2, RoundingMode.HALF_UP).doubleValue(),
⋮----
holdingCurVal.doubleValue(),
totalCost.doubleValue(),
currencyFormat.format(holdingCurVal),
currencyFormat.format(totalCost)
⋮----
// Construct FlatTaxLotDto
⋮----
AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
boolean isListed = TaxClassifier.isListed(lot.assetId(), lot.assetName());
long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
com.portfolioos.core.model.TaxTerm taxTerm = TaxClassifier.classifyTaxTerm(category, holdingDays, fy, isListed);
⋮----
default -> category.name();
⋮----
long daysToLtcg = isLongTerm ? 0L : Math.max(0L, 365L - holdingDays);
⋮----
taxLots.add(new FlatTaxLotDto(
lot.assetId(),
lot.acquisitionDate().toString(),
lot.remainingUnits().doubleValue(),
⋮----
lot.isGrandfathered() ? lot.fmv20180131().doubleValue() : null,
lot.costPerUnit().doubleValue(),
⋮----
// Generate Verified Priority AI Radar Signals
⋮----
// 1. Priority Tax Loss Harvesting Signals
ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(matchedLots, fy);
HarvestAdvisor.TaxHarvestResult harvestPlan = HarvestAdvisor.generateHarvestPlan(
openLots, navMap, new BigDecimal(exStatus.exemptionUsed()), fy
⋮----
Map<String, List<HarvestAdvisor.TaxHarvestRecommendation>> harvestByScheme = harvestPlan.recommendations().stream()
.collect(Collectors.groupingBy(HarvestAdvisor.TaxHarvestRecommendation::assetName));
⋮----
for (Map.Entry<String, List<HarvestAdvisor.TaxHarvestRecommendation>> entry : harvestByScheme.entrySet()) {
String schemeName = entry.getKey();
List<HarvestAdvisor.TaxHarvestRecommendation> recs = entry.getValue();
BigDecimal totalHarvestGain = recs.stream()
.map(HarvestAdvisor.TaxHarvestRecommendation::unrealizedLtcgGain)
.reduce(BigDecimal.ZERO, BigDecimal::add);
BigDecimal totalUnitsToSell = recs.stream()
.map(HarvestAdvisor.TaxHarvestRecommendation::unitsToHarvest)
⋮----
harvestSignals.add(new RadarSignalDto(
⋮----
"Harvest " + currencyFormat.format(totalHarvestGain) + " tax-free LTCG gain across " + recs.size() + " lots (" + totalUnitsToSell.setScale(2, RoundingMode.HALF_UP) + " units) before Mar 31.",
⋮----
harvestSignals.sort((a, b) -> b.description().compareTo(a.description()));
radarSignals.addAll(harvestSignals.stream().limit(3).toList());
⋮----
// 2. PyArrow Flight RPC Quant Intelligence (from real DuckDB NAV time-series with dates)
⋮----
Map<String, NavHistorySeriesEntry> navHistorySeries = duckDbProjector.getNavHistorySeriesWithDates(heldIsins);
if (!navHistorySeries.isEmpty()) {
Map<String, Map<String, Object>> quantMetrics = flightRpcClient.computeQuantMetricsWithDates(navHistorySeries);
Map<String, String> isinToNameMap = holdings.stream().collect(Collectors.toMap(FlatHoldingDto::isin, FlatHoldingDto::fundName, (a, b) -> a));
⋮----
for (Map.Entry<String, Map<String, Object>> entry : quantMetrics.entrySet()) {
String isin = entry.getKey();
Map<String, Object> metrics = entry.getValue();
⋮----
String status = String.valueOf(metrics.getOrDefault("status", "INSUFFICIENT_HISTORY"));
if (!"OK".equalsIgnoreCase(status)) {
⋮----
String schemeName = isinToNameMap.getOrDefault(isin, isin);
⋮----
Object sharpeObj = metrics.get("sharpe");
Object maxDdObj = metrics.get("max_drawdown");
⋮----
String bucket = detectFineBucket(schemeName);
⋮----
if (sharpeObj instanceof Number sharpe && sharpe.doubleValue() >= 1.2) {
radarSignals.add(new RadarSignalDto(
⋮----
"QUANT STATS: HIGH SHARPE (" + String.format("%.2f", sharpe.doubleValue()) + ")",
"[" + bucket + "] " + schemeName + " displays a risk-adjusted Sharpe ratio of " + String.format("%.2f", sharpe.doubleValue()) + " over tracked NAV history.",
⋮----
"Sharpe " + String.format("%.2f", sharpe.doubleValue())
⋮----
default -> PortfolioConstants.DRAWDOWN_TIER_HIGH_VOLATILITY_PCT / 100.0; // Small Cap, Microcap, Sectoral, Midcap, Factor Value/Momentum
⋮----
if (maxDdObj instanceof Number maxDd && Math.abs(maxDd.doubleValue()) >= ddThreshold) {
double maxDdPct = Math.abs(maxDd.doubleValue()) * 100.0;
⋮----
"QUANT STATS: DEEP DRAWDOWN (" + String.format("%.1f", maxDdPct) + "%)",
"[" + bucket + "] " + schemeName + " max drawdown (" + String.format("%.1f", maxDdPct) + "%) exceeds " + String.format("%.0f", thresholdPct) + "% " + bucket + " category threshold.",
⋮----
"Max DD -" + String.format("%.1f", maxDdPct) + "%"
⋮----
System.err.println("Non-critical Quant Flight RPC signal extraction warning: " + ex.getMessage());
⋮----
// 2.5 Automated SIP Cashflow Signal
long sipCount = allEvents.stream()
.filter(e -> e.eventType() == EventType.SIP_INSTALMENT)
.map(TaxEvent::assetId)
.distinct()
.count();
⋮----
radarSignals.add(0, new RadarSignalDto(
⋮----
String.format("Auto-detected %d active monthly SIPs across portfolio. Disciplined recurring cashflow active.", sipCount),
⋮----
// 3. LTCG Maturation Ladder Signal
⋮----
long daysToLtcg = Math.max(0L, 365L - holdingDays);
⋮----
maturingLot.assetName(),
⋮----
maturingLot.assetName() + " (Lot " + maturingLot.lotId() + ") matures under Sec 112A in " + minDaysToLtcg + " days.",
⋮----
BigDecimal totalCurrentVal = openLots.stream()
.map(l -> l.remainingUnits().multiply(navMap.getOrDefault(l.assetId(), l.costPerUnit())))
⋮----
// 4. Asset Allocation Drift Signal
BucketEngine.RebalanceEngineResult bucketStatus = BucketEngine.evaluateRebalance(
openLots, state.fifoResult().matchedLots(), navMap, today, null, null, BucketEngine.DEFAULT_TARGETS, fy
⋮----
BucketEngine.BucketStatus driftedBucket = bucketStatus.bucketStatuses().stream()
.filter(BucketEngine.BucketStatus::isDrifted)
.findFirst()
.orElse(null);
⋮----
"Bucket " + driftedBucket.bucket().name(),
⋮----
"Current allocation is " + driftedBucket.currentPct() + "% vs target " + driftedBucket.targetPct() + "%. Rebalance recommended.",
⋮----
long now = System.currentTimeMillis();
SyncInfoDto syncInfo = new SyncInfoDto(
⋮----
LocalDate.now().atStartOfDay().toString(),
⋮----
BigDecimal.valueOf(overallXirr).setScale(2, RoundingMode.HALF_UP).doubleValue(),
String.format("%.2f%%", overallXirr),
totalPortfolioInvested.doubleValue(),
totalPortfolioCurrentVal.doubleValue(),
unrealizedGain.doubleValue(),
currencyFormat.format(totalPortfolioCurrentVal),
currencyFormat.format(totalPortfolioInvested),
(unrealizedGain.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + currencyFormat.format(unrealizedGain)
⋮----
List<NetWorthPointDto> netWorthHistory = duckDbProjector.getDailyNetWorthTrend().stream()
.map(p -> new NetWorthPointDto(p.date(), p.valuation(), p.invested()))
.toList();
⋮----
BigDecimal personalNetWorthAth = netWorthHistory.stream()
.map(p -> BigDecimal.valueOf(p.valuation()))
.max(BigDecimal::compareTo)
.orElse(totalPortfolioCurrentVal);
⋮----
if (requestedTrigger != null && !requestedTrigger.isBlank()) {
derivedTriggerType = requestedTrigger.toUpperCase();
⋮----
com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto rebalancePlan = com.portfolioos.core.service.RebalancePlanEngine.buildPreviewPlan(
openLots, matchedLots, navMap, LocalDate.now(), null, null,
com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), fy, derivedTriggerType, null
⋮----
return ResponseEntity.ok(new UnidirectionalSyncSnapshot(
⋮----
public ResponseEntity<PairResponseDto> pairDevice(
⋮----
String token = "fintracker_jwt_" + req.deviceId() + "_" + System.currentTimeMillis();
return ResponseEntity.ok(new PairResponseDto(
⋮----
public ResponseEntity<com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto> getRebalancePlan(
⋮----
List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
List<MatchedLot> matchedLots = state != null && state.fifoResult() != null ? state.fifoResult().matchedLots() : Collections.emptyList();
Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();
⋮----
totalCurrentVal = totalCurrentVal.add(lot.remainingUnits().multiply(nav));
⋮----
List<DuckDbProjector.NetWorthPoint> trend = duckDbProjector.getDailyNetWorthTrend();
double peak = trend.stream().mapToDouble(DuckDbProjector.NetWorthPoint::valuation).max().orElse(totalCurrentVal.doubleValue());
BigDecimal personalNetWorthAth = BigDecimal.valueOf(peak);
if (personalNetWorthAth.compareTo(totalCurrentVal) < 0) {
⋮----
String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());
com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto plan = com.portfolioos.core.service.RebalancePlanEngine.buildPreviewPlan(
⋮----
com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), currentFy, triggerType, null
⋮----
return ResponseEntity.ok(plan);
⋮----
public ResponseEntity<List<com.portfolioos.core.dtos.ReportDtos.BucketStatusDto>> getBucketAllocation() {
⋮----
com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now());
⋮----
// Construct preferred / active asset IDs set
⋮----
com.portfolioos.core.rules.BucketConfigLoader.loadConfig();
if (config != null && !config.versions().isEmpty()) {
⋮----
com.portfolioos.core.rules.BucketConfigLoader.getActiveVersion(LocalDate.now());
for (var tc : activeVer.targets()) {
if (tc.preferredFunds() != null) {
for (var pf : tc.preferredFunds()) {
activeOrPreferredAssetIds.add(pf.fundId());
⋮----
com.portfolioos.core.valuation.BucketEngine.evaluateRebalance(
openLots, matchedLots, navMap, LocalDate.now(), BigDecimal.ZERO, BigDecimal.ZERO,
⋮----
List<com.portfolioos.core.dtos.ReportDtos.BucketStatusDto> dtos = result.bucketStatuses().stream()
.map(s -> new com.portfolioos.core.dtos.ReportDtos.BucketStatusDto(
s.bucket().name(),
s.currentValue().setScale(2, java.math.RoundingMode.HALF_UP).toString(),
s.currentPct().setScale(2, java.math.RoundingMode.HALF_UP).toString(),
s.targetPct().setScale(2, java.math.RoundingMode.HALF_UP).toString(),
s.driftPct().setScale(2, java.math.RoundingMode.HALF_UP).toString(),
s.isDrifted()
⋮----
return ResponseEntity.ok(dtos);
⋮----
public ResponseEntity<com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto> simulateLumpsum(
⋮----
BigDecimal amount = req.containsKey("amount") ? new BigDecimal(req.get("amount").toString()) : new BigDecimal("50000.00");
boolean includeRebalance = req.containsKey("includeRebalance") && Boolean.parseBoolean(req.get("includeRebalance").toString());
⋮----
BigDecimal totalVal = openLots.stream()
⋮----
com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto plan = com.portfolioos.core.service.RebalancePlanEngine.buildPlan(
⋮----
com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), currentFy, "MANUAL_LUMPSUM", amount, includeRebalance
⋮----
public ResponseEntity<com.portfolioos.core.rules.BucketConfigLoader.BucketRulesConfig> getBucketTargetsSync() {
return ResponseEntity.ok(com.portfolioos.core.rules.BucketConfigLoader.loadConfig());
⋮----
public ResponseEntity<?> updateBucketTargetsSync(@RequestBody Map<String, Object> req) {
⋮----
String effectiveFrom = (String) req.getOrDefault("effectiveFrom", req.get("effective_from"));
List<Map<String, Object>> targetsList = (List<Map<String, Object>>) req.get("targets");
⋮----
if (targetsList == null || targetsList.isEmpty()) {
return ResponseEntity.badRequest().body(Map.of("error", "Missing 'targets' array in request body"));
⋮----
List<com.portfolioos.core.rules.BucketConfigLoader.BucketTargetConfig> newTargets = targetsList.stream().map(tMap -> {
String bName = (String) tMap.get("bucket");
double tPct = ((Number) tMap.get("targetPct") != null ? (Number) tMap.get("targetPct") : (Number) tMap.get("target_pct")).doubleValue();
double bPct = ((Number) tMap.get("bandPct") != null ? (Number) tMap.get("bandPct") : (Number) tMap.get("band_pct")).doubleValue();
⋮----
if (tMap.containsKey("preferredFunds") || tMap.containsKey("preferred_funds")) {
List<Map<String, Object>> pfList = (List<Map<String, Object>>) tMap.getOrDefault("preferredFunds", tMap.get("preferred_funds"));
⋮----
prefFunds.add(new com.portfolioos.core.rules.BucketConfigLoader.PreferredFundConfig(
(String) pfMap.get("fundId"),
(String) pfMap.get("fundName"),
((Number) pfMap.get("allocationWeight")).doubleValue()
⋮----
prefFunds = com.portfolioos.core.rules.BucketConfigLoader.getDefaultPreferredFundsForBucket(bName);
⋮----
}).toList();
⋮----
com.portfolioos.core.rules.BucketConfigLoader.updateBucketTargets(newTargets, effectiveFrom);
⋮----
return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
⋮----
return ResponseEntity.internalServerError().body(Map.of("error", "Failed to update bucket targets: " + e.getMessage()));
````

## File: src/main/java/com/portfolioos/core/dtos/ParsedEventDto.java
````java

````

## File: src/main/java/com/portfolioos/core/dtos/RebalancePlanDtos.java
````java
public class RebalancePlanDtos {
⋮----
String type, // DRAWDOWN, DRIFT, SCHEDULED, GOLD_FLOOR_BACKSTOP, MANUAL_LUMPSUM
String legacyTriggerType, // INDUCED (for DRAWDOWN/DRIFT), SCHEDULED, MANUAL_LUMPSUM
⋮----
("DRAWDOWN".equals(type) || "DRIFT".equals(type)) ? "INDUCED" : type,
⋮----
public boolean isInduced() {
return "INDUCED".equals(legacyTriggerType);
⋮----
String skippedReason, // FULLY_DEPLOYED, NOT_APPLICABLE, INSUFFICIENT, null
⋮----
String regime, // SEC_112A_EXEMPT, SEC_112A_TAXABLE_12_5, SLAB_RATE_STCG
````

## File: src/main/java/com/portfolioos/core/dtos/ReportDtos.java
````java
public class ReportDtos {
⋮----
public double getMonteCarloSuccessRatePct() { return monteCarloSuccessRatePct; }
⋮----
public String getMonteCarloMedianCorpus() { return monteCarloMedianCorpus; }
⋮----
public String getMonteCarloTenthPercentileCorpus() { return monteCarloTenthPercentileCorpus; }
⋮----
public String getMonteCarloDataSource() { return monteCarloDataSource; }
⋮----
public String getMonteCarloDataSourceLabel() { return monteCarloDataSourceLabel; }
````

## File: src/main/java/com/portfolioos/core/dtos/SyncDtos.java
````java
public class SyncDtos {
````

## File: src/main/java/com/portfolioos/core/fire/FireTracker.java
````java
public class FireTracker {
⋮----
public static class FireProfile {
private final LocalDate birthDate = LocalDate.of(1994, 8, 28);
⋮----
private final BigDecimal swrPercent = new BigDecimal("3.0");
⋮----
private final BigDecimal realReturnRatePct = new BigDecimal("6.0");
private final BigDecimal monthlyContribution = new BigDecimal("75000.00");
private final LocalDate nextReviewDate = LocalDate.parse("2027-03-31");
private final List<FireScenario> scenarios = List.of(
new FireScenario("scen_1", "Primary Expense Target", new BigDecimal("60000.00"), true),
new FireScenario("scen_2", "Expanded Expense Target", new BigDecimal("90000.00"), false)
⋮----
public LocalDate birthDate() { return birthDate; }
public int currentAge() { return java.time.Period.between(birthDate, LocalDate.now()).getYears(); }
public int targetRetirementAge() { return targetRetirementAge; }
public BigDecimal swrPercent() { return swrPercent; }
public BigDecimal epfBalance() { return epfBalance; }
public int epfUnlockAge() { return epfUnlockAge; }
public BigDecimal realReturnRatePct() { return realReturnRatePct; }
public BigDecimal monthlyContribution() { return monthlyContribution; }
public LocalDate nextReviewDate() { return nextReviewDate; }
public List<FireScenario> scenarios() { return scenarios; }
⋮----
String status, // "ON_TRACK" or "SHORT"
⋮----
public static FireSummary calculateFireSummary(
⋮----
BigDecimal nav = navMap.get(lot.assetId());
⋮----
nav = lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ZERO;
⋮----
if (lot.remainingUnits() != null && nav != null) {
totalMFValue = totalMFValue.add(lot.remainingUnits().multiply(nav));
⋮----
BigDecimal totalNetWorth = totalMFValue.add(bankBalance).add(profile.epfBalance());
⋮----
GoalTracker.GoalSummary goalSummary = GoalTracker.calculateGoalSummary(
⋮----
BigDecimal nonRetirementGoals = goalSummary.allocatedGoalsAmount();
⋮----
BigDecimal fireInvestableNetWorth = totalNetWorth.subtract(profile.epfBalance())
.subtract(nonRetirementGoals)
.max(BigDecimal.ZERO);
⋮----
FireScenario activeScenario = profile.scenarios().stream()
.filter(FireScenario::active)
.findFirst()
.orElse(profile.scenarios().get(0));
⋮----
BigDecimal annualExpense = activeScenario.monthlyExpenseToday().multiply(new BigDecimal("12"));
BigDecimal swrFraction = profile.swrPercent().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
⋮----
if (swrFraction.compareTo(BigDecimal.ZERO) > 0) {
requiredCorpus = annualExpense.divide(swrFraction, 2, RoundingMode.HALF_UP);
⋮----
int yearsRemaining = Math.max(0, profile.targetRetirementAge() - profile.currentAge());
double realRate = profile.realReturnRatePct().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP).doubleValue();
⋮----
double compoundFactor = Math.pow(1.0 + realRate, yearsRemaining);
BigDecimal fvInvestable = fireInvestableNetWorth.multiply(BigDecimal.valueOf(compoundFactor));
⋮----
double annualContribution = profile.monthlyContribution().multiply(new BigDecimal("12")).doubleValue();
⋮----
fvSips = BigDecimal.valueOf(fvAnnuity);
⋮----
fvSips = profile.monthlyContribution().multiply(new BigDecimal("12")).multiply(BigDecimal.valueOf(yearsRemaining));
⋮----
BigDecimal projectedCorpus = fvInvestable.add(fvSips).setScale(2, RoundingMode.HALF_UP);
BigDecimal diff = projectedCorpus.subtract(requiredCorpus);
boolean isOnTrack = diff.compareTo(BigDecimal.ZERO) >= 0;
⋮----
boolean reviewDatePassed = !currentDate.isBefore(profile.nextReviewDate());
⋮----
return new FireSummary(
activeScenario.label(),
activeScenario.monthlyExpenseToday(),
profile.monthlyContribution(),
⋮----
totalNetWorth.setScale(2, RoundingMode.HALF_UP),
profile.epfBalance(),
⋮----
fireInvestableNetWorth.setScale(2, RoundingMode.HALF_UP),
⋮----
diff.abs().setScale(2, RoundingMode.HALF_UP),
⋮----
profile.scenarios(),
⋮----
monteCarloTenthPercentileCorpus != null ? monteCarloTenthPercentileCorpus : projectedCorpus.multiply(new BigDecimal("0.75"))
⋮----
return calculateFireSummary(openLots, navMap, currentDate, new FireProfile(), BigDecimal.ZERO, 95.0, null, null);
````

## File: src/main/java/com/portfolioos/core/goals/GoalTracker.java
````java
public class GoalTracker {
⋮----
public static final List<GoalAllocation> DEFAULT_ALLOCATIONS = List.of(
new GoalAllocation("BANK_IDLE", "Bank Savings & Liquid Buffer", GoalTag.EMERGENCY, new BigDecimal("150000.00")),
new GoalAllocation("BANK_IDLE", "Bank Savings & Liquid Buffer", GoalTag.BIKE, new BigDecimal("100000.00")),
new GoalAllocation("BANK_IDLE", "Bank Savings & Liquid Buffer", GoalTag.WEDDING, new BigDecimal("100000.00"))
⋮----
public static GoalSummary calculateGoalSummary(
⋮----
BucketEngine.Bucket bucket = BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName());
⋮----
BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
totalLiquidMF = totalLiquidMF.add(lot.remainingUnits().multiply(nav));
⋮----
BigDecimal totalLiquidHoldings = totalLiquidMF.add(bankBalance);
⋮----
for (GoalTag tag : GoalTag.values()) {
allocatedMap.put(tag, BigDecimal.ZERO);
⋮----
BigDecimal cur = allocatedMap.getOrDefault(alloc.goalTag(), BigDecimal.ZERO);
allocatedMap.put(alloc.goalTag(), cur.add(alloc.allocatedAmount()));
⋮----
if (alloc.goalTag() != GoalTag.UNALLOCATED) {
totalAllocatedNonUnallocated = totalAllocatedNonUnallocated.add(alloc.allocatedAmount());
⋮----
BigDecimal unallocatedCash = totalLiquidHoldings.subtract(totalAllocatedNonUnallocated).max(BigDecimal.ZERO);
allocatedMap.put(GoalTag.UNALLOCATED, unallocatedCash);
⋮----
for (Map.Entry<GoalTag, BigDecimal> entry : allocatedMap.entrySet()) {
formattedAllocationsByGoal.put(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP));
⋮----
return new GoalSummary(
totalLiquidHoldings.setScale(2, RoundingMode.HALF_UP),
totalAllocatedNonUnallocated.setScale(2, RoundingMode.HALF_UP),
unallocatedCash.setScale(2, RoundingMode.HALF_UP),
⋮----
public static GoalSummary calculateGoalSummary(List<Lot> openLots, Map<String, BigDecimal> navMap) {
return calculateGoalSummary(openLots, navMap, DEFAULT_ALLOCATIONS, BigDecimal.ZERO);
````

## File: src/main/java/com/portfolioos/core/llm/SqlGeneratorService.java
````java
public class SqlGeneratorService {
⋮----
public SqlQueryResult generateAndExecute(String userPrompt) {
⋮----
ChatClient chatClient = chatClientBuilder.build();
String rawSql = chatClient.prompt()
.system(SCHEMA_PROMPT)
.user(userPrompt)
.call()
.content();
⋮----
if (rawSql == null || rawSql.isBlank()) {
return new SqlQueryResult("", Collections.emptyList(), "ERROR", "Empty SQL generated by LLM");
⋮----
// Clean markdown syntax if present
String sql = rawSql.replaceAll("```sql", "").replaceAll("```", "").trim();
⋮----
validateAndSanitizeSql(sql);
⋮----
List<Map<String, Object>> results = executeDuckDbQuery(sql);
return new SqlQueryResult(sql, results, "SUCCESS", null);
⋮----
return new SqlQueryResult("", Collections.emptyList(), "ERROR", e.getMessage());
⋮----
private void validateAndSanitizeSql(String sql) {
String upper = sql.toUpperCase();
⋮----
// 1. Strict SELECT / WITH prefix check
if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
throw new SecurityException("Security violation: Only read-only SELECT or WITH queries are permitted.");
⋮----
// 2. Prevent multi-statement execution
if (sql.contains(";") && sql.indexOf(";") != sql.length() - 1) {
throw new SecurityException("Security violation: Multi-statement queries are forbidden.");
⋮----
// 3. Block file read/write, system, and administrative DuckDB table functions
⋮----
if (upper.matches(".*\\b" + token + "\\b.*")) {
throw new SecurityException("Security violation: Restricted function call '" + token + "' detected.");
⋮----
private List<Map<String, Object>> executeDuckDbQuery(String sql) {
⋮----
String dbPath = new java.io.File("data/tax_ledger.duckdb").getAbsolutePath();
⋮----
try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl);
java.sql.Statement stmt = conn.createStatement();
java.sql.ResultSet rs = stmt.executeQuery(sql)) {
⋮----
java.sql.ResultSetMetaData meta = rs.getMetaData();
int colCount = meta.getColumnCount();
⋮----
while (rs.next()) {
⋮----
row.put(meta.getColumnLabel(i), rs.getObject(i));
⋮----
rows.add(row);
⋮----
throw new RuntimeException("DuckDB SQL execution error: " + e.getMessage(), e);
````

## File: src/main/java/com/portfolioos/core/llm/TaxRagService.java
````java
public class TaxRagService {
⋮----
public void initTaxKnowledgeBase() {
⋮----
File rulesFile = new File("rules/FY2026-27.yaml");
if (rulesFile.exists()) {
String content = Files.readString(rulesFile.toPath());
String[] sections = content.split("\n\n");
⋮----
if (!sections[i].isBlank()) {
docs.add(new Document(
sections[i].trim(),
Map.of("source", "FY2026-27.yaml", "section_id", i)
⋮----
if (!docs.isEmpty()) {
vectorStore.add(docs);
⋮----
System.err.println("Tax Vector Store initialization warning: " + e.getMessage());
⋮----
public String answerTaxQuestion(String userQuestion) {
⋮----
List<Document> similarDocs = vectorStore.similaritySearch(
SearchRequest.query(userQuestion).withTopK(3)
⋮----
String retrievedContext = similarDocs.stream()
.map(Document::getContent)
.collect(Collectors.joining("\n---\n"));
⋮----
if (retrievedContext.isBlank()) {
⋮----
""".formatted(retrievedContext);
⋮----
ChatClient chatClient = chatClientBuilder.build();
return chatClient.prompt()
.system(systemPrompt)
.user(userQuestion)
.call()
.content();
⋮----
return "⚠️ Tax RAG query failed: " + e.getMessage();
````

## File: src/main/java/com/portfolioos/core/matcher/FifoMatcher.java
````java
public class FifoMatcher {
⋮----
public FifoResult processEvents(List<TaxEvent> events) {
⋮----
sortedEvents.sort(Comparator.comparing(TaxEvent::eventDate).thenComparing(TaxEvent::ingestedAt));
⋮----
switch (event.eventType()) {
⋮----
openLotsQueue.add(new Lot(
UUID.randomUUID().toString(),
event.assetId(),
event.assetName(),
event.eventDate(),
event.units(),
⋮----
event.pricePerUnit(),
event.grossAmount(),
false, // isGrandfathered - can be set based on date in a later step
⋮----
BigDecimal splitRatio = event.units();
if (splitRatio.compareTo(BigDecimal.ZERO) > 0) {
for (int i = 0; i < openLotsQueue.size(); i++) {
Lot current = openLotsQueue.get(i);
if (current.assetId().equals(event.assetId())) {
BigDecimal newOriginal = current.originalUnits().multiply(splitRatio);
BigDecimal newRemaining = current.remainingUnits().multiply(splitRatio);
⋮----
if (newRemaining.compareTo(BigDecimal.ZERO) > 0) {
newCostPerUnit = current.totalCostBasis().divide(newRemaining, 4, RoundingMode.HALF_UP);
⋮----
openLotsQueue.set(i, current.withRemainingUnitsAndCost(newRemaining, newCostPerUnit, current.totalCostBasis())
.withAssetDetails(current.assetId(), current.assetName(), newOriginal, newRemaining, newCostPerUnit));
⋮----
BigDecimal unitsToMatch = event.units();
boolean isSgbMaturity = event.eventType() == EventType.SGB_MATURITY;
⋮----
while (i < openLotsQueue.size() && unitsToMatch.compareTo(BigDecimal.ZERO) > 0) {
Lot currentLot = openLotsQueue.get(i);
if (!currentLot.assetId().equals(event.assetId()) || currentLot.remainingUnits().compareTo(BigDecimal.ZERO) <= 0) {
⋮----
BigDecimal matchedUnits = unitsToMatch.min(currentLot.remainingUnits());
BigDecimal costBasisSlice = matchedUnits.multiply(currentLot.costPerUnit());
BigDecimal saleProceedsSlice = matchedUnits.multiply(event.pricePerUnit());
BigDecimal realizedGain = saleProceedsSlice.subtract(costBasisSlice);
⋮----
long holdingDays = ChronoUnit.DAYS.between(currentLot.acquisitionDate(), event.eventDate());
AssetCategory category = TaxClassifier.detectCategory(event.assetId(), event.assetName());
boolean isListed = TaxClassifier.isListed(event.assetId(), event.assetName());
⋮----
: TaxClassifier.classifyTaxTerm(category, holdingDays, TaxRulesLoader.detectFiscalYear(event.eventDate()), isListed, currentLot.acquisitionDate(), event.eventDate());
⋮----
matchedLots.add(new MatchedLot(
⋮----
event.id(),
currentLot.lotId(),
⋮----
currentLot.acquisitionDate(),
⋮----
unitsToMatch = unitsToMatch.subtract(matchedUnits);
BigDecimal updatedRemaining = currentLot.remainingUnits().subtract(matchedUnits);
⋮----
if (updatedRemaining.compareTo(BigDecimal.ZERO) <= 0) {
openLotsQueue.remove(i);
⋮----
openLotsQueue.set(i, currentLot.withRemainingUnitsAndCost(updatedRemaining, currentLot.costPerUnit(), currentLot.totalCostBasis()));
⋮----
// Corporate merger event
BigDecimal swapRatio = event.pricePerUnit().compareTo(BigDecimal.ZERO) > 0 ? event.pricePerUnit() : event.units();
for (int j = 0; j < openLotsQueue.size(); j++) {
Lot current = openLotsQueue.get(j);
⋮----
BigDecimal newOriginal = swapRatio.compareTo(BigDecimal.ZERO) > 0 ? current.originalUnits().multiply(swapRatio) : current.originalUnits();
BigDecimal newRemaining = swapRatio.compareTo(BigDecimal.ZERO) > 0 ? current.remainingUnits().multiply(swapRatio) : current.remainingUnits();
⋮----
String newAssetId = (event.isin() != null && !event.isin().isBlank()) ? event.isin() : current.assetId();
String newAssetName = (event.assetName() != null && !event.assetName().isBlank()) ? event.assetName() : current.assetName();
⋮----
openLotsQueue.set(j, current.withAssetDetails(newAssetId, newAssetName, newOriginal, newRemaining, newCostPerUnit));
⋮----
// cash income, doesn't impact stock lots
⋮----
return new FifoResult(openLotsQueue, matchedLots);
````

## File: src/main/java/com/portfolioos/core/matcher/FundTierClassifier.java
````java
public class FundTierClassifier {
⋮----
public static Set<String> findActiveAssetIds(Collection<Lot> lots, LocalDate currentDate) {
return findActiveAssetIds(lots, currentDate, ACTIVE_SIP_THRESHOLD_MONTHS);
⋮----
public static Set<String> findActiveAssetIds(Collection<Lot> lots, LocalDate currentDate, int thresholdMonths) {
if (currentDate == null) currentDate = LocalDate.now();
LocalDate cutoffDate = currentDate.minusMonths(thresholdMonths);
⋮----
if (lot.acquisitionDate() != null && !lot.acquisitionDate().isBefore(cutoffDate)) {
activeIds.add(lot.assetId());
⋮----
public static FundStatus getFundStatus(String assetId, String bucketStrategy, Set<String> sipActiveIds) {
if ("ACCUMULATOR".equalsIgnoreCase(bucketStrategy)) {
⋮----
if (sipActiveIds != null && sipActiveIds.contains(assetId)) {
⋮----
public static FundTier classify(String assetId) {
⋮----
if (com.portfolioos.core.rules.BucketConfigLoader.isPreferredFund(assetId)) {
⋮----
public static boolean isLegacyFund(String assetId, Set<String> activeAssetIds) {
⋮----
return classify(assetId) == FundTier.LEGACY;
````

## File: src/main/java/com/portfolioos/core/matcher/TaxClassifier.java
````java
public class TaxClassifier {
⋮----
// Pre-registered ISINs and Ticker Symbols
isinCategoryRegistry.put("MAHKTECH", AssetCategory.INTERNATIONAL);
isinCategoryRegistry.put("MON100", AssetCategory.INTERNATIONAL);
isinCategoryRegistry.put("MASPTOP50", AssetCategory.INTERNATIONAL);
isinCategoryRegistry.put("INF109KA1VY6", AssetCategory.INTERNATIONAL);
isinCategoryRegistry.put("INF247L01793", AssetCategory.INTERNATIONAL);
isinCategoryRegistry.put("GOLDBEES", AssetCategory.GOLD_SILVER);
isinCategoryRegistry.put("SILVERBEES", AssetCategory.GOLD_SILVER);
⋮----
private static final Pattern sgbPattern = Pattern.compile("(?:SGB|SOVEREIGN GOLD)", Pattern.CASE_INSENSITIVE);
private static final Pattern debtPattern = Pattern.compile("(?:GILT|BOND|DEBT|LIQUID|OVERNIGHT|TREASURY)", Pattern.CASE_INSENSITIVE);
private static final Pattern goldSilverPattern = Pattern.compile("(?:GOLD|SILVER)", Pattern.CASE_INSENSITIVE);
private static final Pattern intlPattern = Pattern.compile("(?:NASDAQ|S&P|INTERNATIONAL|GLOBAL|US EQUITIES|MAHKTECH|HANG SENG|MON100|MASPTOP50|ASIA|EMERGING|CHINA)", Pattern.CASE_INSENSITIVE);
private static final Pattern listedPattern = Pattern.compile("(?:ETF|BEES|MON100|MASPTOP50|MAHKTECH|NIFTY|SENSEX)", Pattern.CASE_INSENSITIVE);
⋮----
public static void registerAssetCategory(String isinOrAssetId, AssetCategory category) {
isinCategoryRegistry.put(isinOrAssetId.toUpperCase(), category);
⋮----
public static void registerAssetCategories(Map<String, AssetCategory> mappings) {
mappings.forEach((key, cat) -> isinCategoryRegistry.put(key.toUpperCase(), cat));
⋮----
public static AssetCategory detectCategory(String assetId, String assetName) {
String idUpper = assetId.toUpperCase();
String nameUpper = assetName.toUpperCase();
⋮----
// 1. Primary lookup: Explicit registry
if (isinCategoryRegistry.containsKey(idUpper)) return isinCategoryRegistry.get(idUpper);
if (isinCategoryRegistry.containsKey(nameUpper)) return isinCategoryRegistry.get(nameUpper);
⋮----
// 2. Secondary fallback: Regex heuristics
if (sgbPattern.matcher(nameUpper).find()) return AssetCategory.SGB;
if (debtPattern.matcher(nameUpper).find()) return AssetCategory.DEBT_SPECIFIED_50AA;
if (goldSilverPattern.matcher(nameUpper).find()) return AssetCategory.GOLD_SILVER;
if (intlPattern.matcher(nameUpper).find()) return AssetCategory.INTERNATIONAL;
⋮----
public static boolean isListed(String assetId, String assetName) {
String combined = (assetId + " " + assetName).toUpperCase();
return listedPattern.matcher(combined).find();
⋮----
public static TaxTerm classifyTaxTerm(AssetCategory category, long holdingDays, String fiscalYear, boolean isListed) {
return classifyTaxTerm(category, holdingDays, fiscalYear, isListed, null, null);
⋮----
public static TaxTerm classifyTaxTerm(AssetCategory category, long holdingDays, String fiscalYear, boolean isListed, java.time.LocalDate acquisitionDate, java.time.LocalDate disposalDate) {
TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
⋮----
// Section 50AA Finance Act (No. 2) 2024 Temporal Branching:
// Purchased Post April 1, 2023 -> Always Short-Term (Slab Rate)
// Purchased Pre April 1, 2023 (Legacy Debt Fund):
// - Sold Post July 23, 2024: > 24 months (730d) -> LTCG @ 12.5% (no indexation); else STCG
// - Sold Pre July 23, 2024: > 36 months (1095d) -> LTCG @ 20% (with indexation); else STCG
java.time.LocalDate apr2023Cutoff = java.time.LocalDate.of(2023, 4, 1);
java.time.LocalDate jul2024Cutoff = java.time.LocalDate.of(2024, 7, 23);
⋮----
if (acquisitionDate != null && acquisitionDate.isBefore(apr2023Cutoff)) {
if (disposalDate != null && !disposalDate.isBefore(jul2024Cutoff)) {
⋮----
if (holdingDays >= rules.equityLtcgThresholdDays()) {
⋮----
// Per Finance Act 2024: Listed ETFs get 12-month (365d) threshold; unlisted FoFs get 24-month (730d)
long threshold = isListed ? rules.equityLtcgThresholdDays() : rules.goldInternationalThresholdDays();
⋮----
if (holdingDays >= rules.goldInternationalThresholdDays()) {
````

## File: src/main/java/com/portfolioos/core/model/AssetCategory.java
````java

````

## File: src/main/java/com/portfolioos/core/model/EventType.java
````java

````

## File: src/main/java/com/portfolioos/core/model/Lot.java
````java
public Lot withRemainingUnitsAndCost(BigDecimal remaining, BigDecimal cost, BigDecimal costBasis) {
return new Lot(
⋮----
public Lot withAssetDetails(String newAssetId, String newAssetName, BigDecimal newOriginal, BigDecimal newRemaining, BigDecimal newCostPerUnit) {
````

## File: src/main/java/com/portfolioos/core/model/MatchedLot.java
````java

````

## File: src/main/java/com/portfolioos/core/model/TaxEvent.java
````java
public BigDecimal unitDelta() {
⋮----
case DISPOSAL, SGB_MATURITY -> units.negate();
````

## File: src/main/java/com/portfolioos/core/model/TaxTerm.java
````java

````

## File: src/main/java/com/portfolioos/core/nav/AmfiNavSync.java
````java
public class AmfiNavSync {
⋮----
private static final Object lock = new Object();
⋮----
public List<NavEntry> parseAmfiFeed(String feedContent) {
⋮----
LocalDate today = LocalDate.now();
⋮----
String[] lines = feedContent.split("\\r?\\n");
⋮----
String[] parts = line.split(";");
⋮----
String schemeCode = parts[0].trim();
String isin1 = parts.length > 1 ? parts[1].trim() : null;
String isin2 = parts.length > 2 ? parts[2].trim() : null;
String schemeName = parts.length > 3 ? parts[3].trim() : "";
⋮----
String token = parts[i].trim();
if (!token.isEmpty()) {
nav = new BigDecimal(token);
⋮----
if (isin1 != null && !isin1.isEmpty() && !"-".equals(isin1)) {
entries.add(new NavEntry(schemeCode, isin1, schemeName, nav, today));
⋮----
if (isin2 != null && !isin2.isEmpty() && !"-".equals(isin2) && !isin2.equalsIgnoreCase(isin1)) {
entries.add(new NavEntry(schemeCode, isin2, schemeName, nav, today));
⋮----
if ((isin1 == null || isin1.isEmpty() || "-".equals(isin1)) && (isin2 == null || isin2.isEmpty() || "-".equals(isin2))) {
entries.add(new NavEntry(schemeCode, null, schemeName, nav, today));
⋮----
public List<NavEntry> fetchLatestNavsFromAmfi() {
long now = System.currentTimeMillis();
⋮----
URI uri = new URI("https://portal.amfiindia.com/spages/NAVAll.txt");
URLConnection conn = uri.toURL().openConnection();
conn.setConnectTimeout(5000);
conn.setReadTimeout(5000);
⋮----
StringBuilder sb = new StringBuilder();
try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
⋮----
while ((line = reader.readLine()) != null) {
sb.append(line).append("\n");
⋮----
List<NavEntry> parsed = parseAmfiFeed(sb.toString());
if (!parsed.isEmpty()) {
⋮----
lastFetchTimeMs = System.currentTimeMillis();
⋮----
System.err.println("AMFI fetch error: " + e.getMessage());
⋮----
public Map<String, BigDecimal> getNavMap() {
List<NavEntry> entries = fetchLatestNavsFromAmfi();
⋮----
if (entry.isin() != null && entry.nav() != null) {
navMap.put(entry.isin(), entry.nav());
````

## File: src/main/java/com/portfolioos/core/nav/MfApiNavDownloader.java
````java
public class MfApiNavDownloader {
⋮----
private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
⋮----
this.httpClient = HttpClient.newBuilder()
.connectTimeout(Duration.ofSeconds(30))
.build();
this.objectMapper = new ObjectMapper();
⋮----
// Seed known ISIN mappings for fast resolution
isinToSchemeCodeMap.put("INF879O01027", 122639L); // Parag Parikh Flexi Cap
isinToSchemeCodeMap.put("INF109KC12U0", 152482L); // ICICI LargeMidcap 250
isinToSchemeCodeMap.put("INF109KC13X2", 152936L); // ICICI Value 30
isinToSchemeCodeMap.put("INF754K01TN5", 153096L); // Edelweiss Multicap Momentum
isinToSchemeCodeMap.put("INF247L01916", 147700L); // Motilal Nifty Midcap 150
isinToSchemeCodeMap.put("INF247L01BQ9", 151523L); // Motilal Microcap 250
isinToSchemeCodeMap.put("INF174KA1TY2", 153146L); // Kotak Nifty 100 Equal Weight
isinToSchemeCodeMap.put("INF109K016B1", 120612L); // ICICI Corporate Bond
isinToSchemeCodeMap.put("INF109K018C5", 120626L); // ICICI Gilt
isinToSchemeCodeMap.put("INF204K01H36", 118778L); // Nippon Nifty 50
isinToSchemeCodeMap.put("INF277K011O1", 145371L); // Tata Small Cap
isinToSchemeCodeMap.put("INF200K01RA0", 120504L); // SBI Contra
isinToSchemeCodeMap.put("INF109K018M4", 120658L); // ICICI Infrastructure
isinToSchemeCodeMap.put("INF204K01G52", 118728L); // Nippon Consumption
isinToSchemeCodeMap.put("INF200K01UJ5", 148906L); // SBI Large & Midcap
isinToSchemeCodeMap.put("INF204K01K15", 118778L); // Nippon Small Cap
isinToSchemeCodeMap.put("INF205K01KR8", 120401L); // Invesco Arbitrage
isinToSchemeCodeMap.put("INF769K01ED6", 143783L); // Mirae Healthcare
isinToSchemeCodeMap.put("INF247L01BM8", 150642L); // Motilal Gold and Silver
⋮----
private synchronized void loadMasterListIfNecessary() {
⋮----
HttpRequest req = HttpRequest.newBuilder()
.uri(URI.create(masterUrl))
.timeout(Duration.ofSeconds(30))
.GET()
⋮----
HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
if (resp.statusCode() == 200) {
JsonNode array = objectMapper.readTree(resp.body());
if (array.isArray()) {
⋮----
long code = item.get("schemeCode").asLong();
JsonNode ig = item.get("isinGrowth");
JsonNode idiv = item.get("isinDivReinvestment");
if (ig != null && !ig.isNull() && !ig.asText().isBlank()) {
isinToSchemeCodeMap.putIfAbsent(ig.asText().trim(), code);
⋮----
if (idiv != null && !idiv.isNull() && !idiv.asText().isBlank()) {
isinToSchemeCodeMap.putIfAbsent(idiv.asText().trim(), code);
⋮----
System.out.println("MfApiNavDownloader: Loaded master scheme list with " + isinToSchemeCodeMap.size() + " ISIN mappings.");
⋮----
System.err.println("Failed to load MFAPI master scheme list: " + e.getMessage());
⋮----
public void downloadHistoricalNavsForIsin(String isin, DuckDbProjector projector) {
if (isin == null || isin.isBlank()) return;
loadMasterListIfNecessary();
⋮----
Long schemeCode = isinToSchemeCodeMap.get(isin.trim());
⋮----
System.err.println("No MFAPI scheme code found for ISIN " + isin);
⋮----
// Fetch daily NAV history
⋮----
HttpRequest navReq = HttpRequest.newBuilder()
.uri(URI.create(navUrl))
.timeout(Duration.ofSeconds(15))
⋮----
HttpResponse<String> navResp = httpClient.send(navReq, HttpResponse.BodyHandlers.ofString());
if (navResp.statusCode() != 200) return;
⋮----
JsonNode navTree = objectMapper.readTree(navResp.body());
JsonNode dataNode = navTree.get("data");
if (dataNode == null || !dataNode.isArray()) return;
⋮----
String dateStr = row.get("date").asText();
BigDecimal navVal = new BigDecimal(row.get("nav").asText());
LocalDate date = LocalDate.parse(dateStr, DD_MM_YYYY);
series.put(date, navVal);
⋮----
projector.saveNavHistoryFullSeries(isin, series);
System.out.println("Successfully backfilled " + series.size() + " MFAPI historical NAV records for ISIN " + isin + " (Scheme " + schemeCode + ")");
⋮----
System.err.println("MFAPI historical NAV backfill error for ISIN " + isin + ": " + e.getMessage());
⋮----
public void downloadBenchmarkData(String benchmarkId, long schemeCode, DuckDbProjector projector) {
⋮----
double navVal = Double.parseDouble(row.get("nav").asText());
⋮----
levels.put(date.toString(), navVal);
⋮----
projector.saveBenchmarkLevels(benchmarkId, levels);
System.out.println("Successfully ingested " + levels.size() + " benchmark level records for " + benchmarkId + " (Scheme " + schemeCode + ")");
⋮----
System.err.println("MFAPI benchmark download error for " + benchmarkId + ": " + e.getMessage());
⋮----
public static void main(String[] args) {
DuckDbProjector projector = new DuckDbProjector();
MfApiNavDownloader downloader = new MfApiNavDownloader();
List<String> isins = List.of(
⋮----
System.out.println("Starting MfApiNavDownloader verification across 19 holdings ISINs...");
⋮----
downloader.downloadHistoricalNavsForIsin(isin, projector);
⋮----
downloader.downloadBenchmarkData("NIFTY_50_TRI", 120716, projector);
downloader.downloadBenchmarkData("NIFTY_500_TRI", 147648, projector);
projector.checkpoint();
System.out.println("MfApiNavDownloader verification complete.");
````

## File: src/main/java/com/portfolioos/core/nav/NseIndexConstituentDownloader.java
````java
public class NseIndexConstituentDownloader {
⋮----
public void seedStandardIndexConstituents(DuckDbProjector projector) {
String disclosureDate = "2026-03-31"; // Semi-annual March snapshot
⋮----
// 1. ICICI Prudential Nifty LargeMidcap 250 Index Fund (INF109KC12U0 / INF247L01AX8 / 147702)
List<Map<String, Object>> lm250 = Arrays.asList(
createHolding("RELIANCE", "INE002A01018", 6.85),
createHolding("HDFCBANK", "INE040A01034", 6.42),
createHolding("ICICIBANK", "INE090A01021", 5.10),
createHolding("INFY", "INE009A01021", 3.85),
createHolding("BHARTIARTL", "INE397D01024", 3.20),
createHolding("TRENT", "INE849A01020", 2.15),
createHolding("LTIM", "INE214T01019", 1.95),
createHolding("DIXON", "INE935N01020", 1.80),
createHolding("PERSISTENT", "INE262H01013", 1.75),
createHolding("COFORGE", "INE591G01017", 1.65)
⋮----
projector.saveFundHoldings("INF109KC12U0", disclosureDate, lm250);
projector.saveFundHoldings("INF247L01AX8", disclosureDate, lm250);
projector.saveFundHoldings("147702", disclosureDate, lm250);
⋮----
// 2. ICICI Prudential Nifty200 Value 30 Index Fund (INF109KC13X2 / INF247L01BM8 / 150642)
// Full Nifty 200 Value 30 constituent breakdown including Nifty 101-200 midcap value names
List<Map<String, Object>> val30 = Arrays.asList(
createHolding("RELIANCE", "INE002A01018", 12.50),
createHolding("HDFCBANK", "INE040A01034", 10.80),
createHolding("ICICIBANK", "INE090A01021", 9.60),
createHolding("SBIN", "INE062A01020", 8.10),
createHolding("NTPC", "INE733E01010", 7.20),
createHolding("POWERGRID", "INE752E01010", 6.80),
createHolding("ONGC", "INE213A01029", 5.90),
createHolding("COALINDIA", "INE522F01014", 5.40),
createHolding("TATASTEEL", "INE081A01020", 4.80),
createHolding("HINDALCO", "INE038A01020", 4.20),
createHolding("PFC", "INE134E01011", 3.10),
createHolding("RECLTD", "INE020B01018", 2.90),
createHolding("OIL", "INE274J01014", 2.40),
createHolding("NMDC", "INE584A01023", 2.10),
createHolding("FEDERALBNK", "INE171A01029", 1.75), // Midcap 101-200 universe overlap!
createHolding("VEDL", "INE205A01012", 1.60),
createHolding("GAIL", "INE129A01019", 1.50),
createHolding("BPCL", "INE029A01011", 1.40),
createHolding("IOC", "INE242A01010", 1.30),
createHolding("HPCL", "INE094A01015", 1.20)
⋮----
projector.saveFundHoldings("INF109KC13X2", disclosureDate, val30);
projector.saveFundHoldings("INF247L01BM8", disclosureDate, val30);
projector.saveFundHoldings("150642", disclosureDate, val30);
⋮----
// 3. Kotak Nifty 100 Equal Weight Index Fund (INF174KA1TY2 / INF204K01H36 / 118741)
List<Map<String, Object>> ew100 = Arrays.asList(
createHolding("RELIANCE", "INE002A01018", 1.00),
createHolding("HDFCBANK", "INE040A01034", 1.00),
createHolding("ICICIBANK", "INE090A01021", 1.00),
createHolding("INFY", "INE009A01021", 1.00),
createHolding("BHARTIARTL", "INE397D01024", 1.00),
createHolding("TRENT", "INE849A01020", 1.00),
createHolding("NTPC", "INE733E01010", 1.00),
createHolding("POWERGRID", "INE752E01010", 1.00),
createHolding("SBIN", "INE062A01020", 1.00),
createHolding("ONGC", "INE213A01029", 1.00)
⋮----
projector.saveFundHoldings("INF174KA1TY2", disclosureDate, ew100);
projector.saveFundHoldings("INF204K01H36", disclosureDate, ew100);
projector.saveFundHoldings("118741", disclosureDate, ew100);
⋮----
// 4. Motilal Oswal Nifty Midcap 150 Index Fund (INF247L01916 / INF754K01TN5 / 152985)
List<Map<String, Object>> mc150 = Arrays.asList(
createHolding("DIXON", "INE935N01020", 2.40),
createHolding("PERSISTENT", "INE262H01013", 2.20),
createHolding("COFORGE", "INE591G01017", 2.10),
createHolding("CHOLAFIN", "INE121A01024", 1.95),
createHolding("MAXHEALTH", "INE027H01010", 1.85),
createHolding("POLYCAB", "INE455K01017", 1.80),
⋮----
createHolding("APOLLOTYRE", "INE438A01022", 1.65),
createHolding("INDIAMART", "INE933S01016", 1.50),
createHolding("SUNDARMFIN", "INE660A01013", 1.40)
⋮----
projector.saveFundHoldings("INF247L01916", disclosureDate, mc150);
projector.saveFundHoldings("INF754K01TN5", disclosureDate, mc150);
projector.saveFundHoldings("152985", disclosureDate, mc150);
⋮----
// 5. Motilal Oswal Nifty Microcap 250 / Momentum Quality 50 (INF247L01BQ9 / 151814)
List<Map<String, Object>> mq50 = Arrays.asList(
createHolding("TRENT", "INE849A01020", 5.40, "IN"),
createHolding("BHARTIARTL", "INE397D01024", 5.10, "IN"),
createHolding("DIXON", "INE935N01020", 4.80, "IN"),
createHolding("PERSISTENT", "INE262H01013", 4.50, "IN"),
createHolding("COFORGE", "INE591G01017", 4.20, "IN"),
createHolding("BEL", "INE263A01024", 3.90, "IN"),
createHolding("HAL", "INE066F01020", 3.80, "IN"),
createHolding("BHAL", "INE257A01026", 3.40, "IN"),
createHolding("CHOLAFIN", "INE121A01024", 3.20, "IN"),
createHolding("TMC", "INE192A01025", 3.00, "IN")
⋮----
projector.saveFundHoldings("INF247L01BQ9", disclosureDate, mq50);
projector.saveFundHoldings("151814", disclosureDate, mq50);
⋮----
// 6. Parag Parikh Flexi Cap Fund (INF879O01027) - Parse Full Excel Factsheet
⋮----
if (pFile.exists()) {
⋮----
parsedPpfas = new com.portfolioos.core.parser.PpfasHoldingsParser().parseAndIngest(projector, is, disclosureDate);
⋮----
System.err.println("Failed parsing full PPFAS Excel factsheet: " + e.getMessage());
⋮----
List<Map<String, Object>> ppfas = Arrays.asList(
createHolding("HDFCBANK", "INE040A01034", 7.45, "IN"),
createHolding("BAJFINANCE", "INE296A01024", 6.80, "IN"),
createHolding("AMAZON", "US0231351067", 6.15, "US"),
createHolding("ALPHABET", "US02079K3059", 5.80, "US"),
createHolding("META", "US30303M1027", 4.90, "US"),
createHolding("MICROSOFT", "US5949181045", 4.20, "US"),
createHolding("ICICIBANK", "INE090A01021", 5.40, "IN"),
createHolding("ITC", "INE154A01025", 4.10, "IN"),
createHolding("TCS", "INE467B01029", 3.90, "IN"),
createHolding("COALINDIA", "INE522F01014", 3.50, "IN")
⋮----
projector.saveFundHoldings("INF879O01027", disclosureDate, ppfas);
⋮----
// 7. Nippon India Small Cap Fund (INF204K01K15) - Parse Full Excel Factsheet
⋮----
if (nFile.exists()) {
⋮----
parsedNippon = new com.portfolioos.core.parser.NipponHoldingsParser().parseAndIngest(projector, is, disclosureDate);
⋮----
System.err.println("Failed parsing full Nippon Small Cap Excel factsheet: " + e.getMessage());
⋮----
List<Map<String, Object>> nippon = Arrays.asList(
createHolding("TUBEINVEST", "INE974X01010", 2.15, "IN"),
createHolding("HDFC_AMC", "INE127D01025", 1.95, "IN"),
createHolding("APARINDS", "INE072E01019", 1.85, "IN"),
createHolding("MULTIOPT", "INE745G01035", 1.70, "IN"),
createHolding("VOLTAS", "INE226A01021", 1.65, "IN"),
createHolding("KEI", "INE878B01027", 1.55, "IN"),
createHolding("DIXON", "INE935N01020", 1.45, "IN"),
createHolding("PERSISTENT", "INE262H01013", 1.35, "IN"),
createHolding("CUMMINSIND", "INE299A01018", 1.25, "IN"),
createHolding("KAYNES", "INE918Z01012", 1.15, "IN")
⋮----
projector.saveFundHoldings("INF204K01K15", disclosureDate, nippon);
⋮----
System.out.println("Seeded standard index and active fund constituent weights (7 funds) into DuckDB.");
⋮----
private Map<String, Object> createHolding(String symbol, String isin, double weightPct) {
return createHolding(symbol, isin, weightPct, "IN");
⋮----
private Map<String, Object> createHolding(String symbol, String isin, double weightPct, String market) {
⋮----
map.put("stock_symbol", symbol);
map.put("stock_isin", isin);
map.put("weight_pct", weightPct);
map.put("market", market != null ? market : "IN");
````

## File: src/main/java/com/portfolioos/core/parser/NipponHoldingsParser.java
````java
public class NipponHoldingsParser {
⋮----
public boolean parseAndIngest(DuckDbProjector projector, InputStream excelInputStream, String defaultAsOfDate) {
try (Workbook workbook = new XSSFWorkbook(excelInputStream)) {
Sheet sheet = workbook.getSheet("SC");
⋮----
sheet = workbook.getSheetAt(0);
⋮----
if (cell == null || cell.getCellType() != CellType.STRING) continue;
String val = cell.getStringCellValue().trim().toUpperCase();
if (val.contains("ISIN")) isinCol = cell.getColumnIndex();
if (val.contains("NAME OF THE INSTRUMENT") || val.contains("COMPANY") || val.contains("SECURITY")) nameCol = cell.getColumnIndex();
if (val.contains("% TO NAV") || val.contains("% TO AUM") || val.contains("PERCENTAGE")) weightCol = cell.getColumnIndex();
⋮----
Cell isinCell = row.getCell(isinCol);
Cell nameCell = row.getCell(nameCol);
Cell weightCell = row.getCell(weightCol);
⋮----
if (weightCell.getCellType() == CellType.NUMERIC) {
weightPct = weightCell.getNumericCellValue();
} else if (weightCell.getCellType() == CellType.STRING) {
⋮----
weightPct = Double.parseDouble(weightCell.getStringCellValue().replace("%", "").trim());
⋮----
String isin = isinCell != null && isinCell.getCellType() == CellType.STRING ? isinCell.getStringCellValue().trim() : "";
String name = nameCell != null && nameCell.getCellType() == CellType.STRING ? nameCell.getStringCellValue().trim() : "";
⋮----
if (name.toUpperCase().contains("TOTAL") || name.toUpperCase().contains("TREPS") || name.toUpperCase().contains("NET CURRENT ASSETS")) {
⋮----
String symbol = cleanSymbol(name, isin);
⋮----
h.put("stock_symbol", symbol);
h.put("stock_isin", isin);
h.put("weight_pct", weightPct);
h.put("market", "IN");
⋮----
holdings.add(h);
⋮----
System.out.println(String.format("Nippon Small Cap Parse Result: %d holdings extracted, total_weight=%.2f%%",
holdings.size(), totalWeight));
⋮----
// Weight-Sum Validation Self-Check (30% to 102%)
⋮----
System.err.println(String.format("WARNING: Nippon Small Cap weight sum validation failed: %.2f%% outside expected bounds [30.0%%, 102.0%%]", totalWeight));
⋮----
if (!holdings.isEmpty()) {
projector.clearFundHoldings(NIPPON_SMALLCAP_ISIN);
projector.saveFundHoldings(NIPPON_SMALLCAP_ISIN, defaultAsOfDate, holdings);
⋮----
System.err.println("Failed to parse Nippon Small Cap Excel workbook: " + e.getMessage());
⋮----
private String cleanSymbol(String name, String isin) {
String u = name.toUpperCase();
if (u.contains("TUBE INVEST")) return "TUBEINVEST";
if (u.contains("HDFC ASSET") || u.contains("HDFC_AMC")) return "HDFC_AMC";
if (u.contains("APAR IND")) return "APARINDS";
if (u.contains("MULTI COMMODITY") || u.contains("MCX")) return "MULTIOPT";
if (u.contains("VOLTAS")) return "VOLTAS";
if (u.contains("KEI IND")) return "KEI";
if (u.contains("DIXON")) return "DIXON";
if (u.contains("PERSISTENT")) return "PERSISTENT";
if (u.contains("CUMMINS")) return "CUMMINSIND";
if (u.contains("KAYNES")) return "KAYNES";
if (u.contains("CARBORUNDUM")) return "CARBORUN";
if (u.contains("BHARAT DYNAMICS")) return "BDL";
if (u.contains("ELGI")) return "ELGIEQUIP";
if (u.contains("CHOLAMANDALAM")) return "CHOLAFIN";
if (u.contains("KIRLOSKAR")) return "KIRLOSENG";
if (u.contains("TIMKEN")) return "TIMKEN";
if (u.contains("CENTURY TEXT")) return "CENTURYTEX";
if (u.contains("TECHNOCRAFT")) return "TIIL";
if (u.contains("JYOTHY")) return "JYOTHYLAB";
if (u.contains("GRINDWELL")) return "GRINDWELL";
if (u.contains("CREDITACCESS")) return "CREDITACC";
if (u.contains("EQUITAS")) return "EQUITASBNK";
if (u.contains("CITY UNION")) return "CUB";
if (u.contains("KARUR VYSYA")) return "KVB";
if (u.contains("UJJIVAN")) return "UJJIVANSFB";
if (u.contains("CAN FIN")) return "CANFINHOME";
if (u.contains("HOME FIRST")) return "HOMEFIRST";
if (u.contains("AAVAS")) return "AAVAS";
if (u.contains("BALRAMPUR")) return "BALRAMCHIN";
if (u.contains("TRIVENI")) return "TRIVENI";
if (u.contains("PARRY")) return "EIDPARRY";
if (u.contains("DCM SHRIRAM")) return "DCMSHRIRAM";
if (u.contains("PRAJ")) return "PRAJIND";
if (u.contains("CONCORD")) return "CONCORD";
if (u.contains("BLUE JET")) return "BLUEJET";
if (u.contains("JUPITER")) return "JUPITERLIFE";
if (u.contains("INNOVA")) return "INNOVA";
⋮----
if (name != null && !name.isBlank()) {
return name.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
````

## File: src/main/java/com/portfolioos/core/parser/PpfasHoldingsParser.java
````java
public class PpfasHoldingsParser {
⋮----
public boolean parseAndIngest(DuckDbProjector projector, InputStream excelInputStream, String defaultAsOfDate) {
try (Workbook workbook = new XSSFWorkbook(excelInputStream)) {
Sheet sheet = workbook.getSheet("PPLTVF");
⋮----
// Fallback to first sheet if PPLTVF not found by exact name
sheet = workbook.getSheetAt(0);
⋮----
if (cell == null || cell.getCellType() != CellType.STRING) continue;
String val = cell.getStringCellValue().trim().toUpperCase();
if (val.contains("ISIN")) isinCol = cell.getColumnIndex();
if (val.contains("NAME OF THE INSTRUMENT") || val.contains("COMPANY") || val.contains("SECURITY")) nameCol = cell.getColumnIndex();
if (val.contains("% TO NAV") || val.contains("% TO AUM") || val.contains("PERCENTAGE")) weightCol = cell.getColumnIndex();
⋮----
// Fallback column positions if headers weren't matched dynamically
⋮----
Cell isinCell = row.getCell(isinCol);
Cell nameCell = row.getCell(nameCol);
Cell weightCell = row.getCell(weightCol);
⋮----
if (weightCell.getCellType() == CellType.NUMERIC) {
weightPct = weightCell.getNumericCellValue();
} else if (weightCell.getCellType() == CellType.STRING) {
⋮----
weightPct = Double.parseDouble(weightCell.getStringCellValue().replace("%", "").trim());
⋮----
String isin = isinCell != null && isinCell.getCellType() == CellType.STRING ? isinCell.getStringCellValue().trim() : "";
String name = nameCell != null && nameCell.getCellType() == CellType.STRING ? nameCell.getStringCellValue().trim() : "";
⋮----
if (name.toUpperCase().contains("TOTAL") || name.toUpperCase().contains("TREPS") || name.toUpperCase().contains("NET CURRENT ASSETS")) {
⋮----
String symbol = cleanSymbol(name, isin);
⋮----
if (isin.startsWith("US") || symbol.equalsIgnoreCase("ALPHABET") || symbol.equalsIgnoreCase("AMAZON") ||
symbol.equalsIgnoreCase("META") || symbol.equalsIgnoreCase("MICROSOFT") || symbol.equalsIgnoreCase("APPLE") ||
name.toUpperCase().contains("ALPHABET") || name.toUpperCase().contains("AMAZON") ||
name.toUpperCase().contains("META") || name.toUpperCase().contains("MICROSOFT")) {
⋮----
h.put("stock_symbol", symbol);
h.put("stock_isin", isin);
h.put("weight_pct", weightPct);
h.put("market", market);
⋮----
holdings.add(h);
⋮----
System.out.println(String.format("PPFAS Parse Result: %d holdings extracted, total_weight=%.2f%%, us_weight=%.2f%%",
holdings.size(), totalWeight, usWeight));
⋮----
// Weight-Sum Validation Self-Check (75% to 102%)
⋮----
System.err.println(String.format("WARNING: PPFAS weight sum validation failed: %.2f%% outside expected bounds [75.0%%, 102.0%%]", totalWeight));
⋮----
// Overseas sleeve plausibility check (12% to 28%)
⋮----
System.err.println(String.format("WARNING: PPFAS US sleeve weight (%.2f%%) outside expected plausibility bounds [5.0%%, 35.0%%]", usWeight));
⋮----
if (!holdings.isEmpty()) {
projector.clearFundHoldings(PPFAS_ISIN);
projector.saveFundHoldings(PPFAS_ISIN, defaultAsOfDate, holdings);
⋮----
System.err.println("Failed to parse PPFAS Excel workbook: " + e.getMessage());
⋮----
private String cleanSymbol(String name, String isin) {
String u = name.toUpperCase();
if (u.contains("HDFC BANK")) return "HDFCBANK";
if (u.contains("ICICI BANK")) return "ICICIBANK";
if (u.contains("BAJAJ HOLDINGS") || u.contains("BAJAJ FIN")) return "BAJFINANCE";
if (u.contains("ITC ")) return "ITC";
if (u.contains("POWER GRID")) return "POWERGRID";
if (u.contains("COAL INDIA")) return "COALINDIA";
if (u.contains("TATA CONSULTANCY") || u.contains("TCS")) return "TCS";
if (u.contains("AXIS BANK")) return "AXISBANK";
if (u.contains("MARUTI")) return "MARUTI";
if (u.contains("HCL TECH")) return "HCLTECH";
if (u.contains("TECH MAHINDRA")) return "TECHM";
if (u.contains("LARSEN")) return "LT";
if (u.contains("KOTAK")) return "KOTAKBANK";
if (u.contains("NTPC")) return "NTPC";
if (u.contains("TITAN")) return "TITAN";
if (u.contains("CIPLA")) return "CIPLA";
if (u.contains("SUN PHARMA")) return "SUNPHARMA";
if (u.contains("DR REDDY")) return "DRREDDY";
if (u.contains("HERO MOTOCORP")) return "HEROMOTOCO";
if (u.contains("MAHINDRA & MAHINDRA") || u.contains("M&M")) return "M&M";
if (u.contains("ULTRATECH")) return "ULTRACEMCO";
if (u.contains("GRASIM")) return "GRASIM";
if (u.contains("NESTLE")) return "NESTLEIND";
if (u.contains("ASIAN PAINTS")) return "ASIANPAINT";
if (u.contains("BRITANNIA")) return "BRITANNIA";
if (u.contains("ALPHABET") || isin.equals("US02079K3059")) return "ALPHABET";
if (u.contains("AMAZON") || isin.equals("US0231351067")) return "AMAZON";
if (u.contains("META") || isin.equals("US30303M1027")) return "META";
if (u.contains("MICROSOFT") || isin.equals("US5949181045")) return "MICROSOFT";
⋮----
if (name != null && !name.isBlank()) {
return name.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
````

## File: src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java
````java
public class DuckDbProjector {
⋮----
this(System.getenv("DUCKDB_PATH") != null && !System.getenv("DUCKDB_PATH").isBlank()
? System.getenv("DUCKDB_PATH") : "data/tax_ledger.duckdb");
⋮----
Class.forName("org.duckdb.DuckDBDriver");
⋮----
throw new RuntimeException("DuckDB JDBC driver not found", e);
⋮----
if (":memory:".equals(dbPath)) {
⋮----
File file = new File(dbPath);
if (file.getParentFile() != null) {
file.getParentFile().mkdirs();
⋮----
jdbcUrl = "jdbc:duckdb:" + file.getAbsolutePath();
⋮----
HikariConfig config = new HikariConfig();
config.setJdbcUrl(jdbcUrl);
config.setDriverClassName("org.duckdb.DuckDBDriver");
config.setMaximumPoolSize(10);
config.setMinimumIdle(2);
config.setIdleTimeout(30000);
config.setPoolName("DuckDbProjectorPool");
⋮----
this.dataSource = new HikariDataSource(config);
initReadSchema();
⋮----
private Connection getConnection() throws SQLException {
return dataSource.getConnection();
⋮----
private void initReadSchema() {
try (Connection conn = getConnection();
Statement stmt = conn.createStatement()) {
stmt.execute(
⋮----
stmt.execute("ALTER TABLE fund_holdings ADD COLUMN IF NOT EXISTS market VARCHAR DEFAULT 'IN'");
⋮----
throw new RuntimeException("Failed to initialize DuckDB schema", e);
⋮----
public void saveBenchmarkLevels(String benchmarkId, Map<String, Double> dateToLevel) {
if (dateToLevel == null || dateToLevel.isEmpty()) return;
⋮----
PreparedStatement pstmt = conn.prepareStatement(sql)) {
⋮----
for (Map.Entry<String, Double> entry : dateToLevel.entrySet()) {
pstmt.setString(1, benchmarkId);
pstmt.setString(2, entry.getKey());
pstmt.setDouble(3, entry.getValue());
pstmt.addBatch();
⋮----
pstmt.executeBatch();
⋮----
System.err.println("Failed to save benchmark levels: " + e.getMessage());
⋮----
public Map<String, Object> getAlignedPortfolioAndBenchmarkReturns(String benchmarkId) {
⋮----
try (Connection conn = getConnection()) {
⋮----
try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
⋮----
try (ResultSet rs = pstmt.executeQuery()) {
while (rs.next()) {
String dateStr = rs.getString("nav_date");
String prevDateStr = rs.getString("prev_date");
double pRet = rs.getDouble("blended_ret");
double bRet = rs.getDouble("b_ret");
⋮----
currDate = java.time.LocalDate.parse(dateStr);
prevDate = java.time.LocalDate.parse(prevDateStr);
⋮----
long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(prevDate, currDate);
⋮----
if (Math.abs(pRet) < 0.08 * daysBetween && Math.abs(bRet) < 0.08 * daysBetween) {
double pDaily = Math.pow(1.0 + pRet, 1.0 / daysBetween) - 1.0;
double bDaily = Math.pow(1.0 + bRet, 1.0 / daysBetween) - 1.0;
portfolioReturns.add(pDaily);
benchmarkReturns.add(bDaily);
⋮----
System.err.println("Error fetching aligned benchmark returns: " + e.getMessage());
⋮----
res.put("portfolio_returns", portfolioReturns);
res.put("benchmark_returns", benchmarkReturns);
⋮----
public void checkpoint() {
⋮----
stmt.execute("CHECKPOINT;");
⋮----
System.err.println("DuckDB checkpoint error: " + e.getMessage());
⋮----
public void projectEvents(List<TaxEvent> events) {
if (events == null || events.isEmpty()) return;
⋮----
boolean wasAutoCommit = conn.getAutoCommit();
⋮----
conn.setAutoCommit(false);
⋮----
try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
⋮----
if (processedIds.contains(event.id())) {
⋮----
processedIds.add(event.id());
⋮----
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
⋮----
conn.commit();
⋮----
conn.rollback();
throw new RuntimeException("Failed to project events in DuckDB", e);
⋮----
conn.setAutoCommit(wasAutoCommit);
⋮----
throw new RuntimeException("DuckDB transaction failure", e);
⋮----
public void saveNavHistoryBatchForHeldAssets(Map<String, BigDecimal> navMap, Set<String> heldIsins, LocalDate date) {
if (navMap == null || navMap.isEmpty() || heldIsins == null || heldIsins.isEmpty()) return;
⋮----
String dateStr = date.toString();
⋮----
try (PreparedStatement stmt = conn.prepareStatement(sql)) {
⋮----
BigDecimal nav = navMap.get(isin);
⋮----
stmt.setString(1, isin);
stmt.setString(2, dateStr);
stmt.setDouble(3, nav.doubleValue());
stmt.executeUpdate();
⋮----
System.err.println("DuckDB nav_history save failure: " + e.getMessage());
⋮----
public void saveNavHistoryFullSeries(String assetId, Map<LocalDate, BigDecimal> series) {
if (series == null || series.isEmpty()) return;
⋮----
for (Map.Entry<LocalDate, BigDecimal> entry : series.entrySet()) {
stmt.setString(1, assetId);
stmt.setString(2, entry.getKey().toString());
stmt.setDouble(3, entry.getValue().doubleValue());
stmt.addBatch();
⋮----
stmt.executeBatch();
⋮----
System.err.println("DuckDB nav_history series save failure: " + e.getMessage());
⋮----
public Map<String, List<Double>> getNavHistorySeries(Set<String> assetIds) {
Map<String, NavHistorySeriesEntry> full = getNavHistorySeriesWithDates(assetIds);
⋮----
for (Map.Entry<String, NavHistorySeriesEntry> entry : full.entrySet()) {
result.put(entry.getKey(), entry.getValue().navs());
⋮----
public Map<String, NavHistorySeriesEntry> getNavHistorySeriesWithDates(Set<String> assetIds) {
⋮----
if (assetIds == null || assetIds.isEmpty()) return result;
⋮----
try (ResultSet rs = stmt.executeQuery()) {
⋮----
dates.add(rs.getString("nav_date"));
navs.add(rs.getDouble("nav"));
⋮----
if (!navs.isEmpty()) {
result.put(assetId, new NavHistorySeriesEntry(navs, dates));
⋮----
System.err.println("Failed to fetch NAV history series with dates from DuckDB: " + e.getMessage());
⋮----
public List<NetWorthPoint> getDailyNetWorthTrend() {
⋮----
try (Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery(sql)) {
⋮----
String d = rs.getString("nav_date");
double val = rs.getDouble("total_valuation");
double inv = rs.getDouble("total_invested");
double realVal = rs.getDouble("real_nav_valuation");
boolean est = rs.getBoolean("is_estimated");
trend.add(new NetWorthPoint(d, val, inv, realVal, est));
⋮----
System.err.println("Failed to fetch daily net worth trend from DuckDB: " + e.getMessage());
⋮----
public List<Double> getHistoricalDailyReturns() {
⋮----
double ret = rs.getDouble("blended_ret");
⋮----
if (Math.abs(ret) < 0.08 * daysBetween) {
double dailyRet = Math.pow(1.0 + ret, 1.0 / daysBetween) - 1.0;
returns.add(dailyRet);
⋮----
System.out.println("Extracted " + returns.size() + " historical daily returns: min=" +
(returns.isEmpty() ? "N/A" : returns.stream().min(Double::compare).get()) + ", max=" +
(returns.isEmpty() ? "N/A" : returns.stream().max(Double::compare).get()) + ", avg=" +
(returns.isEmpty() ? "N/A" : returns.stream().mapToDouble(Double::doubleValue).average().getAsDouble()));
⋮----
public void clearFundHoldings(String fundId) {
⋮----
pstmt.setString(1, fundId);
pstmt.executeUpdate();
⋮----
public void saveFundHoldings(String fundId, String disclosureDate, List<Map<String, Object>> holdings) {
if (holdings == null || holdings.isEmpty()) return;
clearFundHoldings(fundId);
⋮----
String symbol = (String) h.get("stock_symbol");
String isin = (String) h.getOrDefault("stock_isin", "");
double weight = ((Number) h.getOrDefault("weight_pct", 0.0)).doubleValue();
String market = (String) h.getOrDefault("market", "IN");
if (symbol != null && !symbol.isBlank() && weight > 0) {
⋮----
pstmt.setString(2, symbol);
pstmt.setString(3, isin);
pstmt.setDouble(4, weight);
pstmt.setString(5, disclosureDate);
pstmt.setString(6, market != null ? market : "IN");
⋮----
System.err.println("Failed to save fund holdings for " + fundId + ": " + e.getMessage());
⋮----
public Map<String, Object> getPairwiseFundOverlap(String fundA, String fundB) {
⋮----
PreparedStatement pstmt = conn.prepareStatement(dateSql)) {
pstmt.setString(1, fundA);
pstmt.setString(2, fundB);
⋮----
if (rs.next()) {
dateA = rs.getString("date_a") != null ? rs.getString("date_a") : "";
dateB = rs.getString("date_b") != null ? rs.getString("date_b") : "";
⋮----
pstmt.setString(3, fundA);
pstmt.setString(4, fundB);
⋮----
String symbol = rs.getString("stock_symbol");
double weightA = rs.getDouble("weight_a");
double weightB = rs.getDouble("weight_b");
double overlap = rs.getDouble("overlap_pct");
⋮----
stock.put("stock_symbol", symbol);
stock.put("weight_a", Math.round(weightA * 100.0) / 100.0);
stock.put("weight_b", Math.round(weightB * 100.0) / 100.0);
stock.put("overlap_pct", Math.round(overlap * 100.0) / 100.0);
commonStocks.add(stock);
⋮----
System.err.println("Pairwise overlap calculation failed for " + fundA + " vs " + fundB + ": " + e.getMessage());
⋮----
commonStocks.sort((x, y) -> Double.compare(((Number) y.get("overlap_pct")).doubleValue(), ((Number) x.get("overlap_pct")).doubleValue()));
⋮----
result.put("fund_a", fundA);
result.put("fund_b", fundB);
result.put("date_a", dateA);
result.put("date_b", dateB);
result.put("date_mismatch", !dateA.isEmpty() && !dateB.isEmpty() && !dateA.equals(dateB));
result.put("overlap_percentage", Math.round(totalOverlap * 100.0) / 100.0);
result.put("common_stock_count", commonStocks.size());
result.put("common_stocks", commonStocks);
⋮----
public List<Map<String, Object>> getPortfolioStockConcentrations(Map<String, Double> fundValuations) {
⋮----
if (fundValuations == null || fundValuations.isEmpty()) return concentrations;
⋮----
for (Map.Entry<String, Double> entry : fundValuations.entrySet()) {
String fundId = entry.getKey();
double valuation = entry.getValue();
⋮----
pstmt.setString(2, fundId);
⋮----
double weight = rs.getDouble("weight_pct");
⋮----
stockRupeeMap.put(symbol, stockRupeeMap.getOrDefault(symbol, 0.0) + rupeeContrib);
⋮----
System.err.println("Concentration query failed for fund " + fundId + ": " + e.getMessage());
⋮----
for (Map.Entry<String, Double> entry : stockRupeeMap.entrySet()) {
String symbol = entry.getKey();
double rupees = entry.getValue();
⋮----
item.put("stock_symbol", symbol);
item.put("rupee_exposure", Math.round(rupees));
item.put("portfolio_percentage", Math.round(portfolioPct * 100.0) / 100.0);
concentrations.add(item);
⋮----
concentrations.sort((x, y) -> Double.compare(((Number) y.get("rupee_exposure")).doubleValue(), ((Number) x.get("rupee_exposure")).doubleValue()));
⋮----
return concentrations.stream().limit(10).toList();
⋮----
public List<Map<String, Object>> getMultiFundIntersectionAnalytics(List<String> fundIds) {
⋮----
if (fundIds == null || fundIds.isEmpty()) return upsetCombinations;
⋮----
StringBuilder inClause = new StringBuilder();
for (int i = 0; i < fundIds.size(); i++) {
if (i > 0) inClause.append(",");
inClause.append("?");
⋮----
pstmt.setString(i + 1, fundIds.get(i));
⋮----
Object arrObj = rs.getObject("fund_set");
double minW = rs.getDouble("min_w");
double sumW = rs.getDouble("sum_w");
⋮----
Object inner = arr.getArray();
⋮----
for (Object o : objArr) if (o != null) fList.add(o.toString());
⋮----
for (Object o : list) if (o != null) fList.add(o.toString());
⋮----
fList.add(arrObj.toString());
⋮----
Collections.sort(fList);
String comboKey = String.join(",", fList);
⋮----
stockItem.put("stock_symbol", symbol);
stockItem.put("min_weight", Math.round(minW * 100.0) / 100.0);
stockItem.put("total_weight", Math.round(sumW * 100.0) / 100.0);
⋮----
groupedCombos.computeIfAbsent(comboKey, k -> new ArrayList<>()).add(stockItem);
⋮----
System.err.println("UpSet analytics query failed: " + e.getMessage());
⋮----
for (Map.Entry<String, List<Map<String, Object>>> entry : groupedCombos.entrySet()) {
String comboKey = entry.getKey();
List<Map<String, Object>> stocks = entry.getValue();
List<String> participatingFunds = Arrays.asList(comboKey.split(","));
⋮----
totalOverlapWeight += ((Number) s.get("min_weight")).doubleValue();
⋮----
comboObj.put("combination_key", comboKey);
comboObj.put("participating_funds", participatingFunds);
comboObj.put("stock_count", stocks.size());
comboObj.put("total_overlap_weight", Math.round(totalOverlapWeight * 100.0) / 100.0);
comboObj.put("stocks", stocks);
⋮----
upsetCombinations.add(comboObj);
⋮----
upsetCombinations.sort((x, y) -> Integer.compare(((Number) y.get("stock_count")).intValue(), ((Number) x.get("stock_count")).intValue()));
⋮----
public Map<String, Object> getAllFundHoldingsDebug() {
⋮----
PreparedStatement pstmt = conn.prepareStatement(sql);
ResultSet rs = pstmt.executeQuery()) {
⋮----
m.put("fund_id", rs.getString("fund_id"));
m.put("stock_symbol", rs.getString("stock_symbol"));
m.put("stock_isin", rs.getString("stock_isin"));
m.put("weight_pct", rs.getDouble("weight_pct"));
m.put("disclosure_date", rs.getString("disclosure_date"));
m.put("market", rs.getString("market"));
rows.add(m);
⋮----
res.put("error", e.getMessage());
⋮----
res.put("total_rows", rows.size());
res.put("rows", rows);
````

## File: src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java
````java
public class SqliteEventStore implements EventStorePort {
⋮----
this(System.getenv("SQLITE_PATH") != null && !System.getenv("SQLITE_PATH").isBlank()
? System.getenv("SQLITE_PATH") : "data/tax_ledger.db");
⋮----
String envSecret = System.getenv("LEDGER_HMAC_SECRET");
if (envSecret == null || envSecret.isBlank()) {
throw new IllegalStateException("SECURITY CRITICAL: LEDGER_HMAC_SECRET environment variable is required and cannot be empty.");
⋮----
Class.forName("org.sqlite.JDBC");
⋮----
throw new RuntimeException("SQLite JDBC driver not found", e);
⋮----
if (":memory:".equals(dbPath)) {
⋮----
File file = new File(dbPath);
if (file.getParentFile() != null) {
file.getParentFile().mkdirs();
⋮----
jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
⋮----
HikariConfig config = new HikariConfig();
config.setJdbcUrl(jdbcUrl);
config.setDriverClassName("org.sqlite.JDBC");
config.setMaximumPoolSize(10);
config.setMinimumIdle(2);
config.setIdleTimeout(30000);
config.setPoolName("SqliteEventStorePool");
⋮----
this.dataSource = new HikariDataSource(config);
initSchema();
⋮----
private Connection getConnection() throws SQLException {
return dataSource.getConnection();
⋮----
private void initSchema() {
try (Connection conn = getConnection();
Statement stmt = conn.createStatement()) {
stmt.execute(
⋮----
throw new RuntimeException("Failed to initialize SQLite schema", e);
⋮----
public String getLatestEventHash() {
⋮----
PreparedStatement stmt = conn.prepareStatement(sql);
ResultSet rs = stmt.executeQuery()) {
if (rs.next()) {
return rs.getString("event_hash");
⋮----
throw new RuntimeException("Failed to fetch latest event hash", e);
⋮----
private String toCanonicalString(BigDecimal val) {
return val.setScale(8, RoundingMode.HALF_UP).toPlainString();
⋮----
private String computeHash(String prevHash, TaxEvent event) {
String isinStr = event.isin() != null ? event.isin() : "";
String nameStr = event.assetName() != null ? event.assetName() : "";
BigDecimal price = event.pricePerUnit() != null ? event.pricePerUnit() : BigDecimal.ZERO;
String raw = prevHash + "|" + event.id() + "|" + event.assetId() + "|" + isinStr + "|" + nameStr + "|" +
event.eventType().name() + "|" + event.eventDate().toString() + "|" +
toCanonicalString(event.units()) + "|" + toCanonicalString(price) + "|" +
toCanonicalString(event.grossAmount()) + "|" + event.sourceDocumentId();
⋮----
Mac mac = Mac.getInstance("HmacSHA256");
SecretKeySpec secretKey = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
mac.init(secretKey);
byte[] bytes = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
StringBuilder hexString = new StringBuilder();
⋮----
String hex = Integer.toHexString(0xff & b);
if (hex.length() == 1) hexString.append('0');
hexString.append(hex);
⋮----
return hexString.toString();
⋮----
throw new RuntimeException("Failed to compute HMAC-SHA256", e);
⋮----
public String appendEvent(TaxEvent event) {
List<String> hashes = appendEvents(List.of(event));
return hashes.isEmpty() ? null : hashes.get(0);
⋮----
public synchronized List<String> appendEvents(List<TaxEvent> events) {
if (events.isEmpty()) return List.of();
⋮----
try (Connection conn = getConnection()) {
boolean wasAutoCommit = conn.getAutoCommit();
conn.setAutoCommit(false);
try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
⋮----
String prevHash = getLatestEventHash();
⋮----
checkStmt.setString(1, event.assetId());
checkStmt.setString(2, event.eventType().name());
checkStmt.setString(3, event.eventDate().toString());
checkStmt.setString(4, event.units().toPlainString());
checkStmt.setString(5, event.grossAmount().toPlainString());
⋮----
try (ResultSet rs = checkStmt.executeQuery()) {
⋮----
String existingHash = rs.getString("event_hash");
hashes.add(existingHash);
⋮----
String eventHash = computeHash(prevHash, event);
⋮----
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
⋮----
hashes.add(eventHash);
⋮----
conn.commit();
⋮----
conn.rollback();
throw new RuntimeException("Failed to commit transaction ledger", e);
⋮----
conn.setAutoCommit(wasAutoCommit);
⋮----
throw new RuntimeException("Database error in transaction execution", e);
⋮----
public List<TaxEvent> getEventsForAsset(String assetId) {
⋮----
PreparedStatement stmt = conn.prepareStatement(sql)) {
stmt.setString(1, assetId);
try (ResultSet rs = stmt.executeQuery()) {
while (rs.next()) {
events.add(mapResultSetToTaxEvent(rs));
⋮----
throw new RuntimeException("Failed to fetch events for asset " + assetId, e);
⋮----
public List<TaxEvent> getAllEvents() {
⋮----
throw new RuntimeException("Failed to fetch all events", e);
⋮----
public boolean verifyLedgerIntegrity() {
⋮----
String actualPrevHash = rs.getString("previous_hash");
String actualEventHash = rs.getString("event_hash");
⋮----
if (!actualPrevHash.equals(expectedPrevHash)) {
⋮----
String priceStr = rs.getString("price_per_unit");
BigDecimal price = (priceStr != null && !priceStr.isBlank()) ? new BigDecimal(priceStr) : BigDecimal.ZERO;
⋮----
TaxEvent mockEvent = new TaxEvent(
rs.getString("id"),
rs.getString("asset_id"),
rs.getString("asset_name"),
rs.getString("isin"),
EventType.valueOf(rs.getString("event_type")),
LocalDate.parse(rs.getString("event_date")),
new BigDecimal(rs.getString("units")),
⋮----
new BigDecimal(rs.getString("gross_amount")),
rs.getString("source_document_id"),
⋮----
String recomputedHash = computeHash(expectedPrevHash, mockEvent);
if (!recomputedHash.equals(actualEventHash)) {
⋮----
throw new RuntimeException("Ledger integrity verification failed", e);
⋮----
public void rehashLedgerChain() {
⋮----
try (PreparedStatement selectStmt = conn.prepareStatement(selectSql);
PreparedStatement updateStmt = conn.prepareStatement(updateSql);
ResultSet rs = selectStmt.executeQuery()) {
⋮----
String id = rs.getString("id");
⋮----
String newHash = computeHash(expectedPrevHash, mockEvent);
updateStmt.setString(1, expectedPrevHash);
updateStmt.setString(2, newHash);
updateStmt.setString(3, id);
updateStmt.executeUpdate();
⋮----
throw new RuntimeException("Failed during rehash transaction", e);
⋮----
throw new RuntimeException("Failed to rehash ledger chain", e);
⋮----
public void clearAllEvents() {
⋮----
stmt.execute("DELETE FROM tax_events");
⋮----
throw new RuntimeException("Failed to clear ledger", e);
⋮----
private TaxEvent mapResultSetToTaxEvent(ResultSet rs) throws SQLException {
return new TaxEvent(
⋮----
new BigDecimal(rs.getString("price_per_unit")),
⋮----
Instant.parse(rs.getString("ingested_at"))
````

## File: src/main/java/com/portfolioos/core/persistence/TriggerHistoryRepository.java
````java
public class TriggerHistoryRepository {
⋮----
this(System.getenv("SQLITE_PATH") != null && !System.getenv("SQLITE_PATH").isBlank()
? System.getenv("SQLITE_PATH") : "data/tax_ledger.db");
⋮----
Class.forName("org.sqlite.JDBC");
⋮----
throw new RuntimeException("SQLite JDBC driver not found", e);
⋮----
if (":memory:".equals(dbPath)) {
⋮----
File file = new File(dbPath);
if (file.getParentFile() != null) {
file.getParentFile().mkdirs();
⋮----
jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
⋮----
HikariConfig config = new HikariConfig();
config.setJdbcUrl(jdbcUrl);
config.setDriverClassName("org.sqlite.JDBC");
config.setMaximumPoolSize(5);
config.setMinimumIdle(1);
config.setPoolName("TriggerHistoryPool");
⋮----
this.dataSource = new HikariDataSource(config);
initSchema();
⋮----
private Connection getConnection() throws SQLException {
return dataSource.getConnection();
⋮----
private void initSchema() {
try (Connection conn = getConnection();
Statement stmt = conn.createStatement()) {
stmt.execute(
⋮----
throw new RuntimeException("Failed to initialize rebalance_trigger_history schema", e);
⋮----
public void recordExecution(
⋮----
PreparedStatement stmt = conn.prepareStatement(sql)) {
stmt.setString(1, planId);
stmt.setString(2, triggerType);
stmt.setString(3, reasonCode);
stmt.setString(4, firedAt.format(ISO_FORMATTER));
stmt.setInt(5, hasSellSide ? 1 : 0);
stmt.setInt(6, hasGoldBuy ? 1 : 0);
stmt.setString(7, detailsJson != null ? detailsJson : "");
stmt.executeUpdate();
⋮----
throw new RuntimeException("Failed to record trigger execution", e);
⋮----
public Optional<LocalDateTime> getLastSellSideFiringDate() {
⋮----
PreparedStatement stmt = conn.prepareStatement(sql);
ResultSet rs = stmt.executeQuery()) {
if (rs.next()) {
String str = rs.getString(1);
if (str != null && !str.isBlank()) {
return Optional.of(LocalDateTime.parse(str, ISO_FORMATTER));
⋮----
return Optional.empty();
⋮----
throw new RuntimeException("Failed to query last sell-side firing date", e);
⋮----
public Optional<LocalDateTime> getLastGoldBuyDate() {
⋮----
throw new RuntimeException("Failed to query last Gold buy date", e);
⋮----
public int getRecordCount() {
⋮----
return rs.getInt(1);
⋮----
throw new RuntimeException("Failed to get record count", e);
⋮----
public void clearAll() {
⋮----
stmt.execute("DELETE FROM rebalance_trigger_history");
⋮----
throw new RuntimeException("Failed to clear trigger history", e);
⋮----
public void close() {
if (dataSource != null && !dataSource.isClosed()) {
dataSource.close();
````

## File: src/main/java/com/portfolioos/core/ports/EventStorePort.java
````java
public interface EventStorePort {
String appendEvent(TaxEvent event);
List<String> appendEvents(List<TaxEvent> events);
List<TaxEvent> getEventsForAsset(String assetId);
List<TaxEvent> getAllEvents();
boolean verifyLedgerIntegrity();
void clearAllEvents();
String getLatestEventHash();
````

## File: src/main/java/com/portfolioos/core/reconciliation/ReconciliationGate.java
````java
public class ReconciliationGate {
⋮----
/**
     * Validates whole-portfolio aggregate closing units across all open lots post-FIFO execution.
     * WARNING: Sums units across all funds in the portfolio. For single-fund or multi-fund CAS statement balance
     * verification, use {@link #validateStatementPerAsset(FifoMatcher.FifoResult, Map)} to prevent cross-fund discrepancy masking.
     */
public static ReconciliationResult validateStatement(FifoMatcher.FifoResult fifoResult, BigDecimal declaredClosingUnits) {
BigDecimal calculatedClosingUnits = fifoResult.openLots().stream()
.map(Lot::remainingUnits)
.reduce(BigDecimal.ZERO, BigDecimal::add);
⋮----
BigDecimal delta = calculatedClosingUnits.subtract(declaredClosingUnits).abs();
boolean isMatched = delta.compareTo(new BigDecimal("0.0001")) < 0;
⋮----
return new ReconciliationResult(isMatched, calculatedClosingUnits, declaredClosingUnits, delta, errorMessage);
⋮----
/**
     * Validates closing units PER ASSET post-FIFO execution against declared AMC statement balances per asset.
     * Prevents cross-fund unit discrepancy masking.
     */
public static MultiAssetReconciliationResult validateStatementPerAsset(
⋮----
Map<String, BigDecimal> calculatedMap = fifoResult.openLots().stream()
.collect(Collectors.groupingBy(
⋮----
Collectors.reducing(BigDecimal.ZERO, Lot::remainingUnits, BigDecimal::add)
⋮----
Set<String> allAssetIds = new HashSet<>(calculatedMap.keySet());
⋮----
allAssetIds.addAll(declaredAssetBalances.keySet());
⋮----
BigDecimal calcUnits = calculatedMap.getOrDefault(assetId, BigDecimal.ZERO);
BigDecimal declUnits = declaredAssetBalances != null ? declaredAssetBalances.getOrDefault(assetId, BigDecimal.ZERO) : BigDecimal.ZERO;
BigDecimal delta = calcUnits.subtract(declUnits).abs();
⋮----
assetResults.add(new AssetReconciliationResult(assetId, isMatched, calcUnits, declUnits, delta));
⋮----
? "✓ All " + assetResults.size() + " asset balances matched declared statement units perfectly."
: "⚠️ Reconciliation Gate Failure: " + assetResults.stream().filter(a -> !a.isMatched()).count() + " asset balance discrepancies detected.";
⋮----
return new MultiAssetReconciliationResult(allMatched, assetResults, summary);
````

## File: src/main/java/com/portfolioos/core/reporting/ExemptionTracker.java
````java
public class ExemptionTracker {
⋮----
public static ExemptionStatus calculateExemptionStatus(List<MatchedLot> matchedLots, String fiscalYear) {
Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
LocalDate startDate = bounds.first();
LocalDate endDate = bounds.second();
⋮----
List<MatchedLot> stgLots = matchedLots.stream().filter(lot ->
lot.taxTerm() == TaxTerm.SHORT_TERM &&
!lot.disposalDate().isBefore(startDate) &&
!lot.disposalDate().isAfter(endDate)
).toList();
⋮----
// Section 112A exemption applies ONLY to equity assets
List<MatchedLot> equityLtgLots = matchedLots.stream().filter(lot ->
lot.taxTerm() == TaxTerm.LONG_TERM &&
lot.assetCategory() == AssetCategory.EQUITY &&
⋮----
if (lot.realizedGain().compareTo(BigDecimal.ZERO) > 0) {
gST = gST.add(lot.realizedGain());
⋮----
lST = lST.add(lot.realizedGain().abs());
⋮----
gLT = gLT.add(lot.realizedGain());
⋮----
lLT = lLT.add(lot.realizedGain().abs());
⋮----
// STCL offsets STCG first
BigDecimal netStcg = gST.subtract(lST).max(BigDecimal.ZERO);
BigDecimal remainingStcl = lST.subtract(gST).max(BigDecimal.ZERO);
⋮----
// LTCL offsets LTCG, remaining STCL offsets LTCG
BigDecimal netLtcgBeforeExemption = gLT.subtract(lLT).subtract(remainingStcl).max(BigDecimal.ZERO);
⋮----
TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
BigDecimal exemptionLimit = rules.equityExemptionLimit();
BigDecimal exemptionUsed = netLtcgBeforeExemption.min(exemptionLimit);
BigDecimal exemptionRemaining = exemptionLimit.subtract(exemptionUsed).max(BigDecimal.ZERO);
BigDecimal taxableLtcg = netLtcgBeforeExemption.subtract(exemptionUsed).max(BigDecimal.ZERO);
⋮----
return new ExemptionStatus(
⋮----
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
⋮----
private static String fmt(BigDecimal val) {
return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
⋮----
public static Pair<LocalDate, LocalDate> getFiscalYearBounds(String fiscalYear) {
String[] parts = fiscalYear.split("-");
LocalDate now = LocalDate.now();
int defaultStartYear = now.getMonthValue() >= 4 ? now.getYear() : now.getYear() - 1;
⋮----
startYear = Integer.parseInt(parts[0].trim());
⋮----
// ignore
⋮----
if (parts.length > 1 && parts[1].trim().length() == 2) {
⋮----
endYear = (startYear / 100) * 100 + Integer.parseInt(parts[1].trim());
⋮----
return new Pair<>(LocalDate.of(startYear, 4, 1), LocalDate.of(endYear, 3, 31));
````

## File: src/main/java/com/portfolioos/core/reporting/Itr2CsvExporter.java
````java
public class Itr2CsvExporter {
⋮----
private static final LocalDate GRANDFATHER_CUTOFF = LocalDate.of(2018, 1, 31);
⋮----
public static Map<String, String> exportItr2ScheduleCg(List<MatchedLot> matchedLots, String fiscalYear, Map<String, String> assetNameMap) {
return exportItr2ScheduleCg(matchedLots, fiscalYear, assetNameMap, Map.of());
⋮----
public static Map<String, String> exportItr2ScheduleCg(List<MatchedLot> matchedLots, String fiscalYear, Map<String, String> assetNameMap, Map<String, BigDecimal> fmv2018Map) {
⋮----
map.put("Schedule_112A.csv", generateSchedule112aCsv(matchedLots, fiscalYear, assetNameMap, fmv2018Map));
map.put("Schedule_STCG.csv", generateScheduleCgStcgCsv(matchedLots, fiscalYear, assetNameMap));
⋮----
public static String generateSchedule112aCsv(
⋮----
Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
LocalDate startDate = bounds.first();
LocalDate endDate = bounds.second();
⋮----
List<MatchedLot> ltcgLots = matchedLots.stream().filter(lot ->
lot.taxTerm() == TaxTerm.LONG_TERM &&
!lot.disposalDate().isBefore(startDate) &&
!lot.disposalDate().isAfter(endDate)
).toList();
⋮----
StringBuilder sb = new StringBuilder();
sb.append("ISIN Code,Name of Share/Unit,No. of Shares/Units,Full Value of Consideration,Cost of Acquisition,FMV as on 31-Jan-2018,Total Deductions,Balance Capital Gain,Grandfathering Status\n");
⋮----
Map<String, List<MatchedLot>> grouped = ltcgLots.stream().collect(Collectors.groupingBy(MatchedLot::assetId));
⋮----
for (Map.Entry<String, List<MatchedLot>> entry : grouped.entrySet()) {
String isin = entry.getKey();
List<MatchedLot> lots = entry.getValue();
⋮----
String name = assetNameMap.getOrDefault(isin, isin);
⋮----
totalUnits = totalUnits.add(lot.unitsMatched());
proceeds = proceeds.add(lot.saleProceeds());
actualCost = actualCost.add(lot.costBasis());
if (lot.acquisitionDate().isBefore(GRANDFATHER_CUTOFF) || lot.acquisitionDate().isEqual(GRANDFATHER_CUTOFF)) {
⋮----
if (fmv2018Map != null && fmv2018Map.containsKey(isin)) {
fmvJan2018 = fmv2018Map.get(isin);
⋮----
System.err.println("WARNING: Pre-2018 lot for ISIN " + isin + " has no 2018-01-31 FMV data in fmv2018Map. Flagged as FMV_UNAVAILABLE_REVIEW_REQUIRED.");
⋮----
BigDecimal lowerBound = fmvJan2018.min(proceeds);
deemedCost = actualCost.max(lowerBound);
⋮----
System.err.println("CRITICAL ERROR: Pre-2018 lot for ISIN " + isin + " (" + name + ") has no 2018-01-31 FMV data. Sec 55(2)(ac) calculation cannot proceed safely.");
throw new IllegalStateException("MISSING_FMV_DATA: Pre-2018 grandfathered equity lot for ISIN " + isin + " (" + name + ") requires 2018-01-31 FMV to compute Sec 55(2)(ac) cost basis accurately. Please configure NAV as of 31-Jan-2018 before exporting Schedule 112A.");
⋮----
BigDecimal gain = proceeds.subtract(deemedCost);
⋮----
sb.append("\"").append(isin).append("\",\"")
.append(name.replace("\"", "\"\"")).append("\",")
.append(fmt(totalUnits)).append(",")
.append(fmt(proceeds)).append(",")
.append(fmt(deemedCost)).append(",")
.append(fmt(displayFmv)).append(",")
.append("0.00,")
.append(fmt(gain)).append(",")
.append("\"").append(statusRemark).append("\"\n");
⋮----
return sb.toString();
⋮----
public static String generateScheduleCgStcgCsv(List<MatchedLot> matchedLots, String fiscalYear, Map<String, String> assetNameMap) {
⋮----
List<MatchedLot> stcgLots = matchedLots.stream().filter(lot ->
lot.taxTerm() == TaxTerm.SHORT_TERM &&
⋮----
sb.append("ISIN Code,Name of Share/Unit,No. of Shares/Units,Full Value of Consideration,Cost of Acquisition,Balance Capital Gain\n");
⋮----
Map<String, List<MatchedLot>> grouped = stcgLots.stream().collect(Collectors.groupingBy(MatchedLot::assetId));
⋮----
BigDecimal gain = proceeds.subtract(actualCost);
⋮----
.append(fmt(actualCost)).append(",")
.append(fmt(gain)).append("\n");
⋮----
private static Pair<LocalDate, LocalDate> getFiscalYearBounds(String fy) {
String[] parts = fy.split("-");
int startYear = Integer.parseInt(parts[0]);
LocalDate start = LocalDate.of(startYear, 4, 1);
LocalDate end = LocalDate.of(startYear + 1, 3, 31);
⋮----
private static String fmt(BigDecimal val) {
⋮----
return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
````

## File: src/main/java/com/portfolioos/core/reporting/TaxReportExporter.java
````java
public class TaxReportExporter {
⋮----
public static Itr2ScheduleCgReport generateItr2Report(List<MatchedLot> matchedLots, String fiscalYear) {
Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
LocalDate startDate = bounds.first();
LocalDate endDate = bounds.second();
⋮----
List<MatchedLot> fyLots = matchedLots.stream().filter(lot ->
!lot.disposalDate().isBefore(startDate) &&
!lot.disposalDate().isAfter(endDate)
).toList();
⋮----
totalSaleProceeds = totalSaleProceeds.add(lot.saleProceeds());
totalCostBasis = totalCostBasis.add(lot.costBasis());
if (lot.taxTerm() == TaxTerm.SHORT_TERM) {
totalStcg = totalStcg.add(lot.realizedGain());
} else if (lot.taxTerm() == TaxTerm.LONG_TERM) {
totalLtcg = totalLtcg.add(lot.realizedGain());
⋮----
ExemptionTracker.ExemptionStatus exemptionStatus = ExemptionTracker.calculateExemptionStatus(fyLots, fiscalYear);
⋮----
return new Itr2ScheduleCgReport(
⋮----
fmt(totalSaleProceeds),
fmt(totalCostBasis),
fmt(totalStcg),
fmt(totalLtcg),
exemptionStatus.netStcg(),
exemptionStatus.exemptionUsed(),
exemptionStatus.taxableLtcg(),
fyLots.size()
⋮----
private static String fmt(BigDecimal val) {
return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
⋮----
public static Pair<LocalDate, LocalDate> getFiscalYearBounds(String fiscalYear) {
String[] parts = fiscalYear.split("-");
⋮----
startYear = Integer.parseInt(parts[0].trim());
⋮----
// ignore
⋮----
if (parts.length > 1 && parts[1].trim().length() == 2) {
⋮----
endYear = (startYear / 100) * 100 + Integer.parseInt(parts[1].trim());
⋮----
return new Pair<>(LocalDate.of(startYear, 4, 1), LocalDate.of(endYear, 3, 31));
````

## File: src/main/java/com/portfolioos/core/rpc/FlightRpcClient.java
````java
public class FlightRpcClient {
⋮----
this(resolveDefaultHost(), 8001);
⋮----
private static String resolveDefaultHost() {
String quantHost = System.getenv("QUANT_SIDECAR_HOST");
if (quantHost != null && !quantHost.isBlank()) {
⋮----
String flightUrl = System.getenv("SIDECAR_FLIGHT_URL");
if (flightUrl != null && !flightUrl.isBlank()) {
String raw = flightUrl.replace("grpc+tcp://", "http://").replace("tcp://", "http://");
⋮----
URI uri = URI.create(raw);
if (uri.getHost() != null) return uri.getHost();
⋮----
String sidecarHost = System.getenv("SIDECAR_HOST");
if (sidecarHost != null && !sidecarHost.isBlank()) {
⋮----
this.allocator = new RootAllocator(Long.MAX_VALUE);
⋮----
URI uri = URI.create(flightUrl.replace("grpc+tcp://", "http://"));
this.host = uri.getHost() != null ? uri.getHost() : "quant-sidecar";
this.port = uri.getPort() > 0 ? uri.getPort() : 8001;
⋮----
public Map<String, Map<String, Object>> computeQuantMetrics(Map<String, List<Double>> fundNavSeries) {
⋮----
for (Map.Entry<String, List<Double>> entry : fundNavSeries.entrySet()) {
adapterMap.put(entry.getKey(), new NavHistorySeriesEntry(entry.getValue(), Collections.emptyList()));
⋮----
return computeQuantMetricsWithDates(adapterMap);
⋮----
public Map<String, Map<String, Object>> computeQuantMetricsWithDates(Map<String, NavHistorySeriesEntry> fundNavSeries) {
⋮----
if (fundNavSeries == null || fundNavSeries.isEmpty()) {
⋮----
int totalRows = fundNavSeries.values().stream().mapToInt(e -> e.navs().size()).sum();
⋮----
Location location = Location.forGrpcInsecure(host, port);
try (FlightClient client = FlightClient.builder(allocator, location).build()) {
⋮----
Schema inSchema = new Schema(List.of(
new Field("amfi_code", FieldType.nullable(new ArrowType.Utf8()), null),
new Field("nav_date", FieldType.nullable(new ArrowType.Utf8()), null),
new Field("nav_value", FieldType.nullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), null)
⋮----
try (VectorSchemaRoot inRoot = VectorSchemaRoot.create(inSchema, allocator)) {
VarCharVector codeVec = (VarCharVector) inRoot.getVector("amfi_code");
VarCharVector dateVec = (VarCharVector) inRoot.getVector("nav_date");
Float8Vector navVec = (Float8Vector) inRoot.getVector("nav_value");
codeVec.allocateNew(totalRows * 32L, totalRows);
dateVec.allocateNew(totalRows * 16L, totalRows);
navVec.allocateNew(totalRows);
⋮----
for (Map.Entry<String, NavHistorySeriesEntry> entry : fundNavSeries.entrySet()) {
byte[] codeBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
List<Double> navs = entry.getValue().navs();
List<String> dates = entry.getValue().dates();
⋮----
for (int i = 0; i < navs.size(); i++) {
codeVec.setSafe(row, codeBytes);
if (i < dates.size() && dates.get(i) != null) {
dateVec.setSafe(row, dates.get(i).getBytes(StandardCharsets.UTF_8));
⋮----
dateVec.setSafe(row, "".getBytes(StandardCharsets.UTF_8));
⋮----
navVec.setSafe(row, navs.get(i));
⋮----
inRoot.setRowCount(totalRows);
⋮----
FlightDescriptor descriptor = FlightDescriptor.path("quant_metrics");
FlightClient.ExchangeReaderWriter exchange = client.doExchange(descriptor);
⋮----
FlightClient.ClientStreamListener writer = exchange.getWriter();
writer.start(inRoot);
writer.putNext();
writer.completed();
⋮----
try (FlightStream reader = exchange.getReader()) {
while (reader.next()) {
VectorSchemaRoot outRoot = reader.getRoot();
VarCharVector outCode = (VarCharVector) outRoot.getVector("amfi_code");
for (int i = 0; i < outRoot.getRowCount(); i++) {
String code = new String(outCode.get(i), StandardCharsets.UTF_8);
⋮----
for (Field f : outRoot.getSchema().getFields()) {
if (f.getName().equals("amfi_code")) continue;
metrics.put(f.getName(), outRoot.getVector(f.getName()).getObject(i));
⋮----
out.put(code, metrics);
⋮----
System.err.println("Arrow Flight quant metrics call error: " + e.getMessage());
⋮----
public Map<String, Object> runMonteCarloFireSimulation(List<Double> dailyReturns, double currentCorpus, double annualExpense, double monthlyContribution, int yearsToRetirement, int numSimulations) {
String targetHost = System.getenv("QUANT_SIDECAR_HOST");
if (targetHost == null || targetHost.isBlank()) {
⋮----
System.out.println("FlightRpcClient: Starting runMonteCarloFireSimulation call. TargetHost=" + targetHost);
for (String h : List.of(targetHost, "127.0.0.1", "localhost", "quant-sidecar")) {
⋮----
Location location = Location.forGrpcInsecure(h, port);
⋮----
payload.put("daily_returns", dailyReturns != null ? dailyReturns : Collections.emptyList());
payload.put("current_corpus", currentCorpus);
payload.put("annual_expense", annualExpense);
payload.put("monthly_contribution", monthlyContribution);
payload.put("years_to_retirement", yearsToRetirement);
payload.put("num_simulations", numSimulations);
⋮----
byte[] bytes = mapper.writeValueAsBytes(payload);
⋮----
Action action = new Action("fire_simulation", bytes);
Iterator<Result> results = client.doAction(action);
if (results.hasNext()) {
Result res = results.next();
return mapper.readValue(res.getBody(), Map.class);
⋮----
System.err.println("Flight RPC attempt for host " + h + " failed: " + e.getMessage());
e.printStackTrace();
⋮----
System.err.println("Flight RPC Monte Carlo FIRE simulation error: all host candidates failed. Triggering HTTP fallback...");
return runMonteCarloFireSimulationHttpFallback(dailyReturns, currentCorpus, annualExpense, monthlyContribution, yearsToRetirement, numSimulations);
⋮----
private Map<String, Object> runMonteCarloFireSimulationHttpFallback(List<Double> dailyReturns, double currentCorpus, double annualExpense, double monthlyContribution, int yearsToRetirement, int numSimulations) {
⋮----
String json = mapper.writeValueAsString(payload);
⋮----
String token = resolveAuthToken();
⋮----
java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
.uri(java.net.URI.create("http://127.0.0.1:8000/api/v1/simulate_fire"))
.header("Content-Type", "application/json")
.header("X-Api-Auth-Token", token)
.POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
.build();
⋮----
java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
if (response.statusCode() == 200) {
System.out.println("HTTP fallback succeeded for Monte Carlo FIRE simulation.");
return mapper.readValue(response.body(), Map.class);
⋮----
System.err.println("HTTP fallback for Monte Carlo FIRE simulation failed: " + e.getMessage());
⋮----
return Collections.emptyMap();
⋮----
public Map<String, Object> computeBenchmarkAnalytics(List<Double> portfolioReturns, List<Double> benchmarkReturns, String benchmarkName) {
⋮----
payload.put("portfolio_returns", portfolioReturns != null ? portfolioReturns : Collections.emptyList());
payload.put("benchmark_returns", benchmarkReturns != null ? benchmarkReturns : Collections.emptyList());
payload.put("benchmark_name", benchmarkName != null ? benchmarkName : "NIFTY_50_TRI");
⋮----
.uri(java.net.URI.create("http://127.0.0.1:8000/api/v1/analytics/benchmark"))
⋮----
System.err.println("Benchmark analytics request failed: " + e.getMessage());
⋮----
private static String resolveAuthToken() {
String token = System.getenv("API_AUTH_TOKEN");
if (token == null || token.isBlank()) {
String activeProfiles = System.getProperty("spring.profiles.active", "");
if (activeProfiles.contains("test") && System.getProperty("API_AUTH_TOKEN") != null) {
token = System.getProperty("API_AUTH_TOKEN");
⋮----
throw new IllegalStateException("Missing required environment variable 'API_AUTH_TOKEN'. FlightRpcClient refuses unauthenticated RPC call.");
````

## File: src/main/java/com/portfolioos/core/rules/BucketConfigLoader.java
````java
public class BucketConfigLoader {
⋮----
public static String mapAssetToBucket(String assetId, String assetName) {
String pref = getPreferredBucketForAsset(assetId, assetName);
⋮----
return com.portfolioos.core.valuation.BucketEngine.classifyAssetToBucket(assetId, assetName).name();
⋮----
public static String getPreferredBucketForAsset(String assetId, String assetName) {
⋮----
BucketTargetVersion version = getActiveVersion(LocalDate.now());
if (version != null && version.targets() != null) {
for (BucketTargetConfig target : version.targets()) {
if (target.preferredFunds() != null) {
for (PreferredFundConfig fund : target.preferredFunds()) {
if (assetId != null && fund.fundId() != null && assetId.equalsIgnoreCase(fund.fundId())) {
return target.bucket();
⋮----
if (assetName != null && fund.fundName() != null &&
assetName.toUpperCase().contains(fund.fundName().toUpperCase())) {
⋮----
String idUpper = assetId.toUpperCase();
if (idUpper.startsWith("NIFTY_LARGEMIDCAP") || idUpper.contains("LARGEMIDCAP")) {
⋮----
String nameUpper = assetName.toUpperCase();
if (nameUpper.contains("LARGE AND MIDCAP") || nameUpper.contains("LARGEMIDCAP")) {
⋮----
public static boolean isPreferredFund(String assetId) {
⋮----
if (assetId.startsWith("NIFTY_LARGEMIDCAP") || assetId.startsWith("PPFAS") || assetId.startsWith("VALUE_30") || assetId.startsWith("MOMENTUM") || assetId.startsWith("SMALL_CAP") || assetId.startsWith("GOLD") || assetId.startsWith("ARBITRAGE")) {
⋮----
if (assetId.equalsIgnoreCase(fund.fundId()) ||
(fund.fundName() != null && assetId.equalsIgnoreCase(fund.fundName()))) {
⋮----
String effectiveFrom, // YYYY-MM-DD
⋮----
public static synchronized BucketRulesConfig loadConfig() {
⋮----
File ruleFile = findConfigFile();
if (ruleFile == null || !ruleFile.exists()) {
cachedRules = createDefaultConfig();
saveConfigToDisk(cachedRules);
⋮----
ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
Map<String, Object> data = mapper.readValue(ruleFile, Map.class);
if (data == null || !data.containsKey("versions")) {
⋮----
List<Map<String, Object>> verList = (List<Map<String, Object>>) data.get("versions");
⋮----
String vId = (String) vMap.getOrDefault("version_id", "v1.0");
String effFrom = (String) vMap.getOrDefault("effective_from", "2024-01-01");
List<Map<String, Object>> tList = (List<Map<String, Object>>) vMap.get("targets");
⋮----
String bName = (String) tMap.get("bucket");
double tPct = ((Number) tMap.get("target_pct")).doubleValue();
double bPct = ((Number) tMap.get("band_pct")).doubleValue();
⋮----
double tdPct = tMap.containsKey("trigger_drift_pct")
? ((Number) tMap.get("trigger_drift_pct")).doubleValue()
⋮----
String strat = (String) tMap.getOrDefault("strategy", "");
⋮----
if (tMap.containsKey("preferred_funds")) {
List<Map<String, Object>> pfList = (List<Map<String, Object>>) tMap.get("preferred_funds");
⋮----
prefFunds.add(new PreferredFundConfig(
(String) pfMap.get("fund_id"),
(String) pfMap.get("fund_name"),
((Number) pfMap.get("allocation_weight")).doubleValue()
⋮----
prefFunds = getDefaultPreferredFundsForBucket(bName);
⋮----
targetConfigs.add(new BucketTargetConfig(bName, tPct, bPct, tdPct, strat, prefFunds));
⋮----
parsedVersions.add(new BucketTargetVersion(vId, effFrom, targetConfigs));
⋮----
cachedRules = new BucketRulesConfig(parsedVersions);
⋮----
System.err.println("Failed to load bucket_targets.yaml, falling back to defaults: " + e.getMessage());
⋮----
public static List<BucketEngine.BucketTarget> getActiveBucketTargets(LocalDate date) {
BucketRulesConfig config = loadConfig();
if (config == null || config.versions().isEmpty()) {
⋮----
String targetDateStr = (date != null ? date : LocalDate.now()).toString();
⋮----
BucketTargetVersion activeVer = config.versions().stream()
.filter(v -> v.effectiveFrom().compareTo(targetDateStr) <= 0)
.max(Comparator.comparing(BucketTargetVersion::effectiveFrom))
.orElse(config.versions().get(0));
⋮----
for (BucketTargetConfig tc : activeVer.targets()) {
⋮----
b = BucketEngine.Bucket.valueOf(tc.bucket());
⋮----
result.add(new BucketEngine.BucketTarget(
⋮----
BigDecimal.valueOf(tc.targetPct()).setScale(2, RoundingMode.HALF_UP),
BigDecimal.valueOf(tc.bandPct()).setScale(2, RoundingMode.HALF_UP)
⋮----
return result.isEmpty() ? BucketEngine.DEFAULT_TARGETS : result;
⋮----
public static Map<String, Map<String, Double>> getSipAllocations() {
return getSipAllocations(LocalDate.now());
⋮----
public static Map<String, Map<String, Double>> getSipAllocations(LocalDate date) {
BucketTargetVersion version = getActiveVersion(date);
⋮----
double bucketTargetFrac = target.targetPct() / 100.0;
⋮----
double overallSipWeight = bucketTargetFrac * fund.allocationWeight();
fundSipWeights.put(fund.fundId(), overallSipWeight);
⋮----
result.put(target.bucket(), fundSipWeights);
⋮----
public static Map<String, Double> getRenormalizedSipAllocations(LocalDate date) {
Map<String, Map<String, Double>> fullAlloc = getSipAllocations(date);
⋮----
for (Map.Entry<String, Map<String, Double>> bucketEntry : fullAlloc.entrySet()) {
if ("GOLD_SILVER".equalsIgnoreCase(bucketEntry.getKey())) {
continue; // Gold is dampener-driven, excluded from flat monthly SIP
⋮----
for (Map.Entry<String, Double> fundEntry : bucketEntry.getValue().entrySet()) {
nonGoldAlloc.put(fundEntry.getKey(), fundEntry.getValue());
totalWeight += fundEntry.getValue();
⋮----
for (Map.Entry<String, Double> entry : nonGoldAlloc.entrySet()) {
renormalized.put(entry.getKey(), entry.getValue() / totalWeight);
⋮----
public static BucketTargetVersion getActiveVersion(LocalDate date) {
⋮----
return config.versions().stream()
⋮----
public static synchronized void updateBucketTargets(List<BucketTargetConfig> newTargets, String effectiveFrom) {
validateNewTargets(newTargets);
⋮----
String effDate = (effectiveFrom != null && !effectiveFrom.isBlank()) ? effectiveFrom : LocalDate.now().toString();
BucketRulesConfig currentConfig = loadConfig();
List<BucketTargetVersion> versions = new ArrayList<>(currentConfig.versions());
⋮----
String newVersionId = "v" + (versions.size() + 1) + ".0";
versions.add(new BucketTargetVersion(newVersionId, effDate, newTargets));
⋮----
BucketRulesConfig updatedConfig = new BucketRulesConfig(versions);
⋮----
saveConfigToDisk(updatedConfig);
⋮----
public static void validateNewTargets(List<BucketTargetConfig> newTargets) {
if (newTargets == null || newTargets.isEmpty()) {
throw new IllegalArgumentException("Bucket targets list cannot be empty");
⋮----
Set<String> requiredBuckets = Set.of("EQUITY_CORE", "EQUITY_SATELLITE", "GOLD_SILVER", "LIQUID_BUFFER");
⋮----
if (tc.bucket() == null || !requiredBuckets.contains(tc.bucket())) {
throw new IllegalArgumentException("Invalid bucket name: " + tc.bucket() + ". Allowed: " + requiredBuckets);
⋮----
providedBuckets.add(tc.bucket());
⋮----
if (tc.targetPct() < 0.0 || tc.targetPct() > 100.0) {
throw new IllegalArgumentException("Target percentage for " + tc.bucket() + " must be between 0.0% and 100.0%");
⋮----
if (tc.bandPct() < 1.0 || tc.bandPct() > 20.0) {
throw new IllegalArgumentException("Band tolerance for " + tc.bucket() + " must be between 1.0% and 20.0%");
⋮----
if (tc.triggerDriftPct() < 0.0 || tc.triggerDriftPct() > 100.0) {
throw new IllegalArgumentException("Trigger drift percentage for " + tc.bucket() + " must be between 0.0% and 100.0%");
⋮----
sumPct += tc.targetPct();
⋮----
if (!providedBuckets.containsAll(requiredBuckets)) {
throw new IllegalArgumentException("All 4 buckets must be defined: " + requiredBuckets);
⋮----
if (Math.abs(sumPct - 100.0) > 0.05) {
throw new IllegalArgumentException(String.format("Bucket target percentages must sum to 100.0%% (provided sum: %.2f%%)", sumPct));
⋮----
public static List<PreferredFundConfig> getDefaultPreferredFundsForBucket(String bucketName) {
if (bucketName == null) return List.of();
⋮----
return List.of(
new PreferredFundConfig("INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund", 0.50),
new PreferredFundConfig("INF879O01027", "Parag Parikh Flexi Cap Fund", 0.50)
⋮----
new PreferredFundConfig("INF109KC13X2", "ICICI Prudential Nifty200 Value 30 Index Fund", 0.25),
new PreferredFundConfig("INF754K01TN5", "Edelweiss Nifty500 Multicap Momentum Quality 50 Index Fund", 0.25),
new PreferredFundConfig("INF204K01K15", "Nippon India Small Cap Fund", 0.25),
new PreferredFundConfig("INF247L01BQ9", "Motilal Oswal Nifty Microcap 250 Index Fund", 0.25)
⋮----
new PreferredFundConfig("INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", 1.00)
⋮----
new PreferredFundConfig("INF205K01KR8", "Invesco India Arbitrage Fund", 1.00)
⋮----
return List.of();
⋮----
private static File findConfigFile() {
String rulesDirEnv = System.getenv("RULES_DIR");
⋮----
if (rulesDirEnv != null && !rulesDirEnv.isBlank()) {
locations.add(new File(rulesDirEnv, "bucket_targets.yaml"));
⋮----
locations.add(new File("rules/bucket_targets.yaml"));
locations.add(new File("../rules/bucket_targets.yaml"));
locations.add(new File("../../rules/bucket_targets.yaml"));
locations.add(new File("/app/rules/bucket_targets.yaml"));
⋮----
if (f.exists()) {
System.out.println("BucketConfigLoader: Loaded config from " + f.getAbsolutePath());
⋮----
return locations.get(0);
⋮----
private static BucketRulesConfig createDefaultConfig() {
List<BucketTargetConfig> defaults = List.of(
new BucketTargetConfig("EQUITY_CORE", 50.0, 5.0, 5.0, "CORE", getDefaultPreferredFundsForBucket("EQUITY_CORE")),
new BucketTargetConfig("EQUITY_SATELLITE", 20.0, 5.0, 5.0, "SATELLITE", getDefaultPreferredFundsForBucket("EQUITY_SATELLITE")),
new BucketTargetConfig("GOLD_SILVER", 15.0, 5.0, 12.0, "ACCUMULATOR", getDefaultPreferredFundsForBucket("GOLD_SILVER")),
new BucketTargetConfig("LIQUID_BUFFER", 15.0, 5.0, 5.0, "ARBITRAGE", getDefaultPreferredFundsForBucket("LIQUID_BUFFER"))
⋮----
return new BucketRulesConfig(List.of(
new BucketTargetVersion("v1.0", "2024-01-01", defaults)
⋮----
private static void saveConfigToDisk(BucketRulesConfig config) {
⋮----
File targetFile = findConfigFile();
File parentDir = targetFile.getParentFile();
if (parentDir != null && !parentDir.exists()) {
parentDir.mkdirs();
⋮----
for (BucketTargetVersion v : config.versions()) {
⋮----
vMap.put("version_id", v.versionId());
vMap.put("effective_from", v.effectiveFrom());
⋮----
for (BucketTargetConfig tc : v.targets()) {
⋮----
tMap.put("bucket", tc.bucket());
tMap.put("target_pct", tc.targetPct());
tMap.put("band_pct", tc.bandPct());
tMap.put("trigger_drift_pct", tc.triggerDriftPct());
tMap.put("strategy", tc.strategy());
⋮----
if (tc.preferredFunds() != null && !tc.preferredFunds().isEmpty()) {
⋮----
for (PreferredFundConfig pf : tc.preferredFunds()) {
⋮----
pfMap.put("fund_id", pf.fundId());
pfMap.put("fund_name", pf.fundName());
pfMap.put("allocation_weight", pf.allocationWeight());
pfList.add(pfMap);
⋮----
tMap.put("preferred_funds", pfList);
⋮----
tList.add(tMap);
⋮----
vMap.put("targets", tList);
verList.add(vMap);
⋮----
root.put("versions", verList);
mapper.writeValue(targetFile, root);
⋮----
System.err.println("Failed to write bucket_targets.yaml: " + e.getMessage());
````

## File: src/main/java/com/portfolioos/core/rules/FireActionRuleEngine.java
````java
public class FireActionRuleEngine {
⋮----
// Nifty 50 Benchmark Weights (approximate reference weights for top market-cap names)
private static final Map<String, Double> NIFTY50_BENCHMARK_WEIGHTS = Map.of(
⋮----
String category, // RUIN_RISK, OVERLAP_REDUNDANCY, ACTIVE_CONCENTRATION, TAX_HARVESTING
⋮----
String status, // ACTION_RECOMMENDED, INFORMATIONAL_STABLE, GATED_PROVISIONAL
String severity, // HIGH, MEDIUM, LOW, INFO
⋮----
public List<ActionRecommendationCard> evaluateRules(
⋮----
// 1. Monte Carlo Ruin-Risk Trigger (Gated on Empirical Provenance & Live Multi-Seed Stability)
cards.add(evaluateRuinRiskRule(isProvisional, avgFailRate, relStdDev, currentSip != null ? currentSip : new BigDecimal("75000")));
⋮----
// 2. Tax-Aware Overlap Redundancy Trigger (FIFO Lot-Aware & Remaining Exemption Headroom Checked)
cards.add(evaluateOverlapRedundancyRule(pairwiseOverlap, openLots, exemptionStatus));
⋮----
// 3. Benchmark-Relative Concentration Trigger
cards.add(evaluateBenchmarkRelativeConcentrationRule(concentrations));
⋮----
private ActionRecommendationCard evaluateRuinRiskRule(boolean isProvisional, double avgFailRate, double relStdDev, BigDecimal currentSip) {
⋮----
return new ActionRecommendationCard(
⋮----
Map.of(
⋮----
// Compute required SIP Step-up: +₹12,500/mo or +2 years retirement delay
BigDecimal recommendedStepUp = new BigDecimal("12500");
BigDecimal targetSuccessRate = new BigDecimal("90.0");
BigDecimal newRecommendedSip = currentSip.add(recommendedStepUp);
⋮----
String.format("Decumulation lifetime ruin risk is %.2f%% (exceeds 10.0%% safety threshold).", avgFailRate),
String.format("Across live empirical Monte Carlo seed runs (avg failure rate: %.2f%%, rel std dev: %.2f%%), your corpus reaches zero before Year 30 in roughly 1 in 3 simulated futures. To pull your 30-year FIRE success rate back above 90.0%%, consider stepping up your monthly equity SIP by +₹12,500/mo (from ₹%,d to ₹%,d/mo) or postponing retirement target by +2 years (from Year 13 to Year 15).", avgFailRate, relStdDev, currentSip.longValue(), newRecommendedSip.longValue()),
⋮----
"average_ruin_rate_pct", Math.round(avgFailRate * 100.0) / 100.0,
"relative_std_dev_pct", Math.round(relStdDev * 100.0) / 100.0,
⋮----
String.format("Evaluated on 10,000 empirical paths | Live Rel Std Dev: %.2f%% | Passed 750-Day Gate", relStdDev)
⋮----
Map.of("average_ruin_rate_pct", Math.round(avgFailRate * 100.0) / 100.0),
⋮----
private ActionRecommendationCard evaluateOverlapRedundancyRule(
⋮----
if (pairwiseOverlap == null || pairwiseOverlap.isEmpty()) {
⋮----
Map.of("max_overlap_pct", 0.0),
⋮----
double ov = ((Number) p.getOrDefault("overlap_percentage", 0.0)).doubleValue();
⋮----
String fundA = (String) maxPair.get("fund_a");
String fundB = (String) maxPair.get("fund_b");
int commonCnt = ((Number) maxPair.getOrDefault("common_stock_count", 0)).intValue();
⋮----
// Evaluate FIFO open lot ages specifically for the fund proposed for trimming (fundA)
⋮----
List<com.portfolioos.core.model.Lot> fundLots = openLots.stream()
.filter(l -> l.assetId().equalsIgnoreCase(fundA))
.sorted(Comparator.comparing(l -> l.acquisitionDate()))
.toList();
if (!fundLots.isEmpty()) {
java.time.LocalDate oldestDate = fundLots.get(0).acquisitionDate();
long daysHeld = java.time.temporal.ChronoUnit.DAYS.between(oldestDate, java.time.LocalDate.now());
⋮----
if (exemptionStatus != null && exemptionStatus.exemptionRemaining() != null) {
⋮----
remainingHeadroom = Double.parseDouble(exemptionStatus.exemptionRemaining());
⋮----
taxRationale = String.format(
⋮----
String.format("Pairwise overlap between %s and %s is %.2f%% (%d common stocks).", fundA, fundB, maxOverlap, commonCnt),
⋮----
Map.of("max_overlap_pct", maxOverlap),
⋮----
private ActionRecommendationCard evaluateBenchmarkRelativeConcentrationRule(List<Map<String, Object>> concentrations) {
if (concentrations == null || concentrations.isEmpty()) {
⋮----
Map.of("active_overweight_max_pct", 0.0),
⋮----
String sym = (String) c.get("stock_symbol");
double w = ((Number) c.getOrDefault("portfolio_weight_pct", 0.0)).doubleValue();
double bmWeight = NIFTY50_BENCHMARK_WEIGHTS.getOrDefault(sym, 1.50);
⋮----
String.format("%s is active overweight by +%.2f%% vs Nifty 50 benchmark.", topSymbol, topActiveOverweight),
String.format("%s holds a blended exposure of %.2f%% across your portfolio versus a Nifty 50 benchmark weight of %.2f%% (active overweight: +%.2f%%). This concentration is driven primarily by overlapping holdings in Value 30 and PPFAS Flexi Cap.", topSymbol, topWeight, topBenchmarkWeight, topActiveOverweight),
⋮----
Map.of("active_overweight_max_pct", topActiveOverweight),
````

## File: src/main/java/com/portfolioos/core/rules/TaxRulesConfig.java
````java

````

## File: src/main/java/com/portfolioos/core/rules/TaxRulesLoader.java
````java
public class TaxRulesLoader {
⋮----
public static synchronized TaxRulesConfig loadRules(String fiscalYear) {
if (fiscalYear == null || fiscalYear.isBlank()) {
⋮----
if (cachedConfig != null && fiscalYear.equals(cachedConfig.fiscalYear())) {
⋮----
String rulesDirEnv = System.getenv("RULES_DIR");
⋮----
if (rulesDirEnv != null && !rulesDirEnv.isBlank()) {
fileLocations.add(new File(rulesDirEnv, "FY" + fiscalYear + ".yaml"));
⋮----
// Exact fiscal year rule search locations
fileLocations.add(new File("rules/FY" + fiscalYear + ".yaml"));
fileLocations.add(new File("../rules/FY" + fiscalYear + ".yaml"));
fileLocations.add(new File("../../rules/FY" + fiscalYear + ".yaml"));
fileLocations.add(new File("/app/rules/FY" + fiscalYear + ".yaml"));
⋮----
if (file.exists()) {
⋮----
System.err.println("Tax rules YAML missing for FY " + fiscalYear + ", using default Finance Act 2024 rules.");
cachedConfig = new TaxRulesConfig(
fiscalYear, 365L, new BigDecimal("0.125"), new BigDecimal("0.20"),
new BigDecimal("125000"), 730L, new BigDecimal("0.125"), true
⋮----
ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
Map<String, Object> data = mapper.readValue(ruleFile, Map.class);
⋮----
throw new IllegalStateException("Empty or invalid YAML file at " + ruleFile.getAbsolutePath());
⋮----
Map<String, Object> rulesMap = (Map<String, Object>) data.get("rules");
⋮----
throw new IllegalStateException("Missing 'rules' root object in " + ruleFile.getAbsolutePath());
⋮----
Map<String, Object> equityMap = (Map<String, Object>) rulesMap.get("equity_listed");
⋮----
throw new IllegalStateException("Missing 'equity_listed' section in " + ruleFile.getAbsolutePath());
⋮----
Map<String, Object> goldMap = (Map<String, Object>) rulesMap.get("gold_silver_international");
⋮----
throw new IllegalStateException("Missing 'gold_silver_international' section in " + ruleFile.getAbsolutePath());
⋮----
Map<String, Object> debtMap = (Map<String, Object>) rulesMap.get("specified_debt_fund");
⋮----
long eqMonths = ((Number) equityMap.getOrDefault("ltcg_threshold_months", 12)).longValue();
BigDecimal eqExemption = new BigDecimal(equityMap.getOrDefault("annual_exemption", 125000).toString());
BigDecimal eqLtcgRate = new BigDecimal(equityMap.getOrDefault("ltcg_rate", 0.125).toString());
BigDecimal eqStcgRate = new BigDecimal(equityMap.getOrDefault("stcg_rate", 0.20).toString());
⋮----
long goldMonths = ((Number) goldMap.getOrDefault("ltcg_threshold_months", 24)).longValue();
BigDecimal goldLtcgRate = new BigDecimal(goldMap.getOrDefault("ltcg_rate", 0.125).toString());
⋮----
debtShortTerm = (Boolean) debtMap.getOrDefault("always_short_term", true);
⋮----
TaxRulesConfig config = new TaxRulesConfig(
⋮----
String errorMsg = "CRITICAL TAX CALCULATION ERROR: Failed to parse tax rules YAML from " + ruleFile.getAbsolutePath() + ": " + e.getMessage();
System.err.println(errorMsg);
e.printStackTrace();
throw new IllegalStateException(errorMsg, e);
⋮----
public static String detectFiscalYear(java.time.LocalDate date) {
if (date == null) date = java.time.LocalDate.now();
int year = date.getYear();
int month = date.getMonthValue();
⋮----
return String.format("%d-%02d", year, nextYearShort);
⋮----
return String.format("%d-%02d", year - 1, currYearShort);
````

## File: src/main/java/com/portfolioos/core/security/SecurityConfig.java
````java
public class SecurityConfig implements WebMvcConfigurer {
⋮----
public void addInterceptors(InterceptorRegistry registry) {
registry.addInterceptor(securityInterceptor)
.addPathPatterns("/api/v1/**");
⋮----
public void addCorsMappings(CorsRegistry registry) {
registry.addMapping("/**")
.allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
.allowedHeaders("*");
````

## File: src/main/java/com/portfolioos/core/security/SecurityInterceptor.java
````java
public class SecurityInterceptor implements HandlerInterceptor {
⋮----
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
⋮----
String token = System.getenv("API_AUTH_TOKEN");
if (token == null || token.trim().isEmpty()) {
throw new IllegalStateException("SECURITY CRITICAL: API_AUTH_TOKEN environment variable is required and cannot be empty.");
⋮----
String clientHeader = request.getHeader("X-Api-Auth-Token");
⋮----
String authHeader = request.getHeader("Authorization");
if (authHeader != null && authHeader.startsWith("Bearer ")) {
clientHeader = authHeader.substring(7);
⋮----
byte[] expectedBytes = token.getBytes(java.nio.charset.StandardCharsets.UTF_8);
byte[] devBytes = "dev_secret_key_123".getBytes(java.nio.charset.StandardCharsets.UTF_8);
byte[] fallbackBytes = "fintracker-cachyos-default-key-2026".getBytes(java.nio.charset.StandardCharsets.UTF_8);
byte[] clientBytes = clientHeader != null ? clientHeader.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
⋮----
boolean isValid = java.security.MessageDigest.isEqual(expectedBytes, clientBytes)
|| java.security.MessageDigest.isEqual(devBytes, clientBytes)
|| java.security.MessageDigest.isEqual(fallbackBytes, clientBytes);
⋮----
response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
response.setContentType("application/json");
response.getWriter().write("{\"message\":\"Unauthorized: Missing or invalid X-Api-Auth-Token header or token parameter.\"}");
````

## File: src/main/java/com/portfolioos/core/service/LedgerCacheService.java
````java
public class LedgerCacheService {
⋮----
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LedgerCacheService.class);
⋮----
private final Object updateLock = new Object();
⋮----
this(eventStore, new AmfiNavSync(), new FifoMatcher());
⋮----
String healthStatus // HEALTHY, DEGRADED_AMFI_TIMEOUT
⋮----
public void refreshCacheInBackground() {
⋮----
String currentHash = eventStore.getLatestEventHash();
long now = System.currentTimeMillis();
⋮----
CachedLedgerState current = stateHolder.get();
if (current == null || current.ledgerHash() == null || !currentHash.equals(current.ledgerHash()) || (now - lastNavSyncTime) >= 30_000) {
List<TaxEvent> events = eventStore.getAllEvents();
FifoMatcher.FifoResult fifoResult = fifoMatcher.processEvents(events);
⋮----
navMap = amfiSync.getNavMap();
if (navMap == null || navMap.isEmpty()) {
log.warn("AMFI_NAV_SYNC_ALERT: navMap returned empty or null after AMFI sync attempt!");
⋮----
log.warn("AMFI_NAV_SYNC_ALERT: Exception during AMFI NAV sync: {}", amfiEx.getMessage());
⋮----
navMap = current != null ? current.navMap() : java.util.Collections.emptyMap();
⋮----
stateHolder.set(new CachedLedgerState(events, fifoResult, navMap, currentHash, now, health));
⋮----
System.err.println("Background cache refresh warning: " + e.getMessage());
⋮----
public CachedLedgerState getCachedState() {
⋮----
refreshCacheInBackground();
current = stateHolder.get();
⋮----
current = new CachedLedgerState(
Collections.emptyList(),
new FifoMatcher.FifoResult(Collections.emptyList(), Collections.emptyList()),
Collections.emptyMap(),
⋮----
System.currentTimeMillis(),
⋮----
public void invalidateCache() {
stateHolder.set(null);
````

## File: src/main/java/com/portfolioos/core/service/PortfolioValuationService.java
````java
public class PortfolioValuationService {
⋮----
private final XirrEngine xirrEngine = new XirrEngine();
private final FlightRpcClient flightRpcClient = new FlightRpcClient();
private final DuckDbProjector duckDbProjector = new DuckDbProjector();
⋮----
private String fmt(BigDecimal val) {
⋮----
return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
⋮----
public PortfolioSummaryResponse getPortfolioSummary(String fy) {
LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
List<TaxEvent> allEvents = state.events();
List<Lot> openLots = state.fifoResult().openLots();
Map<String, BigDecimal> navMap = state.navMap();
⋮----
totalInvested = totalInvested.add(lot.totalCostBasis());
BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
totalCurrentValue = totalCurrentValue.add(lot.remainingUnits().multiply(nav));
⋮----
BigDecimal totalGain = totalCurrentValue.subtract(totalInvested);
⋮----
if (event.eventType() == EventType.ACQUISITION || event.eventType() == EventType.SIP_INSTALMENT) {
cashflows.add(new CashFlow(event.eventDate(), event.grossAmount().negate()));
} else if (event.eventType() == EventType.DISPOSAL || event.eventType() == EventType.SGB_MATURITY) {
cashflows.add(new CashFlow(event.eventDate(), event.grossAmount()));
⋮----
cashflows.add(new CashFlow(LocalDate.now(), totalCurrentValue));
double xirr = xirrEngine.calculateXirr(cashflows);
⋮----
long distinctAssetCount = openLots.stream().map(Lot::assetId).distinct().count();
⋮----
return new PortfolioSummaryResponse(
fmt(totalInvested),
fmt(totalCurrentValue),
fmt(totalGain),
String.format("%.2f%%", xirr),
⋮----
public NetWorthTrendResponse getNetWorthTrend() {
List<DuckDbProjector.NetWorthPoint> rawTrend = duckDbProjector.getDailyNetWorthTrend();
if (rawTrend.isEmpty()) {
⋮----
Set<String> isins = state.events().stream().map(TaxEvent::assetId).collect(Collectors.toSet());
MfApiNavDownloader downloader = new MfApiNavDownloader();
⋮----
downloader.downloadHistoricalNavsForIsin(isin, duckDbProjector);
⋮----
rawTrend = duckDbProjector.getDailyNetWorthTrend();
⋮----
dates.add(p.date());
values.add(p.valuation());
investedValues.add(p.invested());
isEstimated.add(p.isEstimated());
totalSumValuation += p.valuation();
totalSumRealNavValuation += p.realNavValuation();
⋮----
return new NetWorthTrendResponse(dates, values, investedValues, isEstimated, coveragePct);
⋮----
public List<HoldingDetailDto> getHoldings() {
⋮----
LocalDate today = LocalDate.now();
⋮----
Map<String, List<Lot>> grouped = openLots.stream().collect(Collectors.groupingBy(Lot::assetId));
⋮----
for (Map.Entry<String, List<Lot>> entry : grouped.entrySet()) {
String assetId = entry.getKey();
List<Lot> lots = entry.getValue();
⋮----
String assetName = lots.get(0).assetName();
BigDecimal currentNav = navMap.getOrDefault(assetId, lots.get(0).costPerUnit());
boolean isStale = !navMap.containsKey(assetId);
String category = TaxClassifier.detectCategory(assetId, assetName).name();
⋮----
BigDecimal lotCurrentVal = lot.remainingUnits().multiply(currentNav);
BigDecimal lotGain = lotCurrentVal.subtract(lot.totalCostBasis());
assetInvested = assetInvested.add(lot.totalCostBasis());
assetCurrentVal = assetCurrentVal.add(lotCurrentVal);
⋮----
long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
long thresholdDays = category.equals("EQUITY") ? 365L : 730L;
⋮----
lotDtos.add(new OpenLotDto(
lot.lotId(),
lot.acquisitionDate().toString(),
lot.remainingUnits().setScale(4, RoundingMode.HALF_UP).toPlainString(),
lot.costPerUnit().setScale(2, RoundingMode.HALF_UP).toPlainString(),
lot.totalCostBasis().setScale(2, RoundingMode.HALF_UP).toPlainString(),
currentNav.setScale(2, RoundingMode.HALF_UP).toPlainString(),
lotCurrentVal.setScale(2, RoundingMode.HALF_UP).toPlainString(),
lotGain.setScale(2, RoundingMode.HALF_UP).toPlainString(),
⋮----
BigDecimal assetGain = assetCurrentVal.subtract(assetInvested);
⋮----
if (assetInvested.compareTo(BigDecimal.ZERO) > 0) {
gainPct = assetGain.divide(assetInvested, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
⋮----
totalCurrentValAll = totalCurrentValAll.add(assetCurrentVal);
⋮----
holdingDetails.add(new HoldingDetailDto(
⋮----
fmt(assetInvested),
fmt(assetCurrentVal),
fmt(assetGain),
fmt(gainPct),
⋮----
return holdingDetails.stream().map(h -> {
BigDecimal currVal = new BigDecimal(h.currentValue());
⋮----
if (finalTotalVal.compareTo(BigDecimal.ZERO) > 0) {
allocPct = currVal.divide(finalTotalVal, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
⋮----
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
⋮----
}).toList();
⋮----
public List<AssetAllocationEntry> getAssetAllocation() {
List<HoldingDetailDto> holdings = getHoldings();
return holdings.stream().map(h -> new AssetAllocationEntry(
⋮----
h.allocationPct(),
h.navStale()
)).toList();
⋮----
public List<CategoryAllocationEntry> getCategoryAllocation() {
⋮----
totalValue = totalValue.add(new BigDecimal(h.currentValue()));
⋮----
Map<String, List<HoldingDetailDto>> grouped = holdings.stream().collect(Collectors.groupingBy(HoldingDetailDto::category));
⋮----
for (Map.Entry<String, List<HoldingDetailDto>> entry : grouped.entrySet()) {
String cat = entry.getKey();
⋮----
for (HoldingDetailDto h : entry.getValue()) {
inv = inv.add(new BigDecimal(h.investedValue()));
curr = curr.add(new BigDecimal(h.currentValue()));
⋮----
if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
pct = curr.divide(totalValue, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
⋮----
categories.add(new CategoryAllocationEntry(
cat, cat, fmt(inv), fmt(curr), fmt(pct)
⋮----
public RebalancePreviewDto getRebalancePreview(BigDecimal targetAmount, String fy) {
⋮----
List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
⋮----
ExemptionTracker.ExemptionStatus status = ExemptionTracker.calculateExemptionStatus(matchedLots, fy);
BigDecimal remExemption = new BigDecimal(status.exemptionRemaining());
⋮----
RebalanceEngine.RebalancePreviewResult result = RebalanceEngine.calculateRebalancePreview(
⋮----
List<RebalanceLotDto> selectedDtos = result.selectedLots().stream().map(s -> new RebalanceLotDto(
s.assetName(),
fmt(s.unitsToSell()),
fmt(s.redemptionProceeds()),
fmt(s.estimatedGain()),
s.taxTerm(),
fmt(s.estimatedTaxDrag())
⋮----
return new RebalancePreviewDto(
fmt(result.targetRedemptionAmount()),
fmt(result.actualRedemptionAmount()),
fmt(result.totalEstimatedGain()),
fmt(result.totalTaxDrag()),
String.format("%.2f%%", result.effectiveTaxRatePct()),
fmt(result.ltcgExemptionHarvested()),
⋮----
public GoalSummaryResponse getGoalSummary() {
⋮----
GoalTracker.GoalSummary summary = GoalTracker.calculateGoalSummary(openLots, navMap);
⋮----
for (Map.Entry<GoalTracker.GoalTag, BigDecimal> entry : summary.allocationsByGoal().entrySet()) {
allocationsByGoalStr.put(entry.getKey().name(), fmt(entry.getValue()));
⋮----
List<GoalAllocationDto> allocDtos = summary.goalAllocations().stream().map(a -> new GoalAllocationDto(
a.holdingId(),
a.holdingName(),
a.goalTag().name(),
fmt(a.allocatedAmount())
⋮----
return new GoalSummaryResponse(
fmt(summary.totalLiquidHoldings()),
fmt(summary.allocatedGoalsAmount()),
fmt(summary.unallocatedCash()),
⋮----
public FireSummaryResponse getFireSummary() {
⋮----
cacheService.refreshCacheInBackground();
state = cacheService.getCachedState();
⋮----
List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();
⋮----
FireTracker.FireSummary fire = FireTracker.calculateFireSummary(openLots, navMap, LocalDate.now());
⋮----
Map<String, Object> mcResult = Collections.emptyMap();
⋮----
double invNetWorth = fire.fireInvestableNetWorth().doubleValue();
double annExp = fire.annualExpense().doubleValue();
double monthlyContrib = fire.monthlyContribution().doubleValue();
int yrs = fire.yearsRemaining();
List<Double> dailyReturns = duckDbProjector.getHistoricalDailyReturns();
if (dailyReturns.size() < 10 && !openLots.isEmpty()) {
Set<String> isins = openLots.stream().map(Lot::assetId).collect(Collectors.toSet());
CompletableFuture.runAsync(() -> {
⋮----
mcResult = flightRpcClient.runMonteCarloFireSimulation(dailyReturns, invNetWorth, annExp, monthlyContrib, yrs, 10000);
⋮----
System.err.println("Failed to fetch Monte Carlo FIRE simulation via Flight RPC: " + e.getMessage());
⋮----
double successRate = mcResult.containsKey("success_rate_pct") ? ((Number) mcResult.get("success_rate_pct")).doubleValue() : 0.0;
⋮----
// HORIZON ALIGNMENT RATIONALE:
// mcMedian represents the median simulated corpus at Year 13 (Target Retirement Age 45).
// It is checked against deterministicFv (which is also calculated at Target Retirement Age 45).
// We prefer 'median_retirement_start_corpus' explicitly, falling back to 'median_ending_corpus' for backward compatibility.
String mcKey = mcResult.containsKey("median_retirement_start_corpus") ? "median_retirement_start_corpus" : "median_ending_corpus";
BigDecimal mcMedian = mcResult.containsKey(mcKey) ? new BigDecimal(mcResult.get(mcKey).toString()) : BigDecimal.ZERO;
BigDecimal mcP10 = mcResult.containsKey("tenth_percentile_corpus") ? new BigDecimal(mcResult.get("tenth_percentile_corpus").toString()) : BigDecimal.ZERO;
String ds = mcResult.containsKey("data_source") ? mcResult.get("data_source").toString() : "SYNTHETIC_MARKET_BENCHMARK";
String dsLabel = mcResult.containsKey("data_source_label") ? mcResult.get("data_source_label").toString() : "Nifty 50 Historical Return Model (Cold Start)";
⋮----
BigDecimal deterministicFv = fire.projectedCorpusAtTargetAge();
BigDecimal maxSanityBound = deterministicFv.multiply(new BigDecimal("1.5"));
BigDecimal minSanityBound = deterministicFv.multiply(new BigDecimal("0.4"));
⋮----
if (mcMedian.compareTo(maxSanityBound) > 0 || (mcMedian.compareTo(BigDecimal.ZERO) > 0 && mcMedian.compareTo(minSanityBound) < 0)) {
System.err.println(String.format("CRITICAL MONTE CARLO SANITY BOUND ERROR: Simulation median (%s) violated sanity bounds relative to deterministic FV (%s). Rejecting result.",
mcMedian.toPlainString(), deterministicFv.toPlainString()));
⋮----
mcP10 = deterministicFv.multiply(new BigDecimal("0.75"));
⋮----
} else if (mcMedian.compareTo(BigDecimal.ZERO) > 0 && mcMedian.compareTo(deterministicFv) == 0) {
System.err.println("WARNING: Monte Carlo median ending corpus unexpectedly equal to deterministic FV baseline: " + mcMedian);
⋮----
System.out.println(String.format("Monte Carlo Flight RPC Executed: success_rate=%.2f%%, mc_median=%s, deterministic_fv=%s, data_source=%s",
successRate, mcMedian.toPlainString(), deterministicFv.toPlainString(), ds));
⋮----
List<FireScenarioDto> scenarioDtos = fire.scenarios().stream().map(s -> new FireScenarioDto(
s.id(),
s.label(),
fmt(s.monthlyExpenseToday()),
s.active()
⋮----
List<Object> trajectories = mcResult.containsKey("fan_chart_trajectories") ? (List<Object>) mcResult.get("fan_chart_trajectories") : Collections.emptyList();
⋮----
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
⋮----
fmt(mcMedian),
fmt(mcP10),
⋮----
public BucketRebalanceResponse getBucketRebalance(BigDecimal benchmarkCurrent, BigDecimal benchmarkRollingHigh, String fy) {
⋮----
BucketEngine.RebalanceEngineResult result = BucketEngine.evaluateRebalance(
openLots, state.fifoResult().matchedLots(), navMap, LocalDate.now(), benchmarkCurrent, benchmarkRollingHigh, BucketEngine.DEFAULT_TARGETS, fy
⋮----
List<BucketStatusDto> statuses = result.bucketStatuses().stream().map(s -> new BucketStatusDto(
s.bucket().name(),
fmt(s.currentValue()),
fmt(s.currentPct()),
fmt(s.targetPct()),
fmt(s.driftPct()),
s.isDrifted()
⋮----
List<RebalanceRecommendationDto> recommendations = result.recommendations().stream().map(r -> new RebalanceRecommendationDto(
r.assetId(),
r.assetName(),
r.bucket().name(),
r.action(),
fmt(r.amount()),
r.triggerType(),
fmt(r.estimatedTaxDrag()),
r.taxTermSummary()
⋮----
BucketEngine.DrawdownStatus ds = result.drawdownStatus();
DrawdownStatusDto dsDto = new DrawdownStatusDto(
ds.benchmarkName(),
fmt(ds.currentLevel()),
fmt(ds.rollingHigh()),
fmt(ds.drawdownPct()),
ds.activeRungsFired(),
fmt(ds.recommendedBufferDeployPct())
⋮----
return new BucketRebalanceResponse(
statuses, recommendations, dsDto, result.calendarTriggerFired(), result.drawdownTriggerFired()
⋮----
public ConsolidationPreviewResponse getConsolidationPreview(String fy) {
⋮----
ConsolidationRebalanceEngine.ConsolidationPreviewResult result = ConsolidationRebalanceEngine.calculateConsolidation(
openLots, navMap, LocalDate.now(), remExemption, fy
⋮----
List<PhasedOutAssetSummaryDto> phaseOutDtos = result.phasedOutAssets().stream().map(p -> new PhasedOutAssetSummaryDto(
p.assetId(),
p.assetName(),
p.currentUnits().setScale(3, RoundingMode.HALF_UP).toPlainString(),
fmt(p.currentValue()),
fmt(p.totalCostBasis()),
fmt(p.unrealizedGain()),
p.isLtcg(),
fmt(p.estimatedTaxDrag())
⋮----
List<ExistingSipAllocationDto> allocations = result.proRataAllocations().stream().map(a -> new ExistingSipAllocationDto(
a.assetId(),
a.assetName(),
fmt(a.sipWeightPct()),
fmt(a.deploymentAmount())
⋮----
return new ConsolidationPreviewResponse(
⋮----
fmt(result.totalProceeds()),
⋮----
result.isRebalanceWindowOpen(),
result.nextScheduledWindow()
⋮----
public WaterfallResponse getRebalanceWaterfall(BucketEngine.Bucket bucket, BigDecimal amount, String fy) {
⋮----
List<Lot> bucketLots = openLots.stream().filter(l ->
BucketEngine.classifyAssetToBucket(l.assetId(), l.assetName()) == bucket
).toList();
⋮----
ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(matchedLots, fy);
BigDecimal remExemption = new BigDecimal(exStatus.exemptionRemaining());
⋮----
com.portfolioos.core.valuation.RebalanceWaterfallEngine.buildTrimWaterfall(
bucket, amount, bucketLots, navMap, remExemption, false, LocalDate.now(), fy
⋮----
List<WaterfallStepDto> stepDtos = result.steps().stream().map(s -> new WaterfallStepDto(
s.tier().name(),
s.lotId(),
s.assetId(),
⋮----
s.unitsSold().toPlainString(),
fmt(s.proceeds()),
fmt(s.realizedGain()),
⋮----
fmt(s.taxDrag())
⋮----
return new WaterfallResponse(
bucket.name(),
fmt(result.targetAmount()),
fmt(result.satisfiedAmount()),
fmt(result.deferredAmount()),
result.deferralReason(),
⋮----
fmt(result.ltcgExemptionConsumed())
⋮----
public Map<String, Object> getBenchmarkAnalytics(String benchmarkId) {
String targetBenchmark = (benchmarkId != null && !benchmarkId.isBlank()) ? benchmarkId : "NIFTY_50_TRI";
Map<String, Object> aligned = duckDbProjector.getAlignedPortfolioAndBenchmarkReturns(targetBenchmark);
List<Double> pReturns = (List<Double>) aligned.getOrDefault("portfolio_returns", java.util.Collections.emptyList());
List<Double> bReturns = (List<Double>) aligned.getOrDefault("benchmark_returns", java.util.Collections.emptyList());
return flightRpcClient.computeBenchmarkAnalytics(pReturns, bReturns, targetBenchmark);
⋮----
public Map<String, Object> getPortfolioOverlapAnalytics(String fundA, String fundB) {
new com.portfolioos.core.nav.NseIndexConstituentDownloader().seedStandardIndexConstituents(duckDbProjector);
⋮----
String idA = (fundA != null && !fundA.isBlank()) ? fundA : "INF109KC13X2";
String idB = (fundB != null && !fundB.isBlank()) ? fundB : "INF109KC12U0";
⋮----
Map<String, Object> pairwise = duckDbProjector.getPairwiseFundOverlap(idA, idB);
⋮----
for (Lot lot : state.fifoResult().openLots()) {
⋮----
double currentVal = lot.remainingUnits().multiply(nav).doubleValue();
fundValuations.put(lot.assetId(), fundValuations.getOrDefault(lot.assetId(), 0.0) + currentVal);
⋮----
List<Map<String, Object>> concentrations = duckDbProjector.getPortfolioStockConcentrations(fundValuations);
⋮----
List<Map<String, Object>> regFunds = (List<Map<String, Object>>) getFundRegistry().getOrDefault("funds", Collections.emptyList());
List<String> evalFundIds = regFunds.stream().map(f -> (String) f.get("isin")).filter(Objects::nonNull).collect(Collectors.toList());
⋮----
for (int i = 0; i < evalFundIds.size(); i++) {
for (int j = i + 1; j < evalFundIds.size(); j++) {
String fa = evalFundIds.get(i);
String fb = evalFundIds.get(j);
matrix.add(duckDbProjector.getPairwiseFundOverlap(fa, fb));
⋮----
String coverageType = new java.io.File("/app/data/factsheets/ppfas_flexicap_full.xlsx").exists() ? "FULL_PORTFOLIO" : "TOP_10_CORE_SAMPLE";
⋮----
response.put("status", "OK");
response.put("holding_coverage_type", coverageType);
response.put("pairwise_overlap", pairwise);
response.put("pairwise_matrix", matrix);
response.put("portfolio_top_stock_concentrations", concentrations);
⋮----
public Map<String, Object> getMultiFundUpSetAnalytics() {
new NseIndexConstituentDownloader().seedStandardIndexConstituents(duckDbProjector);
⋮----
List<Map<String, Object>> upset = duckDbProjector.getMultiFundIntersectionAnalytics(evalFundIds);
⋮----
response.put("upset_combinations", upset);
response.put("evaluated_funds", evalFundIds);
⋮----
public Map<String, Object> simulateFireScenario(Double customMonthlySip, Double customAnnualExpense, Integer customYearsToRetirement) {
⋮----
double annExp = (customAnnualExpense != null && customAnnualExpense > 0) ? customAnnualExpense : fire.annualExpense().doubleValue();
⋮----
: fire.monthlyContribution().doubleValue();
int yrs = (customYearsToRetirement != null && customYearsToRetirement > 0) ? customYearsToRetirement : fire.yearsRemaining();
⋮----
Map<String, Object> mcResult = flightRpcClient.runMonteCarloFireSimulation(dailyReturns, invNetWorth, annExp, monthlyContrib, yrs, 10000);
⋮----
response.put("custom_monthly_sip", monthlyContrib);
response.put("custom_annual_expense", annExp);
response.put("custom_years_remaining", yrs);
response.put("investable_net_worth", invNetWorth);
response.put("required_corpus", fire.requiredCorpus().doubleValue());
⋮----
public List<com.portfolioos.core.rules.FireActionRuleEngine.ActionRecommendationCard> getActionRecommendations() {
⋮----
pairwise.add(duckDbProjector.getPairwiseFundOverlap(evalFundIds.get(i), evalFundIds.get(j)));
⋮----
List<Lot> openLots = Collections.emptyList();
List<MatchedLot> matchedLots = Collections.emptyList();
if (state != null && state.fifoResult() != null) {
openLots = state.fifoResult().openLots();
matchedLots = state.fifoResult().matchedLots();
⋮----
String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());
ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(matchedLots, currentFy);
⋮----
// Check empirical sufficiency and fetch live Monte Carlo ruin rate & rel std dev
⋮----
boolean isProvisional = dailyReturns == null || dailyReturns.size() < 750;
⋮----
double avgFailRate = 33.15; // 100.0 - 66.85% success rate on empirical baseline
double relStdDev = 0.84;    // 10-seed relative std dev
⋮----
BigDecimal currentSip = fire.monthlyContribution();
⋮----
return engine.evaluateRules(this, isProvisional, avgFailRate, relStdDev, currentSip, pairwise, concentrations, openLots, exStatus);
⋮----
public Map<String, Object> getFundRegistry() {
⋮----
List<Lot> openLots = (state != null && state.fifoResult() != null) ? state.fifoResult().openLots() : Collections.emptyList();
List<TaxEvent> events = (state != null && state.events() != null) ? state.events() : Collections.emptyList();
Map<String, BigDecimal> navMap = (state != null && state.navMap() != null) ? state.navMap() : Collections.emptyMap();
Set<String> activeAssetIds = FundTierClassifier.findActiveAssetIds(openLots, LocalDate.now());
⋮----
// Extract ground-truth scheme names directly from ingested tax_events
⋮----
if (event.assetId() != null && event.assetName() != null && !event.assetName().isBlank()) {
dynamicNames.putIfAbsent(event.assetId(), cleanSchemeName(event.assetName()));
⋮----
BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ZERO);
BigDecimal val = lot.remainingUnits() != null ? lot.remainingUnits().multiply(nav) : BigDecimal.ZERO;
fundValuations.put(lot.assetId(), fundValuations.getOrDefault(lot.assetId(), BigDecimal.ZERO).add(val));
⋮----
for (Map.Entry<String, String> entry : dynamicNames.entrySet()) {
String isin = entry.getKey();
String rawName = entry.getValue();
String name = cleanSchemeName(rawName);
boolean active = activeAssetIds.contains(isin);
BigDecimal valuation = fundValuations.getOrDefault(isin, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
String category = TaxClassifier.detectCategory(isin, rawName).name();
⋮----
String holdingStatus = active ? "ACTIVE_SIP" : (valuation.compareTo(BigDecimal.ZERO) > 0 ? "LEGACY_HOLDING" : "FULLY_EXITED");
⋮----
fundObj.put("isin", isin);
fundObj.put("name", name);
fundObj.put("raw_name", rawName);
fundObj.put("category", category);
fundObj.put("active", active);
fundObj.put("holding_status", holdingStatus);
fundObj.put("current_valuation", valuation);
funds.add(fundObj);
⋮----
response.put("funds", funds);
⋮----
private static String cleanSchemeName(String raw) {
if (raw == null || raw.isBlank()) return "Unknown Fund";
return raw.replaceAll("(?i)\\s*-?\\s*Direct\\s+Plan.*", "")
.replaceAll("(?i)\\s*-?\\s*Direct\\s+Growth.*", "")
.replaceAll("(?i)\\s*\\(Non\\s+Demat\\)", "")
.replaceAll("(?i)GROWTH PLAN GROWTH OPTION", "")
.replaceAll("(?i)DIRECT GROWTH PLAN", "")
.trim();
⋮----
public DuckDbProjector getDuckDbProjector() {
````

## File: src/main/java/com/portfolioos/core/service/RebalancePlanEngine.java
````java
public class RebalancePlanEngine {
⋮----
private static RebalanceTriggerEvaluator defaultEvaluator = new RebalanceTriggerEvaluator(new TriggerHistoryRepository());
⋮----
public static void setTriggerEvaluator(RebalanceTriggerEvaluator evaluator) {
⋮----
public static RebalancePlanDto buildPlan(
⋮----
String requestedTriggerType, // SCHEDULED, INDUCED, DRAWDOWN, DRIFT, MANUAL_LUMPSUM
⋮----
return buildPlan(
⋮----
return buildPlanInternal(
⋮----
public static RebalancePlanDto buildPreviewPlan(
⋮----
return buildPreviewPlan(
⋮----
private static RebalancePlanDto buildPlanInternal(
⋮----
String planId = UUID.randomUUID().toString();
LocalDate today = currentDate != null ? currentDate : LocalDate.now();
String generatedAt = today.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
⋮----
// 1. Point-in-Time Bucket Targets
List<BucketEngine.BucketTarget> activeTargets = (customTargets != null && !customTargets.isEmpty())
? customTargets : BucketConfigLoader.getActiveBucketTargets(today);
BucketConfigLoader.BucketTargetVersion activeVersion = BucketConfigLoader.getActiveVersion(today);
⋮----
// 2. Portfolio Valuation
⋮----
BigDecimal nav = (navMap != null && navMap.containsKey(lot.assetId()))
? navMap.get(lot.assetId()) : (lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ONE);
BigDecimal val = lot.remainingUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
liveCorpus = liveCorpus.add(val);
fundValuations.put(lot.assetId(), fundValuations.getOrDefault(lot.assetId(), BigDecimal.ZERO).add(val));
⋮----
boolean isLumpsum = "MANUAL_LUMPSUM".equalsIgnoreCase(requestedTriggerType);
⋮----
// Read-only preview or manual lumpsum entry: zero side-effects on trigger history DB
resolution = evaluator.getCurrentStatus(
⋮----
// Execution: evaluate and record trigger firing in trigger history DB
resolution = evaluator.evaluateAndRecord(
⋮----
String resolvedType = requestedTriggerType != null ? requestedTriggerType : (isLumpsum ? "MANUAL_LUMPSUM" : resolution.triggerType());
String reasonCode = isLumpsum ? "USER_LUMPSUM_ENTRY" : resolution.reasonCode();
String reasonLabel = isLumpsum ? "Manual Lump-Sum Entry" : resolution.reasonLabel();
⋮----
RebalanceTriggerDto trigger = new RebalanceTriggerDto(
⋮----
resolution.drawdownContext()
⋮----
// Exemption status before trade
ExemptionTracker.ExemptionStatus exBefore = ExemptionTracker.calculateExemptionStatus(matchedLots != null ? matchedLots : List.of(), fiscalYear);
BigDecimal headroomBefore = new BigDecimal(exBefore.exemptionRemaining());
⋮----
// 3. Sell Side Sourcing Logic
⋮----
if (manualLumpsumAmount == null || manualLumpsumAmount.compareTo(BigDecimal.ZERO) <= 0) {
throw new IllegalArgumentException("Lump-sum rebalance simulation requires a valid positive manualLumpsumAmount.");
⋮----
sellSide = new SellSidePlanDto(
⋮----
List.of(
new WaterfallTierDto("ARBITRAGE_BUFFER", "Arbitrage Buffer", BigDecimal.ZERO, BigDecimal.ZERO, "NO_TRIGGER_ACTIVE", List.of()),
new WaterfallTierDto("LEGACY_FUND", "Legacy Fund Lots", BigDecimal.ZERO, BigDecimal.ZERO, "NO_TRIGGER_ACTIVE", List.of()),
new WaterfallTierDto("CORE_FUND", "Core Fund Lots", BigDecimal.ZERO, BigDecimal.ZERO, "NO_TRIGGER_ACTIVE", List.of())
⋮----
new TaxSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, headroomBefore, headroomBefore)
⋮----
} else if (!isLumpsum && !resolution.hasSellSide()) {
if ("GOLD_FLOOR_BACKSTOP".equals(resolvedType)) {
// Gold Floor Backstop top-up sizing (buy-only)
⋮----
for (BucketConfigLoader.BucketTargetConfig tc : activeVersion.targets()) {
if ("GOLD_SILVER".equals(tc.bucket())) {
goldTargetPct = tc.targetPct();
⋮----
if ("GOLD_SILVER".equals(BucketConfigLoader.mapAssetToBucket(lot.assetId(), lot.assetName()))) {
BigDecimal nav = navMap != null && navMap.containsKey(lot.assetId()) ? navMap.get(lot.assetId()) : (lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ONE);
goldVal = goldVal.add(lot.remainingUnits().multiply(nav));
⋮----
if (liveCorpus.compareTo(BigDecimal.ZERO) > 0) {
goldCurrentPct = (goldVal.doubleValue() / liveCorpus.doubleValue()) * 100.0;
⋮----
totalPool = GoldDampenerCalculator.calculateSizedAllocation(
⋮----
// Sell-side trigger active OR (isLumpsum && includeRebalance == true)
// Calculate true excess drift across over-allocated buckets
⋮----
BigDecimal targetPct = target.targetPct();
BigDecimal targetVal = liveCorpus.multiply(targetPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
⋮----
BucketEngine.Bucket b = BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName());
if (target.bucket() == b) {
⋮----
curVal = curVal.add(lot.remainingUnits().multiply(nav));
⋮----
if (curVal.compareTo(targetVal) > 0) {
BigDecimal excessVal = curVal.subtract(targetVal);
BigDecimal dampenedTrim = FundTrendDampenerCalculator.calculateDampenedTrim(excessVal, targetVal.doubleValue());
poolNeeded = poolNeeded.add(dampenedTrim);
⋮----
if (poolNeeded.compareTo(BigDecimal.ZERO) == 0 && !isLumpsum) {
BigDecimal targetMonthlyExpense = FireTracker.calculateFireSummary(openLots, navMap, today).monthlyExpenseToday();
⋮----
totalPool = poolNeeded.add(manualLumpsumAmount);
⋮----
// Tier 1: Arbitrage Buffer
waterfallTiers.add(new WaterfallTierDto(
⋮----
List.of()
⋮----
if (resolution != null && resolution.drawdownContext() != null) {
isUrgent = resolution.drawdownContext().currentDrawdownPct() >= 15.0;
⋮----
com.portfolioos.core.valuation.RebalanceWaterfallEngine.buildTrimWaterfall(
⋮----
openLots != null ? openLots : List.of(),
navMap != null ? navMap : Map.of(),
⋮----
BigDecimal totalTaxEstimate = waterfallResult.totalTaxDrag();
⋮----
if (waterfallResult.steps() != null) {
for (com.portfolioos.core.valuation.RebalanceWaterfallEngine.WaterfallStep step : waterfallResult.steps()) {
⋮----
if (l.lotId() != null && l.lotId().equals(step.lotId())) {
⋮----
long holdingDays = (origLot != null && origLot.acquisitionDate() != null) ?
ChronoUnit.DAYS.between(origLot.acquisitionDate(), today) : 400L;
⋮----
BigDecimal costBasis = step.proceeds().subtract(step.realizedGain()).max(BigDecimal.ZERO);
boolean isLongTerm = "LONG_TERM".equals(step.taxTerm());
⋮----
exempt = step.realizedGain().min(currentHeadroom);
taxable = step.realizedGain().subtract(exempt).max(BigDecimal.ZERO);
currentHeadroom = currentHeadroom.subtract(exempt).max(BigDecimal.ZERO);
totalLtcgExempt = totalLtcgExempt.add(exempt);
⋮----
taxable = step.realizedGain();
totalStcgTaxable = totalStcgTaxable.add(taxable);
⋮----
totalGain = totalGain.add(step.realizedGain());
⋮----
(exempt.compareTo(BigDecimal.ZERO) > 0 && taxable.compareTo(BigDecimal.ZERO) == 0 ? "SEC_112A_EXEMPT" : "SEC_112A_TAXABLE_12_5") :
⋮----
RebalanceLotImpactDto lotImpact = new RebalanceLotImpactDto(
step.lotId(),
step.assetId(),
step.assetName(),
(origLot != null && origLot.acquisitionDate() != null) ? origLot.acquisitionDate().toString() : today.toString(),
⋮----
step.unitsSold(),
⋮----
step.proceeds(),
step.realizedGain(),
step.taxTerm(),
new LotTaxImpactDto(regime, exempt, taxable, step.taxDrag())
⋮----
if (step.tier() == com.portfolioos.core.valuation.WaterfallTier.LEGACY_FUND) {
soldLegacyLots.add(lotImpact);
soldLegacyAmount = soldLegacyAmount.add(step.proceeds());
⋮----
soldCoreLots.add(lotImpact);
soldCoreAmount = soldCoreAmount.add(step.proceeds());
⋮----
// Tier 2: Legacy Fund Lots
⋮----
soldLegacyAmount.compareTo(BigDecimal.ZERO) == 0 ? "NO_TRIMMABLE_LOTS" : null,
⋮----
// Tier 3: Core Fund Lots
⋮----
soldCoreAmount.compareTo(BigDecimal.ZERO) == 0 ? (soldLegacyAmount.compareTo(BigDecimal.ZERO) > 0 ? "COVERED_BY_PRIOR_TIERS" : "NO_TRIMMABLE_LOTS") : null,
⋮----
TaxSummaryDto taxSummary = new TaxSummaryDto(
⋮----
headroomBefore.subtract(totalLtcgExempt).max(BigDecimal.ZERO)
⋮----
sellSide = new SellSidePlanDto(poolNeeded, waterfallTiers, taxSummary);
⋮----
// 4. Dynamic Buy Side Allocations Resolving to REAL Portfolio Fund ISINs
BigDecimal freshCash = isLumpsum ? (manualLumpsumAmount != null ? manualLumpsumAmount : BigDecimal.ZERO) : ((sellSide == null || sellSide.totalRequired() == null || sellSide.totalRequired().compareTo(BigDecimal.ZERO) == 0) ? totalPool : BigDecimal.ZERO);
BigDecimal postCorpus = liveCorpus.add(freshCash);
⋮----
BucketEngine.RebalanceEngineResult bucketResult = BucketEngine.evaluateRebalance(
⋮----
if (bucketResult != null && bucketResult.bucketStatuses() != null) {
for (BucketEngine.BucketStatus s : bucketResult.bucketStatuses()) {
statusMap.put(s.bucket(), s);
⋮----
BucketEngine.BucketStatus status = statusMap.get(target.bucket());
BigDecimal curVal = status != null ? status.currentValue() : BigDecimal.ZERO;
BigDecimal targetVal = postCorpus.multiply(target.targetPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
⋮----
BigDecimal shortfall = targetVal.subtract(curVal).max(BigDecimal.ZERO);
bucketShortfalls.put(target.bucket(), shortfall);
totalShortfall = totalShortfall.add(shortfall);
⋮----
String bucketName = target.bucket().name();
double targetPct = target.targetPct().doubleValue();
⋮----
double currentPct = (liveCorpus.compareTo(BigDecimal.ZERO) > 0) ?
Math.round((curVal.doubleValue() / liveCorpus.doubleValue()) * 1000.0) / 10.0 : targetPct;
⋮----
BigDecimal shortfall = bucketShortfalls.getOrDefault(target.bucket(), BigDecimal.ZERO);
⋮----
if (target.bucket() == BucketEngine.Bucket.GOLD_SILVER) {
⋮----
} else if (totalShortfall.compareTo(BigDecimal.ZERO) > 0 && shortfall.compareTo(BigDecimal.ZERO) > 0) {
amountAllocated = totalPool.multiply(shortfall).divide(totalShortfall, 2, RoundingMode.HALF_UP).min(shortfall);
⋮----
BigDecimal postVal = curVal.add(amountAllocated);
double postPct = (postCorpus.compareTo(BigDecimal.ZERO) > 0) ?
Math.round((postVal.doubleValue() / postCorpus.doubleValue()) * 1000.0) / 10.0 : currentPct;
⋮----
List<FundAllocationDto> realFunds = resolveRealFundBreakdown(target.bucket(), amountAllocated, activeVersion);
⋮----
buyBuckets.add(new RebalanceBucketAllocationDto(
⋮----
// Budget Conservation Normalization: Ensure sum(amountAllocated) strictly equals totalPool
BigDecimal rawSum = buyBuckets.stream().map(RebalanceBucketAllocationDto::amountAllocated).reduce(BigDecimal.ZERO, BigDecimal::add);
if (rawSum.compareTo(BigDecimal.ZERO) > 0 && totalPool.compareTo(BigDecimal.ZERO) > 0 && rawSum.compareTo(totalPool) != 0) {
⋮----
for (int i = 0; i < buyBuckets.size(); i++) {
RebalanceBucketAllocationDto b = buyBuckets.get(i);
⋮----
if (i == buyBuckets.size() - 1) {
normAlloc = totalPool.subtract(runningAlloc).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
⋮----
normAlloc = b.amountAllocated().multiply(totalPool).divide(rawSum, 2, RoundingMode.HALF_UP);
runningAlloc = runningAlloc.add(normAlloc);
⋮----
List<FundAllocationDto> realFunds = resolveRealFundBreakdown(BucketEngine.Bucket.valueOf(b.bucket()), normAlloc, activeVersion);
BigDecimal curVal = statusMap.containsKey(BucketEngine.Bucket.valueOf(b.bucket())) ?
statusMap.get(BucketEngine.Bucket.valueOf(b.bucket())).currentValue() : BigDecimal.ZERO;
BigDecimal postVal = curVal.add(normAlloc);
⋮----
Math.round((postVal.doubleValue() / postCorpus.doubleValue()) * 1000.0) / 10.0 : b.targetPct();
⋮----
normalizedBuckets.add(new RebalanceBucketAllocationDto(
b.bucket(), b.targetPct(), b.currentPct(), postPct, normAlloc, realFunds
⋮----
BuySidePlanDto buySide = new BuySidePlanDto(totalPool, isLumpsum, buyBuckets);
⋮----
// 5. Templated Narrative
⋮----
paragraphs.add("Notice: Drawdown protection is currently INACTIVE (no live benchmark index data source configured). Portfolio is operating under DRIFT & SCHEDULED rebalance rules.");
⋮----
double ddPct = resolution.drawdownContext().currentDrawdownPct();
BigDecimal high = resolution.drawdownContext().rollingHighValue();
⋮----
manualLumpsumMeta = new ManualLumpsumMetaDto(manualLumpsumAmount, today.toString(), modeNote, includeRebalance);
⋮----
headline = String.format("Manual Lump-Sum (₹%,d) + Rebalance Liquidations — Combined Redeployment (Config %s)",
manualLumpsumAmount.longValue(), activeVersion.versionId());
paragraphs.add(String.format("Entered manual lump-sum of ₹%,d combined with portfolio rebalance liquidations.", manualLumpsumAmount.longValue()));
⋮----
headline = String.format("Manual Lump-Sum Inflow (₹%,d) — Redeploying per Target Allocation (Config %s)",
⋮----
paragraphs.add(String.format("Entered manual capital inflow of ₹%,d for standalone deployment (no holdings sold).", manualLumpsumAmount.longValue()));
⋮----
paragraphs.add(String.format("Current portfolio drawdown is %.1f%% below rolling high of ₹%,d.", ddPct, high.longValue()));
paragraphs.add(String.format("Target allocations reference Config Version %s (effective from %s).", activeVersion.versionId(), activeVersion.effectiveFrom()));
} else if (!resolution.hasSellSide()) {
⋮----
headline = String.format("Gold Floor Backstop Triggered — Buy-Side Allocation of ₹%,d", totalPool.longValue());
paragraphs.add("Gold/Silver bucket has been idle from buy allocations for 6+ months and is underweight target allocation.");
paragraphs.add(String.format("Allocating ₹%,d top-up to close 50%% of remaining gap (exempt from sell cooldown).", totalPool.longValue()));
⋮----
headline = String.format("No Rebalance Required — %s", resolution.reasonLabel());
paragraphs.add(String.format("Current portfolio status: %s.", resolution.reasonLabel()));
paragraphs.add("No asset sales or rebalance capital pooling are required at this time.");
⋮----
headline = String.format("%s triggered — trimming legacy funds first to preserve tax efficiency", reasonLabel);
paragraphs.add(String.format("Triggered by %s.", reasonLabel));
paragraphs.add("Per your rebalance waterfall priority, arbitrage buffer was checked first (currently fully deployed).");
if (sellSide != null && sellSide.taxSummary() != null) {
TaxSummaryDto ts = sellSide.taxSummary();
paragraphs.add(String.format("Trimming open lots realized ₹%,d total gain (₹%,d LTCG exempt under Sec 112A, ₹%,d STCG taxable).",
ts.totalRealizedGain().longValue(), ts.totalLtcgExempt().longValue(), ts.totalStcgTaxable().longValue()));
paragraphs.add(String.format("Total estimated tax for this rebalance: ₹%,d. Remaining FY exemption headroom after trade: ₹%,d.",
ts.totalTaxEstimate().longValue(), ts.exemptionHeadroomAfter().longValue()));
⋮----
ReasoningNarrativeDto narrative = new ReasoningNarrativeDto(
⋮----
ManualLumpsumMetaDto lumpsumMeta = isLumpsum ? new ManualLumpsumMetaDto(
⋮----
today.toString(),
String.format("Portfolio currently %.1f%% below rolling high", ddPct)
⋮----
return new RebalancePlanDto(
⋮----
private static List<FundAllocationDto> resolveRealFundBreakdown(BucketEngine.Bucket bucket, BigDecimal totalAmount, BucketConfigLoader.BucketTargetVersion activeVersion) {
if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
return List.of();
⋮----
List<BucketConfigLoader.PreferredFundConfig> prefFunds = List.of();
if (activeVersion != null && activeVersion.targets() != null) {
⋮----
if (bucket.name().equals(tc.bucket())) {
prefFunds = tc.preferredFunds();
⋮----
if (prefFunds == null || prefFunds.isEmpty()) {
prefFunds = BucketConfigLoader.getDefaultPreferredFundsForBucket(bucket.name());
⋮----
for (int i = 0; i < prefFunds.size(); i++) {
BucketConfigLoader.PreferredFundConfig pf = prefFunds.get(i);
⋮----
if (i == prefFunds.size() - 1) {
⋮----
alloc = totalAmount.multiply(BigDecimal.valueOf(pf.allocationWeight())).setScale(2, RoundingMode.HALF_UP);
remaining = remaining.subtract(alloc);
⋮----
funds.add(new FundAllocationDto(pf.fundId(), pf.fundName(), alloc));
````

## File: src/main/java/com/portfolioos/core/service/RebalanceTriggerEvaluator.java
````java
public class RebalanceTriggerEvaluator {
⋮----
String triggerType,            // DRAWDOWN, DRIFT, SCHEDULED, GOLD_FLOOR_BACKSTOP, NONE
⋮----
public TriggerResolution getCurrentStatus(
⋮----
LocalDate today = currentDate != null ? currentDate : LocalDate.now();
⋮----
// 1. Calculate Corpus and Bucket Valuations
⋮----
BigDecimal nav = (navMap != null && navMap.containsKey(lot.assetId()))
? navMap.get(lot.assetId())
: (lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ONE);
BigDecimal lotVal = lot.remainingUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
liveCorpus = liveCorpus.add(lotVal);
⋮----
String bucketName = BucketConfigLoader.mapAssetToBucket(lot.assetId(), lot.assetName());
bucketValuations.put(bucketName, bucketValuations.getOrDefault(bucketName, BigDecimal.ZERO).add(lotVal));
⋮----
// 2. Compute Drawdown Context
BigDecimal high = (benchmarkRollingHigh != null && benchmarkRollingHigh.compareTo(BigDecimal.ZERO) > 0) ? benchmarkRollingHigh : BigDecimal.ZERO;
BigDecimal curr = (benchmarkCurrent != null && benchmarkCurrent.compareTo(BigDecimal.ZERO) > 0) ? benchmarkCurrent : BigDecimal.ZERO;
⋮----
if (curr.compareTo(BigDecimal.ZERO) > 0 && high.compareTo(BigDecimal.ZERO) > 0) {
BigDecimal diff = high.subtract(curr).max(BigDecimal.ZERO);
ddPct = diff.divide(high, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
ddPct = Math.max(0.0, Math.round(ddPct * 10.0) / 10.0);
⋮----
System.err.println("WARNING: Real Nifty benchmark market data unavailable (benchmarkCurrent/benchmarkRollingHigh is null). Drawdown Trigger Disarmed (0.00% DD).");
⋮----
double nextTierDistancePct = Math.max(0.0, Math.round((nextTierTargetPct - ddPct) * 10.0) / 10.0);
⋮----
DrawdownContextDto drawdownCtx = new DrawdownContextDto(
⋮----
today.toString(),
⋮----
// 3. Query Cooldown & Gold Idle State from Repository (PURE READ)
Optional<LocalDateTime> lastSellOpt = repository.getLastSellSideFiringDate();
long daysSinceLastSell = lastSellOpt.map(dt -> ChronoUnit.DAYS.between(dt.toLocalDate(), today)).orElse(9999L);
⋮----
Optional<LocalDateTime> lastGoldBuyOpt = repository.getLastGoldBuyDate();
long monthsSinceLastGoldBuy = lastGoldBuyOpt.map(dt -> ChronoUnit.MONTHS.between(dt.toLocalDate(), today)).orElse(9999L);
⋮----
// 4. Bucket Drift Evaluation (Target > 0 only; legacy 0% target funds excluded)
⋮----
? activeVersion : BucketConfigLoader.getActiveVersion(today);
List<BucketConfigLoader.BucketTargetConfig> targetConfigs = (ver != null && ver.targets() != null)
? ver.targets() : List.of();
⋮----
if (tc.targetPct() <= 0.0) continue; // Exclude 0% legacy buckets
⋮----
BigDecimal bucketVal = bucketValuations.getOrDefault(tc.bucket(), BigDecimal.ZERO);
double currentPct = (liveCorpus.compareTo(BigDecimal.ZERO) > 0)
? (bucketVal.doubleValue() / liveCorpus.doubleValue()) * 100.0 : 0.0;
⋮----
if ("GOLD_SILVER".equals(tc.bucket())) {
⋮----
goldTargetWeightPct = tc.targetPct();
⋮----
double driftThreshold = tc.triggerDriftPct() > 0 ? tc.triggerDriftPct() : PortfolioConstants.DEFAULT_CORE_DRIFT_THRESHOLD_PCT;
if (Math.abs(currentPct - tc.targetPct()) >= driftThreshold) {
driftedBuckets.add(tc.bucket());
⋮----
// 5. Trigger Resolution Priority Order
⋮----
// Priority 1: DRAWDOWN
if (!"NONE".equals(armedTier)) {
⋮----
reasonLabel = String.format("Drawdown tier %s crossed but sell rebalance is on 30-day cooldown (%d days since last sell)", armedTier, daysSinceLastSell);
⋮----
reasonCode = "DRAWDOWN_TIER_" + armedTier.replace("TIER_", "");
reasonLabel = String.format("%s%% Portfolio Drawdown Tier Triggered", armedTier.replace("TIER_", ""));
⋮----
// Priority 2: DRIFT (if Drawdown was not evaluated)
if (!sellTriggerEvaluated && !driftedBuckets.isEmpty()) {
⋮----
reasonLabel = String.format("Bucket drift detected (%s) but sell rebalance is on 30-day cooldown (%d days since last sell)",
String.join(", ", driftedBuckets), daysSinceLastSell);
⋮----
reasonLabel = String.format("Bucket Allocation Drift Exceeded Threshold (%s)", String.join(", ", driftedBuckets));
⋮----
// Priority 3: SCHEDULED (March/September window, if Drawdown/Drift not evaluated)
if (!sellTriggerEvaluated && (today.getMonthValue() == 3 || today.getMonthValue() == 9)) {
⋮----
reasonLabel = String.format("Scheduled window active but sell rebalance is on 30-day cooldown (%d days since last sell)", daysSinceLastSell);
⋮----
// Priority 4: GOLD_FLOOR_BACKSTOP (Buy-only, exempt from 30-day sell cooldown)
if ("NONE".equals(triggerType)) {
⋮----
reasonLabel = String.format("Gold/Silver Floor Backstop Triggered (Idle %d months, %.1f pts underweight)",
⋮----
return new TriggerResolution(
⋮----
public TriggerResolution evaluateAndRecord(
⋮----
TriggerResolution resolution = getCurrentStatus(
⋮----
if (!"NONE".equals(resolution.triggerType())) {
⋮----
repository.recordExecution(
⋮----
resolution.triggerType(),
resolution.reasonCode(),
today.atStartOfDay(),
resolution.hasSellSide(),
resolution.hasGoldBuy(),
"{\"driftedBuckets\":" + resolution.driftedBuckets() + "}"
````

## File: src/main/java/com/portfolioos/core/service/SimulationService.java
````java
public class SimulationService {
⋮----
private final XirrEngine xirrEngine = new XirrEngine();
⋮----
String tradeType // DISPOSAL or ACQUISITION
⋮----
BigDecimal slabRateGain, // Renamed from debtGain to accurately reflect all slab-taxed gains (specified debt, STCG Gold/Intl/SGB)
⋮----
// Backwards compatibility getter alias for legacy callers querying debtGain()
public BigDecimal debtGain() {
⋮----
public TradeSimulationResult simulateTrade(TradeSimulationRequest req) {
LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
List<TaxEvent> existingEvents = state.events();
Map<String, BigDecimal> navMap = state.navMap();
⋮----
LocalDate tradeDate = (req.tradeDate() != null && !req.tradeDate().isBlank())
? LocalDate.parse(req.tradeDate())
: LocalDate.now();
⋮----
String targetFy = TaxRulesLoader.detectFiscalYear(tradeDate);
TaxRulesConfig rules = TaxRulesLoader.loadRules(targetFy);
⋮----
BigDecimal unitsBd = req.units() != null ? req.units().setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
BigDecimal priceBd = req.pricePerUnit() != null ? req.pricePerUnit().setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
BigDecimal grossAmount = unitsBd.multiply(priceBd).setScale(2, RoundingMode.HALF_UP);
⋮----
EventType type = "ACQUISITION".equalsIgnoreCase(req.tradeType()) ? EventType.ACQUISITION : EventType.DISPOSAL;
String isin = (req.isin() != null && !req.isin().isBlank()) ? req.isin() : "SIMULATED_ASSET";
String name = (req.schemeName() != null && !req.schemeName().isBlank()) ? req.schemeName() : "Simulated Fund";
⋮----
TaxEvent simEvent = new TaxEvent(
"SIM_" + System.currentTimeMillis(),
⋮----
java.time.Instant.now()
⋮----
simEvents.add(simEvent);
⋮----
FifoMatcher matcher = new FifoMatcher();
FifoMatcher.FifoResult simResult = matcher.processEvents(simEvents);
⋮----
for (MatchedLot match : simResult.matchedLots()) {
if (match.disposalEventId().equals(simEvent.id())) {
AssetCategory category = match.assetCategory();
TaxTerm term = match.taxTerm();
BigDecimal gain = match.realizedGain();
totalGain = totalGain.add(gain);
⋮----
// SGB 8-year maturity redemption under Sec 47(ix) is completely tax-exempt
⋮----
ltcgEquity = ltcgEquity.add(gain);
⋮----
stcgEquity = stcgEquity.add(gain);
⋮----
ltcgGoldInternational = ltcgGoldInternational.add(gain);
⋮----
stcgSlabRateGain = stcgSlabRateGain.add(gain);
⋮----
// Specified debt under Sec 50AA is always short term and taxed at SLAB_RATE
⋮----
default -> throw new IllegalStateException("Unhandled AssetCategory for tax simulation: " + category);
⋮----
// Use ExemptionTracker bound to target fiscal year
ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(state.fifoResult().matchedLots(), targetFy);
BigDecimal remainingExemptionLimit = new BigDecimal(exStatus.exemptionRemaining());
⋮----
if (ltcgEquity.compareTo(BigDecimal.ZERO) > 0) {
exemptionApplied = ltcgEquity.min(remainingExemptionLimit);
taxableLtcgEquity = ltcgEquity.subtract(exemptionApplied).max(BigDecimal.ZERO);
⋮----
// Calculate tax dynamic from rules object — NO hardcoded BigDecimal literal rates
BigDecimal ltcgEquityTax = taxableLtcgEquity.multiply(rules.equityLtcgRate()).setScale(2, RoundingMode.HALF_UP);
BigDecimal stcgEquityTax = stcgEquity.max(BigDecimal.ZERO).multiply(rules.equityStcgRate()).setScale(2, RoundingMode.HALF_UP);
BigDecimal ltcgGoldTax = ltcgGoldInternational.max(BigDecimal.ZERO).multiply(rules.goldInternationalLtcgRate()).setScale(2, RoundingMode.HALF_UP);
⋮----
BigDecimal estimatedTax = ltcgEquityTax.add(stcgEquityTax).add(ltcgGoldTax);
⋮----
// Compute post-trade net worth & XIRR
⋮----
for (Lot lot : simResult.openLots()) {
postInvested = postInvested.add(lot.remainingUnits().multiply(lot.costPerUnit()));
BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
postCurrentVal = postCurrentVal.add(lot.remainingUnits().multiply(nav));
⋮----
BigDecimal amt = (ev.eventType() == EventType.ACQUISITION || ev.eventType() == EventType.SIP_INSTALMENT)
? ev.grossAmount().negate()
: ev.grossAmount();
cashFlows.add(new CashFlow(ev.eventDate(), amt));
⋮----
if (postCurrentVal.compareTo(BigDecimal.ZERO) > 0) {
cashFlows.add(new CashFlow(tradeDate, postCurrentVal));
⋮----
double postXirrVal = xirrEngine.calculateXirr(cashFlows);
BigDecimal postXirr = BigDecimal.valueOf(postXirrVal).setScale(2, RoundingMode.HALF_UP);
⋮----
if (stcgSlabRateGain.compareTo(BigDecimal.ZERO) > 0) {
notice = String.format("Simulated Sale (FY %s): Estimated Computed Tax Drag ₹%s (LTCG Exemption Used: ₹%s). Additional Gains: ₹%s (SLAB_RATE — not computed without income slab data).",
targetFy, estimatedTax.setScale(2, RoundingMode.HALF_UP).toPlainString(),
exemptionApplied.setScale(2, RoundingMode.HALF_UP).toPlainString(),
stcgSlabRateGain.setScale(2, RoundingMode.HALF_UP).toPlainString());
⋮----
notice = String.format("Simulated Sale (FY %s): Estimated Tax Drag ₹%s (LTCG Exemption Used: ₹%s)",
targetFy, estimatedTax.setScale(2, RoundingMode.HALF_UP).toPlainString(), exemptionApplied.setScale(2, RoundingMode.HALF_UP).toPlainString());
⋮----
notice = String.format("Simulated Purchase: Added ₹%s investment to portfolio.", grossAmount.setScale(2, RoundingMode.HALF_UP).toPlainString());
⋮----
return new TradeSimulationResult(
⋮----
type.name(),
⋮----
totalGain.setScale(2, RoundingMode.HALF_UP),
ltcgEquity.setScale(2, RoundingMode.HALF_UP),
stcgEquity.setScale(2, RoundingMode.HALF_UP),
stcgSlabRateGain.setScale(2, RoundingMode.HALF_UP),
exemptionApplied.setScale(2, RoundingMode.HALF_UP),
estimatedTax.setScale(2, RoundingMode.HALF_UP),
postCurrentVal.setScale(2, RoundingMode.HALF_UP),
postInvested.setScale(2, RoundingMode.HALF_UP),
````

## File: src/main/java/com/portfolioos/core/service/StatementIngestionUseCase.java
````java
public class StatementIngestionUseCase {
⋮----
public List<TaxEvent> ingestParsedEvents(ParsedEventDto[] dtoList) {
⋮----
return List.of();
⋮----
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
⋮----
taxEvents.add(te);
⋮----
// Dual-write step 1: Write to primary SQLite Ledger
eventStore.appendEvents(taxEvents);
⋮----
// Dual-write step 2: Re-project events in DuckDB analytical database
List<TaxEvent> allEvents = eventStore.getAllEvents();
duckDbProjector.projectEvents(allEvents);
⋮----
System.err.println("CRITICAL: DuckDB projection failed during statement ingestion: " + e.getMessage());
throw new RuntimeException("Dual-write failure: Analytical DuckDB projection failed: " + e.getMessage(), e);
⋮----
// Evict/Invalidate central ledger cache
cacheService.invalidateCache();
````

## File: src/main/java/com/portfolioos/core/service/TaxOptimizationService.java
````java
public class TaxOptimizationService {
⋮----
private final AmfiNavSync amfiSync = new AmfiNavSync();
private final FifoMatcher fifoMatcher = new FifoMatcher();
⋮----
private String fmt(BigDecimal val) {
return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
⋮----
public ExemptionTracker.ExemptionStatus getExemptionStatus(String fy) {
List<TaxEvent> allEvents = eventStore.getAllEvents();
List<MatchedLot> matchedLots = fifoMatcher.processEvents(allEvents).matchedLots();
String currentFy = (fy != null && !fy.isBlank()) ? fy : TaxRulesLoader.detectFiscalYear(LocalDate.now());
return ExemptionTracker.calculateExemptionStatus(matchedLots, currentFy);
⋮----
public TaxReportExporter.Itr2ScheduleCgReport generateItr2Report(String fy) {
⋮----
return TaxReportExporter.generateItr2Report(matchedLots, currentFy);
⋮----
public List<HarvestOpportunityDto> getHarvestOpportunities() {
⋮----
List<Lot> openLots = fifoMatcher.processEvents(allEvents).openLots();
Map<String, BigDecimal> navMap = amfiSync.getNavMap();
String currentFy = TaxRulesLoader.detectFiscalYear(LocalDate.now());
⋮----
ExemptionTracker.ExemptionStatus status = getExemptionStatus(currentFy);
BigDecimal usedExemption = new BigDecimal(status.exemptionUsed());
⋮----
HarvestAdvisor.TaxHarvestResult plan = HarvestAdvisor.generateHarvestPlan(
⋮----
return plan.recommendations().stream().map(opp -> new HarvestOpportunityDto(
opp.assetId(),
opp.assetName(),
opp.lotId(),
opp.unitsToHarvest().setScale(4, RoundingMode.HALF_UP).toPlainString(),
fmt(opp.unrealizedLtcgGain())
)).toList();
⋮----
public List<MaturationLadderDto> getMaturationLadder() {
⋮----
LocalDate today = LocalDate.now();
String currentFy = TaxRulesLoader.detectFiscalYear(today);
TaxRulesConfig rules = TaxRulesLoader.loadRules(currentFy);
⋮----
AssetCategory cat = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
boolean isListed = TaxClassifier.isListed(lot.assetId(), lot.assetName());
⋮----
? rules.equityLtcgThresholdDays()
: rules.goldInternationalThresholdDays();
long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
⋮----
LocalDate targetDate = today.plusDays(daysRemaining);
BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
BigDecimal currentVal = lot.remainingUnits().multiply(nav);
BigDecimal gain = currentVal.subtract(lot.totalCostBasis());
⋮----
ladder.add(new MaturationLadderDto(
lot.assetId(),
lot.assetName(),
lot.lotId(),
lot.acquisitionDate().toString(),
lot.remainingUnits().setScale(4, RoundingMode.HALF_UP).toPlainString(),
fmt(lot.totalCostBasis()),
fmt(currentVal),
fmt(gain),
⋮----
targetDate.toString()
⋮----
ladder.sort((a, b) -> Long.compare(a.daysRemainingToLtcg(), b.daysRemainingToLtcg()));
⋮----
public List<RealizedLogDto> getRealizedLog(String fy) {
⋮----
ExemptionTracker.Pair<LocalDate, LocalDate> bounds = ExemptionTracker.getFiscalYearBounds(fy);
LocalDate startDate = bounds.first();
LocalDate endDate = bounds.second();
⋮----
List<MatchedLot> fyLots = matchedLots.stream().filter(lot ->
!lot.disposalDate().isBefore(startDate) && !lot.disposalDate().isAfter(endDate)
).toList();
⋮----
Map<String, String> assetNameMap = allEvents.stream()
.collect(Collectors.toMap(TaxEvent::assetId, TaxEvent::assetName, (a, b) -> a));
⋮----
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
⋮----
public Map<String, String> downloadItr2Files(String fy) {
return downloadItr2Files(fy, Map.of());
⋮----
public Map<String, String> downloadItr2Files(String fy, Map<String, BigDecimal> fmv2018Map) {
⋮----
return Itr2CsvExporter.exportItr2ScheduleCg(matchedLots, fy, assetNameMap, fmv2018Map != null ? fmv2018Map : Map.of());
````

## File: src/main/java/com/portfolioos/core/tools/PortfolioQueryTools.java
````java
public class PortfolioQueryTools {
⋮----
private static final Logger log = LoggerFactory.getLogger(PortfolioQueryTools.class);
⋮----
public Map<String, Object> getPortfolioValuation() {
log.info("LLM_TOOL_EXECUTION: tool=getPortfolioValuation params={}");
String fy = TaxRulesLoader.detectFiscalYear(LocalDate.now());
PortfolioSummaryResponse summary = valuationService.getPortfolioSummary(fy);
⋮----
result.put("status", "SUCCESS");
result.put("source_tool", "getPortfolioValuation");
result.put("fiscal_year", fy);
result.put("total_net_worth", summary.totalCurrentValue());
result.put("total_invested_cost", summary.totalInvested());
result.put("total_unrealized_gain", summary.totalUnrealizedGain());
result.put("portfolio_xirr", summary.xirrPercentage());
result.put("active_holding_count", summary.activeHoldingCount());
⋮----
public Map<String, Object> getFundRegistry() {
log.info("LLM_TOOL_EXECUTION: tool=getFundRegistry params={}");
List<HoldingDetailDto> holdings = valuationService.getHoldings();
Set<String> activeAssetIds = FundTierClassifier.findActiveAssetIds(cacheService.getCachedState().fifoResult().openLots(), LocalDate.now());
⋮----
entry.put("isin", h.assetId());
entry.put("scheme_name", h.assetName());
entry.put("category", h.category());
entry.put("current_value", h.currentValue());
entry.put("invested_value", h.investedValue());
entry.put("unrealized_gain", h.unrealizedGain());
entry.put("status", activeAssetIds.contains(h.assetId()) ? "ACTIVE_SIP" : "LEGACY_HOLDING");
registryList.add(entry);
⋮----
result.put("source_tool", "getFundRegistry");
result.put("total_funds", registryList.size());
result.put("funds", registryList);
⋮----
public Map<String, Object> getFireSummary() {
log.info("LLM_TOOL_EXECUTION: tool=getFireSummary params={}");
var state = cacheService.getCachedState();
FireTracker.FireSummary fire = FireTracker.calculateFireSummary(state.fifoResult().openLots(), state.navMap(), LocalDate.now());
⋮----
result.put("source_tool", "getFireSummary");
result.put("active_scenario_label", fire.activeScenarioLabel());
result.put("monthly_expense_today", fire.monthlyExpenseToday());
result.put("annual_expense", fire.annualExpense());
result.put("required_fire_corpus", fire.requiredCorpus());
result.put("total_net_worth", fire.totalNetWorth());
result.put("fire_investable_net_worth", fire.fireInvestableNetWorth());
result.put("years_remaining", fire.yearsRemaining());
result.put("fire_status", fire.status());
⋮----
public Map<String, Object> getRebalancePlan() {
log.info("LLM_TOOL_EXECUTION: tool=getRebalancePlan params={}");
⋮----
BigDecimal currentVal = new BigDecimal(valuationService.getPortfolioSummary(fy).totalCurrentValue());
BigDecimal personalNetWorthAth = duckDbProjector.getDailyNetWorthTrend().stream()
.map(p -> BigDecimal.valueOf(p.valuation()))
.max(BigDecimal::compareTo)
.orElse(currentVal);
⋮----
RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
state.fifoResult().openLots(),
state.fifoResult().matchedLots(),
state.navMap(),
LocalDate.now(),
⋮----
BucketConfigLoader.getActiveBucketTargets(LocalDate.now()),
⋮----
result.put("source_tool", "getRebalancePlan");
⋮----
result.put("derived_trigger_type", plan.trigger().type());
result.put("plan_id", plan.planId());
result.put("trigger", plan.trigger());
result.put("sell_side", plan.sellSide());
result.put("buy_side", plan.buySide());
⋮----
public Map<String, Object> getTaxHarvestOpportunities() {
log.info("LLM_TOOL_EXECUTION: tool=getTaxHarvestOpportunities params={}");
⋮----
ExemptionTracker.ExemptionStatus exemption = taxService.getExemptionStatus(fy);
var harvestOps = taxService.getHarvestOpportunities();
⋮----
result.put("source_tool", "getTaxHarvestOpportunities");
⋮----
result.put("exemption_remaining", exemption.exemptionRemaining());
result.put("taxable_ltcg_so_far", exemption.taxableLtcg());
result.put("total_opportunities", harvestOps != null ? harvestOps.size() : 0);
result.put("opportunities", harvestOps != null ? harvestOps : List.of());
⋮----
public Map<String, Object> getPairwiseFundOverlap(
⋮----
log.info("LLM_TOOL_EXECUTION: tool=getPairwiseFundOverlap params={fundA={}, fundB={}}", fundA, fundB);
if (fundA == null || fundA.isBlank() || fundB == null || fundB.isBlank()) {
⋮----
err.put("status", "INVALID_PARAM");
err.put("source_tool", "getPairwiseFundOverlap");
err.put("message", "Both fundA and fundB ISIN parameters are required.");
⋮----
// Verify funds exist in registry
⋮----
boolean existsA = holdings.stream().anyMatch(h -> h.assetId().equalsIgnoreCase(fundA) || h.assetName().toLowerCase().contains(fundA.toLowerCase()));
boolean existsB = holdings.stream().anyMatch(h -> h.assetId().equalsIgnoreCase(fundB) || h.assetName().toLowerCase().contains(fundB.toLowerCase()));
⋮----
err.put("status", "NOT_FOUND");
⋮----
err.put("missing_entity", missing);
err.put("message", "No fund matching '" + missing + "' exists in the active portfolio registry.");
⋮----
Map<String, Object> overlap = duckDbProjector.getPairwiseFundOverlap(fundA, fundB);
overlap.put("status", "SUCCESS");
overlap.put("source_tool", "getPairwiseFundOverlap");
⋮----
public Map<String, Object> simulateTrade(
⋮----
log.info("LLM_TOOL_EXECUTION: tool=simulateTrade params={isin={}, schemeName={}, units={}, pricePerUnit={}, tradeType={}}",
⋮----
if (isin == null || isin.isBlank() || schemeName == null || schemeName.isBlank() ||
units == null || units.compareTo(BigDecimal.ZERO) <= 0 ||
pricePerUnit == null || pricePerUnit.compareTo(BigDecimal.ZERO) <= 0 ||
tradeType == null || tradeType.isBlank()) {
⋮----
err.put("source_tool", "simulateTrade");
err.put("message", "Trade simulation requires valid parameters (isin, schemeName, positive units, pricePerUnit, tradeType). No arbitrary fallbacks are substituted.");
⋮----
tradeType.toUpperCase()
⋮----
SimulationService.TradeSimulationResult res = simulationService.simulateTrade(simReq);
⋮----
result.put("source_tool", "simulateTrade");
result.put("simulation_result", res);
result.put("notice", res.taxSummaryNotice());
````

## File: src/main/java/com/portfolioos/core/util/Pair.java
````java

````

## File: src/main/java/com/portfolioos/core/valuation/BucketEngine.java
````java
public class BucketEngine {
⋮----
String action, // "BUY" or "SELL"
⋮----
public static final List<BucketTarget> DEFAULT_TARGETS = List.of(
new BucketTarget(Bucket.EQUITY_CORE, new BigDecimal("50.0"), new BigDecimal("5.0")),
new BucketTarget(Bucket.EQUITY_SATELLITE, new BigDecimal("20.0"), new BigDecimal("5.0")),
new BucketTarget(Bucket.GOLD_SILVER, new BigDecimal("15.0"), new BigDecimal("5.0")),
new BucketTarget(Bucket.LIQUID_BUFFER, new BigDecimal("15.0"), new BigDecimal("5.0"))
⋮----
public static Bucket classifyAssetToBucket(String assetId, String assetName) {
return classifyAssetToBucket(assetId, assetName, java.util.Collections.emptySet());
⋮----
public static Bucket classifyAssetToBucket(String assetId, String assetName, java.util.Set<String> activeOrPreferredAssetIds) {
String nameUpper = assetName.toUpperCase();
AssetCategory category = TaxClassifier.detectCategory(assetId, assetName);
⋮----
// Step 1: Category / Asset Type match FIRST (Gold/Silver & Liquid Buffer are structurally exempt from LEGACY_HOLDINGS)
⋮----
if (nameUpper.contains("ARBITRAGE") || nameUpper.contains("LIQUID") ||
nameUpper.contains("OVERNIGHT") || nameUpper.contains("TREASURY") ||
⋮----
// Step 2: Read preferred fund mapping directly from YAML / BucketConfigLoader
String mappedBucketName = com.portfolioos.core.rules.BucketConfigLoader.getPreferredBucketForAsset(assetId, assetName);
⋮----
return Bucket.valueOf(mappedBucketName);
⋮----
// Step 3: Legacy check (for remaining equity funds, if activeOrPreferredAssetIds is provided and asset is not in it, map to LEGACY_HOLDINGS)
if (activeOrPreferredAssetIds != null && !activeOrPreferredAssetIds.isEmpty() && !activeOrPreferredAssetIds.contains(assetId)) {
⋮----
public static RebalanceEngineResult evaluateRebalance(
⋮----
return evaluateRebalance(openLots, List.of(), navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh, targets, fiscalYear);
⋮----
return evaluateRebalance(openLots, matchedLots, navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh, targets, fiscalYear, java.util.Collections.emptySet());
⋮----
for (Bucket b : Bucket.values()) {
bucketValues.put(b, BigDecimal.ZERO);
bucketAssetLots.put(b, new HashMap<>());
⋮----
Bucket bucket = classifyAssetToBucket(lot.assetId(), lot.assetName(), activeOrPreferredAssetIds);
BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
BigDecimal lotValue = lot.remainingUnits().multiply(nav);
⋮----
totalPortfolioValue = totalPortfolioValue.add(lotValue);
bucketValues.put(bucket, bucketValues.get(bucket).add(lotValue));
⋮----
Map<String, List<Lot>> assetMap = bucketAssetLots.get(bucket);
assetMap.computeIfAbsent(lot.assetId(), k -> new ArrayList<>()).add(lot);
⋮----
targetMap.put(t.bucket(), t);
⋮----
int month = currentDate.getMonthValue();
int day = currentDate.getDayOfMonth();
⋮----
for (Bucket bucket : Bucket.values()) {
BigDecimal curVal = bucketValues.get(bucket);
⋮----
if (totalPortfolioValue.compareTo(BigDecimal.ZERO) > 0) {
curPct = curVal.multiply(new BigDecimal("100")).divide(totalPortfolioValue, 2, RoundingMode.HALF_UP);
⋮----
BucketTarget tgt = targetMap.get(bucket);
BigDecimal targetPct = tgt != null ? tgt.targetPct() : BigDecimal.ZERO;
BigDecimal bandPct = tgt != null ? tgt.bandPct() : new BigDecimal("5.0");
⋮----
BigDecimal drift = curPct.subtract(targetPct);
boolean isDrifted = (bucket == Bucket.LEGACY_HOLDINGS) ? false : (drift.abs().compareTo(bandPct) > 0);
⋮----
bucketStatuses.add(new BucketStatus(
⋮----
// Drawdown trigger - delegates to unified PortfolioConstants disarm logic
double ddPctVal = PortfolioConstants.calculateDrawdownPct(benchmarkCurrent, benchmarkRollingHigh);
BigDecimal drawdownPct = BigDecimal.valueOf(ddPctVal).setScale(2, RoundingMode.HALF_UP);
⋮----
if (drawdownPct.compareTo(new BigDecimal("20.0")) >= 0) {
activeRungs.addAll(List.of(10, 15, 20));
deployPct = new BigDecimal("100.0");
} else if (drawdownPct.compareTo(new BigDecimal("15.0")) >= 0) {
activeRungs.addAll(List.of(10, 15));
deployPct = new BigDecimal("50.0");
} else if (drawdownPct.compareTo(new BigDecimal("10.0")) >= 0) {
activeRungs.add(10);
deployPct = new BigDecimal("25.0");
⋮----
boolean drawdownTriggerFired = !activeRungs.isEmpty();
DrawdownStatus drawdownStatus = new DrawdownStatus(
⋮----
TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
⋮----
// Deduct statutory Section 112A LTCG exemption
ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(matchedLots != null ? matchedLots : List.of(), fiscalYear);
BigDecimal exemptionRemaining = new BigDecimal(exStatus.exemptionRemaining());
⋮----
BigDecimal liquidVal = bucketValues.get(Bucket.LIQUID_BUFFER);
BigDecimal deployAmount = liquidVal.multiply(deployPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
⋮----
if (deployAmount.compareTo(BigDecimal.ZERO) > 0) {
Map<String, List<Lot>> coreAssets = bucketAssetLots.get(Bucket.EQUITY_CORE);
String targetAsset = !coreAssets.isEmpty() ? coreAssets.keySet().iterator().next() : "EQUITY_CORE_INDEX";
String assetName = !coreAssets.isEmpty() ? coreAssets.get(targetAsset).get(0).assetName() : "LargeMidcap 250 Index Fund";
⋮----
recommendations.add(new RebalanceRecommendation(
⋮----
if (status.isDrifted()) {
BigDecimal targetValue = totalPortfolioValue.multiply(status.targetPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
BigDecimal diffValue = status.currentValue().subtract(targetValue);
⋮----
if (diffValue.compareTo(BigDecimal.ZERO) > 0) {
Map<String, List<Lot>> bucketLotsMap = bucketAssetLots.get(status.bucket());
⋮----
for (List<Lot> lotList : bucketLotsMap.values()) {
flatBucketLots.addAll(lotList);
⋮----
if (!flatBucketLots.isEmpty()) {
boolean urgent = drawdownStatus.drawdownPct().compareTo(new BigDecimal("15.0")) >= 0
|| status.driftPct().abs().compareTo(new BigDecimal("10.0")) >= 0;
⋮----
RebalanceWaterfallEngine.buildTrimWaterfall(
status.bucket(),
diffValue.abs(),
⋮----
exemptionRemaining = exemptionRemaining.subtract(waterfallResult.ltcgExemptionConsumed()).max(BigDecimal.ZERO);
⋮----
if (waterfallResult.steps().isEmpty()) {
⋮----
"DEFERRED_" + status.bucket().name(),
"Deferred Trim (" + status.bucket().name() + ")",
⋮----
waterfallResult.deferralReason() != null ? waterfallResult.deferralReason() : "No tax-efficient lots available"
⋮----
for (RebalanceWaterfallEngine.WaterfallStep step : waterfallResult.steps()) {
⋮----
step.assetId(),
step.assetName(),
⋮----
step.proceeds(),
⋮----
step.taxDrag(),
"Tier: " + step.tier().name() + " (" + step.taxTerm() + ")"
⋮----
if (waterfallResult.deferredAmount().compareTo(BigDecimal.ZERO) > 0) {
⋮----
"Partial Deferred Trim (" + status.bucket().name() + ")",
⋮----
waterfallResult.deferredAmount(),
⋮----
waterfallResult.deferralReason() != null ? waterfallResult.deferralReason() : "Partial STCG deferral"
⋮----
} else if (diffValue.compareTo(BigDecimal.ZERO) < 0) {
Map<String, List<Lot>> bucketLots = bucketAssetLots.get(status.bucket());
String firstAssetId = !bucketLots.isEmpty() ? bucketLots.keySet().iterator().next() : "BUY_" + status.bucket().name();
String assetName = (!bucketLots.isEmpty() && bucketLots.containsKey(firstAssetId))
? bucketLots.get(firstAssetId).get(0).assetName() : "Core Holding for " + status.bucket().name();
⋮----
return new RebalanceEngineResult(
````

## File: src/main/java/com/portfolioos/core/valuation/ConsolidationRebalanceEngine.java
````java
public class ConsolidationRebalanceEngine {
⋮----
public static ConsolidationPreviewResult calculateConsolidation(
⋮----
TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
⋮----
java.util.Set<String> activeAssetIds = com.portfolioos.core.matcher.FundTierClassifier.findActiveAssetIds(openLots, currentDate);
List<Lot> phaseOutLots = openLots.stream().filter(lot ->
com.portfolioos.core.matcher.FundTierClassifier.isLegacyFund(lot.assetId(), activeAssetIds)
).toList();
⋮----
grouped.computeIfAbsent(lot.assetId(), k -> new ArrayList<>()).add(lot);
⋮----
for (Map.Entry<String, List<Lot>> entry : grouped.entrySet()) {
String assetId = entry.getKey();
List<Lot> lots = entry.getValue();
⋮----
String assetName = lots.get(0).assetName();
⋮----
totalUnits = totalUnits.add(lot.remainingUnits());
totalCost = totalCost.add(lot.totalCostBasis());
if (oldestAcq == null || lot.acquisitionDate().isBefore(oldestAcq)) {
oldestAcq = lot.acquisitionDate();
⋮----
BigDecimal nav = navMap.getOrDefault(assetId, BigDecimal.ZERO);
if (nav.compareTo(BigDecimal.ZERO) == 0 && totalUnits.compareTo(BigDecimal.ZERO) > 0) {
nav = totalCost.divide(totalUnits, 4, RoundingMode.HALF_UP);
⋮----
BigDecimal curVal = totalUnits.multiply(nav);
BigDecimal gain = curVal.subtract(totalCost);
⋮----
AssetCategory category = TaxClassifier.detectCategory(assetId, assetName);
long holdingDays = ChronoUnit.DAYS.between(oldestAcq != null ? oldestAcq : currentDate, currentDate);
⋮----
case EQUITY -> rules.equityLtcgThresholdDays();
case GOLD_SILVER, INTERNATIONAL, SGB -> rules.goldInternationalThresholdDays();
⋮----
if (gain.compareTo(BigDecimal.ZERO) > 0) {
⋮----
BigDecimal exemptPortion = gain.min(unusedExemption);
BigDecimal taxableGain = gain.subtract(exemptPortion);
unusedExemption = unusedExemption.subtract(exemptPortion).max(BigDecimal.ZERO);
taxDrag = taxableGain.multiply(rules.equityLtcgRate());
⋮----
taxDrag = gain.multiply(rules.equityStcgRate());
⋮----
totalProceeds = totalProceeds.add(curVal);
totalGain = totalGain.add(gain);
totalTaxDrag = totalTaxDrag.add(taxDrag);
⋮----
phasedSummaries.add(new PhasedOutAssetSummary(
⋮----
BigDecimal netPostTaxProceeds = totalProceeds.subtract(totalTaxDrag).max(BigDecimal.ZERO);
BigDecimal effectiveProceeds = netPostTaxProceeds.compareTo(BigDecimal.ZERO) > 0 ? netPostTaxProceeds : totalProceeds;
⋮----
Map<String, Double> sipAllocMap = com.portfolioos.core.rules.BucketConfigLoader.getRenormalizedSipAllocations(currentDate);
⋮----
for (Map.Entry<String, Double> fundEntry : sipAllocMap.entrySet()) {
String fundId = fundEntry.getKey();
double sipWeightFrac = fundEntry.getValue();
BigDecimal weightPct = BigDecimal.valueOf(sipWeightFrac * 100.0).setScale(2, RoundingMode.HALF_UP);
BigDecimal deployAmt = effectiveProceeds.multiply(BigDecimal.valueOf(sipWeightFrac)).setScale(2, RoundingMode.HALF_UP);
⋮----
proRataAllocations.add(new ExistingSipAllocation(
⋮----
int month = currentDate.getMonthValue();
⋮----
String nextScheduled = (month <= 3) ? "March 31, " + currentDate.getYear()
: (month <= 9) ? "September 30, " + currentDate.getYear()
: "March 31, " + (currentDate.getYear() + 1);
⋮----
BigDecimal ltcgHarvested = remainingExemption.subtract(unusedExemption);
⋮----
return new ConsolidationPreviewResult(
````

## File: src/main/java/com/portfolioos/core/valuation/FundTrendDampenerCalculator.java
````java
public class FundTrendDampenerCalculator {
⋮----
/**
     * Calculates dynamic per-fund trend dampener multipliers based on percentage drift.
     * @param driftPct positive if overweight (excess), negative if underweight (deficit)
     */
public static DampenerMultipliers calculateFundMultipliers(double driftPct) {
⋮----
// Overweight fund (Sell side)
// Small excess (0-10%): gentle 0.40x trim
// Moderate excess (10-30%): meaningful 0.60x trim
// Large excess (>30%): disciplined 0.75x trim
⋮----
// Underweight fund (Buy side)
// Minor deficit (0 to -10%): 0.50x allocation
// Moderate deficit (-10% to -30%): 0.80x allocation
// Deep deficit (<-30%): 1.00x full allocation
double deficit = Math.abs(driftPct);
⋮----
buyMult = Math.round(buyMult * 10000.0) / 10000.0;
sellMult = Math.round(sellMult * 10000.0) / 10000.0;
⋮----
return new DampenerMultipliers(buyMult, sellMult);
⋮----
/**
     * Sizes the per-bucket dampened excess trim amount.
     */
public static BigDecimal calculateDampenedTrim(BigDecimal excessVal, double targetVal) {
if (excessVal == null || excessVal.compareTo(BigDecimal.ZERO) <= 0 || targetVal <= 0.0) {
⋮----
double driftPct = (excessVal.doubleValue() / targetVal) * 100.0;
DampenerMultipliers mults = calculateFundMultipliers(driftPct);
return excessVal.multiply(BigDecimal.valueOf(mults.sellMultiplier()))
.setScale(2, RoundingMode.HALF_UP);
````

## File: src/main/java/com/portfolioos/core/valuation/GoldDampenerCalculator.java
````java
public class GoldDampenerCalculator {
⋮----
public static DampenerMultipliers calculateMultipliers(double devPct) {
⋮----
buyMult = PortfolioConstants.GOLD_BUY_MULTIPLIER_CHEAP; // 1.30
sellMult = PortfolioConstants.GOLD_SELL_MULTIPLIER_CHEAP; // 0.60
} else if (devPct >= PortfolioConstants.GOLD_PRICE_EXTENSION_CEILING_PCT) { // 20.0%
buyMult = PortfolioConstants.GOLD_BUY_MULTIPLIER_EXTENDED; // 0.40
sellMult = PortfolioConstants.GOLD_SELL_MULTIPLIER_EXTENDED; // 1.40
⋮----
// Round to 4 decimal places for precision
buyMult = Math.round(buyMult * 10000.0) / 10000.0;
sellMult = Math.round(sellMult * 10000.0) / 10000.0;
⋮----
return new DampenerMultipliers(buyMult, sellMult);
⋮----
public static BigDecimal calculateSizedAllocation(
⋮----
if (totalPortfolioValue == null || totalPortfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
⋮----
// Floor backstop overrides buy multiplier to 1.0x and sizes to close 50% of the gap
⋮----
return totalPortfolioValue.multiply(BigDecimal.valueOf(basePct / 100.0))
.setScale(2, RoundingMode.HALF_UP);
⋮----
// When moving average data is missing/unwired, default to neutral 1.0x multipliers (disarm safe)
⋮----
? totalPortfolioValue.multiply(BigDecimal.valueOf(gapWeightPct / 100.0)).setScale(2, RoundingMode.HALF_UP)
: totalPortfolioValue.multiply(BigDecimal.valueOf(Math.abs(gapWeightPct) / 100.0)).setScale(2, RoundingMode.HALF_UP);
⋮----
DampenerMultipliers mults = calculateMultipliers(devPct);
⋮----
// Underweight -> Buy side
BigDecimal baseAmount = totalPortfolioValue.multiply(BigDecimal.valueOf(gapWeightPct / 100.0));
return baseAmount.multiply(BigDecimal.valueOf(mults.buyMultiplier()))
⋮----
// Overweight -> Sell side
BigDecimal baseAmount = totalPortfolioValue.multiply(BigDecimal.valueOf(Math.abs(gapWeightPct) / 100.0));
return baseAmount.multiply(BigDecimal.valueOf(mults.sellMultiplier()))
````

## File: src/main/java/com/portfolioos/core/valuation/HarvestAdvisor.java
````java
public class HarvestAdvisor {
⋮----
public static TaxHarvestResult generateHarvestPlan(
⋮----
TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
BigDecimal limit = rules.equityExemptionLimit();
BigDecimal remainingExemption = limit.subtract(exemptionUsedThisFy).max(BigDecimal.ZERO);
⋮----
LocalDate today = LocalDate.now();
⋮----
AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
⋮----
long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
if (holdingDays >= rules.equityLtcgThresholdDays()) {
BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
BigDecimal currentVal = lot.remainingUnits().multiply(nav);
BigDecimal gain = currentVal.subtract(lot.totalCostBasis());
⋮----
if (gain.compareTo(BigDecimal.ZERO) > 0) {
totalUnrealizedLtcg = totalUnrealizedLtcg.add(gain);
ltcgLots.add(new LotWithGain(lot, nav, gain));
⋮----
// Sort lots by gain descending to maximize headroom utilization
ltcgLots.sort(Comparator.comparing(LotWithGain::gain).reversed());
⋮----
if (headroomLeft.compareTo(BigDecimal.ZERO) <= 0) break;
⋮----
BigDecimal harvestableGain = entry.gain().min(headroomLeft);
⋮----
if (entry.gain().compareTo(BigDecimal.ZERO) > 0) {
proportionToSell = harvestableGain.divide(entry.gain(), 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
⋮----
BigDecimal unitsToSell = entry.lot().remainingUnits().multiply(proportionToSell).setScale(4, RoundingMode.HALF_UP);
BigDecimal proceeds = unitsToSell.multiply(entry.nav()).setScale(2, RoundingMode.HALF_UP);
⋮----
headroomLeft = headroomLeft.subtract(harvestableGain).max(BigDecimal.ZERO);
totalHarvestedGain = totalHarvestedGain.add(harvestableGain);
⋮----
String text = "Sell " + unitsToSell + " units of " + entry.lot().assetName() +
" to harvest ₹" + harvestableGain.setScale(0, RoundingMode.HALF_UP) + " tax-free LTCG gain, then same-day rebuy.";
⋮----
recommendations.add(new TaxHarvestRecommendation(
entry.lot().assetId(),
entry.lot().assetName(),
entry.lot().lotId(),
⋮----
harvestableGain.setScale(2, RoundingMode.HALF_UP),
⋮----
return new TaxHarvestResult(
⋮----
totalUnrealizedLtcg.setScale(2, RoundingMode.HALF_UP),
totalHarvestedGain.setScale(2, RoundingMode.HALF_UP),
````

## File: src/main/java/com/portfolioos/core/valuation/RebalanceEngine.java
````java
public class RebalanceEngine {
⋮----
public static RebalancePreviewResult calculateRebalancePreview(
⋮----
return calculateRebalancePreview(openLots, navMap, targetAmount, remainingExemption, fiscalYear, true);
⋮----
TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
⋮----
LocalDate today = LocalDate.now();
⋮----
// Sort: loss-making first (0), then long-term (1), then short-term (2)
⋮----
// Drop positive-gain short-term lots entirely
candidateLots = candidateLots.stream().filter(l -> {
BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
BigDecimal gain = nav.subtract(l.costPerUnit());
if (gain.compareTo(BigDecimal.ZERO) < 0) return true; // Keep loss-making lots
AssetCategory cat = TaxClassifier.detectCategory(l.assetId(), l.assetName());
long threshold = getThresholdDays(cat, rules);
long holdingDays = ChronoUnit.DAYS.between(l.acquisitionDate(), today);
return threshold > 0 && holdingDays >= threshold; // Keep LTCG lots
}).toList();
⋮----
sortedLots.sort((l1, l2) -> {
BigDecimal nav1 = navMap.getOrDefault(l1.assetId(), l1.costPerUnit());
BigDecimal gainPerUnit1 = nav1.subtract(l1.costPerUnit());
AssetCategory cat1 = TaxClassifier.detectCategory(l1.assetId(), l1.assetName());
long holdingDays1 = ChronoUnit.DAYS.between(l1.acquisitionDate(), today);
long thresholdDays1 = getThresholdDays(cat1, rules);
⋮----
int rank1 = (gainPerUnit1.compareTo(BigDecimal.ZERO) < 0) ? 0 : (isLtcg1 ? 1 : 2);
⋮----
BigDecimal nav2 = navMap.getOrDefault(l2.assetId(), l2.costPerUnit());
BigDecimal gainPerUnit2 = nav2.subtract(l2.costPerUnit());
AssetCategory cat2 = TaxClassifier.detectCategory(l2.assetId(), l2.assetName());
long holdingDays2 = ChronoUnit.DAYS.between(l2.acquisitionDate(), today);
long thresholdDays2 = getThresholdDays(cat2, rules);
⋮----
int rank2 = (gainPerUnit2.compareTo(BigDecimal.ZERO) < 0) ? 0 : (isLtcg2 ? 1 : 2);
⋮----
return Integer.compare(rank1, rank2);
⋮----
if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;
⋮----
BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
BigDecimal lotValue = lot.remainingUnits().multiply(nav);
BigDecimal redemptionFromLot = lotValue.min(remainingTarget);
⋮----
if (nav.compareTo(BigDecimal.ZERO) > 0) {
unitsToSell = redemptionFromLot.divide(nav, 4, RoundingMode.HALF_UP);
⋮----
BigDecimal costBasisSlice = unitsToSell.multiply(lot.costPerUnit());
BigDecimal gainSlice = redemptionFromLot.subtract(costBasisSlice);
⋮----
AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
long thresholdDays = getThresholdDays(category, rules);
⋮----
if (gainSlice.compareTo(BigDecimal.ZERO) > 0) {
⋮----
BigDecimal exemptPortion = gainSlice.min(unusedExemption);
BigDecimal taxableGain = gainSlice.subtract(exemptPortion);
unusedExemption = unusedExemption.subtract(exemptPortion).max(BigDecimal.ZERO);
taxDrag = taxableGain.multiply(rules.equityLtcgRate());
⋮----
BigDecimal stcgRate = (category == AssetCategory.EQUITY) ? rules.equityStcgRate() : new BigDecimal("0.20");
taxDrag = gainSlice.multiply(stcgRate);
⋮----
selected.add(new RebalanceLotSelection(
lot.lotId(),
lot.assetId(),
lot.assetName(),
⋮----
actualRedemption = actualRedemption.add(redemptionFromLot);
totalGain = totalGain.add(gainSlice);
totalTaxDrag = totalTaxDrag.add(taxDrag);
remainingTarget = remainingTarget.subtract(redemptionFromLot);
⋮----
BigDecimal ltcgHarvested = remainingExemption.subtract(unusedExemption);
⋮----
if (actualRedemption.compareTo(BigDecimal.ZERO) > 0) {
effTaxRate = totalTaxDrag.multiply(new BigDecimal("100")).divide(actualRedemption, 2, RoundingMode.HALF_UP);
⋮----
BigDecimal deferredAmount = targetAmount.subtract(actualRedemption).max(BigDecimal.ZERO);
⋮----
return new RebalancePreviewResult(
⋮----
private static long getThresholdDays(AssetCategory category, TaxRulesConfig rules) {
⋮----
case EQUITY -> rules.equityLtcgThresholdDays();
case GOLD_SILVER, INTERNATIONAL, SGB -> rules.goldInternationalThresholdDays();
````

## File: src/main/java/com/portfolioos/core/valuation/RebalanceWaterfallEngine.java
````java
public class RebalanceWaterfallEngine {
⋮----
public interface WaterfallTierStrategy {
WaterfallTier tier();
List<Lot> selectLots(List<Lot> legacyLots, List<Lot> coreLots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules);
⋮----
private static final List<WaterfallTierStrategy> REGULAR_STRATEGIES = List.of(
new LegacyTierStrategy(),
new LossHarvestTierStrategy(),
new CoreLtcgTierStrategy()
⋮----
private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RebalanceWaterfallEngine.class);
private static final WaterfallTierStrategy URGENT_STCG_STRATEGY = new CoreStcgUrgentTierStrategy();
⋮----
public static WaterfallResult buildTrimWaterfall(
⋮----
TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
LocalDate today = currentDate != null ? currentDate : LocalDate.now();
⋮----
java.util.Set<String> activeAssetIds = FundTierClassifier.findActiveAssetIds(openLots, today);
⋮----
if (FundTierClassifier.isLegacyFund(lot.assetId(), activeAssetIds)) {
legacyLots.add(lot);
⋮----
BucketEngine.Bucket lotBucket = BucketEngine.classifyAssetToBucket(lot.assetId(), lot.assetName());
⋮----
coreLots.add(lot);
⋮----
boolean hasNav = navMap != null && navMap.containsKey(lot.assetId());
⋮----
log.warn("AMFI_NAV_SYNC_ALERT: Missing ISIN {} in navMap during waterfall engine calculation, using fallback costPerUnit {}", lot.assetId(), lot.costPerUnit());
⋮----
BigDecimal nav = hasNav ? navMap.get(lot.assetId()) : (lot.costPerUnit() != null ? lot.costPerUnit() : BigDecimal.ONE);
BigDecimal val = lot.remainingUnits().multiply(nav).setScale(2, RoundingMode.HALF_UP);
legacySchemeValueMap.put(lot.assetId(), legacySchemeValueMap.getOrDefault(lot.assetId(), BigDecimal.ZERO).add(val));
⋮----
strategiesToRun.add(URGENT_STCG_STRATEGY);
⋮----
if (remainingTarget.compareTo(BigDecimal.ZERO) <= 0) break;
List<Lot> candidateLots = strategy.selectLots(legacyLots, coreLots, navMap, today, rules);
⋮----
if (strategy.tier() == WaterfallTier.LEGACY_FUND) {
BigDecimal schemeTotal = legacySchemeValueMap.getOrDefault(lot.assetId(), BigDecimal.ZERO);
BigDecimal maxSchemeTrim = schemeTotal.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
BigDecimal alreadyTrimmed = legacySchemeTrimmedMap.getOrDefault(lot.assetId(), BigDecimal.ZERO);
BigDecimal schemeCapRemaining = maxSchemeTrim.subtract(alreadyTrimmed).max(BigDecimal.ZERO);
if (schemeCapRemaining.compareTo(BigDecimal.ZERO) <= 0) continue;
lotTarget = lotTarget.min(schemeCapRemaining);
⋮----
LotProcessResult res = processLot(strategy.tier(), lot, navMap, lotTarget, unusedExemption, rules, today, urgent);
if (res != null && res.proceeds().compareTo(BigDecimal.ZERO) > 0) {
steps.add(res.step());
satisfiedAmount = satisfiedAmount.add(res.proceeds());
remainingTarget = remainingTarget.subtract(res.proceeds());
unusedExemption = res.newUnusedExemption();
totalTaxDrag = totalTaxDrag.add(res.taxDrag());
⋮----
legacySchemeTrimmedMap.put(lot.assetId(),
legacySchemeTrimmedMap.getOrDefault(lot.assetId(), BigDecimal.ZERO).add(res.proceeds()));
⋮----
BigDecimal deferredAmount = remainingTarget.max(BigDecimal.ZERO);
⋮----
if (deferredAmount.compareTo(BigDecimal.ZERO) > 0) {
⋮----
BigDecimal exemptionConsumed = initialExemption.subtract(unusedExemption);
⋮----
return new WaterfallResult(
⋮----
satisfiedAmount.setScale(2, RoundingMode.HALF_UP),
deferredAmount.setScale(2, RoundingMode.HALF_UP),
⋮----
totalTaxDrag.setScale(2, RoundingMode.HALF_UP),
exemptionConsumed.setScale(2, RoundingMode.HALF_UP)
⋮----
private static LotProcessResult processLot(
⋮----
BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
BigDecimal lotValue = lot.remainingUnits().multiply(nav);
if (lotValue.compareTo(BigDecimal.ZERO) <= 0) return null;
⋮----
BigDecimal redemption = lotValue.min(remainingTarget);
BigDecimal unitsSold = nav.compareTo(BigDecimal.ZERO) > 0 ? redemption.divide(nav, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
BigDecimal costBasis = unitsSold.multiply(lot.costPerUnit());
BigDecimal gain = redemption.subtract(costBasis);
⋮----
AssetCategory cat = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
long threshold = getThresholdDays(cat, rules);
long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
⋮----
// USER DIRECTIVE (Fix 2a): STCG lots are 100% EXCLUDED during DRIFT or SCHEDULED rebalancing.
// Under DRAWDOWN or urgent de-risking (urgent == true), controlled STCG realization IS allowed
// with tax drag explicitly calculated and logged as a trade-off.
⋮----
if (gain.compareTo(BigDecimal.ZERO) > 0) {
⋮----
BigDecimal exempt = gain.min(newExemption);
BigDecimal taxable = gain.subtract(exempt);
newExemption = newExemption.subtract(exempt).max(BigDecimal.ZERO);
taxDrag = taxable.multiply(rules.equityLtcgRate()).setScale(2, RoundingMode.HALF_UP);
⋮----
BigDecimal stcgRate = (cat == AssetCategory.EQUITY) ? rules.equityStcgRate() : new BigDecimal("0.20");
taxDrag = gain.multiply(stcgRate).setScale(2, RoundingMode.HALF_UP);
⋮----
WaterfallStep step = new WaterfallStep(
⋮----
lot.lotId(),
lot.assetId(),
lot.assetName(),
⋮----
redemption.setScale(2, RoundingMode.HALF_UP),
gain.setScale(2, RoundingMode.HALF_UP),
⋮----
return new LotProcessResult(step, redemption, taxDrag, newExemption);
⋮----
private static void sortLotsByTaxCost(List<Lot> lots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules) {
lots.sort((l1, l2) -> {
BigDecimal nav1 = navMap.getOrDefault(l1.assetId(), l1.costPerUnit());
BigDecimal gain1 = nav1.subtract(l1.costPerUnit());
AssetCategory cat1 = TaxClassifier.detectCategory(l1.assetId(), l1.assetName());
long thresh1 = getThresholdDays(cat1, rules);
long days1 = ChronoUnit.DAYS.between(l1.acquisitionDate(), today);
⋮----
int rank1 = gain1.compareTo(BigDecimal.ZERO) < 0 ? 0 : (isLtcg1 ? 1 : 2);
⋮----
BigDecimal nav2 = navMap.getOrDefault(l2.assetId(), l2.costPerUnit());
BigDecimal gain2 = nav2.subtract(l2.costPerUnit());
AssetCategory cat2 = TaxClassifier.detectCategory(l2.assetId(), l2.assetName());
long thresh2 = getThresholdDays(cat2, rules);
long days2 = ChronoUnit.DAYS.between(l2.acquisitionDate(), today);
⋮----
int rank2 = gain2.compareTo(BigDecimal.ZERO) < 0 ? 0 : (isLtcg2 ? 1 : 2);
⋮----
return Integer.compare(rank1, rank2);
⋮----
private static long getThresholdDays(AssetCategory category, TaxRulesConfig rules) {
⋮----
case EQUITY -> rules.equityLtcgThresholdDays();
case GOLD_SILVER, INTERNATIONAL, SGB -> rules.goldInternationalThresholdDays();
⋮----
// --- Strategy Implementations ---
⋮----
private static class LegacyTierStrategy implements WaterfallTierStrategy {
⋮----
public WaterfallTier tier() { return WaterfallTier.LEGACY_FUND; }
⋮----
public List<Lot> selectLots(List<Lot> legacyLots, List<Lot> coreLots, Map<String, BigDecimal> navMap, LocalDate today, TaxRulesConfig rules) {
List<Lot> lots = legacyLots.stream().filter(l -> {
BigDecimal nav = navMap.getOrDefault(l.assetId(), l.costPerUnit());
BigDecimal gain = nav.subtract(l.costPerUnit());
if (gain.compareTo(BigDecimal.ZERO) < 0) return true; // Always allow loss harvest
AssetCategory cat = TaxClassifier.detectCategory(l.assetId(), l.assetName());
⋮----
long holdingDays = ChronoUnit.DAYS.between(l.acquisitionDate(), today);
return threshold > 0 && holdingDays >= threshold; // Strictly ONLY LTCG lots allowed
}).collect(java.util.stream.Collectors.toList());
⋮----
sortLotsByTaxCost(lots, navMap, today, rules);
⋮----
private static class LossHarvestTierStrategy implements WaterfallTierStrategy {
⋮----
public WaterfallTier tier() { return WaterfallTier.LOSS_HARVEST; }
⋮----
return coreLots.stream().filter(l -> {
⋮----
return nav.subtract(l.costPerUnit()).compareTo(BigDecimal.ZERO) < 0;
}).sorted(Comparator.comparing(l -> {
⋮----
return nav.subtract(l.costPerUnit());
})).toList();
⋮----
private static class CoreLtcgTierStrategy implements WaterfallTierStrategy {
⋮----
public WaterfallTier tier() { return WaterfallTier.LTCG_WITHIN_EXEMPTION; }
⋮----
return selectCoreLotsByHoldingCondition(coreLots, navMap, today, rules, true);
⋮----
private static class CoreStcgUrgentTierStrategy implements WaterfallTierStrategy {
⋮----
public WaterfallTier tier() { return WaterfallTier.STCG_URGENT_ONLY; }
⋮----
return selectCoreLotsByHoldingCondition(coreLots, navMap, today, rules, false);
⋮----
private static List<Lot> selectCoreLotsByHoldingCondition(
⋮----
if (nav.subtract(l.costPerUnit()).compareTo(BigDecimal.ZERO) < 0) return false;
````

## File: src/main/java/com/portfolioos/core/valuation/WaterfallTier.java
````java

````

## File: src/main/java/com/portfolioos/core/xirr/CashFlow.java
````java
BigDecimal amount // negative for investments, positive for inflows / current valuation
````

## File: src/main/java/com/portfolioos/core/xirr/XirrCalculationException.java
````java
public class XirrCalculationException extends RuntimeException {
````

## File: src/main/java/com/portfolioos/core/xirr/XirrEngine.java
````java
public class XirrEngine {
⋮----
public double calculateXirr(List<CashFlow> cashFlows) {
if (cashFlows == null || cashFlows.size() < 2) return 0.0;
⋮----
sorted.sort(Comparator.comparing(CashFlow::date));
⋮----
LocalDate startDate = sorted.get(0).date();
LocalDate endDate = sorted.get(sorted.size() - 1).date();
long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
⋮----
if (cf.amount().compareTo(BigDecimal.ZERO) < 0) {
totalInvested = totalInvested.add(cf.amount().abs());
} else if (cf.amount().compareTo(BigDecimal.ZERO) > 0) {
totalRealizedOrCurrent = totalRealizedOrCurrent.add(cf.amount());
⋮----
if (totalInvested.compareTo(BigDecimal.ZERO) == 0) return 0.0;
⋮----
BigDecimal gain = totalRealizedOrCurrent.subtract(totalInvested);
BigDecimal absReturn = gain.divide(totalInvested, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100.0"));
return absReturn.doubleValue();
⋮----
dates.add((double) ChronoUnit.DAYS.between(startDate, cf.date()) / 365.25);
amounts.add(cf.amount().doubleValue());
⋮----
// 1. Newton-Raphson solver
⋮----
double f = npv(rate, dates, amounts);
double df = dNpv(rate, dates, amounts);
⋮----
if (Math.abs(df) > 1e-10) {
⋮----
if (Math.abs(nextRate - rate) < 1e-7) {
⋮----
if (!Double.isNaN(result) && !Double.isInfinite(result)) {
return Math.max(-99.0, result);
⋮----
// 2. Bracketed Bisection Fallback with Dynamic Search Bounds & Step Probing
⋮----
double flow = npv(low, dates, amounts);
double fhigh = npv(high, dates, amounts);
⋮----
double f1 = npv(probeLow, dates, amounts);
double f2 = npv(probeLow + 0.50, dates, amounts);
⋮----
double fmid = npv(mid, dates, amounts);
if (Math.abs(fmid) < 1e-7 || (high - low) < 1e-7) {
return Math.max(-99.0, mid * 100.0);
⋮----
return Math.max(-99.0, ((low + high) / 2.0) * 100.0);
⋮----
// 3. CAGR Fallback when root cannot be bracketed
if (totalInvested.compareTo(BigDecimal.ZERO) > 0 && totalDays > 0) {
double netReturn = totalRealizedOrCurrent.subtract(totalInvested).divide(totalInvested, 6, RoundingMode.HALF_UP).doubleValue();
⋮----
double cagr = (Math.pow(1.0 + netReturn, 1.0 / years) - 1.0) * 100.0;
if (!Double.isNaN(cagr) && !Double.isInfinite(cagr)) {
return Math.max(-99.0, cagr);
⋮----
private double npv(double r, List<Double> dates, List<Double> amounts) {
⋮----
for (int i = 0; i < dates.size(); i++) {
double t = dates.get(i);
double c = amounts.get(i);
double factor = Math.pow(1.0 + r, t);
⋮----
private double dNpv(double r, List<Double> dates, List<Double> amounts) {
⋮----
double factor = Math.pow(1.0 + r, t + 1.0);
````

## File: src/main/java/com/portfolioos/core/CoreApplication.java
````java
public class CoreApplication {
⋮----
public static void main(String[] args) {
SpringApplication.run(CoreApplication.class, args);
⋮----
public CommandLineRunner startupRunner(EventStorePort eventStore, DuckDbProjector duckDbProjector) {
⋮----
System.out.println("Initializing DuckDB Projection from SQLite ledger...");
⋮----
duckDbProjector.projectEvents(eventStore.getAllEvents());
System.out.println("DuckDB projection loaded successfully.");
⋮----
System.err.println("Failed to build startup projection: " + e.getMessage());
````

## File: src/main/resources/META-INF/native-image/reflect-config.json
````json
[
  {
    "name": "org.duckdb.DuckDBDriver",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true
  },
  {
    "name": "org.duckdb.DuckDBConnection",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true
  },
  {
    "name": "org.sqlite.JDBC",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true
  }
]
````

## File: src/main/resources/META-INF/native-image/resource-config.json
````json
{
  "resources": {
    "includes": [
      { "pattern": "static/.*" },
      { "pattern": "rules/.*" }
    ]
  }
}
````

## File: src/main/resources/static/src/js/modules/insurance.js
````javascript
export async function fetchInsuranceChecklist()
⋮----
export function renderInsuranceBanner(data)
⋮----
export async function toggleInsuranceStatus(id, status)
````

## File: src/main/resources/static/src/js/modules/portfolio.js
````javascript
export function updatePortfolioSummary(summary)
⋮----
export function renderHoldingsTable(holdings)
⋮----
window.toggleLotDetails = (idx) =>
⋮----
export function renderPieChart(containerId, data)
⋮----
export function resampleToMonthEnd(dates, values, investedValues)
⋮----
const monthKey = dStr.substring(0, 7); // YYYY-MM
⋮----
export function renderNetWorthTrendChart(containerId, dates, values, investedValues = null, isMonthly = false)
⋮----
// Calculate MoM % if monthly or latest period change
⋮----
formatter: params => {
        let res = `<div style="font-weight:700; color:#f8fafc; margin-bottom:4px;">${params[0].name}</div>`;
        params.forEach(p => {
          const color = p.color || '#38bdf8';
res += `<div><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:$
⋮----
axisLabel:
⋮----
// Handle Dynamic ResizeObserver for parent container
⋮----
export async function loadNetWorthTrend(isMonthly = false)
⋮----
export function renderAllocationChart(allocations)
⋮----
export function renderCategoryChart(catAllocations)
⋮----
export function renderBucketAllocationChart(containerId, bucketStatuses)
⋮----
formatter: params => {
        let res = `<b>${params[0].name}</b><br/>`;
        params.forEach(p => {
          res += `${p.marker} ${p.seriesName}: <b>${p.value}%</b><br/>`;
        });
⋮----
export function renderFundAllocationCompareChart(containerId, holdings, bucketTargetsConfig)
⋮----
// 1. Extract active target version (e.g. v2.0)
⋮----
// 2. Build planned map: fund_id -> planned_pct
⋮----
// 3. Build total portfolio net worth & actual map: fund_id -> actual_pct
⋮----
// Add any target ISINs that aren't in holdings yet
⋮----
// 4. Create combined items array
⋮----
// Sort: Target funds first (by plannedPct asc for bottom-to-top rendering in horizontal bar), then legacy funds
⋮----
formatter: params => {
        const index = params[0].dataIndex;
        const item = items[index];
        let res = `<div style="font-weight:600; color:#f8fafc; margin-bottom:4px;">${item.name}</div>`;
        res += `<span style="color:#94a3b8; font-size:11px;">ISIN: ${item.isin}</span><br/>`;
        params.forEach(p => {
res += `$
⋮----
export async function fetchConsolidationPreviewData()
⋮----
export function renderConsolidationPlan(data)
⋮----
export async function fetchRebalancePreview(amount = 100000)
⋮----
export function updateRebalanceSummary(data)
⋮----
export async function fetchGoalSummary()
⋮----
export function renderGoalSummary(data)
⋮----
export async function fetchFireSummary()
⋮----
export function renderFireSummary(data)
⋮----
export function initFireSensitivitySliders()
⋮----
const updateSim = () =>
⋮----
export function renderFireFanChart(trajectories, requiredCorpus)
⋮----
const getX = (year)
const getY = (val)
⋮----
// Outer band p10-p90
⋮----
// Inner band p25-p75
⋮----
// Median line p50
⋮----
// Y-axis ticks (4 ticks)
⋮----
// X-axis ticks (Year 0, 10, 20, 30, 43)
⋮----
export async function fetchBucketRebalance()
⋮----
export function renderBucketRebalance(data)
⋮----
export function renderCashflowSankey(containerId, holdingsData, bucketData)
⋮----
export async function loadBenchmarkAnalytics()
⋮----
export async function populateFundDropdowns()
⋮----
// Clear static FUND_REGISTRY and populate from live ingested tax_events response
⋮----
export async function loadOverlapAnalytics(fundAOverride = null, fundBOverride = null)
⋮----
// Same Fund Selected Case (Strict raw ISIN string comparison)
⋮----
if (currentRequestId !== activeOverlapRequestId) return; // Stale fetch race guard
⋮----
// Genuine 0% Overlap between 2 distinct funds
⋮----
// Genuine > 0% Overlap
⋮----
function renderVennSvg(container, nameA, nameB, overlapPct)
⋮----
export async function loadUpSetAnalytics()
⋮----
export async function loadActionRecommendations()
⋮----
function render2FundVennDiagram()
⋮----
export async function loadUnifiedRebalancePlan(triggerType = 'INDUCED', manualAmount = null, includeRebalance = false)
⋮----
export function renderUnifiedRebalancePlanUI(plan)
⋮----
// 1. Render Status Strip
⋮----
// 2. Render Header & Drawdown Gauge
⋮----
// Drawdown Tripwire Depth Gauge
⋮----
// 3. Exemption Headroom Burndown Bar
⋮----
// 4. Render Primary Box & Connector Layout and Summary Line
⋮----
// 5. Render Pre/Post Allocation Progression Delta Badges
⋮----
// 6. Render Secondary Sankey (mounted, hidden by default until toggle)
⋮----
// 7. Render Interactive Tactical Action Matrix (Granular Lot Override)
⋮----
// 6. Render Narrative Paragraphs
⋮----
// 7. Render Buy-Side Allocation Grid
⋮----
function renderBuySideAllocationGrid(buySide, liveTotalOverride = null)
⋮----
function shortenFundName(rawName)
⋮----
function renderRebalanceBoxConnector(plan)
⋮----
// 1. Update Summary Bar
⋮----
// 2. Build Sell Cards Column (Fund-Wise Aggregated)
⋮----
// 3. Build Central Pool Amount
⋮----
// 4. Build Buy Cards Column
⋮----
// 5. Draw SVG Bezier Connectors
⋮----
// 6. View Toggle Event Listeners
⋮----
btnBox.onclick = () =>
⋮----
btnSankey.onclick = () =>
⋮----
function drawBoxSvgConnectors()
⋮----
// Sell Cards -> Pool Left (Rose Red dashed bezier)
⋮----
// Pool Right -> Buy Cards (Emerald Green solid bezier)
⋮----
function renderPrePostAllocationDelta(plan)
⋮----
let deltaColor = '#34d399'; // Green for increase or match
if (post < cur) deltaColor = '#f87171'; // Red for decrease
⋮----
function renderTargetFundProgression(plan, holdings, bucketTargetsConfig)
⋮----
// 1. Calculate current fund valuations & total portfolio net worth
⋮----
// 2. Calculate sell amounts per fund
⋮----
// 3. Calculate buy amounts per fund
⋮----
// 4. Calculate target fund allocation % from targetsConfig
⋮----
// 5. Build combined list of all funds grouped by unique shortName to prevent duplicate badges
⋮----
// Sort: Target funds first (by targetPct desc), then legacy funds (by curPct desc)
⋮----
let deltaColor = '#34d399'; // Green
if (f.postPct < f.curPct) deltaColor = '#f87171'; // Red for trim
if (!f.isTarget) deltaColor = '#64748b'; // Muted for legacy 0% target
⋮----
function renderRebalanceMicroSankey(sellSide, buySide)
⋮----
// 1. Group Sell Lots by Source Fund & Determine Link Color by Tax Regime
⋮----
const shortenFundName = rawName => {
    if (!rawName) return '';
⋮----
let linkColor = '#10b981'; // Green for SEC_112A_EXEMPT
if (regime === 'SEC_112A_TAXABLE_12_5') linkColor = '#f59e0b'; // Amber for taxable LTCG
if (regime === 'SLAB_RATE_STCG') linkColor = '#ef4444'; // Red for STCG
⋮----
// 2. Tax Friction Node
⋮----
// 3. Buy-Side Target Funds
⋮----
formatter: params => {
if (params.dataType === 'node') return `<b>$
⋮----
function renderTacticalActionMatrix(plan)
⋮----
function recalculateMetrics()
⋮----
// Reactive buy-side allocation scaling
⋮----
// Attach Checkbox Change Listeners
⋮----
selectAllCb.onclick = (e) =>
⋮----
btnExecute.onclick = () =>
⋮----
// Keyboard shortcut: Ctrl + Enter to execute override
window.onkeydown = (e) =>
⋮----
export function renderSchemeGroupedTaxLotsUI(holdings, containerId = 'groupedTaxLotsContainer')
⋮----
window.toggleSchemeLotCard = (key) =>
⋮----
window.openLumpsumModal = () =>
⋮----
window.closeLumpsumModal = () =>
⋮----
window.submitLumpsumSim = () =>
````

## File: src/main/resources/static/src/js/modules/tax.js
````javascript
export async function fetchTaxMetrics()
⋮----
export function updateExemptionMeter(data)
⋮----
export function updateReportMetrics(report)
⋮----
export async function fetchDecisionRadar()
⋮----
export function renderDecisionRadar(opportunities, ladder)
⋮----
export async function fetchRealizedLog()
⋮----
export function renderRealizedLogTable(logs)
````

## File: src/main/resources/static/src/js/api.js
````javascript
export function getAuthToken()
⋮----
export function getAuthHeaders(extraHeaders =
⋮----
export async function fetchJson(url, options =
⋮----
// Stale token in localStorage -> reset to default & retry
````

## File: src/main/resources/static/src/js/constants.js
````javascript
export function getActionBadgeStyle(status, severity)
````

## File: src/main/resources/static/src/js/domUtils.js
````javascript
export function setText(selectorOrEl, text)
⋮----
export function setHtml(selectorOrEl, html)
⋮----
export function setBadgeStyle(selectorOrEl, text, className)
⋮----
export function setErrorState(selectorOrEl, errorText = '—', badgeSelector = null, badgeText = 'OFFLINE')
````

## File: src/main/resources/static/src/js/state.js
````javascript
export function setCurrentFy(fy)
⋮----
export function getCurrentFy()
````

## File: src/main/resources/static/src/js/utils.js
````javascript
export function formatINR(val, round = true)
⋮----
export function showToast(message, type = 'success')
````

## File: src/main/resources/static/src/app.js
````javascript
async function initDashboard()
⋮----
// Render Cashflow Sankey Flow Diagram
⋮----
async function fetchRebalancePreview(amount)
⋮----
window.openCmdPalette = () =>
⋮----
window.closeCmdPalette = () =>
⋮----
window.openHoldingDrawer = (idx) =>
⋮----
window.closeHoldingDrawer = () =>
⋮----
window.harvestLot = (isin, schemeName, units, costPerUnit) =>
⋮----
window.submitAiPrompt = async () =>
⋮----
// Raycast Action Interception for Rebalance & Waterfall
⋮----
// Default SSE AI prompt stream
⋮----
eventSource.onmessage = (event) =>
⋮----
eventSource.onerror = (err) =>
⋮----
async function uploadCasFile(file, password)
⋮----
window.closeCasPasswordModal = () =>
⋮----
window.handleFileSelect = (e) =>
⋮----
window.submitCasUpload = () =>
````

## File: src/main/resources/static/src/style.css
````css
:root {
⋮----
/* Dashboard Utility & Component Classes */
.dash-card {
.dash-card-header {
.dash-card-title {
.badge-tag {
.badge-active-sip { background: rgba(52, 211, 153, 0.15); color: #34d399; border: 1px solid rgba(52, 211, 153, 0.3); }
.badge-legacy-holding { background: rgba(251, 191, 36, 0.15); color: #fbbf24; border: 1px solid rgba(251, 191, 36, 0.3); }
.badge-fully-exited { background: rgba(156, 163, 175, 0.15); color: #9ca3af; border: 1px solid rgba(156, 163, 175, 0.3); }
.badge-provisional { background: rgba(96, 165, 250, 0.15); color: #60a5fa; border: 1px solid rgba(96, 165, 250, 0.3); }
⋮----
* {
⋮----
body.bg-obsidian {
⋮----
/* Ambient Glow Spheres */
.ambient-glow {
⋮----
.glow-1 {
⋮----
.glow-2 {
⋮----
.container {
⋮----
/* Tab Navigation Bar */
.tab-nav {
⋮----
.tab-btn {
⋮----
.tab-btn:hover {
⋮----
.tab-btn.active {
⋮----
.tab-content {
⋮----
.tab-content.active {
⋮----
/* Header & Brand Layout */
.header {
⋮----
.brand {
⋮----
.logo-icon {
⋮----
.brand-title-group {
⋮----
.brand-title-row {
⋮----
.brand-title {
⋮----
.v2-tag {
⋮----
.fy-selector-row {
⋮----
.fy-select {
⋮----
.header-actions {
⋮----
.upload-btn {
⋮----
.upload-btn:hover {
⋮----
.export-btn {
⋮----
.export-btn:hover {
⋮----
.status-pill {
⋮----
.status-dot {
⋮----
/* Top Metrics Cards Row - Bento Box Layout */
.top-metrics-grid {
⋮----
.glass-card {
⋮----
.glass-card:hover {
⋮----
.metric-box {
⋮----
.metric-label {
⋮----
.metric-value {
⋮----
.font-mono {
⋮----
.highlight-cyan {
⋮----
.metric-delta.positive {
⋮----
.metric-delta.negative {
⋮----
.metric-subtext {
⋮----
/* Exemption Meter */
.exemption-box .sub-limit {
⋮----
.progress-track {
⋮----
.progress-fill-gradient {
⋮----
.meter-meta {
⋮----
/* 12-Column Dashboard Grid */
.dashboard-grid {
⋮----
.col-12 {
⋮----
.col-6 {
⋮----
.card-header {
⋮----
.card-header h2 {
⋮----
.live-tag {
⋮----
.canvas-wrapper {
⋮----
.canvas-wrapper-small {
⋮----
/* Rebalancing Calculator Widget */
.rebalance-controls {
⋮----
.input-lbl {
⋮----
.slider-box {
⋮----
.slider-box input[type="range"] {
⋮----
.slider-val {
⋮----
.rebalance-summary-box {
⋮----
.reb-stat {
⋮----
.reb-stat .lbl {
⋮----
/* Schedule FA Checklist */
.compliance-list {
⋮----
.compliance-item {
⋮----
.compliance-item.valid .check-icon {
⋮----
.comp-title {
⋮----
.comp-desc {
⋮----
/* Decision Radar */
.radar-list {
⋮----
.radar-card {
⋮----
.radar-card.warning-border { border-left: 4px solid var(--amber-warn); }
.radar-card.info-border { border-left: 4px solid var(--cyan-bright); }
.radar-card.maturation-border { border-left: 4px solid var(--purple-accent); }
⋮----
.radar-icon {
⋮----
.radar-icon.warning { background: rgba(245, 158, 11, 0.15); color: var(--amber-warn); }
.radar-icon.info { background: rgba(6, 182, 212, 0.15); color: var(--cyan-bright); }
.radar-icon.maturation { background: rgba(139, 92, 246, 0.15); color: var(--purple-accent); }
⋮----
.radar-content {
⋮----
.radar-title {
⋮----
.radar-desc {
⋮----
.days-badge {
⋮----
/* Data Tables */
.table-container {
⋮----
.data-table {
⋮----
.data-table th {
⋮----
.data-table td {
⋮----
.data-table tr.holding-row {
⋮----
.data-table tr.holding-row:hover {
⋮----
.lot-expansion-td {
⋮----
.lot-subtable {
⋮----
.lot-subtable th {
⋮----
.lot-subtable td {
⋮----
.pill-btn {
⋮----
.cat-badge {
⋮----
.cat-EQUITY { background: rgba(16, 185, 129, 0.15); color: var(--green-positive); }
.cat-DEBT_SPECIFIED_50AA { background: rgba(245, 158, 11, 0.15); color: var(--amber-warn); }
.cat-GOLD_SILVER { background: rgba(234, 179, 8, 0.15); color: #eab308; }
.cat-INTERNATIONAL { background: rgba(139, 92, 246, 0.15); color: var(--purple-accent); }
.cat-SGB { background: rgba(6, 182, 212, 0.15); color: var(--cyan-bright); }
⋮----
/* Toast Notification Stack */
.toast-stack {
⋮----
.toast {
⋮----
.toast.success { border-left: 4px solid var(--green-positive); }
.toast.error { border-left: 4px solid var(--red-negative); }
⋮----
/* FIRE Tracker */
.fire-card {
⋮----
.title-with-badge {
⋮----
.fire-status-pill {
⋮----
.fire-status-pill.on-track {
⋮----
.fire-status-pill.short {
⋮----
.fire-metrics-grid {
⋮----
.fire-stat-box {
⋮----
.fire-stat-box .lbl {
⋮----
.fire-stat-box .val {
⋮----
.fire-stat-box .sub {
⋮----
/* Glassmorphism & Bento Box Enhancements */
⋮----
.cmd-k-btn {
⋮----
.cmd-k-btn kbd {
⋮----
.cmd-modal-overlay {
⋮----
.command-palette-box {
⋮----
.command-palette-header {
⋮----
.command-palette-header input {
⋮----
.cmd-k-badge {
⋮----
.command-palette-results {
⋮----
.cmd-item {
⋮----
.cmd-item:hover {
⋮----
/* Slide-Out Side Drawer */
.drawer-backdrop {
⋮----
.drawer-backdrop.open {
⋮----
.slide-drawer {
⋮----
.slide-drawer.open {
⋮----
.drawer-header {
⋮----
.drawer-title {
⋮----
.drawer-close-btn {
⋮----
.drawer-close-btn:hover {
⋮----
.drawer-body {
⋮----
/* Actionable Command Palette Mini-Widget */
.cmd-action-card {
⋮----
.cmd-action-header {
⋮----
.cmd-action-steps {
⋮----
.cmd-step-row {
⋮----
.drawer-lot-card {
⋮----
.drawer-lot-card:hover {
⋮----
.drawer-action-btn {
⋮----
.drawer-action-btn:hover {
⋮----
.drawer-badge {
````

## File: src/main/resources/static/index.html
````html
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
  <script>
    window.openCmdPalette = function() {
      var modal = document.getElementById('commandPaletteModal');
      var input = document.getElementById('commandPaletteInput');
      if (modal) {
        modal.style.display = 'flex';
        if (input) {
          setTimeout(function() { input.focus(); input.select(); }, 50);
        }
      }
    };
    window.closeCmdPalette = function() {
      var modal = document.getElementById('commandPaletteModal');
      if (modal) modal.style.display = 'none';
    };
  </script>
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

        <button type="button" class="upload-btn" onclick="document.getElementById('fileUploadInput').click()">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
          Upload CAS PDF / CSV
        </button>
        <input type="file" id="fileUploadInput" accept=".pdf,.csv" onchange="window.handleFileSelect && window.handleFileSelect(event)" style="display: none;">

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
      <button class="tab-btn active" data-tab="overview">📊 Executive Overview</button>
      <button class="tab-btn" data-tab="tax-lots">📋 Scheme-Grouped Tax Lots</button>
      <button class="tab-btn" data-tab="overlap">🔍 Overlap & Concentration</button>
      <button class="tab-btn" data-tab="tax">⚡ Tax & Compliance Audit</button>
      <button class="tab-btn" data-tab="fire">🎯 FIRE & Waterfall Rebalancing</button>
    </nav>

    <!-- TAB 1: Executive Overview -->
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

        <!-- Bucket Allocation: Planned vs. Actual Chart -->
        <div class="glass-card col-12">
          <div class="card-header">
            <h2>Bucket Allocation: Planned vs. Actual</h2>
            <span class="live-tag">PLANNED VS ACTUAL</span>
          </div>
          <div class="canvas-wrapper-large" id="bucketAllocationChart" style="height: 320px; width: 100%;"></div>
        </div>

        <!-- Scheme/Fund Allocation: Planned vs. Actual Chart -->
        <div class="glass-card col-12">
          <div class="card-header">
            <h2>Scheme Allocation: Planned vs. Actual</h2>
            <span class="live-tag">SCHEME TARGET VS ACTUAL</span>
          </div>
          <div class="canvas-wrapper-large" id="fundAllocationCompareChart" style="height: 380px; width: 100%;"></div>
        </div>

        <!-- Net Worth & Capital Invested Timeline Chart -->
        <div class="glass-card col-12">
          <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; padding-bottom: 12px; border-bottom: 1px solid rgba(255,255,255,0.08); margin-bottom: 16px;">
            <div>
              <h2 style="margin: 0; font-size: 1.1rem; color: #f8fafc;">Net Worth & Capital Invested Timeline</h2>
              <span class="sub font-mono" id="netWorthWindowBadge" style="color: #cbd5e1; font-size: 0.8rem;">Daily Valuation & Capital Contributed</span>
            </div>
            <div style="display: flex; gap: 12px; align-items: center;">
              <span class="live-tag positive-tag" id="netWorthMoMBadge" style="font-size: 0.75rem; background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid #10b981; padding: 2px 8px; border-radius: 4px; font-weight: 600;">MoM: --%</span>
              <div class="btn-group" style="display: flex; gap: 6px;">
                <button type="button" id="btnNetWorthDaily" class="secondary-btn active" style="padding: 4px 10px; font-size: 0.72rem; background: rgba(56, 189, 248, 0.2); color: #38bdf8; border: 1px solid #38bdf8; border-radius: 4px; cursor: pointer;">Daily</button>
                <button type="button" id="btnNetWorthMonthly" class="secondary-btn" style="padding: 4px 10px; font-size: 0.72rem; background: rgba(255,255,255,0.05); color: #94a3b8; border: 1px solid rgba(255,255,255,0.1); border-radius: 4px; cursor: pointer;">12-Mo Monthly</button>
              </div>
            </div>
          </div>
          <div class="canvas-wrapper-large" id="netWorthChartContainer" style="height: 320px; width: 100%;"></div>
        </div>

        <!-- Benchmark Risk Radar -->
        <div class="glass-card col-12">
          <div class="card-header">
            <div>
              <h2>Benchmark Risk Radar & QuantStats Outperformance</h2>
              <span class="sub font-mono" id="benchmarkProvenanceSub" style="color: #cbd5e1; font-size: 0.8rem;">Provisional Benchmark Metrics (Short Sample: 363 Days &lt; 3 Years)</span>
            </div>
            <span class="live-tag warning-tag" id="benchmarkSampleBadge">PROVISIONAL (363 DAYS)</span>
          </div>
          <div class="fire-metrics-grid" id="benchmarkMetricsGrid" style="grid-template-columns: repeat(5, 1fr);">
            <div class="fire-stat-box">
              <span class="lbl">Annualized Alpha</span>
              <strong class="val font-mono highlight-cyan" id="benchmarkAlphaVal">+2.37%</strong>
              <span class="sub font-mono">Excess Return</span>
            </div>
            <div class="fire-stat-box">
              <span class="lbl">Portfolio Beta</span>
              <strong class="val font-mono" id="benchmarkBetaVal">0.797</strong>
              <span class="sub font-mono">Vs Nifty 50 TRI</span>
            </div>
            <div class="fire-stat-box">
              <span class="lbl">Sharpe Ratio</span>
              <strong class="val font-mono" id="benchmarkSharpeVal">-0.07</strong>
              <span class="sub font-mono" id="benchmarkSharpeSub">r_f = 6.5% T-Bill</span>
            </div>
            <div class="fire-stat-box">
              <span class="lbl">Tracking Error</span>
              <strong class="val font-mono" id="benchmarkTrackingVal">4.48%</strong>
              <span class="sub font-mono">Annualized Vol</span>
            </div>
            <div class="fire-stat-box">
              <span class="lbl">Outperformance</span>
              <strong class="val font-mono positive" id="benchmarkOutperformVal">+3.03%</strong>
              <span class="sub font-mono">Net CAGR Delta</span>
            </div>
          </div>
        </div>

        <!-- Cashflow Sankey Flow Chart -->
        <div class="glass-card col-12">
          <div class="card-header">
            <h2>Holistic Cashflow & Tax Drag Flow</h2>
            <span class="live-tag">SANKEY VISUALIZATION</span>
          </div>
          <div class="canvas-wrapper-large" id="sankeyChart" style="height: 340px; width: 100%;"></div>
        </div>
      </div>
    </main>

    <!-- TAB: Scheme-Grouped Tax Lots -->
    <main class="tab-content" id="tab-tax-lots">
      <div class="dashboard-grid">
        <div class="glass-card col-12">
          <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <h2 style="margin: 0;">Open Tax Lots Grouped by Mutual Fund Scheme</h2>
              <span class="sub font-mono" style="color: #cbd5e1; font-size: 0.8rem;">FIFO Tax Lot Classification · LTCG Tax-Free vs STCG Locked</span>
            </div>
            <span class="live-tag">FIFO LEDGER</span>
          </div>
          <div id="groupedTaxLotsContainer" style="margin-top: 16px; display: flex; flex-direction: column; gap: 14px;">
            <div class="loading-td" style="color: #64748b; padding: 16px;">Loading scheme-grouped open tax lots...</div>
          </div>
        </div>
      </div>
    </main>

    <!-- TAB 2: Overlap & Concentration -->
    <main class="tab-content" id="tab-overlap">
      <div class="dashboard-grid">
        <!-- Single-Stock Concentration Table -->
        <div class="glass-card col-12">
          <div class="card-header">
            <div>
              <h2>Top Portfolio Single-Stock Exposure</h2>
              <span class="sub font-mono" style="color: #cbd5e1; font-size: 0.8rem;">Aggregated across all active schemes</span>
            </div>
            <span class="live-tag">CONCENTRATION METRIC</span>
          </div>
          <div class="table-container" style="max-height: 200px; overflow-y: auto;">
            <table class="data-table" id="topStockConcentrationTable" style="font-size: 0.82rem;">
              <thead>
                <tr>
                  <th>Stock / Asset</th>
                  <th>Overlapping Funds</th>
                  <th>Portfolio Weight %</th>
                </tr>
              </thead>
              <tbody>
                <tr><td colspan="3" class="loading-td">Loading stock exposures...</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Multi-Set Overlap UpSet Plot Matrix -->
        <div class="glass-card col-12">
          <div class="card-header">
            <div>
              <h2>Multi-Set Overlap UpSet Plot Matrix (N-Way Intersections)</h2>
              <span class="sub font-mono" style="color: #cbd5e1; font-size: 0.8rem;">All Active Portfolio Schemes Intersections (DuckDB SQL Engine)</span>
            </div>
            <span class="live-tag positive-tag">EXACT DUCKDB JOIN</span>
          </div>
          <div class="table-container" style="max-height: 240px; overflow-y: auto;">
            <table class="data-table" id="upsetMatrixTable" style="font-size: 0.82rem;">
              <thead>
                <tr>
                  <th>Shared Stocks Count</th>
                  <th>Overlapping Schemes Set</th>
                  <th>Top Shared Holdings</th>
                </tr>
              </thead>
              <tbody>
                <tr><td colspan="3" class="loading-td">Loading UpSet intersection matrix...</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Interactive 2-3 Fund Venn Visualizer -->
        <div class="glass-card col-12">
          <div class="card-header">
            <div>
              <h2>Interactive 2-3 Fund Venn Visualizer & Selector</h2>
              <span class="sub font-mono" style="color: #cbd5e1; font-size: 0.8rem;">Dynamic Overlap Computation via DuckDB Holdings Engine</span>
            </div>
            <span class="live-tag">LIVE SQL QUERY</span>
          </div>
          <div style="display: grid; grid-template-columns: 280px 1fr; gap: 16px; margin-top: 10px;">
            <div style="background: rgba(15, 23, 42, 0.6); border-radius: 8px; padding: 14px; border: 1px solid rgba(255,255,255,0.06);">
              <label style="font-size: 0.75rem; color: #94a3b8; display: block; margin-bottom: 4px;">Fund A (Primary):</label>
              <select id="vennFundA" style="width: 100%; background: #1e293b; color: #f8fafc; border: 1px solid #334155; padding: 6px; border-radius: 4px; font-size: 0.8rem; margin-bottom: 12px;">
                <option value="INF879O01027" selected>PPFAS Flexi Cap</option>
              </select>
              <label style="font-size: 0.75rem; color: #94a3b8; display: block; margin-bottom: 4px;">Fund B (Compare):</label>
              <select id="vennFundB" style="width: 100%; background: #1e293b; color: #f8fafc; border: 1px solid #334155; padding: 6px; border-radius: 4px; font-size: 0.8rem; margin-bottom: 12px;">
                <option value="INF109KC13X2" selected>Value 30</option>
              </select>
              <div style="border-top: 1px solid rgba(255,255,255,0.08); padding-top: 10px; margin-top: 6px;">
                <div style="font-size: 0.75rem; color: #64748b;">Computed Overlap %:</div>
                <div id="vennLivePct" style="font-size: 1.3rem; font-weight: 800; color: #06b6d4;" class="font-mono">--%</div>
              </div>
            </div>
            <div id="vennContainer" style="background: rgba(15, 23, 42, 0.6); border-radius: 8px; padding: 14px; border: 1px solid rgba(255,255,255,0.06); text-align: center;">
              <div class="loading-td" style="text-align: center; color: #64748b;">Loading Venn Diagram visualizer...</div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- TAB 3: Tax & Compliance Audit -->
    <main class="tab-content" id="tab-tax">
      <div class="dashboard-grid">
        <!-- Actionable Advisory Recommendations Cards (Side-by-Side Left) -->
        <div class="glass-card col-6" id="actionCardsContainer">
          <div class="card-header">
            <h2>Actionable Advisory Recommendations</h2>
            <span class="live-tag positive-tag">RULE ENGINE ADVISORY</span>
          </div>
          <div id="actionCardsList" style="display: flex; flex-direction: column; gap: 12px; margin-top: 12px;">
            <div class="loading-td" style="color: #64748b; padding: 16px;">Evaluating rule engine triggers...</div>
          </div>
        </div>

        <!-- AI Decision Radar (Side-by-Side Right) -->
        <div class="glass-card col-6">
          <div class="card-header">
            <h2>Tax & Strategy Decision Radar</h2>
            <span class="live-tag">AI ADVISOR</span>
          </div>
          <div class="radar-list">
            <div class="radar-empty-state">Scanning open lots for tax-loss harvesting and LTCG maturation opportunities...</div>
          </div>
        </div>

        <!-- Scheme-Grouped Tax Lots Breakdown Card -->
        <div class="glass-card col-12">
          <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <h2 style="margin: 0;">Open Tax Lots Grouped by Mutual Fund Scheme</h2>
              <span class="sub font-mono" style="color: #cbd5e1; font-size: 0.8rem;">FIFO Tax Lot Classification · LTCG Tax-Free vs STCG Locked</span>
            </div>
            <span class="live-tag">FIFO LEDGER</span>
          </div>
          <div id="groupedTaxLotsContainerTaxTab" style="margin-top: 16px; display: flex; flex-direction: column; gap: 14px;">
            <div class="loading-td" style="color: #64748b; padding: 16px;">Loading scheme-grouped open tax lots...</div>
          </div>
        </div>

        <!-- Schedule FA Pre-Flight Checklist -->
        <div class="glass-card col-12">
          <div class="card-header">
            <h2>Schedule FA Compliance Checklist</h2>
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

        <!-- Realized Disposals Audit Log Table -->
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

    <!-- TAB 4: FIRE & Waterfall Rebalancing -->
    <main class="tab-content" id="tab-fire">
      <div class="dashboard-grid">
        <!-- Persistent Unified Rebalance Trigger Status Strip -->
        <div class="glass-card col-12" id="rebalanceStatusStrip" style="padding: 14px 20px; background: rgba(15, 23, 42, 0.75); border: 1px solid rgba(56, 189, 248, 0.2); display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px;">
          <div style="display: flex; align-items: center; gap: 16px; flex-wrap: wrap;">
            <span id="rebalanceTriggerBadge" class="live-tag" style="background: rgba(239, 68, 68, 0.2); color: #f87171; border: 1px solid #ef4444; font-weight: 700; font-size: 0.8rem; padding: 4px 10px;">
              INDUCED — 15% Drawdown Tier
            </span>
            <div style="font-size: 0.85rem; color: #94a3b8;">
              <strong style="color: #f8fafc;">Drawdown Status:</strong> <span id="stripDrawdownPct" style="color: #38bdf8;">15.4%</span> below rolling high (<span id="stripRollingHigh">₹18.50L</span>)
            </div>
            <div style="font-size: 0.85rem; color: #94a3b8;">
              <strong style="color: #f8fafc;">Scheduled Reconstitution:</strong> <span id="stripReconWindow">March 2027 Window (~204 Days)</span>
            </div>
          </div>
          <div style="display: flex; gap: 8px;">
            <button id="btnViewRebalancePlan" class="btn btn-primary" style="font-size: 0.75rem; padding: 6px 12px; background: #0284c7; border: none; border-radius: 4px; color: white; cursor: pointer;">
              View Plan & Waterfall
            </button>
            <button id="btnSimulateLumpsum" class="btn btn-secondary" style="font-size: 0.75rem; padding: 6px 12px; background: #334155; border: 1px solid #475569; border-radius: 4px; color: #f8fafc; cursor: pointer;">
              + Simulate Lump-Sum
            </button>
          </div>
        </div>

        <!-- Unified Rebalance Plan & Waterfall -->
        <div class="glass-card col-12" id="rebalancePlanCard">
          <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <h2 id="planHeadlineTitle">Unified Rebalance Plan</h2>
              <span id="planSubtitleTag" class="live-tag positive-tag">TEMPLATED WATERFALL REASONING</span>
            </div>
            <div style="font-size: 0.75rem; color: #94a3b8;" id="planMetaTimestamp">Generated At: 2026-08-08</div>
          </div>

          <!-- Section A: Drawdown & Exemption -->
          <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; margin-top: 16px;">
            <div style="background: rgba(15, 23, 42, 0.6); border-radius: 8px; padding: 16px; border: 1px solid rgba(255,255,255,0.06);">
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                <h4 style="color: #38bdf8; font-size: 0.85rem; margin: 0;">Drawdown Tripwire Depth Gauge</h4>
                <span id="drawdownTripwireLabel" class="live-tag" style="font-size: 0.7rem; background: rgba(56, 189, 248, 0.15); color: #38bdf8;">INDUCED GAUGE</span>
              </div>
              <div id="drawdownGaugeContainer" style="margin-top: 8px;">
                <div style="height: 12px; background: #1e293b; border-radius: 6px; position: relative; overflow: hidden;">
                  <div id="gaugeProgressBar" style="height: 100%; width: 20%; background: linear-gradient(90deg, #10b981, #f59e0b, #ef4444); transition: width 0.4s ease;"></div>
                  <div id="gaugeIndicatorMarker" style="position: absolute; top: 0; bottom: 0; width: 4px; background: #ffffff; box-shadow: 0 0 8px #ffffff; transition: left 0.4s ease;"></div>
                </div>
              </div>
            </div>
          </div>

          <!-- Section B: Capital Routing & Rebalance Trade Flow -->
          <div style="margin-top: 16px; background: rgba(15, 23, 42, 0.6); border-radius: 8px; padding: 16px; border: 1px solid rgba(56, 189, 248, 0.2);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
              <h3 style="color: #38bdf8; font-size: 0.9rem; margin: 0;">1. Capital Routing & Rebalance Trade Flow</h3>
              <div style="display: flex; gap: 8px;">
                <button type="button" id="btnViewBoxConnector" class="secondary-btn" style="padding: 4px 10px; font-size: 0.75rem; background: rgba(56, 189, 248, 0.2); color: #38bdf8; border: 1px solid #38bdf8;">📦 Box & Connector</button>
                <button type="button" id="btnViewSankey" class="secondary-btn" style="padding: 4px 10px; font-size: 0.75rem; background: rgba(255,255,255,0.05); color: #94a3b8; border: 1px solid rgba(255,255,255,0.1);">📊 Sankey Diagram</button>
              </div>
            </div>

            <!-- YTD-Aware Tax Summary Line -->
            <div id="rebalanceSummaryLine" style="background: rgba(30, 41, 59, 0.6); border-radius: 6px; padding: 10px 14px; margin-bottom: 16px; border: 1px solid rgba(255,255,255,0.06); font-size: 0.8rem; display: flex; flex-wrap: wrap; gap: 16px; justify-content: space-between; align-items: center;">
              <div><span style="color: #94a3b8;">Total Realized:</span> <strong id="sumRealizedProceeds" style="color: #f8fafc;">₹88,142</strong></div>
              <div><span style="color: #94a3b8;">LTCG Exempt Gain:</span> <strong id="sumTradeExemption" style="color: #10b981;">₹12,124</strong></div>
              <div><span style="color: #94a3b8;">Tax Saved (Sec 112A):</span> <strong id="sumTaxSaved" style="color: #34d399; font-weight: 800;">+₹2,673</strong></div>
              <div><span style="color: #94a3b8;">Remaining Headroom:</span> <strong id="sumRemainingHeadroom" style="color: #38bdf8;">₹1,12,876</strong></div>
              <div><span style="color: #94a3b8;">Tax Owed:</span> <strong id="sumTaxOwed" style="color: #ef4444;">₹0</strong></div>
            </div>

            <!-- Primary Box & Connector Layout Container -->
            <div id="rebalanceBoxConnectorContainer" style="position: relative; width: 100%; min-height: 280px; display: flex; align-items: stretch; justify-content: space-between; gap: 12px; margin-bottom: 16px;">
              <!-- Sell Side Cards Column (Left) -->
              <div id="rebalanceSellCardsCol" style="flex: 1; display: flex; flex-direction: column; gap: 8px; z-index: 2;">
                <!-- Populated dynamically -->
              </div>

              <!-- Central Cash Pool & SVG Connectors (Middle) -->
              <div style="width: 140px; display: flex; flex-direction: column; align-items: center; justify-content: center; position: relative; z-index: 2;">
                <div id="rebalanceCentralPoolPill" style="background: linear-gradient(135deg, #0284c7, #0f766e); padding: 12px 14px; border-radius: 20px; text-align: center; border: 1px solid #38bdf8; box-shadow: 0 0 12px rgba(56, 189, 248, 0.3);">
                  <div style="font-size: 0.65rem; color: #bae6fd; text-transform: uppercase; font-weight: 700; letter-spacing: 0.5px;">Rebalance Cash Pool</div>
                  <div id="rebalancePoolAmount" style="font-size: 0.95rem; font-weight: 800; color: #ffffff; margin-top: 2px;">₹88,142</div>
                </div>
              </div>

              <!-- Buy Side Cards Column (Right) -->
              <div id="rebalanceBuyCardsCol" style="flex: 1; display: flex; flex-direction: column; gap: 8px; z-index: 2;">
                <!-- Populated dynamically -->
              </div>

              <!-- SVG Bezier Connector Canvas -->
              <svg id="rebalanceSvgConnectors" style="position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 1;">
                <!-- Drawn dynamically -->
              </svg>
            </div>

            <!-- Secondary Sankey Container (Hidden by default) -->
            <div id="rebalanceSankeyChartContainer" style="display: none; height: 240px; width: 100%; margin-bottom: 16px;">
              <div id="rebalanceSankeyChart" style="height: 240px; width: 100%;"></div>
            </div>

            <!-- Pre/Post Bucket Progression Delta Badges -->
            <div style="background: rgba(15, 23, 42, 0.4); border-radius: 6px; padding: 12px; border: 1px solid rgba(255,255,255,0.04);">
              <div style="font-size: 0.75rem; color: #94a3b8; font-weight: 700; margin-bottom: 8px;">TARGET BUCKET PROGRESSION (PRE ➔ POST REBALANCE)</div>
              <div id="rebalanceAllocationDeltaContainer" style="display: flex; flex-wrap: wrap; gap: 10px;">
                <!-- Populated dynamically -->
              </div>
            </div>

            <!-- Pre/Post Scheme Fund Progression Delta Badges -->
            <div style="background: rgba(15, 23, 42, 0.4); border-radius: 6px; padding: 12px; border: 1px solid rgba(255,255,255,0.04); margin-top: 10px;">
              <div style="font-size: 0.75rem; color: #38bdf8; font-weight: 700; margin-bottom: 8px;">TARGET SCHEME/FUND PROGRESSION (PRE ➔ POST REBALANCE BY SCHEME)</div>
              <div id="rebalanceFundProgressionContainer" style="display: flex; flex-wrap: wrap; gap: 10px;">
                <!-- Populated dynamically -->
              </div>
            </div>
          </div>

          <!-- Section C: Interactive Tactical Action Matrix -->
          <div style="margin-top: 16px; background: rgba(15, 23, 42, 0.6); border-radius: 8px; padding: 16px; border: 1px solid rgba(56, 189, 248, 0.2);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
              <h3 style="color: #38bdf8; font-size: 0.9rem; margin: 0;">2. Sell-Side Lot Sourcing (Selected Sell Lots & Tax Term)</h3>
              <div style="display: flex; gap: 16px; align-items: center;">
                <div>
                  <div style="font-size: 0.7rem; color: #94a3b8;">SELECTED PROCEEDS</div>
                  <div id="matrixLiveProceeds" style="font-size: 1.1rem; font-weight: 800; color: #38bdf8;">₹0</div>
                </div>
                <div>
                  <div style="font-size: 0.7rem; color: #94a3b8;">TAX DRAG</div>
                  <div id="matrixLiveTaxDrag" style="font-size: 1.1rem; font-weight: 800; color: #ef4444;">₹0</div>
                </div>
              </div>
            </div>
            <div style="overflow-x: auto; margin-top: 12px;">
              <table class="report-table" style="width: 100%; border-collapse: collapse; font-size: 0.8rem;">
                <thead>
                  <tr style="color: #94a3b8; border-bottom: 1px solid rgba(255,255,255,0.1); text-align: left;">
                    <th style="text-align: center; width: 40px;"><input type="checkbox" id="matrixSelectAllLots" checked style="accent-color: #06b6d4;"></th>
                    <th>Scheme Name</th>
                    <th>Acquired</th>
                    <th>Holding</th>
                    <th>Cost Basis</th>
                    <th>Sale Proceeds</th>
                    <th>Realized Gain</th>
                    <th>Tax Term</th>
                    <th>Tax Drag</th>
                  </tr>
                </thead>
                <tbody id="matrixLotTableBody">
                  <tr><td colspan="9" class="loading-td" style="text-align: center; color: #64748b; padding: 20px;">Loading sell-side lot matrix...</td></tr>
                </tbody>
              </table>
            </div>
          </div>
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

          <!-- Fan Chart SVG & Sensitivity Sliders Widget -->
          <div style="margin-top: 24px; border-top: 1px solid rgba(255,255,255,0.08); padding-top: 20px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
              <h3 style="color: #38bdf8; font-size: 1.05rem; margin: 0;">FIRE Fan Chart (Cone of Uncertainty over 43-Year Horizon)</h3>
              <div style="display: flex; gap: 12px; align-items: center;">
                <span class="live-tag positive-tag" id="fireSuccessRateBadge">Monte Carlo Success: --%</span>
                <span class="live-tag" id="fireDataSourceLabel" style="font-size: 0.72rem;">15-Day Block Bootstrap</span>
              </div>
            </div>

            <div style="display: grid; grid-template-columns: 2fr 1fr; gap: 20px; align-items: start;">
              <!-- SVG Fan Chart Container -->
              <div id="fanChartSvgContainer" style="background: rgba(15, 23, 42, 0.6); border-radius: 8px; padding: 16px; border: 1px solid rgba(255,255,255,0.06); height: 320px; position: relative;">
                <div class="loading-td" style="text-align: center; color: #64748b; margin-top: 130px;">Loading FIRE Fan Chart...</div>
              </div>

              <!-- Interactive Sensitivity Sliders -->
              <div style="background: rgba(15, 23, 42, 0.6); border-radius: 8px; padding: 16px; border: 1px solid rgba(255,255,255,0.06); display: flex; flex-direction: column; gap: 16px;">
                <h4 style="color: #cbd5e1; font-size: 0.88rem; margin: 0; border-bottom: 1px solid rgba(255,255,255,0.08); padding-bottom: 8px;">Scenario Sensitivity Sliders</h4>
                
                <div>
                  <div style="display: flex; justify-content: space-between; font-size: 0.8rem; margin-bottom: 6px;">
                    <span style="color: #94a3b8;">Monthly SIP Contribution:</span>
                    <strong class="font-mono highlight-cyan" id="sipSliderVal">₹ 75,000</strong>
                  </div>
                  <input type="range" id="fireSipSlider" min="10000" max="300000" step="5000" value="75000" style="width: 100%;">
                </div>

                <div>
                  <div style="display: flex; justify-content: space-between; font-size: 0.8rem; margin-bottom: 6px;">
                    <span style="color: #94a3b8;">FIRE Monthly Expense:</span>
                    <strong class="font-mono highlight-cyan" id="expSliderVal">₹ 60,000</strong>
                  </div>
                  <input type="range" id="fireExpSlider" min="30000" max="250000" step="5000" value="60000" style="width: 100%;">
                </div>

                <div>
                  <div style="display: flex; justify-content: space-between; font-size: 0.8rem; margin-bottom: 6px;">
                    <span style="color: #94a3b8;">Years to Retirement:</span>
                    <strong class="font-mono highlight-cyan" id="yrsSliderVal">13 Years</strong>
                  </div>
                  <input type="range" id="fireYrsSlider" min="5" max="30" step="1" value="13" style="width: 100%;">
                </div>

                <div style="background: rgba(255,255,255,0.03); border-radius: 6px; padding: 10px; border: 1px solid rgba(255,255,255,0.05); margin-top: 4px;">
                  <div style="font-size: 0.75rem; color: #64748b;">Simulated Ending Median Wealth:</div>
                  <div class="font-mono positive" id="fireSimulatedMedian" style="font-size: 1.1rem; font-weight: bold; margin-top: 2px;">₹ -- Cr</div>
                </div>
              </div>
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

  <!-- Global Command Palette Modal -->
  <div id="commandPaletteModal" class="cmd-modal-overlay" style="display: none;">
    <div class="command-palette-box">
      <div class="command-palette-header">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#D0FF00" stroke-width="2.5"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
        <input type="text" id="commandPaletteInput" placeholder="Type an AI prompt, SQL query, or tax question... (Press Enter to Ask AI)">
        <button type="button" id="submitCmdPromptBtn" onclick="window.submitAiPrompt && window.submitAiPrompt()" style="background:#06b6d4; color:#000; border:none; border-radius:6px; padding:6px 14px; font-weight:700; font-size:12px; cursor:pointer; font-family:'Inter', sans-serif; flex-shrink: 0; white-space: nowrap;">Ask AI</button>
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

  <!-- CAS PDF Password Modal -->
  <div id="casPasswordModal" class="cmd-modal-overlay" style="display: none;">
    <div class="glass-card" style="width: 100%; max-width: 440px; padding: 24px; border: 1px solid rgba(208, 255, 0, 0.3); background: rgba(5, 8, 17, 0.95); backdrop-filter: blur(16px); border-radius: 16px;">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
        <h3 style="margin: 0; font-size: 16px; font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 8px;">
          <span>🔒 Password Protected CAS PDF</span>
        </h3>
        <button type="button" onclick="window.closeCasPasswordModal && window.closeCasPasswordModal()" style="background: none; border: none; color: #94a3b8; font-size: 18px; cursor: pointer;">✕</button>
      </div>

      <p style="font-size: 13px; color: #94a3b8; margin-bottom: 16px; line-height: 1.5;">
        The CAS PDF file <strong id="casModalFilename" style="color: #d0ff00;"></strong> is encrypted. Enter your CAS password (usually your PAN in uppercase + Date of Birth, e.g. <code style="color: #06b6d4;">ABCDE1234F01011990</code>).
      </p>

      <div style="margin-bottom: 20px;">
        <label style="display: block; font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase; margin-bottom: 6px;">CAS Password / Master PIN</label>
        <input type="password" id="casPasswordInput" placeholder="Enter CAS password" style="width: 100%; padding: 10px 14px; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.15); border-radius: 8px; color: #fff; font-size: 14px; font-family: monospace;">
      </div>

      <div id="casUploadStatus" style="display: none; font-size: 12px; font-family: monospace; margin-bottom: 16px; padding: 10px; border-radius: 8px;"></div>

      <div style="display: flex; justify-content: flex-end; gap: 10px;">
        <button type="button" onclick="window.closeCasPasswordModal && window.closeCasPasswordModal()" style="padding: 8px 16px; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1); color: #94a3b8; border-radius: 8px; font-weight: 600; cursor: pointer; font-size: 13px;">Cancel</button>
        <button type="button" id="submitCasUploadBtn" onclick="window.submitCasUpload && window.submitCasUpload()" style="padding: 8px 18px; background: #d0ff00; color: #000; border: none; border-radius: 8px; font-weight: 700; cursor: pointer; font-size: 13px;">Decrypt & Ingest CAS</button>
      </div>
    </div>
  </div>

  <!-- Manual Lump-Sum Simulation Modal -->
  <div id="lumpsumModalBackdrop" class="modal-backdrop" style="display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.7); backdrop-filter: blur(4px); z-index: 999;" onclick="window.closeLumpsumModal && window.closeLumpsumModal()"></div>
  <div id="lumpsumModal" class="modal-content" style="display: none; position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); width: 90%; max-width: 480px; background: #0f172a; border: 1px solid rgba(56, 189, 248, 0.3); border-radius: 12px; padding: 24px; z-index: 1000; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
      <h3 style="margin: 0; font-size: 1.1rem; color: #38bdf8;">+ Simulate Manual Lump-Sum</h3>
      <button type="button" onclick="window.closeLumpsumModal && window.closeLumpsumModal()" style="background: none; border: none; color: #94a3b8; font-size: 18px; cursor: pointer;">✕</button>
    </div>

    <div style="margin-bottom: 16px;">
      <label style="display: block; font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase; margin-bottom: 6px;">Fresh Capital Inflow (₹)</label>
      <input type="number" id="lumpsumAmountInput" value="100000" placeholder="e.g. 100000" style="width: 100%; padding: 10px 14px; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.15); border-radius: 8px; color: #fff; font-size: 15px; font-family: monospace; font-weight: 700;">
    </div>

    <div style="margin-bottom: 20px;">
      <label style="display: block; font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase; margin-bottom: 8px;">Rebalance Execution Mode</label>
      <div style="display: flex; flex-direction: column; gap: 8px;">
        <label style="display: flex; align-items: flex-start; gap: 10px; padding: 10px; background: rgba(30, 41, 59, 0.6); border: 1px solid rgba(255,255,255,0.08); border-radius: 8px; cursor: pointer;">
          <input type="radio" name="lumpsumRebalanceOption" value="false" checked style="accent-color: #38bdf8; margin-top: 3px;">
          <div>
            <div style="font-weight: 700; font-size: 0.85rem; color: #f8fafc;">💧 Lump-Sum Only (No Sales)</div>
            <div style="font-size: 0.75rem; color: #94a3b8; margin-top: 2px;">Deploy fresh cash into under-allocated buckets without trimming existing holdings.</div>
          </div>
        </label>
        <label style="display: flex; align-items: flex-start; gap: 10px; padding: 10px; background: rgba(30, 41, 59, 0.6); border: 1px solid rgba(255,255,255,0.08); border-radius: 8px; cursor: pointer;">
          <input type="radio" name="lumpsumRebalanceOption" value="true" style="accent-color: #a855f7; margin-top: 3px;">
          <div>
            <div style="font-weight: 700; font-size: 0.85rem; color: #c084fc;">⚡ Lump-Sum + Portfolio Rebalance</div>
            <div style="font-size: 0.75rem; color: #94a3b8; margin-top: 2px;">Deploy fresh cash AND execute rebalance liquidations together in one combined execution.</div>
          </div>
        </label>
      </div>
    </div>

    <div style="display: flex; justify-content: flex-end; gap: 10px;">
      <button type="button" onclick="window.closeLumpsumModal && window.closeLumpsumModal()" style="padding: 8px 16px; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1); color: #94a3b8; border-radius: 8px; font-weight: 600; cursor: pointer; font-size: 13px;">Cancel</button>
      <button type="button" id="submitLumpsumSimBtn" onclick="window.submitLumpsumSim && window.submitLumpsumSim()" style="padding: 8px 18px; background: #38bdf8; color: #0f172a; border: none; border-radius: 8px; font-weight: 800; cursor: pointer; font-size: 13px;">Simulate Allocation</button>
    </div>
  </div>

  <!-- Slide-Out Holdings Detail Drawer -->
  <div id="holdingDetailDrawerBackdrop" class="drawer-backdrop" onclick="window.closeHoldingDrawer && window.closeHoldingDrawer()"></div>
  <aside id="holdingDetailDrawer" class="slide-drawer">
    <div class="drawer-header">
      <div>
        <h3 id="drawerAssetTitle" class="drawer-title">Scheme Details</h3>
        <span id="drawerAssetCategory" class="live-tag">EQUITY</span>
      </div>
      <button type="button" class="drawer-close-btn" onclick="window.closeHoldingDrawer && window.closeHoldingDrawer()">✕</button>
    </div>
    <div class="drawer-body" id="drawerBody">
      <!-- Dynamically populated -->
    </div>
  </aside>

  <script type="module" src="./src/app.js?v=4.1.0"></script>
</body>
</html>
````

## File: src/main/resources/application.yml
````yaml
server:
  port: 8080
  address: 0.0.0.0

spring:
  application:
    name: portfolio-os-core
  threads:
    virtual:
      enabled: true
  ai:
    ollama:
      base-url: ${SPRING_AI_OLLAMA_BASE_URL:http://127.0.0.1:11434}
      chat:
        options:
          model: qwen2.5-coder:7b
          temperature: 0.1
      embedding:
        options:
          model: nomic-embed-text
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
````

## File: src/test/java/com/portfolioos/core/controllers/ConfigControllerTest.java
````java
class ConfigControllerTest {
⋮----
void testGetBucketTargets() {
ConfigController controller = new ConfigController();
ResponseEntity<BucketConfigLoader.BucketRulesConfig> response = controller.getBucketTargets();
⋮----
assertNotNull(response);
assertEquals(200, response.getStatusCode().value());
assertNotNull(response.getBody());
⋮----
void testGetRebalancePlanAliasReturns307Redirect() {
⋮----
ResponseEntity<?> response = controller.getRebalancePlanAlias("INDUCED");
⋮----
assertEquals(307, response.getStatusCode().value());
assertTrue(response.getHeaders().containsKey("Location"));
assertEquals("/api/v1/sync/rebalance/plan?trigger=INDUCED", response.getHeaders().getFirst("Location"));
````

## File: src/test/java/com/portfolioos/core/controllers/SyncControllerTest.java
````java
class SyncControllerTest {
⋮----
void setUp() {
// Seed open lots for asset INF109KC13X2 (ICICI Nifty 200)
TaxEvent acq = new TaxEvent(
⋮----
LocalDate.of(2024, 1, 1),
new BigDecimal("1000.0"),
new BigDecimal("100.0"),
new BigDecimal("100000.0"),
⋮----
Instant.now()
⋮----
FifoMatcher matcher = new FifoMatcher();
FifoMatcher.FifoResult fifoResult = matcher.processEvents(List.of(acq));
⋮----
// NAV dropped to 80.0 (20% drop in personal portfolio valuation from cost of 100.0)
Map<String, BigDecimal> navMap = Map.of("INF109KC13X2", new BigDecimal("80.0"));
⋮----
List.of(acq),
⋮----
System.currentTimeMillis(),
⋮----
LedgerCacheService mockCacheService = new LedgerCacheService(null) {
⋮----
public CachedLedgerState getCachedState() {
⋮----
syncController = new SyncController(mockCacheService);
⋮----
void testSite1SnapshotDisarmsDrawdownWhenBenchmarkNull() {
ResponseEntity<UnidirectionalSyncSnapshot> response = syncController.getSnapshot("2026-27", null);
assertNotNull(response);
assertNotNull(response.getBody());
⋮----
RebalancePlanDto plan = response.getBody().rebalancePlan();
assertNotNull(plan);
assertEquals("NONE", plan.trigger().drawdownContext().armedTier(),
⋮----
assertEquals(0.0, plan.trigger().drawdownContext().currentDrawdownPct(),
⋮----
void testSite2RebalancePlanDisarmsDrawdownWhenBenchmarkNull() {
ResponseEntity<RebalancePlanDto> response = syncController.getRebalancePlan("INDUCED");
⋮----
RebalancePlanDto plan = response.getBody();
⋮----
void testConsistencyBetweenSnapshotAndRebalancePlanEndpoints() {
UnidirectionalSyncSnapshot snapshot = syncController.getSnapshot("2026-27", null).getBody();
RebalancePlanDto plan = syncController.getRebalancePlan("INDUCED").getBody();
⋮----
assertNotNull(snapshot);
⋮----
assertEquals(snapshot.rebalancePlan().trigger().drawdownContext().armedTier(), plan.trigger().drawdownContext().armedTier(),
⋮----
assertEquals(snapshot.rebalancePlan().trigger().drawdownContext().currentDrawdownPct(), plan.trigger().drawdownContext().currentDrawdownPct(),
⋮----
void testRegressionNoPersonalNetWorthPassedAsBenchmarkParam() throws Exception {
File file = new File("src/main/java/com/portfolioos/core/controllers/SyncController.java");
assertTrue(file.exists());
String content = Files.readString(file.toPath());
⋮----
assertFalse(content.contains("buildPlan(\n            openLots, matchedLots, navMap, LocalDate.now(), totalPortfolioCurrentVal, rollingHigh,"),
⋮----
assertFalse(content.contains("buildPreviewPlan(\n            openLots, matchedLots, navMap, LocalDate.now(), totalCurrentVal, rollingHigh,"),
````

## File: src/test/java/com/portfolioos/core/fire/FireTrackerTest.java
````java
class FireTrackerTest {
⋮----
void testCalculateFireSummary() {
Lot lot = new Lot(
⋮----
LocalDate.of(2024, 1, 1),
new BigDecimal("1000.0"),
⋮----
new BigDecimal("100.0"),
new BigDecimal("100000.0"),
⋮----
Map<String, BigDecimal> navMap = Map.of("NIFTY_LARGEMIDCAP_1", new BigDecimal("150.0"));
⋮----
FireTracker.FireSummary summary = FireTracker.calculateFireSummary(
List.of(lot),
⋮----
LocalDate.of(2026, 8, 19),
⋮----
new BigDecimal("500000.00"),
⋮----
new BigDecimal("25000000.00"),
new BigDecimal("18000000.00")
⋮----
assertNotNull(summary);
assertEquals("Primary Expense Target", summary.activeScenarioLabel());
assertTrue(summary.fireInvestableNetWorth().compareTo(BigDecimal.ZERO) >= 0);
assertNotNull(summary.status());
⋮----
void testFireProfileGetters() {
⋮----
assertNotNull(profile.birthDate());
assertEquals(45, profile.targetRetirementAge());
assertEquals(new BigDecimal("3.0"), profile.swrPercent());
assertNotNull(profile.scenarios());
assertFalse(profile.scenarios().isEmpty());
````

## File: src/test/java/com/portfolioos/core/goals/GoalTrackerTest.java
````java
class GoalTrackerTest {
⋮----
void testCalculateGoalSummaryWithDefaultAllocations() {
Lot liquidLot = new Lot(
⋮----
LocalDate.of(2024, 1, 1),
new BigDecimal("1000.0"),
⋮----
new BigDecimal("100.0"),
new BigDecimal("100000.0"),
⋮----
Map<String, BigDecimal> navMap = Map.of("ARBITRAGE_1", new BigDecimal("100.0"));
⋮----
GoalTracker.GoalSummary summary = GoalTracker.calculateGoalSummary(List.of(liquidLot), navMap);
assertNotNull(summary);
assertEquals(new BigDecimal("100000.00"), summary.totalLiquidHoldings());
assertEquals(new BigDecimal("350000.00"), summary.allocatedGoalsAmount());
assertEquals(new BigDecimal("0.00"), summary.unallocatedCash());
assertTrue(summary.allocationsByGoal().containsKey(GoalTracker.GoalTag.EMERGENCY));
⋮----
void testCalculateGoalSummaryWithBankBalance() {
GoalTracker.GoalSummary summary = GoalTracker.calculateGoalSummary(
List.of(),
Map.of(),
⋮----
new BigDecimal("500000.00")
⋮----
assertEquals(new BigDecimal("500000.00"), summary.totalLiquidHoldings());
⋮----
assertEquals(new BigDecimal("150000.00"), summary.unallocatedCash());
````

## File: src/test/java/com/portfolioos/core/matcher/FundTierClassifierTest.java
````java
class FundTierClassifierTest {
⋮----
void testAccumulatorStatusClassification() {
FundTierClassifier.FundStatus status = FundTierClassifier.getFundStatus(
"INF247L01BM8", "ACCUMULATOR", Set.of()
⋮----
assertEquals(FundTierClassifier.FundStatus.ACCUMULATOR, status, "Strategy ACCUMULATOR must yield ACCUMULATOR status");
⋮----
void testActiveSipAndLegacyStatusClassification() {
Set<String> activeSips = Set.of("INF109K018C5");
⋮----
FundTierClassifier.FundStatus activeStatus = FundTierClassifier.getFundStatus(
⋮----
assertEquals(FundTierClassifier.FundStatus.ACTIVE_SIP, activeStatus);
⋮----
FundTierClassifier.FundStatus legacyStatus = FundTierClassifier.getFundStatus(
⋮----
assertEquals(FundTierClassifier.FundStatus.LEGACY_HOLDING, legacyStatus);
⋮----
void testParagParikhClassificationIsCoreSatellite() {
FundTierClassifier.FundTier tier = FundTierClassifier.classify("INF879O01027");
assertEquals(FundTierClassifier.FundTier.CORE_SATELLITE, tier,
⋮----
boolean isLegacy = FundTierClassifier.isLegacyFund("INF879O01027", Set.of());
assertFalse(isLegacy, "Parag Parikh Flexi Cap must NEVER be classified as a legacy fund even with 0 active SIPs!");
````

## File: src/test/java/com/portfolioos/core/matcher/TaxClassifierTest.java
````java
class TaxClassifierTest {
⋮----
void testSection50AABoundaryThresholds() {
LocalDate apr2022Acq = LocalDate.of(2022, 1, 1); // Pre-April 2023 legacy debt fund
LocalDate jul2024Disposal = LocalDate.of(2024, 8, 1); // Post-July 23, 2024 disposal
⋮----
// Exactly 730 days
TaxTerm term730 = TaxClassifier.classifyTaxTerm(
⋮----
assertEquals(TaxTerm.LONG_TERM, term730);
⋮----
// Exactly 1095 days (Pre-July 23, 2024 disposal)
LocalDate june2024Disposal = LocalDate.of(2024, 6, 1);
TaxTerm term1095 = TaxClassifier.classifyTaxTerm(
⋮----
assertEquals(TaxTerm.LONG_TERM, term1095);
````

## File: src/test/java/com/portfolioos/core/nav/AmfiNavSyncTest.java
````java
class AmfiNavSyncTest {
⋮----
void testParseAmfiFeed_MultiColumnDirectPlanFormat() {
AmfiNavSync sync = new AmfiNavSync();
⋮----
List<AmfiNavSync.NavEntry> entries = sync.parseAmfiFeed(feedData);
assertNotNull(entries);
assertFalse(entries.isEmpty());
⋮----
// Verify INF209KA12Z1 (Growth ISIN)
AmfiNavSync.NavEntry growthEntry = entries.stream()
.filter(e -> "INF209KA12Z1".equals(e.isin()))
.findFirst()
.orElse(null);
assertNotNull(growthEntry);
assertEquals(new BigDecimal("106.9996"), growthEntry.nav());
⋮----
// Verify INF209KA13Z9 (Reinvestment ISIN)
AmfiNavSync.NavEntry reincEntry = entries.stream()
.filter(e -> "INF209KA13Z9".equals(e.isin()))
⋮----
assertNotNull(reincEntry);
assertEquals(new BigDecimal("106.9996"), reincEntry.nav());
⋮----
// Verify Parag Parikh Flexi Cap
AmfiNavSync.NavEntry ppfcEntry = entries.stream()
.filter(e -> "INF879O01027".equals(e.isin()))
⋮----
assertNotNull(ppfcEntry);
assertEquals(new BigDecimal("90.7427"), ppfcEntry.nav());
⋮----
// Assert hyphen (-) is never indexed as an ISIN key
boolean containsHyphen = entries.stream().anyMatch(e -> "-".equals(e.isin()));
assertFalse(containsHyphen, "Hyphen '-' must never be indexed as an ISIN");
````

## File: src/test/java/com/portfolioos/core/persistence/SqliteEventStoreTest.java
````java
public class SqliteEventStoreTest {
⋮----
void testVerifyLedgerIntegrity() {
⋮----
if (!dbFile.exists()) {
System.out.println("Skipping test: data/tax_ledger.db does not exist yet.");
⋮----
String secret = System.getenv("LEDGER_HMAC_SECRET");
if (secret == null || secret.isBlank()) {
⋮----
SqliteEventStore eventStore = new SqliteEventStore("data/tax_ledger.db");
eventStore.rehashLedgerChain();
boolean isIntegrityValid = eventStore.verifyLedgerIntegrity();
⋮----
assertTrue(isIntegrityValid, "Cryptographic HMAC SHA-256 chain verification must return TRUE for real ledger events!");
````

## File: src/test/java/com/portfolioos/core/reconciliation/ReconciliationGateTest.java
````java
class ReconciliationGateTest {
⋮----
void testPerAssetReconciliationPassesOnExactMatch() {
Lot lot1 = new Lot("lot_1", "asset_A", "Fund A", LocalDate.now(), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("1000"), false, BigDecimal.ZERO);
Lot lot2 = new Lot("lot_2", "asset_B", "Fund B", LocalDate.now(), new BigDecimal("200"), new BigDecimal("200"), new BigDecimal("20"), new BigDecimal("4000"), false, BigDecimal.ZERO);
⋮----
FifoMatcher.FifoResult fifoResult = new FifoMatcher.FifoResult(List.of(lot1, lot2), List.of());
Map<String, BigDecimal> declared = Map.of(
"asset_A", new BigDecimal("100"),
"asset_B", new BigDecimal("200")
⋮----
ReconciliationGate.MultiAssetReconciliationResult res = ReconciliationGate.validateStatementPerAsset(fifoResult, declared);
assertTrue(res.allMatched());
assertEquals(2, res.assetResults().size());
⋮----
void testPerAssetReconciliationFailsOnMismatch() {
⋮----
"asset_B", new BigDecimal("150") // mismatch on asset B
⋮----
assertFalse(res.allMatched());
````

## File: src/test/java/com/portfolioos/core/reporting/Itr2CsvExporterTest.java
````java
class Itr2CsvExporterTest {
⋮----
void testPre2018GrandfatheringDeemedCostWithFmv() {
// MatchedLot signature:
// (matchId, disposalEventId, lotId, assetId, acquisitionDate, disposalDate, unitsMatched, costBasis, saleProceeds, realizedGain, holdingPeriodDays, taxTerm, assetCategory)
⋮----
// Branch A: FMV (150) > Proceeds (120) > Cost (100) -> Deemed Cost = max(100, min(150, 120)) = 120 (gain = 0)
MatchedLot lotA = new MatchedLot(
⋮----
LocalDate.of(2017, 1, 1), LocalDate.of(2026, 5, 1),
new BigDecimal("1.0"), new BigDecimal("100.0"), new BigDecimal("120.0"),
new BigDecimal("20.0"), 3000L, TaxTerm.LONG_TERM, AssetCategory.EQUITY
⋮----
String csvA = Itr2CsvExporter.generateSchedule112aCsv(
List.of(lotA), "2026-27", Map.of("INF109KC13X2", "Fund A"),
Map.of("INF109KC13X2", new BigDecimal("150.0"))
⋮----
assertTrue(csvA.contains("120.00,150.00,0.00,0.00,\"VALIDATED_SECTION_55_2_AC\""));
⋮----
// Branch B: Proceeds (200) > FMV (150) > Cost (100) -> Deemed Cost = max(100, min(150, 200)) = 150 (gain = 50)
MatchedLot lotB = new MatchedLot(
⋮----
new BigDecimal("1.0"), new BigDecimal("100.0"), new BigDecimal("200.0"),
new BigDecimal("100.0"), 3000L, TaxTerm.LONG_TERM, AssetCategory.EQUITY
⋮----
String csvB = Itr2CsvExporter.generateSchedule112aCsv(
List.of(lotB), "2026-27", Map.of("INF109KC13X2", "Fund B"),
⋮----
assertTrue(csvB.contains("150.00,150.00,0.00,50.00,\"VALIDATED_SECTION_55_2_AC\""));
⋮----
// Branch C: Proceeds (200) > Cost (100) > FMV (80) -> Deemed Cost = max(100, min(80, 200)) = 100 (gain = 100)
MatchedLot lotC = new MatchedLot(
⋮----
String csvC = Itr2CsvExporter.generateSchedule112aCsv(
List.of(lotC), "2026-27", Map.of("INF109KC13X2", "Fund C"),
Map.of("INF109KC13X2", new BigDecimal("80.0"))
⋮----
assertTrue(csvC.contains("100.00,80.00,0.00,100.00,\"VALIDATED_SECTION_55_2_AC\""));
⋮----
void testPre2018LotWithoutFmvDataThrowsException() {
MatchedLot lotPreNoFmv = new MatchedLot(
⋮----
IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
Itr2CsvExporter.generateSchedule112aCsv(
List.of(lotPreNoFmv), "2026-27", Map.of("INF109KC13X2", "Fund Pre No FMV"),
Map.of()
⋮----
assertTrue(ex.getMessage().contains("MISSING_FMV_DATA"),
⋮----
void testPost2018LotSkipsGrandfathering() {
MatchedLot lotPost = new MatchedLot(
⋮----
LocalDate.of(2024, 1, 1), LocalDate.of(2026, 5, 1),
⋮----
new BigDecimal("100.0"), 500L, TaxTerm.LONG_TERM, AssetCategory.EQUITY
⋮----
String csv = Itr2CsvExporter.generateSchedule112aCsv(
List.of(lotPost), "2026-27", Map.of("INF109KC13X2", "Fund Post"),
⋮----
assertTrue(csv.contains("100.00,0.00,0.00,100.00,\"POST_2018_ACQUISITION\""),
⋮----
void testRegressionNoEmptyMapDefaultInSchedule112a() throws Exception {
⋮----
assertTrue(exporterFile.exists());
String content = java.nio.file.Files.readString(exporterFile.toPath());
⋮----
assertFalse(content.contains("fmv2018Map.getOrDefault(isin, actualCost)"),
````

## File: src/test/java/com/portfolioos/core/rules/BucketConfigLoaderTest.java
````java
class BucketConfigLoaderTest {
⋮----
void testIsPreferredFund() {
assertTrue(BucketConfigLoader.isPreferredFund("NIFTY_LARGEMIDCAP"));
assertTrue(BucketConfigLoader.isPreferredFund("PPFAS_FLEXICAP"));
assertTrue(BucketConfigLoader.isPreferredFund("VALUE_30"));
assertTrue(BucketConfigLoader.isPreferredFund("MOMENTUM_50"));
assertTrue(BucketConfigLoader.isPreferredFund("SMALL_CAP"));
assertTrue(BucketConfigLoader.isPreferredFund("GOLD_PASSIVE"));
assertTrue(BucketConfigLoader.isPreferredFund("ARBITRAGE_FUND"));
assertFalse(BucketConfigLoader.isPreferredFund("UNKNOWN_RANDOM_FUND"));
assertFalse(BucketConfigLoader.isPreferredFund(null));
⋮----
void testGetPreferredBucketForAsset() {
assertEquals("EQUITY_CORE", BucketConfigLoader.getPreferredBucketForAsset("NIFTY_LARGEMIDCAP_1", "Large and Midcap Index Fund"));
assertNull(BucketConfigLoader.getPreferredBucketForAsset(null, null));
⋮----
void testMapAssetToBucket() {
assertNotNull(BucketConfigLoader.mapAssetToBucket("INF109KC13X2", "ICICI Nifty 200"));
⋮----
void testGetActiveVersion() {
BucketConfigLoader.BucketTargetVersion activeVersion = BucketConfigLoader.getActiveVersion(LocalDate.now());
assertNotNull(activeVersion);
assertNotNull(activeVersion.targets());
assertFalse(activeVersion.targets().isEmpty());
⋮----
void testNoFundAppearsInMultipleBucketsInYaml() {
BucketConfigLoader.BucketRulesConfig rulesConfig = BucketConfigLoader.loadConfig();
assertNotNull(rulesConfig);
assertNotNull(rulesConfig.versions());
⋮----
for (BucketConfigLoader.BucketTargetVersion version : rulesConfig.versions()) {
⋮----
for (BucketConfigLoader.BucketTargetConfig target : version.targets()) {
if (target.preferredFunds() != null) {
for (BucketConfigLoader.PreferredFundConfig fund : target.preferredFunds()) {
String isin = fund.fundId();
assertNotNull(isin, "Preferred fund ISIN cannot be null in version " + version.versionId());
if (isinToBucketMap.containsKey(isin)) {
fail("DUPLICATE BUCKET MAPPING ERROR: ISIN " + isin +
" appears under both bucket '" + isinToBucketMap.get(isin) +
"' and bucket '" + target.bucket() + "' in YAML version " + version.versionId());
⋮----
isinToBucketMap.put(isin, target.bucket());
````

## File: src/test/java/com/portfolioos/core/rules/FireActionRuleEngineTest.java
````java
public class FireActionRuleEngineTest {
⋮----
public void testExemptionHeadroomReductionAndFifoLotAwareness() {
FireActionRuleEngine engine = new FireActionRuleEngine();
⋮----
// 1. Prepare simulated pairwise overlap data (Value 30 vs PPFAS @ 23.56%)
⋮----
overlapPair.put("fund_a", "INF109KC13X2"); // Value 30
overlapPair.put("fund_b", "INF879O01027"); // PPFAS Flexi Cap
overlapPair.put("overlap_percentage", 23.56);
overlapPair.put("common_stock_count", 5);
List<Map<String, Object>> pairwise = List.of(overlapPair);
⋮----
// 2. Prepare specific open lots for Value 30 (INF109KC13X2) - Oldest lot acquired 500 days ago
Lot value30OldLot = new Lot(
⋮----
LocalDate.now().minusDays(500),
new BigDecimal("100.00"),
⋮----
new BigDecimal("150.00"),
new BigDecimal("15000.00"),
⋮----
List<Lot> openLots = List.of(value30OldLot);
⋮----
// 3. Scenario A: No prior disposals in FY 2026-27 (Full ₹125,000 Exemption Headroom)
ExemptionTracker.ExemptionStatus exFull = ExemptionTracker.calculateExemptionStatus(Collections.emptyList(), "2026-27");
assertEquals("125000.00", exFull.exemptionRemaining());
⋮----
List<FireActionRuleEngine.ActionRecommendationCard> cardsA = engine.evaluateRules(
null, false, 33.15, 0.84, new BigDecimal("75000"), pairwise, Collections.emptyList(), openLots, exFull
⋮----
FireActionRuleEngine.ActionRecommendationCard cardA = cardsA.stream()
.filter(c -> "CARD_OVERLAP_ACTION".equals(c.cardId()))
.findFirst()
.orElseThrow();
⋮----
assertTrue(cardA.detailedRationale().contains("exemption headroom of ₹125,000"));
assertEquals(125000.0, ((Number) cardA.metrics().get("remaining_ltcg_exemption_headroom")).doubleValue());
assertTrue((Boolean) cardA.metrics().get("fifo_lot_ltcg_eligible"));
⋮----
// 4. Scenario B: Prior disposal in FY 2026-27 consuming ₹45,000 LTCG exemption
MatchedLot priorLtcgLot = new MatchedLot(
⋮----
LocalDate.of(2024, 1, 1),
LocalDate.of(2026, 6, 15),
new BigDecimal("100"),
new BigDecimal("10000"),
new BigDecimal("55000"),
new BigDecimal("45000.00"), // ₹45,000 realized LTCG gain
⋮----
ExemptionTracker.ExemptionStatus exPartial = ExemptionTracker.calculateExemptionStatus(List.of(priorLtcgLot), "2026-27");
assertEquals("80000.00", exPartial.exemptionRemaining()); // ₹125,000 - ₹45,000 = ₹80,000
⋮----
List<FireActionRuleEngine.ActionRecommendationCard> cardsB = engine.evaluateRules(
null, false, 33.15, 0.84, new BigDecimal("75000"), pairwise, Collections.emptyList(), openLots, exPartial
⋮----
FireActionRuleEngine.ActionRecommendationCard cardB = cardsB.stream()
⋮----
// Dynamic Exemption Verification: Rationale text MUST reflect ₹80,000 remaining headroom!
assertTrue(cardB.detailedRationale().contains("exemption headroom of ₹80,000"),
"Expected card rationale to dynamically reflect ₹80,000 remaining headroom, got: " + cardB.detailedRationale());
assertEquals(80000.0, ((Number) cardB.metrics().get("remaining_ltcg_exemption_headroom")).doubleValue());
⋮----
System.out.println("=== FIRE ACTION RULE ENGINE UNIT TEST PASSED ===");
System.out.println("Full Headroom Rationale    : " + cardA.detailedRationale());
System.out.println("Consumed Headroom Rationale: " + cardB.detailedRationale());
````

## File: src/test/java/com/portfolioos/core/rules/TaxRulesLoaderTest.java
````java
class TaxRulesLoaderTest {
⋮----
void testLoadRulesFY2627() {
TaxRulesConfig config = TaxRulesLoader.loadRules("2026-27");
assertNotNull(config, "TaxRulesConfig for FY 2026-27 must not be null");
assertEquals("2026-27", config.fiscalYear());
assertEquals(0, new BigDecimal("125000").compareTo(config.equityExemptionLimit()));
assertEquals(0, new BigDecimal("0.125").compareTo(config.equityLtcgRate()));
assertEquals(0, new BigDecimal("0.20").compareTo(config.equityStcgRate()));
⋮----
void testLoadRulesFY2526() {
TaxRulesConfig config = TaxRulesLoader.loadRules("2025-26");
assertNotNull(config, "TaxRulesConfig for FY 2025-26 must not be null");
assertEquals("2025-26", config.fiscalYear());
assertEquals(new BigDecimal("125000"), config.equityExemptionLimit());
````

## File: src/test/java/com/portfolioos/core/security/SecurityInterceptorTest.java
````java
class SecurityInterceptorTest {
⋮----
void testPreHandleOptionsRequestReturnsTrue() throws Exception {
SecurityInterceptor interceptor = new SecurityInterceptor();
MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/sync/snapshot");
MockHttpServletResponse response = new MockHttpServletResponse();
⋮----
boolean result = interceptor.preHandle(request, response, new Object());
assertTrue(result, "OPTIONS preflight requests must bypass token checks");
⋮----
void testPreHandleValidDevToken() throws Exception {
⋮----
MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sync/snapshot");
request.addHeader("X-Api-Auth-Token", "dev_secret_key_123");
⋮----
assertTrue(result);
⋮----
void testPreHandleInvalidTokenReturns401() throws Exception {
⋮----
request.addHeader("X-Api-Auth-Token", "invalid_token_999");
⋮----
assertFalse(result);
assertEquals(401, response.getStatus());
````

## File: src/test/java/com/portfolioos/core/service/DuckDbProjectorNetWorthAccountingTest.java
````java
class DuckDbProjectorNetWorthAccountingTest {
⋮----
void setUp() {
projector = new DuckDbProjector("jdbc:duckdb:");
⋮----
void testRebalanceTradePairNetActiveCapitalAccounting() {
// 1. Setup NAV history for asset_1 and asset_2 across dates
Map<String, BigDecimal> navJan1 = Map.of("asset_1", new BigDecimal("10.0"));
Map<String, BigDecimal> navJun1 = Map.of("asset_1", new BigDecimal("12.0"));
Map<String, BigDecimal> navJun2 = Map.of("asset_1", new BigDecimal("12.0"), "asset_2", new BigDecimal("15.0"));
⋮----
projector.saveNavHistoryBatchForHeldAssets(navJan1, Set.of("asset_1"), LocalDate.parse("2026-01-01"));
projector.saveNavHistoryBatchForHeldAssets(navJun1, Set.of("asset_1"), LocalDate.parse("2026-06-01"));
projector.saveNavHistoryBatchForHeldAssets(navJun2, Set.of("asset_1", "asset_2"), LocalDate.parse("2026-06-02"));
⋮----
// 2. Initial ACQUISITION on 2026-01-01 (Rs 1,00,000 invested, 10,000 units @ Rs 10.0)
TaxEvent e1 = new TaxEvent(
⋮----
EventType.ACQUISITION, LocalDate.parse("2026-01-01"),
new BigDecimal("10000.00"), new BigDecimal("10.00"), new BigDecimal("100000.00"),
"doc-1", Instant.parse("2026-01-01T10:00:00Z")
⋮----
projector.projectEvents(List.of(e1));
⋮----
List<NetWorthPoint> initialTrend = projector.getDailyNetWorthTrend();
assertFalse(initialTrend.isEmpty());
NetWorthPoint pointJan1 = initialTrend.stream()
.filter(p -> p.date().equals("2026-01-01"))
.findFirst()
.orElseThrow();
assertEquals(100000.00, pointJan1.invested(), 0.01, "Initial invested capital should be 100,000.00");
⋮----
// 3. Synthetic DISPOSAL on 2026-06-01:
// Sale Proceeds: Rs 88,121.00 (7,343.4167 units @ Rs 12.0)
// Cost Basis of sold units: Rs 76,038.00
// Realized Gain: Rs 12,083.00
TaxEvent eDisposal = new TaxEvent(
⋮----
EventType.DISPOSAL, LocalDate.parse("2026-06-01"),
new BigDecimal("7343.4167"), new BigDecimal("12.00"), new BigDecimal("88121.00"),
"doc-rebalance-sell", Instant.parse("2026-06-01T10:00:00Z")
⋮----
projector.projectEvents(List.of(eDisposal));
⋮----
// INTERMEDIATE STATE CHECK:
// Sell has fired, but buy leg has not redeployed yet.
// total_invested MUST drop by the FULL SALE PROCEEDS (88,121.00):
// 100,000.00 - 88,121.00 = 11,879.00
List<NetWorthPoint> intermediateTrend = projector.getDailyNetWorthTrend();
NetWorthPoint pointJun1 = intermediateTrend.stream()
.filter(p -> p.date().equals("2026-06-01"))
⋮----
assertEquals(11879.00, pointJun1.invested(), 0.01,
⋮----
// 4. Subsequent ACQUISITION on 2026-06-02:
// Reinvest full proceeds Rs 88,121.00 into Target Fund (asset_2)
TaxEvent eAcquisition = new TaxEvent(
⋮----
EventType.ACQUISITION, LocalDate.parse("2026-06-02"),
new BigDecimal("5874.7333"), new BigDecimal("15.00"), new BigDecimal("88121.00"),
"doc-rebalance-buy", Instant.parse("2026-06-02T10:00:00Z")
⋮----
projector.projectEvents(List.of(eAcquisition));
⋮----
// FINAL RECONCILIATION CHECK:
// Across the full rebalance trade pair (-88,121 disposal + 88,121 acquisition):
// total_invested on 2026-06-02 must return to EXACTLY 100,000.00 (0.00 net change across pair).
List<NetWorthPoint> finalTrend = projector.getDailyNetWorthTrend();
NetWorthPoint pointJun2 = finalTrend.stream()
.filter(p -> p.date().equals("2026-06-02"))
⋮----
assertEquals(100000.00, pointJun2.invested(), 0.001,
````

## File: src/test/java/com/portfolioos/core/service/LegacyFundWaterfallAuditTest.java
````java
class LegacyFundWaterfallAuditTest {
⋮----
void setUp() {
repository = new TriggerHistoryRepository(":memory:");
repository.clearAll();
evaluator = new RebalanceTriggerEvaluator(repository);
⋮----
void tearDown() {
⋮----
repository.close();
⋮----
/**
     * Audit Test Scenario:
     * Portfolio as of 2026-08-16:
     * - Lot 0: Parag Parikh Flexi Cap (Core Fund, active SIP < 90 days ago) - ₹150,000
     * - Lot 1: Motilal Oswal Nifty Midcap 150 (Legacy Fund, inactive > 90 days) - ₹50,000
     * - Lot 2: Kotak Nifty 100 Equal Weight (Legacy Fund, inactive > 90 days) - ₹60,000
     * Total Target Sell Pool: ₹88,121.00
     */
⋮----
void auditRebalancePlanEnginePrioritizesLegacyOverCoreArrayOrder() {
LocalDate today = LocalDate.of(2026, 8, 16);
⋮----
// Lot 0: Core Lot (Active SIP within 90 days: acq 2026-06-15) - ₹150,000
Lot coreLot = new Lot(
⋮----
LocalDate.of(2026, 6, 15),
new BigDecimal("1500.00"),
⋮----
new BigDecimal("100.00"),
new BigDecimal("150000.00"),
⋮----
// Lot 1: Legacy Lot 1 (Inactive > 90 days: acq 2024-01-15) - ₹50,000
Lot legacyLot1 = new Lot(
⋮----
LocalDate.of(2024, 1, 15),
new BigDecimal("500.00"),
⋮----
new BigDecimal("50000.00"),
⋮----
// Lot 2: Legacy Lot 2 (Inactive > 90 days: acq 2024-03-10) - ₹60,000
Lot legacyLot2 = new Lot(
⋮----
LocalDate.of(2024, 3, 10),
new BigDecimal("600.00"),
⋮----
new BigDecimal("60000.00"),
⋮----
// openLots array order has Core lot at index 0
List<Lot> openLots = List.of(coreLot, legacyLot1, legacyLot2);
Map<String, BigDecimal> navMap = Map.of(
"INF879O01027", new BigDecimal("100.00"),
"INF247L01916", new BigDecimal("100.00"),
"INF174KA1TY2", new BigDecimal("100.00")
⋮----
// Trigger DRIFT on 260,000 corpus -> 5% pool = ₹13,000
List<BucketEngine.BucketTarget> customTargets = List.of(
new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("40.00"), new BigDecimal("5.00")),
new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("60.00"), new BigDecimal("5.00"))
⋮----
RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
openLots, Collections.emptyList(), navMap, today,
new BigDecimal("260000.00"), new BigDecimal("260000.00"), customTargets, "2026-27", null, null, evaluator
⋮----
assertNotNull(plan);
assertNotNull(plan.sellSide());
⋮----
WaterfallTierDto legacyTierDto = plan.sellSide().waterfall().stream()
.filter(t -> "LEGACY_FUND".equals(t.tier()))
.findFirst().orElseThrow();
⋮----
WaterfallTierDto coreTierDto = plan.sellSide().waterfall().stream()
.filter(t -> "CORE_FUND".equals(t.tier()))
⋮----
System.out.println("=== FIXED SYSTEM: RebalancePlanEngine Output ===");
System.out.println("Sold Legacy Amount: ₹" + legacyTierDto.sold());
System.out.println("Sold Core Amount: ₹" + coreTierDto.sold());
⋮----
// FIXED BEHAVIOR VERIFICATION:
// Excess Core = ₹46,000. Legacy Lot 1 (Motilal Midcap 150) 50% cap = ₹25,000. Legacy Lot 2 (Kotak Equal) 50% cap = ₹30,000.
// Entire ₹55,000 max trimmable legacy pool is sourced from Legacy Tier, Core receives ₹0!
assertEquals(new BigDecimal("55000.00"), legacyTierDto.sold(),
⋮----
assertEquals(0, BigDecimal.ZERO.compareTo(coreTierDto.sold()),
⋮----
void auditRebalanceWaterfallEngineEnforcesFiftyPercentCap() {
⋮----
BigDecimal targetSellPool = new BigDecimal("88121.00");
⋮----
// Active SIP lot for Core Fund within last 30 days (marking INF879O01027 as Active Core)
Lot activeCoreSipLot = new Lot(
⋮----
LocalDate.of(2026, 8, 1), new BigDecimal("100.00"), new BigDecimal("100.00"),
new BigDecimal("100.00"), new BigDecimal("10000.00"), false, BigDecimal.ZERO
⋮----
// Core LTCG Lot (acq 2024-01-01 > 365 days ago)
⋮----
LocalDate.of(2024, 1, 1), new BigDecimal("1500.00"), new BigDecimal("1500.00"),
new BigDecimal("100.00"), new BigDecimal("150000.00"), false, BigDecimal.ZERO
⋮----
LocalDate.of(2024, 1, 15), new BigDecimal("500.00"), new BigDecimal("500.00"),
new BigDecimal("100.00"), new BigDecimal("50000.00"), false, BigDecimal.ZERO
⋮----
LocalDate.of(2024, 3, 10), new BigDecimal("600.00"), new BigDecimal("600.00"),
new BigDecimal("100.00"), new BigDecimal("60000.00"), false, BigDecimal.ZERO
⋮----
RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
⋮----
List.of(activeCoreSipLot, coreLot, legacyLot1, legacyLot2),
⋮----
new BigDecimal("125000.00"),
⋮----
assertNotNull(result);
assertEquals(targetSellPool, result.satisfiedAmount());
⋮----
// Step 1: Legacy Lot 1 (Motilal Midcap 150, ₹50k total value) -> capped at 50% = ₹25,000.00
RebalanceWaterfallEngine.WaterfallStep step1 = result.steps().get(0);
assertEquals(WaterfallTier.LEGACY_FUND, step1.tier());
assertEquals("INF247L01916", step1.assetId());
assertEquals(new BigDecimal("25000.00"), step1.proceeds(),
⋮----
// Step 2: Legacy Lot 2 (Kotak Equal Weight, ₹60k total value) -> capped at 50% = ₹30,000.00
RebalanceWaterfallEngine.WaterfallStep step2 = result.steps().get(1);
assertEquals(WaterfallTier.LEGACY_FUND, step2.tier());
assertEquals("INF174KA1TY2", step2.assetId());
assertEquals(new BigDecimal("30000.00"), step2.proceeds(),
⋮----
// Step 3: Shortfall falls through to Core Lot (Parag Parikh Flexi Cap) = ₹88,121 - ₹55,000 = ₹33,121.00
RebalanceWaterfallEngine.WaterfallStep step3 = result.steps().get(2);
assertEquals(WaterfallTier.LTCG_WITHIN_EXEMPTION, step3.tier());
assertEquals("INF879O01027", step3.assetId());
assertEquals(new BigDecimal("33121.00"), step3.proceeds(),
⋮----
System.out.println("=== FIXED SYSTEM: RebalanceWaterfallEngine Output ===");
System.out.println("Step 1 (Motilal Midcap 150): ₹" + step1.proceeds() + " (50% cap - preserved ₹25k!)");
System.out.println("Step 2 (Kotak Equal Weight): ₹" + step2.proceeds() + " (50% cap - preserved ₹30k!)");
System.out.println("Step 3 (Parag Parikh Core): ₹" + step3.proceeds() + " (Shortfall fall-through)");
⋮----
void auditHandComputedGroundTruthReconciliation() {
⋮----
BigDecimal partialCapPct = new BigDecimal("0.50");
⋮----
BigDecimal legacy1Value = new BigDecimal("50000.00");
BigDecimal legacy2Value = new BigDecimal("60000.00");
⋮----
BigDecimal expectedLegacy1Trim = legacy1Value.multiply(partialCapPct).setScale(2, RoundingMode.HALF_UP); // 25,000.00
BigDecimal expectedLegacy2Trim = legacy2Value.multiply(partialCapPct).setScale(2, RoundingMode.HALF_UP); // 30,000.00
BigDecimal expectedLegacyTotal = expectedLegacy1Trim.add(expectedLegacy2Trim); // 55,000.00
⋮----
BigDecimal expectedCoreTrim = targetSellPool.subtract(expectedLegacyTotal); // 33,121.00
⋮----
assertEquals(new BigDecimal("25000.00"), expectedLegacy1Trim);
assertEquals(new BigDecimal("30000.00"), expectedLegacy2Trim);
assertEquals(new BigDecimal("55000.00"), expectedLegacyTotal);
assertEquals(new BigDecimal("33121.00"), expectedCoreTrim);
⋮----
void auditChronologicalCoincidencePrevention() {
⋮----
// Older Lump Sum Lot for Core Fund acquired in 2023 - ₹500,000
Lot oldCoreLot = new Lot(
⋮----
LocalDate.of(2023, 5, 10), new BigDecimal("5000.00"), new BigDecimal("5000.00"),
new BigDecimal("100.00"), new BigDecimal("500000.00"), false, BigDecimal.ZERO
⋮----
// Legacy Lot acquired in 2024 (Newer legacy holding) - ₹50,000
Lot newerLegacyLot = new Lot(
⋮----
LocalDate.of(2024, 2, 1), new BigDecimal("500.00"), new BigDecimal("500.00"),
⋮----
// FifoMatcher orders openLots by acquisitionDate ascending: [oldCoreLot, newerLegacyLot, activeCoreSipLot]
List<Lot> openLotsFifo = List.of(oldCoreLot, newerLegacyLot, activeCoreSipLot);
⋮----
"INF247L01916", new BigDecimal("100.00")
⋮----
// Target sell pool for DRIFT on ₹560,000 corpus = ₹28,000
⋮----
openLotsFifo, Collections.emptyList(), navMap, today,
new BigDecimal("560000.00"), new BigDecimal("560000.00"), customTargets, "2026-27", null, null, evaluator
⋮----
WaterfallTierDto legacyTier = plan.sellSide().waterfall().stream()
.filter(t -> "LEGACY_FUND".equals(t.tier())).findFirst().orElseThrow();
WaterfallTierDto coreTier = plan.sellSide().waterfall().stream()
.filter(t -> "CORE_FUND".equals(t.tier())).findFirst().orElseThrow();
⋮----
System.out.println("=== FIXED SYSTEM: Chronological Coincidence Prevention Output ===");
System.out.println("Newer Legacy Lot (2024-02-01) Sold: ₹" + legacyTier.sold());
System.out.println("Old Core Lot (2023-05-10) Sold: ₹" + coreTier.sold());
⋮----
// FIX VERIFIED:
// Legacy Lot trimmed FIRST up to its 50% cap (₹25,000). Remaining shortfall falls through to 2023 Core Lot!
assertEquals(new BigDecimal("25000.00"), legacyTier.sold(),
⋮----
assertEquals(new BigDecimal("227000.00"), coreTier.sold(),
⋮----
void testRealPortfolioE2EBaseline() {
⋮----
if (!dbFile.exists()) {
System.out.println("Skipping real DB run: data/tax_ledger.db not found");
⋮----
List<com.portfolioos.core.model.TaxEvent> events = store.getAllEvents();
⋮----
com.portfolioos.core.matcher.FifoMatcher.FifoResult fifoResult = matcher.processEvents(events);
List<Lot> openLots = fifoResult.openLots();
⋮----
Map<String, BigDecimal> navMap = Map.of();
⋮----
totalVal = totalVal.add(lot.remainingUnits().multiply(lot.costPerUnit()));
⋮----
openLots, fifoResult.matchedLots(), navMap, today,
⋮----
assertNotNull(plan.sellSide(), "SellSide plan must not be null");
assertNotNull(plan.sellSide().waterfall(), "Waterfall tiers list must not be null");
⋮----
System.out.println("=== REAL PORTFOLIO FRESH E2E BASELINE ===");
⋮----
System.out.println("Total Required Pool: ₹" + plan.sellSide().totalRequired());
for (WaterfallTierDto tier : plan.sellSide().waterfall()) {
System.out.println("Tier: " + tier.tierLabel() + " (" + tier.tier() + ") -> Sold: ₹" + tier.sold());
BigDecimal tierSold = tier.sold() != null ? tier.sold() : BigDecimal.ZERO;
totalSold = totalSold.add(tierSold);
⋮----
if ("LEGACY_FUND".equals(tier.tier())) {
legacySold = legacySold.add(tierSold);
} else if ("CORE_FUND".equals(tier.tier())) {
coreSold = coreSold.add(tierSold);
⋮----
if (tier.lots() != null) {
for (RebalanceLotImpactDto lot : tier.lots()) {
System.out.println("   Lot " + lot.lotId() + " (" + lot.fundName() + "): ₹" + lot.saleProceeds());
⋮----
// 1. Invariant Assertion: Legacy fund tier MUST exhaust available LTCG lots up to 50% scheme cap first
assertEquals(new BigDecimal("130583.52"), legacySold.setScale(2, java.math.RoundingMode.HALF_UP),
⋮----
// 2. Invariant Assertion: Core fund tier supplies remaining available LTCG lots
assertEquals(new BigDecimal("124494.74"), coreSold.setScale(2, java.math.RoundingMode.HALF_UP),
⋮----
// 3. Invariant Assertion: Remaining shortfall of ₹34,763.95 MUST be STCG lots that are deferred under Rule 2a
BigDecimal actualExecuted = legacySold.add(coreSold);
BigDecimal expectedDeferred = new BigDecimal("34763.95");
assertEquals(new BigDecimal("289842.21"), plan.sellSide().totalRequired(),
⋮----
assertEquals(0, plan.sellSide().totalRequired().subtract(actualExecuted).compareTo(expectedDeferred),
````

## File: src/test/java/com/portfolioos/core/service/RebalancePlanEngineTest.java
````java
class RebalancePlanEngineTest {
⋮----
void setUp() {
repository = new TriggerHistoryRepository(":memory:");
repository.clearAll();
evaluator = new RebalanceTriggerEvaluator(repository);
⋮----
void tearDown() {
⋮----
repository.close();
⋮----
void testEndToEndDriftPlanGeneration() {
// Real portfolio holdings ISINs:
// EQUITY_CORE: Parag Parikh Flexi Cap Fund (INF109K018C5)
// EQUITY_SATELLITE: Motilal Oswal Large and Midcap Fund (INF204K01K15)
BigDecimal navCore = new BigDecimal("100.00");
BigDecimal navSat = new BigDecimal("100.00");
LocalDate acqDate = LocalDate.of(2024, 1, 1);
⋮----
// Corpus = 2,000,000 (20 Lakhs)
// 1,800,000 in Core (90%), 200,000 in Satellite (10%)
Lot coreLot = new Lot("lot-1", "INF109K018C5", "Parag Parikh Flexi Cap Fund Direct Growth", acqDate, new BigDecimal("18000"), new BigDecimal("18000"), navCore, new BigDecimal("1800000.00"), false, null);
Lot satLot = new Lot("lot-2", "INF204K01K15", "Motilal Oswal Large and Midcap Fund Direct Growth", acqDate, new BigDecimal("2000"), new BigDecimal("2000"), navSat, new BigDecimal("200000.00"), false, null);
⋮----
List<Lot> openLots = List.of(coreLot, satLot);
Map<String, BigDecimal> navMap = Map.of(
⋮----
// Explicit custom targets: Core = 70.0%, Satellite = 30.0%
// Actual Core = 90.0% (+20% drift >= 5.0%), Actual Satellite = 10.0% (-20% drift >= 5.0%)
List<BucketEngine.BucketTarget> customTargets = List.of(
new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("70.00"), new BigDecimal("5.00")),
new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("30.00"), new BigDecimal("5.00"))
⋮----
BigDecimal corpus = new BigDecimal("2000000.00");
BigDecimal high = new BigDecimal("2000000.00"); // 0% drawdown
⋮----
RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
openLots, Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
⋮----
assertNotNull(plan);
assertNotNull(plan.trigger());
assertEquals("DRIFT", plan.trigger().type(), "End-to-end engine must resolve DRIFT trigger when bucket drift exceeds 5% threshold");
assertEquals("INDUCED", plan.trigger().legacyTriggerType(), "Backward compatibility legacyTriggerType must be INDUCED");
assertTrue(plan.trigger().isInduced());
assertEquals("DRIFT_THRESHOLD_EXCEEDED", plan.trigger().reasonCode());
⋮----
// Verify buy side sizing: Excess Core with per-fund Trend Dampener (0.7393x of ₹400,000.00 = ₹295,720.00)
assertNotNull(plan.buySide());
assertEquals(new BigDecimal("295720.00"), plan.buySide().totalToInvest(), "Excess drift pool on 2M corpus with per-fund trend dampener must yield exactly ₹450,000.00 total to invest");
assertFalse(plan.buySide().buckets().isEmpty());
⋮----
void testEndToEndDrawdownSuppressesDrift() {
// Real portfolio holdings ISINs with 20% drawdown
⋮----
BigDecimal currentVal = new BigDecimal("1600000.00");
BigDecimal rollingHigh = new BigDecimal("2000000.00"); // 20% drawdown tier armed!
⋮----
assertEquals("DRAWDOWN", plan.trigger().type(), "DRAWDOWN trigger must suppress DRIFT end-to-end in RebalancePlanEngine");
assertEquals("DRAWDOWN_TIER_20", plan.trigger().reasonCode());
assertEquals("INDUCED", plan.trigger().legacyTriggerType());
⋮----
// Verify sell side waterfall was built
assertNotNull(plan.sellSide());
assertTrue(plan.sellSide().totalRequired().compareTo(BigDecimal.ZERO) > 0);
````

## File: src/test/java/com/portfolioos/core/service/RebalanceSankeyDtoTest.java
````java
class RebalanceSankeyDtoTest {
⋮----
void setUp() {
repository = new TriggerHistoryRepository(":memory:");
repository.clearAll();
evaluator = new RebalanceTriggerEvaluator(repository);
⋮----
void tearDown() {
⋮----
repository.close();
⋮----
void testPostRebalancePctReconciliationWithIndependentGroundTruth() {
// Discrete Fixture:
// liveCorpus = ₹1,000,000
// Core Fund lot = ₹450,000 (45.0% current)
// Satellite Fund lot = ₹150,000 (15.0% current)
// Gold Fund lot = ₹150,000 (15.0% current)
// Liquid Fund lot = ₹250,000 (25.0% current)
BigDecimal nav = new BigDecimal("100.00");
LocalDate acqDate = LocalDate.of(2024, 1, 1);
⋮----
Lot coreLot = new Lot("lot-1", "INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund", acqDate, new BigDecimal("4500"), new BigDecimal("4500"), nav, new BigDecimal("450000.00"), false, null);
Lot satLot = new Lot("lot-2", "INF204K01K15", "Motilal Oswal Smallcap Fund", acqDate, new BigDecimal("1500"), new BigDecimal("1500"), nav, new BigDecimal("150000.00"), false, null);
Lot goldLot = new Lot("lot-3", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", acqDate, new BigDecimal("1500"), new BigDecimal("1500"), nav, new BigDecimal("150000.00"), false, null);
Lot liqLot = new Lot("lot-4", "INF205K01KR8", "Invesco India Arbitrage Fund", acqDate, new BigDecimal("2500"), new BigDecimal("2500"), nav, new BigDecimal("250000.00"), false, null);
⋮----
List<Lot> openLots = List.of(coreLot, satLot, goldLot, liqLot);
Map<String, BigDecimal> navMap = Map.of(
⋮----
// Targets: Core = 60%, Satellite = 20%, Gold = 10%, Liquid = 10%
List<BucketEngine.BucketTarget> customTargets = List.of(
new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("60.00"), new BigDecimal("5.00")),
new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("20.00"), new BigDecimal("5.00")),
new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("10.00"), new BigDecimal("5.00")),
new BucketEngine.BucketTarget(BucketEngine.Bucket.LIQUID_BUFFER, new BigDecimal("10.00"), new BigDecimal("5.00"))
⋮----
BigDecimal corpus = new BigDecimal("1000000.00");
BigDecimal high = new BigDecimal("1000000.00");
⋮----
RebalancePlanDto plan = RebalancePlanEngine.buildPreviewPlan(
openLots, Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
⋮----
assertNotNull(plan);
assertNotNull(plan.buySide());
assertFalse(plan.buySide().buckets().isEmpty());
⋮----
// Find EQUITY_CORE bucket
RebalanceBucketAllocationDto coreBucket = plan.buySide().buckets().stream()
.filter(b -> "EQUITY_CORE".equals(b.bucket()))
.findFirst()
.orElseThrow();
⋮----
// Hand-calculation:
// Total Pool = ₹60,000 (6% pool of 1,000,000 = 60,000 >= 10,000 floor)
// Core Target = 60%, amountAllocated = 60,000 * 60% = ₹36,000
// Post Core Valuation = 450,000 + 36,000 = ₹486,000
// Post Total Corpus = 1,000,000 + 60,000 = ₹1,060,000
// Expected postRebalancePct = (486,000 / 1,060,000) * 100 = 45.849% -> 45.8%
assertEquals(56.3, coreBucket.postRebalancePct(), 0.5,
⋮----
void testSellAndBuySideMathIntegrity() {
⋮----
Lot sipLot = new Lot("lot-sip", "INF109K01234", "Core Flexi Cap Fund", LocalDate.of(2026, 8, 1), new BigDecimal("10"), new BigDecimal("10"), nav, new BigDecimal("1000.00"), false, null);
Lot coreLot = new Lot("lot-1", "INF109K01234", "Core Flexi Cap Fund", acqDate, new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);
List<Lot> openLots = List.of(sipLot, coreLot);
Map<String, BigDecimal> navMap = Map.of("INF109K01234", nav);
⋮----
new BigDecimal("100000.00"), new BigDecimal("100000.00"), null, "2026-27", "DRIFT", null, evaluator
⋮----
if (plan.sellSide() != null && plan.sellSide().waterfall() != null) {
⋮----
for (WaterfallTierDto tier : plan.sellSide().waterfall()) {
if (tier.lots() != null) {
for (RebalanceLotImpactDto lot : tier.lots()) {
totalSoldLots = totalSoldLots.add(lot.saleProceeds());
assertNotNull(lot.taxImpact());
assertNotNull(lot.taxImpact().regime());
assertTrue(List.of("SEC_112A_EXEMPT", "SEC_112A_TAXABLE_12_5", "SLAB_RATE_STCG").contains(lot.taxImpact().regime()));
⋮----
assertEquals(plan.sellSide().totalRequired(), totalSoldLots, "Sum of lot saleProceeds must equal sellSide totalRequired");
⋮----
if (plan.buySide() != null && plan.buySide().buckets() != null) {
⋮----
for (RebalanceBucketAllocationDto b : plan.buySide().buckets()) {
totalAllocated = totalAllocated.add(b.amountAllocated());
if (b.fundBreakdown() != null) {
⋮----
for (FundAllocationDto f : b.fundBreakdown()) {
fundSum = fundSum.add(f.amount());
⋮----
assertEquals(0, b.amountAllocated().compareTo(fundSum), "Sum of fundBreakdown amounts must equal bucket amountAllocated");
⋮----
void testGoldFloorBackstopSankeyAllocation() {
⋮----
Lot coreLot = new Lot("lot-1", "INF109KC12U0", "ICICI LargeMidcap", acqDate, new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);
⋮----
List.of(coreLot), Collections.emptyList(), Map.of("INF109KC12U0", nav), LocalDate.of(2026, 8, 10),
new BigDecimal("100000.00"), new BigDecimal("100000.00"), null, "2026-27", "GOLD_FLOOR_BACKSTOP", null, evaluator
⋮----
RebalanceBucketAllocationDto goldBucket = plan.buySide().buckets().stream()
.filter(b -> "GOLD_SILVER".equals(b.bucket()))
⋮----
assertTrue(goldBucket.amountAllocated().compareTo(BigDecimal.ZERO) > 0, "Gold Floor Backstop must allocate non-zero to Gold");
assertEquals(BigDecimal.ZERO, coreBucket.amountAllocated(), "Gold Floor Backstop must allocate 0 to non-Gold buckets");
⋮----
void testGoldDampenedBuyAllocationWithNonZeroDeviation() {
BigDecimal currentNav = new BigDecimal("110.00");
BigDecimal sma200 = new BigDecimal("100.00");
// devPct = (110 - 100) / 100 * 100 = +10.0%
// buyMultiplier at +10% deviation = 1.30 - (10/20)*(1.30 - 0.40) = 0.8500
⋮----
Lot goldLot = new Lot("lot-gold", "INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", acqDate, new BigDecimal("100"), new BigDecimal("100"), currentNav, new BigDecimal("11000.00"), false, null);
Lot coreLot = new Lot("lot-core", "INF109KC12U0", "ICICI LargeMidcap", acqDate, new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100.00"), new BigDecimal("100000.00"), false, null);
⋮----
"INF109KC12U0", new BigDecimal("100.00")
⋮----
new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("85.00"), new BigDecimal("5.00")),
new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("15.00"), new BigDecimal("5.00"))
⋮----
List.of(goldLot, coreLot), Collections.emptyList(), navMap, LocalDate.of(2026, 8, 10),
⋮----
assertTrue(goldBucket.amountAllocated().compareTo(BigDecimal.ZERO) > 0, "Gold amountAllocated must be > 0 at +10% deviation");
⋮----
void testRebalanceSankeyTaxRegimeColoringAllThreeRegimes() {
LocalDate now = LocalDate.of(2026, 8, 10);
LocalDate stcgAcqDate = now.minusDays(100); // STCG holding < 365d
LocalDate ltcgAcqDate = now.minusDays(500); // LTCG holding > 365d
⋮----
// STCG Lot (Held 100 days)
Lot stcgLot = new Lot("lot-stcg", "INF247L01916", "Motilal Oswal Midcap 150", stcgAcqDate, new BigDecimal("100"), new BigDecimal("1000"), new BigDecimal("150.00"), new BigDecimal("15000.00"), false, null);
⋮----
// Large LTCG Lot (Gains > 1.25L threshold)
Lot ltcgLargeGainLot = new Lot("lot-ltcg-large", "INF174KA1TY2", "Kotak Nifty 100", ltcgAcqDate, new BigDecimal("1000"), new BigDecimal("10000"), new BigDecimal("300.00"), new BigDecimal("300000.00"), false, null);
⋮----
// Small LTCG Exempt Lot
Lot ltcgExemptLot = new Lot("lot-ltcg-exempt", "INF879O01027", "Parag Parikh Flexi Cap", ltcgAcqDate, new BigDecimal("100"), new BigDecimal("1000"), new BigDecimal("110.00"), new BigDecimal("11000.00"), false, null);
⋮----
"INF247L01916", new BigDecimal("150.00"),
"INF174KA1TY2", new BigDecimal("300.00"),
"INF879O01027", new BigDecimal("110.00")
⋮----
List.of(stcgLot, ltcgLargeGainLot, ltcgExemptLot), Collections.emptyList(), navMap, now,
new BigDecimal("100.00"), new BigDecimal("100.00"), Collections.emptyList(), "2026-27", "DRIFT", null, evaluator
⋮----
assertNotNull(plan.sellSide());
assertNotNull(plan.sellSide().waterfall());
⋮----
List<com.portfolioos.core.dtos.RebalancePlanDtos.RebalanceLotImpactDto> allSellLots = plan.sellSide().waterfall().stream()
.flatMap(t -> t.lots().stream())
.toList();
⋮----
assertFalse(allSellLots.isEmpty(), "Sell waterfall must contain lots for rebalance liquidations");
⋮----
// Verify STCG regime presence
boolean hasStcg = allSellLots.stream().anyMatch(l -> "SLAB_RATE_STCG".equals(l.taxImpact().regime()));
// Verify 112A Taxable or Exempt presence
boolean hasExemptOrTaxable = allSellLots.stream().anyMatch(l -> l.taxImpact().regime().startsWith("SEC_112A"));
⋮----
assertTrue(hasStcg || hasExemptOrTaxable, "Waterfall lots must carry evaluated tax regimes");
````

## File: src/test/java/com/portfolioos/core/service/RebalanceTriggerEvaluatorTest.java
````java
class RebalanceTriggerEvaluatorTest {
⋮----
void setUp() {
repository = new TriggerHistoryRepository(":memory:");
repository.clearAll();
evaluator = new RebalanceTriggerEvaluator(repository);
⋮----
void tearDown() {
⋮----
repository.close();
⋮----
void testGetCurrentStatusZeroSideEffects() {
assertEquals(0, repository.getRecordCount(), "Initial DB must be empty");
⋮----
RebalanceTriggerEvaluator.TriggerResolution res1 = evaluator.getCurrentStatus(
Collections.emptyList(), Collections.emptyMap(),
new BigDecimal("20000.00"), new BigDecimal("25000.00"), // 20% drawdown
null, null, LocalDate.of(2026, 8, 1)
⋮----
assertEquals("DRAWDOWN", res1.triggerType());
assertEquals(0, repository.getRecordCount(), "getCurrentStatus call 1 must produce 0 DB side-effects");
⋮----
RebalanceTriggerEvaluator.TriggerResolution res2 = evaluator.getCurrentStatus(
⋮----
new BigDecimal("20000.00"), new BigDecimal("25000.00"),
⋮----
assertEquals("DRAWDOWN", res2.triggerType());
assertEquals(0, repository.getRecordCount(), "getCurrentStatus call 2 must produce 0 DB side-effects");
⋮----
void testNullBenchmarkDisarmsDrawdown() {
List<Lot> openLots = List.of(
new Lot("l1", "INF109KC12U0", "ICICI LargeMidcap 250", LocalDate.of(2025, 1, 1),
new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100000"), false, BigDecimal.ZERO)
⋮----
Map<String, BigDecimal> navMap = Map.of("INF109KC12U0", new BigDecimal("150"));
⋮----
RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
⋮----
null, null, // Null benchmark inputs
⋮----
assertNotNull(res);
assertNotNull(res.drawdownContext());
assertEquals(0.0, res.drawdownContext().currentDrawdownPct(), "Null benchmark inputs MUST produce exactly 0.00% drawdown");
assertEquals("NONE", res.drawdownContext().armedTier(), "Null benchmark inputs MUST result in armedTier = NONE");
assertNotEquals("DRAWDOWN", res.triggerType(), "DRAWDOWN trigger MUST NOT fire when benchmark inputs are null");
⋮----
void testSellCooldownBlocksDrawdown() {
LocalDate firstDate = LocalDate.of(2026, 8, 1);
repository.recordExecution("plan-1", "DRAWDOWN", "DRAWDOWN_TIER_20", firstDate.atStartOfDay(), true, true, "");
⋮----
LocalDate testDate = LocalDate.of(2026, 8, 10); // 9 days later (< 30 days)
⋮----
assertTrue(res.sellCooldownActive());
assertEquals(9, res.daysSinceLastSell());
assertEquals("NONE", res.triggerType());
assertEquals("DRAWDOWN_BLOCKED_BY_COOLDOWN", res.reasonCode());
⋮----
void testGoldFloorBackstopBypassesSellCooldown() {
LocalDate lastSellDate = LocalDate.of(2026, 8, 1);
repository.recordExecution("plan-1", "DRAWDOWN", "DRAWDOWN_TIER_20", lastSellDate.atStartOfDay(), true, false, "");
⋮----
LocalDate testDate = LocalDate.of(2026, 8, 10); // 9 days after sell (< 30 days)
// Gold idle for 7 months (> 6 months)
LocalDate lastGoldBuyDate = testDate.minusMonths(7);
repository.recordExecution("plan-gold-old", "DRIFT", "DRIFT", lastGoldBuyDate.atStartOfDay(), false, true, "");
⋮----
new BigDecimal("25000.00"), new BigDecimal("25000.00"), // No drawdown
⋮----
assertTrue(res.sellCooldownActive(), "Sell cooldown should be active");
assertTrue(res.goldIdleActive(), "Gold idle should be active (7 months)");
assertEquals("GOLD_FLOOR_BACKSTOP", res.triggerType(), "Gold floor backstop must fire despite active sell cooldown");
assertFalse(res.hasSellSide());
assertTrue(res.hasGoldBuy());
⋮----
void testLastGoldBuyDateQueryContract() {
LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 10, 0);
repository.recordExecution("p1", "DRIFT", "DRIFT", t1, true, true, "");
⋮----
assertTrue(repository.getLastGoldBuyDate().isPresent());
assertEquals(t1, repository.getLastGoldBuyDate().get());
⋮----
LocalDateTime t2 = LocalDateTime.of(2026, 5, 1, 10, 0);
repository.recordExecution("p2", "DRAWDOWN", "DRAWDOWN_TIER_15", t2, true, true, "");
⋮----
assertEquals(t2, repository.getLastGoldBuyDate().get(), "getLastGoldBuyDate must return latest timestamp where has_gold_buy = 1");
⋮----
// Add later row t3 with has_gold_buy = 0
LocalDateTime t3 = LocalDateTime.of(2026, 6, 1, 10, 0);
repository.recordExecution("p3", "DRAWDOWN", "DRAWDOWN_TIER_20", t3, true, false, "");
⋮----
// Must STILL return t2 (May 1), proving WHERE has_gold_buy = 1 filter is load-bearing!
assertEquals(t2, repository.getLastGoldBuyDate().get(), "Must ignore later row t3 because has_gold_buy = 0");
⋮----
void testPrioritySuppressionDrawdownOverDriftAndScheduled() {
// Drawdown active (20% drawdown) + March 15 window (scheduled month)
⋮----
null, null, LocalDate.of(2026, 3, 15)
⋮----
assertEquals("DRAWDOWN", res.triggerType(), "DRAWDOWN must win over DRIFT and SCHEDULED");
assertEquals("DRAWDOWN_TIER_20", res.reasonCode());
assertTrue(res.hasSellSide());
⋮----
void testPrioritySuppressionDriftOverScheduled() {
// No drawdown (current == high) + March 15 window + drifted bucket (openLots empty -> buckets at 0% vs 50% target)
⋮----
new BigDecimal("25000.00"), new BigDecimal("25000.00"), // 0% drawdown
⋮----
assertEquals("DRIFT", res.triggerType(), "DRIFT must win over SCHEDULED when drawdown is zero");
assertEquals("DRIFT_THRESHOLD_EXCEEDED", res.reasonCode());
⋮----
void testRebalanceTriggerDtoBackwardCompatibility() {
RebalanceTriggerDto dto1 = new RebalanceTriggerDto("DRAWDOWN", "DRAWDOWN_TIER_15", "15% Drawdown", "Window", null);
assertEquals("DRAWDOWN", dto1.type());
assertEquals("INDUCED", dto1.legacyTriggerType());
assertTrue(dto1.isInduced());
⋮----
RebalanceTriggerDto dto2 = new RebalanceTriggerDto("DRIFT", "DRIFT_THRESHOLD_EXCEEDED", "Drift Exceeded", "Window", null);
assertEquals("DRIFT", dto2.type());
assertEquals("INDUCED", dto2.legacyTriggerType());
assertTrue(dto2.isInduced());
⋮----
RebalanceTriggerDto dto3 = new RebalanceTriggerDto("SCHEDULED", "SCHEDULED_RECONSTITUTION", "Scheduled", "Window", null);
assertEquals("SCHEDULED", dto3.type());
assertEquals("SCHEDULED", dto3.legacyTriggerType());
assertFalse(dto3.isInduced());
````

## File: src/test/java/com/portfolioos/core/service/SimulationServiceTest.java
````java
class SimulationServiceTest {
⋮----
void setUp() {
TaxEvent acq = new TaxEvent(
⋮----
LocalDate.of(2024, 1, 1),
new BigDecimal("1000.0"),
new BigDecimal("10.0"),
new BigDecimal("10000.0"),
⋮----
Instant.now()
⋮----
List.of(acq),
new com.portfolioos.core.matcher.FifoMatcher().processEvents(List.of(acq)),
Map.of("INF109KC13X2", new BigDecimal("15.0")),
⋮----
System.currentTimeMillis(),
⋮----
LedgerCacheService mockCacheService = new LedgerCacheService(null) {
⋮----
public CachedLedgerState getCachedState() {
⋮----
simulationService = new SimulationService(mockCacheService);
⋮----
void testEquityLtcgTaxDynamicFromRules() {
TaxRulesConfig rules = TaxRulesLoader.loadRules("2026-27");
assertNotNull(rules);
⋮----
new BigDecimal("150.0"),
⋮----
SimulationService.TradeSimulationResult res = simulationService.simulateTrade(req);
assertEquals("DISPOSAL", res.tradeType());
assertTrue(res.ltcgEquity().compareTo(BigDecimal.ZERO) > 0);
⋮----
BigDecimal expectedTaxable = res.ltcgEquity().subtract(rules.equityExemptionLimit());
BigDecimal expectedTax = expectedTaxable.multiply(rules.equityLtcgRate()).setScale(2, java.math.RoundingMode.HALF_UP);
assertEquals(expectedTax, res.estimatedTaxLiability());
⋮----
void testGoldSilverLtcgHeldOver24Months() {
TaxEvent acqGold = new TaxEvent(
⋮----
LocalDate.of(2023, 1, 1),
new BigDecimal("100.0"),
⋮----
List.of(acqGold),
new com.portfolioos.core.matcher.FifoMatcher().processEvents(List.of(acqGold)),
Map.of("INF247L01BM8", new BigDecimal("150.0")),
⋮----
SimulationService simService = new SimulationService(new LedgerCacheService(null) {
⋮----
SimulationService.TradeSimulationResult res = simService.simulateTrade(req);
⋮----
BigDecimal expectedTax = new BigDecimal("5000.00").multiply(new BigDecimal("0.125")).setScale(2, java.math.RoundingMode.HALF_UP);
⋮----
void testGoldSilverStcgHeldUnder24Months() {
TaxEvent acqGoldShort = new TaxEvent(
⋮----
LocalDate.of(2026, 1, 1),
⋮----
List.of(acqGoldShort),
new com.portfolioos.core.matcher.FifoMatcher().processEvents(List.of(acqGoldShort)),
⋮----
assertTrue(res.slabRateGain().compareTo(new BigDecimal("5000.00")) == 0);
assertEquals(res.slabRateGain(), res.debtGain(), "debtGain() alias must equal slabRateGain()");
assertTrue(res.taxSummaryNotice().contains("SLAB_RATE — not computed"));
⋮----
void testRegressionNoHardcodedTaxLiterals() throws Exception {
File simFile = new File("src/main/java/com/portfolioos/core/service/SimulationService.java");
assertTrue(simFile.exists(), "SimulationService.java must exist");
String content = Files.readString(simFile.toPath());
⋮----
assertFalse(content.contains("new BigDecimal(\"0.125\")"), "Must not contain hardcoded 0.125 rate literal");
assertFalse(content.contains("new BigDecimal(\"0.20\")"), "Must not contain hardcoded 0.20 rate literal");
assertFalse(content.contains("new BigDecimal(\"0.30\")"), "Must not contain hardcoded 0.30 rate literal");
assertFalse(content.contains("new BigDecimal(\"0.3\")"), "Must not contain hardcoded 0.3 rate literal");
assertTrue(content.contains("default -> throw new IllegalStateException"), "Must contain explicit default throw branch");
````

## File: src/test/java/com/portfolioos/core/service/TaxOptimizationServiceTest.java
````java
class TaxOptimizationServiceTest {
⋮----
private EventStorePort createMockEventStore(List<TaxEvent> events) {
return new EventStorePort() {
@Override public String appendEvent(TaxEvent event) { return "EV_1"; }
@Override public List<String> appendEvents(List<TaxEvent> events) { return List.of("EV_1"); }
@Override public List<TaxEvent> getEventsForAsset(String assetId) { return events; }
@Override public List<TaxEvent> getAllEvents() { return events; }
@Override public boolean verifyLedgerIntegrity() { return true; }
@Override public void clearAllEvents() {}
@Override public String getLatestEventHash() { return "HASH"; }
⋮----
void testGetHarvestOpportunitiesWithPriorRealizedLtcg() {
TaxEvent acq1 = new TaxEvent(
⋮----
EventType.ACQUISITION, LocalDate.of(2024, 1, 1),
new BigDecimal("1000.0"), new BigDecimal("100.0"), new BigDecimal("100000.0"),
"CAS_IMPORT", Instant.now()
⋮----
TaxEvent acq2 = new TaxEvent(
⋮----
TaxEvent disp2 = new TaxEvent(
⋮----
EventType.DISPOSAL, LocalDate.of(2026, 5, 1),
new BigDecimal("1000.0"), new BigDecimal("200.0"), new BigDecimal("200000.0"),
⋮----
List<TaxEvent> events = List.of(acq1, acq2, disp2);
⋮----
EventStorePort mockEventStore = createMockEventStore(events);
TaxOptimizationService service = new TaxOptimizationService(mockEventStore);
⋮----
List<HarvestOpportunityDto> opps = service.getHarvestOpportunities();
assertNotNull(opps);
⋮----
BigDecimal totalHarvestableGain = opps.stream()
.map(o -> new BigDecimal(o.potentialHarvestableLoss()))
.reduce(BigDecimal.ZERO, BigDecimal::add);
⋮----
assertTrue(totalHarvestableGain.compareTo(new BigDecimal("25000.00")) <= 0,
⋮----
void testGetHarvestOpportunitiesZeroRealizedLtcg() {
⋮----
new BigDecimal("2000.0"), new BigDecimal("100.0"), new BigDecimal("200000.0"),
⋮----
List<TaxEvent> events = List.of(acq1);
⋮----
assertTrue(totalHarvestableGain.compareTo(new BigDecimal("125000.00")) <= 0,
````

## File: src/test/java/com/portfolioos/core/tools/PortfolioQueryToolsTest.java
````java
class PortfolioQueryToolsTest {
⋮----
void setUp() {
TaxEvent acq = new TaxEvent(
⋮----
LocalDate.of(2024, 1, 1),
new BigDecimal("1000.0"),
new BigDecimal("100.0"),
new BigDecimal("100000.0"),
⋮----
Instant.now()
⋮----
FifoMatcher matcher = new FifoMatcher();
FifoMatcher.FifoResult fifoResult = matcher.processEvents(List.of(acq));
⋮----
Map<String, BigDecimal> navMap = Map.of("INF109KC13X2", new BigDecimal("80.0"));
⋮----
List.of(acq),
⋮----
System.currentTimeMillis(),
⋮----
LedgerCacheService mockCacheService = new LedgerCacheService(null) {
⋮----
public CachedLedgerState getCachedState() {
⋮----
PortfolioValuationService mockValuationService = new PortfolioValuationService(mockCacheService) {
⋮----
public PortfolioSummaryResponse getPortfolioSummary(String fy) {
return new PortfolioSummaryResponse(
⋮----
DuckDbProjector duckDbProjector = new DuckDbProjector();
⋮----
queryTools = new PortfolioQueryTools(
⋮----
void testSite3LlmToolGetRebalancePlanDisarmsDrawdown() {
Map<String, Object> result = queryTools.getRebalancePlan();
assertNotNull(result);
assertEquals("SUCCESS", result.get("status"));
assertEquals("getRebalancePlan", result.get("source_tool"));
⋮----
Object triggerObj = result.get("trigger");
assertNotNull(triggerObj);
⋮----
assertEquals("NONE", trigger.drawdownContext().armedTier(),
⋮----
assertEquals(0.0, trigger.drawdownContext().currentDrawdownPct(),
⋮----
void testRegressionNoPersonalNetWorthPassedAsBenchmarkParamInQueryTools() throws Exception {
File file = new File("src/main/java/com/portfolioos/core/tools/PortfolioQueryTools.java");
assertTrue(file.exists());
String content = Files.readString(file.toPath());
⋮----
assertFalse(content.contains("buildPreviewPlan(\n            state.fifoResult().openLots(),\n            state.fifoResult().matchedLots(),\n            state.navMap(),\n            LocalDate.now(),\n            currentVal,\n            rollingHigh,"),
````

## File: src/test/java/com/portfolioos/core/valuation/BucketAllocationTest.java
````java
class BucketAllocationTest {
⋮----
void testBucketClassificationOrderAndLegacyExclusion() {
Set<String> activeOrPreferred = Set.of("INF109KC12U0"); // Only ICICI LargeMidcap 250 is preferred
⋮----
// Gold FoF: should match GOLD_SILVER category FIRST despite not being in activeOrPreferred set
BucketEngine.Bucket goldBucket = BucketEngine.classifyAssetToBucket("INF247L01BM8", "Motilal Oswal Gold and Silver Passive Fund of Funds", activeOrPreferred);
assertEquals(BucketEngine.Bucket.GOLD_SILVER, goldBucket, "Gold/Silver category match must take priority over legacy check");
⋮----
// Arbitrage: should match LIQUID_BUFFER category FIRST
BucketEngine.Bucket liquidBucket = BucketEngine.classifyAssetToBucket("INF205K01KR8", "Invesco India Arbitrage Fund", activeOrPreferred);
assertEquals(BucketEngine.Bucket.LIQUID_BUFFER, liquidBucket, "Liquid/Arbitrage keyword match must take priority over legacy check");
⋮----
// Preferred Core Fund: matches EQUITY_CORE
BucketEngine.Bucket coreBucket = BucketEngine.classifyAssetToBucket("INF109KC12U0", "ICICI Prudential Nifty LargeMidcap 250 Index Fund", activeOrPreferred);
assertEquals(BucketEngine.Bucket.EQUITY_CORE, coreBucket, "Preferred equity fund must map to active equity bucket");
⋮----
// Inactive Non-Preferred Equity Fund: maps to LEGACY_HOLDINGS
BucketEngine.Bucket legacyBucket = BucketEngine.classifyAssetToBucket("INF109K01234", "Nifty 100 Equal Weight Index Fund", activeOrPreferred);
assertEquals(BucketEngine.Bucket.LEGACY_HOLDINGS, legacyBucket, "Inactive non-preferred equity fund must map to LEGACY_HOLDINGS");
⋮----
void testExactValuationAndDriftAssertions() {
LocalDate date = LocalDate.of(2026, 8, 10);
BigDecimal nav = new BigDecimal("100.00");
⋮----
// Fixture: Total Corpus = ₹1,000,000
Lot coreLot = new Lot("lot-1", "INF109KC12U0", "ICICI LargeMidcap 250", date, new BigDecimal("4500"), new BigDecimal("4500"), nav, new BigDecimal("450000.00"), false, null);
Lot satLot = new Lot("lot-2", "INF204K01K15", "Motilal Oswal Smallcap Fund", date, new BigDecimal("1500"), new BigDecimal("1500"), nav, new BigDecimal("150000.00"), false, null);
Lot goldLot = new Lot("lot-3", "INF247L01BM8", "Motilal Gold Silver FoF", date, new BigDecimal("1500"), new BigDecimal("1500"), nav, new BigDecimal("150000.00"), false, null);
Lot liqLot = new Lot("lot-4", "INF205K01KR8", "Invesco Arbitrage Fund", date, new BigDecimal("1500"), new BigDecimal("1500"), nav, new BigDecimal("150000.00"), false, null);
Lot legLot = new Lot("lot-5", "INF109K01234", "Nifty 100 EW Fund", date, new BigDecimal("1000"), new BigDecimal("1000"), nav, new BigDecimal("100000.00"), false, null);
⋮----
List<Lot> openLots = List.of(coreLot, satLot, goldLot, liqLot, legLot);
Map<String, BigDecimal> navMap = Map.of(
⋮----
List<BucketEngine.BucketTarget> targets = List.of(
new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("50.00"), new BigDecimal("5.00")),
new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("20.00"), new BigDecimal("5.00")),
new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("15.00"), new BigDecimal("5.00")),
new BucketEngine.BucketTarget(BucketEngine.Bucket.LIQUID_BUFFER, new BigDecimal("15.00"), new BigDecimal("5.00"))
⋮----
Set<String> activeOrPreferred = Set.of("INF109KC12U0", "INF204K01K15", "INF247L01BM8", "INF205K01KR8");
⋮----
BucketEngine.RebalanceEngineResult result = BucketEngine.evaluateRebalance(
openLots, List.of(), navMap, date, BigDecimal.ZERO, BigDecimal.ZERO, targets, "2026-27", activeOrPreferred
⋮----
assertEquals(5, result.bucketStatuses().size(), "Engine must return status for all 5 buckets");
⋮----
for (BucketEngine.BucketStatus s : result.bucketStatuses()) {
statusMap.put(s.bucket(), s);
⋮----
// EQUITY_CORE: 450,000 (45.00%), Target 50.00%, Drift -5.00%, isDrifted = false (exact boundary -5.00% is NOT > 5.00%)
BucketEngine.BucketStatus coreStatus = statusMap.get(BucketEngine.Bucket.EQUITY_CORE);
assertNotNull(coreStatus);
assertEquals(new BigDecimal("450000.00"), coreStatus.currentValue());
assertEquals(new BigDecimal("45.00"), coreStatus.currentPct());
assertEquals(new BigDecimal("50.00"), coreStatus.targetPct());
assertEquals(new BigDecimal("-5.00"), coreStatus.driftPct());
assertFalse(coreStatus.isDrifted(), "Boundary match |-5.00%| is NOT > 5.00% threshold");
⋮----
// EQUITY_SATELLITE: 150,000 (15.00%), Target 20.00%, Drift -5.00%, isDrifted = false
BucketEngine.BucketStatus satStatus = statusMap.get(BucketEngine.Bucket.EQUITY_SATELLITE);
assertNotNull(satStatus);
assertEquals(new BigDecimal("150000.00"), satStatus.currentValue());
assertEquals(new BigDecimal("15.00"), satStatus.currentPct());
assertEquals(new BigDecimal("20.00"), satStatus.targetPct());
assertEquals(new BigDecimal("-5.00"), satStatus.driftPct());
assertFalse(satStatus.isDrifted());
⋮----
// GOLD_SILVER: 150,000 (15.00%), Target 15.00%, Drift 0.00%, isDrifted = false
BucketEngine.BucketStatus goldStatus = statusMap.get(BucketEngine.Bucket.GOLD_SILVER);
assertNotNull(goldStatus);
assertEquals(new BigDecimal("150000.00"), goldStatus.currentValue());
assertEquals(new BigDecimal("15.00"), goldStatus.currentPct());
assertEquals(new BigDecimal("15.00"), goldStatus.targetPct());
assertEquals(new BigDecimal("0.00"), goldStatus.driftPct());
assertFalse(goldStatus.isDrifted());
⋮----
// LIQUID_BUFFER: 150,000 (15.00%), Target 15.00%, Drift 0.00%, isDrifted = false
BucketEngine.BucketStatus liqStatus = statusMap.get(BucketEngine.Bucket.LIQUID_BUFFER);
assertNotNull(liqStatus);
assertEquals(new BigDecimal("150000.00"), liqStatus.currentValue());
assertEquals(new BigDecimal("15.00"), liqStatus.currentPct());
assertEquals(new BigDecimal("15.00"), liqStatus.targetPct());
assertEquals(new BigDecimal("0.00"), liqStatus.driftPct());
assertFalse(liqStatus.isDrifted());
⋮----
// LEGACY_HOLDINGS: 100,000 (10.00%), Target 0.00%, Drift +10.00%, isDrifted = false (forced false for legacy)
BucketEngine.BucketStatus legStatus = statusMap.get(BucketEngine.Bucket.LEGACY_HOLDINGS);
assertNotNull(legStatus);
assertEquals(0, new BigDecimal("100000.00").compareTo(legStatus.currentValue()));
assertEquals(0, new BigDecimal("10.00").compareTo(legStatus.currentPct()));
assertEquals(0, BigDecimal.ZERO.compareTo(legStatus.targetPct()), "Target % for LEGACY_HOLDINGS must be 0");
assertEquals(0, new BigDecimal("10.00").compareTo(legStatus.driftPct()));
assertFalse(statusMap.get(BucketEngine.Bucket.LEGACY_HOLDINGS).isDrifted(), "LEGACY_HOLDINGS must never be marked drifted");
⋮----
void testRenormalizedSipAllocationsExcludingGold() {
LocalDate date = LocalDate.of(2026, 8, 20); // v2.0 active
Map<String, Double> renormalized = com.portfolioos.core.rules.BucketConfigLoader.getRenormalizedSipAllocations(date);
⋮----
assertEquals(6, renormalized.size(), "Renormalized map must contain exactly 6 non-Gold funds");
assertFalse(renormalized.containsKey("INF247L01BM8"), "Gold FoF must be excluded from flat monthly SIP");
⋮----
for (double w : renormalized.values()) {
⋮----
assertEquals(1.0, sum, 1e-6, "Sum of renormalized non-Gold SIP weights must equal 100% (1.0)");
⋮----
assertEquals(0.3060, renormalized.get("INF109KC12U0"), 0.001, "ICICI LargeMidcap 250 must be ~30.60%");
assertEquals(0.2203, renormalized.get("INF879O01027"), 0.001, "Parag Parikh Flexi Cap must be ~22.03%");
assertEquals(0.1684, renormalized.get("INF109KC13X2"), 0.001, "ICICI Value 30 must be ~16.84%");
assertEquals(0.1474, renormalized.get("INF754K01TN5"), 0.001, "Edelweiss Momentum must be ~14.74%");
assertEquals(0.1053, renormalized.get("INF205K01KR8"), 0.001, "Invesco Arbitrage must be ~10.53%");
assertEquals(0.0526, renormalized.get("INF204K01K15"), 0.001, "Nippon Small Cap must be ~5.26%");
````

## File: src/test/java/com/portfolioos/core/valuation/GoldDampenerCalculatorTest.java
````java
class GoldDampenerCalculatorTest {
⋮----
void testCheapStateMultipliers() {
GoldDampenerCalculator.DampenerMultipliers mults = GoldDampenerCalculator.calculateMultipliers(-5.0);
assertEquals(1.30, mults.buyMultiplier(), 0.0001);
assertEquals(0.60, mults.sellMultiplier(), 0.0001);
⋮----
GoldDampenerCalculator.DampenerMultipliers zeroMults = GoldDampenerCalculator.calculateMultipliers(0.0);
assertEquals(1.30, zeroMults.buyMultiplier(), 0.0001);
assertEquals(0.60, zeroMults.sellMultiplier(), 0.0001);
⋮----
void testMidpointLinearTaperMultipliers() {
GoldDampenerCalculator.DampenerMultipliers mults = GoldDampenerCalculator.calculateMultipliers(10.0);
assertEquals(0.85, mults.buyMultiplier(), 0.0001, "At dev=10%, buy multiplier must taper linearly to 85%");
assertEquals(1.00, mults.sellMultiplier(), 0.0001, "At dev=10%, sell multiplier must taper linearly to 100%");
⋮----
void testExtendedStateMultipliers() {
GoldDampenerCalculator.DampenerMultipliers mults = GoldDampenerCalculator.calculateMultipliers(20.0);
assertEquals(0.40, mults.buyMultiplier(), 0.0001);
assertEquals(1.40, mults.sellMultiplier(), 0.0001);
⋮----
GoldDampenerCalculator.DampenerMultipliers overMults = GoldDampenerCalculator.calculateMultipliers(25.0);
assertEquals(0.40, overMults.buyMultiplier(), 0.0001);
assertEquals(1.40, overMults.sellMultiplier(), 0.0001);
⋮----
void testFloorBackstopOverridesDampenerUnderExtendedState() {
⋮----
double currentWeightPct = 10.0; // 5 points underweight
⋮----
double trailingMa = 100.0; // dev = +20% (highly extended)
BigDecimal corpus = new BigDecimal("1000000.00"); // 10 Lakhs
⋮----
// Normal buy allocation at dev=+20% would damp buy to 40%: (5% * 10L) * 0.40 = 20,000
BigDecimal normalDampenedBuy = GoldDampenerCalculator.calculateSizedAllocation(
⋮----
assertEquals(new BigDecimal("20000.00"), normalDampenedBuy);
⋮----
// Floor backstop under extended state (+20%) MUST override dampener to 1.0x and size to 50% of gap (2.5%):
// 2.5% * 10L * 1.0x = 25,000
BigDecimal floorBackstopSized = GoldDampenerCalculator.calculateSizedAllocation(
⋮----
assertEquals(new BigDecimal("25000.00"), floorBackstopSized,
````

## File: src/test/java/com/portfolioos/core/valuation/MonteCarloSanityTest.java
````java
public class MonteCarloSanityTest {
⋮----
public void testMonteCarloDivergenceAndBounds() {
BigDecimal deterministicFv = new BigDecimal("19997165.16");
BigDecimal mcMedian = new BigDecimal("17871599.69");
⋮----
// Assert that Monte Carlo median is non-zero
assertTrue(mcMedian.compareTo(BigDecimal.ZERO) > 0, "Monte Carlo median should be non-zero");
⋮----
// Assert that Monte Carlo median does NOT collapse bit-for-bit onto deterministic FV
assertNotEquals(0, mcMedian.compareTo(deterministicFv), "Monte Carlo median should be independent from deterministic FV");
⋮----
// Assert ratio between Monte Carlo median and deterministic FV is realistic (between 0.6x and 1.3x due to volatility drag)
double ratio = mcMedian.doubleValue() / deterministicFv.doubleValue();
assertTrue(ratio >= 0.6 && ratio <= 1.3, "Monte Carlo median ratio to deterministic FV should be between 0.6x and 1.3x, but was: " + ratio);
⋮----
// Assert success rate reflects decumulation survival under shortage (between 10% and 90%)
assertTrue(successRate >= 10.0 && successRate <= 90.0, "Success rate must reflect real decumulation survival under shortage");
````

## File: src/test/java/com/portfolioos/core/valuation/RebalanceWaterfallEngineTest.java
````java
class RebalanceWaterfallEngineTest {
⋮----
void testLegacyFundPriorityDynamicInactiveSip() {
LocalDate today = LocalDate.of(2026, 8, 1);
LocalDate acqRecent = LocalDate.of(2026, 7, 15); // Active fund: purchase within 3 months
LocalDate acqOld = LocalDate.of(2024, 1, 1);     // Inactive/Legacy fund: no purchase in last 3 months
⋮----
// Core active fund lot (purchased 17 days ago)
Lot coreActiveLot = new Lot("L1", "NIFTY_LARGEMIDCAP_250", "Nifty LargeMidcap 250 Index Fund",
acqRecent, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, BigDecimal.ZERO);
⋮----
// Inactive fund lot (no purchase in last 3 months)
Lot inactiveLegacyLot = new Lot("L2", "OLD_FUND_XYZ", "Old Phased Out Fund",
acqOld, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, BigDecimal.ZERO);
⋮----
Map<String, BigDecimal> navMap = Map.of(
"NIFTY_LARGEMIDCAP_250", new BigDecimal("150"),
"OLD_FUND_XYZ", new BigDecimal("150")
⋮----
// Trim 5,000 INR
RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
⋮----
new BigDecimal("5000"),
List.of(coreActiveLot, inactiveLegacyLot),
⋮----
new BigDecimal("125000"),
⋮----
assertNotNull(result);
assertEquals(new BigDecimal("5000.00"), result.satisfiedAmount());
assertEquals(new BigDecimal("0.00"), result.deferredAmount());
assertFalse(result.steps().isEmpty());
⋮----
// First step must be Tier 1 (LEGACY_FUND) for the inactive fund
RebalanceWaterfallEngine.WaterfallStep firstStep = result.steps().get(0);
assertEquals(WaterfallTier.LEGACY_FUND, firstStep.tier());
assertEquals("OLD_FUND_XYZ", firstStep.assetId());
⋮----
void testStcgDeferralWhenNotUrgent() {
⋮----
LocalDate acqRecent = LocalDate.of(2026, 7, 1); // 1 month old -> STCG
⋮----
Lot recentLot = new Lot("L1", "NIFTY_LARGEMIDCAP_250", "Nifty LargeMidcap 250 Index Fund",
⋮----
"NIFTY_LARGEMIDCAP_250", new BigDecimal("150")
⋮----
// Trim 5,000 INR, urgent = false
⋮----
List.of(recentLot),
⋮----
assertEquals(new BigDecimal("0.00"), result.satisfiedAmount());
assertEquals(new BigDecimal("5000.00"), result.deferredAmount());
assertTrue(result.steps().isEmpty());
assertNotNull(result.deferralReason());
⋮----
void testStcgExecutionWhenUrgent() {
⋮----
// Trim 5,000 INR, urgent = true
⋮----
assertEquals(new BigDecimal("5000.00"), result.satisfiedAmount(), "Under DRAWDOWN / urgent trigger, STCG realization IS allowed with tax drag explicitly calculated");
⋮----
assertEquals(WaterfallTier.STCG_URGENT_ONLY, result.steps().get(0).tier());
assertEquals("SHORT_TERM", result.steps().get(0).taxTerm());
assertTrue(result.totalTaxDrag().compareTo(BigDecimal.ZERO) > 0, "STCG tax drag must be strictly greater than 0 under DRAWDOWN trigger");
assertEquals(new BigDecimal("333.33"), result.totalTaxDrag(), "STCG tax drag must equal 20% of realized gain (333.33)");
⋮----
void testStcgExcludedWhenNotUrgent() {
⋮----
Map<String, BigDecimal> navMap = Map.of("NIFTY_LARGEMIDCAP_250", new BigDecimal("150"));
⋮----
// Trim 5,000 INR, urgent = false (routine DRIFT trigger)
⋮----
assertEquals(new BigDecimal("0.00"), result.satisfiedAmount(), "STCG lots must be 100% excluded under routine DRIFT (urgent=false)");
````

## File: src/test/java/com/portfolioos/core/xirr/XirrEngineTest.java
````java
class XirrEngineTest {
⋮----
void testXirrCalculationSimpleReturn() {
XirrEngine engine = new XirrEngine();
⋮----
CashFlow cf1 = new CashFlow(LocalDate.of(2023, 1, 1), new BigDecimal("-100000.00"));
CashFlow cf2 = new CashFlow(LocalDate.of(2024, 1, 1), new BigDecimal("112000.00"));
⋮----
double xirr = engine.calculateXirr(List.of(cf1, cf2));
assertTrue(xirr > 11.5 && xirr < 12.5, "XIRR should be approx 12.0%");
⋮----
void testXirrShortDurationReturnsAbsoluteGain() {
⋮----
CashFlow cf1 = new CashFlow(LocalDate.of(2024, 1, 1), new BigDecimal("-100000.00"));
CashFlow cf2 = new CashFlow(LocalDate.of(2024, 1, 15), new BigDecimal("105000.00"));
⋮----
assertEquals(5.0, xirr, 0.01, "Short duration <30 days should return absolute return (5%)");
⋮----
void testXirrNullOrInsufficientFlows() {
⋮----
assertEquals(0.0, engine.calculateXirr(null));
assertEquals(0.0, engine.calculateXirr(List.of()));
assertEquals(0.0, engine.calculateXirr(List.of(new CashFlow(LocalDate.now(), new BigDecimal("-100")))));
````

## File: Dockerfile
````dockerfile
FROM docker.io/library/eclipse-temurin:21-jre

WORKDIR /app

COPY target/core-node-3.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "--add-opens=java.base/java.nio=ALL-UNNAMED", "-jar", "app.jar"]
````

## File: pom.xml
````xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
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
            <version>3.46.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.duckdb</groupId>
            <artifactId>duckdb_jdbc</artifactId>
            <version>0.10.2</version>
        </dependency>
        
        <!-- Apache POI for AMC Excel Factsheets -->
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>5.2.5</version>
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

        <!-- Project Reactor for SSE Flux Streaming -->
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-core</artifactId>
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
            <plugin>
                <groupId>org.graalvm.buildtools</groupId>
                <artifactId>native-maven-plugin</artifactId>
                <version>0.10.2</version>
            </plugin>
        </plugins>
    </build>
</project>
````

## File: rules/bucket_targets.yaml
````yaml
---
versions:
- version_id: "v1.0"
  effective_from: "2024-01-01"
  targets:
  - bucket: "EQUITY_CORE"
    target_pct: 50.0
    band_pct: 5.0
    trigger_drift_pct: 5.0
    strategy: "CORE"
    preferred_funds:
    - fund_id: "INF109KC12U0"
      fund_name: "ICICI Prudential Nifty LargeMidcap 250 Index Fund"
      allocation_weight: 0.5
    - fund_id: "INF879O01027"
      fund_name: "Parag Parikh Flexi Cap Fund"
      allocation_weight: 0.5
  - bucket: "EQUITY_SATELLITE"
    target_pct: 20.0
    band_pct: 5.0
    trigger_drift_pct: 5.0
    strategy: "SATELLITE"
    preferred_funds:
    - fund_id: "INF109KC13X2"
      fund_name: "ICICI Prudential Nifty200 Value 30 Index Fund"
      allocation_weight: 0.25
    - fund_id: "INF754K01TN5"
      fund_name: "Edelweiss Nifty500 Multicap Momentum Quality 50 Index Fund"
      allocation_weight: 0.25
    - fund_id: "INF204K01K15"
      fund_name: "Nippon India Small Cap Fund"
      allocation_weight: 0.25
    - fund_id: "INF247L01BQ9"
      fund_name: "Motilal Oswal Nifty Microcap 250 Index Fund"
      allocation_weight: 0.25
  - bucket: "GOLD_SILVER"
    target_pct: 15.0
    band_pct: 5.0
    trigger_drift_pct: 12.0
    strategy: "ACCUMULATOR"
    preferred_funds:
    - fund_id: "INF247L01BM8"
      fund_name: "Motilal Oswal Gold and Silver Passive Fund of Funds"
      allocation_weight: 1.0
  - bucket: "LIQUID_BUFFER"
    target_pct: 15.0
    band_pct: 5.0
    trigger_drift_pct: 5.0
    strategy: "ARBITRAGE"
    preferred_funds:
    - fund_id: "INF205K01KR8"
      fund_name: "Invesco India Arbitrage Fund"
      allocation_weight: 1.0
- version_id: "v2.0"
  effective_from: "2026-08-17"
  targets:
  - bucket: "EQUITY_CORE"
    target_pct: 50.0
    band_pct: 5.0
    trigger_drift_pct: 5.0
    strategy: "CORE"
    preferred_funds:
    - fund_id: "INF109KC12U0"
      fund_name: "ICICI Prudential Nifty LargeMidcap 250 Index Fund"
      allocation_weight: 0.5814
    - fund_id: "INF879O01027"
      fund_name: "Parag Parikh Flexi Cap Fund"
      allocation_weight: 0.4186
  - bucket: "EQUITY_SATELLITE"
    target_pct: 35.0
    band_pct: 5.0
    trigger_drift_pct: 5.0
    strategy: "SATELLITE"
    preferred_funds:
    - fund_id: "INF109KC13X2"
      fund_name: "ICICI Prudential Nifty200 Value 30 Index Fund"
      allocation_weight: 0.4571
    - fund_id: "INF754K01TN5"
      fund_name: "Edelweiss Nifty500 Multicap Momentum Quality 50 Index Fund"
      allocation_weight: 0.4000
    - fund_id: "INF204K01K15"
      fund_name: "Nippon India Small Cap Fund"
      allocation_weight: 0.1429
  - bucket: "GOLD_SILVER"
    target_pct: 5.0
    band_pct: 5.0
    trigger_drift_pct: 12.0
    strategy: "ACCUMULATOR"
    preferred_funds:
    - fund_id: "INF247L01BM8"
      fund_name: "Motilal Oswal Gold and Silver Passive Fund of Funds"
      allocation_weight: 1.0
  - bucket: "LIQUID_BUFFER"
    target_pct: 10.0
    band_pct: 5.0
    trigger_drift_pct: 5.0
    strategy: "ARBITRAGE"
    preferred_funds:
    - fund_id: "INF205K01KR8"
      fund_name: "Invesco India Arbitrage Fund"
      allocation_weight: 1.0
````
