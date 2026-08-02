package com.portfolioos.core.xirr;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashFlow(
    LocalDate date,
    BigDecimal amount // negative for investments, positive for inflows / current valuation
) {}
