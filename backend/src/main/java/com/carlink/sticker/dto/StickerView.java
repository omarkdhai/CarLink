package com.carlink.sticker.dto;

import com.carlink.sticker.model.Sticker;
import com.carlink.sticker.model.StickerStatus;
import com.carlink.vehicle.dto.VehicleResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * Owner-facing view of one of their stickers. The raw token is deliberately
 * absent (it was shown once at order creation and only the hash is stored);
 * the license plate is safe here because this response only ever goes to the
 * sticker's owner.
 */
public record StickerView(
        UUID id,
        String status,
        Instant boundAt,
        Instant deactivatedAt,
        VehicleResponse vehicle,
        String orderReference
) {

    public static StickerView from(Sticker sticker) {
        return new StickerView(
                sticker.getId(),
                sticker.getStatus().name(),
                sticker.getBoundAt(),
                sticker.getDeactivatedAt(),
                sticker.getVehicle() == null ? null : VehicleResponse.from(sticker.getVehicle()),
                sticker.getOrder() == null ? null : sticker.getOrder().getReference());
    }
}