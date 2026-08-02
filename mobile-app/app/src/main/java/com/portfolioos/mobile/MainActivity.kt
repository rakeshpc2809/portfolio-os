package com.portfolioos.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.ui.DashboardScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var snapshot by remember { mutableStateOf<SyncSnapshot?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            val scope = rememberCoroutineScope()

            fun fetchSyncSnapshot() {
                scope.launch {
                    isLoading = true
                    try {
                        snapshot = SyncApiClient.fetchSnapshotWithFallback()
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
                onRefresh = { fetchSyncSnapshot() }
            )
        }
    }
}
