package com.carlink.common.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Generates cryptographically secure random tokens and their SHA-256 hashes.
 *
 * <p>The raw token is returned exactly once to the caller; every persistent
 * storage path must use {@link #sha256(String)} so the plain value is never
 * written to the database.</p>
 */
@Component
public class TokenGenerator {

    private static final char[] URL_SAFE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
                    .toCharArray();

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a URL-safe random token of {@code byteLength} random bytes.
     * Resulting string length is roughly {@code byteLength * 4 / 3}.
     */
    public String generateUrlSafe(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            // Use positional index mod alphabet length to avoid bias concerns
            // (alphabet length 66 is not a power of two, so mask to 64 below).
            int index = (b & 0x3F) % URL_SAFE_ALPHABET.length;
            sb.append(URL_SAFE_ALPHABET[index]);
        }
        return sb.toString();
    }

    /** Hex-encoded SHA-256 digest of the input. Used for storing token hashes. */
    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Constant-time comparison of two strings. */
    public boolean safeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}