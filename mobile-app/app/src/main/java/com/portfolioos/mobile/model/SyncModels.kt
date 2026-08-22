package com.portfolioos.mobile.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

@Immutable
data class NetWorthPointDto(
    @SerializedName("date") val date: String = "",
    @SerializedName("valuation") val valuation: Double = 0.0,
    @SerializedName("invested") val invested: Double = 0.0
)

@Immutable
data class SyncSnapshot(
    @SerializedName("sync_info") val syncInfo: SyncInfoDto? = null,
    @SerializedName("holdings") val holdings: List<FlatHoldingDto>? = emptyList(),
    @SerializedName("tax_lots") val taxLots: List<FlatTaxLotDto>? = emptyList(),
    @SerializedName("radar_signals") val radarSignals: List<RadarSignalDto>? = emptyList(),
    @SerializedName("net_worth_history") val netWorthHistory: List<NetWorthPointDto>? = emptyList(),
    @SerializedName("rebalance_plan") val rebalancePlan: RebalancePlanDto? = null
)

@Immutable
data class RebalancePlanDto(
    @SerializedName("plan_id") val planId: String = "",
    @SerializedName("generated_at") val generatedAt: String = "",
    @SerializedName("trigger") val trigger: RebalanceTriggerDto? = null,
    @SerializedName("sell_side") val sellSide: SellSidePlanDto? = null,
    @SerializedName("buy_side") val buySide: BuySidePlanDto? = null,
    @SerializedName("reasoning_narrative") val reasoningNarrative: ReasoningNarrativeDto? = null
)

@Immutable
data class BuySidePlanDto(
    @SerializedName("total_to_invest") val totalToInvest: Double = 0.0,
    @SerializedName("is_manual_lumpsum") val isManualLumpsum: Boolean = false,
    @SerializedName("buckets") val buckets: List<RebalanceBucketAllocationDto> = emptyList()
)

@Immutable
data class RebalanceBucketAllocationDto(
    @SerializedName("bucket") val bucket: String = "",
    @SerializedName("target_pct") val targetPct: Double = 0.0,
    @SerializedName("current_pct") val currentPct: Double = 0.0,
    @SerializedName("post_rebalance_pct") val postRebalancePct: Double = 0.0,
    @SerializedName("amount_allocated") val amountAllocated: Double = 0.0,
    @SerializedName("fund_breakdown") val fundBreakdown: List<FundAllocationDto> = emptyList()
)

@Immutable
data class FundAllocationDto(
    @SerializedName("fund_id") val fundId: String = "",
    @SerializedName("fund_name") val fundName: String = "",
    @SerializedName("amount") val amount: Double = 0.0
)

@Immutable
data class ReasoningNarrativeDto(
    @SerializedName("headline") val headline: String = "",
    @SerializedName("paragraphs") val paragraphs: List<String> = emptyList(),
    @SerializedName("generated_from_template_version") val generatedFromTemplateVersion: String = ""
)

@Immutable
data class RebalanceTriggerDto(
    @SerializedName("type") val type: String = "",
    @SerializedName("reason_code") val reasonCode: String = "",
    @SerializedName("reason_label") val reasonLabel: String = ""
)

@Immutable
data class SellSidePlanDto(
    @SerializedName("total_required") val totalRequired: Double = 0.0,
    @SerializedName("waterfall") val waterfall: List<WaterfallTierDto> = emptyList(),
    @SerializedName("tax_summary") val taxSummary: TaxSummaryDto? = null
)

@Immutable
data class WaterfallTierDto(
    @SerializedName("tier") val tier: String = "",
    @SerializedName("tier_label") val tierLabel: String = "",
    @SerializedName("available") val available: Double = 0.0,
    @SerializedName("sold") val sold: Double = 0.0,
    @SerializedName("skipped_reason") val skippedReason: String? = null,
    @SerializedName("lots") val lots: List<RebalanceLotImpactDto> = emptyList()
)

@Immutable
data class RebalanceLotImpactDto(
    @SerializedName("lot_id") val lotId: String = "",
    @SerializedName("fund_id") val fundId: String = "",
    @SerializedName("fund_name") val fundName: String = "",
    @SerializedName("acquisition_date") val acquisitionDate: String = "",
    @SerializedName("holding_days") val holdingDays: Long = 0L,
    @SerializedName("units_sold") val unitsSold: Double = 0.0,
    @SerializedName("cost_basis") val costBasis: Double = 0.0,
    @SerializedName("sale_proceeds") val saleProceeds: Double = 0.0,
    @SerializedName("realized_gain") val realizedGain: Double = 0.0,
    @SerializedName("tax_term") val taxTerm: String = "",
    @SerializedName("tax_impact") val taxImpact: LotTaxImpactDto? = null
)

