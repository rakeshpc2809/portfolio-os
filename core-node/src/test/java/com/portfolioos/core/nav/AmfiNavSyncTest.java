package com.portfolioos.core.nav;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AmfiNavSyncTest {

    @Test
    void testParseAmfiFeed_MultiColumnDirectPlanFormat() {
        AmfiNavSync sync = new AmfiNavSync();
        String feedData = """
            Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Plan;Option;Net Asset Value;Date
            119551;INF209KA12Z1;INF209KA13Z9;Aditya Birla Sun Life Banking & PSU Debt Fund;Direct Plan;IDCW-Re-investment;106.9996;20-Aug-2026
            119552;INF209K01YM2;-;Aditya Birla Sun Life Banking & PSU Debt Fund;Direct Plan;MONTHLY DCW Payout;117.3095;20-Aug-2026
            122639;INF879O01027;-;Parag Parikh Flexi Cap Fund;Direct Plan;Growth;90.7427;20-Aug-2026
            """;

        List<AmfiNavSync.NavEntry> entries = sync.parseAmfiFeed(feedData);
        assertNotNull(entries);
        assertFalse(entries.isEmpty());

        // Verify INF209KA12Z1 (Growth ISIN)
        AmfiNavSync.NavEntry growthEntry = entries.stream()
                .filter(e -> "INF209KA12Z1".equals(e.isin()))
                .findFirst()
                .orElse(null);
        assertNotNull(growthEntry);
        assertEquals(new BigDecimal("106.9996"), growthEntry.nav());

        // Verify INF209KA13Z9 (Reinvestment ISIN)
        AmfiNavSync.NavEntry reincEntry = entries.stream()
                .filter(e -> "INF209KA13Z9".equals(e.isin()))
                .findFirst()
                .orElse(null);
        assertNotNull(reincEntry);
        assertEquals(new BigDecimal("106.9996"), reincEntry.nav());

        // Verify Parag Parikh Flexi Cap
        AmfiNavSync.NavEntry ppfcEntry = entries.stream()
                .filter(e -> "INF879O01027".equals(e.isin()))
                .findFirst()
                .orElse(null);
        assertNotNull(ppfcEntry);
        assertEquals(new BigDecimal("90.7427"), ppfcEntry.nav());

        // Assert hyphen (-) is never indexed as an ISIN key
        boolean containsHyphen = entries.stream().anyMatch(e -> "-".equals(e.isin()));
        assertFalse(containsHyphen, "Hyphen '-' must never be indexed as an ISIN");
    }
}
