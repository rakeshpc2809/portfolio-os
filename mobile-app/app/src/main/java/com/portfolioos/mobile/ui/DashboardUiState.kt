package com.portfolioos.mobile.ui

import com.portfolioos.mobile.model.BenchmarkAnalyticsDto
import com.portfolioos.mobile.model.FireSummaryResponseDto
import com.portfolioos.mobile.model.OverlapReportDto
import com.portfolioos.mobile.model.SyncSnapshot

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
)
