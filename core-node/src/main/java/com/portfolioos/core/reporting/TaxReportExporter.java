package com.portfolioos.core.reporting;

import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.util.Pair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public class TaxReportExporter {

    public record Itr2ScheduleCgReport(
        String fiscalYear,
        String totalSaleProceeds,
        String totalCostBasis,
        String totalRealizedStcg,
        String totalRealizedLtcg,
        String netTaxableStcg,
        String ltcgExemptionUsed,
        String netTaxableLtcg,
        int matchedLotCount
    ) {}

    public static Itr2ScheduleCgReport generateItr2Report(List<MatchedLot> matchedLots, String fiscalYear) {
        Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
        LocalDate startDate = bounds.first();
        LocalDate endDate = bounds.second();

        List<MatchedLot> fyLots = matchedLots.stream().filter(lot ->
            !lot.disposalDate().isBefore(startDate) &&
            !lot.disposalDate().isAfter(endDate)
        ).toList();

        BigDecimal totalSaleProceeds = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;
        BigDecimal totalStcg = BigDecimal.ZERO;
        BigDecimal totalLtcg = BigDecimal.ZERO;

        for (MatchedLot lot : fyLots) {
            totalSaleProceeds = totalSaleProceeds.add(lot.saleProceeds());
            totalCostBasis = totalCostBasis.add(lot.costBasis());
            if (lot.taxTerm() == TaxTerm.SHORT_TERM) {
                totalStcg = totalStcg.add(lot.realizedGain());
            } else if (lot.taxTerm() == TaxTerm.LONG_TERM) {
                totalLtcg = totalLtcg.add(lot.realizedGain());
            }
        }

        ExemptionTracker.ExemptionStatus exemptionStatus = ExemptionTracker.calculateExemptionStatus(fyLots, fiscalYear);

        return new Itr2ScheduleCgReport(
            fiscalYear,
            fmt(totalSaleProceeds),
            fmt(totalCostBasis),
            fmt(totalStcg),
            fmt(totalLtcg),
            exemptionStatus.netStcg(),
            exemptionStatus.exemptionUsed(),
            exemptionStatus.taxableLtcg(),
            fyLots.size()
        );
    }

    private static String fmt(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static Pair<LocalDate, LocalDate> getFiscalYearBounds(String fiscalYear) {
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
