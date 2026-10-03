package com.carlink.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full order response returned at checkout. Contains the sticker tokens
 * exactly once — the client must persist them immediately; refreshing or
 * re-fetching yields the token-free {@link OrderSummaryResponse} instead.
 */
public record OrderResponse(
        String reference,
        String status,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        List<OrderItemView> items,
        List<StickerIssuedResponse> stickers
) {}
