package com.carlink.qr.service;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.NotFoundException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.qr.dto.QrIssuedResponse;
import com.carlink.qr.dto.QrStatusResponse;
import com.carlink.qr.model.QrCode;
import com.carlink.qr.repository.QrCodeRepository;
import com.carlink.vehicle.model.Vehicle;
import com.carlink.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * QR token lifecycle.
 *
 * <p>Only {@code SHA-256(token)} is ever stored. The raw token is returned to
 * the caller exactly once at issuance. A new QR deactivates the previous one,
 * guaranteeing at most one ACTIVE QR per vehicle (also enforced by a partial
 * unique index in the database).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QrService {

    private final QrCodeRepository qrCodeRepository;
    private final VehicleRepository vehicleRepository;
    private final TokenGenerator tokenGenerator;
    private final QrImageGenerator imageGenerator;
    private final CarLinkProperties properties;

    /**
     * Issues a fresh QR for a vehicle, deactivating any previously active one.
     * The returned {@link QrIssuedResponse} carries the raw token — the only
     * time it is shown.
     */
    @Transactional
    public QrIssuedResponse issue(UUID ownerId, UUID vehicleId) {
        Vehicle vehicle = ownedVehicle(ownerId, vehicleId);
        deactivateAllActive(vehicle.getId());
        // The previous QR is deactivated in memory above; flush it NOW so the
        // DB sees the old row inactive before the new active INSERT lands.
        // Otherwise the partial unique index (one ACTIVE per vehicle) is
        // transiently violated because Hibernate may flush the INSERT first.
        qrCodeRepository.flush();

        String rawToken = tokenGenerator.generateUrlSafe(
                properties.qr().tokenBytes());
        QrCode qr = QrCode.newActive(vehicle, tokenGenerator.sha256(rawToken));
        qrCodeRepository.save(qr);

        // The backend serves the public /c/{token} page, so the QR encodes the
        // API host's route, not the Angular app. (See architecture doc.)
        String publicUrl = properties.baseUrl() + "/c/" + rawToken;
        String image = imageGenerator.pngDataUri(publicUrl, 300);
        return QrIssuedResponse.of(rawToken, publicUrl, image, qr);
    }

    /** Returns the currently active QR's status (never the raw token). */
    public QrStatusResponse current(UUID ownerId, UUID vehicleId) {
        ownedVehicle(ownerId, vehicleId);
        return qrCodeRepository.findByVehicleIdAndActiveTrue(vehicleId)
                .map(QrStatusResponse::from)
                .orElseThrow(() -> new NotFoundException("No active QR for this vehicle"));
    }

    /** Full QR history for a vehicle (order by activation, newest first). */
    public List<QrStatusResponse> history(UUID ownerId, UUID vehicleId) {
        ownedVehicle(ownerId, vehicleId);
        return qrCodeRepository.findAllByVehicleId(vehicleId).stream()
                .sorted((a, b) -> b.getActivatedAt().compareTo(a.getActivatedAt()))
                .map(QrStatusResponse::from)
                .toList();
    }

    /** Deactivates the currently active QR, if any. */
    @Transactional
    public void deactivate(UUID ownerId, UUID vehicleId) {
        ownedVehicle(ownerId, vehicleId);
        deactivateAllActive(vehicleId);
    }

    /** Verifies the vehicle exists and is owned by {@code ownerId}. */
    private Vehicle ownedVehicle(UUID ownerId, UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));
        if (!vehicle.getOwner().getId().equals(ownerId)) {
            throw new NotFoundException("Vehicle not found");
        }
        return vehicle;
    }

    private void deactivateAllActive(UUID vehicleId) {
        qrCodeRepository.findByVehicleIdAndActiveTrue(vehicleId)
                .ifPresent(QrCode::deactivate);
    }
}