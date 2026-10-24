package com.portfolioos.core.reporting;

import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.util.Pair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public class TaxReportExporter {

    public record Schedule112aEntryDto(
        String isin,
        String assetName,
        BigDecimal units,
        BigDecimal saleProceeds,
        BigDecimal costBasis,
        BigDecimal fmv2018,
        BigDecimal totalDeductions,
        BigDecimal balanceGain,
        String grandfatheringStatus,
        boolean fmvApplicable,
        boolean isEstimate,
        String formattedUnits,
        String formattedSaleProceeds,
        String formattedCostBasis,
        String formattedFmv2018,
        String formattedBalanceGain
    ) {}

    public record ScheduleStcgEntryDto(
        String isin,
        String assetName,
        BigDecimal units,
        BigDecimal saleProceeds,
        BigDecimal costBasis,
        BigDecimal balanceGain,
        boolean isEstimate,
        String formattedUnits,
        String formattedSaleProceeds,
        String formattedCostBasis,
        String formattedBalanceGain
    ) {}

    public record Itr2DetailsDto(
        Itr2ScheduleCgReport summary,
        List<Schedule112aEntryDto> schedule112a,
        List<ScheduleStcgEntryDto> scheduleStcg,
        List<com.portfolioos.core.dtos.ReportDtos.RealizedLogDto> matchedLots
    ) {}

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
        return com.portfolioos.core.rules.TaxRulesLoader.getFiscalYearBounds(fiscalYear);
    }
}
