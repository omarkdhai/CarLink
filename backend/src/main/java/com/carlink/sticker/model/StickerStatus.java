package com.carlink.sticker.model;

/**
 * Lifecycle of a physical QR sticker.
 *
 * <p>{@code UNBOUND} — a virgin sticker sold with an order but not yet claimed.
 * {@code BOUND} — claimed by exactly one user and linked to one of their
 * vehicles; this is the live anonymous-contact QR. {@code DEACTIVATED} — the
 * owner released it (e.g. sold the car); it is claimable again.</p>
 */
public enum StickerStatus {
    UNBOUND,
    BOUND,
    DEACTIVATED
}