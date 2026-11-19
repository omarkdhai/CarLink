package com.carlink.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email String email,
        // S-14: bounded, so no huge payload reaches BCrypt.
        @NotBlank @Size(max = 256) String password
) {}
