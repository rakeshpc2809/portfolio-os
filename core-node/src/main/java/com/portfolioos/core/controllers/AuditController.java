package com.portfolioos.core.controllers;

import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.rpc.QuantSidecarClient;
import com.portfolioos.core.service.LedgerCacheService;
import com.portfolioos.core.valuation.ExitLoadCalculator;
import com.portfolioos.core.model.Lot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final LedgerCacheService cacheService;
    private final DuckDbProjector duckDbProjector;
    private final QuantSidecarClient quantSidecarClient;

    public AuditController(
        LedgerCacheService cacheService,
        DuckDbProjector duckDbProjector,
        QuantSidecarClient quantSidecarClient
    ) {
        this.cacheService = cacheService;
        this.duckDbProjector = duckDbProjector;
        this.quantSidecarClient = quantSidecarClient;
    }

    public record FundHealthItem(
        String isin,
        String fundName,
        double currentNav,
        double peak52wNav,
        double drawdownPct,
        double exitLoadFreeUnits,
        double exitLoadFreePct,
        String healthStatus,
        Map<String, Object> quantMetrics
    ) {}

    @GetMapping("/fund-health")
    public ResponseEntity<Map<String, Object>> getFundHealth() {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state.fifoResult().openLots();
        Map<String, BigDecimal> navMap = state.navMap();
        LocalDate today = LocalDate.now();

        Set<String> heldIsins = openLots.stream().map(Lot::assetId).collect(Collectors.toSet());
        Map<String, BigDecimal> peakNavMap = duckDbProjector.get52WeekPeakNavs(heldIsins);
        var seriesWithDates = duckDbProjector.getNavHistorySeriesWithDates(heldIsins);
        Map<String, Map<String, Object>> quantMetricsMap = quantSidecarClient.computeQuantMetricsWithDates(seriesWithDates);

        Map<String, List<Lot>> grouped = openLots.stream().collect(Collectors.groupingBy(Lot::assetId));
        List<FundHealthItem> items = new ArrayList<>();

        for (Map.Entry<String, List<Lot>> entry : grouped.entrySet()) {
            String isin = entry.getKey();
            List<Lot> lots = entry.getValue();
            String name = lots.get(0).assetName();

            BigDecimal currentNav = navMap.getOrDefault(isin, BigDecimal.ZERO);
            BigDecimal peakNav = peakNavMap.getOrDefault(isin, currentNav).max(currentNav);
            double ddPct = peakNav.compareTo(BigDecimal.ZERO) > 0
                ? currentNav.subtract(peakNav).divide(peakNav, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                : 0.0;

            var exitSummary = ExitLoadCalculator.calculateForAsset(isin, name, lots, currentNav, today);

            String status = "HEALTHY";
            if (ddPct < -15.0) {
                status = "DEEP_DRAWDOWN_WATCH";
            } else if (ddPct < -10.0) {
                status = "MODERATE_DRAWDOWN";
            }

            Map<String, Object> metrics = quantMetricsMap.getOrDefault(isin, Collections.emptyMap());

            items.add(new FundHealthItem(
                isin,
                name,
                currentNav.doubleValue(),
                peakNav.doubleValue(),
                ddPct,
                exitSummary.exitLoadFreeUnits().doubleValue(),
                exitSummary.exitLoadFreePct(),
                status,
                metrics
            ));
        }

        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "as_of", today.toString(),
            "fund_count", items.size(),
            "funds", items
        ));
    }
}
