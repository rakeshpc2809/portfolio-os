package com.portfolioos.core.service;

import com.portfolioos.core.dtos.ReportDtos.*;
import com.portfolioos.core.fire.FireTracker;
import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.FifoMatcher;
import com.portfolioos.core.matcher.FundTierClassifier;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.valuation.BucketEngine;
import com.portfolioos.core.valuation.RebalanceWaterfallEngine;
import com.portfolioos.core.xirr.CashFlow;
import com.portfolioos.core.xirr.XirrEngine;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.portfolioos.core.nav.MfApiNavDownloader;
import com.portfolioos.core.persistence.MarketDataRepository;

@Service
public class PortfolioValuationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PortfolioValuationService.class);

    private final LedgerCacheService cacheService;
    private final XirrEngine xirrEngine = new XirrEngine();
    private final MarketDataRepository marketDataRepository;

    @Autowired
    public PortfolioValuationService(
        LedgerCacheService cacheService,
        MarketDataRepository marketDataRepository
    ) {
        this.cacheService = cacheService;
        this.marketDataRepository = marketDataRepository;
    }

    private String fmt(BigDecimal val) {
        if (val == null) {
            return "0.00";
        }
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static boolean isNavStale(LocalDate navDate, LocalDate asOfDate) {
        if (navDate == null) {
            return true;
        }
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }
        if (navDate.isAfter(asOfDate)) {
            return false;
        }
        // Count elapsed business days (excluding Saturday and Sunday)
        int businessDays = 0;
        LocalDate curr = navDate.plusDays(1);
        while (!curr.isAfter(asOfDate)) {
            java.time.DayOfWeek dow = curr.getDayOfWeek();
            if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) {
                businessDays++;
            }
            curr = curr.plusDays(1);
        }
        // If more than 3 business days elapsed without an updated NAV, or more than 7 calendar days total, flag as stale
        long calendarDays = ChronoUnit.DAYS.between(navDate, asOfDate);
        return businessDays > 3 || calendarDays > 7;
    }

    public MarketDataRepository.BenchmarkLevelMetrics getLatestBenchmarkMetrics(String benchmarkSymbol) {
        return marketDataRepository.getBenchmarkLevelMetrics(benchmarkSymbol);
    }

    public record PortfolioValuationMetrics(
        BigDecimal totalInvested,
        BigDecimal totalCurrentValue,
        BigDecimal totalGain,
        double overallXirr,
        String formattedXirr,
        int distinctAssetCount
    ) {}

    public PortfolioValuationMetrics computeValuationMetrics(String fy) {
        LedgerCacheService.CachedLedgerState state = cacheService != null ? cacheService.getCachedState() : null;
        List<TaxEvent> allEvents = state != null && state.events() != null ? state.events() : Collections.emptyList();
        List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
        Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;

        for (Lot lot : openLots) {
            totalInvested = totalInvested.add(lot.totalCostBasis());
            BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "PortfolioValuationService.getPortfolioSummary");
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
        double xirr = cashflows.size() >= 2 ? xirrEngine.calculateXirr(cashflows) : 0.0;

        long distinctAssetCount = openLots.stream().map(Lot::assetId).distinct().count();

        return new PortfolioValuationMetrics(
            totalInvested,
            totalCurrentValue,
            totalGain,
            xirr,
            String.format("%.2f%%", xirr),
            (int) distinctAssetCount
        );
    }

    public double calculateHoldingXirr(String assetId, List<TaxEvent> allEvents, LocalDate asOfDate, BigDecimal currentValuation) {
        List<CashFlow> holdingCashflows = new ArrayList<>();
        for (TaxEvent event : allEvents) {
            if (event.assetId().equals(assetId)) {
                if (event.eventType() == EventType.ACQUISITION || event.eventType() == EventType.SIP_INSTALMENT) {
                    holdingCashflows.add(new CashFlow(event.eventDate(), event.grossAmount().negate()));
                } else if (event.eventType() == EventType.DISPOSAL || event.eventType() == EventType.SGB_MATURITY) {
                    holdingCashflows.add(new CashFlow(event.eventDate(), event.grossAmount()));
                }
            }
        }
        holdingCashflows.add(new CashFlow(asOfDate, currentValuation));
        return holdingCashflows.size() >= 2 ? xirrEngine.calculateXirr(holdingCashflows) : 0.0;
    }

    public List<com.portfolioos.core.dtos.ReportDtos.BucketStatusDto> getBucketAllocationStatuses() {
        if (cacheService != null && cacheService.getCachedState() == null) {
            cacheService.refreshCacheInBackground();
        }
        LedgerCacheService.CachedLedgerState state = cacheService != null ? cacheService.getCachedState() : null;
        List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
        List<MatchedLot> matchedLots = state != null && state.fifoResult() != null ? state.fifoResult().matchedLots() : Collections.emptyList();
        Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();

        List<BucketEngine.BucketTarget> activeTargets = 
            com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now());
        
        String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());

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

        BucketEngine.RebalanceEngineResult result = 
            BucketEngine.evaluateRebalance(
                openLots, matchedLots, navMap, LocalDate.now(), BigDecimal.ZERO, BigDecimal.ZERO,
                activeTargets, currentFy, activeOrPreferredAssetIds
            );

        List<TaxEvent> allEvents = (state != null && state.events() != null) ? state.events() : Collections.emptyList();
        LocalDate today = LocalDate.now();

        return result.bucketStatuses().stream()
            .map(s -> new com.portfolioos.core.dtos.ReportDtos.BucketStatusDto(
                s.bucket().name(),
                fmt(s.currentValue()),
                s.currentPct().doubleValue(),
                s.targetPct().doubleValue(),
                s.driftPct().doubleValue(),
                s.isDrifted(),
                (s.funds() != null ? s.funds() : Collections.<BucketEngine.BucketFundConstituent>emptyList()).stream().map(f -> {
                    double fundXirr = calculateHoldingXirr(f.isin(), allEvents, today, f.currentValue());
                    return new com.portfolioos.core.dtos.ReportDtos.BucketFundItemDto(
                        f.isin(),
                        f.fundName(),
                        fmt(f.currentValue()),
                        f.currentValue().doubleValue(),
                        f.units().doubleValue(),
                        f.allocationPctInBucket().doubleValue(),
                        f.allocationPctInPortfolio().doubleValue(),
                        f.targetWeightInBucket() != null ? f.targetWeightInBucket().doubleValue() : null,
                        fmt(f.investedValue()),
                        (f.unrealizedGain().compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + fmt(f.unrealizedGain()),
                        f.gainPct().doubleValue(),
                        BigDecimal.valueOf(fundXirr).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                        f.isPreferred()
                    );
                }).toList()
            ))
            .toList();
    }

    public PortfolioSummaryResponse getPortfolioSummary(String fy) {
        PortfolioValuationMetrics metrics = computeValuationMetrics(fy);
        List<HoldingDetailDto> holdings = getHoldings();
        int staleNavCount = (int) holdings.stream().filter(HoldingDetailDto::navStale).count();
        return new PortfolioSummaryResponse(
            fmt(metrics.totalInvested()),
            fmt(metrics.totalCurrentValue()),
            fmt(metrics.totalGain()),
            metrics.formattedXirr(),
            metrics.distinctAssetCount(),
            staleNavCount
        );
    }

    public NetWorthTrendResponse getNetWorthTrend() {
        List<MarketDataRepository.NetWorthPoint> rawTrend = marketDataRepository.getDailyNetWorthTrend();
        if (rawTrend.isEmpty()) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
                    Set<String> isins = state.events().stream().map(TaxEvent::assetId).collect(Collectors.toSet());
                    MfApiNavDownloader downloader = new MfApiNavDownloader();
                    for (String isin : isins) {
                        downloader.downloadHistoricalNavsForIsin(isin, marketDataRepository);
                    }
                    marketDataRepository.invalidateTrendCache();
                } catch (Exception e) {
                    log.warn("Background historical NAV backfill error: {}", e.getMessage());
                }
            });
        }
        List<String> dates = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<Double> investedValues = new ArrayList<>();
        List<Boolean> isEstimated = new ArrayList<>();
        double totalSumValuation = 0.0;
        double totalSumRealNavValuation = 0.0;

        for (MarketDataRepository.NetWorthPoint p : rawTrend) {
            dates.add(p.date());
            values.add(p.valuation());
            investedValues.add(p.invested());
            isEstimated.add(p.isEstimated());
            totalSumValuation += p.valuation();
            totalSumRealNavValuation += p.realNavValuation();
        }

        double coveragePct = (totalSumValuation > 0) ? (totalSumRealNavValuation / totalSumValuation) * 100.0 : 100.0;
        return new NetWorthTrendResponse(dates, values, investedValues, isEstimated, coveragePct);
    }

    public List<HoldingDetailDto> getHoldings() {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();
        Map<String, LocalDate> navDateMap = (state != null && state.navDateMap() != null) ? state.navDateMap() : Collections.emptyMap();
        LocalDate today = LocalDate.now();

        BigDecimal totalCurrentValAll = BigDecimal.ZERO;
        Map<String, List<Lot>> grouped = openLots.stream().collect(Collectors.groupingBy(Lot::assetId));

        List<HoldingDetailDto> holdingDetails = new ArrayList<>();

        for (Map.Entry<String, List<Lot>> entry : grouped.entrySet()) {
            String assetId = entry.getKey();
            List<Lot> lots = entry.getValue();

            String assetName = lots.get(0).assetName();
            BigDecimal currentNav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, assetId, assetName, "PortfolioValuationService.getHoldings");
            LocalDate navDate = navDateMap.get(assetId);
            boolean isStale = isNavStale(navDate, today);
            String currentFy = com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(today);
            com.portfolioos.core.rules.TaxRulesConfig taxRules = com.portfolioos.core.rules.TaxRulesLoader.loadRules(currentFy);
            AssetCategory catEnum = TaxClassifier.detectCategory(assetId, assetName);
            boolean isListed = TaxClassifier.isListed(assetId, assetName);
            String category = catEnum.name();

            BigDecimal assetInvested = BigDecimal.ZERO;
            BigDecimal assetCurrentVal = BigDecimal.ZERO;

            List<OpenLotDto> lotDtos = new ArrayList<>();
            for (Lot lot : lots) {
                BigDecimal lotCurrentVal = lot.remainingUnits().multiply(currentNav);
                BigDecimal lotGain = lotCurrentVal.subtract(lot.effectiveTotalCostBasis(currentNav));
                assetInvested = assetInvested.add(lot.totalCostBasis());
                assetCurrentVal = assetCurrentVal.add(lotCurrentVal);

                long holdingDays = ChronoUnit.DAYS.between(lot.acquisitionDate(), today);
                com.portfolioos.core.model.TaxTerm taxTerm = TaxClassifier.classifyTaxTerm(catEnum, holdingDays, currentFy, isListed, lot.acquisitionDate(), null);
                boolean isLtcg = taxTerm == com.portfolioos.core.model.TaxTerm.LONG_TERM;
                long thresholdDays = catEnum == AssetCategory.EQUITY ? taxRules.equityLtcgThresholdDays() : (isListed ? taxRules.equityLtcgThresholdDays() : taxRules.goldInternationalThresholdDays());
                long daysToLtcg = isLtcg ? 0L : Math.max(0L, thresholdDays - holdingDays);

                String classification = TaxClassifier.detectTaxClassification(catEnum);
                BigDecimal estimatedTaxDrag = TaxClassifier.computeEstimatedTaxDrag(catEnum, taxTerm, lotGain, taxRules);
                boolean isHarvestCandidate = lotGain.compareTo(BigDecimal.ZERO) < 0;

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
                    isLtcg,
                    classification,
                    estimatedTaxDrag.toPlainString(),
                    isHarvestCandidate
                ));
            }

            BigDecimal assetGain = assetCurrentVal.subtract(assetInvested);
            BigDecimal gainPct = BigDecimal.ZERO;
            if (assetInvested.compareTo(BigDecimal.ZERO) > 0) {
                gainPct = assetGain.divide(assetInvested, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
            }

            totalCurrentValAll = totalCurrentValAll.add(assetCurrentVal);

            com.portfolioos.core.nav.AmfiTerFetcher.TerMetadata terMeta = com.portfolioos.core.nav.AmfiTerFetcher.resolveTer(assetId, assetName, catEnum);

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
                lotDtos,
                terMeta.expenseRatio(),
                terMeta.terStatus(),
                terMeta.terAsOfDate()
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
                h.lots(),
                h.expenseRatio(),
                h.terStatus(),
                h.terAsOfDate()
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
        return getRebalancePreview(targetAmount, fy, BigDecimal.ZERO);
    }

    public RebalancePreviewDto getRebalancePreview(BigDecimal targetAmount, String fy, BigDecimal reservedExemption) {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
        Map<String, BigDecimal> navMap = state.navMap();

        ExemptionTracker.ExemptionStatus status = ExemptionTracker.calculateExemptionStatus(matchedLots, fy);
        BigDecimal remExemption = new BigDecimal(status.exemptionRemaining());

        RebalanceWaterfallEngine.WaterfallResult result = RebalanceWaterfallEngine.buildTrimWaterfall(
            null, targetAmount, openLots, navMap, remExemption, true, LocalDate.now(), fy
        );

        List<RebalanceLotDto> selectedDtos = result.steps().stream().map(s -> new RebalanceLotDto(
            s.assetName(),
            fmt(s.unitsSold()),
            fmt(s.proceeds()),
            fmt(s.realizedGain()),
            s.taxTerm(),
            fmt(s.taxDrag()),
            s.tier() != null ? s.tier().displayName() : "Section 112A LTCG (Taxable)"
        )).toList();

        BigDecimal actualAmount = result.satisfiedAmount();
        BigDecimal totalGain = result.steps().stream().map(RebalanceWaterfallEngine.WaterfallStep::realizedGain).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal effectiveRate = (totalGain.compareTo(BigDecimal.ZERO) > 0)
            ? result.totalTaxDrag().divide(totalGain, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
            : BigDecimal.ZERO;

        return new RebalancePreviewDto(
            fmt(result.targetAmount()),
            fmt(actualAmount),
            fmt(totalGain),
            fmt(result.totalTaxDrag()),
            String.format("%.2f%%", effectiveRate.doubleValue()),
            fmt(result.ltcgExemptionConsumed()),
            selectedDtos,
            "RTA Chronological FIFO compliant liquidation waterfall"
        );
    }

    public GoalSummaryResponse getGoalSummary() {
        return new GoalSummaryResponse("0.00", "0.00", "0.00", Map.of(), List.of());
    }

    public FireSummaryResponse getFireSummary() {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        if (state == null) {
            cacheService.refreshCacheInBackground();
            state = cacheService.getCachedState();
        }
        List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
        Map<String, BigDecimal> navMap = state != null && state.navMap() != null ? state.navMap() : Collections.emptyMap();

        FireTracker.FireSummary fire = FireTracker.calculateFireSummary(openLots, navMap, LocalDate.now());

        BigDecimal deterministicFv = fire.projectedCorpusAtTargetAge();
        double successRate = deterministicFv.compareTo(fire.requiredCorpus()) >= 0 ? 100.0 : 0.0;
        BigDecimal mcMedian = deterministicFv;
        BigDecimal mcP10 = deterministicFv;
        String ds = "DETERMINISTIC_PROJECTION";
        String dsLabel = "Deterministic Compound Growth Model";

        List<FireScenarioDto> scenarioDtos = fire.scenarios().stream().map(s -> {
            BigDecimal monthly = s.monthlyExpenseToday();
            BigDecimal annual = monthly.multiply(BigDecimal.valueOf(12));
            BigDecimal targetCorp = annual.divide(new BigDecimal("0.03"), 2, RoundingMode.HALF_UP);
            return new FireScenarioDto(
                s.id(),
                s.label(),
                fmt(monthly),
                fmt(monthly),
                fmt(annual),
                fmt(targetCorp),
                "3.0",
                s.active()
            );
        }).toList();

        List<Object> trajectories = Collections.emptyList();

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
            scenarioDtos,
            successRate,
            fmt(mcMedian),
            fmt(mcP10),
            ds,
            dsLabel,
            trajectories
        );
    }

    public BucketRebalanceResponse getBucketRebalance(BigDecimal benchmarkCurrent, BigDecimal benchmarkRollingHigh, String fy) {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();
        BucketEngine.RebalanceEngineResult result = BucketEngine.evaluateRebalance(
            openLots, state.fifoResult().matchedLots(), navMap, LocalDate.now(), benchmarkCurrent, benchmarkRollingHigh, com.portfolioos.core.rules.BucketConfigLoader.getActiveBucketTargets(LocalDate.now()), fy
        );

        List<BucketStatusDto> statuses = result.bucketStatuses().stream().map(s -> new BucketStatusDto(
            s.bucket().name(),
            fmt(s.currentValue()),
            s.currentPct().doubleValue(),
            s.targetPct().doubleValue(),
            s.driftPct().doubleValue(),
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

    public WaterfallResponse getRebalanceWaterfall(BucketEngine.Bucket bucket, BigDecimal amount, String fy) {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
        Map<String, BigDecimal> navMap = state.navMap();

        List<Lot> bucketLots = openLots.stream().filter(l -> 
            BucketEngine.classifyAssetToBucket(l.assetId(), l.assetName()) == bucket
        ).toList();

        ExemptionTracker.ExemptionStatus exStatus = ExemptionTracker.calculateExemptionStatus(matchedLots, fy);
        BigDecimal remExemption = new BigDecimal(exStatus.exemptionRemaining());

        com.portfolioos.core.valuation.RebalanceWaterfallEngine.WaterfallResult result = 
            com.portfolioos.core.valuation.RebalanceWaterfallEngine.buildTrimWaterfall(
                bucket, amount, bucketLots, navMap, remExemption, false, LocalDate.now(), fy
            );

        List<WaterfallStepDto> stepDtos = result.steps().stream().map(s -> new WaterfallStepDto(
            s.tier().name(),
            s.lotId(),
            s.assetId(),
            s.assetName(),
            s.unitsSold().toPlainString(),
            fmt(s.proceeds()),
            fmt(s.realizedGain()),
            s.taxTerm(),
            fmt(s.taxDrag())
        )).toList();

        return new WaterfallResponse(
            bucket.name(),
            fmt(result.targetAmount()),
            fmt(result.satisfiedAmount()),
            fmt(result.deferredAmount()),
            result.deferralReason(),
            stepDtos,
            fmt(result.totalTaxDrag()),
            fmt(result.ltcgExemptionConsumed())
        );
    }

    public static final Map<String, Long> BENCHMARK_SCHEME_CODES = Map.of(
        "NIFTY_50_TRI", 120716L,
        "NIFTY_500_TRI", 147625L,
        "NIFTY_LARGEMIDCAP_250", 152482L,
        "NIFTY_200_VALUE_30", 152936L,
        "NIFTY_200_MOMENTUM_30", 148703L,
        "NIFTY_SMALLCAP_250", 147623L
    );

    public static String normalizeBenchmarkId(String benchmarkId) {
        if (benchmarkId == null || benchmarkId.isBlank()) return "NIFTY_500_TRI";
        String normalized = benchmarkId.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        if (normalized.equals("NIFTY_50") || normalized.equals("NIFTY50") || normalized.equals("NIFTY_50_INDEX")) {
            return "NIFTY_50_TRI";
        }
        if (normalized.equals("NIFTY_500") || normalized.equals("NIFTY500") || normalized.equals("NIFTY_500_INDEX")) {
            return "NIFTY_500_TRI";
        }
        return normalized;
    }

    public synchronized void ensureBenchmarkData(String benchmarkId) {
        if (benchmarkId == null || benchmarkId.isBlank()) return;
        String normalized = normalizeBenchmarkId(benchmarkId);
        if (!marketDataRepository.hasBenchmarkData(normalized)) {
            refreshBenchmarkData(normalized);
        }
    }

    public synchronized void refreshBenchmarkData(String benchmarkId) {
        if (benchmarkId == null || benchmarkId.isBlank()) return;
        String normalized = normalizeBenchmarkId(benchmarkId);
        Long schemeCode = BENCHMARK_SCHEME_CODES.get(normalized);
        if (schemeCode != null) {
            log.info("Fetching benchmark series from MFAPI for {} (Scheme {})...", normalized, schemeCode);
            try {
                new MfApiNavDownloader().downloadBenchmarkData(normalized, schemeCode, marketDataRepository);
            } catch (Exception e) {
                log.error("Failed to fetch benchmark {}: {}", normalized, e.getMessage());
            }
        }
    }

    public Map<String, Object> getFundRegistry() {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        if (state == null) {
            cacheService.refreshCacheInBackground();
            state = cacheService.getCachedState();
        }
        List<Lot> openLots = (state != null && state.fifoResult() != null) ? state.fifoResult().openLots() : Collections.emptyList();
        List<TaxEvent> events = (state != null && state.events() != null) ? state.events() : Collections.emptyList();
        Map<String, BigDecimal> navMap = (state != null && state.navMap() != null) ? state.navMap() : Collections.emptyMap();
        Set<String> activeAssetIds = FundTierClassifier.findActiveAssetIds(openLots, LocalDate.now());

        // Extract ground-truth scheme names directly from ingested tax_events
        Map<String, String> dynamicNames = new HashMap<>();
        Map<String, String> assetCategories = new HashMap<>();

        for (TaxEvent event : events) {
            if (event.assetId() != null && event.assetName() != null && !event.assetName().isBlank()) {
                dynamicNames.putIfAbsent(event.assetId(), cleanSchemeName(event.assetName()));
            }
        }

        Map<String, BigDecimal> fundValuations = new HashMap<>();
        for (Lot lot : openLots) {
            BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "PortfolioValuationService.getFundRegistry");
            BigDecimal val = lot.remainingUnits() != null ? lot.remainingUnits().multiply(nav) : BigDecimal.ZERO;
            fundValuations.put(lot.assetId(), fundValuations.getOrDefault(lot.assetId(), BigDecimal.ZERO).add(val));
        }

        List<Map<String, Object>> funds = new ArrayList<>();
        for (Map.Entry<String, String> entry : dynamicNames.entrySet()) {
            String isin = entry.getKey();
            String rawName = entry.getValue();
            String name = cleanSchemeName(rawName);
            boolean active = activeAssetIds.contains(isin);
            BigDecimal valuation = fundValuations.getOrDefault(isin, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            String category = TaxClassifier.detectCategory(isin, rawName).name();

            String holdingStatus = active ? "ACTIVE_SIP" : (valuation.compareTo(BigDecimal.ZERO) > 0 ? "LEGACY_HOLDING" : "FULLY_EXITED");

            Map<String, Object> fundObj = new HashMap<>();
            fundObj.put("isin", isin);
            fundObj.put("name", name);
            fundObj.put("raw_name", rawName);
            fundObj.put("category", category);
            fundObj.put("active", active);
            fundObj.put("holding_status", holdingStatus);
            fundObj.put("current_valuation", valuation);
            funds.add(fundObj);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("funds", funds);
        return response;
    }

    private static String cleanSchemeName(String raw) {
        if (raw == null || raw.isBlank()) return "Unknown Fund";
        return raw.replaceAll("(?i)\\s*-?\\s*Direct\\s+Plan.*", "")
                  .replaceAll("(?i)\\s*-?\\s*Direct\\s+Growth.*", "")
                  .replaceAll("(?i)\\s*\\(Non\\s+Demat\\)", "")
                  .replaceAll("(?i)GROWTH PLAN GROWTH OPTION", "")
                  .replaceAll("(?i)DIRECT GROWTH PLAN", "")
                  .trim();
    }
}
