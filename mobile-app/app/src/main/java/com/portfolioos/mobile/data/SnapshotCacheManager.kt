package com.portfolioos.mobile.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.portfolioos.mobile.model.SyncInfoDto
import com.portfolioos.mobile.model.SyncSnapshot

object SnapshotCacheManager {
    private const val PREF_NAME = "portfolio_os_snapshot_cache"
    private const val KEY_SNAPSHOT_JSON = "key_snapshot_json"
    private const val KEY_LAST_SYNC_TS = "key_last_sync_ts"
    private const val KEY_LAST_FULL_LEDGER_TS = "key_last_full_ledger_ts"
    private const val KEY_IS_AMFI_FALLBACK = "key_is_amfi_fallback"
    private const val KEY_IS_FULLY_OFFLINE = "key_is_fully_offline"
    private const val KEY_BIOMETRIC_LOCK = "key_biometric_lock"
    private const val KEY_CUSTOM_URL = "key_custom_url"
    private const val KEY_AUTH_TOKEN = "key_auth_token"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isBiometricLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BIOMETRIC_LOCK, true)
    }

    fun setBiometricLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
    }

    fun getLastSyncTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_SYNC_TS, 0L)
    }

    fun getLastFullLedgerTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_FULL_LEDGER_TS, 0L)
    }

    fun isAmfiFallback(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_AMFI_FALLBACK, false)
    }

    fun isFullyOffline(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_FULLY_OFFLINE, false)
    }

    fun setFullyOffline(context: Context, isOffline: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_FULLY_OFFLINE, isOffline).apply()
    }

    fun getCustomUrl(context: Context): String? {
        return getPrefs(context).getString(KEY_CUSTOM_URL, null)
    }

    fun setCustomUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_URL, url).apply()
    }

    fun getAuthToken(context: Context): String {
        return getPrefs(context).getString(KEY_AUTH_TOKEN, com.portfolioos.mobile.BuildConfig.DEFAULT_AUTH_TOKEN) ?: com.portfolioos.mobile.BuildConfig.DEFAULT_AUTH_TOKEN
    }

    fun loadSnapshot(context: Context): SyncSnapshot? {
        val json = getPrefs(context).getString(KEY_SNAPSHOT_JSON, null) ?: return null
        return try {
            Gson().fromJson(json, SyncSnapshot::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveSnapshot(context: Context, snapshot: SyncSnapshot, isFullLedgerSync: Boolean) {
        val now = System.currentTimeMillis()
        val editor = getPrefs(context).edit()
        editor.putString(KEY_SNAPSHOT_JSON, Gson().toJson(snapshot))
        editor.putLong(KEY_LAST_SYNC_TS, now)
        if (isFullLedgerSync) {
            editor.putLong(KEY_LAST_FULL_LEDGER_TS, now)
            editor.putBoolean(KEY_IS_AMFI_FALLBACK, false)
            editor.putBoolean(KEY_IS_FULLY_OFFLINE, false)
        }
        editor.apply()
    }

    fun createDefaultFallbackSnapshot(): SyncSnapshot {
        return SyncSnapshot(
            syncInfo = SyncInfoDto(
                timestamp = System.currentTimeMillis(),
                generatedAt = "OFFLINE_FALLBACK",
                fiscalYear = "2026-27"
            ),
            holdings = emptyList(),
            taxLots = emptyList(),
            radarSignals = emptyList(),
            netWorthHistory = emptyList(),
            rebalancePlan = null
        )
    }
}


