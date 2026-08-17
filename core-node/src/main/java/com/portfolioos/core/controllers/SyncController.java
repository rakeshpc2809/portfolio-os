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
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy,
        @RequestParam(value = "trigger", required = false) String requestedTrigger
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

        BigDecimal totalCurrentVal = openLots.stream()
            .map(l -> l.remainingUnits().multiply(navMap.getOrDefault(l.assetId(), l.costPerUnit())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Asset Allocation Drift Signal
        BucketEngine.RebalanceEngineResult bucketStatus = BucketEngine.evaluateRebalance(
            openLots, state.fifoResult().matchedLots(), navMap, today, null, null, BucketEngine.DEFAULT_TARGETS, fy
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

        BigDecimal personalNetWorthAth = netWorthHistory.stream()
            .map(p -> BigDecimal.valueOf(p.valuation()))
            .max(BigDecimal::compareTo)
            .orElse(totalPortfolioCurrentVal);

        String derivedTriggerType;
        if (requestedTrigger != null && !requestedTrigger.isBlank()) {
            derivedTriggerType = requestedTrigger.toUpperCase();
        } else {
            derivedTriggerType = "DRIFT";
        }

        com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto rebalancePlan = com.portfolioos.core.service.RebalancePlanEngine.buildPlan(
            openLots, matchedLots, navMap, LocalDate.now(), null, null,
            com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), fy, derivedTriggerType, null
        );

        return ResponseEntity.ok(new UnidirectionalSyncSnapshot(
            syncInfo, holdings, taxLots, radarSignals, netWorthHistory, rebalancePlan
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

    @GetMapping("/rebalance/plan")
    public ResponseEntity<com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto> getRebalancePlan(
        @RequestParam(value = "trigger", required = false, defaultValue = "INDUCED") String triggerType
    ) {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
        List<MatchedLot> matchedLots = state != null && state.fifoResult() != null ? state.fifoResult().matchedLots() : Collections.emptyList();
        Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();

        BigDecimal totalCurrentVal = BigDecimal.ZERO;
        for (Lot lot : openLots) {
            BigDecimal nav = navMap.getOrDefault(lot.assetId(), lot.costPerUnit());
            totalCurrentVal = totalCurrentVal.add(lot.remainingUnits().multiply(nav));
        }

        List<DuckDbProjector.NetWorthPoint> trend = duckDbProjector.getDailyNetWorthTrend();
        double peak = trend.stream().mapToDouble(DuckDbProjector.NetWorthPoint::valuation).max().orElse(totalCurrentVal.doubleValue());
        BigDecimal personalNetWorthAth = BigDecimal.valueOf(peak);
        if (personalNetWorthAth.compareTo(totalCurrentVal) < 0) {
            personalNetWorthAth = totalCurrentVal;
        }

        String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());
        com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto plan = com.portfolioos.core.service.RebalancePlanEngine.buildPreviewPlan(
            openLots, matchedLots, navMap, LocalDate.now(), null, null,
            com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), currentFy, triggerType, null
        );
        return ResponseEntity.ok(plan);
    }

    @GetMapping("/portfolio/bucket-allocation")
    public ResponseEntity<List<com.portfolioos.core.dtos.ReportDtos.BucketStatusDto>> getBucketAllocation() {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
        List<MatchedLot> matchedLots = state != null && state.fifoResult() != null ? state.fifoResult().matchedLots() : Collections.emptyList();
        Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();

        List<com.portfolioos.core.valuation.BucketEngine.BucketTarget> activeTargets = 
            com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now());
        
        String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());

        // Construct preferred / active asset IDs set
        Set<String> activeOrPreferredAssetIds = new HashSet<>();
        com.portfolioos.core.rules.BucketConfigLoader.BucketRulesConfig config = 
            com.portfolioos.core.rules.BucketConfigLoader.loadConfig();
        if (config != null && !config.versions().isEmpty()) {
            com.portfolioos.core.rules.BucketConfigLoader.BucketTargetVersion activeVer = 
                com.portfolioos.core.rules.BucketConfigLoader.getActiveVersion(LocalDate.now());
            for (var tc : activeVer.targets()) {
                if (tc.preferredFunds() != null) {
                    for (var pf : tc.preferredFunds()) {
                        activeOrPreferredAssetIds.add(pf.fundId());
                    }
                }
            }
        }

        com.portfolioos.core.valuation.BucketEngine.RebalanceEngineResult result = 
            com.portfolioos.core.valuation.BucketEngine.evaluateRebalance(
                openLots, matchedLots, navMap, LocalDate.now(), BigDecimal.ZERO, BigDecimal.ZERO,
                activeTargets, currentFy, activeOrPreferredAssetIds
            );

        List<com.portfolioos.core.dtos.ReportDtos.BucketStatusDto> dtos = result.bucketStatuses().stream()
            .map(s -> new com.portfolioos.core.dtos.ReportDtos.BucketStatusDto(
                s.bucket().name(),
                s.currentValue().setScale(2, java.math.RoundingMode.HALF_UP).toString(),
                s.currentPct().setScale(2, java.math.RoundingMode.HALF_UP).toString(),
                s.targetPct().setScale(2, java.math.RoundingMode.HALF_UP).toString(),
                s.driftPct().setScale(2, java.math.RoundingMode.HALF_UP).toString(),
                s.isDrifted()
            ))
            .toList();

        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/rebalance/simulate-lumpsum")
    public ResponseEntity<com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto> simulateLumpsum(
        @RequestBody Map<String, Object> req
    ) {
        BigDecimal amount = req.containsKey("amount") ? new BigDecimal(req.get("amount").toString()) : new BigDecimal("50000.00");
        boolean includeRebalance = req.containsKey("includeRebalance") && Boolean.parseBoolean(req.get("includeRebalance").toString());
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
        List<MatchedLot> matchedLots = state != null && state.fifoResult() != null ? state.fifoResult().matchedLots() : Collections.emptyList();
        Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();

        BigDecimal totalVal = openLots.stream()
            .map(l -> l.remainingUnits().multiply(navMap.getOrDefault(l.assetId(), l.costPerUnit())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());

        com.portfolioos.core.dtos.RebalancePlanDtos.RebalancePlanDto plan = com.portfolioos.core.service.RebalancePlanEngine.buildPlan(
            openLots, matchedLots, navMap, LocalDate.now(), null, null,
            com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), currentFy, "MANUAL_LUMPSUM", amount, includeRebalance
        );
        return ResponseEntity.ok(plan);
    }

    @GetMapping("/config/bucket-targets")
    public ResponseEntity<com.portfolioos.core.rules.BucketConfigLoader.BucketRulesConfig> getBucketTargetsSync() {
        return ResponseEntity.ok(com.portfolioos.core.rules.BucketConfigLoader.loadConfig());
    }

    @PutMapping("/config/bucket-targets")
    public ResponseEntity<?> updateBucketTargetsSync(@RequestBody Map<String, Object> req) {
        try {
            String effectiveFrom = (String) req.getOrDefault("effectiveFrom", req.get("effective_from"));
            List<Map<String, Object>> targetsList = (List<Map<String, Object>>) req.get("targets");

            if (targetsList == null || targetsList.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing 'targets' array in request body"));
            }

            List<com.portfolioos.core.rules.BucketConfigLoader.BucketTargetConfig> newTargets = targetsList.stream().map(tMap -> {
                String bName = (String) tMap.get("bucket");
                double tPct = ((Number) tMap.get("targetPct") != null ? (Number) tMap.get("targetPct") : (Number) tMap.get("target_pct")).doubleValue();
                double bPct = ((Number) tMap.get("bandPct") != null ? (Number) tMap.get("bandPct") : (Number) tMap.get("band_pct")).doubleValue();

                List<com.portfolioos.core.rules.BucketConfigLoader.PreferredFundConfig> prefFunds = new ArrayList<>();
                if (tMap.containsKey("preferredFunds") || tMap.containsKey("preferred_funds")) {
                    List<Map<String, Object>> pfList = (List<Map<String, Object>>) tMap.getOrDefault("preferredFunds", tMap.get("preferred_funds"));
                    for (Map<String, Object> pfMap : pfList) {
                        prefFunds.add(new com.portfolioos.core.rules.BucketConfigLoader.PreferredFundConfig(
                            (String) pfMap.get("fundId"),
                            (String) pfMap.get("fundName"),
                            ((Number) pfMap.get("allocationWeight")).doubleValue()
                        ));
                    }
                } else {
                    prefFunds = com.portfolioos.core.rules.BucketConfigLoader.getDefaultPreferredFundsForBucket(bName);
                }
                return new com.portfolioos.core.rules.BucketConfigLoader.BucketTargetConfig(bName, tPct, bPct, prefFunds);
            }).toList();

            com.portfolioos.core.rules.BucketConfigLoader.updateBucketTargets(newTargets, effectiveFrom);
            return ResponseEntity.ok(com.portfolioos.core.rules.BucketConfigLoader.loadConfig());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to update bucket targets: " + e.getMessage()));
        }
    }
}
