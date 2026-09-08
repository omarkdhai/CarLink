package com.carlink.qr.repository;

import com.carlink.qr.model.QrCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * QR persistence. Lookups are always scoped by vehicle so the service can
 * verify ownership before touching a vehicle's QR codes.
 */
public interface QrCodeRepository extends JpaRepository<QrCode, UUID> {

    Optional<QrCode> findByVehicleIdAndActiveTrue(UUID vehicleId);

    List<QrCode> findAllByVehicleId(UUID vehicleId);

    boolean existsByVehicleIdAndActiveTrue(UUID vehicleId);
}