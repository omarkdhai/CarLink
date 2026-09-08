package com.carlink.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a new vehicle. {@code licensePlate} is required;
 * all other details are optional.
 */
public record CreateVehicleRequest(
        @Size(max = 100) String nickname,
        @Size(max = 100) String brand,
        @Size(max = 100) String model,
        @Size(max = 50) String color,
        @NotBlank @Size(max = 20) String licensePlate
) {}
