package com.carlink.conversation.model;

/**
 * Lifecycle of a conversation between a visitor and a vehicle owner.
 *
 * <p>PENDING is set the instant a request arrives, before the channel relay
 * has run. The relay then sets SENT when the provider accepted the message
 * (delivery not confirmed yet), DELIVERED when it is confirmed (directly, or
 * later from the provider's delivery report), or FAILED when it was refused or
 * reported undeliverable. EXPIRED is set by the expiry sweeper.</p>
 */
public enum ConversationStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED,
    EXPIRED
}