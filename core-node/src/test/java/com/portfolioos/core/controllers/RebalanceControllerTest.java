package com.portfolioos.core.controllers;

import com.portfolioos.core.dtos.RebalancePlanDtos.*;
import com.portfolioos.core.dtos.ReportDtos.*;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.service.PortfolioValuationService;
import com.portfolioos.core.service.RebalancePlanEngine;
import com.portfolioos.core.valuation.BucketEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RebalanceControllerTest {

    @Test
    @DisplayName("P4.3 Test 3a: RebalanceController endpoints delegate to PortfolioValuationService and return 200 OK")
    void testRebalanceControllerEndpoints() {
        PortfolioValuationService valuationService = Mockito.mock(PortfolioValuationService.class);
        com.portfolioos.core.persistence.TriggerHistoryRepository mockRepo = Mockito.mock(com.portfolioos.core.persistence.TriggerHistoryRepository.class);
        RebalanceController controller = new RebalanceController(valuationService, null, mockRepo);

        // 1. /api/v1/rebalance/bucket
        BucketRebalanceResponse mockBucketResp = Mockito.mock(BucketRebalanceResponse.class);
        Mockito.when(valuationService.getBucketRebalance(Mockito.any(), Mockito.any(), Mockito.anyString()))
            .thenReturn(mockBucketResp);
        ResponseEntity<BucketRebalanceResponse> bucketResp = controller.getBucketRebalance(
            new BigDecimal("25000"), new BigDecimal("26000"), "2026-27"
        );
        assertNotNull(bucketResp);
        assertEquals(200, bucketResp.getStatusCode().value());
        assertSame(mockBucketResp, bucketResp.getBody());

        // 2. /api/v1/rebalance/preview
        RebalancePreviewDto mockPreview = Mockito.mock(RebalancePreviewDto.class);
        Mockito.when(valuationService.getRebalancePreview(Mockito.any(), Mockito.anyString()))
            .thenReturn(mockPreview);
        ResponseEntity<RebalancePreviewDto> prevResp = controller.getRebalancePreview(new BigDecimal("100000"), "2026-27");
        assertNotNull(prevResp);
        assertEquals(200, prevResp.getStatusCode().value());
        assertSame(mockPreview, prevResp.getBody());

        // 3. /api/v1/rebalance/consolidation
        ConsolidationPreviewResponse mockConsolidation = Mockito.mock(ConsolidationPreviewResponse.class);
        Mockito.when(valuationService.getConsolidationPreview(Mockito.anyString()))
            .thenReturn(mockConsolidation);
        ResponseEntity<ConsolidationPreviewResponse> consResp = controller.getConsolidationPreview("2026-27");
        assertNotNull(consResp);
        assertEquals(200, consResp.getStatusCode().value());
        assertSame(mockConsolidation, consResp.getBody());

        // 4. /api/v1/rebalance/waterfall
        WaterfallResponse mockWaterfall = Mockito.mock(WaterfallResponse.class);
        Mockito.when(valuationService.getRebalanceWaterfall(Mockito.any(), Mockito.any(), Mockito.anyString()))
            .thenReturn(mockWaterfall);
        ResponseEntity<WaterfallResponse> waterResp = controller.getRebalanceWaterfall(
            BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("40000"), "2026-27"
        );
        assertNotNull(waterResp);
        assertEquals(200, waterResp.getStatusCode().value());
        assertSame(mockWaterfall, waterResp.getBody());
    }

    @Test
    @DisplayName("P4.3 Test 3b: Exact budget conservation on RebalancePlanEngine.buildPlan for manual lump-sum deployment (0.00 delta)")
    void testExactBudgetConservationOnLumpsumDeployment() {
        // Setup realistic multi-fund portfolio
        Lot lotCore = new Lot(
            "LOT_CORE_1", "INF879O01027", "Parag Parikh Flexi Cap Fund", LocalDate.of(2024, 1, 1),
            new BigDecimal("2000.000"), new BigDecimal("2000.000"),
            new BigDecimal("50.000"), new BigDecimal("100000.00"),
            false, null
        );
        Lot lotSatellite = new Lot(
            "LOT_SAT_1", "INF204K01K15", "Nippon India Small Cap Fund", LocalDate.of(2024, 1, 1),
            new BigDecimal("500.000"), new BigDecimal("500.000"),
            new BigDecimal("100.000"), new BigDecimal("50000.00"),
            false, null
        );
        List<Lot> openLots = List.of(lotCore, lotSatellite);
        Map<String, BigDecimal> navMap = Map.of(
            "INF879O01027", new BigDecimal("75.0000"),  // Core val = 150,000
            "INF204K01K15", new BigDecimal("140.0000")  // Sat val  = 70,000
        );

        BigDecimal[] testInflows = new BigDecimal[]{
            new BigDecimal("50000.00"),
            new BigDecimal("100000.00"),
            new BigDecimal("234567.89"), // Non-round number with fractional paisa
            new BigDecimal("500000.00")
        };

        for (BigDecimal lumpsum : testInflows) {
            RebalancePlanDto plan = RebalancePlanEngine.buildPlan(
                openLots,
                List.of(),
                navMap,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("24000.00"),
                new BigDecimal("25000.00"),
                null,
                "2026-27",
                "MANUAL_LUMPSUM",
                lumpsum,
                false
            );

            assertNotNull(plan);
            assertNotNull(plan.buySide());
            assertEquals(lumpsum.setScale(2, java.math.RoundingMode.HALF_UP),
                plan.buySide().totalToInvest().setScale(2, java.math.RoundingMode.HALF_UP));

            // Sum of bucket allocations must equal buySide.totalToInvest() to the exact paisa (0.00 delta)
            BigDecimal sumBucketAllocations = plan.buySide().buckets().stream()
                .map(RebalanceBucketAllocationDto::amountAllocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);

            BigDecimal expectedTotal = plan.buySide().totalToInvest().setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal delta = sumBucketAllocations.subtract(expectedTotal).abs();

            assertEquals(new BigDecimal("0.00"), delta,
                "Budget conservation failure for inflow ₹" + lumpsum + ": sum of buckets=" + sumBucketAllocations + ", totalToInvest=" + expectedTotal);
        }
    }
}
