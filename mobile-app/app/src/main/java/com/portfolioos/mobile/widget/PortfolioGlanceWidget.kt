package com.portfolioos.mobile.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.portfolioos.mobile.MainActivity
import com.portfolioos.mobile.data.SnapshotCacheManager

class PortfolioGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = SnapshotCacheManager.loadSnapshot(context)
        val info = snapshot?.syncInfo
        val holdings = snapshot?.holdings ?: emptyList()

        val bestFund = holdings.maxByOrNull { it.xirr }
        val worstFund = holdings.minByOrNull { it.xirr }

        // Calculate portfolio gain percentage for privacy-first display
        val isInfoValid = info != null && info.generatedAt.isNotBlank() && info.generatedAt != "OFFLINE_FALLBACK" && info.totalInvested > 0.0
        val gainPct = if (isInfoValid && info != null) {
            (info.unrealizedGain / info.totalInvested) * 100.0
        } else {
            0.0
        }
        val formattedGainPct = if (isInfoValid) {
            String.format("%s%.2f%%", if (gainPct >= 0) "+" else "", gainPct)
        } else {
            "--%"
        }
        val widgetXirr = if (isInfoValid && info != null && info.xirrPercentage.isNotBlank()) info.xirrPercentage else "--% XIRR"
        val gainColor = if (!isInfoValid) Color(0xFF94A3B8) else if (gainPct >= 0) Color(0xFF10B981) else Color(0xFFEF4444)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFF0D1424)))
                        .padding(14.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = "PORTFOLIO OS",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFD0FF00)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = widgetXirr,
                            style = TextStyle(
                                color = ColorProvider(if (isInfoValid) Color(0xFF10B981) else Color(0xFF94A3B8)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Privacy-First Valuation & Return Header
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(
                            text = "₹ • • • • • •",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF94A3B8)),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Text(
                            text = formattedGainPct,
                            style = TextStyle(
                                color = ColorProvider(gainColor),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "BEST PERFORMER",
                                style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 8.sp)
                            )
                            Text(
                                text = bestFund?.let { "${it.fundName.take(14)} (+${it.xirr}%)" } ?: "N/A",
                                style = TextStyle(color = ColorProvider(Color(0xFF10B981)), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "WORST PERFORMER",
                                style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 8.sp)
                            )
                            Text(
                                text = worstFund?.let { "${it.fundName.take(14)} (${it.xirr}%)" } ?: "N/A",
                                style = TextStyle(color = ColorProvider(Color(0xFFF59E0B)), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    Text(
                        text = "Valuation Hidden for Privacy · Tap to Open App",
                        style = TextStyle(color = ColorProvider(Color(0xFF00F0FF)), fontSize = 9.sp)
                    )
                }
            }
        }
    }
}

class PortfolioGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioGlanceWidget()
}
