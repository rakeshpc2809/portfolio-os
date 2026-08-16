package com.portfolioos.core.reporting;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Itr2CsvExporterTest {

    @Test
    void testPre2018GrandfatheringDeemedCostWithFmv() {
        // MatchedLot signature:
        // (matchId, disposalEventId, lotId, assetId, acquisitionDate, disposalDate, unitsMatched, costBasis, saleProceeds, realizedGain, holdingPeriodDays, taxTerm, assetCategory)

        // Branch A: FMV (150) > Proceeds (120) > Cost (100) -> Deemed Cost = max(100, min(150, 120)) = 120 (gain = 0)
        MatchedLot lotA = new MatchedLot(
            "MATCH_A", "EV_DISP_A", "LOT_A", "INF109KC13X2",
            LocalDate.of(2017, 1, 1), LocalDate.of(2026, 5, 1),
            new BigDecimal("1.0"), new BigDecimal("100.0"), new BigDecimal("120.0"),
            new BigDecimal("20.0"), 3000L, TaxTerm.LONG_TERM, AssetCategory.EQUITY
        );

        String csvA = Itr2CsvExporter.generateSchedule112aCsv(
            List.of(lotA), "2026-27", Map.of("INF109KC13X2", "Fund A"),
            Map.of("INF109KC13X2", new BigDecimal("150.0"))
        );
        assertTrue(csvA.contains("120.00,150.00,0.00,0.00,\"VALIDATED_SECTION_55_2_AC\""));

        // Branch B: Proceeds (200) > FMV (150) > Cost (100) -> Deemed Cost = max(100, min(150, 200)) = 150 (gain = 50)
        MatchedLot lotB = new MatchedLot(
            "MATCH_B", "EV_DISP_B", "LOT_B", "INF109KC13X2",
            LocalDate.of(2017, 1, 1), LocalDate.of(2026, 5, 1),
            new BigDecimal("1.0"), new BigDecimal("100.0"), new BigDecimal("200.0"),
            new BigDecimal("100.0"), 3000L, TaxTerm.LONG_TERM, AssetCategory.EQUITY
        );

        String csvB = Itr2CsvExporter.generateSchedule112aCsv(
            List.of(lotB), "2026-27", Map.of("INF109KC13X2", "Fund B"),
            Map.of("INF109KC13X2", new BigDecimal("150.0"))
        );
        assertTrue(csvB.contains("150.00,150.00,0.00,50.00,\"VALIDATED_SECTION_55_2_AC\""));

        // Branch C: Proceeds (200) > Cost (100) > FMV (80) -> Deemed Cost = max(100, min(80, 200)) = 100 (gain = 100)
        MatchedLot lotC = new MatchedLot(
            "MATCH_C", "EV_DISP_C", "LOT_C", "INF109KC13X2",
            LocalDate.of(2017, 1, 1), LocalDate.of(2026, 5, 1),
            new BigDecimal("1.0"), new BigDecimal("100.0"), new BigDecimal("200.0"),
            new BigDecimal("100.0"), 3000L, TaxTerm.LONG_TERM, AssetCategory.EQUITY
        );

        String csvC = Itr2CsvExporter.generateSchedule112aCsv(
            List.of(lotC), "2026-27", Map.of("INF109KC13X2", "Fund C"),
            Map.of("INF109KC13X2", new BigDecimal("80.0"))
        );
        assertTrue(csvC.contains("100.00,80.00,0.00,100.00,\"VALIDATED_SECTION_55_2_AC\""));
    }

    @Test
    void testPre2018LotWithoutFmvDataFlagsUnavailable() {
        MatchedLot lotPreNoFmv = new MatchedLot(
            "MATCH_X", "EV_DISP_X", "LOT_PRE_NO_FMV", "INF109KC13X2",
            LocalDate.of(2017, 1, 1), LocalDate.of(2026, 5, 1),
            new BigDecimal("1.0"), new BigDecimal("100.0"), new BigDecimal("200.0"),
            new BigDecimal("100.0"), 3000L, TaxTerm.LONG_TERM, AssetCategory.EQUITY
        );

        String csv = Itr2CsvExporter.generateSchedule112aCsv(
            List.of(lotPreNoFmv), "2026-27", Map.of("INF109KC13X2", "Fund Pre No FMV"),
            Map.of()
        );

        assertTrue(csv.contains("FMV_UNAVAILABLE_REVIEW_REQUIRED"),
            "Pre-2018 lot without FMV data must explicitly flag FMV_UNAVAILABLE_REVIEW_REQUIRED");
    }

    @Test
    void testPost2018LotSkipsGrandfathering() {
        MatchedLot lotPost = new MatchedLot(
            "MATCH_POST", "EV_DISP_POST", "LOT_POST", "INF109KC13X2",
            LocalDate.of(2024, 1, 1), LocalDate.of(2026, 5, 1),
            new BigDecimal("1.0"), new BigDecimal("100.0"), new BigDecimal("200.0"),
            new BigDecimal("100.0"), 500L, TaxTerm.LONG_TERM, AssetCategory.EQUITY
        );

        String csv = Itr2CsvExporter.generateSchedule112aCsv(
            List.of(lotPost), "2026-27", Map.of("INF109KC13X2", "Fund Post"),
            Map.of("INF109KC13X2", new BigDecimal("150.0"))
        );

        assertTrue(csv.contains("100.00,0.00,0.00,100.00,\"POST_2018_ACQUISITION\""),
            "Post-2018 lot must skip grandfathering and set deemedCost = actualCost");
    }

    @Test
    void testRegressionNoEmptyMapDefaultInSchedule112a() throws Exception {
        java.io.File exporterFile = new java.io.File("src/main/java/com/portfolioos/core/reporting/Itr2CsvExporter.java");
        assertTrue(exporterFile.exists());
        String content = java.nio.file.Files.readString(exporterFile.toPath());

        assertFalse(content.contains("fmv2018Map.getOrDefault(isin, actualCost)"),
            "Must not silently default fmv2018Map missing entries to actualCost");
    }
}
