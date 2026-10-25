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
@RequestMapping({"/api/v1/audit", "/api/v1/analytics"})
public class AuditController {

    private final LedgerCacheService cacheService;
    private final DuckDbProjector duckDbProjector;
    private final QuantSidecarClient quantSidecarClient;
    private final com.portfolioos.core.service.PortfolioValuationService valuationService;

    public AuditController(
        LedgerCacheService cacheService,
        DuckDbProjector duckDbProjector,
        QuantSidecarClient quantSidecarClient,
        @org.springframework.beans.factory.annotation.Autowired(required = false) com.portfolioos.core.service.PortfolioValuationService valuationService
    ) {
        this.cacheService = cacheService;
        this.duckDbProjector = duckDbProjector;
        this.quantSidecarClient = quantSidecarClient;
        this.valuationService = valuationService;
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

    @GetMapping({"/hrp-audit", "/analytics/hrp-audit"})
    public ResponseEntity<Map<String, Object>> getHrpAudit() {
        java.io.File[] candidates = new java.io.File[] {
            new java.io.File("data/hrp_audit.json"),
            new java.io.File("../data/hrp_audit.json"),
            new java.io.File("../../data/hrp_audit.json"),
            new java.io.File("quant-sidecar/data/hrp_audit.json"),
            new java.io.File("../quant-sidecar/data/hrp_audit.json"),
            new java.io.File("/app/data/hrp_audit.json")
        };
        for (java.io.File f : candidates) {
            if (f.exists() && f.isFile()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = mapper.readValue(f, Map.class);
                    return ResponseEntity.ok(data);
                } catch (Exception e) {
                    // fallthrough to next candidate
                }
            }
        }
        return ResponseEntity.ok(Map.of(
            "status", "NOT_FOUND",
            "message", "HRP audit not yet generated by quant-sidecar"
        ));
    }

    public record FactorBenchmarkMetric(
        String isin,
        String fundName,
        String benchmarkId,
        String benchmarkName,
        double beta,
        double trackingErrorPct,
        double alphaPct,
        double informationRatio,
        double outperformancePct,
        String sampleStatus,
        int sampleDays
    ) {}

    private static final Map<String, String[]> FUND_BENCHMARK_CONFIG = Map.of(
        "INF109KC12U0", new String[]{"NIFTY_LARGEMIDCAP_250", "Nifty LargeMidcap 250 TRI"},
        "INF879O01027", new String[]{"NIFTY_500_TRI", "Nifty 500 TRI"},
        "INF109KC13X2", new String[]{"NIFTY_200_VALUE_30", "Nifty 200 Value 30 TRI"},
        "INF754K01TN5", new String[]{"NIFTY_200_MOMENTUM_30", "Nifty 200 Momentum 30 TRI"},
        "INF204K01K15", new String[]{"NIFTY_SMALLCAP_250", "Nifty Smallcap 250 TRI"},
        "INF247L01BQ9", new String[]{"NIFTY_SMALLCAP_250", "Nifty Smallcap 250 TRI"}
    );

    @GetMapping({"/factor-benchmarks", "/analytics/factor-benchmarks"})
    public ResponseEntity<Map<String, Object>> getFactorBenchmarks() {
        LedgerCacheService.CachedLedgerState state = cacheService.getCachedState();
        List<Lot> openLots = state != null && state.fifoResult() != null ? state.fifoResult().openLots() : Collections.emptyList();
        LocalDate today = LocalDate.now();

        Map<String, List<Lot>> grouped = openLots.stream().collect(Collectors.groupingBy(Lot::assetId));
        List<FactorBenchmarkMetric> metrics = new ArrayList<>();

        for (Map.Entry<String, List<Lot>> entry : grouped.entrySet()) {
            String isin = entry.getKey();
            List<Lot> lots = entry.getValue();
            String name = lots.get(0).assetName();

            String[] benchConfig = FUND_BENCHMARK_CONFIG.get(isin);
            if (benchConfig == null) {
                String upper = name.toUpperCase();
                if (upper.contains("LARGEMIDCAP")) {
                    benchConfig = new String[]{"NIFTY_LARGEMIDCAP_250", "Nifty LargeMidcap 250 TRI"};
                } else if (upper.contains("VALUE")) {
                    benchConfig = new String[]{"NIFTY_200_VALUE_30", "Nifty 200 Value 30 TRI"};
                } else if (upper.contains("MOMENTUM")) {
                    benchConfig = new String[]{"NIFTY_200_MOMENTUM_30", "Nifty 200 Momentum 30 TRI"};
                } else if (upper.contains("SMALL") || upper.contains("MICRO")) {
                    benchConfig = new String[]{"NIFTY_SMALLCAP_250", "Nifty Smallcap 250 TRI"};
                } else {
                    benchConfig = new String[]{"NIFTY_500_TRI", "Nifty 500 TRI"};
                }
            }

            String benchId = benchConfig[0];
            String benchName = benchConfig[1];

            if (valuationService != null) {
                valuationService.ensureBenchmarkData(benchId);
            }

            Map<String, Object> aligned = duckDbProjector.getAlignedFundAndBenchmarkReturns(isin, benchId);
            @SuppressWarnings("unchecked")
            List<Double> fRets = (List<Double>) aligned.getOrDefault("fund_returns", Collections.emptyList());
            @SuppressWarnings("unchecked")
            List<Double> bRets = (List<Double>) aligned.getOrDefault("benchmark_returns", Collections.emptyList());

            double beta = 1.0;
            double te = 0.15;
            double alpha = 0.0;
            double ir = 0.0;
            double outperf = 0.0;
            String status = "NO_DATA";
            int days = fRets.size();

            if (days >= 20 && fRets.size() == bRets.size()) {
                Map<String, Object> quantRes = quantSidecarClient.computeBenchmarkAnalytics(fRets, bRets, benchId);
                if (quantRes != null && "OK".equals(quantRes.get("status"))) {
                    beta = ((Number) quantRes.getOrDefault("beta", 1.0)).doubleValue();
                    te = ((Number) quantRes.getOrDefault("tracking_error_pct", 0.0)).doubleValue();
                    alpha = ((Number) quantRes.getOrDefault("alpha_pct", 0.0)).doubleValue();
                    ir = ((Number) quantRes.getOrDefault("information_ratio", 0.0)).doubleValue();
                    outperf = ((Number) quantRes.getOrDefault("outperformance_pct", 0.0)).doubleValue();
                    status = (String) quantRes.getOrDefault("sample_status", "MATURE_EMPIRICAL_SAMPLE");
                }
            } else if (days > 0) {
                status = "PROVISIONAL_SHORT_SAMPLE";
            }

            metrics.add(new FactorBenchmarkMetric(
                isin,
                name,
                benchId,
                benchName,
                beta,
                te,
                alpha,
                ir,
                outperf,
                status,
                days
            ));
        }

        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "as_of", today.toString(),
            "count", metrics.size(),
            "factors", metrics
        ));
    }
}
