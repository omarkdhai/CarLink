package com.carlink.conversation.model;

/**
 * Outcome of one channel of an alert ({@link ConversationDelivery}).
 * SENT = accepted by the provider, waiting for its report; READ exists only on
 * WhatsApp and counts as delivered.
 */
public enum DeliveryStatus {
    SENT,
    DELIVERED,
    READ,
    FAILED;

    public boolean reached() {
        return this == DELIVERED || this == READ;
    }
}
