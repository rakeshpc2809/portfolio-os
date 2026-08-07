package com.portfolioos.core.dtos;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReportDtos {

    public record PortfolioSummaryResponse(
        String totalInvested,
        String totalCurrentValue,
        String totalUnrealizedGain,
        String xirrPercentage,
        int activeHoldingCount,
        int staleNavCount
    ) {}

    public record AssetAllocationEntry(
        String assetId,
        String assetName,
        String investedValue,
        String currentValue,
        String percentage,
        boolean navStale
    ) {}

    public record CategoryAllocationEntry(
        String category,
        String categoryName,
        String investedValue,
        String currentValue,
        String percentage
    ) {}

    public record OpenLotDto(
        String lotId,
        String acquisitionDate,
        String remainingUnits,
        String costPerUnit,
        String totalCostBasis,
        String currentNav,
        String currentValue,
        String unrealizedGain,
        long holdingDays,
        long daysToLtcg,
        boolean isLtcg
    ) {}

    public record HoldingDetailDto(
        String assetId,
        String assetName,
        String category,
        String investedValue,
        String currentValue,
        String unrealizedGain,
        String unrealizedGainPct,
        String allocationPct,
        boolean navStale,
        List<OpenLotDto> lots
    ) {}

    public record HarvestOpportunityDto(
        String assetId,
        String assetName,
        String lotId,
        String remainingUnits,
        String potentialHarvestableLoss
    ) {}

    public record MaturationLadderDto(
        String assetId,
        String assetName,
        String lotId,
        String acquisitionDate,
        String remainingUnits,
        String totalCostBasis,
        String currentValue,
        String unrealizedGain,
        long holdingDays,
        long daysRemainingToLtcg,
        String targetLtcgDate
    ) {}

    public record RealizedLogDto(
        String matchId,
        String disposalDate,
        String acquisitionDate,
        String assetId,
        String assetName,
        String unitsMatched,
        String saleProceeds,
        String costBasis,
        String realizedGain,
        String taxTerm,
        long holdingPeriodDays
    ) {}

    public record BucketStatusDto(
        String bucket,
        String currentValue,
        String currentPct,
        String targetPct,
        String driftPct,
        boolean isDrifted
    ) {}

    public record RebalanceRecommendationDto(
        String assetId,
        String assetName,
        String bucket,
        String action,
        String amount,
        String triggerType,
        String estimatedTaxDrag,
        String taxTermSummary
    ) {}

    public record DrawdownStatusDto(
        String benchmarkName,
        String currentLevel,
        String rollingHigh,
        String drawdownPct,
        List<Integer> activeRungsFired,
        String recommendedBufferDeployPct
    ) {}

    public record BucketRebalanceResponse(
        List<BucketStatusDto> bucketStatuses,
        List<RebalanceRecommendationDto> recommendations,
        DrawdownStatusDto drawdownStatus,
        boolean calendarTriggerFired,
        boolean drawdownTriggerFired
    ) {}

    public record GoalAllocationDto(
        String holdingId,
        String holdingName,
        String goalTag,
        String allocatedAmount
    ) {}

    public record GoalSummaryResponse(
        String totalLiquidHoldings,
        String allocatedGoalsAmount,
        String unallocatedCash,
        Map<String, String> allocationsByGoal,
        List<GoalAllocationDto> goalAllocations
    ) {}

    public record FireScenarioDto(
        String id,
        String label,
        String monthlyExpenseToday,
        boolean active
    ) {}

    public record FireSummaryResponse(
        @JsonProperty("active_scenario_label") String activeScenarioLabel,
        @JsonProperty("monthly_expense_today") String monthlyExpenseToday,
        @JsonProperty("annual_expense") String annualExpense,
        @JsonProperty("required_corpus") String requiredCorpus,
        @JsonProperty("total_net_worth") String totalNetWorth,
        @JsonProperty("epf_balance") String epfBalance,
        @JsonProperty("non_retirement_goal_allocations") String nonRetirementGoalAllocations,
        @JsonProperty("fire_investable_net_worth") String fireInvestableNetWorth,
        @JsonProperty("projected_corpus_at_target_age") String projectedCorpusAtTargetAge,
        @JsonProperty("years_remaining") int yearsRemaining,
        @JsonProperty("status") String status,
        @JsonProperty("shortage_or_surplus_amount") String shortageOrSurplusAmount,
        @JsonProperty("review_date_passed") boolean reviewDatePassed,
        @JsonProperty("scenarios") List<FireScenarioDto> scenarios,
        @JsonProperty("monte_carlo_success_rate_pct") double monteCarloSuccessRatePct,
        @JsonProperty("monte_carlo_median_corpus") String monteCarloMedianCorpus,
        @JsonProperty("monte_carlo_tenth_percentile_corpus") String monteCarloTenthPercentileCorpus,
        @JsonProperty("monte_carlo_data_source") String monteCarloDataSource,
        @JsonProperty("monte_carlo_data_source_label") String monteCarloDataSourceLabel
    ) {
        @JsonProperty("monte_carlo_success_rate_pct")
        public double getMonteCarloSuccessRatePct() { return monteCarloSuccessRatePct; }

        @JsonProperty("monte_carlo_median_corpus")
        public String getMonteCarloMedianCorpus() { return monteCarloMedianCorpus; }

        @JsonProperty("monte_carlo_tenth_percentile_corpus")
        public String getMonteCarloTenthPercentileCorpus() { return monteCarloTenthPercentileCorpus; }

        @JsonProperty("monte_carlo_data_source")
        public String getMonteCarloDataSource() { return monteCarloDataSource; }

        @JsonProperty("monte_carlo_data_source_label")
        public String getMonteCarloDataSourceLabel() { return monteCarloDataSourceLabel; }
    }

    public record RebalanceLotDto(
        String assetName,
        String unitsToSell,
        String redemptionProceeds,
        String estimatedGain,
        String taxTerm,
        String estimatedTaxDrag
    ) {}

    public record RebalancePreviewDto(
        String targetRedemptionAmount,
        String actualRedemptionAmount,
        String totalEstimatedGain,
        String totalTaxDrag,
        String effectiveTaxRatePct,
        String ltcgExemptionHarvested,
        List<RebalanceLotDto> selectedLots
    ) {}

    public record PhasedOutAssetSummaryDto(
        String assetId,
        String assetName,
        String currentUnits,
        String currentValue,
        String totalCostBasis,
        String unrealizedGain,
        boolean isLtcg,
        String estimatedTaxDrag
    ) {}

    public record ExistingSipAllocationDto(
        String assetId,
        String assetName,
        String sipWeightPct,
        String deploymentAmount
    ) {}

    public record ConsolidationPreviewResponse(
        List<PhasedOutAssetSummaryDto> phasedOutAssets,
        String totalProceeds,
        String totalEstimatedGain,
        String totalTaxDrag,
        String ltcgExemptionHarvested,
        List<ExistingSipAllocationDto> proRataAllocations,
        boolean isRebalanceWindowOpen,
        String nextScheduledWindow
    ) {}

    public record WaterfallStepDto(
        String tier,
        String lotId,
        String assetId,
        String assetName,
        String unitsSold,
        String proceeds,
        String realizedGain,
        String taxTerm,
        String taxDrag
    ) {}

    public record WaterfallResponse(
        String bucket,
        String targetAmount,
        String satisfiedAmount,
        String deferredAmount,
        String deferralReason,
        List<WaterfallStepDto> steps,
        String totalTaxDrag,
        String ltcgExemptionConsumed
    ) {}
}
