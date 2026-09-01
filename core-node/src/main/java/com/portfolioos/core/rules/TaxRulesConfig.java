package com.portfolioos.core.rules;

import java.math.BigDecimal;

public record TaxRulesConfig(
    String fiscalYear,
    long equityLtcgThresholdDays,
    BigDecimal equityLtcgRate,
    BigDecimal equityStcgRate,
    BigDecimal equityExemptionLimit,
    long goldInternationalThresholdDays,
    BigDecimal goldInternationalLtcgRate,
    boolean debtAlwaysShortTerm,
    BigDecimal slabRate,
    BigDecimal debtLegacyLtcgRate
) {
    public TaxRulesConfig(
        String fiscalYear,
        long equityLtcgThresholdDays,
        BigDecimal equityLtcgRate,
        BigDecimal equityStcgRate,
        BigDecimal equityExemptionLimit,
        long goldInternationalThresholdDays,
        BigDecimal goldInternationalLtcgRate,
        boolean debtAlwaysShortTerm
    ) {
        this(
            fiscalYear,
            equityLtcgThresholdDays,
            equityLtcgRate,
            equityStcgRate,
            equityExemptionLimit,
            goldInternationalThresholdDays,
            goldInternationalLtcgRate,
            debtAlwaysShortTerm,
            new BigDecimal("0.30"),
            new BigDecimal("0.125")
        );
    }
}
