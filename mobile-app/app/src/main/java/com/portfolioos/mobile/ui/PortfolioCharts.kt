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
