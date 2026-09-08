package com.carlink.security.ratelimit;

/**
 * Sliding concept of per-key request limiting. Implementations must be
 * thread-safe and never leak the limited subject (IP, QR token, …) into
 * results beyond the boolean verdict.
 */
public interface RateLimiter {

    /**
     * Consumes one allowance for {@code key} within {@code windowSeconds}.
     *
     * @return {@code true} if the request is permitted, {@code false} if the
     *         limit is exceeded (caller should return 429).
     */
    boolean tryAcquire(String key, int max, int windowSeconds);

    /** Seconds until the caller may retry, for building a Retry-After header. */
    long retryAfterSeconds(String key, int max, int windowSeconds);
}