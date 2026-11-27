package com.carlink.order.dto;

import java.util.UUID;

/**
 * One sticker of an order as the admin sees it to print and ship it: its QR
 * (public URL + PNG) and the activation code printed under it. Admin-only;
 * every read is audited. {@code codesAvailable} is false for stickers minted
 * before the codes were stored (only their hashes exist), in which case the
 * URL, image and code are null.
 */
public record AdminOrderStickerView(
        UUID id,
        String status,
        boolean codesAvailable,
        String publicUrl,
        String imageDataUri,
        String activationCode
) {}
