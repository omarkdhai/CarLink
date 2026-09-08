package com.carlink.vehicle.dto;

import com.carlink.vehicle.model.Vehicle;

import java.time.Instant;
import java.util.UUID;

/**
 * Public vehicle payload. Contains only owner-safe fields — never any
 * phone number or other private owner data.
 */
public record VehicleResponse(
        UUID id,
        String nickname,
        String brand,
        String model,
        String color,
        String licensePlate,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getNickname(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getColor(),
                vehicle.getLicensePlate(),
                vehicle.getStatus().name(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt());
    }
}
