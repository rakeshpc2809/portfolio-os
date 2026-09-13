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

        // Exactly 1095 days (Pre-July 23, 2024 disposal)
        LocalDate june2024Disposal = LocalDate.of(2024, 6, 1);
        TaxTerm term1095 = TaxClassifier.classifyTaxTerm(
            AssetCategory.DEBT_SPECIFIED_50AA,
            1095L,
            "2026-27",
            false,
            apr2022Acq,
            june2024Disposal
        );
        assertEquals(TaxTerm.LONG_TERM, term1095);
    }

    @Test
    void testEquityHoldingPeriod360vs365Days() {
        LocalDate acq = LocalDate.of(2025, 1, 1);
        LocalDate disp362 = acq.plusDays(362);
        LocalDate disp365 = acq.plusDays(365);

        // 362 days: under 360d bug this was LONG_TERM. Under statutory rule must be SHORT_TERM.
        TaxTerm term362 = TaxClassifier.classifyTaxTerm(
            AssetCategory.EQUITY,
            362L,
            "2026-27",
            true,
            acq,
            disp362
        );
        assertEquals(TaxTerm.SHORT_TERM, term362, "362 days equity holding must be SHORT_TERM (< 365 days)");

        // Exactly 365 days: LONG_TERM
        TaxTerm term365 = TaxClassifier.classifyTaxTerm(
            AssetCategory.EQUITY,
            365L,
            "2026-27",
            true,
            acq,
            disp365
        );
        assertEquals(TaxTerm.LONG_TERM, term365, "365 days equity holding must be LONG_TERM");
    }
}
