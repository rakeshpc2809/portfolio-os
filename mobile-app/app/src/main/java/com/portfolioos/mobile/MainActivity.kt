package com.portfolioos.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.ui.DashboardScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val activePage = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activePage.value = intent.getIntExtra("TARGET_PAGE", 0)

        setContent {
            val page by activePage
            var snapshot by remember { mutableStateOf<SyncSnapshot?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            var isRefreshing by remember { mutableStateOf(false) }
            val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
            
            var lastSyncMillis by remember { mutableLongStateOf(SnapshotCacheManager.getLastSyncTimestamp(applicationContext)) }
            var lastFullLedgerMillis by remember { mutableLongStateOf(SnapshotCacheManager.getLastFullLedgerTimestamp(applicationContext)) }
            var isAmfiFallback by remember { mutableStateOf(SnapshotCacheManager.isAmfiFallback(applicationContext)) }
            
            val scope = rememberCoroutineScope()

            fun fetchSyncSnapshot(isManualRefresh: Boolean = false) {
                scope.launch {
                    if (isManualRefresh) {
                        isRefreshing = true
                    } else if (snapshot == null) {
                        isLoading = true
                    }
                    try {
                        val newSnapshot = SyncApiClient.fetchSnapshotWithFallback(applicationContext)
                        snapshot = newSnapshot
                        lastSyncMillis = SnapshotCacheManager.getLastSyncTimestamp(applicationContext)
                        lastFullLedgerMillis = SnapshotCacheManager.getLastFullLedgerTimestamp(applicationContext)
                        isAmfiFallback = SnapshotCacheManager.isAmfiFallback(applicationContext)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (snapshot != null && isManualRefresh) {
                            snackbarHostState.showSnackbar("Sync failed — showing cached data")
                        }
                    } finally {
                        isLoading = false
                        isRefreshing = false
                    }
                }
            }

            LaunchedEffect(Unit) {
                fetchSyncSnapshot(isManualRefresh = false)
            }

            DashboardScreen(
                snapshot = snapshot,
                isLoading = isLoading,
                isRefreshing = isRefreshing,
                lastSyncMillis = lastSyncMillis,
                lastFullLedgerMillis = lastFullLedgerMillis,
                isAmfiFallback = isAmfiFallback,
                snackbarHostState = snackbarHostState,
                initialPage = page,
                onRefresh = { fetchSyncSnapshot(isManualRefresh = true) },
                onUpdateCustomUrl = { newUrl ->
                    SnapshotCacheManager.setCustomUrl(applicationContext, newUrl)
                    fetchSyncSnapshot(isManualRefresh = true)
                },
                onSimulateAmfiFallback = {
                    if (snapshot != null) {
                        SnapshotCacheManager.saveSnapshot(applicationContext, snapshot!!, isFullLedgerSync = false)
                        lastSyncMillis = System.currentTimeMillis()
                        lastFullLedgerMillis = System.currentTimeMillis() - 172800000L // 2 days ago
                        isAmfiFallback = true
                    }
                },
                onSimulateSyncFailure = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Sync failed — showing cached data")
                    }
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activePage.value = intent.getIntExtra("TARGET_PAGE", 0)
    }
}
