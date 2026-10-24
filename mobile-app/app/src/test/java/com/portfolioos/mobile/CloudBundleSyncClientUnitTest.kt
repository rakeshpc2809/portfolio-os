package com.portfolioos.mobile

import com.portfolioos.mobile.data.CloudBundleSyncClient
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CloudBundleSyncClientUnitTest {

    @Test
    fun testCloudBundleSyncClient_decryptsAndParsesEncryptedBundle() {
        val secret = "my_cloud_secret_passphrase"
        val sampleJson = """
            {
                "sync_info": {
                    "generated_at": "2026-10-24T12:00:00",
                    "current_value": 2500000.0,
                    "total_invested": 1800000.0,
                    "portfolio_xirr": 18.5,
                    "ledger_hash": "abc123hmacroot"
                },
                "holdings": [
                    {
                        "isin": "INF879O01027",
                        "fund_name": "Parag Parikh Flexi Cap",
                        "total_units": 500.0,
                        "current_value": 1250000.0
                    }
                ]
            }
        """.trimIndent()

        // 1. Manually craft bundle following the exact spec
        val gzipBaos = ByteArrayOutputStream()
        GZIPOutputStream(gzipBaos).use { it.write(sampleJson.toByteArray(StandardCharsets.UTF_8)) }
        val compressed = gzipBaos.toByteArray()

        val salt = ByteArray(16) { (it + 1).toByte() }
        val iv = ByteArray(12) { (it + 2).toByte() }

        val derivedKey = CloudBundleSyncClient.deriveKey(
            ikm = secret.toByteArray(StandardCharsets.UTF_8),
            salt = salt,
            info = "portfolio-os-cloud-sync-v1".toByteArray(StandardCharsets.UTF_8),
            length = 32
        )

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derivedKey, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(compressed)

        val bundleBaos = ByteArrayOutputStream()
        bundleBaos.write("POS1".toByteArray(StandardCharsets.US_ASCII))
        bundleBaos.write(salt)
        bundleBaos.write(iv)
        bundleBaos.write(ciphertext)
        val encryptedBundle = bundleBaos.toByteArray()

        // 2. Client decrypts and parses
        val decryptedJson = CloudBundleSyncClient.decryptAndDecompressBundle(encryptedBundle, secret)
        assertNotNull(decryptedJson)
        assertTrue(decryptedJson.contains("Parag Parikh Flexi Cap"))

        val snapshot = CloudBundleSyncClient.parseBundleJson(decryptedJson)
        assertNotNull(snapshot.syncInfo)
        assertEquals(2500000.0, snapshot.syncInfo?.currentValue ?: 0.0, 0.001)
        assertEquals("abc123hmacroot", snapshot.syncInfo?.ledgerHash)
        assertEquals(1, snapshot.holdings?.size)
        assertEquals("INF879O01027", snapshot.holdings?.first()?.isin)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCloudBundleSyncClient_rejectsInvalidMagic() {
        val badPayload = "BAD1".toByteArray(StandardCharsets.US_ASCII) + ByteArray(50)
        CloudBundleSyncClient.decryptAndDecompressBundle(badPayload, "secret")
    }
}
