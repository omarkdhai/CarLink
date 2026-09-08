package com.carlink.common.exception;

import lombok.Getter;

/**
 * Base exception for all application errors.
 * Carries an error code and an HTTP status for the global handler.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final String code;
    private final int status;

    protected ApiException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}