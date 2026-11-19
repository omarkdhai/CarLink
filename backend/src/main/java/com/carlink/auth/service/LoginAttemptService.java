package com.carlink.auth.service;

import com.carlink.common.config.CarLinkProperties;
import lombok.RequiredArgsConstructor;
import com.carlink.security.ratelimit.ThrottleProperties;
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

    /**
     * Security plan S-11: has this account seen at least {@code limit.max} failed
     * logins within {@code limit.windowSeconds}, from any IPs? Then its login is
     * challenged (captcha), never locked: a lock would let anyone shut the owner out.
     */
    public boolean isAccountChallenged(String rawEmail, ThrottleProperties.Limit limit) {
        String count = redisTemplate.opsForValue().get(accountKey(rawEmail));
        return count != null && Integer.parseInt(count) >= limit.getMax();
    }

    /** Counts a failure for the account as a whole (S-11), next to the (IP, e-mail) lockout. */
    public void recordAccountFailure(String rawEmail, ThrottleProperties.Limit limit) {
        String key = accountKey(rawEmail);
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(limit.getWindowSeconds()));
        }
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
        // The owner got in: the account-wide challenge ends with it.
        redisTemplate.delete(accountKey(rawEmail));
    }

    private String accountKey(String rawEmail) {
        return "login:acct:" + tokenGenerator.sha256(rawEmail.trim().toLowerCase());
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