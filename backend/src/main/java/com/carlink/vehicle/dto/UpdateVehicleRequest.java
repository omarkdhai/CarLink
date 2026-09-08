package com.carlink.vehicle.dto;

import jakarta.validation.constraints.Size;

/**
 * Payload for updating vehicle details. Every field is optional so a partial
 * update (PATCH) only touches the provided values.
 */
public record UpdateVehicleRequest(
        @Size(max = 100) String nickname,
        @Size(max = 100) String brand,
        @Size(max = 100) String model,
        @Size(max = 50) String color,
        @Size(max = 20) String licensePlate
) {}
