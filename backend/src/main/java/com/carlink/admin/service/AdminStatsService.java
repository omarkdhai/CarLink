package com.carlink.admin.service;

import com.carlink.admin.dto.AdminStatsResponse;
import com.carlink.admin.model.ReportStatus;
import com.carlink.admin.repository.ReportRepository;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.conversation.repository.MessageRepository;
import com.carlink.order.model.OrderStatus;
import com.carlink.order.repository.OrderRepository;
import com.carlink.qr.repository.QrCodeRepository;
import com.carlink.sticker.repository.StickerRepository;
import com.carlink.user.model.Role;
import com.carlink.user.repository.UserRepository;
import com.carlink.vehicle.model.VehicleStatus;
import com.carlink.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates platform-level counters for the admin analytics overview.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final QrCodeRepository qrCodeRepository;
    private final StickerRepository stickerRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public AdminStatsResponse overview() {
        long usersTotal = userRepository.count();
        long reportsTotal = Math.max(1, reportRepository.count());
        
        // Sales analytics
        long ordersTotal = orderRepository.count();
        long ordersPlaced = orderRepository.countByStatus(OrderStatus.PLACED);
        long ordersDelivered = orderRepository.countByStatus(OrderStatus.DELIVERED);
        long ordersCancelled = orderRepository.countByStatus(OrderStatus.CANCELLED);

        BigDecimal totalRevenue = orderRepository.sumRevenue();
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        Instant startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        BigDecimal revenueThisMonth = orderRepository.sumRevenueSince(startOfMonth);
        if (revenueThisMonth == null) revenueThisMonth = BigDecimal.ZERO;

        // Monthly sales (last 6 months)
        List<AdminStatsResponse.MonthlySales> monthlySales = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy");
        for (int i = 5; i >= 0; i--) {
            LocalDate monthDate = LocalDate.now().minusMonths(i).withDayOfMonth(1);
            Instant monthStart = monthDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant monthEnd = monthDate.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            long monthOrders = orderRepository.countByCreatedAtBetween(monthStart, monthEnd);
            BigDecimal monthRevenue = orderRepository.sumRevenueBetween(monthStart, monthEnd);
            if (monthRevenue == null) monthRevenue = BigDecimal.ZERO;
            monthlySales.add(new AdminStatsResponse.MonthlySales(monthDate.format(formatter), monthOrders, monthRevenue));
        }

        // Sales by governorate
        List<AdminStatsResponse.GovernorateSales> salesByGovernorate = orderRepository.findGovernorateSales().stream()
                .limit(10)
                .map(r -> new AdminStatsResponse.GovernorateSales(
                        r.getGovernorate() != null ? r.getGovernorate().name() : "UNKNOWN",
                        r.getCount(),
                        r.getRevenue() != null ? r.getRevenue() : BigDecimal.ZERO))
                .toList();

        // Daily registrations (last 30 days)
        List<AdminStatsResponse.DailyRegistrations> dailyRegistrations = new ArrayList<>();
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd/MM");
        for (int i = 29; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            Instant dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant dayEnd = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            long count = userRepository.countByCreatedAtBetween(dayStart, dayEnd);
            dailyRegistrations.add(new AdminStatsResponse.DailyRegistrations(day.format(dayFormatter), count));
        }

        // Hourly activity (conversations created)
        List<AdminStatsResponse.HourlyActivity> hourlyActivity = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            long count = conversationRepository.countByHourOfDay(hour);
            hourlyActivity.add(new AdminStatsResponse.HourlyActivity(hour, count));
        }

        // Conversion rate: users with vehicles / active users
        long usersActive = userRepository.countByActive(true);
        long vehiclesActive = vehicleRepository.countByStatus(VehicleStatus.ACTIVE);
        double conversionRate = usersActive == 0 ? 0.0 : (vehiclesActive * 100.0) / usersActive;
        
        return new AdminStatsResponse(
                usersTotal,
                usersActive,
                userRepository.countByRole(Role.ADMIN),
                vehicleRepository.count(),
                vehiclesActive,
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
                (double) reportRepository.countByStatus(ReportStatus.OPEN) / reportsTotal,
                ordersTotal,
                ordersPlaced,
                ordersDelivered,
                ordersCancelled,
                totalRevenue,
                revenueThisMonth,
                monthlySales,
                salesByGovernorate,
                dailyRegistrations,
                hourlyActivity,
                conversionRate);
    }
}