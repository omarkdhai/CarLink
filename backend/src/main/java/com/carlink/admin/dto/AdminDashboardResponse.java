package com.carlink.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Admin dashboard for a rolling window of {@code days}: headline KPIs compared
 * with the previous window of the same length, gap-free time series bucketed
 * by {@code granularity}, period breakdowns and an all-time snapshot.
 *
 * <p>Aggregates only — never message content, phone numbers or tokens. Contact
 * activity is reported by reason code and hour, not by text.</p>
 */
public record AdminDashboardResponse(
        int days,
        String granularity,
        String timeZone,
        Instant generatedAt,
        Kpis kpis,
        List<SalesPoint> sales,
        List<GrowthPoint> growth,
        List<ContactPoint> contacts,
        List<Slice> ordersByStatus,
        List<Slice> packageMix,
        List<Slice> governorates,
        List<Slice> contactReasons,
        List<Long> contactsByHour,
        StickerFunnel stickers,
        Snapshot snapshot,
        List<RecentOrder> recentOrders
) {

    /** A metric for the current window and the one just before it. */
    public record Kpi(double value, double previous) {}

    public record Kpis(
            Kpi revenue,
            Kpi deliveredRevenue,
            Kpi orders,
            Kpi averageOrderValue,
            Kpi stickersSold,
            Kpi activations,
            Kpi newUsers,
            Kpi newVehicles,
            Kpi contacts,
            Kpi deliveryRate
    ) {}

    public record SalesPoint(LocalDate bucket, long orders, BigDecimal revenue, long stickers) {}

    public record GrowthPoint(LocalDate bucket, long users, long vehicles, long activations) {}

    public record ContactPoint(LocalDate bucket, long sent, long failed, long other) {}

    public record Slice(String name, long count, BigDecimal amount) {}

    /** All-time sticker lifecycle. */
    public record StickerFunnel(long issued, long everActivated, long bound, long unbound, long deactivated) {}

    /** All-time platform state plus items that need an operator. */
    public record Snapshot(
            long usersTotal,
            long usersActive,
            long vehiclesTotal,
            long vehiclesActive,
            long ordersPending,
            BigDecimal ordersPendingValue,
            long reportsOpen,
            long contactsFailed
    ) {}

    public record RecentOrder(String reference, String customerName, String governorate,
                              BigDecimal totalAmount, String currency, String status, Instant createdAt) {}
}
