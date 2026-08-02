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

    @GetMapping({"/rebalance/bucket", "/portfolio/buckets/rebalance"})
    public ResponseEntity<BucketRebalanceResponse> getBucketRebalance(
        @RequestParam(value = "benchmarkCurrent", defaultValue = "24000.00") BigDecimal benchmarkCurrent,
        @RequestParam(value = "benchmarkRollingHigh", defaultValue = "25000.00") BigDecimal benchmarkRollingHigh,
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(valuationService.getBucketRebalance(benchmarkCurrent, benchmarkRollingHigh, fy));
    }

    @GetMapping({"/rebalance/preview", "/portfolio/rebalance-preview"})
    public ResponseEntity<RebalancePreviewDto> getRebalancePreview(
        @RequestParam(value = "amount", defaultValue = "100000") BigDecimal amount,
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(valuationService.getRebalancePreview(amount, fy));
    }

    @GetMapping({"/rebalance/consolidation", "/portfolio/consolidation-preview"})
    public ResponseEntity<ConsolidationPreviewResponse> getConsolidationPreview(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(valuationService.getConsolidationPreview(fy));
    }

    @GetMapping({"/goals/summary", "/portfolio/goals"})
    public ResponseEntity<GoalSummaryResponse> getGoalSummary() {
        return ResponseEntity.ok(valuationService.getGoalSummary());
    }

    @GetMapping({"/fire/summary", "/portfolio/fire"})
    public ResponseEntity<FireSummaryResponse> getFireSummary() {
        return ResponseEntity.ok(valuationService.getFireSummary());
    }
}
