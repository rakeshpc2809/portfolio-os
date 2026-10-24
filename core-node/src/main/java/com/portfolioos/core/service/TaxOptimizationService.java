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
import com.portfolioos.core.valuation.RebalanceEngine;
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
    private final com.portfolioos.core.persistence.DuckDbProjector duckDbProjector;
    private final LedgerCacheService cacheService;
    private final AmfiNavSync amfiSync;
    private final FifoMatcher fifoMatcher = new FifoMatcher();

    public TaxOptimizationService(EventStorePort eventStore) {
        this(eventStore, null, null);
    }

    public TaxOptimizationService(EventStorePort eventStore, com.portfolioos.core.persistence.DuckDbProjector duckDbProjector) {
        this(eventStore, duckDbProjector, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TaxOptimizationService(
        EventStorePort eventStore,
        @org.springframework.beans.factory.annotation.Autowired(required = false) com.portfolioos.core.persistence.DuckDbProjector duckDbProjector,
        @org.springframework.beans.factory.annotation.Autowired(required = false) LedgerCacheService cacheService
    ) {
        this.eventStore = eventStore;
        this.duckDbProjector = duckDbProjector;
        this.cacheService = cacheService;
        this.amfiSync = new AmfiNavSync(duckDbProjector);
    }

    private LedgerCacheService.CachedLedgerState resolveState() {
        if (cacheService != null) {
            return cacheService.getCachedState();
        }
        List<TaxEvent> allEvents = eventStore.getAllEvents();
        FifoMatcher.FifoResult res = fifoMatcher.processEvents(allEvents, getJan2018FmvMap());
        Map<String, BigDecimal> navs = amfiSync.getNavMap();
        return new LedgerCacheService.CachedLedgerState(
            allEvents, res, navs, eventStore.getLatestEventHash(),
            System.currentTimeMillis(), "HEALTHY", amfiSync.getNavDateMap()
        );
    }

    public Map<String, BigDecimal> getJan2018FmvMap() {
        if (duckDbProjector != null) {
            return duckDbProjector.getJan2018FmvMap();
        }
        return Map.of();
    }

    private String fmt(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public ExemptionTracker.ExemptionStatus getExemptionStatus(String fy) {
        LedgerCacheService.CachedLedgerState state = resolveState();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
        String currentFy = (fy != null && !fy.isBlank()) ? fy : TaxRulesLoader.detectFiscalYear(LocalDate.now());
        return ExemptionTracker.calculateExemptionStatus(matchedLots, currentFy);
    }

    public TaxReportExporter.Itr2ScheduleCgReport generateItr2Report(String fy) {
        LedgerCacheService.CachedLedgerState state = resolveState();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();
        String currentFy = (fy != null && !fy.isBlank()) ? fy : TaxRulesLoader.detectFiscalYear(LocalDate.now());
        return TaxReportExporter.generateItr2Report(matchedLots, currentFy);
    }

    public List<HarvestOpportunityDto> getHarvestOpportunities() {
        LedgerCacheService.CachedLedgerState state = resolveState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();
        String currentFy = TaxRulesLoader.detectFiscalYear(LocalDate.now());

        ExemptionTracker.ExemptionStatus status = getExemptionStatus(currentFy);
        BigDecimal usedExemption = new BigDecimal(status.exemptionUsed());

        HarvestAdvisor.TaxHarvestResult plan = HarvestAdvisor.generateHarvestPlan(
            openLots, navMap, usedExemption, currentFy
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
        LedgerCacheService.CachedLedgerState state = resolveState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();
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
                BigDecimal nav = com.portfolioos.core.valuation.NavResolver.requireValidNav(navMap, lot, "TaxOptimizationService.getMaturationLadder");
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
        LedgerCacheService.CachedLedgerState state = resolveState();
        List<MatchedLot> matchedLots = state.fifoResult().matchedLots();

        com.portfolioos.core.util.Pair<LocalDate, LocalDate> bounds = TaxRulesLoader.getFiscalYearBounds(fy);
        LocalDate startDate = bounds.first();
        LocalDate endDate = bounds.second();

        List<MatchedLot> fyLots = matchedLots.stream().filter(lot -> 
            !lot.disposalDate().isBefore(startDate) && !lot.disposalDate().isAfter(endDate)
        ).toList();

        Map<String, String> assetNameMap = state.events().stream()
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
        return downloadItr2Files(fy, getJan2018FmvMap());
    }

    public Map<String, String> downloadItr2Files(String fy, Map<String, BigDecimal> fmv2018Map) {
        LedgerCacheService.CachedLedgerState state = resolveState();
        Map<String, BigDecimal> effectiveFmvMap = (fmv2018Map != null && !fmv2018Map.isEmpty()) ? fmv2018Map : getJan2018FmvMap();
        List<MatchedLot> matchedLots;
        if (effectiveFmvMap.equals(getJan2018FmvMap())) {
            matchedLots = state.fifoResult().matchedLots();
        } else {
            matchedLots = fifoMatcher.processEvents(state.events(), effectiveFmvMap).matchedLots();
        }
        Map<String, String> assetNameMap = state.events().stream()
            .collect(Collectors.toMap(TaxEvent::assetId, TaxEvent::assetName, (a, b) -> a));

        return Itr2CsvExporter.exportItr2ScheduleCg(matchedLots, fy, assetNameMap, effectiveFmvMap);
    }

    public TaxReportExporter.Itr2DetailsDto getItr2Details(String fy) {
        return getItr2Details(fy, getJan2018FmvMap());
    }

    public TaxReportExporter.Itr2DetailsDto getItr2Details(String fy, Map<String, BigDecimal> fmv2018Map) {
        LedgerCacheService.CachedLedgerState state = resolveState();
        Map<String, BigDecimal> effectiveFmvMap = (fmv2018Map != null && !fmv2018Map.isEmpty()) ? fmv2018Map : getJan2018FmvMap();
        List<MatchedLot> matchedLots;
        if (effectiveFmvMap.equals(getJan2018FmvMap())) {
            matchedLots = state.fifoResult().matchedLots();
        } else {
            matchedLots = fifoMatcher.processEvents(state.events(), effectiveFmvMap).matchedLots();
        }
        Map<String, String> assetNameMap = state.events().stream()
            .collect(Collectors.toMap(TaxEvent::assetId, TaxEvent::assetName, (a, b) -> a));

        TaxReportExporter.Itr2ScheduleCgReport summary = TaxReportExporter.generateItr2Report(matchedLots, fy);
        List<TaxReportExporter.Schedule112aEntryDto> s112a = Itr2CsvExporter.generateSchedule112aEntries(
            matchedLots, fy, assetNameMap, effectiveFmvMap
        );
        List<TaxReportExporter.ScheduleStcgEntryDto> stcg = Itr2CsvExporter.generateScheduleStcgEntries(
            matchedLots, fy, assetNameMap
        );
        List<RealizedLogDto> realizedLog = getRealizedLog(fy);

        return new TaxReportExporter.Itr2DetailsDto(summary, s112a, stcg, realizedLog);
    }

    public RebalancePreviewDto getTaxOptimalLiquidationPlan(BigDecimal targetAmount, String fy) {
        return getTaxOptimalLiquidationPlan(targetAmount, fy, BigDecimal.ZERO);
    }

    public RebalancePreviewDto getTaxOptimalLiquidationPlan(BigDecimal targetAmount, String fy, BigDecimal reservedExemption) {
        LedgerCacheService.CachedLedgerState state = resolveState();
        FifoMatcher.FifoResult fifoResult = state.fifoResult();
        List<Lot> openLots = fifoResult.openLots();
        List<MatchedLot> matchedLots = fifoResult.matchedLots();
        Map<String, BigDecimal> navMap = state.navMap();
        String currentFy = (fy != null && !fy.isBlank()) ? fy : TaxRulesLoader.detectFiscalYear(LocalDate.now());

        ExemptionTracker.ExemptionStatus status = ExemptionTracker.calculateExemptionStatus(matchedLots, currentFy);
        BigDecimal remExemption = new BigDecimal(status.exemptionRemaining());

        RebalanceEngine.RebalancePreviewResult result = RebalanceEngine.calculateRebalancePreview(
            openLots, navMap, targetAmount, remExemption, reservedExemption, currentFy, true
        );

        List<RebalanceLotDto> selectedDtos = result.selectedLots().stream().map(s -> new RebalanceLotDto(
            s.assetName(),
            fmt(s.unitsToSell()),
            fmt(s.redemptionProceeds()),
            fmt(s.estimatedGain()),
            s.taxTerm(),
            fmt(s.estimatedTaxDrag()),
            s.tier() != null ? s.tier().displayName() : "Section 112A LTCG (Taxable)"
        )).toList();

        return new RebalancePreviewDto(
            fmt(result.targetRedemptionAmount()),
            fmt(result.actualRedemptionAmount()),
            fmt(result.totalEstimatedGain()),
            fmt(result.totalTaxDrag()),
            String.format("%.2f%%", result.effectiveTaxRatePct()),
            fmt(result.ltcgExemptionHarvested()),
            selectedDtos,
            result.exemptionHeadroomCaveat()
        );
    }

    public record CombinedHarvestAndLiquidationPlanDto(
        HarvestAdvisor.TaxHarvestResult harvestPlan,
        RebalancePreviewDto liquidationPlan,
        String coordinationSummary
    ) {}

    public CombinedHarvestAndLiquidationPlanDto getCombinedHarvestAndLiquidationPlan(BigDecimal targetAmount, String fy) {
        LedgerCacheService.CachedLedgerState state = resolveState();
        FifoMatcher.FifoResult fifoResult = state.fifoResult();
        List<Lot> openLots = fifoResult.openLots();
        List<MatchedLot> matchedLots = fifoResult.matchedLots();
        Map<String, BigDecimal> navMap = state.navMap();
        String currentFy = (fy != null && !fy.isBlank()) ? fy : TaxRulesLoader.detectFiscalYear(LocalDate.now());

        ExemptionTracker.ExemptionStatus status = ExemptionTracker.calculateExemptionStatus(matchedLots, currentFy);
        BigDecimal usedExemption = new BigDecimal(status.exemptionUsed());

        HarvestAdvisor.TaxHarvestResult harvestPlan = HarvestAdvisor.generateHarvestPlan(
            openLots, navMap, usedExemption, currentFy
        );

        BigDecimal reservedForHarvest = harvestPlan.harvestableLtcgGain();

        RebalancePreviewDto liquidationPlan = getTaxOptimalLiquidationPlan(targetAmount, currentFy, reservedForHarvest);

        String summary = String.format(
            "Coordinated Plan: ₹%s LTCG allocated for tax-gain harvesting; ₹%s effective headroom reserved, preventing double-spend across ₹%s cash liquidation.",
            fmt(harvestPlan.harvestableLtcgGain()),
            fmt(reservedForHarvest),
            fmt(targetAmount)
        );

        return new CombinedHarvestAndLiquidationPlanDto(harvestPlan, liquidationPlan, summary);
    }

    public String generateHarvestCalendarIcs() {
        LedgerCacheService.CachedLedgerState state = resolveState();
        List<Lot> openLots = state.fifoResult().openLots();
        LocalDate today = LocalDate.now();
        java.time.format.DateTimeFormatter icsDtf = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
        java.time.format.DateTimeFormatter icsTimestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        String nowUtc = java.time.Instant.now().atOffset(java.time.ZoneOffset.UTC).format(icsTimestamp);

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//Portfolio OS//Tax Harvest Calendar 1.0//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:Portfolio OS Tax Harvest Calendar\r\n");

        int currentYear = today.getYear();
        LocalDate marchHarvestDate = LocalDate.of(today.getMonthValue() > 3 ? currentYear + 1 : currentYear, 3, 15);
        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:sec112a-harvest-").append(marchHarvestDate.getYear()).append("@portfolioos.local\r\n");
        sb.append("DTSTAMP:").append(nowUtc).append("\r\n");
        sb.append("DTSTART;VALUE=DATE:").append(marchHarvestDate.format(icsDtf)).append("\r\n");
        sb.append("DTEND;VALUE=DATE:").append(marchHarvestDate.plusDays(1).format(icsDtf)).append("\r\n");
        sb.append("SUMMARY:Sec 112A ₹1.25L Tax-Gain Harvesting Window\r\n");
        sb.append("DESCRIPTION:Review unrealized capital gains. Harvest up to ₹1.25 Lakh tax-free before fiscal year ends on March 31.\r\n");
        sb.append("BEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:Sec 112A Tax Harvesting Reminder\r\nTRIGGER:-P7D\r\nEND:VALARM\r\n");
        sb.append("END:VEVENT\r\n");

        int eventCount = 0;
        for (Lot lot : openLots) {
            LocalDate buyDate = lot.acquisitionDate();
            if (buyDate == null) continue;

            LocalDate equityLtcgDate = buyDate.plusDays(365);
            if (!equityLtcgDate.isBefore(today) && equityLtcgDate.isBefore(today.plusDays(90))) {
                sb.append("BEGIN:VEVENT\r\n");
                String uidId = lot.lotId() != null ? lot.lotId() : (lot.assetId() + "-" + eventCount);
                sb.append("UID:ltcg-maturation-").append(uidId).append("@portfolioos.local\r\n");
                sb.append("DTSTAMP:").append(nowUtc).append("\r\n");
                sb.append("DTSTART;VALUE=DATE:").append(equityLtcgDate.format(icsDtf)).append("\r\n");
                sb.append("DTEND;VALUE=DATE:").append(equityLtcgDate.plusDays(1).format(icsDtf)).append("\r\n");
                sb.append("SUMMARY:LTCG Flip: ").append(lot.assetName() != null ? lot.assetName() : lot.assetId()).append(" (").append(lot.remainingUnits()).append(" units)\r\n");
                sb.append("DESCRIPTION:Lot bought on ").append(buyDate).append(" at ₹").append(lot.costPerUnit()).append(" flips from 20% STCG to 12.5% LTCG.\r\n");
                sb.append("BEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:LTCG Maturity Alert\r\nTRIGGER:-P1D\r\nEND:VALARM\r\n");
                sb.append("END:VEVENT\r\n");
                eventCount++;
            }

            if ("INF205K01KR8".equals(lot.assetId())) {
                LocalDate exitLoadDate = buyDate.plusDays(15);
                if (!exitLoadDate.isBefore(today) && exitLoadDate.isBefore(today.plusDays(30))) {
                    sb.append("BEGIN:VEVENT\r\n");
                    String uidId = lot.lotId() != null ? lot.lotId() : String.valueOf(eventCount);
                    sb.append("UID:exitload-arbitrage-").append(uidId).append("@portfolioos.local\r\n");
                    sb.append("DTSTAMP:").append(nowUtc).append("\r\n");
                    sb.append("DTSTART;VALUE=DATE:").append(exitLoadDate.format(icsDtf)).append("\r\n");
                    sb.append("DTEND;VALUE=DATE:").append(exitLoadDate.plusDays(1).format(icsDtf)).append("\r\n");
                    sb.append("SUMMARY:Exit-Load Free: Invesco Arbitrage (").append(lot.remainingUnits()).append(" units)\r\n");
                    sb.append("DESCRIPTION:15-day exit load period expires today. Free to liquidate without exit load fee.\r\n");
                    sb.append("BEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:Exit Load Maturity\r\nTRIGGER:-P1D\r\nEND:VALARM\r\n");
                    sb.append("END:VEVENT\r\n");
                    eventCount++;
                }
            }
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }
}

