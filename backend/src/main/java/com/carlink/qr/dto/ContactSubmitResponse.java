package com.carlink.qr.dto;

import java.util.UUID;

/**
 * Generic acknowledgement returned to a visitor after submitting a contact
 * request. Contains no owner data and echoes no message content.
 */
public record ContactSubmitResponse(
        UUID conversationId,
        String message
) {}