package com.carlink.auth.service;

import com.carlink.common.config.CarLinkProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Brute-force protection for /login. Counts failures per normalized email and
 * temporarily blocks further attempts after a configurable threshold.
 *
 * <p>Redis keys use the SHA-256 of the email so the raw address is not stored
 * in the cache.</p>
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate redisTemplate;
    private final CarLinkProperties properties;
    private final com.carlink.common.security.TokenGenerator tokenGenerator;

    private static final Duration COUNTER_TTL = Duration.ofMinutes(15);

    public boolean isBlocked(String rawEmail) {
        String key = key(rawEmail);
        String count = redisTemplate.opsForValue().get(key);
        if (count == null) {
            return false;
        }
        return Integer.parseInt(count) >= properties.ratelimit().maxLoginFailures();
    }

    public void recordFailure(String rawEmail) {
        String key = key(rawEmail);
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key,
                    Duration.ofMinutes(properties.ratelimit().loginLockMinutes()));
        }
    }

    public void reset(String rawEmail) {
        redisTemplate.delete(key(rawEmail));
    }

    private String key(String rawEmail) {
        return "login:lock:" + tokenGenerator.sha256(rawEmail.trim().toLowerCase());
    }
}