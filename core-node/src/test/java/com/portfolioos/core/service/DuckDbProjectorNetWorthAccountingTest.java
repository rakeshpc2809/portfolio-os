package com.portfolioos.core.service;

import com.portfolioos.core.model.EventType;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.persistence.DuckDbProjector;
import com.portfolioos.core.persistence.DuckDbProjector.NetWorthPoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DuckDbProjectorNetWorthAccountingTest {

    private DuckDbProjector projector;

    @BeforeEach
    void setUp() {
        projector = new DuckDbProjector("jdbc:duckdb:");
    }

    @Test
    @DisplayName("Verify net active capital accounting: DISPOSAL sale proceeds drop total_invested, and subsequent ACQUISITION nets to exactly 0.00 change across rebalance pair")
    void testRebalanceTradePairNetActiveCapitalAccounting() {
        // 1. Setup NAV history for asset_1 and asset_2 across dates
        Map<String, BigDecimal> navJan1 = Map.of("asset_1", new BigDecimal("10.0"));
        Map<String, BigDecimal> navJun1 = Map.of("asset_1", new BigDecimal("12.0"));
        Map<String, BigDecimal> navJun2 = Map.of("asset_1", new BigDecimal("12.0"), "asset_2", new BigDecimal("15.0"));

        projector.saveNavHistoryBatchForHeldAssets(navJan1, Set.of("asset_1"), LocalDate.parse("2026-01-01"));
        projector.saveNavHistoryBatchForHeldAssets(navJun1, Set.of("asset_1"), LocalDate.parse("2026-06-01"));
        projector.saveNavHistoryBatchForHeldAssets(navJun2, Set.of("asset_1", "asset_2"), LocalDate.parse("2026-06-02"));

        // 2. Initial ACQUISITION on 2026-01-01 (Rs 1,00,000 invested, 10,000 units @ Rs 10.0)
        TaxEvent e1 = new TaxEvent(
            "evt-1", "asset_1", "Legacy Fund", "asset_1",
            EventType.ACQUISITION, LocalDate.parse("2026-01-01"),
            new BigDecimal("10000.00"), new BigDecimal("10.00"), new BigDecimal("100000.00"),
            "doc-1", Instant.parse("2026-01-01T10:00:00Z")
        );
        projector.projectEvents(List.of(e1));

        List<NetWorthPoint> initialTrend = projector.getDailyNetWorthTrend();
        assertFalse(initialTrend.isEmpty());
        NetWorthPoint pointJan1 = initialTrend.stream()
            .filter(p -> p.date().equals("2026-01-01"))
            .findFirst()
            .orElseThrow();
        assertEquals(100000.00, pointJan1.invested(), 0.01, "Initial invested capital should be 100,000.00");

        // 3. Synthetic DISPOSAL on 2026-06-01:
        // Sale Proceeds: Rs 88,121.00 (7,343.4167 units @ Rs 12.0)
        // Cost Basis of sold units: Rs 76,038.00
        // Realized Gain: Rs 12,083.00
        TaxEvent eDisposal = new TaxEvent(
            "evt-disp-1", "asset_1", "Legacy Fund", "asset_1",
            EventType.DISPOSAL, LocalDate.parse("2026-06-01"),
            new BigDecimal("7343.4167"), new BigDecimal("12.00"), new BigDecimal("88121.00"),
            "doc-rebalance-sell", Instant.parse("2026-06-01T10:00:00Z")
        );
        projector.projectEvents(List.of(eDisposal));

        // INTERMEDIATE STATE CHECK:
        // Sell has fired, but buy leg has not redeployed yet.
        // total_invested MUST drop by the FULL SALE PROCEEDS (88,121.00):
        // 100,000.00 - 88,121.00 = 11,879.00
        List<NetWorthPoint> intermediateTrend = projector.getDailyNetWorthTrend();
        NetWorthPoint pointJun1 = intermediateTrend.stream()
            .filter(p -> p.date().equals("2026-06-01"))
            .findFirst()
            .orElseThrow();

        assertEquals(11879.00, pointJun1.invested(), 0.01,
            "Intermediate state: when DISPOSAL fires before ACQUISITION, total_invested must drop by full sale proceeds (100,000 - 88,121 = 11,879.00)");

        // 4. Subsequent ACQUISITION on 2026-06-02:
        // Reinvest full proceeds Rs 88,121.00 into Target Fund (asset_2)
        TaxEvent eAcquisition = new TaxEvent(
            "evt-acq-2", "asset_2", "Target Fund", "asset_2",
            EventType.ACQUISITION, LocalDate.parse("2026-06-02"),
            new BigDecimal("5874.7333"), new BigDecimal("15.00"), new BigDecimal("88121.00"),
            "doc-rebalance-buy", Instant.parse("2026-06-02T10:00:00Z")
        );
        projector.projectEvents(List.of(eAcquisition));

        // FINAL RECONCILIATION CHECK:
        // Across the full rebalance trade pair (-88,121 disposal + 88,121 acquisition):
        // total_invested on 2026-06-02 must return to EXACTLY 100,000.00 (0.00 net change across pair).
        List<NetWorthPoint> finalTrend = projector.getDailyNetWorthTrend();
        NetWorthPoint pointJun2 = finalTrend.stream()
            .filter(p -> p.date().equals("2026-06-02"))
            .findFirst()
            .orElseThrow();

        assertEquals(100000.00, pointJun2.invested(), 0.001,
            "Final state: Net active capital change across rebalance trade pair (-88,121 sale proceeds + 88,121 buy gross amount) must equal EXACTLY 0.00");
    }
}
