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
