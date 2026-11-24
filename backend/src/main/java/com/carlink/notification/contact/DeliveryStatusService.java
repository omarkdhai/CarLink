package com.carlink.notification.contact;

import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.repository.ConversationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Turns an accepted SMS (SENT) into DELIVERED or FAILED from the provider's
 * delivery report:
 *
 * <ul>
 *   <li>on demand, while the visitor's page waits for the confirmation;</li>
 *   <li>every minute in the background, so the owner's dashboard and the admin
 *       statistics show the real outcome even after the visitor left.</li>
 * </ul>
 *
 * <p>Reports are only fetched for alerts younger than {@link #REPORT_WINDOW}:
 * an operator that has not answered by then never will (the alert stays SENT
 * and the expiry sweeper ends it). Without a {@link DeliveryReportClient} bean
 * (mock, Telnyx) nothing changes.</p>
 *
 * <p>The HTTP call runs outside any database transaction; the result is applied
 * with a guarded update that only moves a still-SENT alert.</p>
 */
@Slf4j
@Service
public class DeliveryStatusService {

    static final Duration REPORT_WINDOW = Duration.ofHours(24);

    private final ObjectProvider<DeliveryReportClient> reportClient;
    private final ConversationRepository conversationRepository;

    public DeliveryStatusService(ObjectProvider<DeliveryReportClient> reportClient,
                                 ConversationRepository conversationRepository) {
        this.reportClient = reportClient;
        this.conversationRepository = conversationRepository;
    }

    /** Current status of the alert, refreshed from the delivery report when still SENT. */
    public ConversationStatus refresh(Conversation conversation) {
        ConversationStatus status = conversation.getStatus();
        DeliveryReportClient client = reportClient.getIfAvailable();
        if (status != ConversationStatus.SENT || client == null
                || conversation.getProviderMessageId() == null
                || conversation.getCreatedAt().isBefore(Instant.now().minus(REPORT_WINDOW))) {
            return status;
        }
        Optional<DeliveryReportClient.DeliveryReport> report = client.report(conversation.getProviderMessageId());
        if (report.isEmpty()) {
            return status;                                   // operator has not answered yet
        }
        ConversationStatus outcome = report.get().delivered() ? ConversationStatus.DELIVERED : ConversationStatus.FAILED;
        String error = report.get().delivered() ? null : report.get().code();
        if (conversationRepository.applyDeliveryReport(conversation.getId(), outcome, error) == 1) {
            if (outcome == ConversationStatus.FAILED) {
                log.warn("SMS not delivered for conversation {}: {}", conversation.getId(), error);
            }
            return outcome;
        }
        // Someone else moved it meanwhile (another poll, the sweeper): reread.
        return conversationRepository.findById(conversation.getId())
                .map(Conversation::getStatus).orElse(status);
    }

    /** Background pass over accepted alerts still waiting for their report. */
    @Scheduled(fixedDelayString = "${carlink.contact.report-poll-interval-ms:60000}",
            initialDelayString = "${carlink.contact.report-poll-initial-delay-ms:30000}")
    public void refreshPending() {
        if (reportClient.getIfAvailable() == null) {
            return;
        }
        var waiting = conversationRepository
                .findTop100ByStatusAndProviderMessageIdIsNotNullAndCreatedAtAfterOrderByCreatedAtAsc(
                        ConversationStatus.SENT, Instant.now().minus(REPORT_WINDOW));
        for (Conversation conversation : waiting) {
            try {
                refresh(conversation);
            } catch (RuntimeException e) {
                log.warn("Delivery report check failed for conversation {}: {}",
                        conversation.getId(), e.getClass().getSimpleName());
            }
        }
    }
}
