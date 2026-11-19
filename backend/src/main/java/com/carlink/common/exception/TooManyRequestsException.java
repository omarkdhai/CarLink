package com.carlink.common.exception;

/**
 * Thrown when a rate limit (per-IP or per-QR token) is exceeded on the
 * public endpoint. Rendered as 429 with a Retry-After header.
 */
public class TooManyRequestsException extends ApiException {

    /** Seconds the client should wait before retrying. */
    private final long retryAfterSeconds;

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super("RATE_LIMIT_EXCEEDED", 429, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** A 429 with its own error code, so the client can explain which limit applies. */
    public TooManyRequestsException(String code, String message, long retryAfterSeconds) {
        super(code, 429, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}