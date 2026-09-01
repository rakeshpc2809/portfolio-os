package com.portfolioos.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProvenanceBadge(
    sourceType: String,
    isUnverifiedEstimate: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (label, bgColor, borderColor, textColor) = when {
        isUnverifiedEstimate || sourceType == "MANUAL_ESTIMATE_UNVERIFIED" -> {
            Quadruple(
                "PROVISIONAL SAMPLE",
                Color(0x26F59E0B),
                Color(0x80F59E0B),
                Color(0xFFFBBF24)
            )
        }
        sourceType == "FACTSHEET_POI_PARSED" -> {
            Quadruple(
                "FACTSHEET AUDITED",
                Color(0x2610B981),
                Color(0x8010B981),
                Color(0xFF34D399)
            )
        }
        else -> {
            Quadruple(
                "NSE BENCHMARK",
                Color(0x260284C7),
                Color(0x8038BDF8),
                Color(0xFF38BDF8)
            )
        }
    }

    Text(
        text = label,
        color = textColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
