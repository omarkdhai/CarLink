package com.carlink.common.exception;

/**
 * Thrown when an operation requires an authenticated principal.
 */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", 401, message);
    }

    /** Variant with a custom machine-readable code (e.g. for distinct client handling). */
    public UnauthorizedException(String code, String message) {
        super(code, 401, message);
    }
}