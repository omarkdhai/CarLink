package com.carlink.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Token-free, PII-free order summary. Used for status checks after checkout.
 * No sticker tokens, no customer details — only the reference, status, and
 * totals.
 */
public record OrderSummaryResponse(
        String reference,
        String status,
        BigDecimal totalAmount,
        String currency,
        int stickerCount,
        Instant createdAt
) {}
