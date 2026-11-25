package com.carlink.conversation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One channel of an alert: the SMS or the WhatsApp message sent to the owner,
 * with the provider's message id (to match its delivery report) and a short
 * failure code. Never the phone number or the text.
 */
@Entity
@Table(name = "conversation_deliveries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDelivery {

    /**
     * Failure code when the phone operator refused what the note says
     * (TunisieSMS report {@code UNAUTHORIZED}, "contenu non autorisé").
     */
    public static final String CONTENT_REFUSED = "UNAUTHORIZED";

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    /** Who carried it: tunisiesms, telnyx, meta, mock, or none (never sent). */
    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    /** Short failure code when FAILED (e.g. NO_PHONE, API_402, REJECTED, WA_131026). */
    @Column(length = 40)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
