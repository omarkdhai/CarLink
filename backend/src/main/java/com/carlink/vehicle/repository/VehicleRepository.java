package com.carlink.vehicle.repository;

import com.carlink.vehicle.model.Vehicle;
import com.carlink.vehicle.model.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
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
}
