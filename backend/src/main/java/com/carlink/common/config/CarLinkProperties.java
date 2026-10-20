package com.carlink.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Root configuration properties for the module. Binds the `carlink.*` prefix.
 */
@ConfigurationProperties(prefix = "carlink")
public record CarLinkProperties(
        String baseUrl,
        String publicUrl,
        Jwt jwt,
        Conversation conversation,
        Qr qr,
        RateLimit ratelimit,
        Contact contact,
        ContactForm contactForm,
        Email email,
        Security security,
        Admin admin,
        Telnyx telnyx
) {

    public record Jwt(
            String secret,
            long accessExpirationMinutes,
            String refreshSecret,
            long refreshExpirationDays
    ) {}

    public record Conversation(long expiryHours) {}

    public record Qr(int tokenBytes) {}

    public record RateLimit(
            int ipPerMinute,
            int qrPerHour,
            String captureHosts,
            int maxLoginFailures,
            int loginLockMinutes,
            int reportIpPerMinute,
            int orderIpPerMinute
    ) {}

    /**
     * Contact relay settings.
     *
     * @param provider        which {@code ContactChannelSender} is active:
     *                        {@code mock} (logs, dev) or {@code telnyx}.
     * @param enabledChannels channels the public page advertises and the
     *                        submit endpoint accepts, as {@link
     *                        com.carlink.conversation.model.Channel} names.
     *                        Defaults to SMS-only: Telnyx cannot send free-text
     *                        WhatsApp to an owner who has not messaged the
     *                        business number in the last 24 hours, which is
     *                        every unprompted QR scan, so offering WhatsApp
     *                        would advertise a channel that cannot deliver.
     */
    public record Contact(String provider, List<String> enabledChannels) {}

    /**
     * Public "Contact us" form: the mailbox the form messages are delivered to
     * and the per-IP per-minute rate limit (guarded before any send).
     */
    public record ContactForm(String toEmail, int ipPerMinute) {}

    /**
     * Email sending strategy and envelope identity.
     *
     * @param provider          {@code mock} (logs, dev) or {@code smtp}.
     * @param tokenExpiryMinutes lifetime of verification / reset tokens.
     * @param from              sender address (must be allowed by the SMTP
     *                          account; until a branded mailbox exists this is
     *                          the founder's personal address).
     * @param replyTo           Reply-To address; customers replying to an order
     *                          confirmation reach a human.
     */
    public record Email(String provider, long tokenExpiryMinutes, String from, String replyTo) {}

    public record Security(String corsAllowedOrigins) {}

    /**
     * Initial ADMIN bootstrap. When {@code bootstrapEmail} is set and no user
     * with that email exists, the app creates that user with the ADMIN role on
     * startup. Left unset in production; only enabled for dev management.
     */
    public record Admin(String bootstrapEmail, String bootstrapPassword) {}

    /**
     * Telnyx credentials and sender identities.
     *
     * @param apiKey       API key. Must never be logged or echoed.
     * @param smsFrom      SMS-enabled number in E.164, assigned to a messaging
     *                     profile in the Telnyx portal.
     * @param voiceFrom    Number to present as the caller for the alert call.
     * @param connectionId Call Control connection the outbound call is placed
     *                     on.
     * @param voiceAudioUrl audio played to the owner when they answer. The
     *                     outbound dial API carries a static URL only — it
     *                     cannot speak a dynamic reason or message, so the
     *                     alert has to be a pre-recorded clip.
     */
    public record Telnyx(
            String apiKey,
            String smsFrom,
            String voiceFrom,
            String connectionId,
            String voiceAudioUrl
    ) {}
}