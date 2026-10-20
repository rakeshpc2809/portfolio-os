package com.portfolioos.core.controllers;

import com.portfolioos.core.dtos.ReportDtos.*;
import com.portfolioos.core.service.PortfolioValuationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1")
public class RebalanceController {

    private final PortfolioValuationService valuationService;

    public RebalanceController(PortfolioValuationService valuationService) {
        this.valuationService = valuationService;
    }

    private String resolveFy(String fy) {
        return (fy != null && !fy.isBlank()) ? fy : com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(java.time.LocalDate.now());
    }

    @GetMapping({"/rebalance/bucket", "/portfolio/buckets/rebalance"})
    public ResponseEntity<BucketRebalanceResponse> getBucketRebalance(
        @RequestParam(value = "benchmarkCurrent", required = false) BigDecimal benchmarkCurrent,
        @RequestParam(value = "benchmarkRollingHigh", required = false) BigDecimal benchmarkRollingHigh,
        @RequestParam(value = "fy", required = false) String fy
    ) {
        return ResponseEntity.ok(valuationService.getBucketRebalance(benchmarkCurrent, benchmarkRollingHigh, resolveFy(fy)));
    }

    @GetMapping({"/rebalance/preview", "/portfolio/rebalance-preview"})
    public ResponseEntity<RebalancePreviewDto> getRebalancePreview(
        @RequestParam(value = "amount", defaultValue = "100000") BigDecimal amount,
        @RequestParam(value = "fy", required = false) String fy
    ) {
        return ResponseEntity.ok(valuationService.getRebalancePreview(amount, resolveFy(fy)));
    }

    @GetMapping({"/rebalance/consolidation", "/portfolio/consolidation-preview"})
    public ResponseEntity<ConsolidationPreviewResponse> getConsolidationPreview(
        @RequestParam(value = "fy", required = false) String fy
    ) {
        return ResponseEntity.ok(valuationService.getConsolidationPreview(resolveFy(fy)));
    }

    @GetMapping({"/goals/summary", "/portfolio/goals"})
    public ResponseEntity<GoalSummaryResponse> getGoalSummary() {
        return ResponseEntity.ok(valuationService.getGoalSummary());
    }

    @GetMapping({"/fire/summary", "/portfolio/fire"})
    public ResponseEntity<FireSummaryResponse> getFireSummary() {
        return ResponseEntity.ok(valuationService.getFireSummary());
    }

    @GetMapping({"/rebalance/waterfall", "/portfolio/rebalance-waterfall"})
    public ResponseEntity<WaterfallResponse> getRebalanceWaterfall(
        @RequestParam(value = "bucket", defaultValue = "EQUITY_CORE") com.portfolioos.core.valuation.BucketEngine.Bucket bucket,
        @RequestParam(value = "amount", defaultValue = "40000") BigDecimal amount,
        @RequestParam(value = "fy", required = false) String fy
    ) {
        return ResponseEntity.ok(valuationService.getRebalanceWaterfall(bucket, amount, resolveFy(fy)));
    }
}
