package com.portfolioos.core.valuation;

import java.math.BigDecimal;

public enum LiquidationTier {
    TIER_1_LOSS_HARVEST("Capital Loss Harvesting", new BigDecimal("0.00"), 1),
    TIER_2_LTCG_EXEMPT_HEADROOM("Section 112A LTCG (Exempt Headroom)", new BigDecimal("0.00"), 2),
    TIER_3_GRANDFATHERED_DEBT_LTCG("Grandfathered Debt LTCG (Pre-Apr 2023)", new BigDecimal("0.125"), 3),
    TIER_4_TAXABLE_112A_LTCG("Section 112A LTCG (Taxable)", new BigDecimal("0.125"), 4),
    TIER_5_EQUITY_STCG("Equity STCG (Section 111A)", new BigDecimal("0.20"), 5),
    TIER_6_SPECIFIED_50AA_DEBT("Section 50AA Debt & Non-Equity STCG (Slab Rate)", new BigDecimal("0.30"), 6);

    private final String displayName;
    private final BigDecimal defaultTaxRate;
    private final int priority;

    LiquidationTier(String displayName, BigDecimal defaultTaxRate, int priority) {
        this.displayName = displayName;
        this.defaultTaxRate = defaultTaxRate;
        this.priority = priority;
    }

    public String displayName() {
        return displayName;
    }

    public BigDecimal defaultTaxRate() {
        return defaultTaxRate;
    }

    public int priority() {
        return priority;
    }
}
