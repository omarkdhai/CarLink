package com.carlink.admin.service;

import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.dto.ReportDetailResponse;
import com.carlink.admin.dto.ReportSummaryResponse;
import com.carlink.admin.model.Report;
import com.carlink.admin.model.ReportReason;
import com.carlink.admin.model.ReportStatus;
import com.carlink.admin.repository.ReportRepository;
import com.carlink.common.exception.NotFoundException;
import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.model.Message;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.conversation.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Moderation-report lifecycle: anonymous filing (public flow) and admin
 * list/detail/status transitions. Filing verifies the conversation exists
 * before any write; the moderation reads are admin-only via the URL matcher.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AuditLogService auditLogService;

    /** Files a visitor's report. The conversation must exist or the request is a 404 — nothing is written. */
    public void file(UUID conversationId, String reporterIp, ReportReason reason, String details) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        reportRepository.save(Report.file(
                conversation, reporterIp, reason, normalize(details)));
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> list(ReportStatus status) {
        List<Report> reports = status == null
                ? reportRepository.findAllByOrderByCreatedAtDesc()
                : reportRepository.findAllByStatusOrderByCreatedAtDesc(status);
        return reports.stream().map(ReportSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ReportDetailResponse get(UUID reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found"));
        return ReportDetailResponse.from(report, contextFor(report.getConversation()));
    }

    /** Raw rows for the CSV exporter (admin export). */
    @Transactional(readOnly = true)
    public List<Report> exportRows(ReportStatus status) {
        return status == null
                ? reportRepository.findAllByOrderByCreatedAtDesc()
                : reportRepository.findAllByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional
    public void changeStatus(UUID reportId, ReportStatus newStatus, AdminActor actor) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found"));
        ReportStatus from = report.getStatus();
        report.setStatus(newStatus);
        auditLogService.record("REPORT_STATUS_CHANGE", "REPORT", reportId,
                actor.adminId(), actor.ipAddress(), actor.userAgent(),
                Map.of("from", from.name(), "to", newStatus.name()));
    }

    /** Loads moderation context for a report; null when the conversation was deleted. */
    private ReportDetailResponse.ConversationContext contextFor(Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        List<Message> messages = messageRepository
                .findAllByConversation_IdOrderByCreatedAtAsc(conversation.getId());
        String last = messages.isEmpty() ? null : messages.get(messages.size() - 1).getContent();
        ConversationStatus convStatus = conversation.getStatus();
        return new ReportDetailResponse.ConversationContext(
                conversation.getId(),
                conversation.getChannel().name(),
                convStatus.name(),
                conversation.getCreatedAt(),
                conversation.getVehicle().getNickname(),
                messages.size(),
                last);
    }

    private String normalize(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        return details.trim();
    }
}