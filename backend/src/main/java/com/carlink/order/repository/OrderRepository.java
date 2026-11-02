package com.carlink.order.repository;

import com.carlink.order.model.Order;
import com.carlink.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByReference(String reference);

    Optional<Order> findByReference(String reference);

    long countByStatus(OrderStatus status);

    long countByCreatedAtBetween(Instant start, Instant end);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o")
    BigDecimal sumRevenue();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.createdAt >= :since")
    BigDecimal sumRevenueSince(Instant since);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.createdAt >= :start AND o.createdAt < :end")
    BigDecimal sumRevenueBetween(Instant start, Instant end);

    @Query("SELECT o.governorate as governorate, COUNT(o) as count, COALESCE(SUM(o.totalAmount), 0) as revenue FROM Order o GROUP BY o.governorate ORDER BY COUNT(o) DESC")
    List<GovernorateSalesProjection> findGovernorateSales();

    interface GovernorateSalesProjection {
        com.carlink.order.model.Governorate getGovernorate();
        long getCount();
        BigDecimal getRevenue();
    }
}
