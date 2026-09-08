package com.carlink.admin.service;

import com.carlink.admin.dto.AdminStatsResponse;
import com.carlink.admin.model.ReportStatus;
import com.carlink.admin.repository.ReportRepository;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.conversation.repository.MessageRepository;
import com.carlink.qr.repository.QrCodeRepository;
import com.carlink.user.model.Role;
import com.carlink.user.repository.UserRepository;
import com.carlink.vehicle.model.VehicleStatus;
import com.carlink.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private QrCodeRepository qrCodeRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private ReportRepository reportRepository;

    private AdminStatsService service;

    @BeforeEach
    void setUp() {
        service = new AdminStatsService(userRepository, vehicleRepository,
                qrCodeRepository, conversationRepository, messageRepository, reportRepository);
    }

    @Test
    void overviewAggregatesCounts() {
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByActive(true)).thenReturn(7L);
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);
        when(vehicleRepository.count()).thenReturn(4L);
        when(vehicleRepository.countByStatus(VehicleStatus.ACTIVE)).thenReturn(3L);
        when(qrCodeRepository.count()).thenReturn(6L);
        when(qrCodeRepository.countByActive(true)).thenReturn(5L);
        when(conversationRepository.count()).thenReturn(12L);
        when(conversationRepository.countByStatus(ConversationStatus.PENDING)).thenReturn(1L);
        when(conversationRepository.countByStatus(ConversationStatus.SENT)).thenReturn(8L);
        when(conversationRepository.countByStatus(ConversationStatus.FAILED)).thenReturn(1L);
        when(conversationRepository.countByStatus(ConversationStatus.EXPIRED)).thenReturn(2L);
        when(messageRepository.count()).thenReturn(30L);
        when(reportRepository.count()).thenReturn(4L);
        when(reportRepository.countByStatus(ReportStatus.OPEN)).thenReturn(2L);
        when(reportRepository.countByStatus(ReportStatus.REVIEWED)).thenReturn(1L);
        when(reportRepository.countByStatus(ReportStatus.CLOSED)).thenReturn(1L);

        AdminStatsResponse overview = service.overview();

        assertThat(overview.usersTotal()).isEqualTo(10);
        assertThat(overview.usersActive()).isEqualTo(7);
        assertThat(overview.usersAdmins()).isEqualTo(2);
        assertThat(overview.vehiclesTotal()).isEqualTo(4);
        assertThat(overview.vehiclesActive()).isEqualTo(3);
        assertThat(overview.qrTotal()).isEqualTo(6);
        assertThat(overview.qrActive()).isEqualTo(5);
        assertThat(overview.conversationsTotal()).isEqualTo(12);
        assertThat(overview.conversationsPending()).isEqualTo(1);
        assertThat(overview.conversationsSent()).isEqualTo(8);
        assertThat(overview.conversationsFailed()).isEqualTo(1);
        assertThat(overview.conversationsExpired()).isEqualTo(2);
        assertThat(overview.messagesTotal()).isEqualTo(30);
        assertThat(overview.reportsOpen()).isEqualTo(2);
        assertThat(overview.reportsReviewed()).isEqualTo(1);
        assertThat(overview.reportsClosed()).isEqualTo(1);
        assertThat(overview.reportsOpenVsTotal()).isEqualTo(0.5);
    }

    @Test
    void overviewOpenVsTotalRatioIsZeroWhenNoReports() {
        when(userRepository.count()).thenReturn(1L);
        when(vehicleRepository.count()).thenReturn(0L);
        when(qrCodeRepository.count()).thenReturn(0L);
        when(conversationRepository.count()).thenReturn(0L);
        when(messageRepository.count()).thenReturn(0L);
        // count() -> 0, but the ratio protects against divide-by-zero.
        when(reportRepository.count()).thenReturn(0L);
        when(reportRepository.countByStatus(ReportStatus.OPEN)).thenReturn(0L);
        when(vehicleRepository.countByStatus(VehicleStatus.ACTIVE)).thenReturn(0L);
        when(qrCodeRepository.countByActive(true)).thenReturn(0L);

        AdminStatsResponse overview = service.overview();

        assertThat(overview.reportsOpenVsTotal()).isEqualTo(0.0);
    }
}