@Immutable
data class LotTaxImpactDto(
    @SerializedName("regime") val regime: String = "",
    @SerializedName("exemption_applied") val exemptionApplied: Double = 0.0,
    @SerializedName("taxable_amount") val taxableAmount: Double = 0.0,
    @SerializedName("tax_amount") val taxAmount: Double = 0.0
)

@Immutable
data class TaxSummaryDto(
    @SerializedName("total_realized_gain") val totalRealizedGain: Double = 0.0,
    @SerializedName("total_ltcg_exempt") val totalLtcgExempt: Double = 0.0,
    @SerializedName("total_stcg_taxable") val totalStcgTaxable: Double = 0.0,
    @SerializedName("total_tax_estimate") val totalTaxEstimate: Double = 0.0
)

@Immutable
data class SyncInfoDto(
    @SerializedName("timestamp") val timestamp: Long = 0L,
    @SerializedName("ledger_hash") val ledgerHash: String = "",
    @SerializedName("generated_at") val generatedAt: String = "",
    @SerializedName("fiscal_year") val fiscalYear: String = "2026-27",
    @SerializedName("portfolio_xirr") val portfolioXirr: Double = 0.0,
    @SerializedName("xirr_percentage") val xirrPercentage: String = "0.00%",
    @SerializedName("total_invested") val totalInvested: Double = 0.0,
    @SerializedName("current_value") val currentValue: Double = 0.0,
    @SerializedName("unrealized_gain") val unrealizedGain: Double = 0.0,
    @SerializedName("formatted_current_value") val formattedCurrentValue: String = "₹0.00",
    @SerializedName("formatted_total_invested") val formattedTotalInvested: String = "₹0.00",
    @SerializedName("formatted_unrealized_gain") val formattedUnrealizedGain: String = "₹0.00"
)

@Immutable
data class FlatHoldingDto(
    @SerializedName("isin") val isin: String = "",
    @SerializedName("fund_name") val fundName: String = "",
    @SerializedName("total_units") val totalUnits: Double = 0.0,
    @SerializedName("avg_cost") val avgCost: Double = 0.0,
    @SerializedName("xirr") val xirr: Double = 0.0,
    @SerializedName("asset_bucket") val assetBucket: String = "",
    @SerializedName("current_value") val currentValue: Double = 0.0,
    @SerializedName("invested_value") val investedValue: Double = 0.0,
    @SerializedName("formatted_current_value") val formattedCurrentValue: String = "₹0.00",
    @SerializedName("formatted_invested_value") val formattedInvestedValue: String = "₹0.00"
)

@Immutable
data class FlatTaxLotDto(
    @SerializedName("isin") val isin: String = "",
    @SerializedName("buy_date") val buyDate: String = "",
    @SerializedName("units") val units: Double = 0.0,
    @SerializedName("tax_classification") val taxClassification: String = "",
    @SerializedName("is_long_term") val isLongTerm: Boolean = false,
    @SerializedName("grandfathered_nav") val grandfatheredNav: Double? = null,
    @SerializedName("cost_per_unit") val costPerUnit: Double = 0.0,
    @SerializedName("holding_days") val holdingDays: Long = 0L,
    @SerializedName("days_to_ltcg") val daysToLtcg: Long = 0L
)

@Immutable
data class RadarSignalDto(
    @SerializedName("signal_type") val signalType: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("subtitle") val subtitle: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("severity") val severity: String = "",
    @SerializedName("badge_text") val badgeText: String = ""
)

@Immutable
data class TradeSimulationRequestDto(
    @SerializedName("isin") val isin: String,
    @SerializedName("schemeName") val schemeName: String,
    @SerializedName("units") val units: Double,
    @SerializedName("pricePerUnit") val pricePerUnit: Double,
    @SerializedName("tradeDate") val tradeDate: String = "",
    @SerializedName("tradeType") val tradeType: String // DISPOSAL or ACQUISITION
)

