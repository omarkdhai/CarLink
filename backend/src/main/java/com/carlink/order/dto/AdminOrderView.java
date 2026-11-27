package com.carlink.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminOrderView(
        String reference,
        String status,
        BigDecimal totalAmount,
        String currency,
        int stickerCount,
        String customerName,
        String email,
        String mobile,
        String governorate,
        String deliveryAddress,
        String deliveryNotes,
        Instant createdAt,
        List<OrderItemView> items,
        /** DARK or LIGHT: the design to print. */
        String stickerStyle
) {}
