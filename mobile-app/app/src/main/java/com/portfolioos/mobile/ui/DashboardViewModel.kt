package com.portfolioos.mobile.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "DashboardViewModel"
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            lastSyncMillis = SnapshotCacheManager.getLastSyncTimestamp(context),
            lastFullLedgerMillis = SnapshotCacheManager.getLastFullLedgerTimestamp(context),
            isAmfiFallback = SnapshotCacheManager.isAmfiFallback(context),
            isFullyOffline = SnapshotCacheManager.isFullyOffline(context),
            isBiometricLockEnabled = SnapshotCacheManager.isBiometricLockEnabled(context),
            isAppLocked = SnapshotCacheManager.isBiometricLockEnabled(context)
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadInitialCachedState()
    }

    fun loadInitialCachedState() {
        val cached = SnapshotCacheManager.loadSnapshot(context)
        _uiState.update { current ->
            current.copy(
                snapshot = cached,
                isLoading = (cached == null),
                lastSyncMillis = SnapshotCacheManager.getLastSyncTimestamp(context),
                lastFullLedgerMillis = SnapshotCacheManager.getLastFullLedgerTimestamp(context),
                isAmfiFallback = SnapshotCacheManager.isAmfiFallback(context),
                isFullyOffline = SnapshotCacheManager.isFullyOffline(context),
                isBiometricLockEnabled = SnapshotCacheManager.isBiometricLockEnabled(context)
            )
        }
    }

    fun fetchSyncSnapshot(isManualRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isManualRefresh) {
                _uiState.update { it.copy(isRefreshing = true) }
            } else if (_uiState.value.snapshot == null) {
                val cached = SnapshotCacheManager.loadSnapshot(context)
                _uiState.update { it.copy(snapshot = cached, isLoading = (cached == null)) }
            }

            try {
                val newSnapshot = withContext(Dispatchers.IO) {
                    SyncApiClient.fetchSnapshotWithFallback(context)
                }
                _uiState.update { current ->
                    current.copy(
                        snapshot = newSnapshot,
                        lastSyncMillis = SnapshotCacheManager.getLastSyncTimestamp(context),
                        lastFullLedgerMillis = SnapshotCacheManager.getLastFullLedgerTimestamp(context),
                        isAmfiFallback = SnapshotCacheManager.isAmfiFallback(context),
                        isFullyOffline = SnapshotCacheManager.isFullyOffline(context),
                        isLoading = false,
                        isRefreshing = false
                    )
                }
                fetchRestAnalytics()
            } catch (e: Exception) {
                Log.e(TAG, "Failed fetching snapshot: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    fun fetchRestAnalytics() {
        viewModelScope.launch(Dispatchers.IO) {
            val token = SnapshotCacheManager.getAuthToken(context)
            val urls = listOf(
                SyncApiClient.USB_BASE_URL,
                SyncApiClient.WIFI_BASE_URL,
                SyncApiClient.EMULATOR_BASE_URL
            )
            for (baseUrl in urls) {
                try {
                    val service = SyncApiClient.createService(baseUrl)
                    val bench = service.getBenchmarkAnalytics(token)
                    val fire = service.getFireSummary(token)
                    val overlap = service.getOverlapAnalytics(token)
                    _uiState.update { current ->
                        current.copy(
                            benchmarkData = bench,
                            fireSummaryData = fire,
                            overlapData = overlap
                        )
                    }
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "Rest analytics fetch failed on $baseUrl: ${e.message}")
                }
            }
        }
    }

    fun fetchOverlapAnalytics(includeUnverified: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = SnapshotCacheManager.getAuthToken(context)
            val urls = listOf(
                SyncApiClient.USB_BASE_URL,
                SyncApiClient.WIFI_BASE_URL,
                SyncApiClient.EMULATOR_BASE_URL
            )
            for (baseUrl in urls) {
                try {
                    val service = SyncApiClient.createService(baseUrl)
                    val overlap = service.getOverlapAnalytics(token, includeUnverified)
                    _uiState.update { it.copy(overlapData = overlap) }
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "Overlap analytics fetch failed on $baseUrl: ${e.message}")
                }
            }
        }
    }

    fun setActivePage(page: Int) {
        _uiState.update { it.copy(activePage = page) }
    }

    fun setAppLocked(locked: Boolean) {
        _uiState.update { it.copy(isAppLocked = locked) }
    }

    fun setSecurityEnrolled(enrolled: Boolean) {
        _uiState.update { it.copy(isSecurityEnrolled = enrolled) }
    }

    fun toggleBiometricLock(enabled: Boolean) {
        SnapshotCacheManager.setBiometricLockEnabled(context, enabled)
        _uiState.update { it.copy(isBiometricLockEnabled = enabled, isAppLocked = enabled) }
    }

    fun updateCustomUrl(newUrl: String) {
        SnapshotCacheManager.setCustomUrl(context, newUrl)
        fetchSyncSnapshot(isManualRefresh = true)
    }

    fun updateAuthToken(newToken: String) {
        SnapshotCacheManager.setAuthToken(context, newToken)
        fetchSyncSnapshot(isManualRefresh = true)
    }

    // Debug Simulations
    fun simulateFullSync() {
        val snap = _uiState.value.snapshot ?: return
        val now = System.currentTimeMillis()
        SnapshotCacheManager.saveSnapshot(context, snap, isFullLedgerSync = true)
        SnapshotCacheManager.setFullyOffline(context, false)
        _uiState.update {
            it.copy(
                lastSyncMillis = now,
                lastFullLedgerMillis = now,
                isAmfiFallback = false,
                isFullyOffline = false
            )
        }
    }

    fun simulateAmfiFallback() {
        val snap = _uiState.value.snapshot ?: return
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 172800000L
        SnapshotCacheManager.saveSnapshot(context, snap, isFullLedgerSync = false)
        SnapshotCacheManager.setFullyOffline(context, false)
        _uiState.update {
            it.copy(
                lastSyncMillis = now,
                lastFullLedgerMillis = twoDaysAgo,
                isAmfiFallback = true,
                isFullyOffline = false
            )
        }
    }

    fun simulateFullyOffline() {
        if (_uiState.value.snapshot == null) return
        val newStatus = !_uiState.value.isFullyOffline
        SnapshotCacheManager.setFullyOffline(context, newStatus)
        _uiState.update { it.copy(isFullyOffline = newStatus) }
    }

    fun simulateAgedOffline() {
        if (_uiState.value.snapshot == null) return
        val agedTime = System.currentTimeMillis() - 660000L // 11 mins ago
        SnapshotCacheManager.setFullyOffline(context, true)
        _uiState.update {
            it.copy(
                lastSyncMillis = agedTime,
                lastFullLedgerMillis = agedTime,
                isAmfiFallback = false,
                isFullyOffline = true
            )
        }
    }

    fun simulateRefreshing() {
        _uiState.update { it.copy(isRefreshing = !it.isRefreshing) }
    }
}
