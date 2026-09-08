package com.carlink.qr.dto;

import com.carlink.qr.model.QrCode;

import java.time.Instant;
import java.util.UUID;

/**
 * QR record visible on later reads. The raw token is deliberately absent —
 * it was shown once at issuance and only {@code tokenHash} is stored.
 */
public record QrStatusResponse(
        UUID id,
        UUID vehicleId,
        boolean active,
        Instant activatedAt,
        Instant deactivatedAt
) {

    public static QrStatusResponse from(QrCode qr) {
        return new QrStatusResponse(
                qr.getId(),
                qr.getVehicle().getId(),
                qr.isActive(),
                qr.getActivatedAt(),
                qr.getDeactivatedAt());
    }
}