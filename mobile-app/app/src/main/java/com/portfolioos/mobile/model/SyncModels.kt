package com.portfolioos.mobile.model

data class SyncSnapshot(
    val syncInfo: SyncInfoDto,
    val holdings: List<FlatHoldingDto>,
    val taxLots: List<FlatTaxLotDto>,
    val radarSignals: List<RadarSignalDto>
)

data class SyncInfoDto(
    val epochTimestamp: Long,
    val ledgerHash: String,
    val syncDate: String,
    val fiscalYear: String,
    val xirrPercentage: Double,
    val xirrFormatted: String
)

data class FlatHoldingDto(
    val assetId: String,
    val assetName: String,
    val units: Double,
    val costPrice: Double,
    val xirrPercentage: Double,
    val assetBucket: String
)

data class FlatTaxLotDto(
    val assetId: String,
    val purchaseDate: String,
    val units: Double,
    val taxClassification: String,
    val isLtcg: Boolean,
    val grandfatheredFmv: Double?,
    val costPrice: Double,
    val holdingDays: Long,
    val daysToLtcg: Long
)

data class RadarSignalDto(
    val signalType: String,
    val assetName: String,
    val title: String,
    val description: String,
    val statusLevel: String,
    val actionText: String
)
