package com.carlink.notification.email;

/**
 * Abstraction over transactional email delivery (verification, password reset,
 * future notifications). Decouples business logic from the transport so SMTP,
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
}