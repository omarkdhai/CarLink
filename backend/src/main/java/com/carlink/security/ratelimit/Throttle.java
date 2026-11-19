package com.carlink.security.ratelimit;

import com.carlink.common.exception.TooManyRequestsException;
import com.carlink.common.security.TokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Thin, intention-revealing wrapper over {@link RateLimiter}. Subjects (IPs,
 * e-mails, user ids) are normalised and SHA-256 hashed before they become a
 * Redis key, so no raw e-mail or address is ever stored in Redis.
 */
@Component
@RequiredArgsConstructor
public class Throttle {

    static final String MESSAGE = "Too many requests. Try again later.";

    private final RateLimiter limiter;
    private final TokenGenerator tokens;

    /** Counts one request; throws 429 (with Retry-After) once the limit is exceeded. */
    public void require(String bucket, ThrottleProperties.Limit limit, String subject) {
        String key = key(bucket, subject);
        if (!limiter.tryAcquire(key, limit.getMax(), limit.getWindowSeconds())) {
            throw new TooManyRequestsException(MESSAGE,
                    limiter.retryAfterSeconds(key, limit.getMax(), limit.getWindowSeconds()));
        }
    }

    /** Like {@link #require(String, ThrottleProperties.Limit, String)}, with a specific error code. */
    public void require(String bucket, ThrottleProperties.Limit limit, String subject,
                        String code, String message) {
        String key = key(bucket, subject);
        if (!limiter.tryAcquire(key, limit.getMax(), limit.getWindowSeconds())) {
            throw new TooManyRequestsException(code, message,
                    limiter.retryAfterSeconds(key, limit.getMax(), limit.getWindowSeconds()));
        }
    }

    /** Counts one request; returns false once the limit is exceeded (for silent drops). */
    public boolean allow(String bucket, ThrottleProperties.Limit limit, String subject) {
        return limiter.tryAcquire(key(bucket, subject), limit.getMax(), limit.getWindowSeconds());
    }

    private String key(String bucket, String subject) {
        String normalised = subject == null ? "" : subject.trim().toLowerCase();
        return "throttle:" + bucket + ":" + tokens.sha256(normalised);
    }
}
