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
import androidx.compose.ui.graphics.toArgb
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

import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.shader.toDynamicShader
import com.patrykandpatrick.vico.compose.common.of
import com.patrykandpatrick.vico.core.common.Dimensions
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.shape.Corner
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.data.AxisValueOverrider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker

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
                val modelProducer = remember { CartesianChartModelProducer() }
                LaunchedEffect(trendPoints) {
                    if (trendPoints.isNotEmpty()) {
                        modelProducer.runTransaction {
                            lineSeries {
                                series(
                                    x = trendPoints.indices.map { it.toFloat() },
                                    y = trendPoints.map { it.valuation.toFloat() }
                                )
                            }
                        }
                    }
                }

                val dateAxisFormatter = CartesianValueFormatter { value, _, _ ->
                    val idx = value.toInt()
                    if (idx in trendPoints.indices) trendPoints[idx].date else ""
                }

                val valueAxisFormatter = CartesianValueFormatter { value, _, _ ->
                    "₹${String.format("%.1fL", value / 100000.0)}"
                }

                val marker = rememberChartMarker()

                val line = rememberLine(
                    fill = LineCartesianLayer.LineFill.single(
                        Fill(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ColorTokens.ElectricLime,
                                    ColorTokens.ElectricLime
                                )
                            ).toDynamicShader()
                        )
                    ),
                    areaFill = LineCartesianLayer.AreaFill.single(
                        Fill(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ColorTokens.ElectricLime.copy(alpha = 0.35f),
                                    ColorTokens.CyanBright.copy(alpha = 0.02f)
                                )
                            ).toDynamicShader()
                        )
                    )
                )

                val scrollState = rememberVicoScrollState(scrollEnabled = false)
                val zoomState = rememberVicoZoomState(
                    zoomEnabled = false,
                    initialZoom = Zoom.Content
                )

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(line),
                            axisValueOverrider = remember { AxisValueOverrider.adaptiveYValues(1.05f, false) }
                        ),
                        startAxis = rememberStartAxis(
                            valueFormatter = valueAxisFormatter,
                            guideline = null,
                            label = rememberTextComponent(
                                color = ColorTokens.TextMuted,
                                textSize = 9.sp
                            )
                        ),
                        bottomAxis = rememberBottomAxis(
                            valueFormatter = dateAxisFormatter,
                            guideline = null,
                            itemPlacer = remember { HorizontalAxis.ItemPlacer.default(spacing = 60) },
                            label = rememberTextComponent(
                                color = ColorTokens.TextMuted,
                                textSize = 10.sp
                            )
                        ),
                        marker = marker
                    ),
                    modelProducer = modelProducer,
                    scrollState = scrollState,
                    zoomState = zoomState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            }
        }
    }
}

@Composable
fun rememberChartMarker(): DefaultCartesianMarker {
    val pillShape = CorneredShape(Corner.FullyRounded, Corner.FullyRounded, Corner.FullyRounded, Corner.FullyRounded)
    val label = rememberTextComponent(
        color = ColorTokens.ObsidianBackground,
        background = rememberShapeComponent(
            shape = pillShape,
            color = ColorTokens.ElectricLime
        ),
        padding = Dimensions.of(horizontal = 8.dp, vertical = 4.dp),
        textSize = 11.sp
    )
    val indicator = rememberShapeComponent(
        shape = pillShape,
        color = ColorTokens.CyanBright
    )
    val guideline = rememberLineComponent(
        color = ColorTokens.CyanBright.copy(alpha = 0.5f)
    )
    return rememberDefaultCartesianMarker(
        label = label,
        indicator = { indicator },
        guideline = guideline
    )
}
