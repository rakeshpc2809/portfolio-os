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
