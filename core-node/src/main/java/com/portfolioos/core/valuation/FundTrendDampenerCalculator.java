package com.portfolioos.core.valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class FundTrendDampenerCalculator {

    public record DampenerMultipliers(double buyMultiplier, double sellMultiplier) {}

    /**
     * Calculates dynamic per-fund trend dampener multipliers based on percentage drift.
     * @param driftPct positive if overweight (excess), negative if underweight (deficit)
     */
    public static DampenerMultipliers calculateFundMultipliers(double driftPct) {
        double buyMult;
        double sellMult;

        if (driftPct >= 0.0) {
            // Overweight fund (Sell side)
            // Small excess (0-10%): gentle 0.40x trim
            // Moderate excess (10-30%): meaningful 0.60x trim
            // Large excess (>30%): disciplined 0.75x trim
            if (driftPct <= 10.0) {
                sellMult = 0.40 + (driftPct / 10.0) * (0.60 - 0.40);
            } else if (driftPct <= 30.0) {
                sellMult = 0.60 + ((driftPct - 10.0) / 20.0) * (0.75 - 0.60);
            } else {
                sellMult = 0.75;
            }
            buyMult = 0.0;
        } else {
            // Underweight fund (Buy side)
            // Minor deficit (0 to -10%): 0.50x allocation
            // Moderate deficit (-10% to -30%): 0.80x allocation
            // Deep deficit (<-30%): 1.00x full allocation
            double deficit = Math.abs(driftPct);
            if (deficit <= 10.0) {
                buyMult = 0.50 + (deficit / 10.0) * (0.80 - 0.50);
            } else if (deficit <= 30.0) {
                buyMult = 0.80 + ((deficit - 10.0) / 20.0) * (1.00 - 0.80);
            } else {
                buyMult = 1.00;
            }
            sellMult = 0.0;
        }

        buyMult = Math.round(buyMult * 10000.0) / 10000.0;
        sellMult = Math.round(sellMult * 10000.0) / 10000.0;

        return new DampenerMultipliers(buyMult, sellMult);
    }

    /**
     * Sizes the per-bucket dampened excess trim amount.
     */
    public static BigDecimal calculateDampenedTrim(BigDecimal excessVal, double targetVal) {
        if (excessVal == null || excessVal.compareTo(BigDecimal.ZERO) <= 0 || targetVal <= 0.0) {
            return BigDecimal.ZERO;
        }
        double driftPct = (excessVal.doubleValue() / targetVal) * 100.0;
        DampenerMultipliers mults = calculateFundMultipliers(driftPct);
        return excessVal.multiply(BigDecimal.valueOf(mults.sellMultiplier()))
            .setScale(2, RoundingMode.HALF_UP);
    }
}
