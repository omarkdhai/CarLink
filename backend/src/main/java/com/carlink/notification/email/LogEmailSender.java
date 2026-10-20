package com.carlink.notification.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev-only sender: prints the would-be email as an INFO log line so the
 * verification/reset link is reachable without an SMTP server (MailHog
 * substitute when its container is not running).
 *
 * <p><strong>Never active in production.</strong> The body may contain a
 * single-use, short-lived verification/reset link — an acceptable dev-only
 * exception to the "don't log tokens" rule, because the recipient needs the
 * link to continue. Guarded by {@code carlink.email.provider=mock}.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "carlink.email.provider", havingValue = "mock", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String text) {
        log.info("[MAIL-DEV] To: {} | Subject: {} | Body: {}", to, subject, text);
    }

    /**
     * HTML bodies may contain customer names and addresses, so only metadata is
     * logged. Use MailHog ({@code docker compose --profile email up -d}) to read
     * the rendered message.
     */
    @Override
    public void sendHtml(String to, String subject, String html, String textBody) {
        log.info("[MAIL-DEV] To: {} | Subject: {} | HTML body: {} chars (not logged)",
                to, subject, html.length());
    }
}