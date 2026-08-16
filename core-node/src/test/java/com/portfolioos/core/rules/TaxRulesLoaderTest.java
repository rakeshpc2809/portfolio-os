package com.portfolioos.core.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TaxRulesLoaderTest {

    @Test
    @DisplayName("TaxRulesLoader: Load rules for FY 2026-27 from YAML")
    void testLoadRulesFY2627() {
        TaxRulesConfig config = TaxRulesLoader.loadRules("2026-27");
        assertNotNull(config, "TaxRulesConfig for FY 2026-27 must not be null");
        assertEquals("2026-27", config.fiscalYear());
        assertEquals(0, new BigDecimal("125000").compareTo(config.equityExemptionLimit()));
        assertEquals(0, new BigDecimal("0.125").compareTo(config.equityLtcgRate()));
        assertEquals(0, new BigDecimal("0.20").compareTo(config.equityStcgRate()));
    }

    @Test
    @DisplayName("TaxRulesLoader: Load rules for FY 2025-26 from YAML")
    void testLoadRulesFY2526() {
        TaxRulesConfig config = TaxRulesLoader.loadRules("2025-26");
        assertNotNull(config, "TaxRulesConfig for FY 2025-26 must not be null");
        assertEquals("2025-26", config.fiscalYear());
        assertEquals(new BigDecimal("125000"), config.equityExemptionLimit());
    }
}
