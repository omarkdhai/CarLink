package com.carlink.order.model;

/**
 * Lifecycle of a guest cash-on-delivery order. Status transitions are linear
 * (PLACED → DELIVERED) or terminal (any → CANCELLED); no rollback.
 */
public enum OrderStatus {
    PLACED,
    DELIVERED,
    CANCELLED
}
