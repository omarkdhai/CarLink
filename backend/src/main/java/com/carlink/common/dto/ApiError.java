package com.carlink.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * Standard error payload returned by the global exception handler.
 *
 * @param timestamp when the error occurred (UTC)
 * @param status    HTTP status
 * @param code      stable, machine-readable error code
 * @param message   human-readable message (never includes sensitive data)
 * @param fieldErrors binding/validation errors, when applicable
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {}

    public static ApiError of(int status, String code, String message) {
        return new ApiError(Instant.now(), status, code, message, null);
    }

    public static ApiError withFieldErrors(int status, String code, String message,
                                           List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), status, code, message, fieldErrors);
    }
}