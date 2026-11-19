package com.carlink.notification.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev-only sender: records that an email would have been sent, metadata only.
 *
 * <p>Bodies are never logged (security plan S-16): they carry single-use
 * verification / password-reset links and personal data, and a log is the
 * wrong place for either. To read the actual messages locally, run MailHog
 * ({@code docker compose --profile email up -d}, UI on :8025) with
 * {@code EMAIL_PROVIDER=smtp}. The recipient is masked. Refused in the prod
 * profile by {@code ProductionProviderGuard}.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "carlink.email.provider", havingValue = "mock", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String text) {
        log.info("[MAIL-DEV] To: {} | Subject: {} | Body: {} chars (not logged)",
                mask(to), subject, text == null ? 0 : text.length());
    }

    /**
     * HTML bodies may contain customer names and addresses, so only metadata is
     * logged. Use MailHog ({@code docker compose --profile email up -d}) to read
     * the rendered message.
     */
    @Override
    public void sendHtml(String to, String subject, String html, String textBody) {
        log.info("[MAIL-DEV] To: {} | Subject: {} | HTML body: {} chars (not logged)",
                mask(to), subject, html == null ? 0 : html.length());
    }

    /** "omar@example.com" -> "o***@example.com". */
    static String mask(String address) {
        if (address == null) return "";
        int at = address.indexOf('@');
        if (at <= 0) return "***";
        return address.charAt(0) + "***" + address.substring(at);
    }
}