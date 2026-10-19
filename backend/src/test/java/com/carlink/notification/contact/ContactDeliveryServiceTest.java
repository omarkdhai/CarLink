package com.carlink.notification.contact;

import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactDeliveryServiceTest {

    @Mock private ContactChannelSender sender;
    @Mock private AnonymousCallSender anonymousCallSender;
    @Mock private ConversationService conversationService;

    private ContactDeliveryService service;
    private final UUID conversationId = UUID.randomUUID();
    private final String ownerPhone = "+21655123456";
    private final String message = "Is the car still available?";
    private final String reason = "BLOCKING";

    @BeforeEach
    void setUp() {
        service = new ContactDeliveryService(sender, anonymousCallSender, conversationService);
    }

    @Test
    void deliveredMessageMarksConversationSent() {
        when(sender.send(new ContactDelivery(Channel.SMS, ownerPhone, message, reason)))
                .thenReturn(true);

        service.deliver(conversationId, Channel.SMS, ownerPhone, message, reason);

        ArgumentCaptor<ContactDelivery> captor = ArgumentCaptor.forClass(ContactDelivery.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().channel()).isEqualTo(Channel.SMS);
        assertThat(captor.getValue().ownerPhone()).isEqualTo(ownerPhone);
        assertThat(captor.getValue().message()).isEqualTo(message);
        assertThat(captor.getValue().reason()).isEqualTo(reason);
        // An anonymous masked call is also placed alongside the message relay.
        verify(anonymousCallSender).initiateCall(ownerPhone, reason, message, conversationId.toString());
        verify(conversationService).updateStatus(conversationId, ConversationStatus.SENT);
    }

    @Test
    void rejectedByProviderMarksConversationFailed() {
        when(sender.send(any())).thenReturn(false);

        service.deliver(conversationId, Channel.SMS, ownerPhone, message, reason);

        verify(conversationService).updateStatus(conversationId, ConversationStatus.FAILED);
    }

    @Test
    void throwingProviderMarksConversationFailedAndDoesNotPropagate() {
        when(sender.send(any())).thenThrow(new RuntimeException("provider down"));

        assertThatCode(() -> service.deliver(conversationId, Channel.SMS, ownerPhone, message, reason))
                .doesNotThrowAnyException();

        verify(conversationService).updateStatus(conversationId, ConversationStatus.FAILED);
        verify(anonymousCallSender, never()).initiateCall(any(), any(), any(), any());
    }

    @Test
    void failedCallDoesNotDowngradeDeliveredMessage() {
        when(sender.send(any())).thenReturn(true);
        when(anonymousCallSender.initiateCall(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("voice down"));

        assertThatCode(() -> service.deliver(conversationId, Channel.SMS, ownerPhone, message, reason))
                .doesNotThrowAnyException();

        verify(conversationService).updateStatus(conversationId, ConversationStatus.SENT);
    }

    @Test
    void missingOwnerPhoneFailsWithoutSending() {
        service.deliver(conversationId, Channel.SMS, "  ", message, reason);

        verify(sender, never()).send(any());
        verify(anonymousCallSender, never()).initiateCall(any(), any(), any(), any());
        verify(conversationService).updateStatus(conversationId, ConversationStatus.FAILED);
    }

    @Test
    void nullOwnerPhoneFailsWithoutSending() {
        service.deliver(conversationId, Channel.SMS, null, message, reason);

        verify(sender, never()).send(any());
        verify(conversationService).updateStatus(conversationId, ConversationStatus.FAILED);
    }
}
