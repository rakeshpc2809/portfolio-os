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
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;
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
        String currentFy = (fy != null && !fy.isBlank()) ? fy : TaxRulesLoader.detectFiscalYear(LocalDate.now());
        return ExemptionTracker.calculateExemptionStatus(matchedLots, currentFy);
    }

    public TaxReportExporter.Itr2ScheduleCgReport generateItr2Report(String fy) {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        List<MatchedLot> matchedLots = fifoMatcher.processEvents(allEvents).matchedLots();
        String currentFy = (fy != null && !fy.isBlank()) ? fy : TaxRulesLoader.detectFiscalYear(LocalDate.now());
        return TaxReportExporter.generateItr2Report(matchedLots, currentFy);
    }

    public List<HarvestOpportunityDto> getHarvestOpportunities() {
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        List<Lot> openLots = fifoMatcher.processEvents(allEvents).openLots();
        Map<String, BigDecimal> navMap = amfiSync.getNavMap();
        String currentFy = TaxRulesLoader.detectFiscalYear(LocalDate.now());

        // Assume zero exemption used so far for simple harvest opportunity advice
        HarvestAdvisor.TaxHarvestResult plan = HarvestAdvisor.generateHarvestPlan(
            openLots, navMap, BigDecimal.ZERO, currentFy
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
        String currentFy = TaxRulesLoader.detectFiscalYear(today);
        TaxRulesConfig rules = TaxRulesLoader.loadRules(currentFy);

        List<MaturationLadderDto> ladder = new ArrayList<>();

        for (Lot lot : openLots) {
            AssetCategory cat = TaxClassifier.detectCategory(lot.assetId(), lot.assetName());
            boolean isListed = TaxClassifier.isListed(lot.assetId(), lot.assetName());
            long reqDays = (cat == AssetCategory.EQUITY || isListed) 
                ? rules.equityLtcgThresholdDays() 
                : rules.goldInternationalThresholdDays();
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
