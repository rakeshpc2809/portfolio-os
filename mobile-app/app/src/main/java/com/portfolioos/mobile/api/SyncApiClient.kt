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
}

object SyncApiClient {
    const val USB_BASE_URL = "http://127.0.0.1:8080/"
    const val EMULATOR_BASE_URL = "http://10.0.2.2:8080/"
    const val WIFI_BASE_URL = "http://192.168.1.10:8080/"
    val WIFI_CANDIDATE_URLS = listOf(
        "http://192.168.1.10:8080/",
        "http://192.168.1.13:8080/",
        "http://192.168.0.10:8080/",
        "http://192.168.1.2:8080/"
    )

    fun getCandidateBaseUrls(context: Context): List<String> {
        val urls = mutableListOf<String>()
        val customUrl = SnapshotCacheManager.getCustomUrl(context)
        if (!customUrl.isNullOrBlank()) {
            urls.add(if (customUrl.endsWith("/")) customUrl else "$customUrl/")
        }
        urls.add(USB_BASE_URL)
        urls.add(EMULATOR_BASE_URL)
        urls.addAll(WIFI_CANDIDATE_URLS)
        return urls.distinct()
    }

    fun createService(baseUrl: String = USB_BASE_URL): SyncApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
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
        val authToken = SnapshotCacheManager.getAuthToken(context)
        val candidateUrls = getCandidateBaseUrls(context)

        for (baseUrl in candidateUrls) {
            try {
                val snapshot = createService(baseUrl).getSnapshot(token = authToken)
                SnapshotCacheManager.saveSnapshot(context, snapshot, isFullLedgerSync = true)
                return snapshot
            } catch (e: Exception) {
                // continue to next candidate
            }
        }

        // Offline Fallback: Return cached snapshot if available
        val cached = SnapshotCacheManager.loadSnapshot(context)
        return cached ?: throw java.io.IOException("No network connection available to sync snapshot and no local cache present.")
    }

    suspend fun simulateTradeWithFallback(context: Context, request: TradeSimulationRequestDto): TradeSimulationResultDto {
        val authToken = SnapshotCacheManager.getAuthToken(context)
        val candidateUrls = getCandidateBaseUrls(context)

        var lastException: Exception? = null
        for (baseUrl in candidateUrls) {
            try {
                return createService(baseUrl).simulateTrade(token = authToken, request = request)
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: java.io.IOException("Unable to connect to any backend candidate URL.")
    }
}
