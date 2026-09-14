package com.carlink.admin.dto;

import com.carlink.admin.model.Report;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Full moderation view of a report, including the reported conversation's
 * context — and its last message body. This is the one admin-only exception
 * to "message content is never returned": it requires JWT + ROLE_ADMIN and is
 * needed to judge SPAM/ABUSE. {@code conversation} is null when the reported
 * conversation was deleted.
 *
 * <p>{@code conversation} is {@link JsonInclude.Include#ALWAYS} so a deleted
 * conversation serializes as {@code "conversation": null} — the global Jackson
 * {@code non_null} default would otherwise drop the key entirely, hiding that a
 * report exists but lost its context.</p>
 */
public record ReportDetailResponse(
        UUID id,
        String reason,
        String status,
        String details,
        String reporterIp,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.ALWAYS) ConversationContext conversation
) {

    public static ReportDetailResponse from(Report report, ConversationContext conversation) {
        return new ReportDetailResponse(
                report.getId(),
                report.getReason().name(),
                report.getStatus().name(),
                report.getDetails(),
                report.getReporterIp(),
                report.getCreatedAt(),
                conversation);
    }

    public record ConversationContext(
            UUID conversationId,
            String channel,
            String conversationStatus,
            Instant createdAt,
            String vehicleNickname,
            long messageCount,
            String lastMessageContent
    ) {}
}