package com.portfolioos.core.controllers;

import com.portfolioos.core.common.PortfolioConstants;
import com.portfolioos.core.dtos.SyncDtos.*;
import com.portfolioos.core.fire.FireTracker;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.persistence.MarketDataRepository;
import com.portfolioos.core.persistence.MarketDataRepository.NavHistorySeriesEntry;
import com.portfolioos.core.ports.EventStorePort;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.service.LedgerCacheService;
import com.portfolioos.core.valuation.BucketEngine;
import com.portfolioos.core.valuation.HarvestAdvisor;
import com.portfolioos.core.xirr.CashFlow;
import com.portfolioos.core.xirr.XirrEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SyncController.class);
    private static final java.time.format.DateTimeFormatter ISO_FORMATTER = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final LedgerCacheService cacheService;
    private final com.portfolioos.core.service.PortfolioValuationService valuationService;
    private final MarketDataRepository marketDataRepository;
    private final com.portfolioos.core.persistence.TriggerHistoryRepository triggerHistoryRepository;

    @Autowired
    public SyncController(
        LedgerCacheService cacheService,
        com.portfolioos.core.service.PortfolioValuationService valuationService,
        MarketDataRepository marketDataRepository,
        @Autowired(required = false) com.portfolioos.core.persistence.TriggerHistoryRepository triggerHistoryRepository
    ) {
        this.cacheService = cacheService;
        this.valuationService = valuationService;
        this.marketDataRepository = marketDataRepository;
        this.triggerHistoryRepository = triggerHistoryRepository;
    }

    public SyncController(
        LedgerCacheService cacheService,
        com.portfolioos.core.service.PortfolioValuationService valuationService,
        MarketDataRepository marketDataRepository
    ) {
        this(cacheService, valuationService, marketDataRepository, null);
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
        @RequestParam(value = "fy", required = false) String fy,
        @RequestParam(value = "trigger", required = false) String requestedTrigger
    ) {
        String resolvedFy = (fy != null && !fy.isBlank()) ? fy : com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<TaxEvent> allEvents = state.events();
        List<Lot> openLots = state.fifoResult().openLots();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
        Map<String, BigDecimal> navMap = state.navMap();
        String ledgerHash = state.ledgerHash();

        LocalDate today = LocalDate.now();
        Locale inLocale = new Locale("en", "IN");
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(inLocale);

        // Collect held ISINs
        Set<String> heldIsins = openLots.stream().map(Lot::assetId).collect(Collectors.toSet());
        Map<String, BigDecimal> peakNavMap = marketDataRepository.get52WeekPeakNavs(heldIsins);

        // Calculate overall XIRR & Totals via canonical PortfolioValuationService
        com.portfolioos.core.service.PortfolioValuationService.PortfolioValuationMetrics valMetrics = valuationService.computeValuationMetrics(resolvedFy);
        BigDecimal totalPortfolioInvested = valMetrics.totalInvested();
        BigDecimal totalPortfolioCurrentVal = valMetrics.totalCurrentValue();
        BigDecimal unrealizedGain = valMetrics.totalGain();
        double overallXirr = valMetrics.overallXirr();

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

            BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, assetId, assetName, "SyncController.getSnapshot (holding " + assetId + ")");
            BigDecimal holdingCurVal = totalUnits.multiply(nav);
            double holdingXirr = valuationService.calculateHoldingXirr(assetId, allEvents, today, holdingCurVal);

            AssetCategory catEnum = TaxClassifier.detectCategory(assetId, assetName);
            var terMeta = com.portfolioos.core.nav.AmfiTerFetcher.resolveTer(assetId, assetName, catEnum);

            BigDecimal peakNav = peakNavMap.getOrDefault(assetId, nav).max(nav);
            double drawdownPct = peakNav.compareTo(BigDecimal.ZERO) > 0
                ? nav.subtract(peakNav).divide(peakNav, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                : 0.0;

            com.portfolioos.core.valuation.ExitLoadCalculator.ExitLoadSummary exitSummary =
                com.portfolioos.core.valuation.ExitLoadCalculator.calculateForAsset(assetId, assetName, lots, nav, today);

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
                currencyFormat.format(totalCost),
                terMeta.expenseRatio(),
                terMeta.terStatus(),
                terMeta.terAsOfDate(),
                exitSummary.exitLoadFreeUnits().doubleValue(),
                exitSummary.exitLoadFreeValue().doubleValue(),
                exitSummary.lockedUnits().doubleValue(),
                peakNav.doubleValue(),
                nav.doubleValue(),
                drawdownPct
            ));
        }

        // Construct FlatTaxLotDto
        List<FlatTaxLotDto> taxLots = new ArrayList<>();
        for (Lot lot : openLots) {
            AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
            boolean isListed = TaxClassifier.isListed(lot.assetId(), lot.assetName());
            long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
            com.portfolioos.core.model.TaxTerm taxTerm = TaxClassifier.classifyTaxTerm(category, holdingDays, resolvedFy, isListed, lot.acquisitionDate(), today);
            boolean isLongTerm = taxTerm == com.portfolioos.core.model.TaxTerm.LONG_TERM;

            com.portfolioos.core.rules.TaxRulesConfig taxRules = com.portfolioos.core.rules.TaxRulesLoader.loadRules(resolvedFy);
            long thresholdDays = category == AssetCategory.EQUITY ? taxRules.equityLtcgThresholdDays() : (isListed ? taxRules.equityLtcgThresholdDays() : taxRules.goldInternationalThresholdDays());
            long daysToLtcg = isLongTerm ? 0L : Math.max(0L, thresholdDays - holdingDays);

            BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot.assetId(), lot.assetName(), "SyncController.getSnapshot (lot " + lot.lotId() + ")");
            BigDecimal lotCurrentVal = lot.remainingUnits().multiply(nav);
            BigDecimal effectiveCostBasis = lot.effectiveTotalCostBasis(nav);
            BigDecimal lotGain = lotCurrentVal.subtract(effectiveCostBasis);

            String classification = TaxClassifier.detectTaxClassification(category);
            BigDecimal estimatedTaxDrag = TaxClassifier.computeEstimatedTaxDrag(category, taxTerm, lotGain, taxRules);
            boolean isHarvestCandidate = lotGain.compareTo(BigDecimal.ZERO) < 0;

            taxLots.add(new FlatTaxLotDto(
                lot.assetId(),
                lot.acquisitionDate().toString(),
                lot.remainingUnits().doubleValue(),
                classification,
                isLongTerm,
                lot.isGrandfathered() ? lot.fmv20180131().doubleValue() : null,
                lot.costPerUnit().doubleValue(),
                holdingDays,
                daysToLtcg,
                estimatedTaxDrag.doubleValue(),
                isHarvestCandidate
            ));
        }

        // Generate Verified Priority AI Radar Signals
        List<RadarSignalDto> radarSignals = new ArrayList<>();

        // 1. Priority Tax Loss Harvesting Signals
        ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(matchedLots, resolvedFy);
        HarvestAdvisor.TaxHarvestResult harvestPlan = HarvestAdvisor.generateHarvestPlan(
            openLots, navMap, new BigDecimal(exStatus.exemptionUsed()), resolvedFy
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

        // 2. LTCG Maturation Ladder Signal
        Lot maturingLot = null;
        long minDaysToLtcg = 9999L;

        for (Lot lot : openLots) {
            long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
            AssetCategory cat = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
            boolean isListed = TaxClassifier.isListed(lot.assetId(), lot.assetName());
            long thresholdDays = (cat == AssetCategory.EQUITY || isListed) ? 365L : 730L;
            long daysToLtcg = Math.max(0L, thresholdDays - holdingDays);
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
            openLots, state.fifoResult().matchedLots(), navMap, today, null, null, com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(today), resolvedFy
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

        int staleNavCount = valuationService.getPortfolioSummary(resolvedFy).staleNavCount();
        boolean hasStaleNav = staleNavCount > 0;
        if (hasStaleNav) {
            radarSignals.add(0, new RadarSignalDto(
                "STALE_NAV",
                "AMFI Data Freshness",
                "STALE NAV DETECTED (" + staleNavCount + " FUNDS)",
                staleNavCount + " fund(s) have not received an updated NAV for more than 3 business days. Valuations reflect last known closing NAV.",
                "WARNING",
                staleNavCount + " Stale"
            ));
        }

        long lastSyncTimestamp = state.lastNavFreshnessTimestamp() > 0 ? state.lastNavFreshnessTimestamp() : System.currentTimeMillis();
        boolean ledgerHmacVerified = state.ledgerHmacVerified();
        double remainingHeadroom = Double.parseDouble(exStatus.exemptionRemaining());
        double usedHeadroom = Double.parseDouble(exStatus.exemptionUsed());

        SyncInfoDto syncInfo = new SyncInfoDto(
            lastSyncTimestamp / 1000,
            ledgerHash,
            LocalDate.now().atStartOfDay().toString(),
            resolvedFy,
            BigDecimal.valueOf(overallXirr).setScale(2, RoundingMode.HALF_UP).doubleValue(),
            String.format("%.2f%%", overallXirr),
            totalPortfolioInvested.doubleValue(),
            totalPortfolioCurrentVal.doubleValue(),
            unrealizedGain.doubleValue(),
            currencyFormat.format(totalPortfolioCurrentVal),
            currencyFormat.format(totalPortfolioInvested),
            (unrealizedGain.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + currencyFormat.format(unrealizedGain),
            staleNavCount,
            hasStaleNav,
            "AVAILABLE",
            ledgerHmacVerified,
            java.time.Instant.ofEpochMilli(lastSyncTimestamp).toString(),
            remainingHeadroom,
            usedHeadroom
        );

        List<NetWorthPointDto> netWorthHistory = marketDataRepository.getDailyNetWorthTrend().stream()
            .map(p -> new NetWorthPointDto(p.date(), p.valuation(), p.invested()))
            .toList();

        String derivedTriggerType;
        if (requestedTrigger != null && !requestedTrigger.isBlank()) {
            derivedTriggerType = requestedTrigger.toUpperCase();
        } else {
            derivedTriggerType = null;
        }

        MarketDataRepository.BenchmarkLevelMetrics benchMetrics = valuationService.getLatestBenchmarkMetrics("NIFTY_500_TRI");
        BigDecimal benchCurrent = benchMetrics != null && benchMetrics.currentLevel() != null ? BigDecimal.valueOf(benchMetrics.currentLevel()) : null;
        BigDecimal benchHigh = benchMetrics != null && benchMetrics.rollingHigh() != null ? BigDecimal.valueOf(benchMetrics.rollingHigh()) : null;

        com.portfolioos.core.service.RebalanceTriggerEvaluator evaluator = new com.portfolioos.core.service.RebalanceTriggerEvaluator(
            triggerHistoryRepository
        );
        com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto rebalancePlan = null;
        try {
            rebalancePlan = com.portfolioos.core.service.RebalancePlanEngine.buildPreviewPlan(
                openLots, matchedLots, navMap, LocalDate.now(), benchCurrent, benchHigh,
                com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), resolvedFy, derivedTriggerType, null, evaluator
            );
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("CRITICAL BENCHMARK FAILURE")) {
                log.warn("Benchmark data unavailable during /sync/snapshot — gracefully degrading drawdown status: {}", ex.getMessage());
                syncInfo = syncInfo.withDrawdownStatus("UNAVAILABLE");
                BigDecimal actualHeadroom = null;
                try {
                    if (exStatus != null) {
                        actualHeadroom = new BigDecimal(exStatus.exemptionRemaining());
                    }
                } catch (Exception ignored) {}
                rebalancePlan = com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto.fallbackWithUnavailableDrawdown(actualHeadroom);
            } else {
                throw ex;
            }
        }

        List<com.portfolioos.core.dtos.ReportDtos.BucketStatusDto> bucketAllocation = valuationService.getBucketAllocationStatuses();

        List<RadarSignalDto> filteredSignals = radarSignals.stream()
            .filter(s -> "WARNING".equalsIgnoreCase(s.severity()) || "CRITICAL".equalsIgnoreCase(s.severity()))
            .toList();

        DailyBriefDto dailyBrief = loadDailyBriefCache(BigDecimal.valueOf(syncInfo.currentValue()));

        return ResponseEntity.ok(new UnidirectionalSyncSnapshot(
            syncInfo, holdings, taxLots, filteredSignals, netWorthHistory, rebalancePlan, bucketAllocation, dailyBrief
        ));
    }

    private DailyBriefDto loadDailyBriefCache(BigDecimal currentNetWorth) {
        return null;
    }

    @GetMapping("/rebalance/plan")
    public ResponseEntity<com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto> getRebalancePlan(
        @RequestParam(value = "trigger", required = false) String triggerType
    ) {
        String effectiveTrigger = (triggerType != null && !triggerType.isBlank() && !"INDUCED".equalsIgnoreCase(triggerType)) ? triggerType : null;
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
        List<MatchedLot> matchedLots = state != null && state.fifoResult() != null ? state.fifoResult().matchedLots() : Collections.emptyList();
        Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();

        String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());
        MarketDataRepository.BenchmarkLevelMetrics bench = valuationService.getLatestBenchmarkMetrics("NIFTY_500_TRI");
        BigDecimal benchCurrent = bench != null && bench.currentLevel() != null ? BigDecimal.valueOf(bench.currentLevel()) : null;
        BigDecimal benchHigh = bench != null && bench.rollingHigh() != null ? BigDecimal.valueOf(bench.rollingHigh()) : null;

        com.portfolioos.core.service.RebalanceTriggerEvaluator evaluator = new com.portfolioos.core.service.RebalanceTriggerEvaluator(
            triggerHistoryRepository
        );
        com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto plan = com.portfolioos.core.service.RebalancePlanEngine.buildPreviewPlan(
            openLots, matchedLots, navMap, LocalDate.now(), benchCurrent, benchHigh,
            com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), currentFy, effectiveTrigger, null, evaluator
        );
        return ResponseEntity.ok(plan);
    }

    @GetMapping("/portfolio/bucket-allocation")
    public ResponseEntity<List<com.portfolioos.core.dtos.ReportDtos.BucketStatusDto>> getBucketAllocation() {
        return ResponseEntity.ok(valuationService.getBucketAllocationStatuses());
    }

    @PostMapping("/rebalance/confirm-execution")
    public ResponseEntity<com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto> confirmRebalanceExecution(
        @RequestBody(required = false) Map<String, Object> req
    ) {
        String triggerType = req != null && req.containsKey("trigger") && req.get("trigger") != null ? req.get("trigger").toString() : null;
        String effectiveTrigger = (triggerType != null && !triggerType.isBlank() && !"INDUCED".equalsIgnoreCase(triggerType)) ? triggerType : null;
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
        List<MatchedLot> matchedLots = state != null && state.fifoResult() != null ? state.fifoResult().matchedLots() : Collections.emptyList();
        Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();

        String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());
        MarketDataRepository.BenchmarkLevelMetrics bench = valuationService.getLatestBenchmarkMetrics("NIFTY_500_TRI");
        BigDecimal benchCurrent = bench != null && bench.currentLevel() != null ? BigDecimal.valueOf(bench.currentLevel()) : null;
        BigDecimal benchHigh = bench != null && bench.rollingHigh() != null ? BigDecimal.valueOf(bench.rollingHigh()) : null;

        com.portfolioos.core.service.RebalanceTriggerEvaluator evaluator = new com.portfolioos.core.service.RebalanceTriggerEvaluator(
            triggerHistoryRepository
        );
        com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto plan = com.portfolioos.core.service.RebalancePlanEngine.buildPreviewPlan(
            openLots, matchedLots, navMap, LocalDate.now(), benchCurrent, benchHigh,
            com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), currentFy, effectiveTrigger, null, evaluator
        );

        // F12: Validate plan digest - MANDATORY on confirmed execution.
        // A missing, blank, or mismatched digest rejects execution with 409 Conflict without side effects.
        String approvedDigest = req != null && req.containsKey("digest") && req.get("digest") != null ? req.get("digest").toString().trim() : null;
        if (approvedDigest == null || approvedDigest.isBlank() || !approvedDigest.equalsIgnoreCase(plan.planDigest())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).build();
        }

        String approvedPlanId = req != null && req.containsKey("planId") && req.get("planId") != null ? req.get("planId").toString().trim() : null;
        if (approvedPlanId != null && !approvedPlanId.isBlank()) {
            plan = plan.withPlanId(approvedPlanId);
        }

        if (triggerHistoryRepository == null) {
            log.error("Cannot confirm rebalance execution: TriggerHistoryRepository is not configured");
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        // F12: Confirm execution snapshot persistence - records trigger and snapshot EXACTLY ONCE under approved planId
        com.portfolioos.core.service.RebalancePlanEngine.confirmPlanExecution(plan, triggerHistoryRepository);

        return ResponseEntity.ok(plan);
    }

    @PostMapping("/rebalance/simulate-lumpsum")
    public ResponseEntity<com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto> simulateLumpsum(
        @RequestBody Map<String, Object> req
    ) {
        BigDecimal amount = req.containsKey("amount") ? new BigDecimal(req.get("amount").toString()) : new BigDecimal("50000.00");
        boolean includeRebalance = (req.containsKey("includeRebalance") && Boolean.parseBoolean(req.get("includeRebalance").toString()))
            || (req.containsKey("include_rebalance") && Boolean.parseBoolean(req.get("include_rebalance").toString()));
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
        List<MatchedLot> matchedLots = state != null && state.fifoResult() != null ? state.fifoResult().matchedLots() : Collections.emptyList();
        Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();

        String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());
        MarketDataRepository.BenchmarkLevelMetrics bench = valuationService.getLatestBenchmarkMetrics("NIFTY_500_TRI");
        BigDecimal benchCurrent = bench != null && bench.currentLevel() != null ? BigDecimal.valueOf(bench.currentLevel()) : null;
        BigDecimal benchHigh = bench != null && bench.rollingHigh() != null ? BigDecimal.valueOf(bench.rollingHigh()) : null;

        com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto plan = com.portfolioos.core.service.RebalancePlanEngine.buildPreviewPlan(
            openLots, matchedLots, navMap, LocalDate.now(), benchCurrent, benchHigh,
            com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), currentFy, "MANUAL_LUMPSUM", amount, includeRebalance
        );
        return ResponseEntity.ok(plan);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshNavs() {
        cacheService.invalidateCache();
        try {
            valuationService.refreshBenchmarkData("NIFTY_500_TRI");
        } catch (Exception e) {
            log.warn("Benchmark data refresh warning: {}", e.getMessage());
        }
        LedgerCacheService.CachedLedgerState refreshed = cacheService.getCachedState();
        return ResponseEntity.ok(Map.of(
            "status", "REFRESHED",
            "nav_count", refreshed.navMap() != null ? refreshed.navMap().size() : 0,
            "refreshed_at", java.time.LocalDateTime.now().format(ISO_FORMATTER)
        ));
    }
}

