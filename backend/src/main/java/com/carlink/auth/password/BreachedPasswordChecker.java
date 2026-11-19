package com.carlink.auth.password;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Optional breached-password check against Have I Been Pwned's range API
 * (security plan S-14), by k-anonymity: only the first 5 hex characters of the
 * password's SHA-1 leave the server; the match against the returned suffixes
 * happens here. Neither the password nor its full hash is sent or logged, and
 * padded responses ({@code Add-Padding}) keep the reply size meaningless.
 *
 * <p>Off unless {@code carlink.password.breach-check=true} (on in the prod
 * profile). Fails open: if the service is slow or down, sign-up still works.
 */
@Slf4j
@Component
public class BreachedPasswordChecker {

    private final boolean enabled;
    private final RestClient client;

    public BreachedPasswordChecker(
            @Value("${carlink.password.breach-check:false}") boolean enabled,
            @Value("${carlink.password.breach-check-url:https://api.pwnedpasswords.com/range/}") String baseUrl) {
        this.enabled = enabled;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(2_000);
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("User-Agent", "CarLink-password-check")
                .defaultHeader("Add-Padding", "true")
                .build();
    }

    public boolean isBreached(String password) {
        if (!enabled) {
            return false;
        }
        String sha1 = sha1Hex(password);
        try {
            String body = client.get().uri(sha1.substring(0, 5)).retrieve().body(String.class);
            return body != null && containsSuffix(body, sha1.substring(5));
        } catch (RuntimeException e) {
            log.warn("Breached-password check unavailable ({}); accepting the password",
                    e.getClass().getSimpleName());
            return false;
        }
    }

    /** Lines are {@code SUFFIX:COUNT}; padding entries carry a count of 0. */
    static boolean containsSuffix(String body, String suffix) {
        return body.lines().anyMatch(line -> {
            int colon = line.indexOf(':');
            if (colon <= 0 || !line.substring(0, colon).trim().equalsIgnoreCase(suffix)) {
                return false;
            }
            String count = line.substring(colon + 1).trim();
            return !count.isEmpty() && !"0".equals(count);
        });
    }

    static String sha1Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
