package com.carlink.conversation.service;

import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationDelivery;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.model.DeliveryStatus;
import com.carlink.conversation.repository.ConversationDeliveryRepository;
import com.carlink.conversation.repository.ConversationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.carlink.conversation.model.DeliveryStatus.DELIVERED;
import static com.carlink.conversation.model.DeliveryStatus.FAILED;
import static com.carlink.conversation.model.DeliveryStatus.READ;
import static com.carlink.conversation.model.DeliveryStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationDeliveryServiceTest {

    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationDeliveryRepository deliveries = mock(ConversationDeliveryRepository.class);
    private final ConversationDeliveryService service = new ConversationDeliveryService(conversations, deliveries);

    @Test
    void aggregateIsDeliveredAsSoonAsOneChannelReachedTheOwner() {
        assertThat(ConversationDeliveryService.aggregate(List.of(FAILED, DELIVERED))).isEqualTo(ConversationStatus.DELIVERED);
        assertThat(ConversationDeliveryService.aggregate(List.of(SENT, READ))).isEqualTo(ConversationStatus.DELIVERED);
        assertThat(ConversationDeliveryService.aggregate(List.of(DELIVERED))).isEqualTo(ConversationStatus.DELIVERED);
    }

    @Test
    void aggregateIsSentWhileAChannelStillWaitsAndFailedOnlyWhenAllFailed() {
        assertThat(ConversationDeliveryService.aggregate(List.of(FAILED, SENT))).isEqualTo(ConversationStatus.SENT);
        assertThat(ConversationDeliveryService.aggregate(List.of(SENT))).isEqualTo(ConversationStatus.SENT);
        assertThat(ConversationDeliveryService.aggregate(List.of(FAILED, FAILED))).isEqualTo(ConversationStatus.FAILED);
        assertThat(ConversationDeliveryService.aggregate(List.of())).isEqualTo(ConversationStatus.FAILED);
    }

    private Conversation conversation(ConversationStatus status) {
        Conversation c = Conversation.builder().id(UUID.randomUUID()).status(status).createdAt(Instant.now()).build();
        when(conversations.lockById(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private static ConversationDelivery delivery(UUID conversationId, Channel channel, DeliveryStatus status) {
        return ConversationDelivery.builder().id(UUID.randomUUID()).conversationId(conversationId)
                .channel(channel).provider("p").status(status).build();
    }

    @Test
    void recomputeStoresTheAggregateOnTheLockedConversation() {
        Conversation c = conversation(ConversationStatus.PENDING);
        when(deliveries.findAllByConversationId(c.getId())).thenReturn(List.of(
                delivery(c.getId(), Channel.SMS, FAILED), delivery(c.getId(), Channel.WHATSAPP, SENT)));

        assertThat(service.recompute(c.getId())).isEqualTo(ConversationStatus.SENT);
        assertThat(c.getStatus()).isEqualTo(ConversationStatus.SENT);
    }

    @Test
    void expiredIsFinal() {
        Conversation c = conversation(ConversationStatus.EXPIRED);
        assertThat(service.recompute(c.getId())).isEqualTo(ConversationStatus.EXPIRED);
        assertThat(c.getStatus()).isEqualTo(ConversationStatus.EXPIRED);
    }

    @Test
    void reportsOnlyMoveForward() {
        Conversation c = conversation(ConversationStatus.SENT);
        ConversationDelivery wa = delivery(c.getId(), Channel.WHATSAPP, SENT);

        service.applyReport(wa, DELIVERED, "ignored");
        verify(deliveries).applyReport(eq(wa.getId()), eq(DELIVERED), eq(null), any(), eq(EnumSet.of(SENT)));

        service.applyReport(wa, READ, null);
        verify(deliveries).applyReport(eq(wa.getId()), eq(READ), eq(null), any(), eq(EnumSet.of(SENT, DELIVERED)));

        service.applyReport(wa, FAILED, "WA_131026");
        verify(deliveries).applyReport(eq(wa.getId()), eq(FAILED), eq("WA_131026"), any(), eq(EnumSet.of(SENT)));
    }
}
