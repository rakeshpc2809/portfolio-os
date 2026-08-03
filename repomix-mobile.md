This file is a merged representation of a subset of the codebase, containing files not matching ignore patterns, combined into a single document by Repomix.
The content has been processed where empty lines have been removed, content has been compressed (code blocks are separated by ⋮---- delimiter).

# File Summary

## Purpose
This file contains a packed representation of a subset of the repository's contents that is considered the most important context.
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
- Files matching these patterns are excluded: build/**, .gradle/**, **/*.class, **/*.apk
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Empty lines have been removed from all files
- Content has been compressed - code blocks are separated by ⋮---- delimiter
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
              model/
                SyncModels.kt
              ui/
                DashboardScreen.kt
                PortfolioCharts.kt
              util/
                FormatUtils.kt
              MainActivity.kt
      res/
        values/
          styles.xml
        xml/
          backup_rules.xml
          data_extraction_rules.xml
      AndroidManifest.xml
  build.gradle.kts
build.gradle.kts
gradle.properties
local.properties
settings.gradle.kts
```

# Files

## File: app/src/main/java/com/portfolioos/mobile/api/SyncApiClient.kt
```kotlin
package com.portfolioos.mobile.api
import android.content.Context
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
interface SyncApiService {
    @GET("api/v1/sync/snapshot")
    suspend fun getSnapshot(
        @Header("X-Api-Auth-Token") token: String = "fintracker-cachyos-default-key-2026",
        @Query("fy") fiscalYear: String = "2026-27"
    ): SyncSnapshot
}
object SyncApiClient {
    const val USB_BASE_URL = "http://127.0.0.1:8080/"
    const val EMULATOR_BASE_URL = "http://10.0.2.2:8080/"
    const val WIFI_BASE_URL = "http://192.168.1.13:8080/"
    fun createService(baseUrl: String = USB_BASE_URL): SyncApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
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
        // 1. Try Custom Remote/Tunnel URL if configured
        if (!customUrl.isNullOrBlank()) {
            try {
                val formatted = if (customUrl.endsWith("/")) customUrl else "$customUrl/"
                val remoteSnapshot = createService(formatted).getSnapshot()
                SnapshotCacheManager.saveSnapshot(context, remoteSnapshot)
                return remoteSnapshot
            } catch (e: Exception) {
                // fallthrough to local networks
            }
        }
        // 2. Try USB Loopback (adb reverse)
        try {
            val snapshot = createService(USB_BASE_URL).getSnapshot()
            SnapshotCacheManager.saveSnapshot(context, snapshot)
            return snapshot
        } catch (e1: Exception) {
            // 3. Try Android Emulator loopback
            try {
                val snapshot = createService(EMULATOR_BASE_URL).getSnapshot()
                SnapshotCacheManager.saveSnapshot(context, snapshot)
                return snapshot
            } catch (e2: Exception) {
                // 4. Try Wi-Fi LAN IP
                try {
                    val snapshot = createService(WIFI_BASE_URL).getSnapshot()
                    SnapshotCacheManager.saveSnapshot(context, snapshot)
                    return snapshot
                } catch (e3: Exception) {
                    // 5. Offline Fallback: Load cached snapshot & fetch direct AMFI NAVs over cellular!
                    val cached = SnapshotCacheManager.loadSnapshot(context)
                    if (cached != null) {
                        return SnapshotCacheManager.updateOfflineSnapshotWithLiveAmfi(cached)
                    } else {
                        throw e3
                    }
                }
            }
        }
    }
}
```

## File: app/src/main/java/com/portfolioos/mobile/model/SyncModels.kt
```kotlin
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
```

## File: app/src/main/java/com/portfolioos/mobile/ui/DashboardScreen.kt
```kotlin
package com.portfolioos.mobile.ui
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.launch
// Bleeding-Edge Material 3 Expressive Vibrant Obsidian Palette
val M3ObsidianDark = Color(0xFF030712)
val M3SurfaceCard = Color(0xFF0D1424)
val M3SurfaceVariant = Color(0xFF162036)
val M3ElectricLime = Color(0xFFD0FF00)
val M3NeonCyan = Color(0xFF00F0FF)
val M3VibrantViolet = Color(0xFFE040FB)
val M3GreenPositive = Color(0xFF10B981)
val M3AmberWarning = Color(0xFFF59E0B)
val M3TextMuted = Color(0xFF94A3B8)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    snapshot: SyncSnapshot?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onUpdateCustomUrl: (String) -> Unit = {}
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    var showUrlDialog by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }
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
                            Surface(
                                color = M3ElectricLime.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = snapshot?.syncInfo?.fiscalYear?.let { "FY $it · Android 17 Edge" } ?: "Sync Active",
                                    fontSize = 10.sp,
                                    color = M3ElectricLime,
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
                                    text = "Core Node Offline / Not Synced",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Connect over Wi-Fi, USB, or set a custom server URL.",
                                    color = M3TextMuted,
                                    fontSize = 12.sp
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
                    // High-performance 120fps Horizontal Pager with zero per-frame transform overhead
                    HorizontalPager(
                        state = pagerState,
                        beyondBoundsPageCount = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { page ->
                        when (page) {
                            0 -> HoldingsView(syncInfo, holdings)
                            1 -> RadarSignalsView(radarSignals)
                            2 -> GroupedTaxLotsView(taxLots, holdings)
                        }
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
                    color = Color(0xFF090F1E).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier
                        .shadow(elevation = 24.dp, shape = RoundedCornerShape(100.dp), spotColor = M3ElectricLime)
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
                            label = "Holdings",
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
                            label = "AI Radar",
                            icon = Icons.Default.Notifications,
                            activeColor = M3VibrantViolet,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }
                        )
                        ExpressiveNavPill(
                            selected = pagerState.currentPage == 2,
                            label = "Tax Lots",
                            icon = Icons.Default.List,
                            activeColor = M3NeonCyan,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(2)
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
fun HoldingsView(syncInfo: com.portfolioos.mobile.model.SyncInfoDto?, holdings: List<FlatHoldingDto>) {
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
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 10.dp, bottomEnd = 32.dp, bottomStart = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(M3ElectricLime.copy(alpha = 0.7f), M3NeonCyan.copy(alpha = 0.35f))),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 10.dp, bottomEnd = 32.dp, bottomStart = 10.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF142600), Color(0xFF062C33), Color(0xFF0D1424))
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = M3ElectricLime.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text(
                                    text = "NET WORTH VALUATION",
                                    color = M3ElectricLime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = M3GreenPositive.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text(
                                    text = syncInfo?.xirrPercentage ?: "0.00% XIRR",
                                    color = M3GreenPositive,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = formatInr(syncInfo?.currentValue ?: 0.0),
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = Color.White.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Invested",
                                    color = M3TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formatInr(syncInfo?.totalInvested ?: 0.0),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Unrealized Gain",
                                    color = M3TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val gain = syncInfo?.unrealizedGain ?: 0.0
                                Text(
                                    text = "${if (gain >= 0) "+" else ""}${formatInr(gain)}",
                                    color = if (gain >= 0) M3GreenPositive else Color.Red,
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
        item {
            DonutAllocationChart(holdings = holdings)
        }
        item {
            PerformanceBarChart(holdings = holdings)
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No open holdings recorded in ledger.",
                        color = M3TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(holdings, key = { h -> h.isin.ifEmpty { h.fundName } }) { holding ->
                M3HoldingCard(holding)
            }
        }
    }
}
@Composable
fun M3HoldingCard(holding: FlatHoldingDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 8.dp, bottomEnd = 20.dp, bottomStart = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = holding.fundName.ifEmpty { holding.isin },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = if (holding.xirr >= 0) M3GreenPositive.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "${if (holding.xirr >= 0) "+" else ""}${holding.xirr}% XIRR",
                        color = if (holding.xirr >= 0) M3GreenPositive else Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
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
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${holding.totalUnits} Units · Cost: ${formatInr(holding.investedValue)}",
                        color = M3TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Surface(
                    color = M3SurfaceVariant,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = holding.assetBucket,
                        color = M3NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
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
            items(radarSignals, key = { s -> "${s.title}-${s.signalType}" }) { signal ->
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No tax lots recorded.",
                        color = M3TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(groupedLots.entries.toList(), key = { entry -> entry.key }) { (isin, lots) ->
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
                            Text(
                                text = "${lot.buyDate} · ${lot.units} u @ ${formatInr(lot.costPerUnit)}",
                                color = M3TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
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
```

## File: app/src/main/java/com/portfolioos/mobile/ui/PortfolioCharts.kt
```kotlin
package com.portfolioos.mobile.ui
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.model.FlatHoldingDto
data class BucketAllocation(
    val bucketName: String,
    val totalAmount: Double,
    val percentage: Float,
    val color: Color
)
val SEBIBucketColors = mapOf(
    "Flexi Cap" to Color(0xFF06B6D4),                // Vibrant Cyan
    "Large & Midcap" to Color(0xFFA855F7),           // Electric Violet
    "Midcap" to Color(0xFF3B82F6),                   // Royal Blue
    "Small Cap" to Color(0xFF10B981),                // Emerald Green
    "Microcap" to Color(0xFFEC4899),                 // Coral Pink
    "Factor Value Index" to Color(0xFFF59E0B),       // Amber Gold
    "Factor Momentum Index" to Color(0xFF6366F1),    // Indigo
    "Equal Weight Index" to Color(0xFF14B8A6),       // Teal
    "Sectoral/Thematic" to Color(0xFFF43F5E),        // Rose
    "Gold & Commodities" to Color(0xFFEAB308),       // Gold
    "Debt & Liquid" to Color(0xFF64748B)             // Slate
)
@Composable
fun DonutAllocationChart(
    holdings: List<FlatHoldingDto>,
    modifier: Modifier = Modifier
) {
    val defaultColor = Color(0xFF94A3B8)
    val allocations = remember(holdings) {
        val totalVal = holdings.sumOf { it.currentValue.takeIf { v -> v > 0 } ?: (it.totalUnits * it.avgCost) }.coerceAtLeast(1.0)
        val grouped = holdings.groupBy { it.assetBucket.ifEmpty { "Others" } }
        grouped.map { (bucket, list) ->
            val bucketVal = list.sumOf { it.currentValue.takeIf { v -> v > 0 } ?: (it.totalUnits * it.avgCost) }
            val pct = (bucketVal / totalVal * 100).toFloat()
            BucketAllocation(
                bucketName = bucket,
                totalAmount = bucketVal,
                percentage = pct,
                color = SEBIBucketColors[bucket] ?: defaultColor
            )
        }.sortedByDescending { it.percentage }
    }
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(holdings) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1424)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "SEBI CATEGORY ALLOCATION",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut Canvas
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(120.dp)) {
                        val strokeWidth = 22.dp.toPx()
                        var startAngle = -90f
                        allocations.forEach { alloc ->
                            val sweepAngle = (alloc.percentage / 100f) * 360f * animProgress.value
                            drawArc(
                                color = alloc.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += sweepAngle
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${allocations.size}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Categories",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                // Legend List
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allocations.take(5).forEach { alloc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(alloc.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = alloc.bucketName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "%.1f%%".format(alloc.percentage),
                                color = alloc.color,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun PerformanceBarChart(
    holdings: List<FlatHoldingDto>,
    modifier: Modifier = Modifier
) {
    val topHoldings = remember(holdings) {
        holdings.sortedByDescending { it.xirr }.take(5)
    }
    val maxVal = remember(topHoldings) {
        topHoldings.maxOfOrNull { kotlin.math.abs(it.xirr) }?.toFloat()?.coerceAtLeast(1f) ?: 10f
    }
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(holdings) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1424)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "TOP PERFORMING SCHEMES (XIRR)",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            topHoldings.forEach { holding ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = holding.fundName.ifEmpty { holding.isin },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${if (holding.xirr >= 0) "+" else ""}${holding.xirr}%",
                            color = if (holding.xirr >= 0) Color(0xFF10B981) else Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val barRatio = (kotlin.math.abs(holding.xirr).toFloat() / maxVal * animProgress.value).coerceIn(0.05f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF181F33))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barRatio)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = if (holding.xirr >= 0) listOf(Color(0xFF06B6D4), Color(0xFF10B981))
                                        else listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                                    )
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
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

## File: app/src/main/java/com/portfolioos/mobile/MainActivity.kt
```kotlin
package com.portfolioos.mobile
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.ui.DashboardScreen
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var snapshot by remember { mutableStateOf<SyncSnapshot?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()
            fun fetchSyncSnapshot() {
                scope.launch {
                    isLoading = true
                    try {
                        snapshot = SyncApiClient.fetchSnapshotWithFallback(applicationContext)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
            LaunchedEffect(Unit) {
                fetchSyncSnapshot()
            }
            DashboardScreen(
                snapshot = snapshot,
                isLoading = isLoading,
                onRefresh = { fetchSyncSnapshot() },
                onUpdateCustomUrl = { newUrl ->
                    SnapshotCacheManager.setCustomUrl(applicationContext, newUrl)
                    fetchSyncSnapshot()
                }
            )
        }
    }
}
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
        android:icon="@android:drawable/ic_dialog_info"
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
    </application>
</manifest>
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

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

## File: build.gradle.kts
```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

## File: gradle.properties
```
android.useAndroidX=true
android.nonFinalResIds=false
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
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
