package com.portfolioos.mobile.auth

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthManager {
    private const val TAG = "BiometricAuthManager"

    @Volatile
    var isAuthPromptShowing: Boolean = false
        private set

    enum class SecurityStatus {
        SUCCESS,
        NONE_ENROLLED,
        UNAVAILABLE
    }

    fun checkSecurityStatus(activity: FragmentActivity): SecurityStatus {
        val biometricManager = BiometricManager.from(activity)
        val authenticators = Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> SecurityStatus.SUCCESS
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> SecurityStatus.NONE_ENROLLED
            else -> SecurityStatus.UNAVAILABLE
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        isAuthPromptShowing = true
        Log.d(TAG, "BiometricPrompt requested. Setting isAuthPromptShowing = true")

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isAuthPromptShowing = false
                Log.d(TAG, "Authentication Succeeded! Resetting isAuthPromptShowing = false")
                onAuthSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                isAuthPromptShowing = false
                Log.d(TAG, "Authentication Error ($errorCode): $errString. Resetting isAuthPromptShowing = false")
                onAuthError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.d(TAG, "Authentication Failed (retry attempt)")
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Portfolio OS")
            .setSubtitle("Authenticate using fingerprint or device PIN")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}
