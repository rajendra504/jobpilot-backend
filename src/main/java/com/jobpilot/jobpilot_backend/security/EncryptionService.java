package com.jobpilot.jobpilot_backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * AES-256-GCM symmetric encryption for sensitive data at rest.
 *
 * Format stored in DB (Base64):  [ salt(16) | iv(12) | ciphertext ]
 *
 * Usage:
 *   String encrypted = encryptionService.encrypt("my-password");
 *   String plain     = encryptionService.decrypt(encrypted);
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM      = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_LENGTH = 128;  // bits
    private static final int    IV_LENGTH      = 12;   // bytes
    private static final int    SALT_LENGTH    = 16;   // bytes
    private static final int    KEY_LENGTH     = 256;  // bits
    private static final int    ITERATIONS     = 65536;

    @Value("${app.encryption.secret}")
    private String secret;

    // ── Public API ────────────────────────────────────────────

    public String encrypt(String plainText) {
        try {
            byte[] salt = generateRandom(SALT_LENGTH);
            byte[] iv   = generateRandom(IV_LENGTH);

            SecretKey key = deriveKey(secret, salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combine: salt + iv + ciphertext
            ByteBuffer buf = ByteBuffer.allocate(salt.length + iv.length + cipherText.length);
            buf.put(salt);
            buf.put(iv);
            buf.put(cipherText);

            return Base64.getEncoder().encodeToString(buf.array());

        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedBase64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedBase64);
            ByteBuffer buf = ByteBuffer.wrap(decoded);

            byte[] salt = new byte[SALT_LENGTH];
            buf.get(salt);

            byte[] iv = new byte[IV_LENGTH];
            buf.get(iv);

            byte[] cipherText = new byte[buf.remaining()];
            buf.get(cipherText);

            SecretKey key = deriveKey(secret, salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new EncryptionException("Decryption failed", e);
        }
    }

    // ── Internal helpers ──────────────────────────────────────

    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    private byte[] generateRandom(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    // ── Custom exception ──────────────────────────────────────

    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
