package com.carlink.common.web;

import com.carlink.common.exception.NotFoundException;

/**
 * Raw QR / sticker tokens travel in this request header, never in an API path
 * (security plan S-08): paths end up in proxy and access logs, headers do not.
 * The only URL that carries a token is the QR's own {@code /c/{token}} page,
 * which nginx masks in its access log.
 */
public final class TokenHeader {

    public static final String NAME = "X-CarLink-Token";

    private static final int MAX_LENGTH = 128;

    private TokenHeader() {
    }

    /**
     * Returns the token, or answers 404 for a missing / absurd value — the same
     * answer as an unknown token, so the header reveals nothing either way.
     */
    public static String require(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            throw new NotFoundException("QR code not found");
        }
        return value.trim();
    }
}
