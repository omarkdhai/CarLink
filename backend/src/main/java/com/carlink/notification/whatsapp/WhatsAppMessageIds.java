package com.carlink.notification.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Meta's message id ({@code wamid.…}) is Base64 that embeds the recipient's
 * phone number, so it is never logged or stored as is: we keep only its
 * SHA-256 (lowercase hex, 64 chars). The webhook fingerprints the id Meta
 * reports the same way, so matching a status to its delivery still works.
 */
public final class WhatsAppMessageIds {

    private WhatsAppMessageIds() {
    }

    public static String fingerprint(String messageId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(messageId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
