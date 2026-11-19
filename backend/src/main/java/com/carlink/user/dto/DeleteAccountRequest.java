package com.carlink.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Account deletion is confirmed with the current password (security plan S-17). */
public record DeleteAccountRequest(
        @NotBlank @Size(max = 256) String password
) {}
