package com.portfolioos.core.rules;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.reporting.ExemptionTracker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class FireActionRuleEngineTest {

    @Test
    public void testExemptionHeadroomReductionAndFifoLotAwareness() {
        FireActionRuleEngine engine = new FireActionRuleEngine();

        // 1. Prepare simulated pairwise overlap data (Value 30 vs PPFAS @ 23.56%)
        Map<String, Object> overlapPair = new HashMap<>();
        overlapPair.put("fund_a", "INF109KC13X2"); // Value 30
        overlapPair.put("fund_b", "INF879O01027"); // PPFAS Flexi Cap
        overlapPair.put("overlap_percentage", 23.56);
        overlapPair.put("common_stock_count", 5);
        List<Map<String, Object>> pairwise = List.of(overlapPair);

        // 2. Prepare specific open lots for Value 30 (INF109KC13X2) - Oldest lot acquired 500 days ago
        Lot value30OldLot = new Lot(
            "LOT_V30_1",
            "INF109KC13X2",
            "Value 30 Index Fund",
            LocalDate.now().minusDays(500),
            new BigDecimal("100.00"),
            new BigDecimal("100.00"),
            new BigDecimal("150.00"),
            new BigDecimal("15000.00"),
            false,
            BigDecimal.ZERO
        );
        List<Lot> openLots = List.of(value30OldLot);

        // 3. Scenario A: No prior disposals in FY 2026-27 (Full ₹125,000 Exemption Headroom)
        ExemptionTracker.ExemptionStatus exFull = ExemptionTracker.calculateExemptionStatus(Collections.emptyList(), "2026-27");
        assertEquals("125000.00", exFull.exemptionRemaining());

        List<FireActionRuleEngine.ActionRecommendationCard> cardsA = engine.evaluateRules(
            null, false, pairwise, Collections.emptyList(), openLots, exFull
        );
        FireActionRuleEngine.ActionRecommendationCard cardA = cardsA.stream()
            .filter(c -> "CARD_OVERLAP_ACTION".equals(c.cardId()))
            .findFirst()
            .orElseThrow();

        assertTrue(cardA.detailedRationale().contains("exemption headroom of ₹125,000"));
        assertEquals(125000.0, ((Number) cardA.metrics().get("remaining_ltcg_exemption_headroom")).doubleValue());
        assertTrue((Boolean) cardA.metrics().get("fifo_lot_ltcg_eligible"));

        // 4. Scenario B: Prior disposal in FY 2026-27 consuming ₹45,000 LTCG exemption
        MatchedLot priorLtcgLot = new MatchedLot(
            "MATCH_1",
            "DISP_1",
            "LOT_1",
            "INF109KC12U0",
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2026, 6, 15),
            new BigDecimal("100"),
            new BigDecimal("10000"),
            new BigDecimal("55000"),
            new BigDecimal("45000.00"), // ₹45,000 realized LTCG gain
            900,
            TaxTerm.LONG_TERM,
            AssetCategory.EQUITY
        );
        ExemptionTracker.ExemptionStatus exPartial = ExemptionTracker.calculateExemptionStatus(List.of(priorLtcgLot), "2026-27");
        assertEquals("80000.00", exPartial.exemptionRemaining()); // ₹125,000 - ₹45,000 = ₹80,000

        List<FireActionRuleEngine.ActionRecommendationCard> cardsB = engine.evaluateRules(
            null, false, pairwise, Collections.emptyList(), openLots, exPartial
        );
        FireActionRuleEngine.ActionRecommendationCard cardB = cardsB.stream()
            .filter(c -> "CARD_OVERLAP_ACTION".equals(c.cardId()))
            .findFirst()
            .orElseThrow();

        // Dynamic Exemption Verification: Rationale text MUST reflect ₹80,000 remaining headroom!
        assertTrue(cardB.detailedRationale().contains("exemption headroom of ₹80,000"),
            "Expected card rationale to dynamically reflect ₹80,000 remaining headroom, got: " + cardB.detailedRationale());
        assertEquals(80000.0, ((Number) cardB.metrics().get("remaining_ltcg_exemption_headroom")).doubleValue());

        System.out.println("=== FIRE ACTION RULE ENGINE UNIT TEST PASSED ===");
        System.out.println("Full Headroom Rationale    : " + cardA.detailedRationale());
        System.out.println("Consumed Headroom Rationale: " + cardB.detailedRationale());
    }
}
