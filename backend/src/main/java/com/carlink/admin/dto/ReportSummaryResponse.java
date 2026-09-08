package com.carlink.admin.dto;

import com.carlink.admin.model.Report;

import java.time.Instant;
import java.util.UUID;

/**
 * One moderation report in the admin list. The reported conversation may be
 * null when it was deleted ({@code ON DELETE SET NULL}).
 */
public record ReportSummaryResponse(
        UUID id,
        String reason,
        String status,
        String details,
        String reporterIp,
        Instant createdAt,
        UUID conversationId,
        String conversationChannel,
        String vehicleNickname
) {

    public static ReportSummaryResponse from(Report report) {
        return new ReportSummaryResponse(
                report.getId(),
                report.getReason().name(),
                report.getStatus().name(),
                report.getDetails(),
                report.getReporterIp(),
                report.getCreatedAt(),
                report.getConversation() == null ? null : report.getConversation().getId(),
                report.getConversation() == null ? null
                        : report.getConversation().getChannel().name(),
                report.getConversation() == null ? null
                        : report.getConversation().getVehicle().getNickname());
    }
}