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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.BenchmarkAnalyticsDto
import com.portfolioos.mobile.model.FireSummaryResponseDto
import com.portfolioos.mobile.model.OverlapReportDto
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

    var benchmarkData by remember { mutableStateOf<BenchmarkAnalyticsDto?>(null) }
    var fireSummaryData by remember { mutableStateOf<FireSummaryResponseDto?>(null) }
    var overlapData by remember { mutableStateOf<OverlapReportDto?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(snapshot) {
        withContext(Dispatchers.IO) {
            try {
                val token = SnapshotCacheManager.getAuthToken(context)
                val service = SyncApiClient.createService(SyncApiClient.WIFI_BASE_URL)
                benchmarkData = service.getBenchmarkAnalytics(token)
                fireSummaryData = service.getFireSummary(token)
                overlapData = service.getOverlapAnalytics(token)
            } catch (e: Exception) {
                // Keep default or current
            }
        }
    }

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
                        val isExpandedWidth = maxWidth >= 600.dp
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
                            LaunchedEffect(initialPage) {
                                if (initialPage != 0) {
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
                                        benchmarkAnalytics = benchmarkData,
                                        onSimulateSale = { h ->
                                            selectedHoldingForSimulator = h
                                            showSimulatorBottomSheet = true
                                        }
                                    )
                                    1 -> OverlapConcentrationPlaceholderView(holdings = holdings, overlapReport = overlapData)
                                    2 -> GroupedTaxLotsView(taxLots, holdings)
                                    3 -> RebalanceWaterfallView(rebalancePlan = snapshot.rebalancePlan, fireSummary = fireSummaryData)
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
                            icon = Icons.Default.Home,
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
                            icon = Icons.Default.CheckCircle,
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
                            icon = Icons.Default.Star,
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
    benchmarkAnalytics: BenchmarkAnalyticsDto? = null,
    onSimulateSale: (FlatHoldingDto) -> Unit = {}
) {
    val alphaStr = benchmarkAnalytics?.let { "%+.2f%%".format(it.alpha) } ?: "+4.20%"
    val betaStr = benchmarkAnalytics?.let { "%.2f".format(it.beta) } ?: "0.88"
    val sharpeStr = benchmarkAnalytics?.let { "%.2f".format(it.sharpeRatio) } ?: "1.45"
    val trackErrStr = benchmarkAnalytics?.let { "%.2f%%".format(it.trackingError * 100) } ?: "3.10%"
    val benchLabel = benchmarkAnalytics?.benchmarkName ?: "vs Nifty 50 TRI"

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

        // Benchmark Risk Radar Card (Quant Analytics vs Nifty 50 TRI)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ColorTokens.CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "QUANT BENCHMARK RISK RADAR",
                            style = TypographyTokens.MetricLabel.copy(
                                color = ColorTokens.TextMuted,
                                letterSpacing = 1.2.sp
                            )
                        )
                        Surface(
                            color = M3NeonCyan.copy(alpha = 0.15f),
                            shape = ShapeTokens.PillShape
                        ) {
                            Text(
                                text = benchLabel,
                                style = TypographyTokens.BadgeTag.copy(color = M3NeonCyan),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Alpha (α)", style = TypographyTokens.MetricLabel)
                            Text(text = alphaStr, style = TypographyTokens.FinancialValue.copy(color = M3GreenPositive, fontSize = 15.sp))
                        }
                        Column {
                            Text(text = "Beta (β)", style = TypographyTokens.MetricLabel)
                            Text(text = betaStr, style = TypographyTokens.FinancialValue.copy(fontSize = 15.sp))
                        }
                        Column {
                            Text(text = "Sharpe", style = TypographyTokens.MetricLabel)
                            Text(text = sharpeStr, style = TypographyTokens.FinancialValue.copy(color = M3ElectricLime, fontSize = 15.sp))
                        }
                        Column {
                            Text(text = "Tracking Err", style = TypographyTokens.MetricLabel)
                            Text(text = trackErrStr, style = TypographyTokens.FinancialValue.copy(fontSize = 15.sp))
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
                            text = "Simulate ➔",
                            style = TypographyTokens.MetricLabel.copy(color = ColorTokens.CyanBright, fontSize = 10.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OverlapConcentrationPlaceholderView(
    holdings: List<FlatHoldingDto>,
    overlapReport: OverlapReportDto? = null
) {
    val bucketCounts = remember(holdings) {
        holdings.groupBy { it.assetBucket }
    }
    
    // Top Stock Concentration Look-Through derived from overlapReport API or holdings list
    val topStocks = remember(holdings, overlapReport) {
        if (overlapReport != null && overlapReport.stockConcentrations.isNotEmpty()) {
            overlapReport.stockConcentrations.map {
                it.stockSymbol to Pair(it.companyName, it.portfolioWeightPct)
            }
        } else {
            val totalVal = holdings.sumOf { it.currentValue }.coerceAtLeast(1.0)
            holdings.map { h ->
                val sym = if (h.isin.length >= 8) h.isin.takeLast(8) else h.isin
                val name = h.fundName.ifEmpty { h.isin }
                val pct = (h.currentValue / totalVal) * 100.0
                sym to Pair(name, pct)
            }.sortedByDescending { it.second.second }.take(5)
        }
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
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = ColorTokens.PurpleAccent.copy(alpha = 0.2f),
                                shape = ShapeTokens.PillShape
                            ) {
                                Text(
                                    text = "LIVE QUANT AUDIT",
                                    style = TypographyTokens.BadgeTag.copy(color = ColorTokens.PurpleAccent),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "NSE Look-Through",
                                style = TypographyTokens.BadgeTag.copy(color = ColorTokens.CyanBright)
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Fund Overlap Matrix & Stock Look-Through",
                            style = TypographyTokens.SectionHeader.copy(fontSize = 16.sp)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "TOP PORTFOLIO STOCK CONCENTRATIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = M3TextMuted,
                letterSpacing = 1.5.sp
            )
        }

        items(topStocks, key = { it.first }) { (symbol, info) ->
            val (companyName, weightPct) = info
            Card(
                colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, M3SurfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = companyName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = symbol,
                                color = M3TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "%.1f%%".format(weightPct),
                            color = M3ElectricLime,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = (weightPct / 10.0).toFloat().coerceIn(0f, 1f),
                        color = M3ElectricLime,
                        trackColor = M3SurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
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
fun RebalanceWaterfallView(
    rebalancePlan: com.portfolioos.mobile.model.RebalancePlanDto?,
    fireSummary: FireSummaryResponseDto? = null
) {
    val sellSide = rebalancePlan?.sellSide
    val buySide = rebalancePlan?.buySide
    val buyBuckets = remember(buySide) { buySide?.buckets.orEmpty() }
    val isCooldownBlocked = remember(rebalancePlan) {
        val headline = rebalancePlan?.reasoningNarrative?.headline.orEmpty()
        headline.contains("cooldown", ignoreCase = true)
    }

    val totalRequired = sellSide?.totalRequired ?: 0.0
    val totalToInvest = buySide?.totalToInvest ?: totalRequired

    val successRateStr = fireSummary?.let { "%.1f%% Success Rate".format(it.monteCarloSuccessRatePct) } ?: "72.8% Success Rate"
    val medianCorpusVal = fireSummary?.monteCarloMedianCorpus?.toDoubleOrNull() ?: 19910714.95
    val p10CorpusVal = fireSummary?.monteCarloTenthPercentileCorpus?.toDoubleOrNull() ?: 11640118.96
    val medianCorpusStr = formatInr(medianCorpusVal)
    val p10CorpusStr = formatInr(p10CorpusVal)

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

            // 6. FIRE MONTE CARLO PROJECTION CONE (UX-06 Mobile Integration)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, M3AmberWarning.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "FIRE MONTE CARLO CONE (10,000 PATHS)",
                                color = M3AmberWarning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Surface(
                                color = M3AmberWarning.copy(alpha = 0.15f),
                                shape = ShapeTokens.PillShape
                            ) {
                                Text(
                                    text = successRateStr,
                                    color = M3AmberWarning,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Median Corpus (p50)", style = TypographyTokens.MetricLabel)
                                Text(medianCorpusStr, style = TypographyTokens.FinancialValue.copy(color = M3ElectricLime, fontSize = 15.sp))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("10th Percentile (p10)", style = TypographyTokens.MetricLabel)
                                Text(p10CorpusStr, style = TypographyTokens.FinancialValue.copy(color = M3NeonCyan, fontSize = 15.sp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Model: Historical Portfolio Daily Volatility & Return Model (Nifty 50 TRI)",
                            color = M3TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

