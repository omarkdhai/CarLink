package com.carlink.conversation.dto;

import com.carlink.conversation.model.Conversation;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Owner-facing conversation summary for the dashboard list.
 * Does not expose message content — only a preview snippet.
 */
public record ConversationSummaryResponse(
        @NotNull UUID id,
        @NotNull UUID vehicleId,
        String vehicleNickname,
        String channel,
        String status,
        boolean unread,
        Instant expiresAt,
        Instant createdAt,
        String lastMessagePreview
) {
    /** Maximum length of the last-message preview in the dashboard list. */
    private static final int PREVIEW_MAX = 80;

    public static ConversationSummaryResponse from(Conversation conversation, String lastMessageContent) {
        return new ConversationSummaryResponse(
                conversation.getId(),
                conversation.getVehicle().getId(),
                conversation.getVehicle().getNickname(),
                conversation.getChannel().name(),
                conversation.getStatus().name(),
                conversation.getReadAt() == null,
                conversation.getExpiresAt(),
                conversation.getCreatedAt(),
                lastMessageContent == null ? null : truncate(lastMessageContent)
        );
    }

    private static String truncate(String text) {
        return text.length() <= PREVIEW_MAX ? text : text.substring(0, PREVIEW_MAX) + "…";
    }
}
