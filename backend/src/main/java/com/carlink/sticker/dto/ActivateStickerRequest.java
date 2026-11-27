package com.carlink.sticker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for claiming a sticker: the car details to attach. License plate is
 * required; the other vehicle fields are optional and mirror the vehicle form.
 * (A {@code birthDate} sent by an older client is ignored.)
 */
public record ActivateStickerRequest(
        @NotBlank @Size(max = 20) String licensePlate,
        @Size(max = 100) String nickname,
        @Size(max = 100) String brand,
        @Size(max = 100) String model,
        @Size(max = 50) String color
) {
}