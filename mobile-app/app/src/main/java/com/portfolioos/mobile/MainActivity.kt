package com.portfolioos.mobile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.auth.BiometricAuthManager
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.ui.DashboardScreen
import com.portfolioos.mobile.ui.LockScreenGate
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val TAG = "MainActivity"
    private val activePage = mutableStateOf(0)
    
    private val isAppLockedState = mutableStateOf(false)
    private val isSecurityEnrolledState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activePage.value = intent.getIntExtra("TARGET_PAGE", 0)

        val initialLockEnabled = SnapshotCacheManager.isBiometricLockEnabled(applicationContext)
        isAppLockedState.value = initialLockEnabled

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                if (BiometricAuthManager.isAuthPromptShowing) {
                    Log.d(TAG, "ON_STOP fired while BiometricPrompt is showing. Suppressing re-lock loop.")
                } else if (SnapshotCacheManager.isBiometricLockEnabled(applicationContext)) {
                    Log.d(TAG, "App backgrounded (ON_STOP). Re-locking app.")
                    isAppLockedState.value = true
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                Log.d(TAG, "App resumed (ON_START). Checking security status.")
                val status = BiometricAuthManager.checkSecurityStatus(this@MainActivity)
                isSecurityEnrolledState.value = (status != BiometricAuthManager.SecurityStatus.NONE_ENROLLED)
                
                if (isAppLockedState.value && SnapshotCacheManager.isBiometricLockEnabled(applicationContext)) {
                    triggerBiometricUnlock()
                }
            }
        })

        setContent {
            val page by activePage
            var isAppLocked by remember { isAppLockedState }
            var isSecurityEnrolled by remember { isSecurityEnrolledState }
            var isBiometricLockEnabled by remember { mutableStateOf(SnapshotCacheManager.isBiometricLockEnabled(applicationContext)) }

            var snapshot by remember { mutableStateOf<SyncSnapshot?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            var isRefreshing by remember { mutableStateOf(false) }
            val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
            
            var lastSyncMillis by remember { mutableLongStateOf(SnapshotCacheManager.getLastSyncTimestamp(applicationContext)) }
            var lastFullLedgerMillis by remember { mutableLongStateOf(SnapshotCacheManager.getLastFullLedgerTimestamp(applicationContext)) }
            var isAmfiFallback by remember { mutableStateOf(SnapshotCacheManager.isAmfiFallback(applicationContext)) }
            var isFullyOffline by remember { mutableStateOf(SnapshotCacheManager.isFullyOffline(applicationContext)) }
            
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
                        isFullyOffline = SnapshotCacheManager.isFullyOffline(applicationContext)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (snapshot != null) {
                            isFullyOffline = true
                            SnapshotCacheManager.setFullyOffline(applicationContext, true)
                            if (isManualRefresh) {
                                snackbarHostState.showSnackbar(
                                    message = "Sync failed — showing cached data",
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                            }
                        }
                    } finally {
                        isLoading = false
                        isRefreshing = false
                    }
                }
            }

            LaunchedEffect(isAppLocked, isBiometricLockEnabled) {
                if (!isAppLocked || !isBiometricLockEnabled) {
                    fetchSyncSnapshot(isManualRefresh = false)
                }
            }

            if (isAppLocked && isBiometricLockEnabled) {
                LockScreenGate(
                    isSecurityEnrolled = isSecurityEnrolled,
                    onAuthenticate = { triggerBiometricUnlock() },
                    onRecheckSecurity = { triggerBiometricUnlock() }
                )
            } else {
                DashboardScreen(
                    snapshot = snapshot,
                    isLoading = isLoading,
                    isRefreshing = isRefreshing,
                    lastSyncMillis = lastSyncMillis,
                    lastFullLedgerMillis = lastFullLedgerMillis,
                    isAmfiFallback = isAmfiFallback,
                    isFullyOffline = isFullyOffline,
                    snackbarHostState = snackbarHostState,
                    initialPage = page,
                    onRefresh = { fetchSyncSnapshot(isManualRefresh = true) },
                    onUpdateCustomUrl = { newUrl ->
                        SnapshotCacheManager.setCustomUrl(applicationContext, newUrl)
                        fetchSyncSnapshot(isManualRefresh = true)
                    },
                    onSimulateFullSync = {
                        if (snapshot != null) {
                            SnapshotCacheManager.saveSnapshot(applicationContext, snapshot!!, isFullLedgerSync = true)
                            lastSyncMillis = System.currentTimeMillis()
                            lastFullLedgerMillis = System.currentTimeMillis()
                            isAmfiFallback = false
                            isFullyOffline = false
                            SnapshotCacheManager.setFullyOffline(applicationContext, false)
                        }
                    },
                    onSimulateAmfiFallback = {
                        if (snapshot != null) {
                            SnapshotCacheManager.saveSnapshot(applicationContext, snapshot!!, isFullLedgerSync = false)
                            lastSyncMillis = System.currentTimeMillis()
                            lastFullLedgerMillis = System.currentTimeMillis() - 172800000L // 2 days ago
                            isAmfiFallback = true
                            isFullyOffline = false
                            SnapshotCacheManager.setFullyOffline(applicationContext, false)
                        }
                    },
                    onSimulateFullyOffline = {
                        if (snapshot != null) {
                            isFullyOffline = !isFullyOffline
                            SnapshotCacheManager.setFullyOffline(applicationContext, isFullyOffline)
                        }
                    },
                    onSimulateAgedOffline = {
                        if (snapshot != null) {
                            val agedTime = System.currentTimeMillis() - 660000L // 11 mins ago
                            lastSyncMillis = agedTime
                            lastFullLedgerMillis = agedTime
                            isAmfiFallback = false
                            isFullyOffline = true
                            SnapshotCacheManager.setFullyOffline(applicationContext, true)
                        }
                    },
                    onSimulateRefreshing = {
                        isRefreshing = !isRefreshing
                    },
                    onSimulateSyncFailure = {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Sync failed — showing cached data",
                                duration = androidx.compose.material3.SnackbarDuration.Indefinite
                            )
                        }
                    },
                    isBiometricLockEnabled = isBiometricLockEnabled,
                    onToggleBiometricLock = { enabled ->
                        isBiometricLockEnabled = enabled
                        SnapshotCacheManager.setBiometricLockEnabled(applicationContext, enabled)
                        if (enabled) {
                            isAppLockedState.value = true
                            triggerBiometricUnlock()
                        } else {
                            isAppLockedState.value = false
                        }
                    }
                )
            }
        }
    }

    private fun triggerBiometricUnlock() {
        val status = BiometricAuthManager.checkSecurityStatus(this)
        Log.d(TAG, "triggerBiometricUnlock checked security status: $status")
        if (status == BiometricAuthManager.SecurityStatus.NONE_ENROLLED) {
            isSecurityEnrolledState.value = false
            return
        }
        isSecurityEnrolledState.value = true
        BiometricAuthManager.showBiometricPrompt(
            activity = this,
            onAuthSuccess = {
                Log.d(TAG, "Biometric auth success callback received. Unlocking app.")
                isAppLockedState.value = false
            },
            onAuthError = { err ->
                Log.d(TAG, "Biometric auth error/cancel callback received: $err")
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activePage.value = intent.getIntExtra("TARGET_PAGE", 0)
    }
}
