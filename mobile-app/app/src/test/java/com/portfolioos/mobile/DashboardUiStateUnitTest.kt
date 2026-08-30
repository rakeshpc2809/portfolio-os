package com.portfolioos.mobile

import com.portfolioos.mobile.model.SyncInfoDto
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.ui.DashboardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUiStateUnitTest {

    @Test
    fun testDashboardUiStateInitialDefaults() {
        val state = DashboardUiState()
        assertNull(state.snapshot)
        assertTrue(state.isLoading)
        assertFalse(state.isRefreshing)
        assertEquals(0L, state.lastSyncMillis)
        assertEquals(0L, state.lastFullLedgerMillis)
        assertFalse(state.isAmfiFallback)
        assertFalse(state.isFullyOffline)
        assertFalse(state.isAppLocked)
        assertTrue(state.isSecurityEnrolled)
        assertTrue(state.isBiometricLockEnabled)
        assertEquals(0, state.activePage)
        assertNull(state.benchmarkData)
        assertNull(state.fireSummaryData)
        assertNull(state.overlapData)
    }

    @Test
    fun testDashboardUiStateCopyPreservesImmutability() {
        val initial = DashboardUiState()
        val snapshot = SyncSnapshot(
            syncInfo = SyncInfoDto(
                timestamp = 1788000000000L,
                generatedAt = "2026-08-30T00:00:00Z",
                fiscalYear = "2026-27"
            ),
            holdings = emptyList(),
            taxLots = emptyList(),
            radarSignals = emptyList(),
            netWorthHistory = emptyList(),
            rebalancePlan = null
        )

        val updated = initial.copy(
            snapshot = snapshot,
            isLoading = false,
            lastSyncMillis = 1788000000000L,
            activePage = 2
        )

        assertTrue(initial.isLoading)
        assertNull(initial.snapshot)
        assertEquals(0, initial.activePage)

        assertFalse(updated.isLoading)
        assertEquals(snapshot, updated.snapshot)
        assertEquals(1788000000000L, updated.lastSyncMillis)
        assertEquals(2, updated.activePage)
    }

    @Test
    fun testOfflineFallbackSurvivalInUiState() {
        val fallbackSnap = SyncSnapshot(
            syncInfo = SyncInfoDto(
                timestamp = 1788000000000L,
                generatedAt = "OFFLINE_FALLBACK",
                fiscalYear = "2026-27"
            ),
            holdings = emptyList(),
            taxLots = emptyList(),
            radarSignals = emptyList(),
            netWorthHistory = emptyList(),
            rebalancePlan = null
        )

        val state = DashboardUiState(
            snapshot = fallbackSnap,
            isLoading = false,
            isFullyOffline = true
        )

        val isSyncPopulated = state.snapshot?.syncInfo?.generatedAt?.let {
            it.isNotBlank() && it != "OFFLINE_FALLBACK" && it != "AMFI_NAV_FALLBACK"
        } == true

        assertFalse("OFFLINE_FALLBACK must evaluate isSyncPopulated to false", isSyncPopulated)
        assertTrue(state.isFullyOffline)
    }
}
