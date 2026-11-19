package com.carlink.auth.controller;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.ForbiddenException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The refresh token lives in an HttpOnly cookie (security plan S-09), never in
 * a response body or in JavaScript-readable storage, so an XSS cannot steal a
 * 7-day session.
 *
 * <ul>
 *   <li>{@code HttpOnly} — scripts cannot read it.</li>
 *   <li>{@code SameSite=Strict} — no other site can make the browser send it.</li>
 *   <li>{@code Path=/api/v1/auth} — sent only to the refresh / logout calls,
 *       not with every API request.</li>
 *   <li>{@code Secure} — HTTPS only (off only in the dev profile).</li>
 * </ul>
 *
 * CSRF (S-24): besides SameSite, the two endpoints that act on the cookie
 * require the {@value #CSRF_HEADER} header. A cross-site form or image cannot
 * set a custom header, and a cross-site script that tries needs a CORS
 * preflight, which only the configured origins pass.
 */
@Component
public class RefreshCookie {

    public static final String NAME = "carlink_rt";
    public static final String PATH = "/api/v1/auth";
    public static final String CSRF_HEADER = "X-Requested-With";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshCookie(@Value("${carlink.auth.refresh-cookie-secure:true}") boolean secure,
                         CarLinkProperties properties) {
        this.secure = secure;
        this.maxAge = Duration.ofDays(properties.jwt().refreshExpirationDays());
    }

    public void set(HttpServletResponse response, String rawToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(rawToken, maxAge).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    /** The raw refresh token from the request, or null when absent. */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** Rejects a cookie-authenticated call that lacks the anti-CSRF header. */
    public void requireCsrfHeader(HttpServletRequest request) {
        String value = request.getHeader(CSRF_HEADER);
        if (value == null || value.isBlank()) {
            throw new ForbiddenException("Missing " + CSRF_HEADER + " header");
        }
    }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(age)
                .build();
    }
}
