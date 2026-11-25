package com.carlink.conversation.service;

import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationDelivery;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.model.DeliveryStatus;
import com.carlink.conversation.repository.ConversationDeliveryRepository;
import com.carlink.conversation.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Records what happened on each channel of an alert (SMS, WhatsApp) and keeps
 * the conversation's status as their aggregate:
 *
 * <ul>
 *   <li>DELIVERED as soon as one channel reached the owner;</li>
 *   <li>SENT while a channel is still waiting for its report;</li>
 *   <li>FAILED only when every channel failed.</li>
 * </ul>
 *
 * <p>EXPIRED is final and never recomputed. The aggregate is computed with the
 * conversation row locked, so two reports arriving at the same time (an SMS
 * poll and a WhatsApp webhook) cannot overwrite each other.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationDeliveryService {

    /** A report moves forward only: SENT → DELIVERED/FAILED → … READ. */
    private static final Set<DeliveryStatus> FROM_SENT = EnumSet.of(DeliveryStatus.SENT);
    private static final Set<DeliveryStatus> FROM_SENT_OR_DELIVERED =
            EnumSet.of(DeliveryStatus.SENT, DeliveryStatus.DELIVERED);

    private final ConversationRepository conversationRepository;
    private final ConversationDeliveryRepository deliveryRepository;

    /** Saves the outcome of one channel right after the send (committed at once). */
    public UUID record(UUID conversationId, Channel channel, String provider,
                       DeliveryStatus status, String providerMessageId, String error) {
        Instant now = Instant.now();
        ConversationDelivery delivery = ConversationDelivery.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .channel(channel)
                .provider(provider)
                .providerMessageId(providerMessageId)
                .status(status)
                .error(error)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return deliveryRepository.save(delivery).getId();
    }

    /**
     * Applies a provider report to one delivery (if it moves it forward), then
     * returns the conversation's new aggregate status.
     */
    public ConversationStatus applyReport(ConversationDelivery delivery, DeliveryStatus status, String error) {
        Collection<DeliveryStatus> from = status == DeliveryStatus.READ ? FROM_SENT_OR_DELIVERED : FROM_SENT;
        int applied = deliveryRepository.applyReport(delivery.getId(), status,
                status == DeliveryStatus.FAILED ? error : null, Instant.now(), from);
        if (applied == 1 && status == DeliveryStatus.FAILED && ConversationDelivery.CONTENT_REFUSED.equals(error)) {
            // The operator refused what the note says: spam the classifier missed.
            // The owner must not see it in the dashboard either.
            conversationRepository.lockById(delivery.getConversationId())
                    .filter(c -> c.getHiddenAt() == null)
                    .ifPresent(c -> c.setHiddenAt(Instant.now()));
        }
        return recompute(delivery.getConversationId());
    }

    /** Recomputes and stores the conversation's status from its deliveries. */
    public ConversationStatus recompute(UUID conversationId) {
        Conversation conversation = conversationRepository.lockById(conversationId)
                .orElseThrow(() -> new IllegalStateException("Conversation not found: " + conversationId));
        if (conversation.getStatus() == ConversationStatus.EXPIRED) {
            return ConversationStatus.EXPIRED;
        }
        List<DeliveryStatus> statuses = deliveryRepository.findAllByConversationId(conversationId)
                .stream().map(ConversationDelivery::getStatus).toList();
        ConversationStatus aggregate = aggregate(statuses);
        conversation.setStatus(aggregate);
        return aggregate;
    }

    @Transactional(readOnly = true)
    public List<ConversationDelivery> forConversation(UUID conversationId) {
        return deliveryRepository.findAllByConversationId(conversationId);
    }

    /** DELIVERED if any channel reached the owner, SENT if one still waits, else FAILED. */
    static ConversationStatus aggregate(Collection<DeliveryStatus> statuses) {
        if (statuses.stream().anyMatch(DeliveryStatus::reached)) {
            return ConversationStatus.DELIVERED;
        }
        if (statuses.contains(DeliveryStatus.SENT)) {
            return ConversationStatus.SENT;
        }
        return ConversationStatus.FAILED;
    }
}
