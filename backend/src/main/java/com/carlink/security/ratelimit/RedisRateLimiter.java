package com.carlink.security.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Fixed-window Redis rate limiter: INCR the per-key counter, atomically setting
 * expiry on first use. The Lua script makes INCR+EXPIRE atomic for concurrent
 * requests.
 *
 * <p>Keys are namespaced ({@code rl:<key>}) and expire quickly. The key is
 * always derived server-side from a non-sensitive value (e.g. a token hash or
 * hashed IP), never from domain data in the output.</p>
 */
@Component
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {

    private static final DefaultRedisScript<Long> INCR_EXPIRE_SCRIPT =
            new DefaultRedisScript<>(
                    "local c = redis.call('INCR', KEYS[1]) " +
                    "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end " +
                    "return c",
                    Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryAcquire(String key, int max, int windowSeconds) {
        Long count = incrementAndExpire(key, windowSeconds);
        return count == null || count <= max;
    }

    @Override
    public long retryAfterSeconds(String key, int max, int windowSeconds) {
        Long ttl = redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0L;
    }

    /**
     * Atomically increments the counter for {@code key} and (re)sets the expire
     * on the first hit within the window.
     */
    private Long incrementAndExpire(String key, int windowSeconds) {
        return redisTemplate.execute(
                INCR_EXPIRE_SCRIPT,
                List.of(redisKey(key)),
                String.valueOf(windowSeconds * 1000L));
    }

    private String redisKey(String key) {
        return "rl:" + key;
    }
}