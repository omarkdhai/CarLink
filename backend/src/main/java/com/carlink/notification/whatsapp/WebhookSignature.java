package com.carlink.notification.whatsapp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Meta signs every webhook call: {@code X-Hub-Signature-256: sha256=<hex>}, the
 * HMAC-SHA256 of the raw request body keyed with the app secret. Anything else
 * did not come from Meta.
 */
final class WebhookSignature {

    private static final String PREFIX = "sha256=";

    private WebhookSignature() {
    }

    static boolean valid(byte[] body, String header, String appSecret) {
        if (header == null || !header.startsWith(PREFIX) || appSecret == null || appSecret.isBlank()) {
            return false;
        }
        byte[] expected = hmac(body, appSecret.trim());
        byte[] given;
        try {
            given = HexFormat.of().parseHex(header.substring(PREFIX.length()).trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, given);          // constant time
    }

    static String sign(byte[] body, String appSecret) {
        return PREFIX + HexFormat.of().formatHex(hmac(body, appSecret));
    }

    private static byte[] hmac(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body == null ? new byte[0] : body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
