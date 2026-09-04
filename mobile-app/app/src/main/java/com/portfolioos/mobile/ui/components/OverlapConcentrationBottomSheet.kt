package com.portfolioos.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portfolioos.mobile.model.CoverageTelemetryDto
import com.portfolioos.mobile.model.OverlapReportDto
import com.portfolioos.mobile.model.PairwiseOverlapDto
import com.portfolioos.mobile.model.StockConcentrationDto
import com.portfolioos.mobile.ui.theme.ColorTokens
import com.portfolioos.mobile.util.formatInr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlapConcentrationBottomSheet(
    sheetState: SheetState,
    overlapReport: OverlapReportDto?,
    onDismiss: () -> Unit,
    onToggleProvisional: (Boolean) -> Unit
) {
    var isProvisionalActive by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OVERLAP & CONCENTRATION",
                        color = Color(0xFF38BDF8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    val telemetry = overlapReport?.coverageTelemetry
                    val caption = if (telemetry != null) {
                        val covPct = "%.1f%%".format(telemetry.auditedCoveragePct)
                        val aum = formatInr(telemetry.auditedAum)
                        if (isProvisionalActive) {
                            "Showing all held funds including provisional ($covPct coverage)"
                        } else {
                            "Based on $aum of audited holdings — $covPct of equity"
                        }
                    } else {
                        "Pairwise capital overlap & portfolio aggregation"
                    }
                    Text(
                        text = caption,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "✕",
                    color = Color(0xFF94A3B8),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val matrix = overlapReport?.pairwiseMatrix.orEmpty()
            val filteredPairs = remember(matrix) {
                matrix.filter { it.fundA != it.fundB }
                    .sortedByDescending { it.overlapPercentage }
            }
            val positivePairs = remember(filteredPairs) {
                filteredPairs.filter { it.overlapPercentage > 0.0 }
            }
            val zeroPairs = remember(filteredPairs) {
                filteredPairs.filter { it.overlapPercentage <= 0.0 }
            }
            val top5Stocks = remember(overlapReport?.stockConcentrations) {
                overlapReport?.stockConcentrations.orEmpty().take(5)
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SECTION 1: PAIRWISE OVERLAP
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PAIRWISE FUND OVERLAP",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${filteredPairs.size} pairs (${positivePairs.size} overlapping)",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (positivePairs.isEmpty()) {
                            Text(
                                text = "No overlapping holdings found between held funds.",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                positivePairs.forEach { pair ->
                                    PairwiseBarRow(pair = pair)
                                }
                            }
                        }

                        // Collapsed Zero Overlap Section
                        if (zeroPairs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = Color.White.copy(alpha = 0.05f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                zeroPairs.forEach { pair ->
                                    Text(
                                        text = "No overlap found between ${pair.fundA} and ${pair.fundB}",
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Legend Line
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color.White.copy(alpha = 0.05f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF38BDF8))
                                )
                                Text("Audited", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF59E0B))
                                )
                                Text("Provisional Sample", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                        }
                    }
                }

                // SECTION 2: TOP 5 STOCK CONCENTRATIONS
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOP STOCK CONCENTRATIONS",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (isProvisionalActive) "Hide provisional" else "Show provisional estimates too",
                                color = if (isProvisionalActive) Color(0xFF38BDF8) else Color(0xFF64748B),
                                fontSize = 10.sp,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .clickable {
                                        isProvisionalActive = !isProvisionalActive
                                        onToggleProvisional(isProvisionalActive)
                                    }
                                    .padding(vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (top5Stocks.isEmpty()) {
                            Text(
                                text = "No stock concentrations calculated.",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                top5Stocks.forEach { stock ->
                                    StockConcentrationRow(stock = stock)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PairwiseBarRow(pair: PairwiseOverlapDto) {
    val pct = "%.2f".format(pair.overlapPercentage)
    val dotColorA = if (pair.isUnverifiedEstimate || pair.sourceTypeA == "MANUAL_ESTIMATE_UNVERIFIED") Color(0xFFF59E0B) else Color(0xFF38BDF8)
    val dotColorB = if (pair.isUnverifiedEstimate || pair.sourceTypeB == "MANUAL_ESTIMATE_UNVERIFIED") Color(0xFFF59E0B) else Color(0xFF38BDF8)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColorA))
                Text(
                    text = pair.fundA,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text("×", color = Color(0xFF64748B), fontSize = 10.sp)
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColorB))
                Text(
                    text = pair.fundB,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${pair.commonHoldingsCount} stocks",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp
                )
                Text(
                    text = "$pct%",
                    color = Color(0xFFD0FF00),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Horizontal Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            val progress = (pair.overlapPercentage / 100.0).coerceIn(0.0, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF38BDF8))
            )
        }
    }
}

@Composable
private fun StockConcentrationRow(stock: StockConcentrationDto) {
    val dotColor = if (stock.isAudited) Color(0xFF38BDF8) else Color(0xFFF59E0B)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
            Column {
                Text(
                    text = stock.stockSymbol,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (stock.companyName.isNotBlank()) {
                    Text(
                        text = stock.companyName,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatInr(stock.rupeeExposure),
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Surface(
                color = ColorTokens.GreenPositive.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "%.2f%%".format(stock.portfolioWeightPct),
                    color = ColorTokens.GreenPositive,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}
