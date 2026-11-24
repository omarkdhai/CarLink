package com.carlink.notification.contact;

/**
 * Abstraction over delivering a visitor's contact message to a vehicle owner
 * over a messaging channel (SMS). Decouples the submission flow from the
 * transport so a mock or a provider can be swapped through
 * {@code carlink.contact.provider} — the same shape as
 * {@code notification/email/EmailSender}.
 *
 * <p>Implementations receive the owner phone inside {@link ContactDelivery}
 * but must never log, persist, or echo it.</p>
 */
public interface ContactChannelSender {

    /**
     * Hands the message to the channel.
     *
     * @return whether it was delivered, accepted (delivery still to be
     *         confirmed) or refused.
     * @throws RuntimeException on transport failure (provider unreachable,
     *         timeout, invalid number); the caller records a FAILED alert.
     */
    SendResult send(ContactDelivery delivery);
}
