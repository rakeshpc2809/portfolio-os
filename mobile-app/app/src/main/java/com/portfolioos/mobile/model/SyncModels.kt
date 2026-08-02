package com.portfolioos.mobile.model

data class SyncSnapshot(
    val syncInfo: SyncInfoDto? = null,
    val holdings: List<FlatHoldingDto>? = emptyList(),
    val taxLots: List<FlatTaxLotDto>? = emptyList(),
    val radarSignals: List<RadarSignalDto>? = emptyList()
)

data class SyncInfoDto(
    val epochTimestamp: Long = 0L,
    val ledgerHash: String = "",
    val syncDate: String = "",
    val fiscalYear: String = "2026-27",
    val xirrPercentage: Double = 0.0,
    val xirrFormatted: String = "0.00%"
)

data class FlatHoldingDto(
    val assetId: String = "",
    val assetName: String = "",
    val units: Double = 0.0,
    val costPrice: Double = 0.0,
    val xirrPercentage: Double = 0.0,
    val assetBucket: String = ""
)

data class FlatTaxLotDto(
    val assetId: String = "",
    val purchaseDate: String = "",
    val units: Double = 0.0,
    val taxClassification: String = "",
    val isLtcg: Boolean = false,
    val grandfatheredFmv: Double? = null,
    val costPrice: Double = 0.0,
    val holdingDays: Long = 0L,
    val daysToLtcg: Long = 0L
)

data class RadarSignalDto(
    val signalType: String = "",
    val assetName: String = "",
    val title: String = "",
    val description: String = "",
    val statusLevel: String = "",
    val actionText: String = ""
)