@Immutable
data class TradeSimulationResultDto(
    @SerializedName("isin") val isin: String = "",
    @SerializedName("schemeName") val schemeName: String = "",
    @SerializedName("tradeType") val tradeType: String = "",
    @SerializedName("units") val units: Double = 0.0,
    @SerializedName("pricePerUnit") val pricePerUnit: Double = 0.0,
    @SerializedName("grossTradeAmount") val grossTradeAmount: Double = 0.0,
    @SerializedName("grossCapitalGain") val grossCapitalGain: Double = 0.0,
    @SerializedName("ltcgEquity") val ltcgEquity: Double = 0.0,
    @SerializedName("stcgEquity") val stcgEquity: Double = 0.0,
    @SerializedName("debtGain") val debtGain: Double = 0.0,
    @SerializedName("sec112aExemptionApplied") val sec112aExemptionApplied: Double = 0.0,
    @SerializedName("estimatedTaxLiability") val estimatedTaxLiability: Double = 0.0,
    @SerializedName("postTradeNetWorth") val postTradeNetWorth: Double = 0.0,
    @SerializedName("postTradeInvestedCost") val postTradeInvestedCost: Double = 0.0,
    @SerializedName("postTradeXirr") val postTradeXirr: Double = 0.0,
    @SerializedName("taxSummaryNotice") val taxSummaryNotice: String = ""
)

@Immutable
data class FanChartTrajectoryDto(
    @SerializedName("year") val year: Int = 0,
    @SerializedName("p10") val p10: Double = 0.0,
    @SerializedName("p25") val p25: Double = 0.0,
    @SerializedName("p50") val p50: Double = 0.0,
    @SerializedName("p75") val p75: Double = 0.0,
    @SerializedName("p90") val p90: Double = 0.0
)

@Immutable
data class FireScenarioDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("monthly_expense_today") val monthlyExpenseToday: String = "",
    @SerializedName("active") val active: Boolean = false
)

@Immutable
data class FireSummaryResponseDto(
    @SerializedName("active_scenario_label") val activeScenarioLabel: String = "",
    @SerializedName("monthly_expense_today") val monthlyExpenseToday: String = "",
    @SerializedName("annual_expense") val annualExpense: String = "",
    @SerializedName("required_corpus") val requiredCorpus: String = "",
    @SerializedName("total_net_worth") val totalNetWorth: String = "",
    @SerializedName("epf_balance") val epfBalance: String = "",
    @SerializedName("non_retirement_goal_allocations") val nonRetirementGoalAllocations: String = "",
    @SerializedName("fire_investable_net_worth") val fireInvestableNetWorth: String = "",
    @SerializedName("projected_corpus_at_target_age") val projectedCorpusAtTargetAge: String = "",
    @SerializedName("years_remaining") val yearsRemaining: Int = 0,
    @SerializedName("status") val status: String = "",
    @SerializedName("shortage_or_surplus_amount") val shortageOrSurplusAmount: String = "",
    @SerializedName("review_date_passed") val reviewDatePassed: Boolean = false,
    @SerializedName("scenarios") val scenarios: List<FireScenarioDto> = emptyList(),
    @SerializedName("monte_carlo_success_rate_pct") val monteCarloSuccessRatePct: Double = 0.0,
    @SerializedName("monte_carlo_median_corpus") val monteCarloMedianCorpus: String = "",
    @SerializedName("monte_carlo_tenth_percentile_corpus") val monteCarloTenthPercentileCorpus: String = "",
    @SerializedName("monte_carlo_data_source") val monteCarloDataSource: String = "",
    @SerializedName("monte_carlo_data_source_label") val monteCarloDataSourceLabel: String = "",
    @SerializedName("fan_chart_trajectories") val fanChartTrajectories: List<FanChartTrajectoryDto> = emptyList()
)

@Immutable
data class BenchmarkAnalyticsDto(
    @SerializedName("alpha") val alpha: Double = 0.0,
    @SerializedName("beta") val beta: Double = 0.0,
    @SerializedName("sharpe_ratio") val sharpeRatio: Double = 0.0,
    @SerializedName("tracking_error") val trackingError: Double = 0.0,
    @SerializedName("r_squared") val rSquared: Double = 0.0,
    @SerializedName("benchmark_name") val benchmarkName: String = "NIFTY_50_TRI"
)

@Immutable
data class StockConcentrationDto(
    @SerializedName("stock_symbol") val stockSymbol: String = "",
    @SerializedName("company_name") val companyName: String = "",
    @SerializedName("portfolio_percentage") val portfolioWeightPct: Double = 0.0
)

@Immutable
data class PairwiseOverlapDto(
    @SerializedName("fund_a") val fundA: String = "",
    @SerializedName("fund_b") val fundB: String = "",
    @SerializedName("overlap_percentage") val overlapPercentage: Double = 0.0,
    @SerializedName("common_holdings_count") val commonHoldingsCount: Int = 0
)

@Immutable
data class OverlapReportDto(
    @SerializedName("coverage_type") val coverageType: String = "",
    @SerializedName("pairwise_matrix") val pairwiseMatrix: List<PairwiseOverlapDto> = emptyList(),
    @SerializedName("portfolio_stock_concentrations") val stockConcentrations: List<StockConcentrationDto> = emptyList()
)
