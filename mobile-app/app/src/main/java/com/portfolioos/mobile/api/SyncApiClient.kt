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
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/"

    fun createService(baseUrl: String = DEFAULT_BASE_URL): SyncApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(SyncApiService::class.java)
    }
}
