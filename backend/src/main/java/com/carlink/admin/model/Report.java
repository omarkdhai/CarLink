package com.carlink.admin.model;

import com.carlink.conversation.model.Conversation;
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
 * A visitor's moderation report against a conversation (SPAM/ABUSE/OTHER),
 * filed anonymously on the public QR surface and resolved by an admin.
 *
 * <p>{@code conversation} is optional because the DB drops to {@code SET NULL}
 * when the reported conversation is deleted — the report and its moderation
 * trail survive, but the context is gone.</p>
 */
@Entity
@Table(name = "reports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    private UUID id;

    @Column(name = "reporter_ip", length = 45)
    private String reporterIp;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(length = 2000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Opens a new report in the OPEN state. {@code reporterIp} is captured for moderation only. */
    public static Report file(Conversation conversation, String reporterIp,
                              ReportReason reason, String details) {
        return Report.builder()
                .id(UUID.randomUUID())
                .conversation(conversation)
                .reporterIp(reporterIp)
                .reason(reason)
                .details(details)
                .status(ReportStatus.OPEN)
                .createdAt(Instant.now())
                .build();
    }
}