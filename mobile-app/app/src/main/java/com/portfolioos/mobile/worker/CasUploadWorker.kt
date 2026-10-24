package com.portfolioos.mobile.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.*
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.data.SnapshotCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class CasUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val filePath = inputData.getString(KEY_FILE_PATH)
        val password = inputData.getString(KEY_PASSWORD) ?: ""

        if (filePath.isNullOrBlank()) {
            Log.e(TAG, "Missing filePath in CasUploadWorker inputData")
            return@withContext Result.failure()
        }

        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "CAS file does not exist or is empty: $filePath")
            return@withContext Result.failure()
        }

        Log.i(TAG, "Executing background CAS statement upload for ${file.name} (${file.length()} bytes)")

        val token = SnapshotCacheManager.getAuthToken(applicationContext)
        val candidateUrls = SyncApiClient.getCandidateBaseUrls(applicationContext)

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("application/pdf".toMediaTypeOrNull())
            )

        if (password.isNotBlank()) {
            multipartBuilder.addFormDataPart("password", password)
        }

        val requestBody = multipartBuilder.build()
        var uploadSucceeded = false

        for (baseUrl in candidateUrls) {
            val uploadUrl = if (baseUrl.endsWith("/")) "${baseUrl}statements/upload" else "$baseUrl/statements/upload"
            try {
                val reqBuilder = Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)

                if (token.isNotBlank()) {
                    reqBuilder.addHeader("X-Api-Auth-Token", token)
                }

                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "CAS statement upload succeeded via $uploadUrl (HTTP ${response.code})")
                        uploadSucceeded = true
                        return@use
                    } else {
                        Log.w(TAG, "Upload attempt failed on $uploadUrl with code ${response.code}")
                    }
                }

                if (uploadSucceeded) break
            } catch (e: Exception) {
                Log.w(TAG, "Error connecting to $uploadUrl for CAS upload: ${e.message}")
            }
        }

        if (uploadSucceeded) {
            // Delete uploaded temporary file
            try {
                file.delete()
            } catch (_: Exception) {}

            // Trigger snapshot refresh now that statement is ingested
            try {
                PortfolioSyncWorker.runOneTimeSync(applicationContext)
            } catch (_: Exception) {}

            Result.success()
        } else {
            Log.w(TAG, "Unable to reach any core-node backend for CAS upload. Retrying when connected.")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CasUploadWorker"
        const val KEY_FILE_PATH = "key_file_path"
        const val KEY_PASSWORD = "key_password"
        private const val QUEUE_DIR_NAME = "cas_queue"

        /**
         * Copies a file from a content Uri into internal storage and enqueues a background upload.
         */
        fun enqueueUpload(context: Context, uri: Uri, password: String = ""): Boolean {
            val queueDir = File(context.filesDir, QUEUE_DIR_NAME).apply { mkdirs() }
            val tempFile = File(queueDir, "cas_${System.currentTimeMillis()}.pdf")

            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy CAS file from URI: ${e.message}", e)
                return false
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                Log.e(TAG, "Copied CAS file is empty")
                return false
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = workDataOf(
                KEY_FILE_PATH to tempFile.absolutePath,
                KEY_PASSWORD to password
            )

            val workRequest = OneTimeWorkRequestBuilder<CasUploadWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.i(TAG, "Enqueued CAS upload task for ${tempFile.name}")
            return true
        }
    }
}
