package com.portfolioos.core.controllers;

import com.portfolioos.core.common.PortfolioConstants;
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
import com.portfolioos.core.rpc.QuantSidecarClient;
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
    private static final Set<String> ALLOWED_PARQUET_TABLES = Set.of(
        "fund_holdings", "open_lots", "benchmark_history", "nav_history"
    );

    private final LedgerCacheService cacheService;
    private final com.portfolioos.core.service.PortfolioValuationService valuationService;
    private final DuckDbProjector duckDbProjector;
    private final QuantSidecarClient quantSidecarClient;
    private final com.portfolioos.core.service.CloudBundleExporter cloudBundleExporter;

    @Autowired
    public SyncController(
        LedgerCacheService cacheService,
        com.portfolioos.core.service.PortfolioValuationService valuationService,
        DuckDbProjector duckDbProjector,
        QuantSidecarClient quantSidecarClient,
        com.portfolioos.core.service.CloudBundleExporter cloudBundleExporter
    ) {
        this.cacheService = cacheService;
        this.valuationService = valuationService;
        this.duckDbProjector = duckDbProjector;
        this.quantSidecarClient = quantSidecarClient;
        this.cloudBundleExporter = cloudBundleExporter;
    }

    public SyncController(
        LedgerCacheService cacheService,
        com.portfolioos.core.service.PortfolioValuationService valuationService,
        DuckDbProjector duckDbProjector,
        QuantSidecarClient quantSidecarClient
    ) {
        this(cacheService, valuationService, duckDbProjector, quantSidecarClient, null);
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
        Map<String, BigDecimal> peakNavMap = duckDbProjector.get52WeekPeakNavs(heldIsins);

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
            com.portfolioos.core.model.TaxTerm taxTerm = TaxClassifier.classifyTaxTerm(category, holdingDays, resolvedFy, isListed);
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

        // 2. Quant Sidecar Intelligence (from real DuckDB NAV time-series with dates)
        try {
            Map<String, NavHistorySeriesEntry> navHistorySeries = duckDbProjector.getNavHistorySeriesWithDates(heldIsins);
            if (!navHistorySeries.isEmpty()) {
                Map<String, Map<String, Object>> quantMetrics = quantSidecarClient.computeQuantMetricsWithDates(navHistorySeries);
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

                    double ddThreshold = switch (bucket) {
                        case "Debt & Liquid" -> PortfolioConstants.DRAWDOWN_TIER_1_PCT / 100.0;
                        case "Core Equity", "Flexi Cap", "Large & Midcap", "Equal Weight Index", "Gold & Commodities" -> PortfolioConstants.DRAWDOWN_TIER_2_PCT / 100.0;
                        default -> PortfolioConstants.DRAWDOWN_TIER_HIGH_VOLATILITY_PCT / 100.0; // Small Cap, Microcap, Sectoral, Midcap, Factor Value/Momentum
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
        } catch (Throwable ex) {
            System.err.println("Non-critical Quant Flight RPC signal extraction warning: " + ex.getMessage());
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

        long now = System.currentTimeMillis();
        SyncInfoDto syncInfo = new SyncInfoDto(
            now / 1000,
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
            hasStaleNav
        );

        List<NetWorthPointDto> netWorthHistory = duckDbProjector.getDailyNetWorthTrend().stream()
            .map(p -> new NetWorthPointDto(p.date(), p.valuation(), p.invested()))
            .toList();

        String derivedTriggerType;
        if (requestedTrigger != null && !requestedTrigger.isBlank()) {
            derivedTriggerType = requestedTrigger.toUpperCase();
        } else {
            derivedTriggerType = "DRIFT";
        }

        DuckDbProjector.BenchmarkLevelMetrics benchMetrics = valuationService.getLatestBenchmarkMetrics("NIFTY_500_TRI");
        BigDecimal benchCurrent = benchMetrics != null && benchMetrics.currentLevel() != null ? BigDecimal.valueOf(benchMetrics.currentLevel()) : null;
        BigDecimal benchHigh = benchMetrics != null && benchMetrics.rollingHigh() != null ? BigDecimal.valueOf(benchMetrics.rollingHigh()) : null;

        com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto rebalancePlan = com.portfolioos.core.service.RebalancePlanEngine.buildPreviewPlan(
            openLots, matchedLots, navMap, LocalDate.now(), benchCurrent, benchHigh,
            com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), resolvedFy, derivedTriggerType, null
        );

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
        java.io.File[] candidates = new java.io.File[] {
            new java.io.File("data/daily_brief_cache.json"),
            new java.io.File("../data/daily_brief_cache.json"),
            new java.io.File("../../data/daily_brief_cache.json"),
            new java.io.File("/app/data/daily_brief_cache.json")
        };
        for (java.io.File f : candidates) {
            if (f.exists() && f.isFile()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    DailyBriefDto dto = mapper.readValue(f, DailyBriefDto.class);
                    // Zero-Trust Validation: verify cached brief doesn't hallucinate or diverge from live net worth
                    if (dto != null && dto.metrics() != null && dto.metrics().containsKey("net_worth")) {
                        Object nwObj = dto.metrics().get("net_worth");
                        if (nwObj instanceof Number nwNum && currentNetWorth != null && currentNetWorth.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal cachedNw = BigDecimal.valueOf(nwNum.doubleValue());
                            BigDecimal diff = cachedNw.subtract(currentNetWorth).abs();
                            BigDecimal tolerance = currentNetWorth.multiply(new BigDecimal("0.15"));
                            if (diff.compareTo(tolerance) > 0) {
                                // Stale mock or divergent brief detected — reject to prevent user-facing hallucination
                                return null;
                            }
                        }
                    }
                    return dto;
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return null;
    }

    @GetMapping("/rebalance/plan")
    public ResponseEntity<com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto> getRebalancePlan(
        @RequestParam(value = "trigger", required = false, defaultValue = "INDUCED") String triggerType
    ) {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
        List<MatchedLot> matchedLots = state != null && state.fifoResult() != null ? state.fifoResult().matchedLots() : Collections.emptyList();
        Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();

        String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());
        DuckDbProjector.BenchmarkLevelMetrics bench = valuationService.getLatestBenchmarkMetrics("NIFTY_500_TRI");
        BigDecimal benchCurrent = bench != null && bench.currentLevel() != null ? BigDecimal.valueOf(bench.currentLevel()) : null;
        BigDecimal benchHigh = bench != null && bench.rollingHigh() != null ? BigDecimal.valueOf(bench.rollingHigh()) : null;

        com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto plan = com.portfolioos.core.service.RebalancePlanEngine.buildPreviewPlan(
            openLots, matchedLots, navMap, LocalDate.now(), benchCurrent, benchHigh,
            com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), currentFy, triggerType, null
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
        String triggerType = req != null && req.containsKey("trigger") ? req.get("trigger").toString() : "SCHEDULED";
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
        List<MatchedLot> matchedLots = state != null && state.fifoResult() != null ? state.fifoResult().matchedLots() : Collections.emptyList();
        Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();

        String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());
        DuckDbProjector.BenchmarkLevelMetrics bench = valuationService.getLatestBenchmarkMetrics("NIFTY_500_TRI");
        BigDecimal benchCurrent = bench != null && bench.currentLevel() != null ? BigDecimal.valueOf(bench.currentLevel()) : null;
        BigDecimal benchHigh = bench != null && bench.rollingHigh() != null ? BigDecimal.valueOf(bench.rollingHigh()) : null;

        com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto plan = com.portfolioos.core.service.RebalancePlanEngine.buildPlan(
            openLots, matchedLots, navMap, LocalDate.now(), benchCurrent, benchHigh,
            com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), currentFy, triggerType, null
        );
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
        DuckDbProjector.BenchmarkLevelMetrics bench = valuationService.getLatestBenchmarkMetrics("NIFTY_500_TRI");
        BigDecimal benchCurrent = bench != null && bench.currentLevel() != null ? BigDecimal.valueOf(bench.currentLevel()) : null;
        BigDecimal benchHigh = bench != null && bench.rollingHigh() != null ? BigDecimal.valueOf(bench.rollingHigh()) : null;

        com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto plan = com.portfolioos.core.service.RebalancePlanEngine.buildPreviewPlan(
            openLots, matchedLots, navMap, LocalDate.now(), benchCurrent, benchHigh,
            com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), currentFy, "MANUAL_LUMPSUM", amount, includeRebalance
        );
        return ResponseEntity.ok(plan);
    }

    @GetMapping(value = "/bundle/encrypted", produces = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getEncryptedBundle() {
        if (cloudBundleExporter == null) {
            return ResponseEntity.status(503).build();
        }
        byte[] existing = cloudBundleExporter.getExistingEncryptedBundle();
        if (existing == null) {
            try {
                UnidirectionalSyncSnapshot snapshot = getSnapshot(null, null).getBody();
                if (snapshot != null) {
                    existing = cloudBundleExporter.exportAndEncryptBundle(snapshot);
                }
            } catch (Exception e) {
                log.error("Failed on-demand bundle export: {}", e.getMessage(), e);
            }
        }
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"portfolio_state.enc\"")
            .body(existing);
    }

    @PostMapping("/bundle/export")
    public ResponseEntity<Map<String, Object>> triggerBundleExport() {
        if (cloudBundleExporter == null) {
            return ResponseEntity.status(503).body(Map.of("status", "ERROR", "message", "Cloud bundle exporter not initialized"));
        }
        try {
            UnidirectionalSyncSnapshot snapshot = getSnapshot(null, null).getBody();
            if (snapshot == null) {
                return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "message", "Unable to build snapshot"));
            }
            byte[] encrypted = cloudBundleExporter.exportAndEncryptBundle(snapshot);
            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "bytes", encrypted.length,
                "file", "data/portfolio_state.enc"
            ));
        } catch (Exception e) {
            log.error("Bundle export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshNavs() {
        cacheService.invalidateCache();
        LedgerCacheService.CachedLedgerState refreshed = cacheService.getCachedState();
        return ResponseEntity.ok(Map.of(
            "status", "REFRESHED",
            "nav_count", refreshed.navMap() != null ? refreshed.navMap().size() : 0,
            "refreshed_at", java.time.LocalDateTime.now().format(ISO_FORMATTER)
        ));
    }

    @GetMapping("/parquet")
    public ResponseEntity<byte[]> getParquetTable(@RequestParam(defaultValue = "fund_holdings") String table) {
        if (duckDbProjector == null) {
            return ResponseEntity.status(503).build();
        }
        String cleanTable = table.replaceAll("[^a-zA-Z0-9_]", "").toLowerCase(Locale.ROOT);
        if (cleanTable.isBlank()) {
            cleanTable = "fund_holdings";
        }
        if (!ALLOWED_PARQUET_TABLES.contains(cleanTable)) {
            return ResponseEntity.badRequest().build();
        }
        File tempFile = null;
        try {
            tempFile = File.createTempFile("parquet_" + cleanTable + "_", ".parquet");
            if ("open_lots".equalsIgnoreCase(cleanTable)) {
                duckDbProjector.syncOpenLots(cacheService.getCachedState().fifoResult().openLots());
            }
            duckDbProjector.exportTableToParquet(cleanTable, tempFile);
            byte[] bytes = Files.readAllBytes(tempFile.toPath());
            return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/vnd.apache.parquet")
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + cleanTable + ".parquet\"")
                .body(bytes);
        } catch (Exception e) {
            log.error("Failed to export table {} to parquet: {}", cleanTable, e.getMessage());
            return ResponseEntity.internalServerError().build();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    @GetMapping("/parquet-bundle")
    public ResponseEntity<byte[]> getParquetBundle() {
        if (duckDbProjector == null) {
            return ResponseEntity.status(503).build();
        }
        List<File> tempFiles = new ArrayList<>();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // 1. open_lots.parquet (PII stripped - no folio_no)
                File openLotsFile = File.createTempFile("open_lots_", ".parquet");
                tempFiles.add(openLotsFile);
                duckDbProjector.exportQueryToParquet(
                    "SELECT id, asset_id, asset_name, isin, event_date, TRY_CAST(units AS DOUBLE) AS units, TRY_CAST(price_per_unit AS DOUBLE) AS price_per_unit, TRY_CAST(gross_amount AS DOUBLE) AS gross_amount FROM projected_events WHERE event_type IN ('ACQUISITION', 'SIP_INSTALMENT', 'BONUS')",
                    openLotsFile
                );
                zos.putNextEntry(new ZipEntry("open_lots.parquet"));
                zos.write(Files.readAllBytes(openLotsFile.toPath()));
                zos.closeEntry();

                // 2. fund_holdings.parquet
                File fundHoldingsFile = File.createTempFile("fund_holdings_", ".parquet");
                tempFiles.add(fundHoldingsFile);
                duckDbProjector.exportTableToParquet("fund_holdings", fundHoldingsFile);
                zos.putNextEntry(new ZipEntry("fund_holdings.parquet"));
                zos.write(Files.readAllBytes(fundHoldingsFile.toPath()));
                zos.closeEntry();

                // 3. benchmark_history.parquet
                File benchmarkFile = File.createTempFile("benchmark_history_", ".parquet");
                tempFiles.add(benchmarkFile);
                duckDbProjector.exportTableToParquet("benchmark_history", benchmarkFile);
                zos.putNextEntry(new ZipEntry("benchmark_history.parquet"));
                zos.write(Files.readAllBytes(benchmarkFile.toPath()));
                zos.closeEntry();
            }

            byte[] zipBytes = baos.toByteArray();
            return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"parquet_bundle.zip\"")
                .body(zipBytes);
        } catch (Exception e) {
            log.error("Failed to export parquet bundle: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        } finally {
            for (File f : tempFiles) {
                if (f != null && f.exists()) {
                    f.delete();
                }
            }
        }
    }
}

