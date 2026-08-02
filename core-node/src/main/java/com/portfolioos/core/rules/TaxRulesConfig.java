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
    boolean debtAlwaysShortTerm
) {}
