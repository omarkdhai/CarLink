package com.carlink.order.dto;

import com.carlink.order.model.StickerPackage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * One line in a sticker order: a package type and quantity. The unit price and
 * sticker count come from the server-side {@link StickerPackage} enum — the
 * client never sets prices.
 */
public record OrderItemRequest(
        @NotNull StickerPackage stickerPackage,
        @Min(1) @Max(50) int quantity
) {}
