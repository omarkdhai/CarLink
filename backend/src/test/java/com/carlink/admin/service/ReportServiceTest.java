package com.carlink.admin.service;

import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.dto.ReportDetailResponse;
import com.carlink.admin.dto.ReportSummaryResponse;
import com.carlink.admin.model.Report;
import com.carlink.admin.model.ReportReason;
import com.carlink.admin.model.ReportStatus;
import com.carlink.admin.repository.ReportRepository;
import com.carlink.common.exception.NotFoundException;
import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.model.Message;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.conversation.repository.MessageRepository;
import com.carlink.vehicle.model.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private AuditLogService auditLogService;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(reportRepository, conversationRepository,
                messageRepository, auditLogService);
    }

    // ---------- filing ----------

    @Test
    void filePersistsOpenReportWithReporterIp() {
        Conversation conversation = conversation();
        when(conversationRepository.findById(conversation.getId()))
                .thenReturn(Optional.of(conversation));

        service.file(conversation.getId(), "203.0.113.9", ReportReason.SPAM, "  Spammy   ");

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(saved.getReason()).isEqualTo(ReportReason.SPAM);
        assertThat(saved.getReporterIp()).isEqualTo("203.0.113.9");
        assertThat(saved.getConversation()).isEqualTo(conversation);
        assertThat(saved.getDetails()).isEqualTo("Spammy"); // trimmed
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void fileThrowsNotFoundForMissingConversationAndSavesNothing() {
        UUID missing = UUID.randomUUID();
        when(conversationRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.file(missing, "1.2.3.4", ReportReason.OTHER, "x"))
                .isInstanceOf(NotFoundException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void fileTrimsBlankDetailsToNull() {
        Conversation conversation = conversation();
        when(conversationRepository.findById(conversation.getId()))
                .thenReturn(Optional.of(conversation));

        service.file(conversation.getId(), "1.2.3.4", ReportReason.OTHER, "   ");

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).isNull();
    }

    // ---------- list / get ----------

    @Test
    void listReturnsAllNewestFirstWhenNoStatus() {
        Report r = report();
        when(reportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(r));

        List<ReportSummaryResponse> out = service.list(null);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).id()).isEqualTo(r.getId());
        assertThat(out.get(0).conversationId()).isEqualTo(r.getConversation().getId());
    }

    @Test
    void listFiltersByStatus() {
        Report r = report();
        when(reportRepository.findAllByStatusOrderByCreatedAtDesc(ReportStatus.OPEN))
                .thenReturn(List.of(r));

        List<ReportSummaryResponse> out = service.list(ReportStatus.OPEN);

        assertThat(out).hasSize(1);
    }

    @Test
    void getReturnsDetailWithLastMessageAndCount() {
        Report r = report();
        when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));
        Message first = Message.of(r.getConversation(), "Hello");
        Message last = Message.of(r.getConversation(), "Buy it please");
        when(messageRepository.findAllByConversation_IdOrderByCreatedAtAsc(
                r.getConversation().getId())).thenReturn(List.of(first, last));

        ReportDetailResponse detail = service.get(r.getId());

        assertThat(detail.conversation()).isNotNull();
        assertThat(detail.conversation().messageCount()).isEqualTo(2);
        assertThat(detail.conversation().lastMessageContent()).isEqualTo("Buy it please");
        assertThat(detail.conversation().vehicleNickname()).isEqualTo("My Car");
    }

    @Test
    void getReturnsNullContextWhenConversationDeleted() {
        Report r = report();
        r.setConversation(null);
        when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));

        ReportDetailResponse detail = service.get(r.getId());

        assertThat(detail.conversation()).isNull();
    }

    @Test
    void getThrowsNotFoundForUnknownReport() {
        UUID missing = UUID.randomUUID();
        when(reportRepository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(missing)).isInstanceOf(NotFoundException.class);
    }

    // ---------- status transition ----------

    @Test
    void changeStatusTransitionsAndAudits() {
        Report r = report();
        when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));
        AdminActor actor = new AdminActor(UUID.randomUUID(), "127.0.0.1", "test");

        service.changeStatus(r.getId(), ReportStatus.REVIEWED, actor);

        assertThat(r.getStatus()).isEqualTo(ReportStatus.REVIEWED);
        verify(auditLogService).record(
                eq("REPORT_STATUS_CHANGE"), eq("REPORT"), eq(r.getId()),
                eq(actor.adminId()), eq(actor.ipAddress()), eq(actor.userAgent()),
                org.mockito.ArgumentMatchers.argThat(details ->
                        "OPEN".equals(details.get("from")) && "REVIEWED".equals(details.get("to"))));
    }

    @Test
    void changeStatusThrowsNotFound() {
        UUID missing = UUID.randomUUID();
        when(reportRepository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changeStatus(missing, ReportStatus.CLOSED, null))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- helpers ----------

    private static Conversation conversation() {
        Vehicle vehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .nickname("My Car")
                .build();
        return Conversation.builder()
                .id(UUID.randomUUID())
                .vehicle(vehicle)
                .channel(Channel.SMS)
                .status(ConversationStatus.SENT)
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .build();
    }

    private static Report report() {
        return Report.file(conversation(), "203.0.113.5", ReportReason.ABUSE, "details");
    }
}