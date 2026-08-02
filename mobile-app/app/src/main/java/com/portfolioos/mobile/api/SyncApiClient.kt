package com.portfolioos.mobile.api

import com.portfolioos.mobile.model.SyncSnapshot
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SyncApiService {
    @GET("api/v1/sync/snapshot")
    suspend fun getSnapshot(
        @Header("X-Api-Auth-Token") token: String = "fintracker-cachyos-default-key-2026",
        @Query("fy") fiscalYear: String = "2026-27"
    ): SyncSnapshot
}

object SyncApiClient {
    const val USB_BASE_URL = "http://127.0.0.1:8080/"
    const val WIFI_BASE_URL = "http://192.168.1.13:8080/"

    fun createService(baseUrl: String = USB_BASE_URL): SyncApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(SyncApiService::class.java)
    }

    suspend fun fetchSnapshotWithFallback(): SyncSnapshot {
        return try {
            createService(USB_BASE_URL).getSnapshot()
        } catch (e1: Exception) {
            createService(WIFI_BASE_URL).getSnapshot()
        }
    }
}
