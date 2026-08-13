package com.portfolioos.core.service;

import com.portfolioos.core.dtos.RebalancePlanDtos.RebalanceTriggerDto;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.persistence.TriggerHistoryRepository;
import com.portfolioos.core.rules.BucketConfigLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RebalanceTriggerEvaluatorTest {

    private TriggerHistoryRepository repository;
    private RebalanceTriggerEvaluator evaluator;

    @BeforeEach
    void setUp() {
        repository = new TriggerHistoryRepository(":memory:");
        repository.clearAll();
        evaluator = new RebalanceTriggerEvaluator(repository);
    }

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.close();
        }
    }

    @Test
    @DisplayName("getCurrentStatus called twice in a row produces zero DB writes")
    void testGetCurrentStatusZeroSideEffects() {
        assertEquals(0, repository.getRecordCount(), "Initial DB must be empty");

        RebalanceTriggerEvaluator.TriggerResolution res1 = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("20000.00"), new BigDecimal("25000.00"), // 20% drawdown
            null, null, LocalDate.of(2026, 8, 1)
        );
        assertEquals("DRAWDOWN", res1.triggerType());
        assertEquals(0, repository.getRecordCount(), "getCurrentStatus call 1 must produce 0 DB side-effects");

        RebalanceTriggerEvaluator.TriggerResolution res2 = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("20000.00"), new BigDecimal("25000.00"),
            null, null, LocalDate.of(2026, 8, 1)
        );
        assertEquals("DRAWDOWN", res2.triggerType());
        assertEquals(0, repository.getRecordCount(), "getCurrentStatus call 2 must produce 0 DB side-effects");
    }

    @Test
    @DisplayName("30-day sell cooldown blocks DRAWDOWN sell plan")
    void testSellCooldownBlocksDrawdown() {
        LocalDate firstDate = LocalDate.of(2026, 8, 1);
        repository.recordExecution("plan-1", "DRAWDOWN", "DRAWDOWN_TIER_20", firstDate.atStartOfDay(), true, true, "");

        LocalDate testDate = LocalDate.of(2026, 8, 10); // 9 days later (< 30 days)
        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("20000.00"), new BigDecimal("25000.00"),
            null, null, testDate
        );

        assertTrue(res.sellCooldownActive());
        assertEquals(9, res.daysSinceLastSell());
        assertEquals("NONE", res.triggerType());
        assertEquals("DRAWDOWN_BLOCKED_BY_COOLDOWN", res.reasonCode());
    }

    @Test
    @DisplayName("GOLD_FLOOR_BACKSTOP co-fires despite active 30-day sell cooldown")
    void testGoldFloorBackstopBypassesSellCooldown() {
        LocalDate lastSellDate = LocalDate.of(2026, 8, 1);
        repository.recordExecution("plan-1", "DRAWDOWN", "DRAWDOWN_TIER_20", lastSellDate.atStartOfDay(), true, false, "");

        LocalDate testDate = LocalDate.of(2026, 8, 10); // 9 days after sell (< 30 days)
        // Gold idle for 7 months (> 6 months)
        LocalDate lastGoldBuyDate = testDate.minusMonths(7);
        repository.recordExecution("plan-gold-old", "DRIFT", "DRIFT", lastGoldBuyDate.atStartOfDay(), false, true, "");

        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("25000.00"), new BigDecimal("25000.00"), // No drawdown
            null, null, testDate
        );

        assertTrue(res.sellCooldownActive(), "Sell cooldown should be active");
        assertTrue(res.goldIdleActive(), "Gold idle should be active (7 months)");
        assertEquals("GOLD_FLOOR_BACKSTOP", res.triggerType(), "Gold floor backstop must fire despite active sell cooldown");
        assertFalse(res.hasSellSide());
        assertTrue(res.hasGoldBuy());
    }

    @Test
    @DisplayName("getLastGoldBuyDate contract queries has_gold_buy = 1 across all trigger types and ignores has_gold_buy = 0")
    void testLastGoldBuyDateQueryContract() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 10, 0);
        repository.recordExecution("p1", "DRIFT", "DRIFT", t1, true, true, "");

        assertTrue(repository.getLastGoldBuyDate().isPresent());
        assertEquals(t1, repository.getLastGoldBuyDate().get());

        LocalDateTime t2 = LocalDateTime.of(2026, 5, 1, 10, 0);
        repository.recordExecution("p2", "DRAWDOWN", "DRAWDOWN_TIER_15", t2, true, true, "");

        assertEquals(t2, repository.getLastGoldBuyDate().get(), "getLastGoldBuyDate must return latest timestamp where has_gold_buy = 1");

        // Add later row t3 with has_gold_buy = 0
        LocalDateTime t3 = LocalDateTime.of(2026, 6, 1, 10, 0);
        repository.recordExecution("p3", "DRAWDOWN", "DRAWDOWN_TIER_20", t3, true, false, "");

        // Must STILL return t2 (May 1), proving WHERE has_gold_buy = 1 filter is load-bearing!
        assertEquals(t2, repository.getLastGoldBuyDate().get(), "Must ignore later row t3 because has_gold_buy = 0");
    }

    @Test
    @DisplayName("Priority suppression: DRAWDOWN suppresses DRIFT and SCHEDULED")
    void testPrioritySuppressionDrawdownOverDriftAndScheduled() {
        // Drawdown active (20% drawdown) + March 15 window (scheduled month)
        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("20000.00"), new BigDecimal("25000.00"), // 20% drawdown
            null, null, LocalDate.of(2026, 3, 15)
        );

        assertEquals("DRAWDOWN", res.triggerType(), "DRAWDOWN must win over DRIFT and SCHEDULED");
        assertEquals("DRAWDOWN_TIER_20", res.reasonCode());
        assertTrue(res.hasSellSide());
    }

    @Test
    @DisplayName("Priority suppression: DRIFT suppresses SCHEDULED")
    void testPrioritySuppressionDriftOverScheduled() {
        // No drawdown (current == high) + March 15 window + drifted bucket (openLots empty -> buckets at 0% vs 50% target)
        RebalanceTriggerEvaluator.TriggerResolution res = evaluator.getCurrentStatus(
            Collections.emptyList(), Collections.emptyMap(),
            new BigDecimal("25000.00"), new BigDecimal("25000.00"), // 0% drawdown
            null, null, LocalDate.of(2026, 3, 15)
        );

        assertEquals("DRIFT", res.triggerType(), "DRIFT must win over SCHEDULED when drawdown is zero");
        assertEquals("DRIFT_THRESHOLD_EXCEEDED", res.reasonCode());
        assertTrue(res.hasSellSide());
    }

    @Test
    @DisplayName("RebalanceTriggerDto constructor populates legacyTriggerType as INDUCED for DRAWDOWN/DRIFT")
    void testRebalanceTriggerDtoBackwardCompatibility() {
        RebalanceTriggerDto dto1 = new RebalanceTriggerDto("DRAWDOWN", "DRAWDOWN_TIER_15", "15% Drawdown", "Window", null);
        assertEquals("DRAWDOWN", dto1.type());
        assertEquals("INDUCED", dto1.legacyTriggerType());
        assertTrue(dto1.isInduced());

        RebalanceTriggerDto dto2 = new RebalanceTriggerDto("DRIFT", "DRIFT_THRESHOLD_EXCEEDED", "Drift Exceeded", "Window", null);
        assertEquals("DRIFT", dto2.type());
        assertEquals("INDUCED", dto2.legacyTriggerType());
        assertTrue(dto2.isInduced());

        RebalanceTriggerDto dto3 = new RebalanceTriggerDto("SCHEDULED", "SCHEDULED_RECONSTITUTION", "Scheduled", "Window", null);
        assertEquals("SCHEDULED", dto3.type());
        assertEquals("SCHEDULED", dto3.legacyTriggerType());
        assertFalse(dto3.isInduced());
    }
}
