package com.portfolioos.mobile.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.model.SyncSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

object CloudBundleSyncClient {

    private const val TAG = "CloudBundleSyncClient"
    private val MAGIC_HEADER = "POS1".toByteArray(StandardCharsets.US_ASCII)
    private val HKDF_INFO = "portfolio-os-cloud-sync-v1".toByteArray(StandardCharsets.UTF_8)
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val KEY_LENGTH_BYTES = 32

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson: Gson = GsonBuilder().create()

    /**
     * Derives a 256-bit symmetric key using RFC 5869 HKDF-SHA256.
     */
    fun deriveKey(ikm: ByteArray, salt: ByteArray?, info: ByteArray?, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val effectiveSalt = if (salt == null || salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val baos = ByteArrayOutputStream()
        var t = ByteArray(0)
        val n = ceil(length / 32.0).toInt()
        for (i in 1..n) {
            mac.reset()
            mac.update(t)
            if (info != null) mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            baos.write(t)
        }
        val okm = baos.toByteArray()
        val result = ByteArray(length)
        System.arraycopy(okm, 0, result, 0, length)
        return result
    }

    /**
     * Decrypts and decompresses an encrypted portfolio_state.enc bundle payload.
     */
    fun decryptAndDecompressBundle(encryptedBytes: ByteArray, secretPassphrase: String): String {
        val minHeaderLen = MAGIC_HEADER.size + SALT_LENGTH + IV_LENGTH
        require(encryptedBytes.size > minHeaderLen) { "Encrypted bundle payload too short" }

        val magic = Arrays.copyOfRange(encryptedBytes, 0, MAGIC_HEADER.size)
        require(Arrays.equals(magic, MAGIC_HEADER)) { "Invalid magic header in encrypted bundle" }

        var offset = MAGIC_HEADER.size
        val salt = Arrays.copyOfRange(encryptedBytes, offset, offset + SALT_LENGTH)
        offset += SALT_LENGTH

        val iv = Arrays.copyOfRange(encryptedBytes, offset, offset + IV_LENGTH)
        offset += IV_LENGTH

        val ciphertext = Arrays.copyOfRange(encryptedBytes, offset, encryptedBytes.size)

        // Derive 256-bit AES key
        val derivedKey = deriveKey(secretPassphrase.toByteArray(StandardCharsets.UTF_8), salt, HKDF_INFO, KEY_LENGTH_BYTES)

        // Decrypt with AES-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val decompressedCompressed = cipher.doFinal(ciphertext)

        // Decompress Gzip
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(decompressedCompressed)).use { gzis ->
            val buf = ByteArray(4096)
            var read: Int
            while (gzis.read(buf).also { read = it } != -1) {
                out.write(buf, 0, read)
            }
        }
        return out.toString(StandardCharsets.UTF_8.name())
    }

    /**
     * Parses decrypted JSON into a SyncSnapshot.
     */
    fun parseBundleJson(jsonStr: String): SyncSnapshot {
        return gson.fromJson(jsonStr, SyncSnapshot::class.java)
    }

    /**
     * Attempts to fetch the encrypted bundle from cloud candidate endpoints or relay,
     * decrypts client-side, and stores in SnapshotCacheManager.
     */
    suspend fun fetchAndApplyEncryptedBundle(context: Context, cloudSecretKey: String): SyncSnapshot? = withContext(Dispatchers.IO) {
        val candidateUrls = SyncApiClient.getCandidateBaseUrls(context)
        val token = SnapshotCacheManager.getAuthToken(context)

        for (baseUrl in candidateUrls) {
            val bundleUrl = if (baseUrl.endsWith("/")) "${baseUrl}sync/bundle/encrypted" else "$baseUrl/sync/bundle/encrypted"
            try {
                val reqBuilder = Request.Builder().url(bundleUrl)
                if (token.isNotBlank()) reqBuilder.addHeader("X-Api-Auth-Token", token)

                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val encryptedBytes = response.body?.bytes()
                        if (encryptedBytes != null && encryptedBytes.isNotEmpty()) {
                            val json = decryptAndDecompressBundle(encryptedBytes, cloudSecretKey)
                            val snapshot = parseBundleJson(json)
                            SnapshotCacheManager.saveSnapshot(context, snapshot, isFullLedgerSync = true)
                            Log.i(TAG, "Successfully synced and decrypted cloud bundle from $bundleUrl")
                            return@withContext snapshot
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed cloud bundle attempt on $bundleUrl: ${e.message}")
            }
        }
        return@withContext null
    }
}
