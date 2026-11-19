package com.carlink.privacy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM encryption of owners' phone numbers at rest (security plan S-17).
 *
 * <p>Stored form: {@code v1:} + Base64(12-byte IV ‖ ciphertext ‖ 16-byte tag).
 * A fresh random IV per value, so equal numbers give different ciphertexts.
 * The version prefix leaves room for key rotation. A value without the prefix
 * is a legacy plaintext row: it is returned as-is and encrypted on the next
 * save ({@link PhoneEncryptionBackfill} does that for every row at startup).
 *
 * <p>The key is {@code PHONE_ENCRYPTION_KEY}: 32 random bytes as 64 hex
 * characters ({@code node scripts/dev-secrets.mjs} creates one locally). Lose
 * it and the stored numbers are unrecoverable — back it up with the database
 * credentials, never next to a database dump.
 */
@Component
public class PhoneCipher {

    static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public PhoneCipher(@Value("${carlink.crypto.phone-key:}") String hexKey) {
        this.key = parseKey(hexKey);
    }

    static SecretKey parseKey(String hexKey) {
        if (hexKey == null || hexKey.isBlank()) {
            throw new IllegalStateException(
                    "PHONE_ENCRYPTION_KEY (carlink.crypto.phone-key) is not set: 64 hex characters "
                            + "(32 random bytes). Run node scripts/dev-secrets.mjs locally.");
        }
        byte[] bytes;
        try {
            bytes = HexFormat.of().parseHex(hexKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PHONE_ENCRYPTION_KEY must be hexadecimal.");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("PHONE_ENCRYPTION_KEY must be 32 bytes (64 hex characters).");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Phone encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored; // null, or a legacy plaintext row awaiting re-encryption
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Wrong key or tampered value. Never log the value itself.
            throw new IllegalStateException("Stored phone number cannot be decrypted (wrong PHONE_ENCRYPTION_KEY?)");
        }
    }

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}
