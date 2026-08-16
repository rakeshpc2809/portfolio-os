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
