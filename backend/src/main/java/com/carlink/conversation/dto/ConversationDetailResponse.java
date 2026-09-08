package com.carlink.conversation.dto;

import com.carlink.conversation.model.Conversation;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owner-facing conversation detail with full message history.
 * Extends the summary fields with the complete message list.
 */
public record ConversationDetailResponse(
        @NotNull UUID id,
        @NotNull UUID vehicleId,
        String vehicleNickname,
        String channel,
        String status,
        boolean unread,
        Instant expiresAt,
        Instant createdAt,
        @NotNull List<ConversationMessageResponse> messages
) {
    public static ConversationDetailResponse from(
            Conversation conversation,
            List<ConversationMessageResponse> messages
    ) {
        return new ConversationDetailResponse(
                conversation.getId(),
                conversation.getVehicle().getId(),
                conversation.getVehicle().getNickname(),
                conversation.getChannel().name(),
                conversation.getStatus().name(),
                conversation.getReadAt() == null,
                conversation.getExpiresAt(),
                conversation.getCreatedAt(),
                messages
        );
    }
}
