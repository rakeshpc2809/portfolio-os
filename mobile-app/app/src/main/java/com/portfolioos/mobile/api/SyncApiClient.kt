package com.portfolioos.mobile.api

import android.content.Context
import com.portfolioos.mobile.BuildConfig
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.model.SyncSnapshot
import com.portfolioos.mobile.model.TradeSimulationRequestDto
import com.portfolioos.mobile.model.TradeSimulationResultDto
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SyncApiService {
    @GET("api/v1/sync/snapshot")
    suspend fun getSnapshot(
        @Header("X-Api-Auth-Token") token: String,
        @Query("fy") fiscalYear: String = "2026-27"
    ): SyncSnapshot

    @POST("api/v1/simulate/trade")
    suspend fun simulateTrade(
        @Header("X-Api-Auth-Token") token: String,
        @Body request: TradeSimulationRequestDto
    ): TradeSimulationResultDto
}

object SyncApiClient {
    const val USB_BASE_URL = "http://127.0.0.1:8080/"
    const val EMULATOR_BASE_URL = "http://10.0.2.2:8080/"
    const val WIFI_BASE_URL = "http://192.168.1.13:8080/"

    fun createService(baseUrl: String = USB_BASE_URL): SyncApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(SyncApiService::class.java)
    }

    suspend fun fetchSnapshotWithFallback(context: Context): SyncSnapshot {
        val customUrl = SnapshotCacheManager.getCustomUrl(context)
        val authToken = SnapshotCacheManager.getAuthToken(context)
        
        // 1. Try Custom Remote/Tunnel URL if configured
        if (!customUrl.isNullOrBlank()) {
            try {
                val formatted = if (customUrl.endsWith("/")) customUrl else "$customUrl/"
                val remoteSnapshot = createService(formatted).getSnapshot(token = authToken)
                SnapshotCacheManager.saveSnapshot(context, remoteSnapshot, isFullLedgerSync = true)
                return remoteSnapshot
            } catch (e: Exception) {
                // fallthrough to local networks
            }
        }

        // 2. Try USB Loopback (adb reverse)
        try {
            val snapshot = createService(USB_BASE_URL).getSnapshot(token = authToken)
            SnapshotCacheManager.saveSnapshot(context, snapshot, isFullLedgerSync = true)
            return snapshot
        } catch (e1: Exception) {
            // 3. Try Android Emulator loopback
            try {
                val snapshot = createService(EMULATOR_BASE_URL).getSnapshot(token = authToken)
                SnapshotCacheManager.saveSnapshot(context, snapshot, isFullLedgerSync = true)
                return snapshot
            } catch (e2: Exception) {
                // 4. Try Wi-Fi LAN IP
                try {
                    val snapshot = createService(WIFI_BASE_URL).getSnapshot(token = authToken)
                    SnapshotCacheManager.saveSnapshot(context, snapshot, isFullLedgerSync = true)
                    return snapshot
                } catch (e3: Exception) {
                    // 5. Offline Fallback: Check direct AMFI NAVs over cellular if connected, or return frozen cache if fully offline!
                    val cached = SnapshotCacheManager.loadSnapshot(context)
                    if (cached != null) {
                        val liveNavs = com.portfolioos.mobile.data.nav.AmfiDirectFetcher.fetchLatestNavMap()
                        if (liveNavs.isNotEmpty()) {
                            val updated = SnapshotCacheManager.updateOfflineSnapshotWithLiveAmfi(cached)
                            SnapshotCacheManager.saveSnapshot(context, updated, isFullLedgerSync = false)
                            SnapshotCacheManager.setFullyOffline(context, false)
                            return updated
                        } else {
                            // Airplane Mode / Completely Offline: Preserve frozen timestamps & mark fully offline
                            SnapshotCacheManager.setFullyOffline(context, true)
                            return cached
                        }
                    } else {
                        throw e3
                    }
                }
            }
        }
    }

    suspend fun simulateTradeWithFallback(context: Context, request: TradeSimulationRequestDto): TradeSimulationResultDto {
        val customUrl = SnapshotCacheManager.getCustomUrl(context)
        val authToken = SnapshotCacheManager.getAuthToken(context)

        if (!customUrl.isNullOrBlank()) {
            try {
                val formatted = if (customUrl.endsWith("/")) customUrl else "$customUrl/"
                return createService(formatted).simulateTrade(token = authToken, request = request)
            } catch (e: Exception) {
                // fallthrough
            }
        }

        try {
            return createService(USB_BASE_URL).simulateTrade(token = authToken, request = request)
        } catch (e1: Exception) {
            try {
                return createService(EMULATOR_BASE_URL).simulateTrade(token = authToken, request = request)
            } catch (e2: Exception) {
                return createService(WIFI_BASE_URL).simulateTrade(token = authToken, request = request)
            }
        }
    }
}
