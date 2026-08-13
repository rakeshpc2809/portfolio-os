package com.portfolioos.core.common;

/**
 * System-wide operational parameters (non-tax law constants).
 */
public final class PortfolioConstants {

    public static final int ACTIVE_SIP_THRESHOLD_MONTHS = 3;

    public static final double DRAWDOWN_TIER_1_PCT = 10.0;
    public static final double DRAWDOWN_TIER_2_PCT = 15.0;
    public static final double DRAWDOWN_TIER_3_PCT = 20.0;
    public static final double DRAWDOWN_TIER_HIGH_VOLATILITY_PCT = 25.0;

    public static final int REBALANCE_COOLDOWN_DAYS = 30;
    public static final int GOLD_FLOOR_IDLE_MONTHS = 6;
    public static final double GOLD_FLOOR_UNDERWEIGHT_PTS = 2.0;
    public static final int GOLD_PRICE_MA_WINDOW_DAYS = 200;
    public static final double GOLD_PRICE_EXTENSION_CEILING_PCT = 20.0;

    public static final double GOLD_BUY_MULTIPLIER_CHEAP = 1.30;
    public static final double GOLD_BUY_MULTIPLIER_EXTENDED = 0.40;
    public static final double GOLD_SELL_MULTIPLIER_CHEAP = 0.60;
    public static final double GOLD_SELL_MULTIPLIER_EXTENDED = 1.40;

    public static final double DEFAULT_CORE_DRIFT_THRESHOLD_PCT = 5.0;
    public static final double DEFAULT_GOLD_DRIFT_THRESHOLD_PCT = 12.0;

    public static double calculateDrawdownPct(java.math.BigDecimal currentVal, java.math.BigDecimal rollingHigh) {
        if (rollingHigh == null || rollingHigh.compareTo(java.math.BigDecimal.ZERO) <= 0 || currentVal == null) {
            return 0.0;
        }
        return rollingHigh.subtract(currentVal)
            .divide(rollingHigh, 4, java.math.RoundingMode.HALF_UP)
            .doubleValue() * 100.0;
    }

    public static String deriveTriggerType(double drawdownPct) {
        return drawdownPct >= DRAWDOWN_TIER_1_PCT ? "DRAWDOWN" : "SCHEDULED";
    }

    private PortfolioConstants() {}
}
