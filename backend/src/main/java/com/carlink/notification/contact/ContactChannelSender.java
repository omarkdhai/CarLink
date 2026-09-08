package com.carlink.notification.contact;

/**
 * Abstraction over delivering a visitor's contact message to a vehicle owner
 * over a messaging channel (WhatsApp / SMS). Decouples the submission flow
 * from the transport so a mock, an HTTP gateway, or a provider can be swapped
 * through {@code carlink.contact.provider} — the same shape as
 * {@code notification/email/EmailSender}.
 *
 * <p>Implementations receive the owner phone inside {@link ContactDelivery}
 * but must never log, persist, or echo it.</p>
 */
public interface ContactChannelSender {

    /**
     * Sends a message on its channel.
     *
     * @return {@code true} when the provider accepted/delivered the message,
     *         {@code false} when delivery could not be confirmed.
     * @throws RuntimeException on transport failure; the caller records a
     *         {@code FAILED} conversation and swallows the exception.
     */
    boolean send(ContactDelivery delivery);
}
