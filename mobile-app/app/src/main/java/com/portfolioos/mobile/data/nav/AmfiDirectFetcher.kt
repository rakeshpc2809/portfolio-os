package com.portfolioos.mobile.data.nav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object AmfiDirectFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatestNavMap(): Map<String, Double> = withContext(Dispatchers.IO) {
        val navMap = mutableMapOf<String, Double>()
        try {
            val request = Request.Builder()
                .url("https://www.amfiindia.com/spages/NAVAll.txt")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyMap()

                val body = response.body?.string() ?: return@withContext emptyMap()
                val lines = body.split("\n")

                for (line in lines) {
                    val parts = line.split(";")
                    if (parts.size >= 6) {
                        val isin = parts[1].trim()
                        val navStr = parts[4].trim()
                        if (isin.isNotEmpty()) {
                            try {
                                val nav = navStr.toDouble()
                                navMap[isin] = nav
                            } catch (e: Exception) {
                                // skip header/corrupted
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext navMap
    }
}
