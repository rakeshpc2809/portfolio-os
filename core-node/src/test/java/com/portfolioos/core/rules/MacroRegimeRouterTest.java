package com.portfolioos.core.rules;

import com.portfolioos.core.valuation.BucketEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MacroRegimeRouterTest {

    private List<BucketEngine.BucketTarget> createBaseTargets() {
        return List.of(
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, new BigDecimal("50.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, new BigDecimal("30.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, new BigDecimal("10.00"), new BigDecimal("5.00")),
            new BucketEngine.BucketTarget(BucketEngine.Bucket.LIQUID_BUFFER, new BigDecimal("10.00"), new BigDecimal("5.00"))
        );
    }

    private MarketIndicatorsReader.MarketIndicators createIndicators(double pe, double gsecYield, double repoRate) {
        return new MarketIndicatorsReader.MarketIndicators(
            LocalDate.now(),
            gsecYield,
            repoRate,
            pe,
            false,
            "TEST",
            "Test indicators"
        );
    }

    @Test
    @DisplayName("3x3 Matrix: Expensive Equities (PE > 22.0) with Rate Sensitivity")
    void testExpensiveEquitiesMatrix() {
        // Flat/Inverted Slope (< 0.25%): Conflicting signals cancel -> Neutral
        MarketIndicatorsReader.MarketIndicators flat = createIndicators(24.0, 5.35, 5.25); // slope = 0.10%
        assertEquals(MacroRegime.CORRIDOR_NEUTRAL, MacroRegimeRouter.classifyRegime(flat));

        // Normal Slope (0.25% <= Slope < 1.00%): Expansion Risk-Off
        MarketIndicatorsReader.MarketIndicators normal = createIndicators(24.0, 5.85, 5.25); // slope = 0.60%
        assertEquals(MacroRegime.EXPANSION_RISK_OFF, MacroRegimeRouter.classifyRegime(normal));

        // Steep Slope (Slope >= 1.00%): Expansion Risk-Off
        MarketIndicatorsReader.MarketIndicators steep = createIndicators(24.0, 7.10, 5.25); // slope = 1.85%
        assertEquals(MacroRegime.EXPANSION_RISK_OFF, MacroRegimeRouter.classifyRegime(steep));
    }

    @Test
    @DisplayName("3x3 Matrix: Fair Value Corridor (18.0 <= PE <= 22.0)")
    void testFairValueCorridorMatrix() {
        // Flat/Inverted Slope (< 0.25%): Late-cycle inversion nudges to Accumulation
        MarketIndicatorsReader.MarketIndicators flat = createIndicators(20.0, 5.35, 5.25); // slope = 0.10%
        assertEquals(MacroRegime.ACCUMULATION_RISK_ON, MacroRegimeRouter.classifyRegime(flat));

        // Normal Slope (0.25% <= Slope < 1.00%): Corridor Neutral
        MarketIndicatorsReader.MarketIndicators normal = createIndicators(20.0, 5.85, 5.25); // slope = 0.60%
        assertEquals(MacroRegime.CORRIDOR_NEUTRAL, MacroRegimeRouter.classifyRegime(normal));

        // Steep Slope (Slope >= 1.00%): Corridor Neutral
        MarketIndicatorsReader.MarketIndicators steep = createIndicators(20.0, 7.10, 5.25); // slope = 1.85%
        assertEquals(MacroRegime.CORRIDOR_NEUTRAL, MacroRegimeRouter.classifyRegime(steep));
    }

    @Test
    @DisplayName("3x3 Matrix: Cheap Equities (PE < 18.0) Undervalued Opportunity Cost Dominates")
    void testCheapEquitiesMatrix() {
        // Flat/Inverted: Accumulation Risk-On
        MarketIndicatorsReader.MarketIndicators flat = createIndicators(16.5, 5.35, 5.25); // slope = 0.10%
        assertEquals(MacroRegime.ACCUMULATION_RISK_ON, MacroRegimeRouter.classifyRegime(flat));

        // Normal: Accumulation Risk-On
        MarketIndicatorsReader.MarketIndicators normal = createIndicators(16.5, 5.85, 5.25); // slope = 0.60%
        assertEquals(MacroRegime.ACCUMULATION_RISK_ON, MacroRegimeRouter.classifyRegime(normal));

        // Steep: Accumulation Risk-On
        MarketIndicatorsReader.MarketIndicators steep = createIndicators(16.5, 7.10, 5.25); // slope = 1.85%
        assertEquals(MacroRegime.ACCUMULATION_RISK_ON, MacroRegimeRouter.classifyRegime(steep));
    }

    @Test
    @DisplayName("Safety Fallback: Null indicators default to CORRIDOR_NEUTRAL")
    void testNullIndicatorsDefault() {
        assertEquals(MacroRegime.CORRIDOR_NEUTRAL, MacroRegimeRouter.classifyRegime(null));

        List<BucketEngine.BucketTarget> baseTargets = createBaseTargets();
        List<BucketEngine.BucketTarget> adjusted = MacroRegimeRouter.adjustBucketTargets(baseTargets, null);
        assertEquals(baseTargets.size(), adjusted.size());
        for (int i = 0; i < baseTargets.size(); i++) {
            assertEquals(baseTargets.get(i).targetPct(), adjusted.get(i).targetPct());
        }
    }

    @Test
    @DisplayName("Exact Residual Weight Conservation: Sum equals 100.00% with zero drift across all regimes")
    void testExactResidualWeightConservation() {
        List<BucketEngine.BucketTarget> baseTargets = createBaseTargets();

        // 1. EXPANSION_RISK_OFF: Buffer = 15.00%, Gold = 10.00%, Core = 46.88%, Satellite = 28.12%
        List<BucketEngine.BucketTarget> expTargets = MacroRegimeRouter.adjustBucketTargets(baseTargets, MacroRegime.EXPANSION_RISK_OFF);
        BigDecimal sumExp = BigDecimal.ZERO;
        for (BucketEngine.BucketTarget bt : expTargets) {
            sumExp = sumExp.add(bt.targetPct());
            if (bt.bucket() == BucketEngine.Bucket.LIQUID_BUFFER) {
                assertEquals(new BigDecimal("15.00"), bt.targetPct());
            } else if (bt.bucket() == BucketEngine.Bucket.GOLD_SILVER) {
                assertEquals(new BigDecimal("10.00"), bt.targetPct());
            } else if (bt.bucket() == BucketEngine.Bucket.EQUITY_CORE) {
                assertEquals(new BigDecimal("46.88"), bt.targetPct());
            } else if (bt.bucket() == BucketEngine.Bucket.EQUITY_SATELLITE) {
                assertEquals(new BigDecimal("28.12"), bt.targetPct());
            }
        }
        assertEquals(new BigDecimal("100.00"), sumExp);

        // 2. CORRIDOR_NEUTRAL: Buffer = 10.00%, Gold = 10.00%, Core = 50.00%, Satellite = 30.00%
        List<BucketEngine.BucketTarget> neuTargets = MacroRegimeRouter.adjustBucketTargets(baseTargets, MacroRegime.CORRIDOR_NEUTRAL);
        BigDecimal sumNeu = BigDecimal.ZERO;
        for (BucketEngine.BucketTarget bt : neuTargets) {
            sumNeu = sumNeu.add(bt.targetPct());
            if (bt.bucket() == BucketEngine.Bucket.LIQUID_BUFFER) {
                assertEquals(new BigDecimal("10.00"), bt.targetPct());
            } else if (bt.bucket() == BucketEngine.Bucket.GOLD_SILVER) {
                assertEquals(new BigDecimal("10.00"), bt.targetPct());
            } else if (bt.bucket() == BucketEngine.Bucket.EQUITY_CORE) {
                assertEquals(new BigDecimal("50.00"), bt.targetPct());
            } else if (bt.bucket() == BucketEngine.Bucket.EQUITY_SATELLITE) {
                assertEquals(new BigDecimal("30.00"), bt.targetPct());
            }
        }
        assertEquals(new BigDecimal("100.00"), sumNeu);

        // 3. ACCUMULATION_RISK_ON: Buffer = 5.00%, Gold = 10.00%, Core = 53.13%, Satellite = 31.87%
        List<BucketEngine.BucketTarget> accTargets = MacroRegimeRouter.adjustBucketTargets(baseTargets, MacroRegime.ACCUMULATION_RISK_ON);
        BigDecimal sumAcc = BigDecimal.ZERO;
        for (BucketEngine.BucketTarget bt : accTargets) {
            sumAcc = sumAcc.add(bt.targetPct());
            if (bt.bucket() == BucketEngine.Bucket.LIQUID_BUFFER) {
                assertEquals(new BigDecimal("5.00"), bt.targetPct());
            } else if (bt.bucket() == BucketEngine.Bucket.GOLD_SILVER) {
                assertEquals(new BigDecimal("10.00"), bt.targetPct());
            } else if (bt.bucket() == BucketEngine.Bucket.EQUITY_CORE) {
                assertEquals(new BigDecimal("53.13"), bt.targetPct());
            } else if (bt.bucket() == BucketEngine.Bucket.EQUITY_SATELLITE) {
                assertEquals(new BigDecimal("31.87"), bt.targetPct());
            }
        }
        assertEquals(new BigDecimal("100.00"), sumAcc);
    }

    @Test
    @DisplayName("Evaluate Method: Correct telemetry, rationale, and cash runway calculation")
    void testEvaluateMethod() {
        MarketIndicatorsReader.MarketIndicators indicators = createIndicators(22.40, 7.10, 5.25);
        List<BucketEngine.BucketTarget> baseTargets = createBaseTargets();

        MacroRegimeRouter.RegimeEvaluationResult result = MacroRegimeRouter.evaluate(indicators, baseTargets);

        assertNotNull(result);
        assertEquals(MacroRegime.EXPANSION_RISK_OFF, result.regime());
        assertEquals(22.40, result.niftyPe(), 0.001);
        assertEquals(7.10, result.gsec10yYieldPct(), 0.001);
        assertEquals(5.25, result.repoRatePct(), 0.001);
        assertEquals(1.85, result.yieldCurveSlopePct(), 0.001);
        assertEquals(new BigDecimal("15.00"), result.liquidBufferTargetPct());
        assertEquals(24, result.recommendedRunwayMonths());
        assertTrue(result.rationale().contains("Expansion / Risk-Off"));
        assertTrue(result.rationale().contains("24 months expense runway"));
    }
}
