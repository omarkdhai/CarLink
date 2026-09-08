package com.carlink.conversation.model;

/**
 * Contact channel chosen by a visitor on the public page. Which providers are
 * actually wired is decided in {@code carlink.contact.provider} (Phase 6+).
 */
public enum Channel {
    WHATSAPP,
    SMS
}