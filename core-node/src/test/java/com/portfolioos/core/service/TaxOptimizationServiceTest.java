package com.portfolioos.core.service;

import com.portfolioos.core.dtos.ReportDtos.*;
import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.ports.EventStorePort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaxOptimizationServiceTest {

    private EventStorePort createMockEventStore(List<TaxEvent> events) {
        return new EventStorePort() {
            @Override public String appendEvent(TaxEvent event) { return "EV_1"; }
            @Override public List<String> appendEvents(List<TaxEvent> events) { return List.of("EV_1"); }
            @Override public List<TaxEvent> getEventsForAsset(String assetId) { return events; }
            @Override public List<TaxEvent> getAllEvents() { return events; }
            @Override public boolean verifyLedgerIntegrity() { return true; }
            @Override public void clearAllEvents() {}
            @Override public String getLatestEventHash() { return "HASH"; }
        };
    }

    @Test
    void testGetHarvestOpportunitiesWithPriorRealizedLtcg() {
        TaxEvent acq1 = new TaxEvent(
            "EV_ACQ_1", "INF109KC13X2", "ICICI Nifty200", "INF109KC13X2",
            EventType.ACQUISITION, LocalDate.of(2024, 1, 1),
            new BigDecimal("1000.0"), new BigDecimal("100.0"), new BigDecimal("100000.0"),
            "CAS_IMPORT", Instant.now()
        );

        TaxEvent acq2 = new TaxEvent(
            "EV_ACQ_2", "INF109KC13X2", "ICICI Nifty200", "INF109KC13X2",
            EventType.ACQUISITION, LocalDate.of(2024, 1, 1),
            new BigDecimal("1000.0"), new BigDecimal("100.0"), new BigDecimal("100000.0"),
            "CAS_IMPORT", Instant.now()
        );

        TaxEvent disp2 = new TaxEvent(
            "EV_DISP_2", "INF109KC13X2", "ICICI Nifty200", "INF109KC13X2",
            EventType.DISPOSAL, LocalDate.of(2026, 5, 1),
            new BigDecimal("1000.0"), new BigDecimal("200.0"), new BigDecimal("200000.0"),
            "CAS_IMPORT", Instant.now()
        );

        List<TaxEvent> events = List.of(acq1, acq2, disp2);

        EventStorePort mockEventStore = createMockEventStore(events);
        TaxOptimizationService service = new TaxOptimizationService(mockEventStore);

        List<HarvestOpportunityDto> opps = service.getHarvestOpportunities();
        assertNotNull(opps);

        BigDecimal totalHarvestableGain = opps.stream()
            .map(o -> new BigDecimal(o.potentialHarvestableLoss()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(totalHarvestableGain.compareTo(new BigDecimal("25000.00")) <= 0,
            "Harvest opportunities must respect remaining exemption headroom (25,000) rather than assuming 1,25,000");
    }

    @Test
    void testGetHarvestOpportunitiesZeroRealizedLtcg() {
        TaxEvent acq1 = new TaxEvent(
            "EV_ACQ_1", "INF109KC13X2", "ICICI Nifty200", "INF109KC13X2",
            EventType.ACQUISITION, LocalDate.of(2024, 1, 1),
            new BigDecimal("2000.0"), new BigDecimal("100.0"), new BigDecimal("200000.0"),
            "CAS_IMPORT", Instant.now()
        );

        List<TaxEvent> events = List.of(acq1);

        EventStorePort mockEventStore = createMockEventStore(events);
        TaxOptimizationService service = new TaxOptimizationService(mockEventStore);

        List<HarvestOpportunityDto> opps = service.getHarvestOpportunities();
        assertNotNull(opps);

        BigDecimal totalHarvestableGain = opps.stream()
            .map(o -> new BigDecimal(o.potentialHarvestableLoss()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(totalHarvestableGain.compareTo(new BigDecimal("125000.00")) <= 0,
            "With 0 realized gain, full 1,25,000 headroom is available");
    }

    @Test
    void testGetTaxOptimalLiquidationPlan() {
        TaxEvent acqLoss = new TaxEvent(
            "EV_ACQ_LOSS", "INF109KC13X2", "ICICI Nifty200", "INF109KC13X2",
            EventType.ACQUISITION, LocalDate.of(2024, 1, 1),
            new BigDecimal("100.0"), new BigDecimal("100.0"), new BigDecimal("10000.0"),
            "CAS_IMPORT", Instant.now()
        );

        TaxEvent acqGain = new TaxEvent(
            "EV_ACQ_GAIN", "INF109KC13X2", "ICICI Nifty200", "INF109KC13X2",
            EventType.ACQUISITION, LocalDate.of(2024, 1, 1),
            new BigDecimal("1000.0"), new BigDecimal("5.0"), new BigDecimal("5000.0"),
            "CAS_IMPORT", Instant.now()
        );

        EventStorePort mockEventStore = createMockEventStore(List.of(acqLoss, acqGain));
        TaxOptimizationService service = new TaxOptimizationService(mockEventStore);

        RebalancePreviewDto plan = service.getTaxOptimalLiquidationPlan(new BigDecimal("50000.00"), "2026-27");
        assertNotNull(plan);
        assertNotNull(plan.selectedLots());
        assertEquals(2, plan.selectedLots().size());
        assertNotNull(plan.exemptionHeadroomCaveat());

        RebalanceLotDto firstLot = plan.selectedLots().get(0);
        assertEquals("Capital Loss Harvesting", firstLot.tier());

        RebalanceLotDto secondLot = plan.selectedLots().get(1);
        assertEquals("Section 112A LTCG (Exempt Headroom)", secondLot.tier());
    }

    @Test
    void testGetCombinedHarvestAndLiquidationPlanPreventsDoubleSpend() {
        TaxEvent acq1 = new TaxEvent(
            "EV_ACQ_1", "INF109KC13X2", "ICICI Nifty200", "INF109KC13X2",
            EventType.ACQUISITION, LocalDate.of(2024, 1, 1),
            new BigDecimal("2000.0"), new BigDecimal("100.0"), new BigDecimal("200000.0"),
            "CAS_IMPORT", Instant.now()
        );

        EventStorePort mockEventStore = createMockEventStore(List.of(acq1));
        TaxOptimizationService service = new TaxOptimizationService(mockEventStore);

        TaxOptimizationService.CombinedHarvestAndLiquidationPlanDto combined = service.getCombinedHarvestAndLiquidationPlan(new BigDecimal("50000.00"), "2026-27");
        assertNotNull(combined);
        assertNotNull(combined.harvestPlan());
        assertNotNull(combined.liquidationPlan());
        assertNotNull(combined.coordinationSummary());
        assertTrue(combined.coordinationSummary().contains("Coordinated Plan"));
    }
}
