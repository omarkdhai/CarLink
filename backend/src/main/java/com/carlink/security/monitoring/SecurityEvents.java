package com.carlink.security.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Security signals for alerting (security plan S-25), emitted two ways:
 *
 * <ul>
 *   <li>the Prometheus counter {@code carlink_security_events_total{type, detail}},
 *       which the alert rules in {@code monitoring/prometheus/alerts.yml} watch;</li>
 *   <li>one log line per event on the {@code carlink.security} logger:
 *       {@code SECURITY_EVENT type=<type> detail=<detail>}, for log-based alerting.</li>
 * </ul>
 *
 * Neither ever carries personal data: {@code detail} is a fixed vocabulary (a
 * route pattern or a throttle bucket), never an e-mail, IP, phone or token.
 * That also keeps the metric's label set bounded.
 */
@Component
public class SecurityEvents {

    /** Dedicated logger name, so ops can route these lines on their own. */
    private static final Logger SECURITY_LOG = LoggerFactory.getLogger("carlink.security");

    public enum Type {
        /** Wrong e-mail or password. */
        LOGIN_FAILURE,
        /** Login refused while the (IP, e-mail) pair is locked out. */
        LOGIN_BLOCKED,
        /** Any 429, from the per-route limiters or the throttles. */
        RATE_LIMITED,
        /** Turnstile token missing or rejected. */
        CAPTCHA_FAILED,
        /** Successful sign-in to an ADMIN account. */
        ADMIN_LOGIN,
        /** A rotated refresh token was presented again: possible session theft. */
        REFRESH_TOKEN_REUSE,
        /** Every session of an account was ended (deactivation, role change, password reset). */
        SESSIONS_REVOKED,
        /** An owner deleted their account. */
        ACCOUNT_DELETED,
        /** Unhandled exception answered with 500. */
        SERVER_ERROR
    }

    private final MeterRegistry registry;

    public SecurityEvents(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(Type type) {
        record(type, "none");
    }

    /** {@code detail} must come from a fixed vocabulary (route pattern, bucket name). */
    public void record(Type type, String detail) {
        String name = type.name().toLowerCase(Locale.ROOT);
        String safeDetail = detail == null || detail.isBlank() ? "none" : detail;
        registry.counter("carlink.security.events", "type", name, "detail", safeDetail).increment();
        if (type == Type.REFRESH_TOKEN_REUSE || type == Type.SERVER_ERROR) {
            SECURITY_LOG.warn("SECURITY_EVENT type={} detail={}", name, safeDetail);
        } else {
            SECURITY_LOG.info("SECURITY_EVENT type={} detail={}", name, safeDetail);
        }
    }
}
