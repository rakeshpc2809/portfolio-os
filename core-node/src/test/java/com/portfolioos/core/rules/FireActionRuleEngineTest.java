package com.portfolioos.core.rules;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.Lot;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.reporting.ExemptionTracker;
import org.junit.jupiter.api.DisplayName;
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
            null, false, 33.15, 0.84, new BigDecimal("75000"), pairwise, Collections.emptyList(), openLots, exFull
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
            null, false, 33.15, 0.84, new BigDecimal("75000"), pairwise, Collections.emptyList(), openLots, exPartial
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

    @Test
    public void testConcentrationActionWithPortfolioPercentageKey() {
        FireActionRuleEngine engine = new FireActionRuleEngine();

        // Prepare single-stock concentration data using the canonical portfolio_percentage key
        // HDFCBANK benchmark weight in NIFTY50_BENCHMARK_WEIGHTS is ~11.5% or default 1.50
        // Top stock with portfolio_percentage = 8.5% against default 1.50% gives activeOverweight = 7.0% (> 2.50%)
        Map<String, Object> concentrationItem = new HashMap<>();
        concentrationItem.put("stock_symbol", "RELIANCE");
        concentrationItem.put("rupee_exposure", 250000.0);
        concentrationItem.put("portfolio_percentage", 12.5); // benchmark is 9.50%, activeOverweight = 3.0% > 2.50%
        concentrationItem.put("is_audited", true);

        List<Map<String, Object>> concentrations = List.of(concentrationItem);

        ExemptionTracker.ExemptionStatus exFull = ExemptionTracker.calculateExemptionStatus(Collections.emptyList(), "2026-27");
        List<FireActionRuleEngine.ActionRecommendationCard> cards = engine.evaluateRules(
            null, false, 33.15, 0.84, new BigDecimal("75000"), Collections.emptyList(), concentrations, Collections.emptyList(), exFull
        );

        FireActionRuleEngine.ActionRecommendationCard concCard = cards.stream()
            .filter(c -> "CARD_CONCENTRATION_ACTION".equals(c.cardId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected CARD_CONCENTRATION_ACTION to fire when active overweight > 2.50%"));

        assertNotNull(concCard);
        assertEquals("ACTIVE_CONCENTRATION", concCard.category());
        assertTrue(concCard.detailedRationale().contains("RELIANCE"));
        assertEquals(12.5, ((Number) concCard.metrics().get("blended_weight_pct")).doubleValue());
    }

    @Test
    public void testGuytonKlingerHighPeCausesSwrContractionAndUpperGuardrailBreach() {
        FireActionRuleEngine engine = new FireActionRuleEngine();

        // 1. Setup FireSummary:
        // Annual expense: ₹720,000 (₹60,000/mo)
        // Investable net worth: ₹20,000,000 (₹2 Crore) -> Withdrawal rate = 720k / 20M = 3.60%
        com.portfolioos.core.fire.FireTracker.FireSummary fireSummary = new com.portfolioos.core.fire.FireTracker.FireSummary(
            "Primary Expense Target",
            new BigDecimal("60000.00"),
            new BigDecimal("75000.00"),
            new BigDecimal("720000.00"),
            new BigDecimal("24000000.00"),
            new BigDecimal("23000000.00"), // total net worth
            new BigDecimal("2000000.00"),  // epf balance
            new BigDecimal("1000000.00"),  // non-retirement goals
            new BigDecimal("20000000.00"), // investable net worth (23M - 2M - 1M = 20M)
            new BigDecimal("35000000.00"), // projected corpus at age 45
            13,
            "ON_TRACK",
            new BigDecimal("11000000.00"),
            false,
            Collections.emptyList(),
            95.0,
            new BigDecimal("35000000.00"),
            new BigDecimal("26000000.00")
        );

        BigDecimal totalMFValue = new BigDecimal("17000000.00"); // pure MF (3M is bank cash buffer)

        // Nifty 50 PE = 26.5 (>= 25.0, EXPENSIVE zone -> Base SWR 3.00% - 0.40% = 2.60%)
        // Upper guardrail = 2.60% * 1.20 = 3.12%
        // Current withdrawal rate = 3.60% > 3.12% -> triggers UPPER GUARDRAIL WARNING
        MarketIndicatorsReader.MarketIndicators highPeIndicators = new MarketIndicatorsReader.MarketIndicators(
            LocalDate.of(2026, 9, 1),
            7.10,
            26.50,
            false,
            "LIVE_NSE",
            "Test Notes"
        );

        FireActionRuleEngine.ActionRecommendationCard card = engine.evaluateGuytonKlingerCapeRule(
            fireSummary, totalMFValue, highPeIndicators
        );

        assertNotNull(card);
        assertEquals("CARD_DECUMULATION_GUARDRAIL", card.cardId());
        assertEquals("DECUMULATION_GUARDRAIL", card.category());
        assertEquals("ACTION_RECOMMENDED", card.status());
        assertEquals("HIGH", card.severity());
        assertTrue(card.summary().contains("exceeds GK upper guardrail"));

        // Verify metrics
        assertEquals(3.00, ((Number) card.metrics().get("base_swr_pct")).doubleValue());
        assertEquals(2.60, ((Number) card.metrics().get("cape_adjusted_swr_pct")).doubleValue());
        assertEquals(3.12, ((Number) card.metrics().get("upper_guardrail_pct")).doubleValue());
        assertEquals(3.60, ((Number) card.metrics().get("current_withdrawal_rate_pct")).doubleValue());
        assertEquals(26.50, ((Number) card.metrics().get("nifty50_pe")).doubleValue());
        assertEquals("EXPENSIVE", card.metrics().get("pe_valuation_zone"));
        assertEquals(20000000.0, ((Number) card.metrics().get("investable_net_worth")).doubleValue());
        assertEquals(17000000.0, ((Number) card.metrics().get("pure_equity_debt_mf_worth")).doubleValue());

        // Verify Mode B metrics
        assertEquals(35000000.0, ((Number) card.metrics().get("projected_corpus_at_target_age")).doubleValue());
        assertEquals("ON_TRACK", card.metrics().get("target_on_track_status"));

        System.out.println("=== GUYTON-KLINGER HIGH PE BREACH TEST PASSED ===");
        System.out.println("Nifty PE: " + card.metrics().get("nifty50_pe") + " -> Contracted SWR: " + card.metrics().get("cape_adjusted_swr_pct") + "%");
        System.out.println("Upper Guardrail: " + card.metrics().get("upper_guardrail_pct") + "% vs Current Rate: " + card.metrics().get("current_withdrawal_rate_pct") + "%");
        System.out.println("Status: " + card.status() + " | Severity: " + card.severity());
    }

    @Test
    public void testGuytonKlingerLowPeCausesSwrExpansionAndProsperityHeadroom() {
        FireActionRuleEngine engine = new FireActionRuleEngine();

        // Annual expense: ₹720,000
        // Investable net worth: ₹40,000,000 (₹4 Crore) -> Withdrawal rate = 720k / 40M = 1.80%
        com.portfolioos.core.fire.FireTracker.FireSummary fireSummary = new com.portfolioos.core.fire.FireTracker.FireSummary(
            "Primary Expense Target",
            new BigDecimal("60000.00"),
            new BigDecimal("75000.00"),
            new BigDecimal("720000.00"),
            new BigDecimal("24000000.00"),
            new BigDecimal("43000000.00"),
            new BigDecimal("2000000.00"),
            new BigDecimal("1000000.00"),
            new BigDecimal("40000000.00"),
            new BigDecimal("65000000.00"),
            13,
            "ON_TRACK",
            new BigDecimal("41000000.00"),
            false,
            Collections.emptyList(),
            98.0,
            new BigDecimal("65000000.00"),
            new BigDecimal("50000000.00")
        );

        BigDecimal totalMFValue = new BigDecimal("38000000.00");

        // Nifty 50 PE = 16.5 (<= 18.0, UNDERVALUED zone -> Base SWR 3.00% + 0.35% = 3.35%)
        // Lower guardrail = 3.35% * 0.80 = 2.68%
        // Current withdrawal rate = 1.80% < 2.68% -> triggers PROSPERITY HEADROOM
        MarketIndicatorsReader.MarketIndicators lowPeIndicators = new MarketIndicatorsReader.MarketIndicators(
            LocalDate.of(2026, 9, 1),
            6.95,
            16.50,
            false,
            "LIVE_NSE",
            "Test Notes"
        );

        FireActionRuleEngine.ActionRecommendationCard card = engine.evaluateGuytonKlingerCapeRule(
            fireSummary, totalMFValue, lowPeIndicators
        );

        assertNotNull(card);
        assertEquals("CARD_DECUMULATION_GUARDRAIL", card.cardId());
        assertEquals("DECUMULATION_GUARDRAIL", card.category());
        assertEquals("INFORMATIONAL_STABLE", card.status());
        assertEquals("INFO", card.severity());
        assertTrue(card.summary().contains("below GK lower guardrail"));

        assertEquals(3.35, ((Number) card.metrics().get("cape_adjusted_swr_pct")).doubleValue());
        assertEquals(2.68, ((Number) card.metrics().get("lower_guardrail_pct")).doubleValue());
        assertEquals(1.80, ((Number) card.metrics().get("current_withdrawal_rate_pct")).doubleValue());
        assertEquals("UNDERVALUED", card.metrics().get("pe_valuation_zone"));

        // ERP = (1/16.5)*100 - 6.95 = 6.06% - 6.95% = -0.89%
        double expectedErp = Math.round(((1.0 / 16.5) * 100.0 - 6.95) * 100.0) / 100.0;
        assertEquals(expectedErp, ((Number) card.metrics().get("equity_risk_premium_pct")).doubleValue());

        System.out.println("=== GUYTON-KLINGER LOW PE EXPANSION TEST PASSED ===");
        System.out.println("Nifty PE: " + card.metrics().get("nifty50_pe") + " -> Expanded SWR: " + card.metrics().get("cape_adjusted_swr_pct") + "%");
        System.out.println("Lower Guardrail: " + card.metrics().get("lower_guardrail_pct") + "% vs Current Rate: " + card.metrics().get("current_withdrawal_rate_pct") + "%");
        System.out.println("Computed ERP: " + card.metrics().get("equity_risk_premium_pct") + "%");
    }

    @Test
    public void testGuytonKlingerCorridorStableState() {
        FireActionRuleEngine engine = new FireActionRuleEngine();

        // Annual expense: ₹720,000
        // Investable net worth: ₹25,000,000 -> Withdrawal rate = 720k / 25M = 2.88%
        com.portfolioos.core.fire.FireTracker.FireSummary fireSummary = new com.portfolioos.core.fire.FireTracker.FireSummary(
            "Primary Expense Target",
            new BigDecimal("60000.00"),
            new BigDecimal("75000.00"),
            new BigDecimal("720000.00"),
            new BigDecimal("24000000.00"),
            new BigDecimal("27000000.00"),
            new BigDecimal("1500000.00"),
            new BigDecimal("500000.00"),
            new BigDecimal("25000000.00"),
            new BigDecimal("42000000.00"),
            13,
            "ON_TRACK",
            new BigDecimal("18000000.00"),
            false,
            Collections.emptyList(),
            96.0,
            new BigDecimal("42000000.00"),
            new BigDecimal("32000000.00")
        );

        // Fair value Nifty PE = 22.40 (18 < PE < 25 -> Base SWR remains 3.00%)
        // Upper guardrail = 3.60%, Lower guardrail = 2.40%
        // Current withdrawal rate = 2.88% (comfortably inside [2.40%, 3.60%])
        MarketIndicatorsReader.MarketIndicators fairValueIndicators = new MarketIndicatorsReader.MarketIndicators(
            LocalDate.of(2026, 9, 1),
            7.10,
            22.40,
            true,
            "FALLBACK_CACHED",
            "Statutory benchmark"
        );

        FireActionRuleEngine.ActionRecommendationCard card = engine.evaluateGuytonKlingerCapeRule(
            fireSummary, new BigDecimal("22000000.00"), fairValueIndicators
        );

        assertNotNull(card);
        assertEquals("INFORMATIONAL_STABLE", card.status());
        assertEquals("INFO", card.severity());
        assertTrue(card.summary().contains("Decumulation corridor stable"));
        assertEquals("FAIR_VALUE", card.metrics().get("pe_valuation_zone"));
        assertEquals(3.00, ((Number) card.metrics().get("cape_adjusted_swr_pct")).doubleValue());
        assertEquals(2.88, ((Number) card.metrics().get("current_withdrawal_rate_pct")).doubleValue());
        assertTrue((Boolean) card.metrics().get("is_market_indicator_fallback"));

        System.out.println("=== GUYTON-KLINGER CORRIDOR STABLE TEST PASSED ===");
        System.out.println("Summary  : " + card.summary());
        System.out.println("Rationale: " + card.detailedRationale());
    }

    @Test
    public void testDenominatorStrictlyExcludesEpfAndGoalsWhileIncludingBankBuffer() {
        FireActionRuleEngine engine = new FireActionRuleEngine();

        // Total Net Worth: ₹10,000,000
        // EPF Balance: ₹2,000,000 (MUST BE EXCLUDED)
        // Non-Retirement Goals: ₹1,500,000 (MUST BE EXCLUDED)
        // Bank Buffer: ₹1,500,000 (INCLUDED in investable)
        // Pure MF: ₹6,500,000 -> Total Investable = 10M - 2M - 1.5M = ₹6,500,000 (pure MF + bank - goals)
        // Annual Expense: ₹650,000 -> R_curr = 650k / 6.5M = 10.00%
        com.portfolioos.core.fire.FireTracker.FireSummary fireSummary = new com.portfolioos.core.fire.FireTracker.FireSummary(
            "Primary Expense Target",
            new BigDecimal("54166.67"),
            new BigDecimal("50000.00"),
            new BigDecimal("650000.00"),
            new BigDecimal("21666667.00"),
            new BigDecimal("10000000.00"), // total net worth
            new BigDecimal("2000000.00"),  // epf balance
            new BigDecimal("1500000.00"),  // non-retirement goals
            new BigDecimal("6500000.00"),  // investable net worth
            new BigDecimal("22000000.00"),
            13,
            "ON_TRACK",
            new BigDecimal("333333.00"),
            false,
            Collections.emptyList(),
            95.0,
            new BigDecimal("22000000.00"),
            new BigDecimal("16000000.00")
        );

        BigDecimal totalMFValue = new BigDecimal("6500000.00");
        MarketIndicatorsReader.MarketIndicators indicators = new MarketIndicatorsReader.MarketIndicators(
            LocalDate.of(2026, 9, 1), 7.10, 22.40, false, "LIVE", "Notes"
        );

        FireActionRuleEngine.ActionRecommendationCard card = engine.evaluateGuytonKlingerCapeRule(
            fireSummary, totalMFValue, indicators
        );

        assertEquals(6500000.0, ((Number) card.metrics().get("investable_net_worth")).doubleValue());
        assertEquals(6500000.0, ((Number) card.metrics().get("pure_equity_debt_mf_worth")).doubleValue());
        assertEquals(10.0, ((Number) card.metrics().get("current_withdrawal_rate_pct")).doubleValue());
        // 10.00% > 3.60% upper guardrail -> HIGH warning
        assertEquals("HIGH", card.severity());
        assertEquals("ACTION_RECOMMENDED", card.status());

        System.out.println("=== DENOMINATOR EXCLUSION TEST PASSED ===");
        System.out.println("Investable Net Worth Denominator: ₹" + card.metrics().get("investable_net_worth"));
        System.out.println("Computed Withdrawal Rate        : " + card.metrics().get("current_withdrawal_rate_pct") + "%");
    }

    @Test
    public void testGuytonKlingerFallbackIndicatorsWhenMissing() {
        FireActionRuleEngine engine = new FireActionRuleEngine();

        com.portfolioos.core.fire.FireTracker.FireSummary fireSummary = new com.portfolioos.core.fire.FireTracker.FireSummary(
            "Primary Expense Target",
            new BigDecimal("60000.00"),
            new BigDecimal("75000.00"),
            new BigDecimal("720000.00"),
            new BigDecimal("24000000.00"),
            new BigDecimal("25000000.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("25000000.00"),
            new BigDecimal("40000000.00"),
            13,
            "ON_TRACK",
            new BigDecimal("16000000.00"),
            false,
            Collections.emptyList(),
            95.0,
            new BigDecimal("40000000.00"),
            new BigDecimal("30000000.00")
        );

        // Passing null indicators safely engages statutory defaults
        FireActionRuleEngine.ActionRecommendationCard card = engine.evaluateGuytonKlingerCapeRule(
            fireSummary, new BigDecimal("25000000.00"), null
        );

        assertNotNull(card);
        assertEquals(22.40, ((Number) card.metrics().get("nifty50_pe")).doubleValue());
        assertEquals(7.10, ((Number) card.metrics().get("gsec_10y_yield_pct")).doubleValue());
        assertTrue((Boolean) card.metrics().get("is_market_indicator_fallback"));
        assertEquals("FAIR_VALUE", card.metrics().get("pe_valuation_zone"));

        System.out.println("=== FALLBACK INDICATOR TEST PASSED ===");
        System.out.println("Default Nifty PE: " + card.metrics().get("nifty50_pe"));
        System.out.println("Fallback Flag   : " + card.metrics().get("is_market_indicator_fallback"));
    }

    @Test
    public void testGuytonKlingerBankBalanceGatedWhenUnpopulated() {
        FireActionRuleEngine engine = new FireActionRuleEngine();

        // Simulate live state where bankBalance is 0.0 (inferred bank balance = totalNetWorth - totalMFValue - epf = 0)
        // Total net worth = 1,729,557.83 (pure MF)
        // Non-retirement goals = 350,000.00
        // Investable = 1,379,557.83
        // Annual expense = 720,000.00 -> R_curr = 52.19% > 3.60% upper guardrail
        // But because bankBalance is unpopulated, it MUST gate to GATED_PROVISIONAL rather than firing HIGH action panic.
        com.portfolioos.core.fire.FireTracker.FireSummary fireSummary = new com.portfolioos.core.fire.FireTracker.FireSummary(
            "Primary Expense Target",
            new BigDecimal("60000.00"),
            new BigDecimal("75000.00"),
            new BigDecimal("720000.00"),
            new BigDecimal("24000000.00"),
            new BigDecimal("1729557.83"), // total net worth (equals pure MF value)
            BigDecimal.ZERO,              // epf balance
            new BigDecimal("350000.00"),  // non-retirement goals
            new BigDecimal("1379557.83"), // investable net worth
            new BigDecimal("19936421.79"),
            13,
            "SHORT",
            new BigDecimal("4063578.21"),
            false,
            Collections.emptyList(),
            0.0,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );

        BigDecimal totalMFValue = new BigDecimal("1729557.83");
        MarketIndicatorsReader.MarketIndicators indicators = new MarketIndicatorsReader.MarketIndicators(
            LocalDate.of(2026, 8, 31), 7.10, 22.40, true, "FALLBACK", "Notes"
        );

        FireActionRuleEngine.ActionRecommendationCard card = engine.evaluateGuytonKlingerCapeRule(
            fireSummary, totalMFValue, indicators
        );

        assertNotNull(card);
        assertEquals("GATED_PROVISIONAL", card.status());
        assertEquals("INFO", card.severity());
        assertTrue(card.summary().contains("Bank balance unpopulated"));
        assertTrue(card.provenanceFooter().contains("Bank Cash Buffer: UNPOPULATED"));

        System.out.println("=== BANK BALANCE GATED TEST PASSED ===");
        System.out.println("Status  : " + card.status() + " | Severity: " + card.severity());
        System.out.println("Summary : " + card.summary());
    }

    @Test
    @DisplayName("Market Indicators: Repo rate defaults to 5.25% and yield curve slope evaluates to 10Y - Repo")
    void testMarketIndicatorsRepoRateAndYieldCurveSlope() {
        MarketIndicatorsReader reader = new MarketIndicatorsReader();
        MarketIndicatorsReader.MarketIndicators indicators = reader.readIndicators();

        assertNotNull(indicators);
        // Statutory default / cached repo rate is 5.25% (August 2026 RBI MPC decision)
        assertEquals(5.25, indicators.repoRatePct(), 0.001);
        assertEquals(7.10, indicators.gsec10yYieldPct(), 0.001);
        // Slope = 7.10 - 5.25 = +1.85% (+185 bps)
        assertEquals(1.85, indicators.yieldCurveSlopePct(), 0.001);
    }
}

