package com.portfolioos.core.valuation;

import com.portfolioos.core.model.Lot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BucketEngineTest {

    @Test
    @DisplayName("BucketEngine calculates bucket allocation with valid live NAV")
    void testEvaluateRebalanceSuccess() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        Lot coreLot = new Lot("lot-1", "INF109KC12U0", "ICICI LargeMidcap 250", date,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null);

        Map<String, BigDecimal> navMap = Map.of("INF109KC12U0", new BigDecimal("150"));
        BucketEngine.RebalanceEngineResult result = BucketEngine.evaluateRebalance(
            List.of(coreLot), List.of(), navMap, date, BigDecimal.ZERO, BigDecimal.ZERO, BucketEngine.DEFAULT_TARGETS, "2026-27"
        );

        assertNotNull(result);
        assertEquals(5, result.bucketStatuses().size());
        BucketEngine.BucketStatus coreStatus = result.bucketStatuses().stream()
            .filter(b -> b.bucket() == BucketEngine.Bucket.EQUITY_CORE)
            .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("15000").compareTo(coreStatus.currentValue()));
    }

    @Test
    @DisplayName("Fail-Loud Invariant: BucketEngine throws IllegalStateException on missing NAV")
    void testEvaluateRebalanceThrowsOnMissingNav() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        Lot coreLot = new Lot("lot-1", "INF109KC12U0", "ICICI LargeMidcap 250", date,
            new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10000"), false, null);

        Map<String, BigDecimal> emptyNavMap = Map.of();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            BucketEngine.evaluateRebalance(
                List.of(coreLot), List.of(), emptyNavMap, date, BigDecimal.ZERO, BigDecimal.ZERO, BucketEngine.DEFAULT_TARGETS, "2026-27"
            )
        );
        assertTrue(ex.getMessage().contains("CRITICAL VALUATION ERROR"));
        assertTrue(ex.getMessage().contains("INF109KC12U0"));
    }

    @Test
    @DisplayName("Regime-Conditional Target Shifting across all 5 drawdown regimes")
    void testCalculateRegimeShiftedTargetsAllRegimes() {
        List<BucketEngine.BucketTarget> baseTargets = BucketEngine.DEFAULT_TARGETS; // 50% Core, 30% Sat, 10% Gold, 10% Liquid

        // Regime 1: Normal / Recovery (DD < 5%) -> Standard targets
        List<BucketEngine.BucketTarget> normalTargets = BucketEngine.calculateRegimeShiftedTargets(baseTargets, 3.2, new BigDecimal("10.0"));
        assertEquals(0, new BigDecimal("10.00").compareTo(findTarget(normalTargets, BucketEngine.Bucket.LIQUID_BUFFER)));
        assertEquals(0, new BigDecimal("50.00").compareTo(findTarget(normalTargets, BucketEngine.Bucket.EQUITY_CORE)));

        // Regime 2: Hold Zone (5% <= DD < 10%) with depleted buffer -> Freeze at current %, Core absorbs delta
        List<BucketEngine.BucketTarget> holdTargetsDepleted = BucketEngine.calculateRegimeShiftedTargets(baseTargets, 7.5, new BigDecimal("5.0"));
        assertEquals(0, new BigDecimal("5.00").compareTo(findTarget(holdTargetsDepleted, BucketEngine.Bucket.LIQUID_BUFFER)));
        assertEquals(0, new BigDecimal("55.00").compareTo(findTarget(holdTargetsDepleted, BucketEngine.Bucket.EQUITY_CORE)));

        // Regime 2b: Hold Zone (5% <= DD < 10%) with full buffer -> Stays at base targets
        List<BucketEngine.BucketTarget> holdTargetsFull = BucketEngine.calculateRegimeShiftedTargets(baseTargets, 7.5, new BigDecimal("10.0"));
        assertEquals(0, new BigDecimal("10.00").compareTo(findTarget(holdTargetsFull, BucketEngine.Bucket.LIQUID_BUFFER)));
        assertEquals(0, new BigDecimal("50.00").compareTo(findTarget(holdTargetsFull, BucketEngine.Bucket.EQUITY_CORE)));

        // Regime 3: Correction Rung 1 (10% <= DD < 15%) -> Liquid 7.5%, Core 52.5%
        List<BucketEngine.BucketTarget> rung1Targets = BucketEngine.calculateRegimeShiftedTargets(baseTargets, 12.0, new BigDecimal("10.0"));
        assertEquals(0, new BigDecimal("7.50").compareTo(findTarget(rung1Targets, BucketEngine.Bucket.LIQUID_BUFFER)));
        assertEquals(0, new BigDecimal("52.50").compareTo(findTarget(rung1Targets, BucketEngine.Bucket.EQUITY_CORE)));

        // Regime 4: Bear Market Rung 2 (15% <= DD < 20%) -> Liquid 5.0%, Core 55.0%
        List<BucketEngine.BucketTarget> rung2Targets = BucketEngine.calculateRegimeShiftedTargets(baseTargets, 16.5, new BigDecimal("7.5"));
        assertEquals(0, new BigDecimal("5.00").compareTo(findTarget(rung2Targets, BucketEngine.Bucket.LIQUID_BUFFER)));
        assertEquals(0, new BigDecimal("55.00").compareTo(findTarget(rung2Targets, BucketEngine.Bucket.EQUITY_CORE)));

        // Regime 5: Capitulation Rung 3 (DD >= 20%) -> Liquid 0.0%, Core 60.0%
        List<BucketEngine.BucketTarget> rung3Targets = BucketEngine.calculateRegimeShiftedTargets(baseTargets, 22.0, new BigDecimal("5.0"));
        assertEquals(0, new BigDecimal("0.00").compareTo(findTarget(rung3Targets, BucketEngine.Bucket.LIQUID_BUFFER)));
        assertEquals(0, new BigDecimal("60.00").compareTo(findTarget(rung3Targets, BucketEngine.Bucket.EQUITY_CORE)));

        // Invariant: In all regimes, sum of active targets equals 100.00%
        for (List<BucketEngine.BucketTarget> tList : List.of(normalTargets, holdTargetsDepleted, holdTargetsFull, rung1Targets, rung2Targets, rung3Targets)) {
            BigDecimal sum = tList.stream().map(BucketEngine.BucketTarget::targetPct).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, new BigDecimal("100.00").compareTo(sum), "Sum of active targets must strictly equal 100%");
        }
    }

    @Test
    @DisplayName("Drawdown Rung 3 Execution in BucketEngine: Emits SELL Arbitrage + BUY Core, ZERO conflicting drift BUYs")
    void testDrawdownRungExecutionInBucketEngineNoConflictingDriftRecommendations() {
        LocalDate date = LocalDate.of(2026, 8, 10);
        // Portfolio ₹10,00,000 at peak:
        // Core: ₹5,00,000 (50%)
        // Arbitrage: ₹1,00,000 (10%)
        // Satellite: ₹3,00,000 (30%)
        // Gold: ₹1,00,000 (10%)
        Lot coreLot = new Lot("lot-1", "INF109KC12U0", "ICICI LargeMidcap 250 Index Fund", date,
            new BigDecimal("5000"), new BigDecimal("5000"), new BigDecimal("100"), new BigDecimal("500000"), false, null);
        Lot arbLot = new Lot("lot-2", "INF205K01QU7", "Invesco India Arbitrage Fund", date,
            new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("100000"), false, null);

        Map<String, BigDecimal> navMap = Map.of(
            "INF109KC12U0", new BigDecimal("100"),
            "INF205K01QU7", new BigDecimal("100")
        );

        // 20% drawdown on Nifty 500
        BigDecimal benchmarkHigh = new BigDecimal("10000.00");
        BigDecimal benchmarkCurrent = new BigDecimal("8000.00");

        BucketEngine.RebalanceEngineResult result = BucketEngine.evaluateRebalance(
            List.of(coreLot, arbLot), List.of(), navMap, date, benchmarkCurrent, benchmarkHigh, BucketEngine.DEFAULT_TARGETS, "2026-27"
        );

        assertNotNull(result);
        assertTrue(result.drawdownTriggerFired(), "Drawdown trigger must fire on 20% benchmark drop");
        assertEquals(0, new BigDecimal("20.00").compareTo(result.drawdownStatus().drawdownPct()));
        assertEquals(0, new BigDecimal("100.0").compareTo(result.drawdownStatus().recommendedBufferDeployPct()));

        // Check recommendations: Must contain SELL for Arbitrage, BUY for Core, and ZERO secondary BUY for Arbitrage
        List<BucketEngine.RebalanceRecommendation> recs = result.recommendations();
        assertFalse(recs.isEmpty());

        BucketEngine.RebalanceRecommendation sellArb = recs.stream()
            .filter(r -> r.bucket() == BucketEngine.Bucket.LIQUID_BUFFER && "SELL".equals(r.action()))
            .findFirst().orElseThrow(() -> new AssertionError("Expected SELL recommendation for LIQUID_BUFFER"));
        assertEquals(0, new BigDecimal("100000.00").compareTo(sellArb.amount()), "Must sell 100% of arbitrage buffer (₹100,000)");
        assertEquals("MARKET_DRAWDOWN", sellArb.triggerType());

        BucketEngine.RebalanceRecommendation buyCore = recs.stream()
            .filter(r -> r.bucket() == BucketEngine.Bucket.EQUITY_CORE && "BUY".equals(r.action()))
            .findFirst().orElseThrow(() -> new AssertionError("Expected BUY recommendation for EQUITY_CORE"));
        assertEquals(0, new BigDecimal("100000.00").compareTo(buyCore.amount()), "Must buy ₹100,000 into core equity");
        assertEquals("MARKET_DRAWDOWN", buyCore.triggerType());

        // CRITICAL INVARIANT: Zero contradictory BUY LIQUID_BUFFER recommendations in the same response!
        boolean hasContradictoryBuyArb = recs.stream()
            .anyMatch(r -> (r.bucket() == BucketEngine.Bucket.LIQUID_BUFFER || r.bucket() == BucketEngine.Bucket.LIQUIDITY_ARBITRAGE) && "BUY".equals(r.action()));
        assertFalse(hasContradictoryBuyArb, "Contradiction bug resolved: BucketEngine must NEVER emit BUY LIQUID_BUFFER during market drawdown");
    }

    private static BigDecimal findTarget(List<BucketEngine.BucketTarget> list, BucketEngine.Bucket bucket) {
        return list.stream()
            .filter(t -> t.bucket() == bucket)
            .findFirst()
            .map(BucketEngine.BucketTarget::targetPct)
            .orElse(BigDecimal.ZERO);
    }
}
