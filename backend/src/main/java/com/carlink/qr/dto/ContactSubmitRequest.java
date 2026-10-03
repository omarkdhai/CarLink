package com.carlink.qr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload from the public page's contact form. The {@code reason} is the
 * problem the passenger picked; {@code message} is the optional note to the
 * owner (may be blank). Length capped to keep the relay safe and legible
 * (security model: message length cap).
 */
public record ContactSubmitRequest(
        @NotBlank @Pattern(regexp = "WHATSAPP|SMS", message = "channel must be WHATSAPP or SMS")
        String channel,
        @Size(max = 500, message = "message must be at most 500 characters")
        String message,
        @NotBlank @Pattern(regexp = "BLOCKING|LIGHTS|LEFT_OPEN|HIT_CAR|WINDOWS_OPEN|EV_CHARGE", message = "invalid reason")
        String reason
) {}