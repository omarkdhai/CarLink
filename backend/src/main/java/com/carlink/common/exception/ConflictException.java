package com.carlink.common.exception;

/**
 * Thrown when an operation conflicts with the current state of a resource —
 * for example, claiming a sticker that is already linked to an account.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super("CONFLICT", 409, message);
    }
}