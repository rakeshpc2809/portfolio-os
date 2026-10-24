package com.portfolioos.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CloudBundleCrypto {

    public static final byte[] MAGIC_HEADER = "POS1".getBytes(StandardCharsets.US_ASCII);
    public static final byte[] HKDF_INFO = "portfolio-os-cloud-sync-v1".getBytes(StandardCharsets.UTF_8);
    public static final int SALT_LENGTH = 16;
    public static final int IV_LENGTH = 12;
    public static final int TAG_LENGTH_BITS = 128;
    public static final int KEY_LENGTH_BYTES = 32;

    /**
     * Derives a 256-bit symmetric key using RFC 5869 HKDF-SHA256.
     */
    public static byte[] deriveKey(byte[] ikm, byte[] salt, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        byte[] effectiveSalt = (salt == null || salt.length == 0) ? new byte[32] : salt;
        mac.init(new SecretKeySpec(effectiveSalt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);

        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] t = new byte[0];
        int n = (int) Math.ceil((double) length / 32.0);
        for (int i = 1; i <= n; i++) {
            mac.reset();
            mac.update(t);
            if (info != null) mac.update(info);
            mac.update((byte) i);
            t = mac.doFinal();
            baos.write(t);
        }
        byte[] okm = baos.toByteArray();
        byte[] result = new byte[length];
        System.arraycopy(okm, 0, result, 0, length);
        return result;
    }

    /**
     * Compresses with Gzip and encrypts with AES-256-GCM.
     * Binary format: [4 bytes MAGIC] + [16 bytes SALT] + [12 bytes IV] + [CIPHERTEXT + 16 bytes TAG]
     */
    public static byte[] encryptBundle(byte[] plaintextJson, String secretPassphrase) throws Exception {
        // 1. Gzip compress
        ByteArrayOutputStream gzipBaos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(gzipBaos)) {
            gzos.write(plaintextJson);
        }
        byte[] compressedData = gzipBaos.toByteArray();

        // 2. Generate random salt and IV
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);

        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        // 3. Derive 256-bit key
        byte[] derivedKey = deriveKey(secretPassphrase.getBytes(StandardCharsets.UTF_8), salt, HKDF_INFO, KEY_LENGTH_BYTES);

        // 4. Encrypt with AES-GCM
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derivedKey, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        byte[] ciphertext = cipher.doFinal(compressedData);

        // 5. Pack bundle
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(MAGIC_HEADER);
        out.write(salt);
        out.write(iv);
        out.write(ciphertext);
        return out.toByteArray();
    }

    /**
     * Decrypts and decompresses an encrypted bundle.
     */
    public static byte[] decryptBundle(byte[] encryptedBundle, String secretPassphrase) throws Exception {
        int minHeaderLen = MAGIC_HEADER.length + SALT_LENGTH + IV_LENGTH;
        if (encryptedBundle.length <= minHeaderLen) {
            throw new IllegalArgumentException("Encrypted bundle payload too short");
        }

        // Verify magic
        byte[] magic = Arrays.copyOfRange(encryptedBundle, 0, MAGIC_HEADER.length);
        if (!Arrays.equals(magic, MAGIC_HEADER)) {
            throw new IllegalArgumentException("Invalid magic header in encrypted bundle");
        }

        int offset = MAGIC_HEADER.length;
        byte[] salt = Arrays.copyOfRange(encryptedBundle, offset, offset + SALT_LENGTH);
        offset += SALT_LENGTH;

        byte[] iv = Arrays.copyOfRange(encryptedBundle, offset, offset + IV_LENGTH);
        offset += IV_LENGTH;

        byte[] ciphertext = Arrays.copyOfRange(encryptedBundle, offset, encryptedBundle.length);

        // Derive key
        byte[] derivedKey = deriveKey(secretPassphrase.getBytes(StandardCharsets.UTF_8), salt, HKDF_INFO, KEY_LENGTH_BYTES);

        // Decrypt
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(derivedKey, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        byte[] decompressed = cipher.doFinal(ciphertext);

        // Gzip decompress
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(decompressed))) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = gzis.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
        }
        return out.toByteArray();
    }
}
