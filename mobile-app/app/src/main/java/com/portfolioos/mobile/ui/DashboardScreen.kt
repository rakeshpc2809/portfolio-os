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
            HistoricalNetWorthTrendChart(trendPoints = snapshot?.netWorthHistory ?: emptyList())
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
            items(radarSignals, key = { s -> s.title.ifEmpty { s.signalType } }) { signal ->
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
            items(holdings, key = { h -> h.isin.ifEmpty { h.fundName } }) { holding ->
                M3HoldingCard(holding)
            }
        }
    }
}

@Composable
fun M3HoldingCard(holding: FlatHoldingDto, onSimulateSale: (FlatHoldingDto) -> Unit = {}) {
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
                    color = M3ElectricLime.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "🔄 SIP",
                        color = M3ElectricLime,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Button(
                        onClick = { onSimulateSale(holding) },
                        colors = ButtonDefaults.buttonColors(containerColor = M3NeonCyan.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Simulate ➔", color = M3NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(listOf(M3VibrantViolet.copy(alpha = 0.7f), M3NeonCyan.copy(alpha = 0.35f))),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1E0B36), Color(0xFF0F172A), Color(0xFF030712))
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Surface(
                            color = M3VibrantViolet.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "COMING IN PHASE 2",
                                color = M3VibrantViolet,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "OVERLAP & CONCENTRATION AUDIT",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Fund Overlap Matrix & Stock Look-Through",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The mobile 4-tab navigation shell is active. Interactive fund-to-fund portfolio overlap, stock concentration analysis, and asset class drift details are undergoing mobile-first UI adaptation for Phase 2.",
                            color = M3TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
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

@Composable
fun RebalanceWaterfallView(rebalancePlan: com.portfolioos.mobile.model.RebalancePlanDto?) {
    val sellSide = rebalancePlan?.sellSide
    val tiers = remember(sellSide) { sellSide?.waterfall.orEmpty().filter { it.lots.isNotEmpty() } }

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
                text = "REBALANCE WATERFALL FLOW",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = M3TextMuted,
                letterSpacing = 1.5.sp
            )
        }

        if (rebalancePlan == null) {
            item {
                PortfolioStateCard(
                    icon = Icons.Default.Info,
                    iconTint = M3NeonCyan,
                    title = "Rebalance Plan Unavailable",
                    subtitle = "Core Node Sync Required",
                    description = "Point-in-time drawdown tiers and waterfall trade plans require connection to Core Node. Connect to Core Node to compute allocation drift.",
                    actionLabel = "Sync with Core Node",
                    onAction = {}
                )
            }
        } else if (sellSide == null || tiers.isEmpty() || sellSide.totalRequired == 0.0) {
            item {
                PortfolioStateCard(
                    icon = Icons.Default.CheckCircle,
                    iconTint = M3GreenPositive,
                    title = "Portfolio Allocation Balanced",
                    subtitle = "No Rebalance Action Required",
                    description = rebalancePlan.reasoningNarrative?.headline?.ifEmpty {
                        "All asset categories remain within target allocation bands. LTCG exemption headroom is uncompromised."
                    } ?: "All asset categories remain within target allocation bands. LTCG exemption headroom is uncompromised."
                )
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E150A)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = rebalancePlan.trigger?.reasonLabel ?: "INDUCED REBALANCE TRIGGERED",
                            color = M3AmberWarning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("TARGET TRIM", color = M3TextMuted, fontSize = 10.sp)
                                Text(formatInr(sellSide.totalRequired), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("ESTIMATED TAX DRAG", color = M3TextMuted, fontSize = 10.sp)
                                Text(formatInr(sellSide.taxSummary?.totalTaxEstimate ?: 0.0), color = M3AmberWarning, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            tiers.forEach { tier ->
                item(key = "tier_${tier.tier}") {
                    Text(
                        text = tier.tierLabel.ifEmpty { tier.tier },
                        color = M3NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(
                    items = tier.lots,
                    key = { "${it.fundId}_${it.acquisitionDate}_${it.unitsSold}_${it.saleProceeds}" }
                ) { lot ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lot.fundName.ifEmpty { lot.fundId },
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Acquired ${lot.acquisitionDate} · ${lot.unitsSold} units",
                                    color = M3TextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatInr(lot.saleProceeds),
                                    color = M3ElectricLime,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = lot.taxTerm,
                                    color = if (lot.taxTerm.contains("LONG")) M3GreenPositive else M3AmberWarning,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

