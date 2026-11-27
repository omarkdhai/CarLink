package com.carlink.sticker.repository;

import com.carlink.sticker.model.Sticker;
import com.carlink.sticker.model.StickerStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sticker persistence. Only the token hash is ever stored; lookups happen via
 * the hash, never the raw token.
 */
public interface StickerRepository extends JpaRepository<Sticker, UUID> {

    Optional<Sticker> findByTokenHash(String tokenHash);

    /**
     * Row-level write lock taken during activation so two concurrent claims of
     * the same sticker cannot both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sticker s where s.tokenHash = :tokenHash")
    Optional<Sticker> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<Sticker> findByActivationCodeHash(String activationCodeHash);

    /**
     * Row-level write lock taken during registration/activation by short code,
     * so two concurrent claims of the same sticker cannot both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sticker s where s.activationCodeHash = :activationCodeHash")
    Optional<Sticker> findByActivationCodeHashForUpdate(
            @Param("activationCodeHash") String activationCodeHash);

    List<Sticker> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<Sticker> findByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId);

    /** BOUND stickers of {@code ownerId} per order, as {@code [orderId, count]} rows. */
    @Query("""
            select s.order.id, count(s) from Sticker s
             where s.owner.id = :ownerId
               and s.status = com.carlink.sticker.model.StickerStatus.BOUND
               and s.order.id in :orderIds
             group by s.order.id""")
    List<Object[]> countBoundByOrderIds(@Param("ownerId") UUID ownerId,
                                        @Param("orderIds") Collection<UUID> orderIds);

    /** Shared WHERE clause of {@link #adminSearch}; o/u/v are the order, owner and vehicle aliases. */
    String ADMIN_FILTER = """
            where (:q is null
                     or lower(cast(s.id as text)) like lower(concat(cast(:q as text), '%'))
                     or lower(coalesce(o.reference, '')) like lower(concat('%', cast(:q as text), '%'))
                     or lower(coalesce(o.customerName, '')) like lower(concat('%', cast(:q as text), '%'))
                     or lower(coalesce(u.email, '')) like lower(concat('%', cast(:q as text), '%'))
                     or lower(coalesce(u.firstName, '')) like lower(concat('%', cast(:q as text), '%'))
                     or lower(coalesce(u.lastName, '')) like lower(concat('%', cast(:q as text), '%'))
                     or lower(coalesce(v.licensePlate, '')) like lower(concat('%', cast(:q as text), '%')))
              and (:status is null or s.status = :status)""";

    /**
     * Admin sticker search: optional freetext over sticker id prefix, order
     * reference/customer, owner email/name and plate, optional status filter.
     * Order, owner and vehicle are fetched in the same query (no N+1).
     */
    @Query(value = "select s from Sticker s left join fetch s.order o left join fetch s.owner u "
            + "left join fetch s.vehicle v " + ADMIN_FILTER,
            countQuery = "select count(s) from Sticker s left join s.order o left join s.owner u "
                    + "left join s.vehicle v " + ADMIN_FILTER)
    Page<Sticker> adminSearch(@Param("q") String q,
                              @Param("status") StickerStatus status,
                              Pageable pageable);

    long countByStatus(StickerStatus status);

    /** Admin: number of BOUND stickers per vehicle, as {@code [vehicleId, count]} rows. */
    @Query("""
            select s.vehicle.id, count(s) from Sticker s
            where s.vehicle.id in :vehicleIds
              and s.status = com.carlink.sticker.model.StickerStatus.BOUND
            group by s.vehicle.id""")
    List<Object[]> countBoundByVehicleIds(@Param("vehicleIds") Collection<UUID> vehicleIds);

    /**
     * Account deletion (security plan S-17): every sticker of the owner becomes
     * claimable again, with no trace of who held it.
     */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("""
            update Sticker s
               set s.status = com.carlink.sticker.model.StickerStatus.DEACTIVATED,
                   s.vehicle = null, s.owner = null, s.deactivatedAt = :now
             where s.owner.id = :ownerId""")
    int releaseAllOwnedBy(@Param("ownerId") UUID ownerId, @Param("now") java.time.Instant now);
}