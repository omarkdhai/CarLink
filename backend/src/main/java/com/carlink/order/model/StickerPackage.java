package com.carlink.order.model;

import java.math.BigDecimal;

/**
 * Predefined sticker packages sold through the web shop. Each package carries
 * a fixed unit price and a sticker count; the buyer selects a package and
 * quantity — totals are always computed server-side.
 */
public enum StickerPackage {
    SINGLE(new BigDecimal("20.00"), 1),
    DOUBLE(new BigDecimal("35.00"), 2),
    BUSINESS(new BigDecimal("350.00"), 20);

    private final BigDecimal packPrice;
    private final int stickersPerPack;

    StickerPackage(BigDecimal packPrice, int stickersPerPack) {
        this.packPrice = packPrice;
        this.stickersPerPack = stickersPerPack;
    }

    public BigDecimal packPrice() {
        return packPrice;
    }

    public int stickersPerPack() {
        return stickersPerPack;
    }
}
