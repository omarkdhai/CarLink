package com.carlink.order.dto;

/**
 * Exactly-once response for a single sticker minted during order creation.
 * The raw token is shown here and nowhere else — after this response it
 * exists only as a SHA-256 hash in the stickers table.
 */
public record StickerIssuedResponse(
        String rawToken,
        String publicUrl,
        String imageDataUri
) {}
