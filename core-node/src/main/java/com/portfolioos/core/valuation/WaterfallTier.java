package com.portfolioos.core.valuation;

public enum WaterfallTier {
    LEGACY_FUND("Legacy Holdings"),
    LOSS_HARVEST("Capital Loss Harvesting"),
    LTCG_WITHIN_EXEMPTION("Section 112A LTCG (Exempt Headroom)"),
    LTCG_BEYOND_EXEMPTION("Section 112A LTCG (Taxable)"),
    STCG_URGENT_ONLY("Urgent STCG (Higher Tax Drag)");

    private final String displayName;

    WaterfallTier(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
