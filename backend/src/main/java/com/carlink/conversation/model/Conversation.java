package com.carlink.conversation.model;

import com.carlink.vehicle.model.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A visitor-initiated contact request for a {@link Vehicle}.
 *
 * <p>This table deliberately has no {@code updated_at}; the entity sets
 * {@code createdAt} explicitly rather than extending {@code BaseEntity}.
 * A conversation reaches a terminal state via {@code status} (SENT/FAILED/
 * EXPIRED) and {@code expiresAt}.</p>
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Timestamp when the vehicle owner first opened this conversation. Null = unread. */
    @Column(name = "read_at")
    private Instant readAt;

    /**
     * Creates a new conversation in the terminal-success state ({@code SENT}).
     * In Phase 7 the expiry sweeper flips stale conversations to EXPIRED.
     */
    public static Conversation open(Vehicle vehicle, Channel channel,
                                    ConversationStatus status, Instant expiresAt) {
        return Conversation.builder()
                .id(UUID.randomUUID())
                .vehicle(vehicle)
                .channel(channel)
                .status(status)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .build();
    }
}