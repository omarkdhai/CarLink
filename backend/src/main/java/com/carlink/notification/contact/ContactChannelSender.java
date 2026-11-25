package com.carlink.notification.contact;

import com.carlink.conversation.model.Channel;

/**
 * Abstraction over delivering a visitor's contact message to a vehicle owner
 * over a messaging channel (SMS or WhatsApp). Decouples the submission flow from the
 * transport so a mock or a provider can be swapped through
 * {@code carlink.contact.provider} — the same shape as
 * {@code notification/email/EmailSender}.
 *
 * <p>Implementations receive the owner phone inside {@link ContactDelivery}
 * but must never log, persist, or echo it.</p>
 */
public interface ContactChannelSender {

    /** The channel this sender carries (one SMS sender and at most one WhatsApp sender are active). */
    Channel channel();

    /** Short provider name stored with each delivery (e.g. tunisiesms, meta, mock). */
    String provider();

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
