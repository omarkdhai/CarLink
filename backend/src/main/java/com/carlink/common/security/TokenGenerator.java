package com.carlink.common.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
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

    private static final Base64.Encoder URL_SAFE = Base64.getUrlEncoder().withoutPadding();

    /**
     * Ambiguity-free alphabet for short activation codes: no {@code O/0},
     * {@code I/1/L}, so a code printed under a QR stays easy to read and type.
     */
    private static final char[] CODE_ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a URL-safe random token carrying all {@code byteLength * 8}
     * random bits: unpadded Base64URL ({@code A-Z a-z 0-9 - _}), so 32 bytes
     * give a 43-character, 256-bit token.
     *
     * <p>Security plan S-23: the previous encoder emitted one character per
     * byte from its low 6 bits, so 32 bytes carried only 192 bits. Tokens
     * issued before the change stay valid: only {@code sha256(token)} is
     * stored, whatever the format.
     */
    public String generateUrlSafe(int byteLength) {
        byte[] bytes = new byte[byteLength];
        secureRandom.nextBytes(bytes);
        return URL_SAFE.encodeToString(bytes);
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