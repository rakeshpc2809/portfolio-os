package com.portfolioos.mobile.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import com.portfolioos.mobile.worker.PortfolioSyncWorker

class PortfolioGlanceWidget : GlanceAppWidget() {

    enum class WidgetStatus(
        val label: String,
        val backgroundColor: Color,
        val textColor: Color
    ) {
        REFRESHING("REFRESHING", Color(0xFF1E3A8A), Color(0xFF93C5FD)),
        BALANCED("● BALANCED", Color(0xFF064E3B), Color(0xFF6EE7B7)),
        NEEDS_REBALANCE("▲ REBALANCE", Color(0xFF78350F), Color(0xFFFCD34D)),
        OFFLINE("OFFLINE", Color(0xFF334155), Color(0xFF94A3B8)),
        DISCONNECTED("DISCONNECTED", Color(0xFF450A0A), Color(0xFFFCA5A5))
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = SnapshotCacheManager.loadSnapshot(context)
        val info = snapshot?.syncInfo
        val holdings = snapshot?.holdings ?: emptyList()
        val isMasked = SnapshotCacheManager.isValuationMasked(context)
        val statusOverride = SnapshotCacheManager.getWidgetStatusOverride(context)

        val lastSync = SnapshotCacheManager.getLastSyncTimestamp(context)
        val elapsedMillis = if (lastSync > 0L) System.currentTimeMillis() - lastSync else Long.MAX_VALUE
        val elapsedHours = elapsedMillis / (1000L * 60L * 60L)

        // Status Determination with Strict Offline Safeguard:
        // Under no circumstances evaluate drift or report BALANCED on a disconnected/stale cache!
        val status = when {
            statusOverride == "REFRESHING" -> WidgetStatus.REFRESHING
            elapsedHours >= 36 -> WidgetStatus.DISCONNECTED
            SnapshotCacheManager.isFullyOffline(context) || info == null || info.generatedAt == "OFFLINE_FALLBACK" -> WidgetStatus.OFFLINE
            else -> {
                val hasRebalanceTrigger = snapshot.rebalancePlan?.trigger != null &&
                        snapshot.rebalancePlan.trigger.type.isNotBlank() &&
                        snapshot.rebalancePlan.trigger.type != "NONE"
                val hasSoldLots = snapshot.rebalancePlan?.sellSide?.waterfall?.any { it.sold > 0.0 } == true
                val hasStaleNav = info.hasStaleNav
                if (hasRebalanceTrigger || hasSoldLots || hasStaleNav) {
                    WidgetStatus.NEEDS_REBALANCE
                } else {
                    WidgetStatus.BALANCED
                }
            }
        }

        val bestFund = holdings.maxByOrNull { it.xirr }
        val worstFund = holdings.minByOrNull { it.xirr }

        val isInfoValid = info != null && info.generatedAt.isNotBlank() &&
                info.generatedAt != "OFFLINE_FALLBACK" && info.totalInvested > 0.0

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

        val widgetXirr = if (isInfoValid && info != null && info.xirrPercentage.isNotBlank()) {
            info.xirrPercentage
        } else {
            "--% XIRR"
        }

        val valuationDisplay = when {
            isMasked -> "₹ • • • • • •"
            isInfoValid && info != null -> {
                if (info.formattedCurrentValue.isNotBlank()) {
                    info.formattedCurrentValue
                } else {
                    String.format("₹ %.2f L", info.currentValue / 100000.0)
                }
            }
            else -> "₹ --.-- L"
        }

        val gainColor = when {
            !isInfoValid -> Color(0xFF94A3B8)
            gainPct >= 0 -> Color(0xFF10B981)
            else -> Color(0xFFEF4444)
        }

        val targetPageParam = ActionParameters.Key<Int>("TARGET_PAGE")
        val openRebalanceAction = actionStartActivity<MainActivity>(actionParametersOf(targetPageParam to 2))
        val openDashboardAction = actionStartActivity<MainActivity>()

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFF0D1424)))
                        .padding(12.dp)
                        .clickable(openDashboardAction),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    // Header Row: Brand + Status Pill + XIRR
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = "PORTFOLIO OS",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFD0FF00)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        // Interactive Status Pill: Tap opens Rebalance Waterfall
                        Box(
                            modifier = GlanceModifier
                                .background(ColorProvider(status.backgroundColor))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clickable(openRebalanceAction)
                        ) {
                            Text(
                                text = status.label,
                                style = TextStyle(
                                    color = ColorProvider(status.textColor),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        Text(
                            text = widgetXirr,
                            style = TextStyle(
                                color = ColorProvider(if (isInfoValid) Color(0xFF10B981) else Color(0xFF94A3B8)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Valuation Row: Masked/Unmasked Value (Tap to toggle) + Gain Pct
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionRunCallback<ToggleMaskActionCallback>()),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = valuationDisplay,
                            style = TextStyle(
                                color = ColorProvider(if (isMasked) Color(0xFF94A3B8) else Color.White),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        Text(
                            text = formattedGainPct,
                            style = TextStyle(
                                color = ColorProvider(gainColor),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        // Mask toggle hint button
                        Box(
                            modifier = GlanceModifier
                                .background(ColorProvider(Color(0xFF1E293B)))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clickable(actionRunCallback<ToggleMaskActionCallback>())
                        ) {
                            Text(
                                text = if (isMasked) "SHOW" else "HIDE",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFF00F0FF)),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Context / Safeguard Message
                    if (status == WidgetStatus.DISCONNECTED) {
                        Text(
                            text = "⚠ Snapshot > 36h old. Connect to Wi-Fi/Tailscale to refresh.",
                            style = TextStyle(color = ColorProvider(Color(0xFFFCA5A5)), fontSize = 8.sp)
                        )
                    } else if (status == WidgetStatus.OFFLINE) {
                        Text(
                            text = "Cached data · Drift paused until next sync",
                            style = TextStyle(color = ColorProvider(Color(0xFF64748B)), fontSize = 8.sp)
                        )
                    } else {
                        // Holdings High/Low Summary
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = "TOP PERFORMER",
                                    style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 8.sp)
                                )
                                Text(
                                    text = bestFund?.let { "${it.fundName.take(13)} (+${it.xirr}%)" } ?: "N/A",
                                    style = TextStyle(color = ColorProvider(Color(0xFF10B981)), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                )
                            }

                            Spacer(modifier = GlanceModifier.width(6.dp))

                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = "LAGGING",
                                    style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 8.sp)
                                )
                                Text(
                                    text = worstFund?.let { "${it.fundName.take(13)} (${it.xirr}%)" } ?: "N/A",
                                    style = TextStyle(color = ColorProvider(Color(0xFFF59E0B)), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    // Bottom Row: Action bar (Refresh + Deep link note)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = if (isMasked) "Tap value to unmask" else "Tap value to mask",
                            style = TextStyle(color = ColorProvider(Color(0xFF64748B)), fontSize = 8.sp)
                        )

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        // Refresh button
                        Box(
                            modifier = GlanceModifier
                                .background(ColorProvider(Color(0xFF1E293B)))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clickable(actionRunCallback<RefreshWidgetActionCallback>())
                        ) {
                            Text(
                                text = "REFRESH ⟳",
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFD0FF00)),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

class ToggleMaskActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SnapshotCacheManager.toggleValuationMask(context)
        PortfolioGlanceWidget().update(context, glanceId)
    }
}

class RefreshWidgetActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PortfolioSyncWorker.runOneTimeSync(context)
    }
}

class PortfolioGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PortfolioGlanceWidget()
}
