package com.portfolioos.core.reporting;

import com.portfolioos.core.model.AssetCategory;
import com.portfolioos.core.model.MatchedLot;
import com.portfolioos.core.model.TaxTerm;
import com.portfolioos.core.rules.TaxRulesConfig;
import com.portfolioos.core.rules.TaxRulesLoader;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public class ExemptionTracker {

    public record ExemptionStatus(
        String fiscalYear,
        String grossLtcg,
        String grossLtcl,
        String grossStcg,
        String grossStcl,
        String netStcg,
        String netLtcgBeforeExemption,
        String exemptionLimit,
        String exemptionUsed,
        String exemptionRemaining,
        String taxableLtcg
    ) {}

    public static ExemptionStatus calculateExemptionStatus(List<MatchedLot> matchedLots, String fiscalYear) {
        Pair<LocalDate, LocalDate> bounds = getFiscalYearBounds(fiscalYear);
        LocalDate startDate = bounds.first();
        LocalDate endDate = bounds.second();

        List<MatchedLot> stgLots = matchedLots.stream().filter(lot -> 
            lot.taxTerm() == TaxTerm.SHORT_TERM &&
            !lot.disposalDate().isBefore(startDate) &&
            !lot.disposalDate().isAfter(endDate)
        ).toList();

        // Section 112A exemption applies ONLY to equity assets
        List<MatchedLot> equityLtgLots = matchedLots.stream().filter(lot -> 
            lot.taxTerm() == TaxTerm.LONG_TERM &&
            lot.assetCategory() == AssetCategory.EQUITY &&
            !lot.disposalDate().isBefore(startDate) &&
            !lot.disposalDate().isAfter(endDate)
        ).toList();

        BigDecimal gST = BigDecimal.ZERO;
        BigDecimal lST = BigDecimal.ZERO;
        for (MatchedLot lot : stgLots) {
            if (lot.realizedGain().compareTo(BigDecimal.ZERO) > 0) {
                gST = gST.add(lot.realizedGain());
            } else {
                lST = lST.add(lot.realizedGain().abs());
            }
        }

        BigDecimal gLT = BigDecimal.ZERO;
        BigDecimal lLT = BigDecimal.ZERO;
        for (MatchedLot lot : equityLtgLots) {
            if (lot.realizedGain().compareTo(BigDecimal.ZERO) > 0) {
                gLT = gLT.add(lot.realizedGain());
            } else {
                lLT = lLT.add(lot.realizedGain().abs());
            }
        }

        // STCL offsets STCG first
        BigDecimal netStcg = gST.subtract(lST).max(BigDecimal.ZERO);
        BigDecimal remainingStcl = lST.subtract(gST).max(BigDecimal.ZERO);

        // LTCL offsets LTCG, remaining STCL offsets LTCG
        BigDecimal netLtcgBeforeExemption = gLT.subtract(lLT).subtract(remainingStcl).max(BigDecimal.ZERO);

        TaxRulesConfig rules = TaxRulesLoader.loadRules(fiscalYear);
        BigDecimal exemptionLimit = rules.equityExemptionLimit();
        BigDecimal exemptionUsed = netLtcgBeforeExemption.min(exemptionLimit);
        BigDecimal exemptionRemaining = exemptionLimit.subtract(exemptionUsed).max(BigDecimal.ZERO);
        BigDecimal taxableLtcg = netLtcgBeforeExemption.subtract(exemptionUsed).max(BigDecimal.ZERO);

        return new ExemptionStatus(
            fiscalYear,
            fmt(gLT),
            fmt(lLT),
            fmt(gST),
            fmt(lST),
            fmt(netStcg),
            fmt(netLtcgBeforeExemption),
            fmt(exemptionLimit),
            fmt(exemptionUsed),
            fmt(exemptionRemaining),
            fmt(taxableLtcg)
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

    public record Pair<A, B>(A first, B second) {}
}
