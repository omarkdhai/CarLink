package com.carlink.order.dto;

import com.carlink.order.model.OrderItem;
import com.carlink.order.model.StickerPackage;

import java.math.BigDecimal;

/**
 * Serializable snapshot of a single order line item. Prices and sticker counts
 * are always server-computed.
 */
public record OrderItemView(
        StickerPackage stickerPackage,
        int quantity,
        BigDecimal unitPrice,
        int stickersPerPack,
        BigDecimal subtotal
) {
    public static OrderItemView from(OrderItem item) {
        return new OrderItemView(
                item.getStickerPackage(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getStickersPerPack(),
                item.getSubtotal());
    }
}
