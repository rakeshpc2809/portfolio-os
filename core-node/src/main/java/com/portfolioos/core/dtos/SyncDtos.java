package com.portfolioos.core.dtos;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

public class SyncDtos {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SyncInfoDto(
        long timestamp,
        String ledgerHash,
        String generatedAt,
        String fiscalYear,
        double portfolioXirr,
        String xirrPercentage,
        double totalInvested,
        double currentValue,
        double unrealizedGain,
        String formattedCurrentValue,
        String formattedTotalInvested,
        String formattedUnrealizedGain
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FlatHoldingDto(
        String isin,
        String fundName,
        double totalUnits,
        double avgCost,
        double xirr,
        String assetBucket,
        double currentValue,
        double investedValue,
        String formattedCurrentValue,
        String formattedInvestedValue,
        Double expenseRatio,
        String terStatus,
        String terAsOfDate
    ) {
        public FlatHoldingDto(
            String isin,
            String fundName,
            double totalUnits,
            double avgCost,
            double xirr,
            String assetBucket,
            double currentValue,
            double investedValue,
            String formattedCurrentValue,
            String formattedInvestedValue
        ) {
            this(
                isin,
                fundName,
                totalUnits,
                avgCost,
                xirr,
                assetBucket,
                currentValue,
                investedValue,
                formattedCurrentValue,
                formattedInvestedValue,
                0.20,
                "OPTIMAL",
                "Aug 2026"
            );
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FlatTaxLotDto(
        String isin,
        String buyDate,
        double units,
        String taxClassification,
        boolean isLongTerm,
        Double grandfatheredNav,
        double costPerUnit,
        long holdingDays,
        long daysToLtcg,
        Double estimatedTaxDrag,
        boolean isHarvestCandidate
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RadarSignalDto(
        String signalType,
        String title,
        String subtitle,
        String description,
        String severity,
        String badgeText
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record NetWorthPointDto(
        String date,
        double valuation,
        double invested
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UnidirectionalSyncSnapshot(
        SyncInfoDto syncInfo,
        List<FlatHoldingDto> holdings,
        List<FlatTaxLotDto> taxLots,
        List<RadarSignalDto> radarSignals,
        List<NetWorthPointDto> netWorthHistory,
        RebalancePlanDtos.RebalancePlanDto rebalancePlan
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PairRequestDto(
        String deviceId,
        String deviceName
    ) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PairResponseDto(
        String status,
        String token,
        String serverName
    ) {}
}
