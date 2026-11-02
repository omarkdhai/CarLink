package com.carlink.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Platform-level counters for the admin analytics overview.
 */
public record AdminStatsResponse(
        long usersTotal,
        long usersActive,
        long usersAdmins,
        long vehiclesTotal,
        long vehiclesActive,
        long qrTotal,
        long qrActive,
        long conversationsTotal,
        long conversationsPending,
        long conversationsSent,
        long conversationsFailed,
        long conversationsExpired,
        long messagesTotal,
        long reportsOpen,
        long reportsReviewed,
        long reportsClosed,
        double reportsOpenVsTotal,
        // Sales analytics
        long ordersTotal,
        long ordersPlaced,
        long ordersDelivered,
        long ordersCancelled,
        BigDecimal totalRevenue,
        BigDecimal revenueThisMonth,
        // Time series
        List<MonthlySales> monthlySales,
        // Geographic
        List<GovernorateSales> salesByGovernorate,
        // User behavior
        List<DailyRegistrations> dailyRegistrations,
        List<HourlyActivity> hourlyActivity,
        double conversionRate
) {
    public record MonthlySales(String month, long orders, BigDecimal revenue) {}
    public record GovernorateSales(String governorate, long count, BigDecimal revenue) {}
    public record DailyRegistrations(String date, long count) {}
    public record HourlyActivity(int hour, long count) {}
}