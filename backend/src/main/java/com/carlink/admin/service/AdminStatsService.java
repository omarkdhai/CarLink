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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates platform-level counters for the admin analytics overview.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final QrCodeRepository qrCodeRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;

    @Transactional(readOnly = true)
    public AdminStatsResponse overview() {
        long usersTotal = userRepository.count();
        long reportsTotal = Math.max(1, reportRepository.count());
        return new AdminStatsResponse(
                usersTotal,
                userRepository.countByActive(true),
                userRepository.countByRole(Role.ADMIN),
                vehicleRepository.count(),
                vehicleRepository.countByStatus(VehicleStatus.ACTIVE),
                qrCodeRepository.count(),
                qrCodeRepository.countByActive(true),
                conversationRepository.count(),
                conversationRepository.countByStatus(ConversationStatus.PENDING),
                conversationRepository.countByStatus(ConversationStatus.SENT),
                conversationRepository.countByStatus(ConversationStatus.FAILED),
                conversationRepository.countByStatus(ConversationStatus.EXPIRED),
                messageRepository.count(),
                reportRepository.countByStatus(ReportStatus.OPEN),
                reportRepository.countByStatus(ReportStatus.REVIEWED),
                reportRepository.countByStatus(ReportStatus.CLOSED),
                (double) reportRepository.countByStatus(ReportStatus.OPEN) / reportsTotal);
    }
}