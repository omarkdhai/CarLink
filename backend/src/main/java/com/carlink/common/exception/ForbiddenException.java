package com.carlink.common.exception;

/**
 * Thrown when an operation is not permitted for the current principal,
 * or a resource belongs to another owner.
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", 403, message);
    }
}