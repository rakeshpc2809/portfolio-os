package com.portfolioos.core.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Lot(
    String lotId,
    String assetId,
    String assetName,
    LocalDate acquisitionDate,
    BigDecimal originalUnits,
    BigDecimal remainingUnits,
    BigDecimal costPerUnit,
    BigDecimal totalCostBasis,
    boolean isGrandfathered,
    BigDecimal fmv20180131
) {
    public Lot withRemainingUnitsAndCost(BigDecimal remaining, BigDecimal cost, BigDecimal costBasis) {
        return new Lot(
            lotId, assetId, assetName, acquisitionDate, originalUnits,
            remaining, cost, costBasis, isGrandfathered, fmv20180131
        );
    }
    
    public Lot withAssetDetails(String newAssetId, String newAssetName, BigDecimal newOriginal, BigDecimal newRemaining, BigDecimal newCostPerUnit) {
        return new Lot(
            lotId, newAssetId, newAssetName, acquisitionDate, newOriginal,
            newRemaining, newCostPerUnit, totalCostBasis, isGrandfathered, fmv20180131
        );
    }
}
