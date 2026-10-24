package com.portfolioos.core.reporting;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaxReportAndItr2ExporterTest {

    @Test
    @DisplayName("P4.3 Test 1: Non-Equity LTCG (Gold/Silver) is excluded from Schedule 112A CSV but included in TaxReport totalRealizedLtcg with 0.00 exemption used")
    void testNonEquityLtcgExclusion() {
        // Gold/Silver asset with Long-Term Capital Gain
        MatchedLot goldLot = new MatchedLot(
            "MATCH_GOLD_1",
            "EV_DISP_GOLD_1",
            "LOT_GOLD_1",
            "INF247L01BM8", // Motilal Gold & Silver
            LocalDate.of(2023, 1, 10),
            LocalDate.of(2026, 6, 15),
            new BigDecimal("50.000"),
            new BigDecimal("50000.00"), // Cost basis
            new BigDecimal("75000.00"), // Sale proceeds
            new BigDecimal("25000.00"), // Realized gain
            1250L,
            TaxTerm.LONG_TERM,
            AssetCategory.GOLD_SILVER
        );

        String fy = "2026-27";
        Map<String, String> assetNames = Map.of("INF247L01BM8", "Motilal Oswal Gold and Silver Passive FoF");
        Map<String, BigDecimal> fmvMap = Map.of();

        // 1. Assert Schedule_112A.csv has 0 data rows (header only)
        String schedule112aCsv = Itr2CsvExporter.generateSchedule112aCsv(List.of(goldLot), fy, assetNames, fmvMap);
        assertNotNull(schedule112aCsv);
        String[] lines = schedule112aCsv.trim().split("\n");
        assertEquals(1, lines.length, "Schedule 112A CSV must contain exactly 1 header line and 0 data rows for non-equity assets");
        assertFalse(schedule112aCsv.contains("INF247L01BM8"), "Gold/Silver asset ISIN must not appear in Schedule 112A CSV");

        // 2. Assert TaxReportExporter.generateItr2Report includes realized LTCG but ltcgExemptionUsed is 0.00
        TaxReportExporter.Itr2ScheduleCgReport report = TaxReportExporter.generateItr2Report(List.of(goldLot), fy);
        assertNotNull(report);
        assertEquals("25000.00", report.totalRealizedLtcg(), "Total realized LTCG must include the 25,000 gold gain");
        assertEquals("0.00", report.ltcgExemptionUsed(), "Section 112A exemption must NOT be applied to non-equity LTCG");
        assertEquals("0.00", report.netTaxableLtcg(), "Sec 112A taxable equity LTCG is 0.00 for non-equity asset");
    }

    @Test
    @DisplayName("P4.3 Test 2: Jan 31 2018 Grandfathering FMV Lower-Bound computes deemedCost = 18000.00 and balanceGain = 7000.00")
    void testJan312018GrandfatheringFmvLowerBound() {
        // Pre-2018 Equity lot: acquisitionDate = 2017-06-01, units = 100, cost = 10000, proceeds = 25000
        MatchedLot pre2018Lot = new MatchedLot(
            "MATCH_PRE_2018",
            "EV_DISP_PRE_2018",
            "LOT_PRE_2018",
            "INF109KC12U0",
            LocalDate.of(2017, 6, 1),
            LocalDate.of(2026, 5, 10),
            new BigDecimal("100.000"),
            new BigDecimal("10000.00"), // Actual cost = ₹10,000 (₹100/unit)
            new BigDecimal("25000.00"), // Sale proceeds = ₹25,000 (₹250/unit)
            new BigDecimal("15000.00"), // Nominal gain
            3265L,
            TaxTerm.LONG_TERM,
            AssetCategory.EQUITY
        );

        String fy = "2026-27";
        Map<String, String> assetNames = Map.of("INF109KC12U0", "ICICI Prudential Pre-2018 Grandfathered Fund");
        // FMV on 31-Jan-2018 = ₹180.00/unit -> Total FMV for 100 units = ₹18,000.00
        Map<String, BigDecimal> fmv2018Map = Map.of("INF109KC12U0", new BigDecimal("180.00"));

        String schedule112aCsv = Itr2CsvExporter.generateSchedule112aCsv(List.of(pre2018Lot), fy, assetNames, fmv2018Map);
        assertNotNull(schedule112aCsv);

        // Section 55(2)(ac) formula:
        // deemedCost = max(actualCost, min(FMV, saleProceeds)) = max(10000, min(18000, 25000)) = 18000.00
        // balanceGain = saleProceeds - deemedCost = 25000 - 18000 = 7000.00
        assertTrue(schedule112aCsv.contains("18000.00"), "Schedule 112A CSV must contain deemedCost = 18000.00");
        assertTrue(schedule112aCsv.contains("7000.00"), "Schedule 112A CSV must contain balanceGain = 7000.00");
        assertTrue(schedule112aCsv.contains("\"VALIDATED_SECTION_55_2_AC\""), "Status remark must show VALIDATED_SECTION_55_2_AC");
    }
}
