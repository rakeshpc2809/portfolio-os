package com.portfolioos.core.matcher;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.TaxTerm;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TaxClassifierTest {

    @Test
    void testSection50AABoundaryThresholds() {
        LocalDate apr2022Acq = LocalDate.of(2022, 1, 1); // Pre-April 2023 legacy debt fund
        LocalDate jul2024Disposal = LocalDate.of(2024, 8, 1); // Post-July 23, 2024 disposal

        // Exactly 730 days
        TaxTerm term730 = TaxClassifier.classifyTaxTerm(
            AssetCategory.DEBT_SPECIFIED_50AA,
            730L,
            "2026-27",
            false,
            apr2022Acq,
            jul2024Disposal
        );
        assertEquals(TaxTerm.LONG_TERM, term730);

        // Pre-July 23, 2024 disposal requiring > 36 calendar months
        LocalDate pre2021Acq = LocalDate.of(2021, 1, 1);
        LocalDate june2024Disposal = LocalDate.of(2024, 6, 1);
        TaxTerm term1095 = TaxClassifier.classifyTaxTerm(
            AssetCategory.DEBT_SPECIFIED_50AA,
            1247L,
            "2026-27",
            false,
            pre2021Acq,
            june2024Disposal
        );
        assertEquals(TaxTerm.LONG_TERM, term1095);
    }

    @Test
    void testEquityHoldingPeriod360vs365Days() {
        LocalDate acq = LocalDate.of(2025, 1, 1);
        LocalDate disp362 = acq.plusDays(362);
        LocalDate dispLtcg = acq.plusMonths(12).plusDays(1); // Held > 12 calendar months

        // 362 days: under 360d bug this was LONG_TERM. Under statutory rule must be SHORT_TERM.
        TaxTerm term362 = TaxClassifier.classifyTaxTerm(
            AssetCategory.EQUITY,
            362L,
            "2026-27",
            true,
            acq,
            disp362
        );
        assertEquals(TaxTerm.SHORT_TERM, term362, "362 days equity holding must be SHORT_TERM (< 12 months)");

        // Exceeds 12 calendar months: LONG_TERM
        TaxTerm termLtcg = TaxClassifier.classifyTaxTerm(
            AssetCategory.EQUITY,
            366L,
            "2026-27",
            true,
            acq,
            dispLtcg
        );
        assertEquals(TaxTerm.LONG_TERM, termLtcg, "Holding period > 12 months must be LONG_TERM");
    }

    @Test
    void testLeapYearBoundaryCalendarMonthThreshold() {
        // Leap-year boundary: buy 2023-03-01, sell 2024-02-29 is 365 days, but NOT > 12 months under Sec 2(42A)
        LocalDate acq = LocalDate.of(2023, 3, 1);
        LocalDate sellFeb29 = LocalDate.of(2024, 2, 29); // 365 days
        LocalDate sellMar02 = LocalDate.of(2024, 3, 2);  // 367 days (> 12 months)

        TaxTerm termFeb29 = TaxClassifier.classifyTaxTerm(
            AssetCategory.EQUITY, 365L, "2024-25", true, acq, sellFeb29
        );
        assertEquals(TaxTerm.SHORT_TERM, termFeb29, "Sell on 2024-02-29 (365 days in leap year) must be SHORT_TERM (< 12 full months)");

        TaxTerm termMar02 = TaxClassifier.classifyTaxTerm(
            AssetCategory.EQUITY, 367L, "2024-25", true, acq, sellMar02
        );
        assertEquals(TaxTerm.LONG_TERM, termMar02, "Sell on 2024-03-02 must be LONG_TERM (> 12 full months)");
    }

    @Test
    void testComputeEstimatedTaxDragIncludesFourPercentCess() {
        com.portfolioos.core.rules.TaxRulesConfig rules = com.portfolioos.core.rules.TaxRulesLoader.loadRules("2026-27");
        java.math.BigDecimal gain = new java.math.BigDecimal("10000.00");

        // Equity LTCG: 12.5% * 1.04 = 13.00% -> 1300.00
        java.math.BigDecimal equityLtcgDrag = TaxClassifier.computeEstimatedTaxDrag(AssetCategory.EQUITY, TaxTerm.LONG_TERM, gain, rules);
        assertEquals(new java.math.BigDecimal("1300.00"), equityLtcgDrag);

        // Equity STCG: 20% * 1.04 = 20.80% -> 2080.00
        java.math.BigDecimal equityStcgDrag = TaxClassifier.computeEstimatedTaxDrag(AssetCategory.EQUITY, TaxTerm.SHORT_TERM, gain, rules);
        assertEquals(new java.math.BigDecimal("2080.00"), equityStcgDrag);

        // SGB LTCG: secondary sale is 12.5% * 1.04 = 13.00% -> 1300.00
        java.math.BigDecimal sgbLtcgDrag = TaxClassifier.computeEstimatedTaxDrag(AssetCategory.SGB, TaxTerm.LONG_TERM, gain, rules);
        assertEquals(new java.math.BigDecimal("1300.00"), sgbLtcgDrag);
    }
}
