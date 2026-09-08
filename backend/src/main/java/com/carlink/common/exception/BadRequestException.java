package com.carlink.common.exception;

/**
 * Thrown when a request is invalid or violates business rules.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super("BAD_REQUEST", 400, message);
    }
}