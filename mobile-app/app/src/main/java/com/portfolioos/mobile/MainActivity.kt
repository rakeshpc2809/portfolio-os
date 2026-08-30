package com.portfolioos.mobile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.portfolioos.mobile.auth.BiometricAuthManager
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.ui.DashboardScreen
import com.portfolioos.mobile.ui.DashboardViewModel
import com.portfolioos.mobile.ui.LockScreenGate
import com.portfolioos.mobile.ui.theme.PortfolioOSTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val TAG = "MainActivity"
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialPage = intent.getIntExtra("TARGET_PAGE", 0)
        viewModel.setActivePage(initialPage)

        val disableLockExtra = intent.getBooleanExtra("DISABLE_LOCK", false)
        if (disableLockExtra) {
            SnapshotCacheManager.setBiometricLockEnabled(applicationContext, false)
            viewModel.setAppLocked(false)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                super.onStop(owner)
                if (BiometricAuthManager.isAuthPromptShowing) {
                    Log.d(TAG, "ON_STOP fired while BiometricPrompt is showing. Suppressing re-lock loop.")
                } else if (SnapshotCacheManager.isBiometricLockEnabled(applicationContext)) {
                    Log.d(TAG, "App backgrounded (ON_STOP). Re-locking app.")
                    viewModel.setAppLocked(true)
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                Log.d(TAG, "App resumed (ON_START). Checking security status.")
                val status = BiometricAuthManager.checkSecurityStatus(this@MainActivity)
                val isEnrolled = (status != BiometricAuthManager.SecurityStatus.NONE_ENROLLED)
                viewModel.setSecurityEnrolled(isEnrolled)

                if (viewModel.uiState.value.isAppLocked && SnapshotCacheManager.isBiometricLockEnabled(applicationContext)) {
                    triggerBiometricUnlock()
                }
            }
        })

        setContent {
            PortfolioOSTheme {
                val uiState by viewModel.uiState.collectAsState()
                val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
                val scope = rememberCoroutineScope()

                LaunchedEffect(uiState.isAppLocked, uiState.isBiometricLockEnabled) {
                    if (!uiState.isAppLocked || !uiState.isBiometricLockEnabled) {
                        viewModel.fetchSyncSnapshot(isManualRefresh = false)
                    }
                }

                if (uiState.isAppLocked && uiState.isBiometricLockEnabled) {
                    LockScreenGate(
                        isSecurityEnrolled = uiState.isSecurityEnrolled,
                        onAuthenticate = { triggerBiometricUnlock() },
                        onRecheckSecurity = { triggerBiometricUnlock() }
                    )
                } else {
                    DashboardScreen(
                        uiState = uiState,
                        snackbarHostState = snackbarHostState,
                        onRefresh = { viewModel.fetchSyncSnapshot(isManualRefresh = true) },
                        onUpdateCustomUrl = { newUrl -> viewModel.updateCustomUrl(newUrl) },
                        onSimulateFullSync = { viewModel.simulateFullSync() },
                        onSimulateAmfiFallback = { viewModel.simulateAmfiFallback() },
                        onSimulateFullyOffline = { viewModel.simulateFullyOffline() },
                        onSimulateAgedOffline = { viewModel.simulateAgedOffline() },
                        onSimulateRefreshing = { viewModel.simulateRefreshing() },
                        onSimulateSyncFailure = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Sync failed — showing cached data",
                                    duration = androidx.compose.material3.SnackbarDuration.Indefinite
                                )
                            }
                        },
                        onToggleBiometricLock = { enabled ->
                            viewModel.toggleBiometricLock(enabled)
                            if (enabled) {
                                triggerBiometricUnlock()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun triggerBiometricUnlock() {
        if (intent.getBooleanExtra("DISABLE_LOCK", false)) {
            viewModel.setAppLocked(false)
            return
        }
        val status = BiometricAuthManager.checkSecurityStatus(this)
        Log.d(TAG, "triggerBiometricUnlock checked security status: $status")
        if (status == BiometricAuthManager.SecurityStatus.NONE_ENROLLED) {
            viewModel.setSecurityEnrolled(false)
            return
        }
        viewModel.setSecurityEnrolled(true)
        BiometricAuthManager.showBiometricPrompt(
            activity = this,
            onAuthSuccess = {
                Log.d(TAG, "Biometric auth success callback received. Unlocking app.")
                viewModel.setAppLocked(false)
            },
            onAuthError = { err ->
                Log.d(TAG, "Biometric auth error/cancel callback received: $err")
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val targetPage = intent.getIntExtra("TARGET_PAGE", 0)
        viewModel.setActivePage(targetPage)
    }
}
