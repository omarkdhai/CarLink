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

    /**
     * Ambiguity-free alphabet for short activation codes: no {@code O/0},
     * {@code I/1/L}, so a code printed under a QR stays easy to read and type.
     */
    private static final char[] CODE_ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

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

    /**
     * Generates a short, human-enterable activation code in {@code XXXX-XXXX}
     * form (8 characters from the ambiguity-free alphabet). Printed under the
     * QR so a buyer can register without scanning.
     */
    public String generateActivationCode() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /**
     * Canonicalizes a user-typed activation code for hashing: uppercases,
     * strips every non-alphanumeric character, so "cl-4f7k 2m9q" and
     * "CL4F7K2M9Q" hash identically.
     */
    public String normalizeActivationCode(String code) {
        return code == null ? "" : code.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
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