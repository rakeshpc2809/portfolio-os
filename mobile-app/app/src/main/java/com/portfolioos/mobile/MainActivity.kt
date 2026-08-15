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
            var snapshot by remember { mutableStateOf<SyncSnapshot?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()

            fun fetchSyncSnapshot() {
                scope.launch {
                    isLoading = true
                    try {
                        snapshot = SyncApiClient.fetchSnapshotWithFallback(applicationContext)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }

            LaunchedEffect(Unit) {
                fetchSyncSnapshot()
            }

            DashboardScreen(
                snapshot = snapshot,
                isLoading = isLoading,
                initialPage = activePage.value,
                onRefresh = { fetchSyncSnapshot() },
                onUpdateCustomUrl = { newUrl ->
                    SnapshotCacheManager.setCustomUrl(applicationContext, newUrl)
                    fetchSyncSnapshot()
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
