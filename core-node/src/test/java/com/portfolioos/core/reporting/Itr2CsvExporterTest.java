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
    void testPre2018LotWithoutFmvDataThrowsException() {
        MatchedLot lotPreNoFmv = new MatchedLot(
            "MATCH_X", "EV_DISP_X", "LOT_PRE_NO_FMV", "INF109KC13X2",
            LocalDate.of(2017, 1, 1), LocalDate.of(2026, 5, 1),
            new BigDecimal("1.0"), new BigDecimal("100.0"), new BigDecimal("200.0"),
            new BigDecimal("100.0"), 3000L, TaxTerm.LONG_TERM, AssetCategory.EQUITY
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            Itr2CsvExporter.generateSchedule112aCsv(
                List.of(lotPreNoFmv), "2026-27", Map.of("INF109KC13X2", "Fund Pre No FMV"),
                Map.of()
            );
        });

        assertTrue(ex.getMessage().contains("MISSING_FMV_DATA"),
            "Pre-2018 lot without FMV data must throw IllegalStateException with MISSING_FMV_DATA error code");
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
    void testMultiUnitPre2018GrandfatheringDimensionalIntegrity() {
        // Multi-unit lot: 100 units @ 100 cost = 10,000 costBasis. Proceeds = 20,000 (200/unit).
        // FMV = 150/unit -> Total FMV = 15,000.
        // Lower bound = min(15000, 20000) = 15,000.
        // Deemed cost = max(10000, 15000) = 15,000. Realized gain = 5,000.
        MatchedLot multiUnitPre = new MatchedLot(
            "MATCH_MULTI_PRE", "EV_DISP_1", "LOT_MULTI_PRE", "INF109KC13X2",
            LocalDate.of(2017, 1, 1), LocalDate.of(2026, 5, 1),
            new BigDecimal("100.0"), new BigDecimal("10000.0"), new BigDecimal("20000.0"),
            new BigDecimal("10000.0"), 3000L, TaxTerm.LONG_TERM, AssetCategory.EQUITY
        );

        String csv = Itr2CsvExporter.generateSchedule112aCsv(
            List.of(multiUnitPre), "2026-27", Map.of("INF109KC13X2", "Fund Multi Pre"),
            Map.of("INF109KC13X2", new BigDecimal("150.0"))
        );

        // Asserts: totalUnits=100.00, proceeds=20000.00, deemedCost=15000.00, displayFmv=150.00, gain=5000.00
        assertTrue(csv.contains("100.00,20000.00,15000.00,150.00,0.00,5000.00,\"VALIDATED_SECTION_55_2_AC\""),
            "Multi-unit pre-2018 lot must compute deemedCost using total units * unit FMV, not raw unit FMV");
    }

    @Test
    void testMixedPreAndPost2018LotsInSameIsinGroup() {
        // Lot 1: Pre-2018. 50 units @ 100 = 5,000 cost. Proceeds = 10,000 (200/unit).
        // FMV = 150/unit -> Total FMV = 7,500. Deemed cost = 7,500.
        MatchedLot lotPre = new MatchedLot(
            "MATCH_PRE", "EV_DISP_PRE", "LOT_PRE", "INF109KC13X2",
            LocalDate.of(2017, 6, 1), LocalDate.of(2026, 5, 1),
            new BigDecimal("50.0"), new BigDecimal("5000.0"), new BigDecimal("10000.0"),
            new BigDecimal("5000.0"), 3200L, TaxTerm.LONG_TERM, AssetCategory.EQUITY
        );

        // Lot 2: Post-2018. 50 units @ 120 = 6,000 cost. Proceeds = 10,000 (200/unit).
        // Not grandfathered -> Deemed cost = actual cost = 6,000.
        MatchedLot lotPost = new MatchedLot(
            "MATCH_POST", "EV_DISP_POST", "LOT_POST", "INF109KC13X2",
            LocalDate.of(2023, 1, 1), LocalDate.of(2026, 5, 1),
            new BigDecimal("50.0"), new BigDecimal("6000.0"), new BigDecimal("10000.0"),
            new BigDecimal("4000.0"), 1200L, TaxTerm.LONG_TERM, AssetCategory.EQUITY
        );

        String csv = Itr2CsvExporter.generateSchedule112aCsv(
            List.of(lotPre, lotPost), "2026-27", Map.of("INF109KC13X2", "Fund Mixed"),
            Map.of("INF109KC13X2", new BigDecimal("150.0"))
        );

        // Aggregate asserts:
        // totalUnits = 100.00
        // proceeds = 20000.00
        // deemedCost = 7500.00 (pre) + 6000.00 (post) = 13500.00
        // gain = 20000.00 - 13500.00 = 6500.00
        // status = MIXED_PRE_AND_POST_2018
        assertTrue(csv.contains("100.00,20000.00,13500.00,150.00,0.00,6500.00,\"MIXED_PRE_AND_POST_2018\""),
            "Mixed pre- and post-2018 lots must compute deemed cost lot-by-lot without cross-contamination");
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
