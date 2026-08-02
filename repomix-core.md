This file is a merged representation of a subset of the codebase, containing files not matching ignore patterns, combined into a single document by Repomix.
The content has been processed where empty lines have been removed, content has been compressed (code blocks are separated by ⋮---- delimiter).

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
- Content has been compressed - code blocks are separated by ⋮---- delimiter
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
              LedgerCacheService.java
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
```

## File: src/main/java/com/portfolioos/core/controllers/RebalanceController.java
```java
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
```

## File: src/main/java/com/portfolioos/core/controllers/ReportController.java
```java
public class ReportController {
⋮----
public ResponseEntity<PortfolioSummaryResponse> getSummary(
⋮----
return ResponseEntity.ok(valuationService.getPortfolioSummary(fy));
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
```

## File: src/main/java/com/portfolioos/core/controllers/StatementsController.java
```java
public class StatementsController {
⋮----
this.restClient = RestClient.builder().baseUrl(sidecarUrl).build();
⋮----
public ResponseEntity<?> uploadStatement(
⋮----
// Forward request to sidecar
⋮----
// Convert file to ByteArrayResource for multipart formatting
ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
⋮----
public String getFilename() {
return file.getOriginalFilename();
⋮----
body.add("file", fileResource);
if (password != null && !password.isEmpty()) {
body.add("password", password);
⋮----
// POST to parser sidecar
ResponseEntity<ParsedEventDto[]> response = restClient.post()
.uri("/api/v1/parse")
.contentType(MediaType.MULTIPART_FORM_DATA)
.body(body)
.retrieve()
.toEntity(ParsedEventDto[].class);
⋮----
ParsedEventDto[] dtoList = response.getBody();
⋮----
return ResponseEntity.ok(List.of());
⋮----
// Convert to domain entities and append to event store
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
// Write to SQLite
eventStore.appendEvents(taxEvents);
⋮----
// Re-project events in DuckDB
List<TaxEvent> allEvents = eventStore.getAllEvents();
duckDbProjector.projectEvents(allEvents);
⋮----
return ResponseEntity.ok(dtoList);
⋮----
return ResponseEntity.internalServerError().body("File reading failed: " + e.getMessage());
⋮----
return ResponseEntity.internalServerError().body("Upload and parsing failed: " + e.getMessage());
```

## File: src/main/java/com/portfolioos/core/controllers/SyncController.java
```java
public class SyncController {
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
BigDecimal nav = navMap.getOrDefault(assetId, lots.get(0).costPerUnit());
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
if (sharpeObj instanceof Number sharpe && sharpe.doubleValue() >= 1.2) {
radarSignals.add(new RadarSignalDto(
⋮----
"QUANT STATS: HIGH SHARPE (" + String.format("%.2f", sharpe.doubleValue()) + ")",
schemeName + " displays a risk-adjusted Sharpe ratio of " + String.format("%.2f", sharpe.doubleValue()) + " over tracked NAV history.",
⋮----
"Sharpe " + String.format("%.2f", sharpe.doubleValue())
⋮----
if (maxDdObj instanceof Number maxDd && Math.abs(maxDd.doubleValue()) >= 0.20) {
double maxDdPct = Math.abs(maxDd.doubleValue()) * 100.0;
⋮----
"QUANT STATS: DEEP DRAWDOWN (" + String.format("%.1f", maxDdPct) + "%)",
schemeName + " max drawdown (" + String.format("%.1f", maxDdPct) + "%) exceeds 20% threshold — historical corrections deeper than buffer sizing.",
⋮----
"Max DD -" + String.format("%.1f", maxDdPct) + "%"
⋮----
System.err.println("Non-critical Quant Flight RPC signal extraction warning: " + ex.getMessage());
⋮----
// 3. LTCG Maturation Ladder Signal
⋮----
long daysToLtcg = Math.max(0L, 365L - holdingDays);
⋮----
radarSignals.add(0, new RadarSignalDto(
⋮----
maturingLot.assetName(),
⋮----
maturingLot.assetName() + " (Lot " + maturingLot.lotId() + ") matures under Sec 112A in " + minDaysToLtcg + " days.",
⋮----
// 4. Asset Allocation Drift Signal
BucketEngine.RebalanceEngineResult bucketStatus = BucketEngine.evaluateRebalance(
openLots, navMap, today, new BigDecimal("24000.00"), new BigDecimal("25000.00"), BucketEngine.DEFAULT_TARGETS, fy
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
return ResponseEntity.ok(new UnidirectionalSyncSnapshot(
⋮----
public ResponseEntity<PairResponseDto> pairDevice(
⋮----
String token = "fintracker_jwt_" + req.deviceId() + "_" + System.currentTimeMillis();
return ResponseEntity.ok(new PairResponseDto(
```

## File: src/main/java/com/portfolioos/core/dtos/ReportDtos.java
```java
public class ReportDtos {
```

## File: src/main/java/com/portfolioos/core/dtos/SyncDtos.java
```java
public class SyncDtos {
```

## File: src/main/java/com/portfolioos/core/fire/FireTracker.java
```java
public class FireTracker {
⋮----
public static class FireProfile {
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
public int currentAge() { return currentAge; }
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
BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
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
⋮----
totalNetWorth.setScale(2, RoundingMode.HALF_UP),
profile.epfBalance(),
⋮----
fireInvestableNetWorth.setScale(2, RoundingMode.HALF_UP),
⋮----
diff.abs().setScale(2, RoundingMode.HALF_UP),
⋮----
profile.scenarios()
⋮----
return calculateFireSummary(openLots, navMap, currentDate, new FireProfile(), BigDecimal.ZERO);
```

## File: src/main/java/com/portfolioos/core/goals/GoalTracker.java
```java
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
```

## File: src/main/java/com/portfolioos/core/matcher/FifoMatcher.java
```java
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
: TaxClassifier.classifyTaxTerm(category, holdingDays, "2026-27", isListed);
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
```

## File: src/main/java/com/portfolioos/core/matcher/TaxClassifier.java
```java
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
TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
⋮----
case DEBT_SPECIFIED_50AA -> TaxTerm.SHORT_TERM; // Sec 50AA: Always Short-Term
⋮----
if (holdingDays >= rules.equityLtcgThresholdDays()) {
⋮----
// Per Finance Act 2024: Listed ETFs get 12-month (365d) threshold; unlisted FoFs get 24-month (730d)
long threshold = isListed ? rules.equityLtcgThresholdDays() : rules.goldInternationalThresholdDays();
⋮----
if (holdingDays >= rules.goldInternationalThresholdDays()) {
```

## File: src/main/java/com/portfolioos/core/model/AssetCategory.java
```java

```

## File: src/main/java/com/portfolioos/core/model/EventType.java
```java

```

## File: src/main/java/com/portfolioos/core/model/Lot.java
```java
public Lot withRemainingUnitsAndCost(BigDecimal remaining, BigDecimal cost, BigDecimal costBasis) {
return new Lot(
⋮----
public Lot withAssetDetails(String newAssetId, String newAssetName, BigDecimal newOriginal, BigDecimal newRemaining, BigDecimal newCostPerUnit) {
```

## File: src/main/java/com/portfolioos/core/model/MatchedLot.java
```java

```

## File: src/main/java/com/portfolioos/core/model/TaxEvent.java
```java
public BigDecimal unitDelta() {
⋮----
case DISPOSAL, SGB_MATURITY -> units.negate();
```

## File: src/main/java/com/portfolioos/core/model/TaxTerm.java
```java

```

## File: src/main/java/com/portfolioos/core/nav/AmfiNavSync.java
```java
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
String isinGrowth = parts[1].trim();
if (isinGrowth.isEmpty()) {
⋮----
String schemeName = parts[3].trim();
String navStr = parts[4].trim();
⋮----
BigDecimal nav = new BigDecimal(navStr);
entries.add(new NavEntry(
⋮----
// Skip headers or corrupted rows
⋮----
public List<NavEntry> fetchLatestNavsFromAmfi() {
long now = System.currentTimeMillis();
⋮----
URI uri = new URI("https://www.amfiindia.com/spages/NAVAll.txt");
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
if (entry.isin() != null) {
navMap.put(entry.isin(), entry.nav());
```

## File: src/main/java/com/portfolioos/core/persistence/DuckDbProjector.java
```java
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
throw new RuntimeException("Failed to initialize DuckDB schema", e);
⋮----
public synchronized void projectEvents(List<TaxEvent> events) {
if (events == null || events.isEmpty()) return;
⋮----
try (Connection conn = getConnection()) {
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
public synchronized void saveNavHistoryBatchForHeldAssets(Map<String, BigDecimal> navMap, Set<String> heldIsins, LocalDate date) {
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
stmt.setString(1, assetId);
⋮----
try (ResultSet rs = stmt.executeQuery()) {
while (rs.next()) {
dates.add(rs.getString("nav_date"));
navs.add(rs.getDouble("nav"));
⋮----
if (!navs.isEmpty()) {
result.put(assetId, new NavHistorySeriesEntry(navs, dates));
⋮----
System.err.println("Failed to fetch NAV history series with dates from DuckDB: " + e.getMessage());
```

## File: src/main/java/com/portfolioos/core/persistence/SqliteEventStore.java
```java
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
String raw = prevHash + "|" + event.id() + "|" + event.assetId() + "|" + event.eventType().name() + "|" +
event.eventDate().toString() + "|" + toCanonicalString(event.units()) + "|" +
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
checkStmt.setString(6, event.sourceDocumentId());
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
TaxEvent mockEvent = new TaxEvent(
rs.getString("id"),
rs.getString("asset_id"),
⋮----
EventType.valueOf(rs.getString("event_type")),
LocalDate.parse(rs.getString("event_date")),
rs.getBigDecimal("units"),
⋮----
rs.getBigDecimal("gross_amount"),
rs.getString("source_document_id"),
⋮----
String recomputedHash = computeHash(expectedPrevHash, mockEvent);
if (!recomputedHash.equals(actualEventHash)) {
⋮----
throw new RuntimeException("Ledger integrity verification failed", e);
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
rs.getString("asset_name"),
rs.getString("isin"),
⋮----
new BigDecimal(rs.getString("units")),
new BigDecimal(rs.getString("price_per_unit")),
new BigDecimal(rs.getString("gross_amount")),
⋮----
Instant.parse(rs.getString("ingested_at"))
```

## File: src/main/java/com/portfolioos/core/ports/EventStorePort.java
```java
public interface EventStorePort {
String appendEvent(TaxEvent event);
List<String> appendEvents(List<TaxEvent> events);
List<TaxEvent> getEventsForAsset(String assetId);
List<TaxEvent> getAllEvents();
boolean verifyLedgerIntegrity();
void clearAllEvents();
String getLatestEventHash();
```

## File: src/main/java/com/portfolioos/core/reconciliation/ReconciliationGate.java
```java
public class ReconciliationGate {
⋮----
public static ReconciliationResult validateStatement(List<TaxEvent> events, BigDecimal declaredClosingUnits) {
⋮----
calculatedClosingUnits = calculatedClosingUnits.add(event.unitDelta());
⋮----
BigDecimal delta = calculatedClosingUnits.subtract(declaredClosingUnits).abs();
boolean isMatched = delta.compareTo(new BigDecimal("0.0001")) < 0;
⋮----
return new ReconciliationResult(isMatched, calculatedClosingUnits, declaredClosingUnits, delta, errorMessage);
```

## File: src/main/java/com/portfolioos/core/reporting/ExemptionTracker.java
```java
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
```

## File: src/main/java/com/portfolioos/core/reporting/Itr2CsvExporter.java
```java
public class Itr2CsvExporter {
⋮----
private static final LocalDate GRANDFATHER_CUTOFF = LocalDate.of(2018, 1, 31);
⋮----
public static Map<String, String> exportItr2ScheduleCg(List<MatchedLot> matchedLots, String fiscalYear, Map<String, String> assetNameMap) {
⋮----
map.put("Schedule_112A.csv", generateSchedule112aCsv(matchedLots, fiscalYear, assetNameMap, Map.of()));
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
sb.append("ISIN Code,Name of Share/Unit,No. of Shares/Units,Full Value of Consideration,Cost of Acquisition,FMV as on 31-Jan-2018,Total Deductions,Balance Capital Gain\n");
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
BigDecimal fmvJan2018 = fmv2018Map.getOrDefault(isin, actualCost);
⋮----
// Statutory Section 55(2)(ac) Formula:
// Deemed Cost = max(Actual Cost, min(FMV on 31-Jan-2018, Sale Proceeds))
⋮----
BigDecimal lowerBound = fmvJan2018.min(proceeds);
deemedCost = actualCost.max(lowerBound);
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
.append(fmt(gain)).append("\n");
⋮----
return sb.toString();
⋮----
public static String generateScheduleCgStcgCsv(List<MatchedLot> matchedLots, String fiscalYear, Map<String, String> assetNameMap) {
⋮----
List<MatchedLot> stcgLots = matchedLots.stream().filter(lot ->
lot.taxTerm() == TaxTerm.SHORT_TERM &&
⋮----
sb.append("Section,Asset Type,Asset Name,Disposal Date,Sale Proceeds,Cost Basis,STCG Realized,Tax Rate\n");
⋮----
String name = assetNameMap.getOrDefault(lot.assetId(), lot.assetId());
AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), name);
⋮----
sb.append("\"").append(section).append("\",\"")
.append(category.name()).append("\",\"")
⋮----
.append(lot.disposalDate()).append(",")
.append(fmt(lot.saleProceeds())).append(",")
.append(fmt(lot.costBasis())).append(",")
.append(fmt(lot.realizedGain())).append(",\"")
.append(taxRate).append("\"\n");
⋮----
public static String generateScheduleFaCsv(List<TaxEvent> allEventsList) {
⋮----
sb.append("Country Code,Foreign Entity Name,Address,Initial Investment (INR),Peak Value INR (Requires Statement Verification),Closing Balance (INR),Gross Amount Paid/Credited\n");
⋮----
List<TaxEvent> intlEvents = allEventsList.stream().filter(e ->
TaxClassifier.detectCategory(e.assetId(), e.assetName()) == AssetCategory.INTERNATIONAL
⋮----
Map<String, List<TaxEvent>> grouped = intlEvents.stream().collect(Collectors.groupingBy(TaxEvent::assetId));
for (Map.Entry<String, List<TaxEvent>> entry : grouped.entrySet()) {
⋮----
List<TaxEvent> events = entry.getValue();
⋮----
String name = events.get(0).assetName();
⋮----
if (e.eventType() == EventType.ACQUISITION) {
initialCost = initialCost.add(e.grossAmount());
⋮----
sb.append("\"US\",\"").append(name.replace("\"", "\"\"")).append("\",\"United States\",")
.append(fmt(initialCost)).append(",\"VERIFY_PEAK_NAV\",")
.append(fmt(initialCost)).append(",0.00\n");
⋮----
private static String fmt(BigDecimal val) {
return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
⋮----
private static Pair<LocalDate, LocalDate> getFiscalYearBounds(String fiscalYear) {
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
```

## File: src/main/java/com/portfolioos/core/reporting/TaxReportExporter.java
```java
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
```

## File: src/main/java/com/portfolioos/core/rpc/FlightRpcClient.java
```java
public class FlightRpcClient {
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
```

## File: src/main/java/com/portfolioos/core/rules/TaxRulesConfig.java
```java

```

## File: src/main/java/com/portfolioos/core/rules/TaxRulesLoader.java
```java
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
System.err.println(msg);
throw new IllegalArgumentException(msg);
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
```

## File: src/main/java/com/portfolioos/core/security/SecurityConfig.java
```java
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
```

## File: src/main/java/com/portfolioos/core/security/SecurityInterceptor.java
```java
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
clientHeader = request.getParameter("token");
⋮----
if (!token.equals(clientHeader)) {
response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
response.setContentType("application/json");
response.getWriter().write("{\"message\":\"Unauthorized: Missing or invalid X-Api-Auth-Token header or token parameter.\"}");
```

## File: src/main/java/com/portfolioos/core/service/LedgerCacheService.java
```java
public class LedgerCacheService {
⋮----
private final AmfiNavSync amfiSync = new AmfiNavSync();
private final FifoMatcher fifoMatcher = new FifoMatcher();
⋮----
private final Object lock = new Object();
⋮----
public void refreshCacheInBackground() {
⋮----
String currentHash = eventStore.getLatestEventHash();
long now = System.currentTimeMillis();
⋮----
if (cachedResult == null || !currentHash.equals(cachedHash) || (now - lastNavSyncTime) >= 30_000) {
cachedEvents = eventStore.getAllEvents();
cachedResult = fifoMatcher.processEvents(cachedEvents);
cachedNavMap = amfiSync.getNavMap();
⋮----
System.err.println("Background cache refresh warning: " + e.getMessage());
⋮----
public CachedLedgerState getCachedState() {
⋮----
refreshCacheInBackground();
⋮----
return new CachedLedgerState(cachedEvents, cachedResult, cachedNavMap, cachedHash);
⋮----
public void invalidateCache() {
```

## File: src/main/java/com/portfolioos/core/service/PortfolioValuationService.java
```java
public class PortfolioValuationService {
⋮----
private final XirrEngine xirrEngine = new XirrEngine();
⋮----
private String fmt(BigDecimal val) {
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
FireTracker.FireSummary fire = FireTracker.calculateFireSummary(openLots, navMap, LocalDate.now());
⋮----
List<FireScenarioDto> scenarioDtos = fire.scenarios().stream().map(s -> new FireScenarioDto(
s.id(),
s.label(),
fmt(s.monthlyExpenseToday()),
s.active()
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
public BucketRebalanceResponse getBucketRebalance(BigDecimal benchmarkCurrent, BigDecimal benchmarkRollingHigh, String fy) {
⋮----
BucketEngine.RebalanceEngineResult result = BucketEngine.evaluateRebalance(
openLots, navMap, LocalDate.now(), benchmarkCurrent, benchmarkRollingHigh, BucketEngine.DEFAULT_TARGETS, fy
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
```

## File: src/main/java/com/portfolioos/core/service/TaxOptimizationService.java
```java
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
return ExemptionTracker.calculateExemptionStatus(matchedLots, fy);
⋮----
public TaxReportExporter.Itr2ScheduleCgReport generateItr2Report(String fy) {
⋮----
return TaxReportExporter.generateItr2Report(matchedLots, fy);
⋮----
public List<HarvestOpportunityDto> getHarvestOpportunities() {
⋮----
List<Lot> openLots = fifoMatcher.processEvents(allEvents).openLots();
Map<String, BigDecimal> navMap = amfiSync.getNavMap();
⋮----
// Assume zero exemption used so far for simple harvest opportunity advice
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
⋮----
AssetCategory cat = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
boolean isListed = TaxClassifier.isListed(lot.assetId(), lot.assetName());
⋮----
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
⋮----
return Itr2CsvExporter.exportItr2ScheduleCg(matchedLots, fy, assetNameMap);
```

## File: src/main/java/com/portfolioos/core/util/Pair.java
```java

```

## File: src/main/java/com/portfolioos/core/valuation/AntigravityEngine.java
```java
public class AntigravityEngine {
⋮----
public static BigDecimal calculateBeta(List<Double> assetReturns, List<Double> marketReturns) {
if (assetReturns.size() < 2 || assetReturns.size() != marketReturns.size()) {
⋮----
int n = assetReturns.size();
double meanAsset = assetReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
double meanMarket = marketReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
⋮----
double devAsset = assetReturns.get(i) - meanAsset;
double devMarket = marketReturns.get(i) - meanMarket;
⋮----
return BigDecimal.valueOf(cov / varMarket).setScale(2, RoundingMode.HALF_UP);
⋮----
public static BigDecimal calculateDownsideBeta(List<Double> assetReturns, List<Double> marketReturns) {
if (assetReturns.size() != marketReturns.size()) return BigDecimal.ONE;
⋮----
for (int i = 0; i < assetReturns.size(); i++) {
double mRet = marketReturns.get(i);
⋮----
downAsset.add(assetReturns.get(i));
downMarket.add(mRet);
⋮----
if (downAsset.size() < 2) return calculateBeta(assetReturns, marketReturns);
⋮----
double meanAsset = downAsset.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
double meanMarket = downMarket.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
⋮----
for (int i = 0; i < downAsset.size(); i++) {
double devAsset = downAsset.get(i) - meanAsset;
double devMarket = downMarket.get(i) - meanMarket;
⋮----
public static AntigravitySummary analyzePortfolioFactors(
⋮----
boolean isCorrection = marketDrawdownPct.compareTo(new BigDecimal("5.0")) >= 0;
⋮----
for (Map.Entry<String, List<Double>> entry : assetReturnsMap.entrySet()) {
List<Double> returns = entry.getValue();
⋮----
if (!returns.isEmpty()) {
int start = Math.max(0, returns.size() - 30);
⋮----
for (int i = start; i < returns.size(); i++) {
compound *= (1.0 + returns.get(i));
⋮----
twr30dMap.put(entry.getKey(), twr);
allTwr30d.add(twr);
⋮----
double meanTwr30d = allTwr30d.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
⋮----
double stdDevTwr30d = (allTwr30d.size() > 1) ? Math.sqrt(varianceSum / (allTwr30d.size() - 1)) : 1.0;
⋮----
String assetId = entry.getKey();
⋮----
BigDecimal beta = calculateBeta(returns, marketReturns);
BigDecimal downsideBeta = calculateDownsideBeta(returns, marketReturns);
⋮----
double twr30 = twr30dMap.getOrDefault(assetId, 0.0);
⋮----
int start = Math.max(0, returns.size() - 90);
⋮----
BigDecimal twr30dBd = BigDecimal.valueOf(twr30 * 100.0).setScale(2, RoundingMode.HALF_UP);
BigDecimal twr90dBd = BigDecimal.valueOf(twr90 * 100.0).setScale(2, RoundingMode.HALF_UP);
BigDecimal zScoreBd = BigDecimal.valueOf(zScore).setScale(2, RoundingMode.HALF_UP);
⋮----
boolean isAntigravity = downsideBeta.compareTo(new BigDecimal("0.75")) < 0
&& zScoreBd.compareTo(new BigDecimal("0.50")) > 0
⋮----
antigravityList.add(new AssetFactorScore(assetId, assetNamesMap.getOrDefault(assetId, assetId), beta, downsideBeta, zScoreBd, twr30dBd, twr90dBd, true, recommendation));
} else if (downsideBeta.compareTo(new BigDecimal("0.75")) < 0) {
⋮----
} else if (zScoreBd.compareTo(new BigDecimal("0.50")) > 0) {
⋮----
scores.add(new AssetFactorScore(
⋮----
assetNamesMap.getOrDefault(assetId, assetId),
⋮----
return new AntigravitySummary(
```

## File: src/main/java/com/portfolioos/core/valuation/BucketEngine.java
```java
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
String nameUpper = assetName.toUpperCase();
AssetCategory category = TaxClassifier.detectCategory(assetId, assetName);
⋮----
if (nameUpper.contains("ARBITRAGE") || nameUpper.contains("LIQUID") ||
nameUpper.contains("OVERNIGHT") || nameUpper.contains("TREASURY") ||
⋮----
if (nameUpper.contains("SMALL") || nameUpper.contains("MICRO") || nameUpper.contains("SMALLCAP")) {
⋮----
public static RebalanceEngineResult evaluateRebalance(
⋮----
return evaluateRebalance(openLots, List.of(), navMap, currentDate, benchmarkCurrent, benchmarkRollingHigh, targets, fiscalYear);
⋮----
for (Bucket b : Bucket.values()) {
bucketValues.put(b, BigDecimal.ZERO);
bucketAssetLots.put(b, new HashMap<>());
⋮----
Bucket bucket = classifyAssetToBucket(lot.assetId(), lot.assetName());
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
BucketTarget tgt = targetMap.getOrDefault(bucket, new BucketTarget(bucket, new BigDecimal("25.0"), new BigDecimal("5.0")));
BigDecimal drift = curPct.subtract(tgt.targetPct());
boolean isDrifted = drift.abs().compareTo(tgt.bandPct()) > 0;
⋮----
bucketStatuses.add(new BucketStatus(
bucket, curVal, curPct, tgt.targetPct(), drift, isDrifted
⋮----
// Drawdown trigger
⋮----
if (benchmarkRollingHigh.compareTo(BigDecimal.ZERO) > 0) {
drawdownPct = benchmarkRollingHigh.subtract(benchmarkCurrent)
.multiply(new BigDecimal("100"))
.divide(benchmarkRollingHigh, 2, RoundingMode.HALF_UP);
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
Map<String, List<Lot>> bucketLots = bucketAssetLots.get(status.bucket());
if (bucketLots.isEmpty()) continue;
String firstAssetId = bucketLots.keySet().iterator().next();
List<Lot> firstLots = bucketLots.get(firstAssetId);
String assetName = firstLots.get(0).assetName();
⋮----
BigDecimal nav = navMap.getOrDefault(firstAssetId, firstLots.get(0).costPerUnit());
⋮----
AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), currentDate);
boolean isLtcg = TaxClassifier.classifyTaxTerm(category, holdingDays, fiscalYear, true) == TaxTerm.LONG_TERM;
BigDecimal gain = nav.subtract(lot.costPerUnit()).multiply(lot.remainingUnits()).max(BigDecimal.ZERO);
⋮----
if (gain.compareTo(BigDecimal.ZERO) > 0) {
BigDecimal rate = isLtcg ? rules.equityLtcgRate() : rules.equityStcgRate();
⋮----
if (isLtcg && exemptionRemaining.compareTo(BigDecimal.ZERO) > 0) {
if (taxableGain.compareTo(exemptionRemaining) <= 0) {
exemptionRemaining = exemptionRemaining.subtract(taxableGain);
⋮----
taxableGain = taxableGain.subtract(exemptionRemaining);
⋮----
estTaxDrag = estTaxDrag.add(taxableGain.multiply(rate));
taxTerms.add(isLtcg ? "LTCG @ " + rules.equityLtcgRate().multiply(new BigDecimal("100")) + "% (Sec 112A exemption applied)"
: "STCG @ " + rules.equityStcgRate().multiply(new BigDecimal("100")) + "%");
⋮----
status.bucket(),
⋮----
diffValue.abs(),
⋮----
estTaxDrag.setScale(2, RoundingMode.HALF_UP),
taxTerms.stream().distinct().collect(Collectors.joining(", "))
⋮----
} else if (diffValue.compareTo(BigDecimal.ZERO) < 0) {
⋮----
String firstAssetId = !bucketLots.isEmpty() ? bucketLots.keySet().iterator().next() : "BUY_" + status.bucket().name();
String assetName = (!bucketLots.isEmpty() && bucketLots.containsKey(firstAssetId))
? bucketLots.get(firstAssetId).get(0).assetName() : "Core Holding for " + status.bucket().name();
⋮----
return new RebalanceEngineResult(
```

## File: src/main/java/com/portfolioos/core/valuation/ConsolidationRebalanceEngine.java
```java
public class ConsolidationRebalanceEngine {
⋮----
CORE_SIP_WEIGHTS.put("NIFTY_LARGEMIDCAP_250", new Pair<>("Nifty LargeMidcap 250 Index Fund", new BigDecimal("33.0")));
CORE_SIP_WEIGHTS.put("PARAG_PARIKH_FLEXI", new Pair<>("Parag Parikh Flexi Cap Fund", new BigDecimal("24.0")));
CORE_SIP_WEIGHTS.put("ARBITRAGE_LIQUID", new Pair<>("Kotak Equity Arbitrage / Liquid Buffer", new BigDecimal("16.0")));
CORE_SIP_WEIGHTS.put("NIFTY_VALUE_30", new Pair<>("Nifty200 Value 30 Index Fund", new BigDecimal("11.0")));
CORE_SIP_WEIGHTS.put("NIFTY_MOMENTUM_50", new Pair<>("Nifty200 Momentum Quality 50 Index Fund", new BigDecimal("9.0")));
CORE_SIP_WEIGHTS.put("NIFTY_SMALLCAP_250", new Pair<>("Nifty Smallcap 250 Index Fund", new BigDecimal("7.0")));
⋮----
public static ConsolidationPreviewResult calculateConsolidation(
⋮----
TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
⋮----
List<String> phaseOutKeywords = List.of("EQUAL", "MIDCAP150", "NIFTY100_EW", "MIDCAP_150");
⋮----
List<Lot> phaseOutLots = openLots.stream().filter(lot ->
phaseOutKeywords.stream().anyMatch(kw ->
lot.assetId().toUpperCase().contains(kw) || lot.assetName().toUpperCase().contains(kw)
⋮----
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
BigDecimal effectiveProceeds = totalProceeds.compareTo(BigDecimal.ZERO) > 0 ? totalProceeds : new BigDecimal("256200.00");
⋮----
for (Map.Entry<String, Pair<String, BigDecimal>> entry : CORE_SIP_WEIGHTS.entrySet()) {
String id = entry.getKey();
Pair<String, BigDecimal> pair = entry.getValue();
BigDecimal weightPct = pair.second();
BigDecimal deployAmt = effectiveProceeds.multiply(weightPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
proRataAllocations.add(new ExistingSipAllocation(
id, pair.first(), weightPct, deployAmt
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
⋮----
private static class Pair<A, B> {
⋮----
public A first() { return first; }
public B second() { return second; }
```

## File: src/main/java/com/portfolioos/core/valuation/HarvestAdvisor.java
```java
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
```

## File: src/main/java/com/portfolioos/core/valuation/RebalanceEngine.java
```java
public class RebalanceEngine {
⋮----
public static RebalancePreviewResult calculateRebalancePreview(
⋮----
TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
⋮----
LocalDate today = LocalDate.now();
⋮----
// Sort: loss-making first (0), then long-term (1), then short-term (2)
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
BigDecimal stcgRate = (category == AssetCategory.EQUITY) ? rules.equityStcgRate() : new BigDecimal("0.30"); // slab default
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
return new RebalancePreviewResult(
⋮----
private static long getThresholdDays(AssetCategory category, TaxRulesConfig rules) {
⋮----
case EQUITY -> rules.equityLtcgThresholdDays();
case GOLD_SILVER, INTERNATIONAL, SGB -> rules.goldInternationalThresholdDays();
```

## File: src/main/java/com/portfolioos/core/xirr/CashFlow.java
```java
BigDecimal amount // negative for investments, positive for inflows / current valuation
```

## File: src/main/java/com/portfolioos/core/xirr/XirrEngine.java
```java
public class XirrEngine {
⋮----
public double calculateXirr(List<CashFlow> cashFlows) {
if (cashFlows.size() < 2) return 0.0;
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
// Newton-Raphson solver
⋮----
double f = npv(rate, dates, amounts);
double df = dNpv(rate, dates, amounts);
⋮----
if (Math.abs(df) > 1e-10) {
⋮----
if (Math.abs(nextRate - rate) < 1e-7) {
⋮----
if (Double.isNaN(result) || Double.isInfinite(result)) return 0.0;
return Math.max(-99.0, result);
⋮----
// Bracketed Bisection Fallback
⋮----
double flow = npv(low, dates, amounts);
double fhigh = npv(high, dates, amounts);
⋮----
double fmid = npv(mid, dates, amounts);
if (Math.abs(fmid) < 1e-7 || (high - low) < 1e-7) {
return Math.max(-99.0, mid * 100.0);
⋮----
return Math.max(-99.0, ((low + high) / 2.0) * 100.0);
⋮----
if (Double.isNaN(rawResult) || Double.isInfinite(rawResult)) return 0.0;
return Math.max(-99.0, rawResult);
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
```

## File: src/main/java/com/portfolioos/core/CoreApplication.java
```java
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
```

## File: src/main/resources/static/src/js/modules/insurance.js
```javascript
export async function fetchInsuranceChecklist()
⋮----
export function renderInsuranceBanner(data)
⋮----
export async function toggleInsuranceStatus(id, status)
```

## File: src/main/resources/static/src/js/modules/portfolio.js
```javascript
export function updatePortfolioSummary(summary)
⋮----
export function renderHoldingsTable(holdings)
⋮----
window.toggleLotDetails = (idx) =>
⋮----
export function renderPieChart(containerId, data)
⋮----
export function renderAllocationChart(allocations)
⋮----
export function renderCategoryChart(catAllocations)
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
export async function fetchBucketRebalance()
⋮----
export function renderBucketRebalance(data)
```

## File: src/main/resources/static/src/js/modules/tax.js
```javascript
export async function fetchTaxMetrics()
⋮----
export function updateExemptionMeter(data)
⋮----
export function updateReportMetrics(report)
⋮----
export async function fetchDecisionRadar()
⋮----
export function renderDecisionRadar(opportunities, ladder, antigravityData)
⋮----
export async function fetchRealizedLog()
⋮----
export function renderRealizedLogTable(logs)
```

## File: src/main/resources/static/src/js/api.js
```javascript
export function getAuthHeaders(extraHeaders =
⋮----
export async function fetchJson(url, options =
```

## File: src/main/resources/static/src/js/state.js
```javascript
export function setCurrentFy(fy)
⋮----
export function getCurrentFy()
```

## File: src/main/resources/static/src/js/utils.js
```javascript
export function formatINR(val, round = true)
⋮----
export function showToast(message, type = 'success')
```

## File: src/main/resources/static/src/app.js
```javascript
// Tab Switching Handler
⋮----
// Export ZIP button listener
⋮----
// Rebalance Slider listener
⋮----
// File Upload listener
⋮----
async function fetchLiveMetrics()
⋮----
// Global debounced resize listener for ECharts
```

## File: src/main/resources/static/src/style.css
```css
:root {
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
/* Top Metrics Cards Row */
.top-metrics-grid {
⋮----
.glass-card {
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
  jackson:
    property-naming-strategy: SNAKE_CASE
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

ENTRYPOINT ["java", "--add-opens=java.base/java.nio=ALL-UNNAMED", "-jar", "app.jar"]
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
