package com.carlink.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Order response returned at checkout. Token-free: the stickers' QR codes and
 * activation codes are never sent to the buyer; the admin prints them from
 * the order page and ships them on the stickers.
 */
public record OrderResponse(
        String reference,
        String status,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        List<OrderItemView> items
) {}
