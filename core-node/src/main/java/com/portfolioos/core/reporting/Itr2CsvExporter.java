package com.portfolioos.core.reporting;

import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.util.Pair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Itr2CsvExporter {

    private static final LocalDate GRANDFATHER_CUTOFF = LocalDate.of(2018, 1, 31);

    public static Map<String, String> exportItr2ScheduleCg(List<MatchedLot> matchedLots, String fiscalYear, Map<String, String> assetNameMap) {
        return exportItr2ScheduleCg(matchedLots, fiscalYear, assetNameMap, Map.of());
    }

    public static Map<String, String> exportItr2ScheduleCg(List<MatchedLot> matchedLots, String fiscalYear, Map<String, String> assetNameMap, Map<String, BigDecimal> fmv2018Map) {
        Map<String, String> map = new HashMap<>();
        map.put("Schedule_112A.csv", generateSchedule112aCsv(matchedLots, fiscalYear, assetNameMap, fmv2018Map));
        map.put("Schedule_STCG.csv", generateScheduleCgStcgCsv(matchedLots, fiscalYear, assetNameMap));
        return map;
    }

    public static List<TaxReportExporter.Schedule112aEntryDto> generateSchedule112aEntries(
        List<MatchedLot> matchedLots,
        String fiscalYear,
        Map<String, String> assetNameMap,
        Map<String, BigDecimal> fmv2018Map
    ) {
        return generateSchedule112aEntries(matchedLots, fiscalYear, assetNameMap, fmv2018Map, java.util.Collections.emptySet());
    }

    public static List<TaxReportExporter.Schedule112aEntryDto> generateSchedule112aEntries(
        List<MatchedLot> matchedLots,
        String fiscalYear,
        Map<String, String> assetNameMap,
        Map<String, BigDecimal> fmv2018Map,
        java.util.Set<String> estimatedIsins
    ) {
        Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
        LocalDate startDate = bounds.first();
        LocalDate endDate = bounds.second();

        List<MatchedLot> ltcgLots = matchedLots.stream().filter(lot ->
            lot.taxTerm() == TaxTerm.LONG_TERM &&
            !lot.disposalDate().isBefore(startDate) &&
            !lot.disposalDate().isAfter(endDate)
        ).toList();

        Map<String, List<MatchedLot>> grouped = ltcgLots.stream().collect(Collectors.groupingBy(MatchedLot::assetId));
        List<TaxReportExporter.Schedule112aEntryDto> result = new ArrayList<>();

        for (Map.Entry<String, List<MatchedLot>> entry : grouped.entrySet()) {
            String isin = entry.getKey();
            List<MatchedLot> lots = entry.getValue();

            String name = assetNameMap.getOrDefault(isin, isin);
            BigDecimal totalUnits = BigDecimal.ZERO;
            BigDecimal proceeds = BigDecimal.ZERO;
            BigDecimal deemedCost = BigDecimal.ZERO;

            boolean isPre2018 = false;
            boolean hasPost2018 = false;
            BigDecimal fmvJan2018 = (fmv2018Map != null) ? fmv2018Map.get(isin) : null;

            for (MatchedLot lot : lots) {
                totalUnits = totalUnits.add(lot.unitsMatched());
                proceeds = proceeds.add(lot.saleProceeds());

                boolean lotPre2018 = !lot.acquisitionDate().isAfter(GRANDFATHER_CUTOFF);
                if (lotPre2018) {
                    isPre2018 = true;
                    if (fmvJan2018 == null) {
                        System.err.println("CRITICAL ERROR: Pre-2018 lot for ISIN " + isin + " (" + name + ") has no 2018-01-31 FMV data. Sec 55(2)(ac) calculation cannot proceed safely.");
                        throw new IllegalStateException("MISSING_FMV_DATA: Pre-2018 grandfathered equity lot for ISIN " + isin + " (" + name + ") requires 2018-01-31 FMV to compute Sec 55(2)(ac) cost basis accurately. Please configure NAV as of 31-Jan-2018 before exporting Schedule 112A.");
                    }
                    BigDecimal lotFmv = fmvJan2018.multiply(lot.unitsMatched());
                    BigDecimal lotLowerBound = lotFmv.min(lot.saleProceeds());
                    deemedCost = deemedCost.add(lot.costBasis().max(lotLowerBound));
                } else {
                    hasPost2018 = true;
                    deemedCost = deemedCost.add(lot.costBasis());
                }
            }

            boolean isEst = estimatedIsins != null && estimatedIsins.contains(isin);
            String statusRemark;
            if (isEst) {
                statusRemark = isPre2018 ? "SECTION_55_2_AC_ESTIMATED" : "PROVISIONAL_COST_BASIS";
            } else if (isPre2018 && hasPost2018) {
                statusRemark = "MIXED_PRE_AND_POST_2018";
            } else if (isPre2018) {
                statusRemark = "VALIDATED_SECTION_55_2_AC";
            } else {
                statusRemark = "POST_2018_ACQUISITION";
            }

            BigDecimal gain = proceeds.subtract(deemedCost);
            BigDecimal displayFmv = (isPre2018 && fmvJan2018 != null) ? fmvJan2018 : BigDecimal.ZERO;
            boolean fmvApp = isPre2018;

            result.add(new TaxReportExporter.Schedule112aEntryDto(
                isin,
                name,
                totalUnits,
                proceeds,
                deemedCost,
                displayFmv,
                BigDecimal.ZERO,
                gain,
                statusRemark,
                fmvApp,
                isEst,
                fmt(totalUnits),
                fmt(proceeds),
                fmt(deemedCost),
                fmt(displayFmv),
                fmt(gain)
            ));
        }
        return result;
    }

    public static String generateSchedule112aCsv(
        List<MatchedLot> matchedLots,
        String fiscalYear,
        Map<String, String> assetNameMap,
        Map<String, BigDecimal> fmv2018Map
    ) {
        List<TaxReportExporter.Schedule112aEntryDto> entries = generateSchedule112aEntries(matchedLots, fiscalYear, assetNameMap, fmv2018Map);
        StringBuilder sb = new StringBuilder();
        sb.append("ISIN Code,Name of Share/Unit,No. of Shares/Units,Full Value of Consideration,Cost of Acquisition,FMV as on 31-Jan-2018,Total Deductions,Balance Capital Gain,Grandfathering Status\n");

        for (TaxReportExporter.Schedule112aEntryDto e : entries) {
            sb.append("\"").append(e.isin()).append("\",\"")
              .append(e.assetName().replace("\"", "\"\"")).append("\",")
              .append(e.formattedUnits()).append(",")
              .append(e.formattedSaleProceeds()).append(",")
              .append(e.formattedCostBasis()).append(",")
              .append(e.formattedFmv2018()).append(",")
              .append("0.00,")
              .append(e.formattedBalanceGain()).append(",")
              .append("\"").append(e.grandfatheringStatus()).append("\"\n");
        }

        return sb.toString();
    }

    public static List<TaxReportExporter.ScheduleStcgEntryDto> generateScheduleStcgEntries(
        List<MatchedLot> matchedLots,
        String fiscalYear,
        Map<String, String> assetNameMap
    ) {
        return generateScheduleStcgEntries(matchedLots, fiscalYear, assetNameMap, java.util.Collections.emptySet());
    }

    public static List<TaxReportExporter.ScheduleStcgEntryDto> generateScheduleStcgEntries(
        List<MatchedLot> matchedLots,
        String fiscalYear,
        Map<String, String> assetNameMap,
        java.util.Set<String> estimatedIsins
    ) {
        Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
        LocalDate startDate = bounds.first();
        LocalDate endDate = bounds.second();

        List<MatchedLot> stcgLots = matchedLots.stream().filter(lot ->
            lot.taxTerm() == TaxTerm.SHORT_TERM &&
            !lot.disposalDate().isBefore(startDate) &&
            !lot.disposalDate().isAfter(endDate)
        ).toList();

        Map<String, List<MatchedLot>> grouped = stcgLots.stream().collect(Collectors.groupingBy(MatchedLot::assetId));
        List<TaxReportExporter.ScheduleStcgEntryDto> result = new ArrayList<>();

        for (Map.Entry<String, List<MatchedLot>> entry : grouped.entrySet()) {
            String isin = entry.getKey();
            List<MatchedLot> lots = entry.getValue();

            String name = assetNameMap.getOrDefault(isin, isin);
            BigDecimal totalUnits = BigDecimal.ZERO;
            BigDecimal proceeds = BigDecimal.ZERO;
            BigDecimal actualCost = BigDecimal.ZERO;

            for (MatchedLot lot : lots) {
                totalUnits = totalUnits.add(lot.unitsMatched());
                proceeds = proceeds.add(lot.saleProceeds());
                actualCost = actualCost.add(lot.costBasis());
            }

            BigDecimal gain = proceeds.subtract(actualCost);
            boolean isEst = estimatedIsins != null && estimatedIsins.contains(isin);

            result.add(new TaxReportExporter.ScheduleStcgEntryDto(
                isin,
                name,
                totalUnits,
                proceeds,
                actualCost,
                gain,
                isEst,
                fmt(totalUnits),
                fmt(proceeds),
                fmt(actualCost),
                fmt(gain)
            ));
        }
        return result;
    }

    public static String generateScheduleCgStcgCsv(List<MatchedLot> matchedLots, String fiscalYear, Map<String, String> assetNameMap) {
        List<TaxReportExporter.ScheduleStcgEntryDto> entries = generateScheduleStcgEntries(matchedLots, fiscalYear, assetNameMap);
        StringBuilder sb = new StringBuilder();
        sb.append("ISIN Code,Name of Share/Unit,No. of Shares/Units,Full Value of Consideration,Cost of Acquisition,Balance Capital Gain\n");

        for (TaxReportExporter.ScheduleStcgEntryDto e : entries) {
            sb.append("\"").append(e.isin()).append("\",\"")
              .append(e.assetName().replace("\"", "\"\"")).append("\",")
              .append(e.formattedUnits()).append(",")
              .append(e.formattedSaleProceeds()).append(",")
              .append(e.formattedCostBasis()).append(",")
              .append(e.formattedBalanceGain()).append("\n");
        }

        return sb.toString();
    }

    private static Pair<LocalDate, LocalDate> getFiscalYearBounds(String fy) {
        String[] parts = fy.split("-");
        int startYear = Integer.parseInt(parts[0]);
        LocalDate start = LocalDate.of(startYear, 4, 1);
        LocalDate end = LocalDate.of(startYear + 1, 3, 31);
        return new Pair<>(start, end);
    }

    private static String fmt(BigDecimal val) {
        if (val == null) return "0.00";
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
