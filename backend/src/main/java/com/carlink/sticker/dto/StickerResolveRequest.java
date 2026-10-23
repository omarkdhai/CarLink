package com.carlink.sticker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public sticker registration lookup: either the short activation code
 * printed under the QR, or the raw token from the scanned QR URL. The service
 * canonicalizes and hashes the value; the raw input is never persisted.
 */
public record StickerResolveRequest(
        @NotBlank @Size(max = 128) String code
) {}
