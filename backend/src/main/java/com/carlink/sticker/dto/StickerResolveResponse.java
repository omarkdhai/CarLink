package com.carlink.sticker.dto;

/**
 * Result of a public sticker lookup by code/token. Deliberately minimal: it
 * reveals only whether the sticker can be registered, never the owner, the
 * vehicle, or the order.
 *
 * @param state {@code CLAIMABLE} (virgin or released and ready to register) or
 *              {@code BOUND} (already linked to an account)
 */
public record StickerResolveResponse(
        String state
) {
    public static final String CLAIMABLE = "CLAIMABLE";
    public static final String BOUND = "BOUND";

    public static StickerResolveResponse claimable() {
        return new StickerResolveResponse(CLAIMABLE);
    }

    public static StickerResolveResponse bound() {
        return new StickerResolveResponse(BOUND);
    }
}
