package com.carlink.sticker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for claiming a sticker by its short printed activation code: the code
 * plus the same car details as {@link ActivateStickerRequest}. Used after
 * registration and by the car-details step of the public registration flow.
 */
public record ActivateStickerByCodeRequest(
        @NotBlank @Size(max = 32) String code,
        @NotNull @Valid ActivateStickerRequest details
) {
}
