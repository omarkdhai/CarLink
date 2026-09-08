package com.carlink.notification.contact;

import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Routes one visitor contact request to the vehicle owner and records the
 * outcome on the conversation (SENT on success, FAILED on relay error).
 *
 * <p>Delivery failures are swallowed here so the persisted request survives
 * and the visitor still receives the generic success response — the owner
 * sees the failed conversation in their dashboard (Phase 7). The owner phone
 * is passed only into {@link ContactChannelSender} and is never logged.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactDeliveryService {

    private final ContactChannelSender sender;
    private final ConversationService conversationService;

    public void deliver(UUID conversationId, Channel channel,
                        String ownerPhone, String message) {
        boolean delivered;
        try {
            delivered = sender.send(new ContactDelivery(channel, ownerPhone, message));
        } catch (RuntimeException e) {
            log.warn("Contact relay failed for conversation {} ({})",
                    conversationId, channel);
            conversationService.updateStatus(conversationId, ConversationStatus.FAILED);
            return;
        }
        conversationService.updateStatus(conversationId,
                delivered ? ConversationStatus.SENT : ConversationStatus.FAILED);
    }
}
