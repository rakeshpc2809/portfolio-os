package com.portfolioos.mobile.model

import com.google.gson.annotations.SerializedName

data class SyncSnapshot(
    @SerializedName("sync_info") val syncInfo: SyncInfoDto? = null,
    @SerializedName("holdings") val holdings: List<FlatHoldingDto>? = emptyList(),
    @SerializedName("tax_lots") val taxLots: List<FlatTaxLotDto>? = emptyList(),
    @SerializedName("radar_signals") val radarSignals: List<RadarSignalDto>? = emptyList()
)

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

data class RadarSignalDto(
    @SerializedName("signal_type") val signalType: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("subtitle") val subtitle: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("severity") val severity: String = "",
    @SerializedName("badge_text") val badgeText: String = ""
)
