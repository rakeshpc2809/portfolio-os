package com.portfolioos.core.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioos.core.dtos.ReportDtos.*;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.reporting.TaxReportExporter;
import com.portfolioos.core.service.PortfolioValuationService;
import com.portfolioos.core.service.TaxOptimizationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final PortfolioValuationService valuationService;
    private final TaxOptimizationService taxService;
    private final com.portfolioos.core.service.LedgerCacheService cacheService;

    public ReportController(PortfolioValuationService valuationService, TaxOptimizationService taxService, com.portfolioos.core.service.LedgerCacheService cacheService) {
        this.valuationService = valuationService;
        this.taxService = taxService;
        this.cacheService = cacheService;
    }

    private String resolveFy(String fy) {
        return (fy != null && !fy.isBlank()) ? fy : com.portfolioos.core.rules.TaxRulesLoader.detectFiscalYear(LocalDate.now());
    }

    @GetMapping({"/reports/summary", "/portfolio/summary"})
    public ResponseEntity<PortfolioSummaryResponse> getSummary(
        @RequestParam(value = "fy", required = false) String fy
    ) {
        return ResponseEntity.ok(valuationService.getPortfolioSummary(resolveFy(fy)));
    }

    @GetMapping({"/reports/trend", "/portfolio/net-worth-trend"})
    public ResponseEntity<NetWorthTrendResponse> getNetWorthTrend() {
        return ResponseEntity.ok(valuationService.getNetWorthTrend());
    }

    @GetMapping({"/reports/holdings", "/portfolio/holdings"})
    public ResponseEntity<List<HoldingDetailDto>> getHoldings() {
        return ResponseEntity.ok(valuationService.getHoldings());
    }

    @GetMapping({"/reports/allocations/asset", "/portfolio/allocation"})
    public ResponseEntity<List<AssetAllocationEntry>> getAssetAllocation() {
        return ResponseEntity.ok(valuationService.getAssetAllocation());
    }

    @GetMapping({"/reports/allocations/category", "/portfolio/category-allocation"})
    public ResponseEntity<List<CategoryAllocationEntry>> getCategoryAllocation() {
        return ResponseEntity.ok(valuationService.getCategoryAllocation());
    }

    @GetMapping({"/reports/allocations/bucket", "/portfolio/bucket-allocation"})
    public ResponseEntity<List<com.portfolioos.core.dtos.ReportDtos.BucketStatusDto>> getBucketAllocation() {
        return ResponseEntity.ok(valuationService.getBucketAllocationStatuses());
    }

    @GetMapping({"/reports/tax/exemption", "/tax/exemption-status"})
    public ResponseEntity<ExemptionTracker.ExemptionStatus> getExemptionStatus(
        @RequestParam(value = "fy", required = false) String fy
    ) {
        return ResponseEntity.ok(taxService.getExemptionStatus(resolveFy(fy)));
    }

    @GetMapping({"/reports/tax/itr2", "/tax/reports/itr2"})
    public ResponseEntity<TaxReportExporter.Itr2ScheduleCgReport> getItr2Report(
        @RequestParam(value = "fy", required = false) String fy
    ) {
        return ResponseEntity.ok(taxService.generateItr2Report(resolveFy(fy)));
    }

    @GetMapping({"/reports/tax/itr2/details", "/tax/reports/itr2/details"})
    public ResponseEntity<TaxReportExporter.Itr2DetailsDto> getItr2Details(
        @RequestParam(value = "fy", required = false) String fy
    ) {
        return ResponseEntity.ok(taxService.getItr2Details(resolveFy(fy)));
    }

    @GetMapping({"/reports/tax/harvest", "/tax/harvest-opportunities"})
    public ResponseEntity<List<HarvestOpportunityDto>> getHarvestOpportunities() {
        return ResponseEntity.ok(taxService.getHarvestOpportunities());
    }

    @GetMapping({"/reports/tax/maturation", "/tax/maturation-ladder"})
    public ResponseEntity<List<MaturationLadderDto>> getMaturationLadder() {
        return ResponseEntity.ok(taxService.getMaturationLadder());
    }

    @GetMapping({"/reports/tax/realized", "/tax/realized-log"})
    public ResponseEntity<List<RealizedLogDto>> getRealizedLog(
        @RequestParam(value = "fy", required = false) String fy
    ) {
        return ResponseEntity.ok(taxService.getRealizedLog(resolveFy(fy)));
    }

    @GetMapping({"/reports/tax/liquidation-plan", "/tax/liquidation-plan"})
    public ResponseEntity<RebalancePreviewDto> getTaxOptimalLiquidationPlan(
        @RequestParam(value = "amount", defaultValue = "100000") BigDecimal amount,
        @RequestParam(value = "fy", required = false) String fy
    ) {
        return ResponseEntity.ok(taxService.getTaxOptimalLiquidationPlan(amount, resolveFy(fy)));
    }

    @GetMapping({"/reports/tax/combined-plan", "/tax/combined-plan"})
    public ResponseEntity<TaxOptimizationService.CombinedHarvestAndLiquidationPlanDto> getCombinedHarvestAndLiquidationPlan(
        @RequestParam(value = "amount", defaultValue = "100000") BigDecimal amount,
        @RequestParam(value = "fy", required = false) String fy
    ) {
        return ResponseEntity.ok(taxService.getCombinedHarvestAndLiquidationPlan(amount, resolveFy(fy)));
    }

    @GetMapping({"/reports/tax/itr2/csv", "/tax/export/itr2/zip"})
    public ResponseEntity<byte[]> downloadItr2Csv(
        @RequestParam(value = "fy", required = false) String fy
    ) throws IOException {
        String resolvedFy = resolveFy(fy);
        Map<String, String> files = taxService.downloadItr2Files(resolvedFy);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                zos.putNextEntry(entry);
                zos.write(file.getValue().getBytes("UTF-8"));
                zos.closeEntry();
            }
        }

        byte[] zipBytes = baos.toByteArray();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"itr2_schedule_cg_" + resolvedFy + ".zip\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .contentLength(zipBytes.length)
            .body(zipBytes);
    }

    @GetMapping("/tax/schedule-cg/export")
    public ResponseEntity<String> downloadScheduleCgCsv(
        @RequestParam(value = "fy", required = false) String fy
    ) {
        String resolvedFy = resolveFy(fy);
        Map<String, String> files = taxService.downloadItr2Files(resolvedFy);
        String csv = files.getOrDefault("Schedule_112A.csv", "");

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Schedule-CG-FY" + resolvedFy + ".csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }

    @GetMapping(value = "/tax/harvest-calendar.ics", produces = "text/calendar")
    public ResponseEntity<String> downloadHarvestCalendarIcs() {
        String ics = taxService.generateHarvestCalendarIcs();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"portfolio_harvest_calendar.ics\"")
            .header(HttpHeaders.CONTENT_TYPE, "text/calendar; charset=UTF-8")
            .body(ics);
    }

    @GetMapping("/funds/registry")
    public ResponseEntity<Map<String, Object>> getFundRegistry() {
        return ResponseEntity.ok(valuationService.getFundRegistry());
    }

    @GetMapping({"/fire/summary", "/portfolio/fire", "/reports/fire"})
    public ResponseEntity<FireSummaryResponse> getFireSummary() {
        return ResponseEntity.ok(valuationService.getFireSummary());
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

    @GetMapping({"/goals/summary", "/portfolio/goals"})
    public ResponseEntity<GoalSummaryResponse> getGoalSummary() {
        return ResponseEntity.ok(valuationService.getGoalSummary());
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
