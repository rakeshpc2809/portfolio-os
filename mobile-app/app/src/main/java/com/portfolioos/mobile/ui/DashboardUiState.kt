package com.portfolioos.mobile.ui

import com.portfolioos.mobile.model.BenchmarkAnalyticsDto
import com.portfolioos.mobile.model.FireSummaryResponseDto
import com.portfolioos.mobile.model.OverlapReportDto
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.util.formatInr

data class DashboardUiState(
    val snapshot: SyncSnapshot? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val lastSyncMillis: Long = 0L,
    val lastFullLedgerMillis: Long = 0L,
    val isAmfiFallback: Boolean = false,
    val isFullyOffline: Boolean = false,
    val isAppLocked: Boolean = false,
    val isSecurityEnrolled: Boolean = true,
    val isBiometricLockEnabled: Boolean = true,
    val activePage: Int = 0,
    val benchmarkData: BenchmarkAnalyticsDto? = null,
    val fireSummaryData: FireSummaryResponseDto? = null,
    val overlapData: OverlapReportDto? = null
) {
    val isMasked: Boolean
        get() = isAppLocked && isBiometricLockEnabled

    val displayNetWorth: String
        get() {
            if (isMasked) return "••••••"
            val valNum = snapshot?.syncInfo?.currentValue ?: return "₹ --"
            return formatInr(valNum)
        }

    val displayUnrealizedGain: String
        get() {
            if (isMasked) return "••••••"
            val gain = snapshot?.syncInfo?.unrealizedGain ?: return "₹ --"
            return formatInr(gain)
        }

    val displayTotalInvested: String
        get() {
            if (isMasked) return "••••••"
            val inv = snapshot?.syncInfo?.totalInvested ?: return "₹ --"
            return formatInr(inv)
        }
}
