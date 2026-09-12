package com.portfolioos.core.rules;

import java.math.BigDecimal;

public enum MacroRegime {
    EXPANSION_RISK_OFF(
        "Expansion / Risk-Off (Elevated Equity Valuation & Steep Yield Curve)",
        new BigDecimal("15.00"),
        new BigDecimal("5.00"),
        24
    ),
    CORRIDOR_NEUTRAL(
        "Corridor / Neutral (Fair Value Equity & Normal Yield Curve)",
        new BigDecimal("10.00"),
        BigDecimal.ZERO,
        12
    ),
    ACCUMULATION_RISK_ON(
        "Accumulation / Risk-On (Depressed Equity Valuation / Late-Cycle Inversion)",
        new BigDecimal("5.00"),
        new BigDecimal("-5.00"),
        6
    );

    private final String displayName;
    private final BigDecimal targetLiquidBufferPct;
    private final BigDecimal bufferDeltaPct;
    private final int recommendedRunwayMonths;

    MacroRegime(
        String displayName,
        BigDecimal targetLiquidBufferPct,
        BigDecimal bufferDeltaPct,
        int recommendedRunwayMonths
    ) {
        this.displayName = displayName;
        this.targetLiquidBufferPct = targetLiquidBufferPct;
        this.bufferDeltaPct = bufferDeltaPct;
        this.recommendedRunwayMonths = recommendedRunwayMonths;
    }

    public String displayName() {
        return displayName;
    }

    public BigDecimal targetLiquidBufferPct() {
        return targetLiquidBufferPct;
    }

    public BigDecimal bufferDeltaPct() {
        return bufferDeltaPct;
    }

    public int recommendedRunwayMonths() {
        return recommendedRunwayMonths;
    }
}
