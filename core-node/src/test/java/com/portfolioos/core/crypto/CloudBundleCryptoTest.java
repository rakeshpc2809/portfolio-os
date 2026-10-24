package com.portfolioos.core.crypto;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class CloudBundleCryptoTest {

    @Test
    public void testEncryptAndDecryptRoundTrip() throws Exception {
        String secret = "test_super_secret_key_123456789";
        String payload = "{\"sync_info\":{\"current_value\":1500000.50},\"holdings\":[{\"isin\":\"INF879O01027\"}]}";
        byte[] plaintext = payload.getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = CloudBundleCrypto.encryptBundle(plaintext, secret);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 32);

        byte[] decrypted = CloudBundleCrypto.decryptBundle(encrypted, secret);
        String decryptedStr = new String(decrypted, StandardCharsets.UTF_8);

        assertEquals(payload, decryptedStr);
    }

    @Test
    public void testTamperedCiphertextFailsIntegrityCheck() throws Exception {
        String secret = "test_secret";
        byte[] plaintext = "hello portfolio os".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = CloudBundleCrypto.encryptBundle(plaintext, secret);
        // Tamper with the last byte (authentication tag)
        encrypted[encrypted.length - 1] ^= 0x01;

        assertThrows(Exception.class, () -> {
            CloudBundleCrypto.decryptBundle(encrypted, secret);
        });
    }

    @Test
    public void testWrongSecretKeyFailsDecryption() throws Exception {
        byte[] plaintext = "{\"test\":123}".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = CloudBundleCrypto.encryptBundle(plaintext, "correct_secret");

        assertThrows(Exception.class, () -> {
            CloudBundleCrypto.decryptBundle(encrypted, "wrong_secret");
        });
    }
}
