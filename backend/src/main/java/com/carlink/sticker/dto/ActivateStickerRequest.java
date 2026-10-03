package com.carlink.sticker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body for claiming a sticker: the owner's date of birth (converted from age
 * on the client) and the car details to attach. License plate is required;
 * the other vehicle fields are optional and mirror the vehicle form.
 */
public record ActivateStickerRequest(
        @NotNull LocalDate birthDate,
        @NotBlank @Size(max = 20) String licensePlate,
        @Size(max = 100) String nickname,
        @Size(max = 100) String brand,
        @Size(max = 100) String model,
        @Size(max = 50) String color
) {
}