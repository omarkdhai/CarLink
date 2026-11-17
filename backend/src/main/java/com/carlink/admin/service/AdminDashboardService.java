package com.carlink.admin.service;

import com.carlink.admin.dto.AdminDashboardResponse;
import com.carlink.admin.dto.AdminDashboardResponse.ContactPoint;
import com.carlink.admin.dto.AdminDashboardResponse.GrowthPoint;
import com.carlink.admin.dto.AdminDashboardResponse.Kpi;
import com.carlink.admin.dto.AdminDashboardResponse.Kpis;
import com.carlink.admin.dto.AdminDashboardResponse.RecentOrder;
import com.carlink.admin.dto.AdminDashboardResponse.SalesPoint;
import com.carlink.admin.dto.AdminDashboardResponse.Slice;
import com.carlink.admin.dto.AdminDashboardResponse.Snapshot;
import com.carlink.admin.dto.AdminDashboardResponse.StickerFunnel;
import com.carlink.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the admin dashboard with one grouped SQL query per figure (no
 * per-day loops). Timestamps are stored as UTC ({@code hibernate.jdbc.time_zone});
 * buckets and "today" are computed in the business time zone (Tunisia).
 * Revenue never counts cancelled orders.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    public static final Set<Integer> ALLOWED_DAYS = Set.of(7, 30, 90, 365);
    static final String TIME_ZONE = "Africa/Tunis";
    private static final ZoneId ZONE = ZoneId.of(TIME_ZONE);

    private final NamedParameterJdbcTemplate jdbc;

    public AdminDashboardResponse dashboard(int days) {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new BadRequestException("days must be one of 7, 30, 90 or 365");
        }
        String granularity = days <= 30 ? "day" : days <= 90 ? "week" : "month";

        LocalDate today = LocalDate.now(ZONE);
        LocalDate fromDate = today.minusDays(days - 1L);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", utc(fromDate))
                .addValue("prevFrom", utc(fromDate.minusDays(days)));
        List<LocalDate> buckets = buckets(fromDate, today, granularity);

        return new AdminDashboardResponse(
                days,
                granularity,
                TIME_ZONE,
                Instant.now(),
                kpis(p),
                sales(p, granularity, buckets),
                growth(p, granularity, buckets),
                contacts(p, granularity, buckets),
                slices("""
                        select status as name, count(*) as n, coalesce(sum(total_amount), 0) as amount
                        from orders where created_at >= :from group by status order by n desc""", p),
                slices("""
                        select i.sticker_package as name, sum(i.quantity) as n, sum(i.subtotal) as amount
                        from order_items i join orders o on o.id = i.order_id
                        where o.created_at >= :from and o.status <> 'CANCELLED'
                        group by i.sticker_package order by amount desc""", p),
                slices("""
                        select governorate as name, count(*) as n,
                               coalesce(sum(total_amount) filter (where status <> 'CANCELLED'), 0) as amount
                        from orders where created_at >= :from
                        group by governorate order by amount desc, n desc limit 8""", p),
                slices("""
                        select reason as name, count(*) as n, 0 as amount
                        from messages where created_at >= :from
                        group by reason order by n desc""", p),
                contactsByHour(p),
                stickerFunnel(),
                snapshot(p),
                recentOrders());
    }

    // ---------- KPIs ----------

    private Kpis kpis(MapSqlParameterSource p) {
        return jdbc.queryForObject("""
                select
                  (select count(*) from orders where created_at >= :from) as orders_c,
                  (select count(*) from orders where created_at >= :prevFrom and created_at < :from) as orders_p,
                  (select coalesce(sum(total_amount), 0) from orders
                     where created_at >= :from and status <> 'CANCELLED') as rev_c,
                  (select coalesce(sum(total_amount), 0) from orders
                     where created_at >= :prevFrom and created_at < :from and status <> 'CANCELLED') as rev_p,
                  (select count(*) from orders where created_at >= :from and status <> 'CANCELLED') as valid_c,
                  (select count(*) from orders
                     where created_at >= :prevFrom and created_at < :from and status <> 'CANCELLED') as valid_p,
                  (select coalesce(sum(total_amount), 0) from orders
                     where created_at >= :from and status = 'DELIVERED') as cash_c,
                  (select coalesce(sum(total_amount), 0) from orders
                     where created_at >= :prevFrom and created_at < :from and status = 'DELIVERED') as cash_p,
                  (select coalesce(sum(i.quantity * i.stickers_per_pack), 0) from order_items i
                     join orders o on o.id = i.order_id
                     where o.created_at >= :from and o.status <> 'CANCELLED') as stk_c,
                  (select coalesce(sum(i.quantity * i.stickers_per_pack), 0) from order_items i
                     join orders o on o.id = i.order_id
                     where o.created_at >= :prevFrom and o.created_at < :from and o.status <> 'CANCELLED') as stk_p,
                  (select count(*) from stickers where bound_at >= :from) as act_c,
                  (select count(*) from stickers where bound_at >= :prevFrom and bound_at < :from) as act_p,
                  (select count(*) from users where created_at >= :from) as users_c,
                  (select count(*) from users where created_at >= :prevFrom and created_at < :from) as users_p,
                  (select count(*) from vehicles where created_at >= :from) as veh_c,
                  (select count(*) from vehicles where created_at >= :prevFrom and created_at < :from) as veh_p,
                  (select count(*) from conversations where created_at >= :from) as conv_c,
                  (select count(*) from conversations where created_at >= :prevFrom and created_at < :from) as conv_p,
                  (select count(*) from conversations where created_at >= :from and status = 'SENT') as sent_c,
                  (select count(*) from conversations
                     where created_at >= :prevFrom and created_at < :from and status = 'SENT') as sent_p,
                  (select count(*) from conversations where created_at >= :from and status = 'FAILED') as fail_c,
                  (select count(*) from conversations
                     where created_at >= :prevFrom and created_at < :from and status = 'FAILED') as fail_p
                """, p, (rs, i) -> new Kpis(
                kpi(rs, "rev_c", "rev_p"),
                kpi(rs, "cash_c", "cash_p"),
                kpi(rs, "orders_c", "orders_p"),
                new Kpi(ratio(rs.getDouble("rev_c"), rs.getDouble("valid_c")),
                        ratio(rs.getDouble("rev_p"), rs.getDouble("valid_p"))),
                kpi(rs, "stk_c", "stk_p"),
                kpi(rs, "act_c", "act_p"),
                kpi(rs, "users_c", "users_p"),
                kpi(rs, "veh_c", "veh_p"),
                kpi(rs, "conv_c", "conv_p"),
                new Kpi(percent(rs.getDouble("sent_c"), rs.getDouble("sent_c") + rs.getDouble("fail_c")),
                        percent(rs.getDouble("sent_p"), rs.getDouble("sent_p") + rs.getDouble("fail_p")))));
    }

    // ---------- time series ----------

    private List<SalesPoint> sales(MapSqlParameterSource p, String g, List<LocalDate> buckets) {
        Map<LocalDate, ResultRow> orders = series("select " + bucket("created_at", g) + """
                 as b, count(*) as orders,
                  coalesce(sum(total_amount) filter (where status <> 'CANCELLED'), 0) as revenue
                from orders where created_at >= :from group by 1""", p);
        Map<LocalDate, ResultRow> stickers = series("select " + bucket("o.created_at", g) + """
                 as b, coalesce(sum(i.quantity * i.stickers_per_pack), 0) as stickers
                from order_items i join orders o on o.id = i.order_id
                where o.created_at >= :from and o.status <> 'CANCELLED' group by 1""", p);
        return buckets.stream().map(b -> new SalesPoint(b,
                ResultRow.of(orders, b).longValue("orders"),
                ResultRow.of(orders, b).decimal("revenue"),
                ResultRow.of(stickers, b).longValue("stickers"))).toList();
    }

    private List<GrowthPoint> growth(MapSqlParameterSource p, String g, List<LocalDate> buckets) {
        Map<LocalDate, ResultRow> users = countSeries("users", "created_at", g, p);
        Map<LocalDate, ResultRow> vehicles = countSeries("vehicles", "created_at", g, p);
        Map<LocalDate, ResultRow> activations = countSeries("stickers", "bound_at", g, p);
        return buckets.stream().map(b -> new GrowthPoint(b,
                ResultRow.of(users, b).longValue("n"),
                ResultRow.of(vehicles, b).longValue("n"),
                ResultRow.of(activations, b).longValue("n"))).toList();
    }

    private List<ContactPoint> contacts(MapSqlParameterSource p, String g, List<LocalDate> buckets) {
        Map<LocalDate, ResultRow> rows = series("select " + bucket("created_at", g) + """
                 as b,
                  count(*) filter (where status = 'SENT') as sent,
                  count(*) filter (where status = 'FAILED') as failed,
                  count(*) filter (where status not in ('SENT', 'FAILED')) as other
                from conversations where created_at >= :from group by 1""", p);
        return buckets.stream().map(b -> new ContactPoint(b,
                ResultRow.of(rows, b).longValue("sent"),
                ResultRow.of(rows, b).longValue("failed"),
                ResultRow.of(rows, b).longValue("other"))).toList();
    }

    private Map<LocalDate, ResultRow> countSeries(String table, String column, String g,
                                                  MapSqlParameterSource p) {
        // table/column are compile-time constants from this class, never request input.
        return series("select " + bucket(column, g) + " as b, count(*) as n from " + table
                + " where " + column + " >= :from group by 1", p);
    }

    private List<Long> contactsByHour(MapSqlParameterSource p) {
        Long[] hours = new Long[24];
        Arrays.fill(hours, 0L);
        jdbc.query("select extract(hour from " + local("created_at") + ")::int as h, count(*) as n "
                        + "from conversations where created_at >= :from group by 1", p,
                (RowCallbackHandler) rs -> hours[rs.getInt("h")] = rs.getLong("n"));
        return List.of(hours);
    }

    // ---------- breakdowns & snapshot ----------

    private List<Slice> slices(String sql, MapSqlParameterSource p) {
        return jdbc.query(sql, p, (rs, i) -> new Slice(
                rs.getString("name"), rs.getLong("n"), money(rs.getBigDecimal("amount"))));
    }

    private StickerFunnel stickerFunnel() {
        return jdbc.queryForObject("""
                select count(*) as issued,
                       count(*) filter (where bound_at is not null) as ever,
                       count(*) filter (where status = 'BOUND') as bound,
                       count(*) filter (where status = 'UNBOUND') as unbound,
                       count(*) filter (where status = 'DEACTIVATED') as deactivated
                from stickers""", new MapSqlParameterSource(), (rs, i) -> new StickerFunnel(
                rs.getLong("issued"), rs.getLong("ever"), rs.getLong("bound"),
                rs.getLong("unbound"), rs.getLong("deactivated")));
    }

    private Snapshot snapshot(MapSqlParameterSource p) {
        return jdbc.queryForObject("""
                select
                  (select count(*) from users) as users_total,
                  (select count(*) from users where is_active) as users_active,
                  (select count(*) from vehicles) as veh_total,
                  (select count(*) from vehicles where status = 'ACTIVE') as veh_active,
                  (select count(*) from orders where status = 'PLACED') as pending,
                  (select coalesce(sum(total_amount), 0) from orders where status = 'PLACED') as pending_value,
                  (select count(*) from reports where status = 'OPEN') as reports_open,
                  (select count(*) from conversations where created_at >= :from and status = 'FAILED') as failed
                """, p, (rs, i) -> new Snapshot(
                rs.getLong("users_total"), rs.getLong("users_active"),
                rs.getLong("veh_total"), rs.getLong("veh_active"),
                rs.getLong("pending"), money(rs.getBigDecimal("pending_value")),
                rs.getLong("reports_open"), rs.getLong("failed")));
    }

    private List<RecentOrder> recentOrders() {
        return jdbc.query("""
                select reference, customer_name, governorate, total_amount, currency, status,
                       created_at at time zone 'UTC' as created_at
                from orders order by created_at desc limit 6""", new MapSqlParameterSource(),
                (rs, i) -> new RecentOrder(
                        rs.getString("reference"), rs.getString("customer_name"), rs.getString("governorate"),
                        money(rs.getBigDecimal("total_amount")), rs.getString("currency"), rs.getString("status"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()));
    }

    // ---------- helpers ----------

    private Map<LocalDate, ResultRow> series(String sql, MapSqlParameterSource p) {
        Map<LocalDate, ResultRow> out = new HashMap<>();
        jdbc.query(sql, p, (RowCallbackHandler) rs -> {
            Map<String, Object> values = new HashMap<>();
            for (int c = 2; c <= rs.getMetaData().getColumnCount(); c++) {
                values.put(rs.getMetaData().getColumnLabel(c), rs.getObject(c));
            }
            out.put(rs.getObject("b", LocalDate.class), new ResultRow(values));
        });
        return out;
    }

    /** Naive-UTC column converted to business-local wall time. */
    private static String local(String column) {
        return "(" + column + " at time zone 'UTC' at time zone '" + TIME_ZONE + "')";
    }

    private static String bucket(String column, String granularity) {
        return "date_trunc('" + granularity + "', " + local(column) + ")::date";
    }

    /** Every bucket start from {@code from} to {@code to}, so charts have no gaps. */
    static List<LocalDate> buckets(LocalDate from, LocalDate to, String granularity) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate b = switch (granularity) {
            case "week" -> from.with(DayOfWeek.MONDAY);
            case "month" -> from.withDayOfMonth(1);
            default -> from;
        };
        while (!b.isAfter(to)) {
            out.add(b);
            b = switch (granularity) {
                case "week" -> b.plusWeeks(1);
                case "month" -> b.plusMonths(1);
                default -> b.plusDays(1);
            };
        }
        return out;
    }

    /** Local midnight of {@code day} expressed as the naive-UTC value stored in the DB. */
    private static Timestamp utc(LocalDate day) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(day.atStartOfDay(ZONE).toInstant(), ZoneOffset.UTC));
    }

    private static Kpi kpi(ResultSet rs, String current, String previous) throws SQLException {
        return new Kpi(rs.getDouble(current), rs.getDouble(previous));
    }

    private static double ratio(double a, double b) {
        return b == 0 ? 0 : BigDecimal.valueOf(a / b).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double percent(double part, double total) {
        return total == 0 ? 0 : BigDecimal.valueOf(part * 100 / total).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** One aggregated row of a series query; missing buckets read as zero. */
    private record ResultRow(Map<String, Object> values) {

        private static final ResultRow EMPTY = new ResultRow(Map.of());

        static ResultRow of(Map<LocalDate, ResultRow> rows, LocalDate bucket) {
            return rows.getOrDefault(bucket, EMPTY);
        }

        long longValue(String key) {
            Object v = values.get(key);
            return v == null ? 0 : ((Number) v).longValue();
        }

        BigDecimal decimal(String key) {
            Object v = values.get(key);
            return v == null ? BigDecimal.ZERO.setScale(2) : money(new BigDecimal(v.toString()));
        }
    }
}
