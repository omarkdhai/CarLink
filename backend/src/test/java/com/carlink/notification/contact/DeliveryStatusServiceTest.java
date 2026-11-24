package com.carlink.notification.contact;

import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.repository.ConversationRepository;
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
    private final ConversationRepository repository = mock(ConversationRepository.class);

    @SuppressWarnings("unchecked")
    private DeliveryStatusService service(DeliveryReportClient reportClient) {
        ObjectProvider<DeliveryReportClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(reportClient);
        return new DeliveryStatusService(provider, repository);
    }

    private static Conversation sent(String providerId, Instant createdAt) {
        return Conversation.builder().id(UUID.randomUUID()).status(ConversationStatus.SENT)
                .providerMessageId(providerId).createdAt(createdAt).build();
    }

    @Test
    void delivrdReportMakesItDelivered() {
        Conversation c = sent("m1", Instant.now());
        when(client.report("m1")).thenReturn(Optional.of(new DeliveryReportClient.DeliveryReport(true, "DELIVRD")));
        when(repository.applyDeliveryReport(c.getId(), ConversationStatus.DELIVERED, null)).thenReturn(1);

        assertThat(service(client).refresh(c)).isEqualTo(ConversationStatus.DELIVERED);
    }

    @Test
    void rejectedReportMakesItFailedWithTheOperatorCode() {
        Conversation c = sent("m2", Instant.now());
        when(client.report("m2")).thenReturn(Optional.of(new DeliveryReportClient.DeliveryReport(false, "REJECTED")));
        when(repository.applyDeliveryReport(c.getId(), ConversationStatus.FAILED, "REJECTED")).thenReturn(1);

        assertThat(service(client).refresh(c)).isEqualTo(ConversationStatus.FAILED);
    }

    @Test
    void noReportYetKeepsItSent() {
        Conversation c = sent("m3", Instant.now());
        when(client.report("m3")).thenReturn(Optional.empty());

        assertThat(service(client).refresh(c)).isEqualTo(ConversationStatus.SENT);
        verify(repository, never()).applyDeliveryReport(any(), any(), any());
    }

    @Test
    void finalStatusesOldAlertsAndProvidersWithoutReportsAreLeftAlone() {
        Conversation delivered = sent("m4", Instant.now());
        delivered.setStatus(ConversationStatus.DELIVERED);
        Conversation old = sent("m5", Instant.now().minus(DeliveryStatusService.REPORT_WINDOW).minus(Duration.ofMinutes(1)));
        Conversation noId = sent(null, Instant.now());

        assertThat(service(client).refresh(delivered)).isEqualTo(ConversationStatus.DELIVERED);
        assertThat(service(client).refresh(old)).isEqualTo(ConversationStatus.SENT);
        assertThat(service(client).refresh(noId)).isEqualTo(ConversationStatus.SENT);
        assertThat(service(null).refresh(sent("m6", Instant.now()))).isEqualTo(ConversationStatus.SENT);
        verify(client, never()).report(anyString());
    }

    @Test
    void aReportArrivingAfterAnotherUpdateRereadsTheStatus() {
        Conversation c = sent("m7", Instant.now());
        when(client.report("m7")).thenReturn(Optional.of(new DeliveryReportClient.DeliveryReport(true, "DELIVRD")));
        when(repository.applyDeliveryReport(c.getId(), ConversationStatus.DELIVERED, null)).thenReturn(0);
        Conversation expired = sent("m7", Instant.now());
        expired.setStatus(ConversationStatus.EXPIRED);
        when(repository.findById(c.getId())).thenReturn(Optional.of(expired));

        assertThat(service(client).refresh(c)).isEqualTo(ConversationStatus.EXPIRED);
    }

    @Test
    void backgroundPassChecksEveryWaitingAlertAndSurvivesErrors() {
        Conversation a = sent("a", Instant.now());
        Conversation b = sent("b", Instant.now());
        when(repository.findTop100ByStatusAndProviderMessageIdIsNotNullAndCreatedAtAfterOrderByCreatedAtAsc(
                any(), any())).thenReturn(List.of(a, b));
        when(client.report("a")).thenThrow(new IllegalStateException("boom"));
        when(client.report("b")).thenReturn(Optional.of(new DeliveryReportClient.DeliveryReport(true, "DELIVRD")));
        when(repository.applyDeliveryReport(b.getId(), ConversationStatus.DELIVERED, null)).thenReturn(1);

        service(client).refreshPending();

        verify(repository).applyDeliveryReport(b.getId(), ConversationStatus.DELIVERED, null);
    }
}
