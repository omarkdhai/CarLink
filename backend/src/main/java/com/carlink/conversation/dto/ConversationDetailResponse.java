package com.carlink.conversation.dto;

import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationDelivery;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owner-facing conversation detail with full message history.
 * Extends the summary fields with the complete message list and, per
 * channel, how the alert was delivered (never a phone number or error detail).
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
        @NotNull List<ConversationMessageResponse> messages,
        @NotNull List<Delivery> deliveries
) {

    /** How the alert went on one channel (SMS, WHATSAPP): SENT, DELIVERED, READ or FAILED. */
    public record Delivery(@NotNull String channel, @NotNull String status) {}

    public static ConversationDetailResponse from(
            Conversation conversation,
            List<ConversationMessageResponse> messages,
            List<ConversationDelivery> deliveries
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
                messages,
                deliveries.stream()
                        .sorted(java.util.Comparator.comparing(ConversationDelivery::getChannel).reversed())
                        .map(d -> new Delivery(d.getChannel().name(), d.getStatus().name()))
                        .toList()
        );
    }
}
