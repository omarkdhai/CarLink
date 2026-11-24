package com.carlink.qr.dto;

/**
 * Delivery status of a visitor's alert: DELIVERED, SENT (accepted by the SMS
 * provider, confirmation pending) or FAILED. Nothing else leaves the server.
 */
public record DeliveryStatusResponse(String status) {
}
