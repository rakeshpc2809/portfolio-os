package com.portfolioos.core.valuation;

import com.portfolioos.core.common.PortfolioConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class GoldDampenerCalculator {

    public record DampenerMultipliers(double buyMultiplier, double sellMultiplier) {}

    public static DampenerMultipliers calculateMultipliers(double devPct) {
        double buyMult;
        double sellMult;

        if (devPct <= 0.0) {
            buyMult = PortfolioConstants.GOLD_BUY_MULTIPLIER_CHEAP; // 1.30
            sellMult = PortfolioConstants.GOLD_SELL_MULTIPLIER_CHEAP; // 0.60
        } else if (devPct >= PortfolioConstants.GOLD_PRICE_EXTENSION_CEILING_PCT) { // 20.0%
            buyMult = PortfolioConstants.GOLD_BUY_MULTIPLIER_EXTENDED; // 0.40
            sellMult = PortfolioConstants.GOLD_SELL_MULTIPLIER_EXTENDED; // 1.40
        } else {
            double fraction = devPct / PortfolioConstants.GOLD_PRICE_EXTENSION_CEILING_PCT;
            buyMult = PortfolioConstants.GOLD_BUY_MULTIPLIER_CHEAP - fraction * (PortfolioConstants.GOLD_BUY_MULTIPLIER_CHEAP - PortfolioConstants.GOLD_BUY_MULTIPLIER_EXTENDED);
            sellMult = PortfolioConstants.GOLD_SELL_MULTIPLIER_CHEAP + fraction * (PortfolioConstants.GOLD_SELL_MULTIPLIER_EXTENDED - PortfolioConstants.GOLD_SELL_MULTIPLIER_CHEAP);
        }

        // Round to 4 decimal places for precision
        buyMult = Math.round(buyMult * 10000.0) / 10000.0;
        sellMult = Math.round(sellMult * 10000.0) / 10000.0;

        return new DampenerMultipliers(buyMult, sellMult);
    }

    public static BigDecimal calculateSizedAllocation(
        double targetWeightPct,
        double currentWeightPct,
        double currentPrice,
        double trailingMa,
        BigDecimal totalPortfolioValue,
        boolean isFloorBackstop
    ) {
        if (totalPortfolioValue == null || totalPortfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        double gapWeightPct = targetWeightPct - currentWeightPct;

        if (isFloorBackstop) {
            // Floor backstop overrides buy multiplier to 1.0x and sizes to close 50% of the gap
            if (gapWeightPct <= 0) return BigDecimal.ZERO;
            double basePct = gapWeightPct / 2.0;
            return totalPortfolioValue.multiply(BigDecimal.valueOf(basePct / 100.0))
                .setScale(2, RoundingMode.HALF_UP);
        }

        if (trailingMa <= 0.0 || currentPrice <= 0.0) {
            // When moving average data is missing/unwired, default to neutral 1.0x multipliers (disarm safe)
            return gapWeightPct > 0 
                ? totalPortfolioValue.multiply(BigDecimal.valueOf(gapWeightPct / 100.0)).setScale(2, RoundingMode.HALF_UP)
                : totalPortfolioValue.multiply(BigDecimal.valueOf(Math.abs(gapWeightPct) / 100.0)).setScale(2, RoundingMode.HALF_UP);
        }

        double devPct = ((currentPrice - trailingMa) / trailingMa) * 100.0;
        DampenerMultipliers mults = calculateMultipliers(devPct);

        if (gapWeightPct > 0) {
            // Underweight -> Buy side
            BigDecimal baseAmount = totalPortfolioValue.multiply(BigDecimal.valueOf(gapWeightPct / 100.0));
            return baseAmount.multiply(BigDecimal.valueOf(mults.buyMultiplier()))
                .setScale(2, RoundingMode.HALF_UP);
        } else if (gapWeightPct < 0) {
            // Overweight -> Sell side
            BigDecimal baseAmount = totalPortfolioValue.multiply(BigDecimal.valueOf(Math.abs(gapWeightPct) / 100.0));
            return baseAmount.multiply(BigDecimal.valueOf(mults.sellMultiplier()))
                .setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }
}
