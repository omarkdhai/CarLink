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

    public boolean isBlocked(String clientIp, String rawEmail) {
        String key = key(clientIp, rawEmail);
        String count = redisTemplate.opsForValue().get(key);
        if (count == null) {
            return false;
        }
        return Integer.parseInt(count) >= properties.ratelimit().maxLoginFailures();
    }

    public void recordFailure(String clientIp, String rawEmail) {
        String key = key(clientIp, rawEmail);
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key,
                    Duration.ofMinutes(properties.ratelimit().loginLockMinutes()));
        }
    }

    public void reset(String clientIp, String rawEmail) {
        redisTemplate.delete(key(clientIp, rawEmail));
    }

    /**
     * Keyed on the (client IP, e-mail) pair, not the e-mail alone (security plan
     * S-04): with an e-mail-only key, anyone could lock any owner out by typing
     * five wrong passwords. Now a failing client only locks itself out; the
     * per-IP cap in AuthController bounds how many accounts one client can try.
     */
    private String key(String clientIp, String rawEmail) {
        String ip = clientIp == null ? "" : clientIp.trim();
        return "login:lock:" + tokenGenerator.sha256(ip + "|" + rawEmail.trim().toLowerCase());
    }
}