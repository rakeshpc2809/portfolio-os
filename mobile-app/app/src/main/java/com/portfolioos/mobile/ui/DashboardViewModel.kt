package com.portfolioos.mobile.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
            isAppLocked = SnapshotCacheManager.isBiometricLockEnabled(context),
            isQuietModeEnabled = SnapshotCacheManager.isQuietModeEnabled(context)
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
                isBiometricLockEnabled = SnapshotCacheManager.isBiometricLockEnabled(context),
                isQuietModeEnabled = SnapshotCacheManager.isQuietModeEnabled(context)
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
            val urls = SyncApiClient.getCandidateBaseUrls(context)
            for (baseUrl in urls) {
                try {
                    val service = SyncApiClient.createService(baseUrl)
                    coroutineScope {
                        val benchDeferred = async { runCatching { service.getBenchmarkAnalytics(token) }.getOrNull() }
                        val fireDeferred = async { runCatching { service.getFireSummary(token) }.getOrNull() }
                        val overlapDeferred = async { runCatching { service.getOverlapAnalytics(token) }.getOrNull() }
                        val actionDeferred = async { runCatching { service.getActionRecommendations(token) }.getOrNull() }

                        val bench = benchDeferred.await()
                        val fire = fireDeferred.await()
                        val overlap = overlapDeferred.await()
                        val recs = actionDeferred.await()
                        if (bench != null || fire != null || overlap != null || recs != null) {
                            _uiState.update { state ->
                                state.copy(
                                    benchmarkData = bench ?: state.benchmarkData,
                                    fireSummaryData = fire ?: state.fireSummaryData,
                                    overlapData = overlap ?: state.overlapData,
                                    actionRecommendations = recs ?: state.actionRecommendations
                                )
                            }
                            return@coroutineScope true
                        }
                        false
                    }.let { anySucceeded ->
                        if (anySucceeded) return@launch
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Rest analytics fetch failed on $baseUrl: ${e.message}")
                }
            }
        }
    }

    fun fetchOverlapAnalytics(includeUnverified: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = SnapshotCacheManager.getAuthToken(context)
            val urls = SyncApiClient.getCandidateBaseUrls(context)
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

    fun toggleQuietMode(enabled: Boolean) {
        SnapshotCacheManager.setQuietModeEnabled(context, enabled)
        _uiState.update { it.copy(isQuietModeEnabled = enabled) }
    }

    fun updateCustomUrl(newUrl: String) {
        SnapshotCacheManager.setCustomUrl(context, newUrl)
        fetchSyncSnapshot(isManualRefresh = true)
    }

    fun updateAuthToken(newToken: String) {
        SnapshotCacheManager.setAuthToken(context, newToken)
        fetchSyncSnapshot(isManualRefresh = true)
    }
}
