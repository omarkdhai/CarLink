package com.carlink.qr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload from the public page's contact form. Message length is capped to
 * keep the relay safe and legible (security model: message length cap).
 */
public record ContactSubmitRequest(
        @NotBlank @Pattern(regexp = "WHATSAPP|SMS", message = "channel must be WHATSAPP or SMS")
        String channel,
        @NotBlank @Size(min = 1, max = 500, message = "message must be 1-500 characters")
        String message
) {}