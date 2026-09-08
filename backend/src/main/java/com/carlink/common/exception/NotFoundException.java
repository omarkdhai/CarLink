package com.carlink.common.exception;

/**
 * Thrown when a resource is not found or a token is invalid/expired.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super("NOT_FOUND", 404, message);
    }
}