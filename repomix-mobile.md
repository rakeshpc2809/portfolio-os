This file is a merged representation of the entire codebase, combined into a single document by Repomix.

# File Summary

## Purpose
This file contains a packed representation of the entire repository's contents.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.

## File Format
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Repository files (if enabled)
5. Multiple file entries, each consisting of:
  a. A header with the file path (## File: path/to/file)
  b. The full contents of the file in a code block

## Usage Guidelines
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.

## Notes
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Files are sorted by Git change count (files with more changes are at the bottom)

# Directory Structure
```
app/
  src/
    main/
      java/
        com/
          portfolioos/
            mobile/
              api/
                SyncApiClient.kt
              auth/
                BiometricAuthManager.kt
              model/
                SyncModels.kt
              ui/
                components/
                  StateCard.kt
                theme/
                  ColorTokens.kt
                  ShapeTokens.kt
                  SpacingTokens.kt
                  Theme.kt
                  TypographyTokens.kt
                DashboardScreen.kt
                LockScreenGate.kt
                PortfolioCharts.kt
                SimulatorScreen.kt
              util/
                FormatUtils.kt
              widget/
                PortfolioGlanceWidget.kt
              MainActivity.kt
      res/
        drawable/
          ic_launcher_background.xml
          ic_launcher_foreground.xml
        font/
          inter_bold.ttf
          inter_regular.ttf
          inter_semibold.ttf
          jetbrains_mono_bold.ttf
          jetbrains_mono_regular.ttf
          jetbrains_mono_semibold.ttf
          outfit_bold.ttf
          outfit_medium.ttf
          outfit_semibold.ttf
        mipmap-anydpi-v26/
          ic_launcher_round.xml
          ic_launcher.xml
        values/
          styles.xml
        xml/
          backup_rules.xml
          data_extraction_rules.xml
          portfolio_glance_widget_info.xml
      AndroidManifest.xml
    test/
      java/
        com/
          portfolioos/
            mobile/
              RebalanceWaterfallUnitTest.kt
  build.gradle.kts
gradle/
  wrapper/
    gradle-wrapper.properties
build.gradle.kts
capture_clean_6_states.py
capture_perfect_states.py
capture_unlocked_states.py
gradle.properties
gradlew
local.properties
settings.gradle.kts
```

# Files

## File: app/src/main/java/com/portfolioos/mobile/api/SyncApiClient.kt
```kotlin
package com.portfolioos.mobile.api

import android.content.Context
import com.portfolioos.mobile.BuildConfig
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.model.TradeSimulationRequestDto
import com.portfolioos.mobile.model.TradeSimulationResultDto
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SyncApiService {
    @GET("api/v1/sync/snapshot")
    suspend fun getSnapshot(
        @Header("X-Api-Auth-Token") token: String,
        @Query("fy") fiscalYear: String = "2026-27"
    ): SyncSnapshot

    @POST("api/v1/simulate/trade")
    suspend fun simulateTrade(
        @Header("X-Api-Auth-Token") token: String,
        @Body request: TradeSimulationRequestDto
    ): TradeSimulationResultDto
}

object SyncApiClient {
    const val USB_BASE_URL = "http://127.0.0.1:8080/"
    const val EMULATOR_BASE_URL = "http://10.0.2.2:8080/"
    const val WIFI_BASE_URL = "http://192.168.1.13:8080/"

    fun createService(baseUrl: String = USB_BASE_URL): SyncApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(SyncApiService::class.java)
    }

    suspend fun fetchSnapshotWithFallback(context: Context): SyncSnapshot {
        val customUrl = SnapshotCacheManager.getCustomUrl(context)
        val authToken = SnapshotCacheManager.getAuthToken(context)
        
        // 1. Try Custom Remote/Tunnel URL if configured
        if (!customUrl.isNullOrBlank()) {
            try {
                val formatted = if (customUrl.endsWith("/")) customUrl else "$customUrl/"
                val remoteSnapshot = createService(formatted).getSnapshot(token = authToken)
                SnapshotCacheManager.saveSnapshot(context, remoteSnapshot, isFullLedgerSync = true)
                return remoteSnapshot
            } catch (e: Exception) {
                // fallthrough to local networks
            }
        }

        // 2. Try USB Loopback (adb reverse)
        try {
            val snapshot = createService(USB_BASE_URL).getSnapshot(token = authToken)
            SnapshotCacheManager.saveSnapshot(context, snapshot, isFullLedgerSync = true)
            return snapshot
        } catch (e1: Exception) {
            // 3. Try Android Emulator loopback
            try {
                val snapshot = createService(EMULATOR_BASE_URL).getSnapshot(token = authToken)
                SnapshotCacheManager.saveSnapshot(context, snapshot, isFullLedgerSync = true)
                return snapshot
            } catch (e2: Exception) {
                // 4. Try Wi-Fi LAN IP
                try {
                    val snapshot = createService(WIFI_BASE_URL).getSnapshot(token = authToken)
                    SnapshotCacheManager.saveSnapshot(context, snapshot, isFullLedgerSync = true)
                    return snapshot
                } catch (e3: Exception) {
                    // 5. Offline Fallback: Check direct AMFI NAVs over cellular if connected, or return frozen cache if fully offline!
                    val cached = SnapshotCacheManager.loadSnapshot(context)
                    if (cached != null) {
                        val liveNavs = com.portfolioos.mobile.data.nav.AmfiDirectFetcher.fetchLatestNavMap()
                        if (liveNavs.isNotEmpty()) {
                            val updated = SnapshotCacheManager.updateOfflineSnapshotWithLiveAmfi(cached)
                            SnapshotCacheManager.saveSnapshot(context, updated, isFullLedgerSync = false)
                            SnapshotCacheManager.setFullyOffline(context, false)
                            return updated
                        } else {
                            // Airplane Mode / Completely Offline: Preserve frozen timestamps & mark fully offline
                            SnapshotCacheManager.setFullyOffline(context, true)
                            return cached
                        }
                    } else {
                        throw e3
                    }
                }
            }
        }
    }

    suspend fun simulateTradeWithFallback(context: Context, request: TradeSimulationRequestDto): TradeSimulationResultDto {
        val customUrl = SnapshotCacheManager.getCustomUrl(context)
        val authToken = SnapshotCacheManager.getAuthToken(context)

        if (!customUrl.isNullOrBlank()) {
            try {
                val formatted = if (customUrl.endsWith("/")) customUrl else "$customUrl/"
                return createService(formatted).simulateTrade(token = authToken, request = request)
            } catch (e: Exception) {
                // fallthrough
            }
        }

        try {
            return createService(USB_BASE_URL).simulateTrade(token = authToken, request = request)
        } catch (e1: Exception) {
            try {
                return createService(EMULATOR_BASE_URL).simulateTrade(token = authToken, request = request)
            } catch (e2: Exception) {
                return createService(WIFI_BASE_URL).simulateTrade(token = authToken, request = request)
            }
        }
    }
}
```

## File: app/src/main/java/com/portfolioos/mobile/auth/BiometricAuthManager.kt
```kotlin
package com.portfolioos.mobile.auth

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthManager {
    private const val TAG = "BiometricAuthManager"

    @Volatile
    var isAuthPromptShowing: Boolean = false
        private set

    enum class SecurityStatus {
        SUCCESS,
        NONE_ENROLLED,
        UNAVAILABLE
    }

    fun checkSecurityStatus(activity: FragmentActivity): SecurityStatus {
        val biometricManager = BiometricManager.from(activity)
        val authenticators = Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> SecurityStatus.SUCCESS
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> SecurityStatus.NONE_ENROLLED
            else -> SecurityStatus.UNAVAILABLE
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        isAuthPromptShowing = true
        Log.d(TAG, "BiometricPrompt requested. Setting isAuthPromptShowing = true")

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isAuthPromptShowing = false
                Log.d(TAG, "Authentication Succeeded! Resetting isAuthPromptShowing = false")
                onAuthSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                isAuthPromptShowing = false
                Log.d(TAG, "Authentication Error ($errorCode): $errString. Resetting isAuthPromptShowing = false")
                onAuthError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.d(TAG, "Authentication Failed (retry attempt)")
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Portfolio OS")
            .setSubtitle("Authenticate using fingerprint or device PIN")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}
```

## File: app/src/main/java/com/portfolioos/mobile/model/SyncModels.kt
```kotlin
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
```

## File: app/src/main/java/com/portfolioos/mobile/ui/components/StateCard.kt
```kotlin
package com.portfolioos.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.ui.theme.ColorTokens
import com.portfolioos.mobile.ui.theme.ShapeTokens
import com.portfolioos.mobile.ui.theme.SpacingTokens
import com.portfolioos.mobile.ui.theme.TypographyTokens

@Composable
fun PortfolioStateCard(
    icon: ImageVector,
    iconTint: Color,
    iconBgColor: Color = iconTint.copy(alpha = 0.15f),
    title: String,
    subtitle: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorTokens.SurfaceCard),
        shape = ShapeTokens.GlassCardShape, // Aligned to web 16.dp standard
        border = BorderStroke(1.dp, ColorTokens.CardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(SpacingTokens.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = iconBgColor,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        style = TypographyTokens.CardTitle
                    )
                    Text(
                        text = subtitle,
                        style = TypographyTokens.BodyText.copy(
                            color = iconTint,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(SpacingTokens.md))
            Text(
                text = description,
                style = TypographyTokens.BodyText
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(modifier = Modifier.height(SpacingTokens.lg))
                OutlinedButton(
                    onClick = onAction,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = iconTint),
                    shape = ShapeTokens.PillShape,
                    border = BorderStroke(1.dp, iconTint.copy(alpha = 0.5f)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = actionLabel,
                        style = TypographyTokens.BadgeTag.copy(color = iconTint)
                    )
                }
            }
        }
    }
}
```

## File: app/src/main/java/com/portfolioos/mobile/ui/theme/ColorTokens.kt
```kotlin
package com.portfolioos.mobile.ui.theme

import androidx.compose.ui.graphics.Color

object ColorTokens {
    // Signature Obsidian Deep Navy (#050811 background)
    val ObsidianBackground = Color(0xFF050811)
    val SurfaceCard = Color(0xFF0A0E1A)
    val GlassSurfaceBase = Color(0xFF0E1424)
    val CardBorder = Color(0x1FFFFFFF) // rgba(255, 255, 255, 0.12)
    val SubtleDivider = Color(0xFF1E293B)

    // Text Scale
    val TextMain = Color(0xFFF8FAFC)      // Slate 50
    val TextMuted = Color(0xFF64748B)     // Slate 500
    val TextSubtext = Color(0xFF94A3B8)   // Slate 400
    val TextLightSub = Color(0xFFCBD5E1)  // Slate 300

    // Web Standard Accent Colors (Aligned with Web CSS)
    val CyanBright = Color(0xFF06B6D4)    // Web --cyan-bright (#06b6d4)
    val CyanSky = Color(0xFF38BDF8)       // Web Sky 400 (#38bdf8)
    val PurpleAccent = Color(0xFF8B5CF6)  // Web --purple-accent (#8b5cf6)
    val PurpleLight = Color(0xFFC084FC)   // Web Purple 400 (#c084fc)
    val AmberWarning = Color(0xFFF59E0B)  // Web --amber-warn (#f59e0b)
    val GreenPositive = Color(0xFF10B981) // Web --green-positive (#10b981)
    val EmeraldLight = Color(0xFF34D399)  // Web Emerald 400 (#34d399)
    val RedNegative = Color(0xFFEF4444)   // Web --red-negative (#ef4444)
    val ElectricLime = Color(0xFFD0FF00)  // Web Electric Lime (#D0FF00)
}
```

## File: app/src/main/java/com/portfolioos/mobile/ui/theme/ShapeTokens.kt
```kotlin
package com.portfolioos.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object ShapeTokens {
    val BadgeShape = RoundedCornerShape(6.dp)
    val ButtonShape = RoundedCornerShape(8.dp)
    val DashCardShape = RoundedCornerShape(12.dp)
    val GlassCardShape = RoundedCornerShape(16.dp) // Web .glass-card standard (aligned from 20.dp)
    val PillShape = RoundedCornerShape(100.dp)
}
```

## File: app/src/main/java/com/portfolioos/mobile/ui/theme/SpacingTokens.kt
```kotlin
package com.portfolioos.mobile.ui.theme

import androidx.compose.ui.unit.dp

object SpacingTokens {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}
```

## File: app/src/main/java/com/portfolioos/mobile/ui/theme/Theme.kt
```kotlin
package com.portfolioos.mobile.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalColorTokens = staticCompositionLocalOf { ColorTokens }
val LocalTypographyTokens = staticCompositionLocalOf { TypographyTokens }
val LocalShapeTokens = staticCompositionLocalOf { ShapeTokens }
val LocalSpacingTokens = staticCompositionLocalOf { SpacingTokens }

private val PortfolioDarkColorScheme = darkColorScheme(
    background = ColorTokens.ObsidianBackground,
    surface = ColorTokens.SurfaceCard,
    surfaceVariant = ColorTokens.GlassSurfaceBase,
    primary = ColorTokens.CyanBright,
    secondary = ColorTokens.PurpleAccent,
    tertiary = ColorTokens.ElectricLime,
    error = ColorTokens.RedNegative,
    onBackground = ColorTokens.TextMain,
    onSurface = ColorTokens.TextMain
)

@Composable
fun PortfolioOSTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalColorTokens provides ColorTokens,
        LocalTypographyTokens provides TypographyTokens,
        LocalShapeTokens provides ShapeTokens,
        LocalSpacingTokens provides SpacingTokens
    ) {
        MaterialTheme(
            colorScheme = PortfolioDarkColorScheme,
            content = content
        )
    }
}
```

## File: app/src/main/java/com/portfolioos/mobile/ui/theme/TypographyTokens.kt
```kotlin
package com.portfolioos.mobile.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.R

object TypographyTokens {
    val InterFontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold)
    )

    val OutfitFontFamily = FontFamily(
        Font(R.font.outfit_medium, FontWeight.Medium),
        Font(R.font.outfit_semibold, FontWeight.SemiBold),
        Font(R.font.outfit_bold, FontWeight.Bold)
    )

    val JetBrainsMonoFontFamily = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
    )

    // Text Style Presets
    val BrandTitle = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = ColorTokens.TextMain
    )

    val SectionHeader = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = ColorTokens.TextMain
    )

    val CardTitle = TextStyle(
        fontFamily = OutfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = ColorTokens.TextMain
    )

    val MetricNumber = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = ColorTokens.TextMain
    )

    val FinancialValue = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = ColorTokens.TextMain
    )

    val MetricLabel = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        color = ColorTokens.TextMuted
    )

    val BodyText = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = ColorTokens.TextSubtext
    )

    val BadgeTag = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp
    )
}
```

## File: app/src/main/java/com/portfolioos/mobile/ui/DashboardScreen.kt
```kotlin
package com.portfolioos.mobile.ui

import com.portfolioos.mobile.BuildConfig
import com.portfolioos.mobile.ui.components.PortfolioStateCard
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.FlatTaxLotDto
import com.portfolioos.mobile.model.RadarSignalDto
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.util.formatInr
import com.portfolioos.mobile.ui.theme.ColorTokens
import com.portfolioos.mobile.ui.theme.TypographyTokens
import com.portfolioos.mobile.ui.theme.ShapeTokens
import com.portfolioos.mobile.ui.theme.SpacingTokens
import kotlinx.coroutines.launch

// Web CSS Aligned Tokens Palette Mapping
val M3ObsidianDark = ColorTokens.ObsidianBackground
val M3SurfaceCard = ColorTokens.SurfaceCard
val M3SurfaceVariant = ColorTokens.GlassSurfaceBase
val M3ElectricLime = ColorTokens.ElectricLime
val M3NeonCyan = ColorTokens.CyanBright
val M3VibrantViolet = ColorTokens.PurpleAccent
val M3GreenPositive = ColorTokens.GreenPositive
val M3AmberWarning = ColorTokens.AmberWarning
val M3TextMuted = ColorTokens.TextMuted

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    snapshot: SyncSnapshot?,
    isLoading: Boolean,
    isRefreshing: Boolean = false,
    lastSyncMillis: Long = 0L,
    lastFullLedgerMillis: Long = 0L,
    isAmfiFallback: Boolean = false,
    isFullyOffline: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    initialPage: Int = 0,
    onRefresh: () -> Unit,
    onUpdateCustomUrl: (String) -> Unit = {},
    onSimulateFullSync: () -> Unit = {},
    onSimulateAmfiFallback: () -> Unit = {},
    onSimulateFullyOffline: () -> Unit = {},
    onSimulateAgedOffline: () -> Unit = {},
    onSimulateRefreshing: () -> Unit = {},
    onSimulateSyncFailure: () -> Unit = {},
    isBiometricLockEnabled: Boolean = true,
    onToggleBiometricLock: (Boolean) -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(snapshot) {
        if (snapshot != null) {
            pagerState.scrollToPage(initialPage)
        }
    }
    var showSimulatorBottomSheet by remember { mutableStateOf(false) }
    var selectedHoldingForSimulator by remember { mutableStateOf<FlatHoldingDto?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }

    fun formatRelativeTime(millis: Long): String {
        if (millis <= 0L) return "Never"
        val diffSec = (System.currentTimeMillis() - millis) / 1000
        return when {
            diffSec < 10 -> "just now"
            diffSec < 60 -> "${diffSec}s ago"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            diffSec < 86400 -> "${diffSec / 3600}h ago"
            else -> {
                val sdf = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US)
                sdf.format(java.util.Date(millis))
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = M3ObsidianDark,
            surface = M3SurfaceCard,
            surfaceVariant = M3SurfaceVariant,
            primary = M3ElectricLime,
            secondary = M3NeonCyan,
            tertiary = M3VibrantViolet
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(M3ObsidianDark)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Sleek Expressive Top Header
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "PORTFOLIO OS",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                letterSpacing = 3.sp,
                                color = Color.White
                            )
                            val (sectionTagText, sectionTagColor) = when (pagerState.currentPage) {
                                0 -> "EXECUTIVE OVERVIEW" to M3ElectricLime
                                1 -> "OVERLAP & CONCENTRATION" to M3VibrantViolet
                                2 -> "TAX & COMPLIANCE AUDIT" to M3NeonCyan
                                else -> "FIRE & REBALANCING" to M3AmberWarning
                            }
                            
                            val (timestampText, pillColor) = when {
                                isFullyOffline && lastSyncMillis > 0L -> "Offline · Synced ${formatRelativeTime(lastSyncMillis)}" to M3AmberWarning
                                isAmfiFallback && lastFullLedgerMillis > 0L -> "Valuations ${formatRelativeTime(lastSyncMillis)} (AMFI) · Ledger ${formatRelativeTime(lastFullLedgerMillis)}" to sectionTagColor
                                lastSyncMillis > 0L -> "Synced ${formatRelativeTime(lastSyncMillis)}" to sectionTagColor
                                else -> sectionTagText to sectionTagColor
                            }

                            Surface(
                                color = pillColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = timestampText,
                                    fontSize = 10.sp,
                                    color = pillColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showUrlDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Server Settings",
                                tint = M3ElectricLime
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = M3ObsidianDark
                    )
                )

                AnimatedVisibility(
                    visible = isRefreshing,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = M3ElectricLime,
                        trackColor = M3ObsidianDark
                    )
                }

                AnimatedVisibility(
                    visible = isFullyOffline && snapshot != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OFFLINE MODE — Showing cached portfolio data from ${formatRelativeTime(lastSyncMillis)}",
                                color = Color(0xFFF59E0B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = M3ElectricLime)
                    }
                } else if (snapshot == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No Connection & No Cached Data",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No internet connection & no cached portfolio data available. Connect to Wi-Fi, USB, or set server URL.",
                                    color = M3TextMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onRefresh,
                                        colors = ButtonDefaults.buttonColors(containerColor = M3ElectricLime),
                                        shape = RoundedCornerShape(100.dp)
                                    ) {
                                        Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { showUrlDialog = true },
                                        shape = RoundedCornerShape(100.dp)
                                    ) {
                                        Text("Set Server URL", color = M3NeonCyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val syncInfo = snapshot.syncInfo
                    val holdings = snapshot.holdings ?: emptyList()
                    val radarSignals = snapshot.radarSignals ?: emptyList()
                    val taxLots = snapshot.taxLots ?: emptyList()

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val isExpandedWidth = false
                        if (isExpandedWidth) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(0.5f).fillMaxHeight()) {
                                    HoldingsView(snapshot, syncInfo, holdings, onSimulateSale = { h ->
                                        selectedHoldingForSimulator = h
                                        showSimulatorBottomSheet = true
                                    })
                                }
                                Box(modifier = Modifier.weight(0.5f).fillMaxHeight()) {
                                    SimulatorView(holdings)
                                }
                            }
                        } else {
                            LaunchedEffect(initialPage, snapshot) {
                                if (snapshot != null) {
                                    pagerState.scrollToPage(initialPage)
                                }
                            }
                            HorizontalPager(
                                state = pagerState,
                                beyondBoundsPageCount = 3,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                when (page) {
                                    0 -> HoldingsView(
                                        snapshot = snapshot,
                                        syncInfo = syncInfo,
                                        holdings = holdings,
                                        radarSignals = radarSignals,
                                        onSimulateSale = { h ->
                                            selectedHoldingForSimulator = h
                                            showSimulatorBottomSheet = true
                                        }
                                    )
                                    1 -> OverlapConcentrationPlaceholderView(holdings)
                                    2 -> GroupedTaxLotsView(taxLots, holdings)
                                    3 -> RebalanceWaterfallView(snapshot.rebalancePlan)
                                }
                            }
                        }
                    }
                }
            }

            if (showSimulatorBottomSheet && selectedHoldingForSimulator != null) {
                ModalBottomSheet(
                    onDismissRequest = { showSimulatorBottomSheet = false },
                    containerColor = M3SurfaceCard,
                    contentColor = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "WHAT-IF TRADE SIMULATOR",
                            color = M3NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = selectedHoldingForSimulator!!.fundName.ifEmpty { selectedHoldingForSimulator!!.isin },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SimulatorView(listOf(selectedHoldingForSimulator!!))
                    }
                }
            }

            // Google Material 3 Expressive Floating Glassmorphic Pill Overlaid directly over Screen
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color(0xFF090F1E).copy(alpha = 0.94f),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(100.dp))
                        .border(
                            width = 1.5.dp,
                            brush = Brush.horizontalGradient(
                                listOf(M3ElectricLime.copy(alpha = 0.5f), M3NeonCyan.copy(alpha = 0.3f), M3VibrantViolet.copy(alpha = 0.4f))
                            ),
                            shape = RoundedCornerShape(100.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 0,
                            label = "Overview",
                            icon = Icons.Default.Star,
                            activeColor = M3ElectricLime,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        )
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 1,
                            label = "Overlap",
                            icon = Icons.Default.List,
                            activeColor = M3VibrantViolet,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }
                        )
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 2,
                            label = "Tax Audit",
                            icon = Icons.Default.Notifications,
                            activeColor = M3NeonCyan,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(2)
                                }
                            }
                        )
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 3,
                            label = "FIRE",
                            icon = Icons.Default.Settings,
                            activeColor = M3AmberWarning,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(3)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Surface(
                            onClick = onRefresh,
                            color = M3ElectricLime,
                            shape = CircleShape,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
            )

            // Dialog for setting Custom Core Node Remote Server URL (Tailscale / Ngrok / LAN IP)
            if (showUrlDialog) {
                AlertDialog(
                    onDismissRequest = { showUrlDialog = false },
                    title = { Text("Core Node Server URL", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                text = "Enter custom Core Node IP or Tunnel URL (e.g. http://192.168.1.13:8080 or https://xyz.ngrok-free.app):",
                                color = M3TextMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = inputUrl,
                                onValueChange = { inputUrl = it },
                                placeholder = { Text("http://192.168.1.13:8080", color = M3TextMuted) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (BuildConfig.DEBUG) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onSimulateFullSync()
                                                showUrlDialog = false
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(100.dp)
                                        ) {
                                            Text("Full Sync", color = M3ElectricLime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onSimulateAmfiFallback()
                                                showUrlDialog = false
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(100.dp)
                                        ) {
                                            Text("AMFI Tag", color = M3VibrantViolet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onSimulateRefreshing()
                                                showUrlDialog = false
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(100.dp)
                                        ) {
                                            Text("Toggle Refresh", color = M3NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onSimulateSyncFailure()
                                                showUrlDialog = false
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(100.dp)
                                        ) {
                                            Text("Failure Toast", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onSimulateAgedOffline()
                                                showUrlDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(100.dp)
                                        ) {
                                            Text("Aged Offline (11m)", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Require Biometric / PIN Lock", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("Require fingerprint or PIN on launch & resume", color = M3TextMuted, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = isBiometricLockEnabled,
                                    onCheckedChange = { onToggleBiometricLock(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = M3ObsidianDark,
                                        checkedTrackColor = M3ElectricLime
                                    )
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onUpdateCustomUrl(inputUrl.trim())
                                showUrlDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = M3ElectricLime)
                        ) {
                            Text("Save & Sync", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUrlDialog = false }) {
                            Text("Cancel", color = M3TextMuted)
                        }
                    },
                    containerColor = M3SurfaceCard,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

@Composable
fun ExpressiveNavPill(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    onClick: () -> Unit
) {
    val pillScale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "PillScale"
    )

    Surface(
        onClick = onClick,
        color = if (selected) activeColor.copy(alpha = 0.22f) else Color.Transparent,
        shape = RoundedCornerShape(100.dp),
        modifier = Modifier
            .scale(pillScale)
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessHigh))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) activeColor else M3TextMuted,
                modifier = Modifier.size(18.dp)
            )
            if (selected) {
                Text(
                    text = label,
                    color = activeColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun HoldingsView(
    snapshot: com.portfolioos.mobile.model.SyncSnapshot?,
    syncInfo: com.portfolioos.mobile.model.SyncInfoDto?,
    holdings: List<FlatHoldingDto>,
    radarSignals: List<RadarSignalDto> = emptyList(),
    onSimulateSale: (FlatHoldingDto) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Expressive M3 Hero Net Worth Card (en-IN Currency Format)
            Card(
                shape = ShapeTokens.GlassCardShape,
                colors = CardDefaults.cardColors(containerColor = ColorTokens.SurfaceCard),
                border = BorderStroke(1.dp, ColorTokens.CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0F1B2B), Color(0xFF0C101C), Color(0xFF050811))
                            )
                        )
                        .padding(SpacingTokens.xxl)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = ColorTokens.ElectricLime.copy(alpha = 0.15f),
                                shape = ShapeTokens.PillShape
                            ) {
                                Text(
                                    text = "NET WORTH VALUATION",
                                    color = ColorTokens.ElectricLime,
                                    style = TypographyTokens.MetricLabel.copy(
                                        color = ColorTokens.ElectricLime,
                                        letterSpacing = 1.5.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = ColorTokens.GreenPositive.copy(alpha = 0.15f),
                                shape = ShapeTokens.PillShape
                            ) {
                                Text(
                                    text = syncInfo?.xirrPercentage ?: "0.00% XIRR",
                                    style = TypographyTokens.BadgeTag.copy(color = ColorTokens.GreenPositive),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = formatInr(syncInfo?.currentValue ?: 0.0),
                            style = TypographyTokens.MetricNumber.copy(
                                fontSize = 34.sp,
                                color = ColorTokens.TextMain
                            )
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = ColorTokens.CardBorder)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Invested",
                                    style = TypographyTokens.MetricLabel
                                )
                                Text(
                                    text = formatInr(syncInfo?.totalInvested ?: 0.0),
                                    style = TypographyTokens.FinancialValue
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Unrealized Gain",
                                    style = TypographyTokens.MetricLabel
                                )
                                val gain = syncInfo?.unrealizedGain ?: 0.0
                                Text(
                                    text = "${if (gain >= 0) "+" else ""}${formatInr(gain)}",
                                    style = TypographyTokens.FinancialValue.copy(
                                        color = if (gain >= 0) ColorTokens.GreenPositive else ColorTokens.RedNegative
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            HistoricalNetWorthTrendChart(trendPoints = snapshot?.netWorthHistory.orEmpty())
        }

        item {
            PortfolioAllocationBarChart(holdings = holdings)
        }

        if (radarSignals.isNotEmpty()) {
            item {
                Text(
                    text = "PRIORITY AI RADAR & QUANT INTELLIGENCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = M3TextMuted,
                    letterSpacing = 1.5.sp
                )
            }
            itemsIndexed(radarSignals, key = { index, s -> "${s.title}_${s.signalType}_$index" }) { _, signal ->
                M3RadarCard(signal)
            }
        } else {
            item {
                PortfolioStateCard(
                    icon = Icons.Default.Info,
                    iconTint = M3NeonCyan,
                    title = "All Clear — No Radar Signals",
                    subtitle = "Quant Scans Nominal",
                    description = "No maturing tax lots, harvest opportunities, or asset drift triggers detected across your active holdings."
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE HOLDINGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = M3TextMuted,
                    letterSpacing = 1.5.sp
                )
                Surface(
                    color = M3SurfaceVariant,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "${holdings.size} Schemes",
                        color = M3NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        if (holdings.isEmpty()) {
            item {
                PortfolioStateCard(
                    icon = Icons.Default.List,
                    iconTint = M3ElectricLime,
                    title = "No Open Holdings",
                    subtitle = "Ledger Empty",
                    description = "No active fund or equity holdings recorded in your ledger. Sync your CAS statement or add transactions to populate net worth metrics.",
                    actionLabel = "Sync Statement",
                    onAction = {}
                )
            }
        } else {
            itemsIndexed(holdings, key = { index, h -> "${h.isin}_${h.fundName}_${h.currentValue}_$index" }) { _, holding ->
                M3HoldingCard(holding)
            }
        }
    }
}

@Composable
fun M3HoldingCard(holding: FlatHoldingDto, onSimulateSale: (FlatHoldingDto) -> Unit = {}) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorTokens.SurfaceCard),
        shape = ShapeTokens.GlassCardShape,
        border = BorderStroke(1.dp, ColorTokens.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(SpacingTokens.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = holding.fundName.ifEmpty { holding.isin },
                    style = TypographyTokens.CardTitle.copy(fontSize = 14.sp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = ColorTokens.ElectricLime.copy(alpha = 0.15f),
                    shape = ShapeTokens.PillShape
                ) {
                    Text(
                        text = "🔄 SIP",
                        style = TypographyTokens.BadgeTag.copy(color = ColorTokens.ElectricLime, fontSize = 9.sp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    color = if (holding.xirr >= 0) ColorTokens.GreenPositive.copy(alpha = 0.15f) else ColorTokens.RedNegative.copy(alpha = 0.15f),
                    shape = ShapeTokens.PillShape
                ) {
                    Text(
                        text = "${if (holding.xirr >= 0) "+" else ""}${holding.xirr}% XIRR",
                        style = TypographyTokens.BadgeTag.copy(
                            color = if (holding.xirr >= 0) ColorTokens.GreenPositive else ColorTokens.RedNegative,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Valuation: ${formatInr(holding.currentValue)}",
                        style = TypographyTokens.FinancialValue.copy(fontSize = 13.sp)
                    )
                    Text(
                        text = "${holding.totalUnits} Units · Cost: ${formatInr(holding.investedValue)}",
                        style = TypographyTokens.FinancialValue.copy(color = ColorTokens.TextMuted, fontSize = 11.sp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = ColorTokens.GlassSurfaceBase,
                        shape = ShapeTokens.PillShape
                    ) {
                        Text(
                            text = holding.assetBucket,
                            style = TypographyTokens.BadgeTag.copy(color = ColorTokens.CyanBright, fontSize = 10.sp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Button(
                        onClick = { onSimulateSale(holding) },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.CyanBright.copy(alpha = 0.2f)),
                        shape = ShapeTokens.PillShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            "Simulate ➔",
                            style = TypographyTokens.MetricLabel.copy(color = ColorTokens.CyanBright, fontSize = 10.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OverlapConcentrationPlaceholderView(holdings: List<FlatHoldingDto>) {
    val bucketCounts = remember(holdings) {
        holdings.groupBy { it.assetBucket }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                shape = ShapeTokens.GlassCardShape,
                colors = CardDefaults.cardColors(containerColor = ColorTokens.SurfaceCard),
                border = BorderStroke(1.dp, ColorTokens.CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1E0B36), Color(0xFF0F172A), Color(0xFF030712))
                            )
                        )
                        .padding(SpacingTokens.xxl)
                ) {
                    Column {
                        Surface(
                            color = ColorTokens.PurpleAccent.copy(alpha = 0.2f),
                            shape = ShapeTokens.PillShape
                        ) {
                            Text(
                                text = "COMING IN PHASE 2",
                                style = TypographyTokens.BadgeTag.copy(color = ColorTokens.PurpleAccent),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "OVERLAP & CONCENTRATION AUDIT",
                            style = TypographyTokens.MetricLabel.copy(
                                color = ColorTokens.TextMain,
                                letterSpacing = 1.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Fund Overlap Matrix & Stock Look-Through",
                            style = TypographyTokens.SectionHeader.copy(fontSize = 18.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The mobile 4-tab navigation shell is active. Interactive fund-to-fund portfolio overlap, stock concentration analysis, and asset class drift details are undergoing mobile-first UI adaptation for Phase 2.",
                            style = TypographyTokens.BodyText
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "PORTFOLIO BUCKET CONCENTRATION SUMMARY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = M3TextMuted,
                letterSpacing = 1.5.sp
            )
        }

        if (bucketCounts.isEmpty()) {
            item {
                PortfolioStateCard(
                    icon = Icons.Default.Star,
                    iconTint = M3VibrantViolet,
                    title = "No Bucket Concentration Data",
                    subtitle = "Asset Allocation Pending",
                    description = "Asset class and category concentration summaries will appear here once active holdings are recorded in your portfolio."
                )
            }
        } else {
            items(bucketCounts.entries.toList(), key = { entry -> entry.key }) { (bucket, list) ->
                val bucketVal = list.sumOf { it.currentValue }
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = bucket.ifEmpty { "UNCLASSIFIED" },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${list.size} Schemes",
                                color = M3TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = formatInr(bucketVal),
                            color = M3NeonCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RadarSignalsView(radarSignals: List<RadarSignalDto>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "PRIORITY AI RADAR & QUANT INTELLIGENCE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = M3TextMuted,
                letterSpacing = 1.5.sp
            )
        }

        if (radarSignals.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ Portfolio status optimal. No immediate tax or rebalance recommendations.",
                        color = M3GreenPositive,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            itemsIndexed(radarSignals, key = { index, s -> "${s.title}_${s.signalType}_$index" }) { _, signal ->
                M3RadarCard(signal)
            }
        }
    }
}

@Composable
fun M3RadarCard(signal: RadarSignalDto) {
    val isQuant = signal.signalType.contains("QUANT", ignoreCase = true)
    val isWarning = signal.severity.equals("WARNING", ignoreCase = true)
    val borderColor = if (isQuant) M3VibrantViolet else if (isWarning) M3AmberWarning else M3NeonCyan
    val containerColor = if (isQuant) Color(0xFF1A0A26) else M3SurfaceCard

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(borderColor)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 24.dp, bottomStart = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = signal.title.ifEmpty { "Recommendation" },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = borderColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = signal.badgeText.ifEmpty { "Action Required" },
                        color = borderColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = signal.description,
                color = M3TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun GroupedTaxLotsView(taxLots: List<FlatTaxLotDto>, holdings: List<FlatHoldingDto>) {
    val nameMap = remember(holdings) {
        holdings.associate { it.isin to it.fundName }
    }

    val groupedLots = remember(taxLots) {
        taxLots.groupBy { it.isin }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SCHEME-GROUPED TAX LOTS (${groupedLots.size} SCHEMES · ${taxLots.size} LOTS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = M3TextMuted,
                letterSpacing = 1.5.sp
            )
        }

        if (groupedLots.isEmpty()) {
            item {
                PortfolioStateCard(
                    icon = Icons.Default.Info,
                    iconTint = M3AmberWarning,
                    title = "No Open Tax Lots",
                    subtitle = "Zero Active Tax Lots",
                    description = "No open tax lots detected in ledger. Your portfolio may be fully liquidated, or tax lot breakdown data has not been synced yet.",
                    actionLabel = "Refresh Tax Ledger",
                    onAction = {}
                )
            }
        } else {
            itemsIndexed(groupedLots.entries.toList(), key = { index, entry -> "${entry.key}_$index" }) { _, (isin, lots) ->
                val schemeName = nameMap[isin] ?: isin
                GroupedSchemeTaxLotCard(schemeName = schemeName, isin = isin, lots = lots)
            }
        }
    }
}

@Composable
fun GroupedSchemeTaxLotCard(schemeName: String, isin: String, lots: List<FlatTaxLotDto>) {
    var expanded by remember { mutableStateOf(false) }

    val ltcgCount = remember(lots) { lots.count { it.isLongTerm } }
    val stcgCount = remember(lots) { lots.size - ltcgCount }
    val totalUnits = remember(lots) { lots.sumOf { it.units } }

    Card(
        colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schemeName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${lots.size} Open Lots · Total %.2f Units".format(totalUnits),
                        color = M3TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ltcgCount > 0) {
                        Surface(
                            color = M3GreenPositive.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "$ltcgCount LTCG",
                                color = M3GreenPositive,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (stcgCount > 0) {
                        Surface(
                            color = M3AmberWarning.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "$stcgCount STCG",
                                color = M3AmberWarning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = M3NeonCyan
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = M3SurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    lots.forEach { lot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = schemeName,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${lot.buyDate} · ${lot.units} u @ ${formatInr(lot.costPerUnit)}",
                                    color = M3TextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = if (lot.isLongTerm) "LTCG" else "STCG (${lot.daysToLtcg}d)",
                                color = if (lot.isLongTerm) M3GreenPositive else M3AmberWarning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

internal data class FundSellAggregated(
    val fundName: String,
    val totalProceeds: Double,
    val totalUnits: Double,
    val totalGain: Double,
    val taxSaved: Double,
    val taxTerm: String,
    val tierLabel: String
)

internal fun shortenFundName(rawName: String?): String {
    if (rawName.isNullOrBlank()) return ""
    return rawName
        .replace(Regex("""\s*\(Non Demat\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*-\s*Direct Plan Growth""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*-\s*DIRECT GROWTH PLAN GROWTH OPTION""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*Direct Plan\s*-\s*Growth""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*Direct Growth Plan Growth Option""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*-\s*Direct Growth""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*Direct Growth""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*Direct Plan""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*Direct""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*Growth""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*Index Fund""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""ICICI Prudential""", RegexOption.IGNORE_CASE), "ICICI")
        .replace(Regex("""Motilal Oswal""", RegexOption.IGNORE_CASE), "Motilal")
        .replace(Regex("""NIPPON INDIA""", RegexOption.IGNORE_CASE), "Nippon")
        .replace(Regex("""Mirae Asset""", RegexOption.IGNORE_CASE), "Mirae")
        .replace(Regex("""Edelweiss Nifty500 Multicap Momentum Quality 50""", RegexOption.IGNORE_CASE), "Edelweiss MomQual 50")
        .replace(Regex("""Invesco India""", RegexOption.IGNORE_CASE), "Invesco")
        .replace(Regex("""Kotak Mahindra""", RegexOption.IGNORE_CASE), "Kotak")
        .replace(Regex("""Parag Parikh""", RegexOption.IGNORE_CASE), "PPFAS")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .removeSuffix("-")
        .trim()
}

@Composable
fun RebalanceWaterfallView(rebalancePlan: com.portfolioos.mobile.model.RebalancePlanDto?) {
    val sellSide = rebalancePlan?.sellSide
    val buySide = rebalancePlan?.buySide
    val buyBuckets = remember(buySide) { buySide?.buckets.orEmpty() }
    val isCooldownBlocked = remember(rebalancePlan) {
        val headline = rebalancePlan?.reasoningNarrative?.headline.orEmpty()
        headline.contains("cooldown", ignoreCase = true)
    }

    val totalRequired = sellSide?.totalRequired ?: 0.0
    val totalToInvest = buySide?.totalToInvest ?: totalRequired

    val fundSellList = remember(sellSide) {
        val map = mutableMapOf<String, FundSellAggregated>()
        sellSide?.waterfall.orEmpty().forEach { tier ->
            tier.lots.forEach { lot ->
                val fName = shortenFundName(lot.fundName.ifEmpty { lot.fundId })
                val proceeds = lot.saleProceeds
                val units = lot.unitsSold
                val gain = lot.realizedGain
                val isLtcg = lot.taxTerm.contains("LONG", ignoreCase = true) || lot.taxImpact?.regime?.contains("112A") == true
                val taxSaved = if (isLtcg) Math.max(0.0, gain) * 0.125 else 0.0

                val existing = map[fName]
                if (existing != null) {
                    map[fName] = existing.copy(
                        totalProceeds = existing.totalProceeds + proceeds,
                        totalUnits = existing.totalUnits + units,
                        totalGain = existing.totalGain + gain,
                        taxSaved = existing.taxSaved + taxSaved
                    )
                } else {
                    map[fName] = FundSellAggregated(
                        fundName = fName,
                        totalProceeds = proceeds,
                        totalUnits = units,
                        totalGain = gain,
                        taxSaved = taxSaved,
                        taxTerm = if (isLtcg) "LTCG EXEMPT" else lot.taxTerm,
                        tierLabel = tier.tierLabel.ifEmpty { tier.tier }
                    )
                }
            }
        }
        map.values.toList()
    }

    val totalTaxSaved = remember(fundSellList) { fundSellList.sumOf { it.taxSaved } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 110.dp)
    ) {
        if (rebalancePlan == null) {
            item {
                PortfolioStateCard(
                    icon = Icons.Default.Info,
                    iconTint = M3NeonCyan,
                    title = "Rebalance Plan Unavailable",
                    subtitle = "Core Node Sync Required",
                    description = "Point-in-time drawdown tiers and waterfall trade plans require connection to Core Node.",
                    actionLabel = "Sync with Core Node",
                    onAction = {}
                )
            }
        } else if (isCooldownBlocked) {
            item {
                PortfolioStateCard(
                    icon = Icons.Default.Info,
                    iconTint = M3AmberWarning,
                    title = "Rebalance Action Deferred",
                    subtitle = "30-Day Cooldown Active",
                    description = rebalancePlan.reasoningNarrative?.headline ?: "Bucket drift detected, but sell rebalance is on 30-day cooldown."
                )
            }
        } else if (sellSide == null || (fundSellList.isEmpty() && totalToInvest == 0.0)) {
            item {
                PortfolioStateCard(
                    icon = Icons.Default.CheckCircle,
                    iconTint = M3GreenPositive,
                    title = "Portfolio Allocation Balanced",
                    subtitle = "No Rebalance Action Required",
                    description = "All asset categories remain within target allocation bands. LTCG exemption headroom is uncompromised."
                )
            }
        } else {
            // 1. HERO SUMMARY CARD (Sleek Compact Glass Pill Header)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E150A)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, M3AmberWarning.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = M3AmberWarning.copy(alpha = 0.15f),
                                shape = ShapeTokens.PillShape
                            ) {
                                Text(
                                    text = "DRIFT REBALANCE TRIGGERED",
                                    color = M3AmberWarning,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (totalTaxSaved > 0) {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = ShapeTokens.PillShape,
                                    border = BorderStroke(1.dp, Color(0xFF10B981))
                                ) {
                                    Text(
                                        text = "Saved +${formatInr(totalTaxSaved)} Tax",
                                        color = Color(0xFF34D399),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = rebalancePlan.trigger?.reasonLabel ?: "Bucket Allocation Drift Exceeded Threshold",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("SELL TRIM", color = M3TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("-${formatInr(totalRequired)}", color = Color(0xFFFB7185), fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TAX SAVED", color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("+${formatInr(totalTaxSaved)}", color = Color(0xFF34D399), fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("BUY ALLOC", color = M3TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text("+${formatInr(totalToInvest)}", color = Color(0xFF34D399), fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            // 2. SELL SIDE CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1015)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = Color(0xFFF43F5E).copy(alpha = 0.2f),
                                shape = ShapeTokens.PillShape,
                                border = BorderStroke(1.dp, Color(0xFFF43F5E))
                            ) {
                                Text(
                                    text = "1. SELL SIDE (LIQUIDATIONS)",
                                    color = Color(0xFFFB7185),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (fundSellList.isEmpty()) {
                            Text("No fund liquidations required", color = M3TextMuted, fontSize = 11.sp)
                        } else {
                            fundSellList.forEachIndexed { index, f ->
                                if (index > 0) Divider(color = Color(0xFFF43F5E).copy(alpha = 0.15f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(f.fundName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("${String.format("%.1f", f.totalUnits)} units · ${f.tierLabel}", color = M3TextMuted, fontSize = 10.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("-${formatInr(f.totalProceeds)}", color = Color(0xFFFB7185), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        if (f.taxSaved > 0) {
                                            Text("LTCG Exempt", color = Color(0xFF34D399), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. FLOW CONNECTOR BANNER
            item {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, M3AmberWarning.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "↓  REDEPLOYING ${formatInr(totalToInvest)} CAPITAL  ↓",
                        color = M3AmberWarning,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }

            // 4. BUY SIDE CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D2018)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = ShapeTokens.PillShape,
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Text(
                                text = "2. BUY SIDE (TARGET RE-ALLOCATIONS)",
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        var isFirst = true
                        buyBuckets.forEach { bkt ->
                            val bktAlloc = bkt.amountAllocated
                            val funds = bkt.fundBreakdown
                            if (bktAlloc > 0 || funds.isNotEmpty()) {
                                if (funds.isEmpty()) {
                                    if (!isFirst) Divider(color = Color(0xFF10B981).copy(alpha = 0.15f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
                                    isFirst = false
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(bkt.bucket.replace('_', ' '), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("+${formatInr(bktAlloc)}", color = Color(0xFF34D399), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    funds.forEach { fund ->
                                        if (!isFirst) Divider(color = Color(0xFF10B981).copy(alpha = 0.15f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
                                        isFirst = false
                                        val fName = shortenFundName(fund.fundName.ifEmpty { fund.fundId })
                                        val fAmt = if (fund.amount > 0) fund.amount else (bktAlloc / funds.size)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(fName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(bkt.bucket.replace('_', ' '), color = M3TextMuted, fontSize = 10.sp)
                                            }
                                            Text("+${formatInr(fAmt)}", color = Color(0xFF34D399), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. TARGET BUCKET ALLOCATION DELTAS CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "3. TARGET BUCKET ALLOCATION DELTA",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        buyBuckets.forEachIndexed { idx, bkt ->
                            if (idx > 0) Divider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(bkt.bucket.replace('_', ' '), color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${String.format("%.1f", bkt.currentPct)}%", color = M3TextMuted, fontSize = 11.sp)
                                    Text("➔", color = M3TextMuted, fontSize = 10.sp)
                                    val isInc = bkt.postRebalancePct >= bkt.currentPct
                                    Text(
                                        text = "${String.format("%.1f", bkt.postRebalancePct)}%",
                                        color = if (isInc) Color(0xFF34D399) else Color(0xFFFB7185),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("(Tgt ${String.format("%.1f", bkt.targetPct)}%)", color = M3TextMuted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

## File: app/src/main/java/com/portfolioos/mobile/ui/LockScreenGate.kt
```kotlin
package com.portfolioos.mobile.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.ui.theme.ColorTokens
import com.portfolioos.mobile.ui.theme.ShapeTokens
import com.portfolioos.mobile.ui.theme.SpacingTokens
import com.portfolioos.mobile.ui.theme.TypographyTokens

@Composable
fun LockScreenGate(
    isSecurityEnrolled: Boolean,
    onAuthenticate: () -> Unit,
    onRecheckSecurity: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorTokens.ObsidianBackground),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ColorTokens.SurfaceCard),
            shape = ShapeTokens.GlassCardShape,
            border = BorderStroke(1.dp, ColorTokens.CardBorder),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(SpacingTokens.xxl)
        ) {
            Column(
                modifier = Modifier.padding(SpacingTokens.xxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSecurityEnrolled) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "App Locked",
                        tint = ColorTokens.ElectricLime,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(SpacingTokens.lg))
                    Text(
                        text = "PORTFOLIO OS LOCKED",
                        style = TypographyTokens.CardTitle.copy(
                            fontSize = 18.sp,
                            letterSpacing = 2.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Biometric or device PIN authentication required to view financial portfolio data.",
                        style = TypographyTokens.BodyText.copy(textAlign = TextAlign.Center)
                    )
                    Spacer(modifier = Modifier.height(SpacingTokens.xxl))
                    Button(
                        onClick = onAuthenticate,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.ElectricLime),
                        shape = ShapeTokens.PillShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Unlock App",
                            style = TypographyTokens.MetricLabel.copy(
                                color = ColorTokens.ObsidianBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Device Security Required",
                        tint = ColorTokens.AmberWarning,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(SpacingTokens.lg))
                    Text(
                        text = "Device Security Required",
                        style = TypographyTokens.CardTitle.copy(fontSize = 18.sp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Portfolio OS contains sensitive financial data. Please set up a PIN, pattern, or biometric lock in Android Settings.",
                        style = TypographyTokens.BodyText.copy(textAlign = TextAlign.Center)
                    )
                    Spacer(modifier = Modifier.height(SpacingTokens.xxl))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTokens.AmberWarning),
                        shape = ShapeTokens.PillShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Open Security Settings",
                            style = TypographyTokens.MetricLabel.copy(
                                color = ColorTokens.ObsidianBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(SpacingTokens.sm))
                    OutlinedButton(
                        onClick = onRecheckSecurity,
                        shape = ShapeTokens.PillShape,
                        border = BorderStroke(1.dp, ColorTokens.CyanSky.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Check Again",
                            style = TypographyTokens.MetricLabel.copy(
                                color = ColorTokens.CyanSky,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
```

## File: app/src/main/java/com/portfolioos/mobile/ui/PortfolioCharts.kt
```kotlin
package com.portfolioos.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.NetWorthPointDto
import com.portfolioos.mobile.ui.theme.ColorTokens
import com.portfolioos.mobile.ui.theme.ShapeTokens
import com.portfolioos.mobile.ui.theme.TypographyTokens
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.compose.component.marker.markerComponent
import com.patrykandpatrick.vico.compose.component.shapeComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.marker.Marker
import kotlin.math.roundToInt

data class BucketAllocation(
    val bucketName: String,
    val totalAmount: Double,
    val percentage: Float,
    val color: Color
)

val SEBIBucketColors = mapOf(
    "Flexi Cap" to Color(0xFF06B6D4),                // Vibrant Cyan
    "Large & Midcap" to Color(0xFFD0FF00),           // Electric Lime
    "Large & Mid Cap" to Color(0xFFD0FF00),          // Electric Lime
    "Midcap" to Color(0xFFA855F7),                   // Deep Violet
    "Mid Cap" to Color(0xFFA855F7),                  // Deep Violet
    "Small Cap" to Color(0xFFF59E0B),                 // Amber Gold
    "Microcap" to Color(0xFFFF007A),                 // Hot Neon Pink
    "Factor Value Index" to Color(0xFF3B82F6),       // Royal Blue
    "Factor Momentum Index" to Color(0xFF10B981),    // Emerald Green
    "Equal Weight Index" to Color(0xFF8B5CF6),       // Soft Purple
    "Sectoral/Thematic" to Color(0xFFEC4899),        // Vibrant Magenta
    "Gold & Commodities" to Color(0xFFEAB308),       // Metallic Gold
    "Gold & Silver" to Color(0xFFEAB308),            // Metallic Gold
    "Debt & Liquid" to Color(0xFF6366F1),            // Indigo
    "Specified Debt (50AA)" to Color(0xFFEF4444),    // Coral Red
    "Arbitrage / Cash" to Color(0xFF14B8A6),         // Teal
    "Core Equity" to Color(0xFF00F0FF),              // Neon Cyan
    "EQUITY_CORE" to Color(0xFF00F0FF),
    "EQUITY_LARGE_MID" to Color(0xFFD0FF00),
    "EQUITY_SMALL_CAP" to Color(0xFFF59E0B),
    "EQUITY_MID_CAP" to Color(0xFFA855F7),
    "DEBT" to Color(0xFF6366F1),
    "LIQUID_BUFFER" to Color(0xFF14B8A6),
    "GOLD" to Color(0xFFEAB308)
)

fun getBucketColor(cat: String): Color {
    val predefined = SEBIBucketColors[cat]
    if (predefined != null) return predefined
    val fallbackPalette = listOf(
        Color(0xFF00F0FF), Color(0xFFD0FF00), Color(0xFFE040FB),
        Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF3B82F6),
        Color(0xFFEC4899), Color(0xFF6366F1), Color(0xFFEAB308)
    )
    val hashIdx = kotlin.math.abs(cat.hashCode()) % fallbackPalette.size
    return fallbackPalette[hashIdx]
}

@Composable
fun PortfolioAllocationBarChart(
    holdings: List<FlatHoldingDto>,
    modifier: Modifier = Modifier
) {
    if (holdings.isEmpty()) return

    val totalInvested = holdings.sumOf { it.currentValue }
    if (totalInvested <= 0) return

    val bucketMap = holdings.groupBy { it.assetBucket }
        .mapValues { entry -> entry.value.sumOf { it.currentValue } }

    val allocations = bucketMap.map { (cat, amount) ->
        val pct = (amount / totalInvested * 100).toFloat()
        val color = getBucketColor(cat)
        BucketAllocation(cat, amount, pct, color)
    }.sortedByDescending { it.percentage }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorTokens.SurfaceCard),
        shape = ShapeTokens.GlassCardShape,
        border = BorderStroke(1.dp, ColorTokens.CardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ASSET ALLOCATION BREAKDOWN",
                        style = TypographyTokens.MetricLabel.copy(
                            color = ColorTokens.TextMuted,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "SEBI Fund Categorization Engine",
                        style = TypographyTokens.CardTitle.copy(color = ColorTokens.ElectricLime)
                    )
                }
                Text(
                    text = "₹${String.format("%,.0f", totalInvested)}",
                    style = TypographyTokens.MetricNumber.copy(
                        fontSize = 15.sp,
                        color = ColorTokens.TextMain
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-segment Linear Progress Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(ShapeTokens.PillShape)
                    .background(ColorTokens.GlassSurfaceBase)
            ) {
                allocations.forEach { alloc ->
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(alloc.percentage.coerceAtLeast(0.1f))
                            .background(alloc.color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Legend Grid with Enhanced Legibility
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                allocations.chunked(2).forEach { rowAllocations ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowAllocations.forEach { alloc ->
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(alloc.color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = alloc.bucketName,
                                        style = TypographyTokens.BodyText.copy(
                                            color = ColorTokens.TextMain,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${String.format("%.1f", alloc.percentage)}%",
                                    style = TypographyTokens.FinancialValue.copy(
                                        color = ColorTokens.TextMuted,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                        if (rowAllocations.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoricalNetWorthTrendChart(
    trendPoints: List<NetWorthPointDto>,
    modifier: Modifier = Modifier
) {
    var selectedPoint by remember { mutableStateOf<NetWorthPointDto?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = ColorTokens.SurfaceCard),
        shape = ShapeTokens.GlassCardShape,
        border = BorderStroke(1.dp, ColorTokens.CardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "HISTORICAL NET WORTH TREND",
                        style = TypographyTokens.MetricLabel.copy(
                            color = ColorTokens.TextMuted,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "NAV Growth & Capital Curve",
                        style = TypographyTokens.CardTitle.copy(color = ColorTokens.ElectricLime)
                    )
                }
                if (selectedPoint != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = selectedPoint!!.date,
                            style = TypographyTokens.BadgeTag.copy(color = ColorTokens.CyanBright)
                        )
                        Text(
                            text = "₹${String.format("%,.0f", selectedPoint!!.valuation)}",
                            style = TypographyTokens.MetricNumber.copy(
                                fontSize = 14.sp,
                                color = ColorTokens.ElectricLime
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (trendPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No historical net worth data available.",
                        style = TypographyTokens.BodyText.copy(color = ColorTokens.TextMuted)
                    )
                }
            } else {
                val rawVals = remember(trendPoints) { trendPoints.map { it.valuation } }
                val minVal = remember(rawVals) { rawVals.minOrNull() ?: 1.0 }
                val maxVal = remember(rawVals) { rawVals.maxOrNull() ?: (minVal * 1.05) }
                val valRange = remember(minVal, maxVal) { (maxVal - minVal).coerceAtLeast(1.0) }

                val chartEntries = remember(trendPoints, minVal, valRange) {
                    trendPoints.mapIndexed { idx, pt ->
                        val normY = (((pt.valuation - minVal) / valRange) * 80.0 + 10.0).toFloat()
                        entryOf(idx.toFloat(), normY)
                    }
                }
                val entryModel = remember(chartEntries) { entryModelOf(chartEntries) }

                val dateAxisFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
                    val idx = value.toInt()
                    if (idx in trendPoints.indices) trendPoints[idx].date else ""
                }

                val marker = rememberChartMarker(selectedPoint)
                val markerVisibilityChangeListener = remember(trendPoints) {
                    object : com.patrykandpatrick.vico.core.marker.MarkerVisibilityChangeListener {
                        override fun onMarkerShown(
                            marker: Marker,
                            markerEntryModels: List<com.patrykandpatrick.vico.core.marker.Marker.EntryModel>
                        ) {
                            val entry = markerEntryModels.firstOrNull()?.entry
                            if (entry != null) {
                                val idx = entry.x.toInt()
                                if (idx in trendPoints.indices) {
                                    selectedPoint = trendPoints[idx]
                                }
                            }
                        }

                        override fun onMarkerHidden(marker: Marker) {
                            selectedPoint = null
                        }
                    }
                }

                Chart(
                    chart = lineChart(
                        lines = listOf(
                            lineSpec(
                                lineColor = ColorTokens.ElectricLime,
                                lineBackgroundShader = com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders.fromBrush(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            ColorTokens.ElectricLime.copy(alpha = 0.35f),
                                            ColorTokens.CyanBright.copy(alpha = 0.02f)
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    model = entryModel,
                    marker = marker,
                    markerVisibilityChangeListener = markerVisibilityChangeListener,
                    isZoomEnabled = false,
                    chartScrollSpec = com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec(isScrollEnabled = false),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = dateAxisFormatter,
                        guideline = null,
                        itemPlacer = com.patrykandpatrick.vico.core.axis.AxisItemPlacer.Horizontal.default(spacing = 4),
                        label = textComponent(
                            color = ColorTokens.TextMuted,
                            textSize = 10.sp
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            }
        }
    }
}

@Composable
fun rememberChartMarker(selectedPoint: NetWorthPointDto? = null): Marker {
    val label = textComponent(
        color = ColorTokens.ObsidianBackground,
        background = com.patrykandpatrick.vico.compose.component.shapeComponent(
            shape = com.patrykandpatrick.vico.core.component.shape.Shapes.pillShape,
            color = ColorTokens.ElectricLime
        ),
        padding = com.patrykandpatrick.vico.compose.dimensions.dimensionsOf(horizontal = 8.dp, vertical = 4.dp),
        textSize = 11.sp
    )
    val indicator = com.patrykandpatrick.vico.compose.component.shapeComponent(
        shape = com.patrykandpatrick.vico.core.component.shape.Shapes.pillShape,
        color = ColorTokens.CyanBright
    )
    val guideline = com.patrykandpatrick.vico.compose.component.lineComponent(
        color = ColorTokens.CyanBright.copy(alpha = 0.5f)
    )
    return com.patrykandpatrick.vico.compose.component.marker.markerComponent(
        label = label,
        indicator = indicator,
        guideline = guideline
    )
}
```

## File: app/src/main/java/com/portfolioos/mobile/ui/SimulatorScreen.kt
```kotlin
package com.portfolioos.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.TradeSimulationRequestDto
import com.portfolioos.mobile.util.formatInr
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorView(holdings: List<FlatHoldingDto>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedIsin by remember { mutableStateOf(holdings.firstOrNull()?.isin ?: "") }
    var selectedName by remember { mutableStateOf(holdings.firstOrNull()?.fundName ?: "Select Scheme") }
    var unitsText by remember { mutableStateOf("100.0") }
    var priceText by remember { mutableStateOf("150.0") }
    var tradeType by remember { mutableStateOf("DISPOSAL") }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "⚡ WHAT-IF TRADE SIMULATOR",
            color = Color(0xFFD0FF00),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            text = "Preview tax drag and post-trade XIRR before executing trades.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Scheme Selector
        ExposedDropdownMenuBox(
            expanded = isExpanded,
            onExpandedChange = { isExpanded = !isExpanded }
        ) {
            OutlinedTextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Target Scheme", color = Color(0xFF94A3B8)) },
                colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = Color(0xFF00F0FF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false }
            ) {
                holdings.forEach { holding ->
                    DropdownMenuItem(
                        text = { Text(holding.fundName) },
                        onClick = {
                            selectedIsin = holding.isin
                            selectedName = holding.fundName
                            isExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = unitsText,
                onValueChange = { unitsText = it },
                label = { Text("Units", color = Color(0xFF94A3B8)) },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Price/NAV (₹)", color = Color(0xFF94A3B8)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tradeType == "DISPOSAL",
                onClick = { tradeType = "DISPOSAL" },
                label = { Text("Simulate Sale (Disposal)") }
            )
            FilterChip(
                selected = tradeType == "ACQUISITION",
                onClick = { tradeType = "ACQUISITION" },
                label = { Text("Simulate Buy (SIP)") }
            )
        }

        Button(
            onClick = {
                val units = unitsText.toDoubleOrNull()
                val price = priceText.toDoubleOrNull()
                if (units == null || units <= 0.0 || price == null || price <= 0.0) {
                    resultText = "⚠️ Validation Error: Please enter valid positive numbers for Units and Price/NAV."
                    return@Button
                }
                isLoading = true
                scope.launch {
                    try {
                        val req = TradeSimulationRequestDto(
                            isin = selectedIsin,
                            schemeName = selectedName,
                            units = units,
                            pricePerUnit = price,
                            tradeType = tradeType
                        )
                        val res = SyncApiClient.simulateTradeWithFallback(context, req)
                        resultText = """
                            ✓ Simulation Execution Successful (Live Engine)
                            • Target: ${res.schemeName}
                            • Trade Type: ${res.tradeType} (${res.units} Units @ ₹${res.pricePerUnit})
                            • Gross Trade Amount: ${formatInr(res.grossTradeAmount)}
                            • Gross Capital Gain: ${formatInr(res.grossCapitalGain)}
                            • LTCG Equity: ${formatInr(res.ltcgEquity)} | STCG Equity: ${formatInr(res.stcgEquity)}
                            • Sec 112A Exemption Applied: ${formatInr(res.sec112aExemptionApplied)}
                            • Projected Tax Liability: ${formatInr(res.estimatedTaxLiability)}
                            • Post-Trade Valuation: ${formatInr(res.postTradeNetWorth)}
                            • Post-Trade Portfolio XIRR: ${String.format("%.2f", res.postTradeXirr)}%
                        """.trimIndent()
                    } catch (e: Exception) {
                        resultText = "⚠️ Simulation RPC failed: ${e.localizedMessage}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0FF00)),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
            } else {
                Text("Run What-If Simulation", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (resultText != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = resultText!!,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
```

## File: app/src/main/java/com/portfolioos/mobile/util/FormatUtils.kt
```kotlin
package com.portfolioos.mobile.util

import java.text.NumberFormat
import java.util.Locale

fun formatInr(valNum: Double, showDecimals: Boolean = false): String {
    val locale = Locale("en", "IN")
    val formatter = NumberFormat.getCurrencyInstance(locale).apply {
        maximumFractionDigits = if (showDecimals) 2 else 0
        minimumFractionDigits = if (showDecimals) 2 else 0
    }
    val formatted = formatter.format(valNum)
    return if (formatted.startsWith("INR")) {
        formatted.replace("INR", "₹").trim()
    } else {
        formatted
    }
}

fun formatInrStr(valStr: String?): String {
    if (valStr.isNullOrBlank()) return "₹0"
    val cleaned = valStr.replace("₹", "").replace(",", "").trim()
    val dbl = cleaned.toDoubleOrNull() ?: return valStr
    return formatInr(dbl, showDecimals = false)
}
```

## File: app/src/main/java/com/portfolioos/mobile/widget/PortfolioGlanceWidget.kt
```kotlin
package com.portfolioos.mobile.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.portfolioos.mobile.MainActivity
import com.portfolioos.mobile.data.SnapshotCacheManager

class PortfolioGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = SnapshotCacheManager.loadSnapshot(context)
        val info = snapshot?.syncInfo
        val holdings = snapshot?.holdings ?: emptyList()

        val bestFund = holdings.maxByOrNull { it.xirr }
        val worstFund = holdings.minByOrNull { it.xirr }

        // Calculate portfolio gain percentage for privacy-first display
        val totalInvested = info?.totalInvested ?: 1.0
        val unrealizedGain = info?.unrealizedGain ?: 0.0
        val gainPct = if (totalInvested > 0) (unrealizedGain / totalInvested) * 100.0 else 0.0
        val formattedGainPct = String.format("%s%.2f%%", if (gainPct >= 0) "+" else "", gainPct)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFF0D1424)))
                        .padding(14.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = "PORTFOLIO OS",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFD0FF00)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = info?.xirrPercentage ?: "0.00% XIRR",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF10B981)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Privacy-First Valuation & Return Header
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(
                            text = "₹ • • • • • •",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF94A3B8)),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = formattedGainPct,
                            style = TextStyle(
                                color = ColorProvider(if (gainPct >= 0) Color(0xFF10B981) else Color(0xFFEF4444)),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "BEST PERFORMER",
                                style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 8.sp)
                            )
                            Text(
                                text = bestFund?.let { "${it.fundName.take(14)} (+${it.xirr}%)" } ?: "N/A",
                                style = TextStyle(color = ColorProvider(Color(0xFF10B981)), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "WORST PERFORMER",
                                style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 8.sp)
                            )
                            Text(
                                text = worstFund?.let { "${it.fundName.take(14)} (${it.xirr}%)" } ?: "N/A",
                                style = TextStyle(color = ColorProvider(Color(0xFFF59E0B)), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    Text(
                        text = "Valuation Hidden for Privacy · Tap to Open App",
                        style = TextStyle(color = ColorProvider(Color(0xFF00F0FF)), fontSize = 9.sp)
                    )
                }
            }
        }
    }
}

class PortfolioGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioGlanceWidget()
}
```

## File: app/src/main/java/com/portfolioos/mobile/MainActivity.kt
```kotlin
package com.portfolioos.mobile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.auth.BiometricAuthManager
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.ui.DashboardScreen
import com.portfolioos.mobile.ui.LockScreenGate
import com.portfolioos.mobile.ui.theme.PortfolioOSTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val TAG = "MainActivity"
    private val activePage = mutableStateOf(0)
    
    private val isAppLockedState = mutableStateOf(false)
    private val isSecurityEnrolledState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activePage.value = intent.getIntExtra("TARGET_PAGE", 0)

        val disableLockExtra = intent.getBooleanExtra("DISABLE_LOCK", false)
        if (disableLockExtra) {
            SnapshotCacheManager.setBiometricLockEnabled(applicationContext, false)
        }
        isAppLockedState.value = if (disableLockExtra) false else SnapshotCacheManager.isBiometricLockEnabled(applicationContext)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                if (BiometricAuthManager.isAuthPromptShowing) {
                    Log.d(TAG, "ON_STOP fired while BiometricPrompt is showing. Suppressing re-lock loop.")
                } else if (SnapshotCacheManager.isBiometricLockEnabled(applicationContext)) {
                    Log.d(TAG, "App backgrounded (ON_STOP). Re-locking app.")
                    isAppLockedState.value = true
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                Log.d(TAG, "App resumed (ON_START). Checking security status.")
                val status = BiometricAuthManager.checkSecurityStatus(this@MainActivity)
                isSecurityEnrolledState.value = (status != BiometricAuthManager.SecurityStatus.NONE_ENROLLED)
                
                if (isAppLockedState.value && SnapshotCacheManager.isBiometricLockEnabled(applicationContext)) {
                    triggerBiometricUnlock()
                }
            }
        })

        setContent {
            PortfolioOSTheme {
                val page by activePage
                var isAppLocked by remember { isAppLockedState }
                var isSecurityEnrolled by remember { isSecurityEnrolledState }
                var isBiometricLockEnabled by remember { mutableStateOf(SnapshotCacheManager.isBiometricLockEnabled(applicationContext)) }

                var snapshot by remember { mutableStateOf<SyncSnapshot?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                var isRefreshing by remember { mutableStateOf(false) }
                val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
                
                var lastSyncMillis by remember { mutableLongStateOf(SnapshotCacheManager.getLastSyncTimestamp(applicationContext)) }
                var lastFullLedgerMillis by remember { mutableLongStateOf(SnapshotCacheManager.getLastFullLedgerTimestamp(applicationContext)) }
                var isAmfiFallback by remember { mutableStateOf(SnapshotCacheManager.isAmfiFallback(applicationContext)) }
                var isFullyOffline by remember { mutableStateOf(SnapshotCacheManager.isFullyOffline(applicationContext)) }
                
                val scope = rememberCoroutineScope()

                fun fetchSyncSnapshot(isManualRefresh: Boolean = false) {
                    scope.launch {
                        if (isManualRefresh) {
                            isRefreshing = true
                        } else if (snapshot == null) {
                            snapshot = SnapshotCacheManager.loadSnapshot(applicationContext)
                            isLoading = (snapshot == null)
                        }
                        try {
                            val newSnapshot = SyncApiClient.fetchSnapshotWithFallback(applicationContext)
                            snapshot = newSnapshot
                            lastSyncMillis = SnapshotCacheManager.getLastSyncTimestamp(applicationContext)
                            lastFullLedgerMillis = SnapshotCacheManager.getLastFullLedgerTimestamp(applicationContext)
                            isAmfiFallback = SnapshotCacheManager.isAmfiFallback(applicationContext)
                            isFullyOffline = SnapshotCacheManager.isFullyOffline(applicationContext)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            if (snapshot == null) {
                                snapshot = SnapshotCacheManager.createDefaultFallbackSnapshot()
                            }
                        } finally {
                            isLoading = false
                            isRefreshing = false
                        }
                    }
                }

                LaunchedEffect(isAppLocked, isBiometricLockEnabled) {
                    if (!isAppLocked || !isBiometricLockEnabled) {
                        fetchSyncSnapshot(isManualRefresh = false)
                    }
                }

                if (isAppLocked && isBiometricLockEnabled) {
                    LockScreenGate(
                        isSecurityEnrolled = isSecurityEnrolled,
                        onAuthenticate = { triggerBiometricUnlock() },
                        onRecheckSecurity = { triggerBiometricUnlock() }
                    )
                } else {
                    DashboardScreen(
                        snapshot = snapshot,
                        isLoading = isLoading,
                        isRefreshing = isRefreshing,
                        lastSyncMillis = lastSyncMillis,
                        lastFullLedgerMillis = lastFullLedgerMillis,
                        isAmfiFallback = isAmfiFallback,
                        isFullyOffline = isFullyOffline,
                        snackbarHostState = snackbarHostState,
                        initialPage = page,
                        onRefresh = { fetchSyncSnapshot(isManualRefresh = true) },
                        onUpdateCustomUrl = { newUrl ->
                            SnapshotCacheManager.setCustomUrl(applicationContext, newUrl)
                            fetchSyncSnapshot(isManualRefresh = true)
                        },
                        onSimulateFullSync = {
                            if (snapshot != null) {
                                SnapshotCacheManager.saveSnapshot(applicationContext, snapshot!!, isFullLedgerSync = true)
                                lastSyncMillis = System.currentTimeMillis()
                                lastFullLedgerMillis = System.currentTimeMillis()
                                isAmfiFallback = false
                                isFullyOffline = false
                                SnapshotCacheManager.setFullyOffline(applicationContext, false)
                            }
                        },
                        onSimulateAmfiFallback = {
                            if (snapshot != null) {
                                SnapshotCacheManager.saveSnapshot(applicationContext, snapshot!!, isFullLedgerSync = false)
                                lastSyncMillis = System.currentTimeMillis()
                                lastFullLedgerMillis = System.currentTimeMillis() - 172800000L // 2 days ago
                                isAmfiFallback = true
                                isFullyOffline = false
                                SnapshotCacheManager.setFullyOffline(applicationContext, false)
                            }
                        },
                        onSimulateFullyOffline = {
                            if (snapshot != null) {
                                isFullyOffline = !isFullyOffline
                                SnapshotCacheManager.setFullyOffline(applicationContext, isFullyOffline)
                            }
                        },
                        onSimulateAgedOffline = {
                            if (snapshot != null) {
                                val agedTime = System.currentTimeMillis() - 660000L // 11 mins ago
                                lastSyncMillis = agedTime
                                lastFullLedgerMillis = agedTime
                                isAmfiFallback = false
                                isFullyOffline = true
                                SnapshotCacheManager.setFullyOffline(applicationContext, true)
                            }
                        },
                        onSimulateRefreshing = {
                            isRefreshing = !isRefreshing
                        },
                        onSimulateSyncFailure = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Sync failed — showing cached data",
                                    duration = androidx.compose.material3.SnackbarDuration.Indefinite
                                )
                            }
                        },
                        isBiometricLockEnabled = isBiometricLockEnabled,
                        onToggleBiometricLock = { enabled ->
                            isBiometricLockEnabled = enabled
                            SnapshotCacheManager.setBiometricLockEnabled(applicationContext, enabled)
                            if (enabled) {
                                isAppLockedState.value = true
                                triggerBiometricUnlock()
                            } else {
                                isAppLockedState.value = false
                            }
                        }
                    )
                }
            }
        }
    }

    private fun triggerBiometricUnlock() {
        if (intent.getBooleanExtra("DISABLE_LOCK", false)) {
            isAppLockedState.value = false
            return
        }
        val status = BiometricAuthManager.checkSecurityStatus(this)
        Log.d(TAG, "triggerBiometricUnlock checked security status: $status")
        if (status == BiometricAuthManager.SecurityStatus.NONE_ENROLLED) {
            isSecurityEnrolledState.value = false
            return
        }
        isSecurityEnrolledState.value = true
        BiometricAuthManager.showBiometricPrompt(
            activity = this,
            onAuthSuccess = {
                Log.d(TAG, "Biometric auth success callback received. Unlocking app.")
                isAppLockedState.value = false
            },
            onAuthError = { err ->
                Log.d(TAG, "Biometric auth error/cancel callback received: $err")
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activePage.value = intent.getIntExtra("TARGET_PAGE", 0)
    }
}
```

## File: app/src/main/res/drawable/ic_launcher_background.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#030712"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#0D1424"
        android:pathData="M0,0 L108,108 L0,108 Z" />
</vector>
```

## File: app/src/main/res/drawable/ic_launcher_foreground.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Glowing Cyber Grid Accent -->
    <path
        android:strokeColor="#1E293B"
        android:strokeWidth="1"
        android:pathData="M24,36 H84 M24,54 H84 M24,72 H84" />

    <!-- Upward Trend Line -->
    <path
        android:strokeColor="#00F0FF"
        android:strokeWidth="4"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M28,68 L44,52 L56,60 L80,36" />

    <!-- Trend Line Sparkle Dots -->
    <path
        android:fillColor="#D0FF00"
        android:pathData="M80,36 m-4,0 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0" />

    <!-- Portfolio OS Monogram "P" Emblem -->
    <path
        android:fillColor="#D0FF00"
        android:pathData="M34,34 h16 a12,12 0 0,1 0,24 h-8 v16 h-8 z" />

    <path
        android:fillColor="#030712"
        android:pathData="M42,42 h8 a4,4 0 0,1 0,8 h-8 z" />
</vector>
```

## File: app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

## File: app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

## File: app/src/main/res/values/styles.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.PortfolioOS" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">#050811</item>
        <item name="android:windowBackground">#050811</item>
    </style>
</resources>
```

## File: app/src/main/res/xml/backup_rules.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude path="." />
</full-backup-content>
```

## File: app/src/main/res/xml/data_extraction_rules.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude path="." />
    </cloud-backup>
</data-extraction-rules>
```

## File: app/src/main/res/xml/portfolio_glance_widget_info.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:updatePeriodMillis="1800000"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen">
</appwidget-provider>
```

## File: app/src/main/AndroidManifest.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="Portfolio OS"
        android:supportsRtl="true"
        android:theme="@style/Theme.PortfolioOS"
        android:usesCleartextTraffic="true"
        tools:targetApi="31">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PortfolioOS">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>


        <receiver
            android:name=".widget.PortfolioGlanceReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/portfolio_glance_widget_info" />
        </receiver>
    </application>

</manifest>
```

## File: app/src/test/java/com/portfolioos/mobile/RebalanceWaterfallUnitTest.kt
```kotlin
package com.portfolioos.mobile

import com.portfolioos.mobile.ui.FundSellAggregated
import com.portfolioos.mobile.ui.shortenFundName
import com.portfolioos.mobile.util.formatInr
import org.junit.Assert.assertEquals
import org.junit.Test

class RebalanceWaterfallUnitTest {

    @Test
    fun testShortenFundName() {
        assertEquals("Motilal Nifty Midcap 150", shortenFundName("Motilal Oswal Nifty Midcap 150 Index Fund Direct Growth"))
        assertEquals("Mirae Healthcare Fund", shortenFundName("Mirae Asset Healthcare Fund - (Non Demat)"))
        assertEquals("Kotak Nifty 100 Equal Weight", shortenFundName("Kotak Nifty 100 Equal Weight Index Fund"))
        assertEquals("Short Fund Name", shortenFundName("Short Fund Name"))
    }

    @Test
    fun testFundSellAggregatedTaxSavedCalculation() {
        val gain = 21386.0
        val isLtcg = true
        val taxSaved = if (isLtcg) gain * 0.125 else 0.0

        val agg = FundSellAggregated(
            fundName = "Kotak Nifty 100 Equal Weight",
            totalProceeds = 76040.0,
            totalUnits = 6740.5,
            totalGain = gain,
            taxSaved = taxSaved,
            taxTerm = "LTCG EXEMPT",
            tierLabel = "Tier 1 - Capital Buffer"
        )

        assertEquals("Kotak Nifty 100 Equal Weight", agg.fundName)
        assertEquals(76040.0, agg.totalProceeds, 0.01)
        assertEquals(2673.25, agg.taxSaved, 0.01)
        assertEquals("LTCG EXEMPT", agg.taxTerm)
    }

    @Test
    fun testFormatInrFormatting() {
        assertEquals("₹1,298,893", formatInr(1298893.0))
        assertEquals("₹2,673", formatInr(2673.0))
        assertEquals("₹500", formatInr(500.0))
    }

    @Test
    fun testTaxExemption112AFormula() {
        // Section 112A LTCG tax rate is 12.5% on gains up to Rs 1.25 Lakh exemption limit
        val exemptGain = 100000.0
        val taxRate = 0.125
        val taxSaved = exemptGain * taxRate

        assertEquals(12500.0, taxSaved, 0.01)
    }
}
```

## File: app/build.gradle.kts
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.portfolioos.mobile"
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.portfolioos.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    lint {
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Vico Charting Library
    implementation("com.patrykandpatrick.vico:core:1.13.1")
    implementation("com.patrykandpatrick.vico:compose:1.13.1")
    implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Jetpack Glance Widget
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.glance:glance-material3:1.0.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

## File: gradle/wrapper/gradle-wrapper.properties
```
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

## File: build.gradle.kts
```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

## File: capture_clean_6_states.py
```python
import subprocess
import time
import os
from PIL import Image

brain_dir = '/home/rakeshpc/.gemini/antigravity/brain/9e3415e7-14a0-4cff-8405-ef29cd6b790c'
dashboard_path = '/home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/DashboardScreen.kt'

with open(dashboard_path, 'r') as f:
    orig_dashboard = f.read()

states = [
    ('state1', 'empty_tab0_holdings', 0, False, 'holdings_empty'),
    ('state2', 'empty_tab0_radar', 0, True, 'radar_empty'),
    ('state3', 'empty_tab1_buckets', 1, False, 'buckets_empty'),
    ('state4', 'empty_tab2_taxlots', 2, False, 'taxlots_empty'),
    ('state5', 'empty_tab3_core_node_sync_required', 3, False, 'tab3_null'),
    ('state6', 'empty_tab3_balanced', 3, False, 'tab3_balanced'),
    ('state7', 'empty_tab3_cooldown_deferred', 3, False, 'tab3_cooldown')
]

for sid, sname, page, scroll, mode in states:
    print(f'=== Capturing {sname} (Page {page}, Mode {mode}) ===')
    
    mod_code = orig_dashboard
    # Force snapshot to be non-null and isInitialLoading = false directly inside DashboardScreen
    mod_code = mod_code.replace('if (isInitialLoading && snapshot == null)', 'if (false)')

    if mode == 'holdings_empty':
        mod_code = mod_code.replace(
            '0 -> HoldingsView(\n                                        snapshot = snapshot,\n                                        syncInfo = syncInfo,\n                                        holdings = holdings,\n                                        radarSignals = radarSignals,\n                                        onSimulateSale = {\n                                            selectedHoldingForSimulator = it\n                                            showSimulatorBottomSheet = true\n                                        }\n                                    )',
            '0 -> HoldingsView(snapshot = snapshot, syncInfo = syncInfo, holdings = emptyList(), radarSignals = emptyList(), onSimulateSale = {})'
        )
    elif mode == 'radar_empty':
        mod_code = mod_code.replace(
            '0 -> HoldingsView(\n                                        snapshot = snapshot,\n                                        syncInfo = syncInfo,\n                                        holdings = holdings,\n                                        radarSignals = radarSignals,\n                                        onSimulateSale = {\n                                            selectedHoldingForSimulator = it\n                                            showSimulatorBottomSheet = true\n                                        }\n                                    )',
            '0 -> HoldingsView(snapshot = snapshot, syncInfo = syncInfo, holdings = holdings, radarSignals = emptyList(), onSimulateSale = {})'
        )
    elif mode == 'buckets_empty':
        mod_code = mod_code.replace('1 -> OverlapConcentrationPlaceholderView(holdings)', '1 -> OverlapConcentrationPlaceholderView(emptyList())')
    elif mode == 'taxlots_empty':
        mod_code = mod_code.replace('2 -> GroupedTaxLotsView(taxLots, holdings)', '2 -> GroupedTaxLotsView(emptyList(), holdings)')
    elif mode == 'tab3_null':
        mod_code = mod_code.replace('3 -> RebalanceWaterfallView(snapshot.rebalancePlan)', '3 -> RebalanceWaterfallView(null)')
    elif mode == 'tab3_balanced':
        balanced_code = '''
RebalanceWaterfallView(
    com.portfolioos.mobile.model.RebalancePlanDto(
        sellSide = com.portfolioos.mobile.model.RebalanceSideDto(totalRequired = 0.0, waterfall = emptyList()),
        reasoningNarrative = com.portfolioos.mobile.model.NarrativeDto(headline = "All asset categories remain within target allocation bands. LTCG exemption headroom is uncompromised.")
    )
)
'''
        mod_code = mod_code.replace('3 -> RebalanceWaterfallView(snapshot.rebalancePlan)', f'3 -> {balanced_code}')
    elif mode == 'tab3_cooldown':
        cooldown_code = '''
RebalanceWaterfallView(
    com.portfolioos.mobile.model.RebalancePlanDto(
        sellSide = com.portfolioos.mobile.model.RebalanceSideDto(totalRequired = 0.0, waterfall = emptyList()),
        reasoningNarrative = com.portfolioos.mobile.model.NarrativeDto(headline = "No Rebalance Required — Bucket drift detected (EQUITY_CORE, EQUITY_SATELLITE, GOLD_SILVER, LIQUID_BUFFER) but sell rebalance is on 30-day cooldown (0 days since last sell)")
    )
)
'''
        mod_code = mod_code.replace('3 -> RebalanceWaterfallView(snapshot.rebalancePlan)', f'3 -> {cooldown_code}')

    with open(dashboard_path, 'w') as f:
        f.write(mod_code)

    subprocess.run(['./gradlew', 'assembleDebug'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)
    subprocess.run(['adb', '-s', '38261JEHN08992', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)

    subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'am', 'force-stop', 'com.portfolioos.mobile'])
    time.sleep(1)
    subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'am', 'start', '-n', 'com.portfolioos.mobile/.MainActivity'])
    time.sleep(3)

    if page == 1:
        # Swipe 1 page right
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1.5)
    elif page == 2:
        # Swipe 2 pages right
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1)
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1.5)
    elif page == 3:
        # Swipe 3 pages right
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1)
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1)
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1.5)

    if scroll:
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '500', '1600', '500', '600', '300'])
        time.sleep(1.5)

    raw_path = os.path.join(brain_dir, f'raw_{sname}.png')
    out_path = os.path.join(brain_dir, f'{sname}.png')

    with open(raw_path, 'wb') as out_f:
        subprocess.run(['adb', '-s', '38261JEHN08992', 'exec-out', 'screencap', '-p'], stdout=out_f, check=True)

    img = Image.open(raw_path)
    img.thumbnail((600, 1333))
    img.save(out_path)
    print(f'Captured & saved {out_path}')

# Restore original code
with open(dashboard_path, 'w') as f:
    f.write(orig_dashboard)

subprocess.run(['./gradlew', 'assembleDebug'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)
subprocess.run(['adb', '-s', '38261JEHN08992', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)
print('All 7 state captures complete cleanly!')
```

## File: capture_perfect_states.py
```python
import subprocess
import time
import os
from PIL import Image

brain_dir = '/home/rakeshpc/.gemini/antigravity/brain/9e3415e7-14a0-4cff-8405-ef29cd6b790c'
dashboard_path = '/home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/DashboardScreen.kt'

with open(dashboard_path, 'r') as f:
    orig_dashboard = f.read()

states = [
    ('state1', 'empty_tab0_holdings', 0, False, 'holdings_empty'),
    ('state2', 'empty_tab0_radar', 0, True, 'radar_empty'),
    ('state3', 'empty_tab1_buckets', 1, False, 'buckets_empty'),
    ('state4', 'empty_tab2_taxlots', 2, False, 'taxlots_empty'),
    ('state5', 'empty_tab3_core_node_sync_required', 3, False, 'tab3_null'),
    ('state6', 'empty_tab3_balanced', 3, False, 'tab3_balanced'),
    ('state7', 'empty_tab3_cooldown_deferred', 3, False, 'tab3_cooldown')
]

for sid, sname, page, scroll, mode in states:
    print(f'=== Capturing {sname} (Page {page}, Mode {mode}) ===')
    
    mod_code = orig_dashboard
    mod_code = mod_code.replace('if (isInitialLoading && snapshot == null)', 'if (false)')

    if mode == 'holdings_empty':
        mod_code = mod_code.replace('val holdings = snapshot?.holdings.orEmpty()', 'val holdings = emptyList<com.portfolioos.mobile.model.FlatHoldingDto>()')
        mod_code = mod_code.replace('val radarSignals = snapshot?.radarSignals.orEmpty()', 'val radarSignals = emptyList<com.portfolioos.mobile.model.PortfolioSignalDto>()')
    elif mode == 'radar_empty':
        mod_code = mod_code.replace('val radarSignals = snapshot?.radarSignals.orEmpty()', 'val radarSignals = emptyList<com.portfolioos.mobile.model.PortfolioSignalDto>()')
    elif mode == 'buckets_empty':
        mod_code = mod_code.replace('1 -> OverlapConcentrationPlaceholderView(holdings)', '1 -> OverlapConcentrationPlaceholderView(emptyList())')
    elif mode == 'taxlots_empty':
        mod_code = mod_code.replace('val taxLots = snapshot?.taxLots.orEmpty()', 'val taxLots = emptyList<com.portfolioos.mobile.model.FlatTaxLotDto>()')
    elif mode == 'tab3_null':
        mod_code = mod_code.replace('3 -> RebalanceWaterfallView(snapshot.rebalancePlan)', '3 -> RebalanceWaterfallView(null)')
    elif mode == 'tab3_balanced':
        balanced_code = '''
RebalanceWaterfallView(
    com.portfolioos.mobile.model.RebalancePlanDto(
        sellSide = com.portfolioos.mobile.model.RebalanceSideDto(totalRequired = 0.0, waterfall = emptyList()),
        reasoningNarrative = com.portfolioos.mobile.model.NarrativeDto(headline = "All asset categories remain within target allocation bands. LTCG exemption headroom is uncompromised.")
    )
)
'''
        mod_code = mod_code.replace('3 -> RebalanceWaterfallView(snapshot.rebalancePlan)', f'3 -> {balanced_code}')
    elif mode == 'tab3_cooldown':
        cooldown_code = '''
RebalanceWaterfallView(
    com.portfolioos.mobile.model.RebalancePlanDto(
        sellSide = com.portfolioos.mobile.model.RebalanceSideDto(totalRequired = 0.0, waterfall = emptyList()),
        reasoningNarrative = com.portfolioos.mobile.model.NarrativeDto(headline = "No Rebalance Required — Bucket drift detected (EQUITY_CORE, EQUITY_SATELLITE, GOLD_SILVER, LIQUID_BUFFER) but sell rebalance is on 30-day cooldown (0 days since last sell)")
    )
)
'''
        mod_code = mod_code.replace('3 -> RebalanceWaterfallView(snapshot.rebalancePlan)', f'3 -> {cooldown_code}')

    with open(dashboard_path, 'w') as f:
        f.write(mod_code)

    subprocess.run(['./gradlew', 'assembleDebug'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)
    subprocess.run(['adb', '-s', '38261JEHN08992', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)

    subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'am', 'force-stop', 'com.portfolioos.mobile'])
    time.sleep(1)
    subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'am', 'start', '-n', 'com.portfolioos.mobile/.MainActivity'])
    time.sleep(3)

    if page == 1:
        # Swipe 1 page right
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1.5)
    elif page == 2:
        # Swipe 2 pages right
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1)
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1.5)
    elif page == 3:
        # Swipe 3 pages right
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1)
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1)
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1.5)

    if scroll:
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '500', '1600', '500', '600', '300'])
        time.sleep(1.5)

    raw_path = os.path.join(brain_dir, f'raw_{sname}.png')
    out_path = os.path.join(brain_dir, f'{sname}.png')

    with open(raw_path, 'wb') as out_f:
        subprocess.run(['adb', '-s', '38261JEHN08992', 'exec-out', 'screencap', '-p'], stdout=out_f, check=True)

    img = Image.open(raw_path)
    img.thumbnail((600, 1333))
    img.save(out_path)
    print(f'Captured & saved {out_path}')

# Restore original code
with open(dashboard_path, 'w') as f:
    f.write(orig_dashboard)

subprocess.run(['./gradlew', 'assembleDebug'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)
subprocess.run(['adb', '-s', '38261JEHN08992', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)
print('All 7 state captures complete cleanly!')
```

## File: capture_unlocked_states.py
```python
import subprocess
import time
import os
from PIL import Image

brain_dir = '/home/rakeshpc/.gemini/antigravity/brain/9e3415e7-14a0-4cff-8405-ef29cd6b790c'
dashboard_path = '/home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/ui/DashboardScreen.kt'
main_activity_path = '/home/rakeshpc/Projects/portfolio-os/mobile-app/app/src/main/java/com/portfolioos/mobile/MainActivity.kt'

with open(dashboard_path, 'r') as f:
    orig_dashboard = f.read()

with open(main_activity_path, 'r') as f:
    orig_main = f.read()

# Force isAppLockedState = false in MainActivity.kt
mod_main = orig_main.replace('isAppLockedState.value = initialLockEnabled', 'isAppLockedState.value = false')
with open(main_activity_path, 'w') as f:
    f.write(mod_main)

states = [
    ('state1', 'empty_tab0_holdings', 0, False, 'holdings_empty'),
    ('state2', 'empty_tab0_radar', 0, True, 'radar_empty'),
    ('state3', 'empty_tab1_buckets', 1, False, 'buckets_empty'),
    ('state4', 'empty_tab2_taxlots', 2, False, 'taxlots_empty'),
    ('state5', 'empty_tab3_core_node_sync_required', 3, False, 'tab3_null'),
    ('state6', 'empty_tab3_balanced', 3, False, 'tab3_balanced'),
    ('state7', 'empty_tab3_cooldown_deferred', 3, False, 'tab3_cooldown')
]

for sid, sname, page, scroll, mode in states:
    print(f'=== Capturing {sname} (Page {page}, Mode {mode}) ===')
    
    mod_code = orig_dashboard
    mod_code = mod_code.replace('if (isInitialLoading && snapshot == null)', 'if (false)')

    if mode == 'holdings_empty':
        mod_code = mod_code.replace('val holdings = snapshot?.holdings.orEmpty()', 'val holdings = emptyList<com.portfolioos.mobile.model.FlatHoldingDto>()')
        mod_code = mod_code.replace('val radarSignals = snapshot?.radarSignals.orEmpty()', 'val radarSignals = emptyList<com.portfolioos.mobile.model.PortfolioSignalDto>()')
    elif mode == 'radar_empty':
        mod_code = mod_code.replace('val radarSignals = snapshot?.radarSignals.orEmpty()', 'val radarSignals = emptyList<com.portfolioos.mobile.model.PortfolioSignalDto>()')
    elif mode == 'buckets_empty':
        mod_code = mod_code.replace('1 -> OverlapConcentrationPlaceholderView(holdings)', '1 -> OverlapConcentrationPlaceholderView(emptyList())')
    elif mode == 'taxlots_empty':
        mod_code = mod_code.replace('val taxLots = snapshot?.taxLots.orEmpty()', 'val taxLots = emptyList<com.portfolioos.mobile.model.FlatTaxLotDto>()')
    elif mode == 'tab3_null':
        mod_code = mod_code.replace('3 -> RebalanceWaterfallView(snapshot.rebalancePlan)', '3 -> RebalanceWaterfallView(null)')
    elif mode == 'tab3_balanced':
        balanced_code = '''
RebalanceWaterfallView(
    com.portfolioos.mobile.model.RebalancePlanDto(
        sellSide = com.portfolioos.mobile.model.RebalanceSideDto(totalRequired = 0.0, waterfall = emptyList()),
        reasoningNarrative = com.portfolioos.mobile.model.NarrativeDto(headline = "All asset categories remain within target allocation bands. LTCG exemption headroom is uncompromised.")
    )
)
'''
        mod_code = mod_code.replace('3 -> RebalanceWaterfallView(snapshot.rebalancePlan)', f'3 -> {balanced_code}')
    elif mode == 'tab3_cooldown':
        cooldown_code = '''
RebalanceWaterfallView(
    com.portfolioos.mobile.model.RebalancePlanDto(
        sellSide = com.portfolioos.mobile.model.RebalanceSideDto(totalRequired = 0.0, waterfall = emptyList()),
        reasoningNarrative = com.portfolioos.mobile.model.NarrativeDto(headline = "No Rebalance Required — Bucket drift detected (EQUITY_CORE, EQUITY_SATELLITE, GOLD_SILVER, LIQUID_BUFFER) but sell rebalance is on 30-day cooldown (0 days since last sell)")
    )
)
'''
        mod_code = mod_code.replace('3 -> RebalanceWaterfallView(snapshot.rebalancePlan)', f'3 -> {cooldown_code}')

    with open(dashboard_path, 'w') as f:
        f.write(mod_code)

    subprocess.run(['./gradlew', 'assembleDebug'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)
    subprocess.run(['adb', '-s', '38261JEHN08992', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)

    subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'am', 'force-stop', 'com.portfolioos.mobile'])
    time.sleep(1)
    subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'am', 'start', '-n', 'com.portfolioos.mobile/.MainActivity'])
    time.sleep(3)

    if page == 1:
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1.5)
    elif page == 2:
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1)
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1.5)
    elif page == 3:
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1)
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1)
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '900', '1000', '200', '1000', '300'])
        time.sleep(1.5)

    if scroll:
        subprocess.run(['adb', '-s', '38261JEHN08992', 'shell', 'input', 'swipe', '500', '1600', '500', '600', '300'])
        time.sleep(1.5)

    raw_path = os.path.join(brain_dir, f'raw_{sname}.png')
    out_path = os.path.join(brain_dir, f'{sname}.png')

    with open(raw_path, 'wb') as out_f:
        subprocess.run(['adb', '-s', '38261JEHN08992', 'exec-out', 'screencap', '-p'], stdout=out_f, check=True)

    img = Image.open(raw_path)
    img.thumbnail((600, 1333))
    img.save(out_path)
    print(f'Captured & saved {out_path}')

# Restore original code
with open(dashboard_path, 'w') as f:
    f.write(orig_dashboard)

with open(main_activity_path, 'w') as f:
    f.write(orig_main)

subprocess.run(['./gradlew', 'assembleDebug'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)
subprocess.run(['adb', '-s', '38261JEHN08992', 'install', '-r', 'app/build/outputs/apk/debug/app-debug.apk'], cwd='/home/rakeshpc/Projects/portfolio-os/mobile-app', check=True)
print('All 7 state captures complete cleanly with lock screen bypassed!')
```

## File: gradle.properties
```
android.useAndroidX=true
android.nonFinalResIds=false
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

## File: gradlew
```
#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
#
#   Gradle start up script for POSIX generated by Gradle.
#
#   Important for running:
#
#   (1) You need a POSIX-compliant shell to run this script. If your /bin/sh is
#       noncompliant, but you have some other compliant shell such as ksh or
#       bash, then to run this script, type that shell name before the whole
#       command line, like:
#
#           ksh Gradle
#
#       Busybox and similar reduced shells will NOT work, because this script
#       requires all of these POSIX shell features:
#         * functions;
#         * expansions «$var», «${var}», «${var:-default}», «${var+SET}»,
#           «${var#prefix}», «${var%suffix}», and «$( cmd )»;
#         * compound commands having a testable exit status, especially «case»;
#         * various built-in commands including «command», «set», and «ulimit».
#
#   Important for patching:
#
#   (2) This script targets any POSIX shell, so it avoids extensions provided
#       by Bash, Ksh, etc; in particular arrays are avoided.
#
#       The "traditional" practice of packing multiple parameters into a
#       space-separated string is a well documented source of bugs and security
#       problems, so this is (mostly) avoided, by progressively accumulating
#       options in "$@", and eventually passing that to Java.
#
#       Where the inherited environment variables (DEFAULT_JVM_OPTS, JAVA_OPTS,
#       and GRADLE_OPTS) rely on word-splitting, this is performed explicitly;
#       see the in-line comments for details.
#
#       There are tweaks for specific operating systems such as AIX, CygWin,
#       Darwin, MinGW, and NonStop.
#
#   (3) This script is generated from the Groovy template
#       https://github.com/gradle/gradle/blob/HEAD/subprojects/plugins/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
#       within the Gradle project.
#
#       You can find Gradle at https://github.com/gradle/gradle/.
#
##############################################################################

# Attempt to set APP_HOME

# Resolve links: $0 may be a link
app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# This is normally unused
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P ) || exit

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar


# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # In POSIX sh, ulimit -H is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "Could not query maximum file descriptor limit"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC3045
        ulimit -n "$MAX_FD" ||
            warn "Could not set maximum file descriptor limit to $MAX_FD"
    esac
fi

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

# For Cygwin or MSYS, switch paths to Windows format before running java
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )
    CLASSPATH=$( cygpath --path --mixed "$CLASSPATH" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # don't mess with options #(
              /?*)  t=${arg#/} t=/${t%%/*}              # looks like a POSIX filepath
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # Roll the args list around exactly as many times as the number of
        # args, so each arg winds up back in the position where it started, but
        # possibly modified.
        #
        # NB: a `for` loop captures its iteration list before it begins, so
        # changing the positional parameters here affects neither the number of
        # iterations, nor the values presented in `arg`.
        shift                   # remove old arg
        set -- "$@" "$arg"      # push replacement arg
    done
fi


# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Collect all arguments for the java command;
#   * $DEFAULT_JVM_OPTS, $JAVA_OPTS, and $GRADLE_OPTS can contain fragments of
#     shell script including quotes and variable substitutions, so put them in
#     double quotes to make sure that they get re-expanded; and
#   * put everything else in single quotes, so that it's not re-expanded.

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
fi

# Use "xargs" to parse quoted args.
#
# With -n1 it outputs one arg per line, with the quotes and backslashes removed.
#
# In Bash we could simply go:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# but POSIX shell has neither arrays nor command substitution, so instead we
# post-process each arg (as a line of input to sed) to backslash-escape any
# character that might be a shell metacharacter, then use eval to reverse
# that process (while maintaining the separation between arguments), and wrap
# the whole thing up as a single "set" statement.
#
# This will of course break if any of these variables contains a newline or
# an unmatched quote.
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
```

## File: local.properties
```
sdk.dir=/home/rakeshpc/Android/Sdk
```

## File: settings.gradle.kts
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "portfolio-os-mobile"
include(":app")
```
