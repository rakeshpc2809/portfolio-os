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
}
