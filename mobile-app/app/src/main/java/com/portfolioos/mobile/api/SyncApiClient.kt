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
        @Query("fy") fiscalYear: String? = null
    ): SyncSnapshot

    @POST("api/v1/simulate/trade")
    suspend fun simulateTrade(
        @Header("X-Api-Auth-Token") token: String,
        @Body request: TradeSimulationRequestDto
    ): TradeSimulationResultDto

    @GET("api/v1/analytics/overlap")
    suspend fun getOverlapAnalytics(
        @Header("X-Api-Auth-Token") token: String,
        @Query("includeUnverified") includeUnverified: Boolean = false
    ): com.portfolioos.mobile.model.OverlapReportDto

    @GET("api/v1/fire/summary")
    suspend fun getFireSummary(
        @Header("X-Api-Auth-Token") token: String
    ): com.portfolioos.mobile.model.FireSummaryResponseDto

    @GET("api/v1/analytics/benchmark")
    suspend fun getBenchmarkAnalytics(
        @Header("X-Api-Auth-Token") token: String,
        @Query("benchmark") benchmark: String = "NIFTY_50_TRI"
    ): com.portfolioos.mobile.model.BenchmarkAnalyticsDto

    @GET("api/v1/rules/action-recommendations")
    suspend fun getActionRecommendations(
        @Header("X-Api-Auth-Token") token: String
    ): List<com.portfolioos.mobile.model.ActionRecommendationDto>
}

object SyncApiClient {
    const val EMULATOR_BASE_URL = "http://10.0.2.2:8080/"
    const val USB_BASE_URL = "http://127.0.0.1:8080/"
    const val DEFAULT_BASE_URL = EMULATOR_BASE_URL

    @Volatile
    var lastWorkingBaseUrl: String? = null

    private val sharedOkHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            redactHeader("X-Api-Auth-Token")
            redactHeader("Authorization")
        }
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    fun getCandidateBaseUrls(context: Context): List<String> {
        val urls = mutableListOf<String>()
        val customUrl = SnapshotCacheManager.getCustomUrl(context)
        if (!customUrl.isNullOrBlank()) {
            urls.add(if (customUrl.endsWith("/")) customUrl else "$customUrl/")
        }
        lastWorkingBaseUrl?.let { urls.add(it) }

        val isEmulator = android.os.Build.FINGERPRINT.isNullOrBlank() ||
                         android.os.Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                         android.os.Build.MODEL.contains("sdk", ignoreCase = true) ||
                         android.os.Build.MODEL.contains("Emulator", ignoreCase = true)
        if (isEmulator) {
            urls.add(EMULATOR_BASE_URL)
        }
        urls.add(USB_BASE_URL)
        return urls.distinct()
    }

    fun createService(baseUrl: String = DEFAULT_BASE_URL): SyncApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(sharedOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(SyncApiService::class.java)
    }

    suspend fun fetchSnapshotOnline(context: Context): SyncSnapshot {
        val authToken = SnapshotCacheManager.getAuthToken(context)
        val candidateUrls = getCandidateBaseUrls(context)

        var lastException: Exception? = null
        for (baseUrl in candidateUrls) {
            try {
                val snapshot = createService(baseUrl).getSnapshot(token = authToken)
                lastWorkingBaseUrl = baseUrl
                val savedSnapshot = SnapshotCacheManager.saveSnapshot(context, snapshot, isFullLedgerSync = true)
                SnapshotCacheManager.setFullyOffline(context, false)
                return savedSnapshot
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw (lastException ?: java.io.IOException("All backend candidate URLs unreachable"))
    }

    suspend fun fetchSnapshotWithFallback(context: Context): SyncSnapshot {
        return try {
            fetchSnapshotOnline(context)
        } catch (e: Exception) {
            SnapshotCacheManager.setFullyOffline(context, true)
            val cached = SnapshotCacheManager.loadSnapshot(context)
            cached ?: throw java.io.IOException("No network connection available to sync snapshot and no local cache present: ${e.message}", e)
        }
    }

    suspend fun simulateTradeWithFallback(context: Context, request: TradeSimulationRequestDto): TradeSimulationResultDto {
        val authToken = SnapshotCacheManager.getAuthToken(context)
        val candidateUrls = getCandidateBaseUrls(context)

        var lastException: Exception? = null
        for (baseUrl in candidateUrls) {
            try {
                val result = createService(baseUrl).simulateTrade(token = authToken, request = request)
                lastWorkingBaseUrl = baseUrl
                return result
            } catch (e: Exception) {
                lastException = e
            }
        }

        // Offline Fallback: compute simulation locally on device using cached snapshot
        val cached = SnapshotCacheManager.loadSnapshot(context)
        if (cached != null) {
            return com.portfolioos.mobile.data.nav.LocalTradeSimulator.simulateTradeLocally(cached, request)
        }

        throw lastException ?: java.io.IOException("Unable to connect to any backend candidate URL and no local cache present.")
    }

    suspend fun fetchActionRecommendations(context: Context): List<com.portfolioos.mobile.model.ActionRecommendationDto> {
        val authToken = SnapshotCacheManager.getAuthToken(context)
        val candidateUrls = getCandidateBaseUrls(context)

        for (baseUrl in candidateUrls) {
            try {
                val result = createService(baseUrl).getActionRecommendations(token = authToken)
                lastWorkingBaseUrl = baseUrl
                return result
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }
}
