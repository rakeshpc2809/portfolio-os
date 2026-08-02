package com.portfolioos.core.reporting;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.matcher.TaxClassifier;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxEvent;
import com.portfolioos.core.model.EventType;
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
        Map<String, String> map = new HashMap<>();
        map.put("Schedule_112A.csv", generateSchedule112aCsv(matchedLots, fiscalYear, assetNameMap, Map.of()));
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
        sb.append("ISIN Code,Name of Share/Unit,No. of Shares/Units,Full Value of Consideration,Cost of Acquisition,FMV as on 31-Jan-2018,Total Deductions,Balance Capital Gain\n");

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

            BigDecimal fmvJan2018 = fmv2018Map.getOrDefault(isin, actualCost);

            // Statutory Section 55(2)(ac) Formula:
            // Deemed Cost = max(Actual Cost, min(FMV on 31-Jan-2018, Sale Proceeds))
            BigDecimal deemedCost;
            if (isPre2018) {
                BigDecimal lowerBound = fmvJan2018.min(proceeds);
                deemedCost = actualCost.max(lowerBound);
            } else {
                deemedCost = actualCost;
            }

            BigDecimal gain = proceeds.subtract(deemedCost);
            BigDecimal displayFmv = isPre2018 ? fmvJan2018 : BigDecimal.ZERO;

            sb.append("\"").append(isin).append("\",\"")
              .append(name.replace("\"", "\"\"")).append("\",")
              .append(fmt(totalUnits)).append(",")
              .append(fmt(proceeds)).append(",")
              .append(fmt(deemedCost)).append(",")
              .append(fmt(displayFmv)).append(",")
              .append("0.00,")
              .append(fmt(gain)).append("\n");
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
        sb.append("Section,Asset Type,Asset Name,Disposal Date,Sale Proceeds,Cost Basis,STCG Realized,Tax Rate\n");

        for (MatchedLot lot : stcgLots) {
            String name = assetNameMap.getOrDefault(lot.assetId(), lot.assetId());
            AssetCategory category = TaxClassifier.detectCategory(lot.assetId(), name);
            String section = (category == AssetCategory.DEBT_SPECIFIED_50AA) ? "Sec 50AA" : "Sec 111A";
            String taxRate = (category == AssetCategory.DEBT_SPECIFIED_50AA) ? "Slab Rate" : "20%";

            sb.append("\"").append(section).append("\",\"")
              .append(category.name()).append("\",\"")
              .append(name.replace("\"", "\"\"")).append("\",")
              .append(lot.disposalDate()).append(",")
              .append(fmt(lot.saleProceeds())).append(",")
              .append(fmt(lot.costBasis())).append(",")
              .append(fmt(lot.realizedGain())).append(",\"")
              .append(taxRate).append("\"\n");
        }

        return sb.toString();
    }

    public static String generateScheduleFaCsv(List<TaxEvent> allEventsList) {
        StringBuilder sb = new StringBuilder();
        sb.append("Country Code,Foreign Entity Name,Address,Initial Investment (INR),Peak Value INR (Requires Statement Verification),Closing Balance (INR),Gross Amount Paid/Credited\n");

        List<TaxEvent> intlEvents = allEventsList.stream().filter(e ->
            TaxClassifier.detectCategory(e.assetId(), e.assetName()) == AssetCategory.INTERNATIONAL
        ).toList();

        Map<String, List<TaxEvent>> grouped = intlEvents.stream().collect(Collectors.groupingBy(TaxEvent::assetId));
        for (Map.Entry<String, List<TaxEvent>> entry : grouped.entrySet()) {
            String isin = entry.getKey();
            List<TaxEvent> events = entry.getValue();

            String name = events.get(0).assetName();
            BigDecimal initialCost = BigDecimal.ZERO;
            for (TaxEvent e : events) {
                if (e.eventType() == EventType.ACQUISITION) {
                    initialCost = initialCost.add(e.grossAmount());
                }
            }

            sb.append("\"US\",\"").append(name.replace("\"", "\"\"")).append("\",\"United States\",")
              .append(fmt(initialCost)).append(",\"VERIFY_PEAK_NAV\",")
              .append(fmt(initialCost)).append(",0.00\n");
        }

        return sb.toString();
    }

    private static String fmt(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Pair<LocalDate, LocalDate> getFiscalYearBounds(String fiscalYear) {
        String[] parts = fiscalYear.split("-");
        int startYear = 2026;
        try {
            startYear = Integer.parseInt(parts[0].trim());
        } catch (Exception e) {
            // ignore
        }
        int endYear = startYear + 1;
        if (parts.length > 1 && parts[1].trim().length() == 2) {
            try {
                endYear = (startYear / 100) * 100 + Integer.parseInt(parts[1].trim());
            } catch (Exception e) {
                // ignore
            }
        }
        return new Pair<>(LocalDate.of(startYear, 4, 1), LocalDate.of(endYear, 3, 31));
    }
}
