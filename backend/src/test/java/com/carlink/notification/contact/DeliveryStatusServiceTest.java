package com.carlink.notification.contact;

import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationDelivery;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.model.DeliveryStatus;
import com.carlink.conversation.repository.ConversationDeliveryRepository;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.conversation.service.ConversationDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryStatusServiceTest {

    private final DeliveryReportClient client = mock(DeliveryReportClient.class);
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationDeliveryRepository deliveryRepository = mock(ConversationDeliveryRepository.class);
    private final ConversationDeliveryService deliveries = mock(ConversationDeliveryService.class);

    @SuppressWarnings("unchecked")
    private DeliveryStatusService service(DeliveryReportClient reportClient) {
        when(client.provider()).thenReturn("tunisiesms");
        ObjectProvider<DeliveryReportClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(reportClient);
        return new DeliveryStatusService(provider, conversations, deliveryRepository, deliveries);
    }

    private static Conversation conversation(ConversationStatus status, Instant createdAt) {
        return Conversation.builder().id(UUID.randomUUID()).status(status).createdAt(createdAt).build();
    }

    private static ConversationDelivery delivery(UUID conversationId, Channel channel, String provider,
                                                 String messageId, DeliveryStatus status) {
        return ConversationDelivery.builder().id(UUID.randomUUID()).conversationId(conversationId)
                .channel(channel).provider(provider).providerMessageId(messageId).status(status)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void delivrdReportMarksTheSmsDeliveredAndReturnsTheNewAggregate() {
        Conversation c = conversation(ConversationStatus.SENT, Instant.now());
        ConversationDelivery sms = delivery(c.getId(), Channel.SMS, "tunisiesms", "m1", DeliveryStatus.SENT);
        when(deliveryRepository.findAllByConversationId(c.getId())).thenReturn(List.of(sms));
        when(client.report("m1")).thenReturn(Optional.of(new DeliveryReportClient.DeliveryReport(true, "DELIVRD")));
        Conversation updated = conversation(ConversationStatus.DELIVERED, Instant.now());
        when(conversations.findById(c.getId())).thenReturn(Optional.of(updated));

        assertThat(service(client).refresh(c)).isEqualTo(ConversationStatus.DELIVERED);
        verify(deliveries).applyReport(sms, DeliveryStatus.DELIVERED, null);
    }

    @Test
    void rejectedReportMarksTheSmsFailedWithTheOperatorCode() {
        Conversation c = conversation(ConversationStatus.SENT, Instant.now());
        ConversationDelivery sms = delivery(c.getId(), Channel.SMS, "tunisiesms", "m2", DeliveryStatus.SENT);
        when(deliveryRepository.findAllByConversationId(c.getId())).thenReturn(List.of(sms));
        when(client.report("m2")).thenReturn(Optional.of(new DeliveryReportClient.DeliveryReport(false, "REJECTED")));
        when(conversations.findById(c.getId())).thenReturn(Optional.of(c));

        service(client).refresh(c);

        verify(deliveries).applyReport(sms, DeliveryStatus.FAILED, "REJECTED");
    }

    @Test
    void noReportYetKeepsItSent() {
        Conversation c = conversation(ConversationStatus.SENT, Instant.now());
        ConversationDelivery sms = delivery(c.getId(), Channel.SMS, "tunisiesms", "m3", DeliveryStatus.SENT);
        when(deliveryRepository.findAllByConversationId(c.getId())).thenReturn(List.of(sms));
        when(client.report("m3")).thenReturn(Optional.empty());

        assertThat(service(client).refresh(c)).isEqualTo(ConversationStatus.SENT);
        verify(deliveries, never()).applyReport(any(), any(), any());
    }

    @Test
    void whatsAppDeliveriesAreNotPolled() {
        Conversation c = conversation(ConversationStatus.SENT, Instant.now());
        ConversationDelivery wa = delivery(c.getId(), Channel.WHATSAPP, "meta", "wamid.1", DeliveryStatus.SENT);
        when(deliveryRepository.findAllByConversationId(c.getId())).thenReturn(List.of(wa));

        assertThat(service(client).refresh(c)).isEqualTo(ConversationStatus.SENT);
        verify(client, never()).report(anyString());
    }

    @Test
    void finalStatusesOldAlertsAndProvidersWithoutReportsAreLeftAlone() {
        Conversation delivered = conversation(ConversationStatus.DELIVERED, Instant.now());
        Conversation old = conversation(ConversationStatus.SENT,
                Instant.now().minus(DeliveryStatusService.REPORT_WINDOW).minus(Duration.ofMinutes(1)));

        assertThat(service(client).refresh(delivered)).isEqualTo(ConversationStatus.DELIVERED);
        assertThat(service(client).refresh(old)).isEqualTo(ConversationStatus.SENT);
        assertThat(service(null).refresh(conversation(ConversationStatus.SENT, Instant.now())))
                .isEqualTo(ConversationStatus.SENT);
        verify(client, never()).report(anyString());
    }

    @Test
    void backgroundPassChecksEveryWaitingSmsAndSurvivesErrors() {
        ConversationDelivery a = delivery(UUID.randomUUID(), Channel.SMS, "tunisiesms", "a", DeliveryStatus.SENT);
        ConversationDelivery b = delivery(UUID.randomUUID(), Channel.SMS, "tunisiesms", "b", DeliveryStatus.SENT);
        DeliveryStatusService service = service(client);
        when(deliveryRepository
                .findTop100ByChannelAndProviderAndStatusAndProviderMessageIdIsNotNullAndCreatedAtAfterOrderByCreatedAtAsc(
                        any(), any(), any(), any())).thenReturn(List.of(a, b));
        when(client.report("a")).thenThrow(new IllegalStateException("boom"));
        when(client.report("b")).thenReturn(Optional.of(new DeliveryReportClient.DeliveryReport(true, "DELIVRD")));

        service.refreshPending();

        verify(deliveries).applyReport(b, DeliveryStatus.DELIVERED, null);
    }

    @Test
    void pushedReportIsAppliedToTheMatchingDelivery() {
        ConversationDelivery wa = delivery(UUID.randomUUID(), Channel.WHATSAPP, "meta", "wamid.9", DeliveryStatus.SENT);
        when(deliveryRepository.findByProviderAndProviderMessageId("meta", "wamid.9")).thenReturn(Optional.of(wa));

        assertThat(service(client).applyPushedReport("meta", "wamid.9", DeliveryStatus.READ, null)).isTrue();
        verify(deliveries).applyReport(wa, DeliveryStatus.READ, null);
    }

    @Test
    void pushedReportForAnUnknownMessageIsIgnored() {
        when(deliveryRepository.findByProviderAndProviderMessageId("meta", "other")).thenReturn(Optional.empty());

        assertThat(service(client).applyPushedReport("meta", "other", DeliveryStatus.DELIVERED, null)).isFalse();
        verify(deliveries, never()).applyReport(any(), any(), any());
    }

    @Test
    void contentRefusedByTheOperatorIsRecognised() {
        UUID refusedId = UUID.randomUUID();
        ConversationDelivery refused = delivery(refusedId, Channel.SMS, "tunisiesms", "r1", DeliveryStatus.FAILED);
        refused.setError("UNAUTHORIZED");
        when(deliveryRepository.findAllByConversationId(refusedId)).thenReturn(List.of(refused));
        UUID rejectedId = UUID.randomUUID();
        ConversationDelivery rejected = delivery(rejectedId, Channel.SMS, "tunisiesms", "r2", DeliveryStatus.FAILED);
        rejected.setError("REJECTED");
        when(deliveryRepository.findAllByConversationId(rejectedId)).thenReturn(List.of(rejected));

        assertThat(service(client).refusedForContent(refusedId)).isTrue();
        assertThat(service(client).refusedForContent(rejectedId)).isFalse();
    }
}
