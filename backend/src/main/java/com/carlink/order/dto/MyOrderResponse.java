package com.carlink.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One sticker purchase in the signed-in owner's history. PII-free and
 * token-free: no buyer name, mobile, address, notes, sticker tokens or
 * activation codes. {@code activatedCount} is how many of the order's stickers
 * this account has activated.
 */
public record MyOrderResponse(
        String reference,
        String status,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        List<OrderItemView> items,
        int stickerCount,
        int activatedCount
) {}
