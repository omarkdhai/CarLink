package com.carlink.admin.model;

import com.carlink.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;
import java.util.UUID;

/**
 * A write-only audit trail of admin and other sensitive actions. Rows are
 * immutable: they are recorded, listed by admins, and never edited.
 *
 * <p>{@code actor} is optional — {@code users.user_id} is {@code SET NULL}
 * when the acting user is deleted. {@code details} is a JSON object held in
 * the {@code audit_logs.details} jsonb column; it must never carry passwords,
 * tokens, phone numbers, or message bodies.</p>
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id")
    private User actor;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    /** Raw JSON; the jsonb type is asserted by {@code @JdbcTypeCode} for {@code ddl-auto: validate}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AuditLog of(User actor, String action, String entityType, UUID entityId,
                              String ipAddress, String userAgent, String details, Instant createdAt) {
        return AuditLog.builder()
                .id(UUID.randomUUID())
                .actor(actor)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .details(details)
                .createdAt(createdAt)
                .build();
    }
}