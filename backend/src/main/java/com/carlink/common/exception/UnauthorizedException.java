package com.carlink.common.exception;

/**
 * Thrown when an operation requires an authenticated principal.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", 401, message);
    }
}