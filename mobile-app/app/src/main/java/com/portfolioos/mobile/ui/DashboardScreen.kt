package com.portfolioos.mobile.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

// Material 3 Expressive Vibrant Obsidian Palette
val M3ObsidianDark = Color(0xFF04060E)
val M3SurfaceCard = Color(0xFF0B101D)
val M3SurfaceVariant = Color(0xFF141C30)
val M3ElectricLime = Color(0xFFD0FF00)
val M3NeonCyan = Color(0xFF00F0FF)
val M3VibrantViolet = Color(0xFFD946EF)
val M3GreenPositive = Color(0xFF10B981)
val M3AmberWarning = Color(0xFFF59E0B)
val M3TextMuted = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    snapshot: SyncSnapshot?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

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
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "PORTFOLIO OS",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                letterSpacing = 2.5.sp,
                                color = Color.White
                            )
                            Text(
                                text = snapshot?.syncInfo?.fiscalYear?.let { "Fiscal Year $it · Expressive M3" } ?: "Sync Active",
                                fontSize = 11.sp,
                                color = M3ElectricLime,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = M3ObsidianDark
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = M3SurfaceCard,
                    contentColor = M3ElectricLime
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Star, contentDescription = "Holdings") },
                        label = { Text("Holdings", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = M3ElectricLime,
                            indicatorColor = Color(0xFF263500)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = "Radar") },
                        label = { Text("AI Radar", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = M3VibrantViolet,
                            indicatorColor = Color(0xFF380842)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.List, contentDescription = "Tax Lots") },
                        label = { Text("Tax Lots", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = M3NeonCyan,
                            indicatorColor = Color(0xFF00363A)
                        )
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onRefresh,
                    containerColor = M3ElectricLime,
                    contentColor = Color.Black,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync Refresh")
                }
            },
            containerColor = M3ObsidianDark
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
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
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Core Node Disconnected",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ensure Core Node container is running on port 8080.",
                                    color = M3TextMuted,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onRefresh,
                                    colors = ButtonDefaults.buttonColors(containerColor = M3ElectricLime)
                                ) {
                                    Text("Retry Connection", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    val syncInfo = snapshot.syncInfo
                    val holdings = snapshot.holdings ?: emptyList()
                    val radarSignals = snapshot.radarSignals ?: emptyList()
                    val taxLots = snapshot.taxLots ?: emptyList()

                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) togetherWith
                            fadeOut(animationSpec = tween(200))
                        },
                        label = "TabTransition"
                    ) { targetTab ->
                        when (targetTab) {
                            0 -> HoldingsView(syncInfo, holdings)
                            1 -> RadarSignalsView(radarSignals)
                            2 -> GroupedTaxLotsView(taxLots, holdings)
                        }
                    }
                }
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Asymmetrical Expressive Hero Net Worth Card
            Card(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomEnd = 28.dp, bottomStart = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(M3ElectricLime.copy(alpha = 0.5f), M3NeonCyan.copy(alpha = 0.2f))),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomEnd = 28.dp, bottomStart = 8.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF142400), Color(0xFF072A30), Color(0xFF0B101D))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NET WORTH VALUATION",
                                color = M3ElectricLime,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp
                            )
                            Surface(
                                color = M3ElectricLime.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = syncInfo?.xirrPercentage ?: "0.00% XIRR",
                                    color = M3ElectricLime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = syncInfo?.formattedCurrentValue ?: "₹0.00",
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
                                    text = syncInfo?.formattedTotalInvested ?: "₹0.00",
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
                                Text(
                                    text = syncInfo?.formattedUnrealizedGain ?: "+₹0.00",
                                    color = if ((syncInfo?.unrealizedGain ?: 0.0) >= 0) M3GreenPositive else Color.Red,
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
            Text(
                text = "ACTIVE HOLDINGS (${holdings.size} SCHEMES)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = M3TextMuted,
                letterSpacing = 1.sp
            )
        }

        if (holdings.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(16.dp),
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
            items(holdings) { holding ->
                M3HoldingCard(holding)
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun M3HoldingCard(holding: FlatHoldingDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
        shape = RoundedCornerShape(16.dp),
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
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${if (holding.xirr >= 0) "+" else ""}${holding.xirr}% XIRR",
                        color = if (holding.xirr >= 0) M3GreenPositive else Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                        text = "Valuation: ${holding.formattedCurrentValue}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${holding.totalUnits} Units · Cost: ${holding.formattedInvestedValue}",
                        color = M3TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(holding.assetBucket, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = M3SurfaceVariant,
                        labelColor = M3NeonCyan
                    )
                )
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "PRIORITY AI RADAR & QUANT INTELLIGENCE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = M3TextMuted,
                letterSpacing = 1.sp
            )
        }

        if (radarSignals.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(16.dp),
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
            items(radarSignals) { signal ->
                M3RadarCard(signal)
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun M3RadarCard(signal: RadarSignalDto) {
    val isQuant = signal.signalType.contains("QUANT", ignoreCase = true)
    val isWarning = signal.severity.equals("WARNING", ignoreCase = true)
    val borderColor = if (isQuant) M3VibrantViolet else if (isWarning) M3AmberWarning else M3NeonCyan
    val containerColor = if (isQuant) Color(0xFF1E0A24) else M3SurfaceCard

    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(borderColor)),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 6.dp),
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
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = signal.badgeText.ifEmpty { "Action Required" },
                        color = borderColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SCHEME-GROUPED TAX LOTS (${groupedLots.size} SCHEMES · ${taxLots.size} LOTS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = M3TextMuted,
                letterSpacing = 1.sp
            )
        }

        if (groupedLots.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
                    shape = RoundedCornerShape(16.dp),
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
            items(groupedLots.entries.toList()) { (isin, lots) ->
                val schemeName = nameMap[isin] ?: isin
                GroupedSchemeTaxLotCard(schemeName = schemeName, isin = isin, lots = lots)
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun GroupedSchemeTaxLotCard(schemeName: String, isin: String, lots: List<FlatTaxLotDto>) {
    var expanded by remember { mutableStateOf(false) }

    val ltcgCount = lots.count { it.isLongTerm }
    val stcgCount = lots.size - ltcgCount
    val totalUnits = lots.sumOf { it.units }

    Card(
        colors = CardDefaults.cardColors(containerColor = M3SurfaceCard),
        shape = RoundedCornerShape(16.dp),
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
                            shape = RoundedCornerShape(6.dp)
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
                            shape = RoundedCornerShape(6.dp)
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
                                text = "${lot.buyDate} · ${lot.units} u @ ₹${lot.costPerUnit}",
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
