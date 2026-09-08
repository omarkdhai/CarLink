package com.carlink.conversation.dto;

import com.carlink.conversation.model.Message;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Owner-facing chat message record for a single conversation's history.
 * (Named distinct from the generic {@code common.dto.MessageResponse} used for
 * action acknowledgments.)
 */
public record ConversationMessageResponse(
        @NotNull UUID id,
        @NotNull String content,
        @NotNull Instant createdAt
) {
    public static ConversationMessageResponse from(Message message) {
        return new ConversationMessageResponse(
                message.getId(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}