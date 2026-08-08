package com.portfolioos.core.controllers;

import com.portfolioos.core.dtos.ReportDtos.*;
import com.portfolioos.core.reporting.ExemptionTracker;
import com.portfolioos.core.reporting.TaxReportExporter;
import com.portfolioos.core.service.PortfolioValuationService;
import com.portfolioos.core.service.TaxOptimizationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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

    @GetMapping({"/reports/summary", "/portfolio/summary"})
    public ResponseEntity<PortfolioSummaryResponse> getSummary(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(valuationService.getPortfolioSummary(fy));
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

    @GetMapping({"/reports/tax/exemption", "/tax/exemption-status"})
    public ResponseEntity<ExemptionTracker.ExemptionStatus> getExemptionStatus(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(taxService.getExemptionStatus(fy));
    }

    @GetMapping({"/reports/tax/itr2", "/tax/reports/itr2"})
    public ResponseEntity<TaxReportExporter.Itr2ScheduleCgReport> getItr2Report(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(taxService.generateItr2Report(fy));
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
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        return ResponseEntity.ok(taxService.getRealizedLog(fy));
    }

    @GetMapping({"/reports/tax/itr2/csv", "/tax/export/itr2/zip"})
    public ResponseEntity<byte[]> downloadItr2Csv(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) throws IOException {
        Map<String, String> files = taxService.downloadItr2Files(fy);

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
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"itr2_schedule_cg_" + fy + ".zip\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .contentLength(zipBytes.length)
            .body(zipBytes);
    }

    @GetMapping("/tax/schedule-cg/export")
    public ResponseEntity<String> downloadScheduleCgCsv(
        @RequestParam(value = "fy", defaultValue = "2026-27") String fy
    ) {
        String csv = com.portfolioos.core.reporting.Itr2CsvExporter.generateSchedule112aCsv(
            cacheService.getCachedState().fifoResult().matchedLots(),
            fy,
            java.util.Collections.emptyMap(),
            java.util.Collections.emptyMap()
        );

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Schedule-CG-FY" + fy + ".csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }

    @GetMapping("/analytics/benchmark")
    public ResponseEntity<Map<String, Object>> getBenchmarkAnalytics(
        @RequestParam(value = "benchmark", defaultValue = "NIFTY_50_TRI") String benchmark
    ) {
        return ResponseEntity.ok(valuationService.getBenchmarkAnalytics(benchmark));
    }

    @GetMapping("/analytics/overlap")
    public ResponseEntity<Map<String, Object>> getPortfolioOverlapAnalytics(
        @RequestParam(value = "fundA", defaultValue = "INF109KC13X2") String fundA,
        @RequestParam(value = "fundB", defaultValue = "INF109KC12U0") String fundB
    ) {
        return ResponseEntity.ok(valuationService.getPortfolioOverlapAnalytics(fundA, fundB));
    }

    @GetMapping("/analytics/overlap/upset")
    public ResponseEntity<Map<String, Object>> getMultiFundUpSetAnalytics() {
        return ResponseEntity.ok(valuationService.getMultiFundUpSetAnalytics());
    }
}
