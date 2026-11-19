package com.carlink.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Abuse limits for the authentication and activation endpoints (security plan
 * S-03 / S-04). Each limit is "at most {@code max} requests per
 * {@code windowSeconds}" for one subject (an IP, an e-mail hash or a user id).
 *
 * <p>Defaults are the production values; override any of them under
 * {@code carlink.throttle.*} (e.g. {@code carlink.throttle.register-ip.max=20}).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "carlink.throttle")
public class ThrottleProperties {

    /** Account creation per IP. Also caps sticker-code guessing through registration. */
    private Limit registerIp = new Limit(10, 3600);
    /** Password-reset requests per IP (answered 429). */
    private Limit passwordResetIp = new Limit(5, 900);
    /** Password-reset mails per target address; beyond it the request is silently dropped. */
    private Limit passwordResetEmail = new Limit(3, 3600);
    /** Reset-link redemptions per IP. */
    private Limit passwordResetConfirmIp = new Limit(10, 900);
    /** E-mail verification attempts per IP. */
    private Limit verifyEmailIp = new Limit(20, 900);
    /** Session refreshes per IP. */
    private Limit refreshIp = new Limit(60, 60);
    /** Sticker activations per signed-in user (sticker-code guessing). */
    private Limit stickerActivateUser = new Limit(10, 3600);
    /** Sticker activations per IP. */
    private Limit stickerActivateIp = new Limit(20, 3600);
    /** Login attempts per IP, whatever the e-mail (credential stuffing). */
    private Limit loginIp = new Limit(20, 300);
    /** S-12: one relayed alert per (visitor IP, sticker) per window. */
    private Limit relayVisitorCooldown = new Limit(1, 120);
    /** S-12: relayed alerts per sticker per day (harassment / SMS cost). */
    private Limit relayStickerDay = new Limit(30, 86400);
    /** S-12: relayed alerts per owner per day, across all their stickers. */
    private Limit relayOwnerDay = new Limit(60, 86400);
    /** S-17: account-deletion attempts (password re-check) per user. */
    private Limit accountDeleteUser = new Limit(5, 3600);
    /** S-11: guest orders per e-mail address per day (fake orders, confirmation-mail spam). */
    private Limit orderEmailDay = new Limit(3, 86400);
    /** S-11: guest orders per mobile number per day. */
    private Limit orderMobileDay = new Limit(3, 86400);
    /**
     * S-11: failed logins on ONE account, from any number of IPs, after which
     * that account's login needs a captcha (distributed guessing). Never a lock.
     */
    private Limit loginAccountFailures = new Limit(10, 900);
    /** S-11: when captcha is off, a challenged account accepts this many attempts per window instead. */
    private Limit loginAccountSlow = new Limit(1, 30);

    @Getter
    @Setter
    public static class Limit {
        private int max;
        private int windowSeconds;

        public Limit() {
        }

        public Limit(int max, int windowSeconds) {
            this.max = max;
            this.windowSeconds = windowSeconds;
        }
    }
}
