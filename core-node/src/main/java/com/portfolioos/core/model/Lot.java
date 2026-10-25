package com.portfolioos.core.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public BigDecimal effectiveCostPerUnit(BigDecimal currentOrSaleNav) {
        if (isGrandfathered && fmv20180131 != null && fmv20180131.compareTo(BigDecimal.ZERO) > 0 && currentOrSaleNav != null) {
            return costPerUnit.max(fmv20180131.min(currentOrSaleNav));
        }
        return costPerUnit;
    }

    public BigDecimal effectiveTotalCostBasis(BigDecimal currentOrSaleNav) {
        if (isGrandfathered && fmv20180131 != null && fmv20180131.compareTo(BigDecimal.ZERO) > 0 && currentOrSaleNav != null) {
            return remainingUnits.multiply(effectiveCostPerUnit(currentOrSaleNav)).setScale(2, RoundingMode.HALF_UP);
        }
        return totalCostBasis;
    }
}
