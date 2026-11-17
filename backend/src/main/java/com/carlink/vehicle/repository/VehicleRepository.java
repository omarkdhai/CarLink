package com.carlink.vehicle.repository;

import com.carlink.vehicle.model.Vehicle;
import com.carlink.vehicle.model.VehicleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Vehicle persistence. Ownership filtering happens at the service layer to
 * guarantee the returned rows always belong to the acting user.
 */
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByOwnerIdAndStatusOrderByCreatedAtDesc(
            UUID ownerId, VehicleStatus status);

    long countByOwnerId(UUID ownerId);

    long countByStatus(VehicleStatus status);

    /**
     * Finds the most recent ACTIVE vehicle of {@code ownerId} whose license
     * plate matches (case-insensitive). Used by sticker activation to reuse an
     * existing fleet entry instead of minting a duplicate vehicle per sticker.
     */
    Optional<Vehicle> findFirstByOwnerIdAndStatusAndLicensePlateIgnoreCaseOrderByCreatedAtDesc(
            UUID ownerId, VehicleStatus status, String licensePlate);

    /** Shared WHERE clause of {@link #adminSearch}; {@code o} is the owner alias. */
    String ADMIN_FILTER = """
            where (:q is null
                     or lower(v.licensePlate) like lower(concat('%', cast(:q as text), '%'))
                     or lower(coalesce(v.nickname, '')) like lower(concat('%', cast(:q as text), '%'))
                     or lower(coalesce(v.brand, '')) like lower(concat('%', cast(:q as text), '%'))
                     or lower(coalesce(v.model, '')) like lower(concat('%', cast(:q as text), '%'))
                     or lower(o.email) like lower(concat('%', cast(:q as text), '%'))
                     or lower(coalesce(o.firstName, '')) like lower(concat('%', cast(:q as text), '%'))
                     or lower(coalesce(o.lastName, '')) like lower(concat('%', cast(:q as text), '%')))
              and (:status is null or v.status = :status)""";

    /**
     * Admin fleet search across every owner: optional freetext over plate,
     * nickname, brand, model and owner email/name, optional status filter.
     * The owner is fetched in the same query so a page renders without N+1.
     */
    @Query(value = "select v from Vehicle v join fetch v.owner o " + ADMIN_FILTER,
            countQuery = "select count(v) from Vehicle v join v.owner o " + ADMIN_FILTER)
    Page<Vehicle> adminSearch(@Param("q") String q,
                              @Param("status") VehicleStatus status,
                              Pageable pageable);
}
