package com.portfolioos.core.rules;

import com.portfolioos.core.valuation.BucketEngine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class MacroRegimeRouter {

    public record RegimeEvaluationResult(
        MacroRegime regime,
        double niftyPe,
        double gsec10yYieldPct,
        double repoRatePct,
        double yieldCurveSlopePct,
        double beerSpreadPct,
        BigDecimal liquidBufferTargetPct,
        int recommendedRunwayMonths,
        String rationale,
        List<BucketEngine.BucketTarget> adjustedBucketTargets
    ) {}

    public static MacroRegime classifyRegime(MarketIndicatorsReader.MarketIndicators indicators) {
        if (indicators == null) {
            return MacroRegime.CORRIDOR_NEUTRAL;
        }

        double pe = indicators.nifty50Pe();
        double slope = indicators.yieldCurveSlopePct();

        // 1. Cheap Equities (PE < 18.0): Undervalued equity opportunity cost unconditionally dominates
        if (pe < 18.0) {
            return MacroRegime.ACCUMULATION_RISK_ON;
        }

        // 2. Expensive Equities (PE > 22.0): Elevated valuation with rate sensitivity
        if (pe > 22.0) {
            if (slope < 0.25) {
                // Inverted or flat yield curve cancels overvaluation signal -> settle at neutral
                return MacroRegime.CORRIDOR_NEUTRAL;
            }
            return MacroRegime.EXPANSION_RISK_OFF;
        }

        // 3. Fair Value Corridor (18.0 <= PE <= 22.0)
        if (slope < 0.25) {
            // Late-cycle inversion / flat curve nudges toward accumulation
            return MacroRegime.ACCUMULATION_RISK_ON;
        }

        return MacroRegime.CORRIDOR_NEUTRAL;
    }

    public static List<BucketEngine.BucketTarget> adjustBucketTargets(
        List<BucketEngine.BucketTarget> baseTargets,
        MacroRegime regime
    ) {
        if (baseTargets == null || baseTargets.isEmpty()) {
            return List.of();
        }

        if (regime == null || regime == MacroRegime.CORRIDOR_NEUTRAL) {
            return new ArrayList<>(baseTargets);
        }

        BigDecimal baseCore = BigDecimal.ZERO;
        BigDecimal baseSat = BigDecimal.ZERO;
        BigDecimal baseGold = BigDecimal.ZERO;
        BigDecimal baseBuffer = BigDecimal.ZERO;

        BigDecimal coreBand = new BigDecimal("5.00");
        BigDecimal satBand = new BigDecimal("5.00");
        BigDecimal goldBand = new BigDecimal("5.00");
        BigDecimal bufferBand = new BigDecimal("5.00");

        for (BucketEngine.BucketTarget bt : baseTargets) {
            if (bt.bucket() == BucketEngine.Bucket.EQUITY_CORE) {
                baseCore = bt.targetPct();
                coreBand = bt.bandPct();
            } else if (bt.bucket() == BucketEngine.Bucket.EQUITY_SATELLITE) {
                baseSat = bt.targetPct();
                satBand = bt.bandPct();
            } else if (bt.bucket() == BucketEngine.Bucket.GOLD_SILVER) {
                baseGold = bt.targetPct();
                goldBand = bt.bandPct();
            } else if (bt.bucket() == BucketEngine.Bucket.LIQUID_BUFFER) {
                baseBuffer = bt.targetPct();
                bufferBand = bt.bandPct();
            }
        }

        BigDecimal bufferDelta = regime.bufferDeltaPct();
        // Core absorbs 5/8 (0.625) of the buffer delta; round core directly to preserve algebraic half-up rounding (50 - 3.125 = 46.875 -> 46.88)
        BigDecimal adjustedCore = baseCore.subtract(bufferDelta.multiply(new BigDecimal("0.625"))).setScale(2, RoundingMode.HALF_UP);
        BigDecimal adjustedBuffer = baseBuffer.add(bufferDelta).setScale(2, RoundingMode.HALF_UP);
        BigDecimal adjustedGold = baseGold.setScale(2, RoundingMode.HALF_UP);

        // Satellite is the algebraic residual plug, ensuring strict 100.00% sum with zero rounding drift
        BigDecimal adjustedSat = new BigDecimal("100.00")
            .subtract(adjustedBuffer)
            .subtract(adjustedGold)
            .subtract(adjustedCore)
            .setScale(2, RoundingMode.HALF_UP);

        List<BucketEngine.BucketTarget> adjustedList = new ArrayList<>();
        for (BucketEngine.BucketTarget bt : baseTargets) {
            if (bt.bucket() == BucketEngine.Bucket.EQUITY_CORE) {
                adjustedList.add(new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_CORE, adjustedCore, coreBand));
            } else if (bt.bucket() == BucketEngine.Bucket.EQUITY_SATELLITE) {
                adjustedList.add(new BucketEngine.BucketTarget(BucketEngine.Bucket.EQUITY_SATELLITE, adjustedSat, satBand));
            } else if (bt.bucket() == BucketEngine.Bucket.GOLD_SILVER) {
                adjustedList.add(new BucketEngine.BucketTarget(BucketEngine.Bucket.GOLD_SILVER, adjustedGold, goldBand));
            } else if (bt.bucket() == BucketEngine.Bucket.LIQUID_BUFFER) {
                adjustedList.add(new BucketEngine.BucketTarget(BucketEngine.Bucket.LIQUID_BUFFER, adjustedBuffer, bufferBand));
            } else {
                adjustedList.add(bt);
            }
        }

        return adjustedList;
    }

    public static RegimeEvaluationResult evaluate(
        MarketIndicatorsReader.MarketIndicators indicators,
        List<BucketEngine.BucketTarget> baseTargets
    ) {
        MacroRegime regime = classifyRegime(indicators);
        double pe = indicators != null ? indicators.nifty50Pe() : 22.40;
        double gsec = indicators != null ? indicators.gsec10yYieldPct() : 7.10;
        double repo = indicators != null ? indicators.repoRatePct() : 5.25;
        double slope = indicators != null ? indicators.yieldCurveSlopePct() : (gsec - repo);
        String asOfDateStr = indicators != null && indicators.asOfDate() != null ? indicators.asOfDate().toString() : null;
        double beerSpread = com.portfolioos.core.valuation.BeerSpreadCalculator.evaluateBeerSpread(gsec, pe, asOfDateStr).beerSpreadPct();

        List<BucketEngine.BucketTarget> adjustedTargets = adjustBucketTargets(baseTargets, regime);

        String rationale = String.format(
            "Macro Regime: %s. Nifty 50 P/E of %.2f, 10Y G-Sec yield of %.2f%%, Repo rate of %.2f%% (Yield curve slope: +%.2f%% / %d bps). " +
            "Recommended liquid buffer target is %s%% (%d months expense runway).",
            regime.displayName(),
            pe,
            gsec,
            repo,
            slope,
            (int) Math.round(slope * 100),
            regime.targetLiquidBufferPct().toPlainString(),
            regime.recommendedRunwayMonths()
        );

        return new RegimeEvaluationResult(
            regime,
            pe,
            gsec,
            repo,
            slope,
            beerSpread,
            regime.targetLiquidBufferPct(),
            regime.recommendedRunwayMonths(),
            rationale,
            adjustedTargets
        );
    }
}
