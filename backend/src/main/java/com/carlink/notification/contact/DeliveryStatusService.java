package com.carlink.notification.contact;

import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationDelivery;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.model.DeliveryStatus;
import com.carlink.conversation.repository.ConversationDeliveryRepository;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.conversation.service.ConversationDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Turns accepted messages (SENT) into DELIVERED / READ / FAILED from the
 * providers' delivery reports, and keeps the alert's aggregate status in step:
 *
 * <ul>
 *   <li>SMS (TunisieSMS) reports are fetched — on demand while the visitor's
 *       page waits, and every minute in the background, so the owner's dashboard
 *       shows the real outcome even after the visitor left;</li>
 *   <li>WhatsApp reports are pushed by Meta's webhook
 *       ({@link #applyPushedReport}).</li>
 * </ul>
 *
 * <p>SMS reports are only fetched for alerts younger than {@link #REPORT_WINDOW}:
 * an operator that has not answered by then never will. Without a
 * {@link DeliveryReportClient} bean (mock, Telnyx) nothing is polled.</p>
 *
 * <p>The HTTP call runs outside any database transaction; the result is applied
 * with a guarded update that only moves a delivery forward.</p>
 */
@Slf4j
@Service
public class DeliveryStatusService {

    static final Duration REPORT_WINDOW = Duration.ofHours(24);

    private final ObjectProvider<DeliveryReportClient> reportClient;
    private final ConversationRepository conversationRepository;
    private final ConversationDeliveryRepository deliveryRepository;
    private final ConversationDeliveryService deliveries;

    public DeliveryStatusService(ObjectProvider<DeliveryReportClient> reportClient,
                                 ConversationRepository conversationRepository,
                                 ConversationDeliveryRepository deliveryRepository,
                                 ConversationDeliveryService deliveries) {
        this.reportClient = reportClient;
        this.conversationRepository = conversationRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveries = deliveries;
    }

    /** Current status of the alert, refreshed from the SMS delivery report when still SENT. */
    public ConversationStatus refresh(Conversation conversation) {
        ConversationStatus status = conversation.getStatus();
        DeliveryReportClient client = reportClient.getIfAvailable();
        if (status != ConversationStatus.SENT || client == null
                || conversation.getCreatedAt().isBefore(Instant.now().minus(REPORT_WINDOW))) {
            return status;
        }
        boolean changed = false;
        for (ConversationDelivery delivery : deliveryRepository.findAllByConversationId(conversation.getId())) {
            if (delivery.getStatus() == DeliveryStatus.SENT && delivery.getProviderMessageId() != null
                    && client.provider().equals(delivery.getProvider())) {
                changed |= check(client, delivery);
            }
        }
        if (!changed) {
            return status;
        }
        return conversationRepository.findById(conversation.getId())
                .map(Conversation::getStatus).orElse(status);
    }

    /** Background pass over accepted SMS still waiting for their report. */
    @Scheduled(fixedDelayString = "${carlink.contact.report-poll-interval-ms:60000}",
            initialDelayString = "${carlink.contact.report-poll-initial-delay-ms:30000}")
    public void refreshPending() {
        DeliveryReportClient client = reportClient.getIfAvailable();
        if (client == null) {
            return;
        }
        var waiting = deliveryRepository
                .findTop100ByChannelAndProviderAndStatusAndProviderMessageIdIsNotNullAndCreatedAtAfterOrderByCreatedAtAsc(
                        Channel.SMS, client.provider(), DeliveryStatus.SENT, Instant.now().minus(REPORT_WINDOW));
        for (ConversationDelivery delivery : waiting) {
            try {
                check(client, delivery);
            } catch (RuntimeException e) {
                log.warn("Delivery report check failed for conversation {}: {}",
                        delivery.getConversationId(), e.getClass().getSimpleName());
            }
        }
    }

    /**
     * A report pushed by a provider (WhatsApp webhook). Unknown ids — another
     * app on the same account, or a message older than the retention — are ignored.
     *
     * @return whether a delivery of ours matched
     */
    public boolean applyPushedReport(String provider, String providerMessageId,
                                     DeliveryStatus status, String error) {
        Optional<ConversationDelivery> delivery =
                deliveryRepository.findByProviderAndProviderMessageId(provider, providerMessageId);
        if (delivery.isEmpty()) {
            return false;
        }
        deliveries.applyReport(delivery.get(), status, error);
        if (status == DeliveryStatus.FAILED) {
            log.warn("{} not delivered for conversation {}: {}",
                    delivery.get().getChannel(), delivery.get().getConversationId(), error);
        }
        return true;
    }

    /**
     * Whether the operator refused the alert because of what the note says
     * (TunisieSMS report {@code UNAUTHORIZED}, "contenu non autorisé").
     */
    public boolean refusedForContent(java.util.UUID conversationId) {
        return deliveryRepository.findAllByConversationId(conversationId).stream()
                .anyMatch(d -> d.getStatus() == DeliveryStatus.FAILED
                        && ConversationDelivery.CONTENT_REFUSED.equals(d.getError()));
    }

    /** Asks for one SMS report and applies it. @return whether there was a report. */
    private boolean check(DeliveryReportClient client, ConversationDelivery delivery) {
        Optional<DeliveryReportClient.DeliveryReport> report = client.report(delivery.getProviderMessageId());
        if (report.isEmpty()) {
            return false;                                    // operator has not answered yet
        }
        boolean delivered = report.get().delivered();
        String error = delivered ? null : report.get().code();
        deliveries.applyReport(delivery, delivered ? DeliveryStatus.DELIVERED : DeliveryStatus.FAILED, error);
        if (!delivered) {
            log.warn("SMS not delivered for conversation {}: {}", delivery.getConversationId(), error);
        }
        return true;
    }
}
