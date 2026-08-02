package com.portfolioos.core.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MatchedLot(
    String matchId,
    String disposalEventId,
    String lotId,
    String assetId,
    LocalDate acquisitionDate,
    LocalDate disposalDate,
    BigDecimal unitsMatched,
    BigDecimal costBasis,
    BigDecimal saleProceeds,
    BigDecimal realizedGain,
    long holdingPeriodDays,
    TaxTerm taxTerm,
    AssetCategory assetCategory
) {}
