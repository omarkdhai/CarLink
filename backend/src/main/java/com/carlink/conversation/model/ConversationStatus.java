package com.carlink.conversation.model;

/**
 * Lifecycle of a conversation between a visitor and a vehicle owner.
 *
 * <p>PENDING is set the instant a request arrives, before the channel relay
 * has run; the relay (Phase 6) then flips it to SENT on success or FAILED on
 * failure. EXPIRED is a Phase 7 sweeper concern.</p>
 */
public enum ConversationStatus {
    PENDING,
    SENT,
    FAILED,
    EXPIRED
}