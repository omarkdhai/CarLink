package com.carlink.notification.contact;

/**
 * Places the "someone is trying to reach you" alert call to an owner, mirroring
 * the {@link ContactChannelSender} strategy: one interface, a logging mock for
 * dev, and a provider implementation selected by {@code carlink.contact.provider}.
 *
 * <p>Implementations receive the owner phone but must never log, persist, or
 * echo it.</p>
 */
public interface AnonymousCallSender {

    /**
     * Places the alert call.
     *
     * @return {@code true} when the call was placed, {@code false} when it was
     *         skipped (for example, no audio clip configured).
     * @throws RuntimeException on transport failure; the caller treats the call
     *         as best-effort and does not fail the message relay because of it.
     */
    boolean initiateCall(String ownerPhone, String reason, String message, String conversationId);
}
