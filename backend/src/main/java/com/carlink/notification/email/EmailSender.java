package com.carlink.notification.email;

/**
 * Abstraction over transactional email delivery (verification, password reset,
 * order confirmations). Decouples business logic from the transport so SMTP,
 * a mock, or a provider can be swapped through configuration.
 */
public interface EmailSender {

    /**
     * Sends a plain-text email.
     *
     * @param to      recipient address (NOT a phone number)
     * @param subject email subject
     * @param text    plain-text body
     */
    void send(String to, String subject, String text);

    /**
     * Sends an HTML email. Implementations should also supply a plain-text
     * alternative to clients that cannot render HTML.
     *
     * @param to       recipient address (NOT a phone number)
     * @param subject  email subject
     * @param html     HTML body
     * @param textBody plain-text fallback body
     */
    void sendHtml(String to, String subject, String html, String textBody);
}
