package com.portfolioos.core.reporting;

import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.util.Pair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

    public static String generateSchedule112aCsv(
        List<MatchedLot> matchedLots,
        String fiscalYear,
        Map<String, String> assetNameMap,
        Map<String, BigDecimal> fmv2018Map
    ) {
        Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
        LocalDate startDate = bounds.first();
        LocalDate endDate = bounds.second();

        List<MatchedLot> ltcgLots = matchedLots.stream().filter(lot ->
            lot.taxTerm() == TaxTerm.LONG_TERM &&
            !lot.disposalDate().isBefore(startDate) &&
            !lot.disposalDate().isAfter(endDate)
        ).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("ISIN Code,Name of Share/Unit,No. of Shares/Units,Full Value of Consideration,Cost of Acquisition,FMV as on 31-Jan-2018,Total Deductions,Balance Capital Gain,Grandfathering Status\n");

        Map<String, List<MatchedLot>> grouped = ltcgLots.stream().collect(Collectors.groupingBy(MatchedLot::assetId));

        for (Map.Entry<String, List<MatchedLot>> entry : grouped.entrySet()) {
            String isin = entry.getKey();
            List<MatchedLot> lots = entry.getValue();

            String name = assetNameMap.getOrDefault(isin, isin);
            BigDecimal totalUnits = BigDecimal.ZERO;
            BigDecimal proceeds = BigDecimal.ZERO;
            BigDecimal actualCost = BigDecimal.ZERO;

            boolean isPre2018 = false;
            for (MatchedLot lot : lots) {
                totalUnits = totalUnits.add(lot.unitsMatched());
                proceeds = proceeds.add(lot.saleProceeds());
                actualCost = actualCost.add(lot.costBasis());
                if (lot.acquisitionDate().isBefore(GRANDFATHER_CUTOFF) || lot.acquisitionDate().isEqual(GRANDFATHER_CUTOFF)) {
                    isPre2018 = true;
                }
            }

            BigDecimal fmvJan2018 = null;
            boolean fmvAvailable = false;
            if (isPre2018) {
                if (fmv2018Map != null && fmv2018Map.containsKey(isin)) {
                    fmvJan2018 = fmv2018Map.get(isin);
                    fmvAvailable = true;
                } else {
                    System.err.println("WARNING: Pre-2018 lot for ISIN " + isin + " has no 2018-01-31 FMV data in fmv2018Map. Flagged as FMV_UNAVAILABLE_REVIEW_REQUIRED.");
                }
            }

            BigDecimal deemedCost;
            String statusRemark;
            if (isPre2018) {
                if (fmvAvailable && fmvJan2018 != null) {
                    BigDecimal lowerBound = fmvJan2018.min(proceeds);
                    deemedCost = actualCost.max(lowerBound);
                    statusRemark = "VALIDATED_SECTION_55_2_AC";
                } else {
                    // Fail visibly with flag rather than silently understating gains
                    deemedCost = actualCost;
                    statusRemark = "FMV_UNAVAILABLE_REVIEW_REQUIRED";
                }
            } else {
                deemedCost = actualCost;
                statusRemark = "POST_2018_ACQUISITION";
            }

            BigDecimal gain = proceeds.subtract(deemedCost);
            BigDecimal displayFmv = (isPre2018 && fmvAvailable && fmvJan2018 != null) ? fmvJan2018 : BigDecimal.ZERO;

            sb.append("\"").append(isin).append("\",\"")
              .append(name.replace("\"", "\"\"")).append("\",")
              .append(fmt(totalUnits)).append(",")
              .append(fmt(proceeds)).append(",")
              .append(fmt(deemedCost)).append(",")
              .append(fmt(displayFmv)).append(",")
              .append("0.00,")
              .append(fmt(gain)).append(",")
              .append("\"").append(statusRemark).append("\"\n");
        }

        return sb.toString();
    }

    public static String generateScheduleCgStcgCsv(List<MatchedLot> matchedLots, String fiscalYear, Map<String, String> assetNameMap) {
        Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
        LocalDate startDate = bounds.first();
        LocalDate endDate = bounds.second();

        List<MatchedLot> stcgLots = matchedLots.stream().filter(lot ->
            lot.taxTerm() == TaxTerm.SHORT_TERM &&
            !lot.disposalDate().isBefore(startDate) &&
            !lot.disposalDate().isAfter(endDate)
        ).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("ISIN Code,Name of Share/Unit,No. of Shares/Units,Full Value of Consideration,Cost of Acquisition,Balance Capital Gain\n");

        Map<String, List<MatchedLot>> grouped = stcgLots.stream().collect(Collectors.groupingBy(MatchedLot::assetId));

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

            sb.append("\"").append(isin).append("\",\"")
              .append(name.replace("\"", "\"\"")).append("\",")
              .append(fmt(totalUnits)).append(",")
              .append(fmt(proceeds)).append(",")
              .append(fmt(actualCost)).append(",")
              .append(fmt(gain)).append("\n");
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
