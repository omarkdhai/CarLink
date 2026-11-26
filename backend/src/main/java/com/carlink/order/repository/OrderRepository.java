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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByReference(String reference);

    Page<Order> findAllByStatus(OrderStatus status, Pageable pageable);

    /**
     * Admin order search: optional freetext over reference, customer name,
     * email and mobile, combined with an optional status filter.
     */
    @Query("""
            select o from Order o
            where (:q is null
                     or lower(o.reference) like lower(concat('%', cast(:q as text), '%'))
                     or lower(o.customerName) like lower(concat('%', cast(:q as text), '%'))
                     or lower(o.email) like lower(concat('%', cast(:q as text), '%'))
                     or o.mobile like concat('%', cast(:q as text), '%'))
              and (:status is null or o.status = :status)""")
    Page<Order> adminSearch(@org.springframework.data.repository.query.Param("q") String q,
                            @org.springframework.data.repository.query.Param("status") OrderStatus status,
                            Pageable pageable);

    Optional<Order> findByReference(String reference);

    /**
     * An owner's purchases: orders placed with their (verified) e-mail, or that
     * contain a sticker they own. {@code email} is null when it isn't verified.
     */
    @Query("""
            select o from Order o
            where (cast(:email as text) is not null and lower(o.email) = lower(cast(:email as text)))
               or exists (select 1 from Sticker s where s.order = o and s.owner.id = :ownerId)
            order by o.createdAt desc""")
    List<Order> findForAccount(@org.springframework.data.repository.query.Param("email") String email,
                               @org.springframework.data.repository.query.Param("ownerId") UUID ownerId,
                               Pageable pageable);


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
