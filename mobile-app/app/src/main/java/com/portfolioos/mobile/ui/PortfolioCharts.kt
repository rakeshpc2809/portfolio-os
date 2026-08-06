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
import com.portfolioos.mobile.model.FlatHoldingDto
import com.portfolioos.mobile.model.NetWorthPointDto
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1424)),
        shape = RoundedCornerShape(20.dp),
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
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "SEBI Fund Categorization Engine",
                        color = Color(0xFFD0FF00),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "₹${String.format("%,.0f", totalInvested)}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-segment Linear Progress Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E293B))
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

            // Category Legend Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(alloc.color)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = alloc.bucketName.take(16),
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = "${String.format("%.1f", alloc.percentage)}%",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
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
    val animProgress = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(trendPoints) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1424)),
        shape = RoundedCornerShape(20.dp),
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
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "NAV Growth & Capital Curve",
                        color = Color(0xFFD0FF00),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (selectedIndex != null && selectedIndex!! in trendPoints.indices) {
                    val pt = trendPoints[selectedIndex!!]
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = pt.date,
                            color = Color(0xFF06B6D4),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "₹${String.format("%,.0f", pt.valuation)}",
                            color = Color(0xFFD0FF00),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
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
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                val rawVals = trendPoints.map { it.valuation }
                val minVal = rawVals.minOrNull() ?: 1.0
                val maxVal = rawVals.maxOrNull() ?: (minVal * 1.2)
                val valRange = (maxVal - minVal).coerceAtLeast(1.0)
                val points = rawVals.map { v -> ((v - minVal) / valRange * 0.70 + 0.25).toFloat() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer()
                            .pointerInput(trendPoints) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                                        val idx = (offset.x / stepX).roundToInt().coerceIn(0, trendPoints.size - 1)
                                        if (idx != selectedIndex) {
                                            selectedIndex = idx
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                                        val idx = (change.position.x / stepX).roundToInt().coerceIn(0, trendPoints.size - 1)
                                        if (idx != selectedIndex) {
                                            selectedIndex = idx
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDragEnd = { selectedIndex = null },
                                    onDragCancel = { selectedIndex = null }
                                )
                            }
                    ) {
                        val width = size.width
                        val height = size.height

                        val stepX = width / (points.size - 1).coerceAtLeast(1)
                        val path = androidx.compose.ui.graphics.Path()
                        val fillPath = androidx.compose.ui.graphics.Path()

                        val startY = height - (points[0] * height * 0.7f * animProgress.value)
                        path.moveTo(0f, startY)
                        fillPath.moveTo(0f, height)
                        fillPath.lineTo(0f, startY)

                        for (i in 1 until points.size) {
                            val x = i * stepX
                            val y = height - (points[i] * height * 0.7f * animProgress.value)
                            val prevX = (i - 1) * stepX
                            val prevY = height - (points[i - 1] * height * 0.7f * animProgress.value)

                            val controlX1 = prevX + (stepX / 2f)
                            val controlY1 = prevY
                            val controlX2 = prevX + (stepX / 2f)
                            val controlY2 = y

                            path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                            fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                        }

                        fillPath.lineTo(width, height)
                        fillPath.close()

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFD0FF00).copy(alpha = 0.35f), Color(0xFF00F0FF).copy(alpha = 0.02f))
                            )
                        )

                        drawPath(
                            path = path,
                            color = Color(0xFFD0FF00),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Render active scrub cursor line
                        if (selectedIndex != null && selectedIndex!! in points.indices) {
                            val cx = selectedIndex!! * stepX
                            drawLine(
                                color = Color(0xFF06B6D4),
                                start = androidx.compose.ui.geometry.Offset(cx, 0f),
                                end = androidx.compose.ui.geometry.Offset(cx, height),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
            }
        }
    }
}
