package com.carlink.notification.contact;

import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;

/**
 * Routes one visitor contact request to the vehicle owner and records the
 * outcome on the conversation:
 *
 * <ul>
 *   <li>{@code DELIVERED}: delivery certain (dev mock);</li>
 *   <li>{@code SENT}: accepted by the provider; the delivery report decides
 *       later ({@link DeliveryStatusService});</li>
 *   <li>{@code FAILED}: not sent, with a short code — NO_PHONE, INVALID_NUMBER,
 *       UNREACHABLE (provider down / timeout), API_&lt;code&gt; (refused by the
 *       provider), or ERROR.</li>
 * </ul>
 *
 * <p>Failures are not thrown: the request stays recorded and the owner sees it
 * in the dashboard. The returned status lets the public page tell the visitor
 * the truth instead of a blanket "sent". The owner phone is passed only into
 * the senders and is never logged.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactDeliveryService {

    private final ContactChannelSender sender;
    private final AnonymousCallSender anonymousCallSender;
    private final ConversationService conversationService;

    public ConversationStatus deliver(UUID conversationId, Channel channel,
                                      String ownerPhone, String message, String reason) {
        // Phone is optional at registration, but the relay cannot reach an owner
        // without one. Record it as failed rather than sending to null.
        if (ownerPhone == null || ownerPhone.isBlank()) {
            log.warn("Relay skipped, owner has no phone (conversation={})", conversationId);
            return fail(conversationId, "NO_PHONE");
        }

        SendResult result;
        try {
            result = sender.send(new ContactDelivery(channel, ownerPhone, message, reason));
        } catch (RuntimeException e) {
            // The error type tells a timeout / unreachable provider apart from a
            // bad number or a bug; the exception message is not logged (it may
            // carry provider details), and never the phone or the note.
            log.warn("Contact relay failed for conversation {} ({}): {}",
                    conversationId, channel, e.getClass().getSimpleName());
            return fail(conversationId, errorCode(e));
        }

        if (result.outcome() == SendResult.Outcome.FAILED) {
            return fail(conversationId, result.error());
        }

        // The alert call is best-effort. Its failure must not downgrade a
        // message that was already handed over.
        try {
            anonymousCallSender.initiateCall(
                    ownerPhone, reason, message, conversationId.toString());
        } catch (RuntimeException e) {
            log.warn("Alert call failed for conversation {} (message already relayed)", conversationId);
        }

        ConversationStatus status = result.outcome() == SendResult.Outcome.DELIVERED
                ? ConversationStatus.DELIVERED
                : ConversationStatus.SENT;
        conversationService.recordDelivery(conversationId, status, result.providerMessageId(), null);
        return status;
    }

    private ConversationStatus fail(UUID conversationId, String code) {
        conversationService.recordDelivery(conversationId, ConversationStatus.FAILED, null, code);
        return ConversationStatus.FAILED;
    }

    private static String errorCode(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            return "UNREACHABLE";
        }
        if (e instanceof IllegalArgumentException) {
            return "INVALID_NUMBER";
        }
        return "ERROR";
    }
}
