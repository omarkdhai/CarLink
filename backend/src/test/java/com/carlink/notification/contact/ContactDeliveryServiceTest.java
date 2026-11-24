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
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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

    private ConversationStatus deliver(String phone) {
        return service.deliver(conversationId, Channel.SMS, phone, message, reason);
    }

    @Test
    void acceptedByTheProviderIsSentWithItsIdUntilTheReportConfirms() {
        when(sender.send(any())).thenReturn(SendResult.accepted("msg-1"));

        assertThat(deliver(ownerPhone)).isEqualTo(ConversationStatus.SENT);

        ArgumentCaptor<ContactDelivery> captor = ArgumentCaptor.forClass(ContactDelivery.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ContactDelivery(Channel.SMS, ownerPhone, message, reason));
        verify(conversationService).recordDelivery(conversationId, ConversationStatus.SENT, "msg-1", null);
        verify(anonymousCallSender).initiateCall(ownerPhone, reason, message, conversationId.toString());
    }

    @Test
    void certainDeliveryIsDelivered() {
        when(sender.send(any())).thenReturn(SendResult.delivered());

        assertThat(deliver(ownerPhone)).isEqualTo(ConversationStatus.DELIVERED);
        verify(conversationService).recordDelivery(conversationId, ConversationStatus.DELIVERED, null, null);
    }

    @Test
    void refusedByTheProviderIsFailedWithItsCodeAndNoCall() {
        when(sender.send(any())).thenReturn(SendResult.failed("API_402"));

        assertThat(deliver(ownerPhone)).isEqualTo(ConversationStatus.FAILED);
        verify(conversationService).recordDelivery(conversationId, ConversationStatus.FAILED, null, "API_402");
        verify(anonymousCallSender, never()).initiateCall(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unreachableProviderIsFailedNotThrown() {
        when(sender.send(any())).thenThrow(new ResourceAccessException("timeout"));

        assertThat(deliver(ownerPhone)).isEqualTo(ConversationStatus.FAILED);
        verify(conversationService).recordDelivery(conversationId, ConversationStatus.FAILED, null, "UNREACHABLE");
    }

    @Test
    void invalidNumberIsFailed() {
        when(sender.send(any())).thenThrow(new IllegalArgumentException("bad number"));

        assertThat(deliver(ownerPhone)).isEqualTo(ConversationStatus.FAILED);
        verify(conversationService).recordDelivery(conversationId, ConversationStatus.FAILED, null, "INVALID_NUMBER");
    }

    @Test
    void anyOtherErrorIsFailed() {
        when(sender.send(any())).thenThrow(new IllegalStateException("misconfigured"));

        assertThat(deliver(ownerPhone)).isEqualTo(ConversationStatus.FAILED);
        verify(conversationService).recordDelivery(conversationId, ConversationStatus.FAILED, null, "ERROR");
    }

    @Test
    void ownerWithoutPhoneIsFailedWithoutCallingTheProvider() {
        assertThat(deliver("  ")).isEqualTo(ConversationStatus.FAILED);
        assertThat(deliver(null)).isEqualTo(ConversationStatus.FAILED);
        verify(sender, never()).send(any());
    }

    @Test
    void aFailingAlertCallDoesNotDowngradeTheMessage() {
        when(sender.send(any())).thenReturn(SendResult.accepted("msg-2"));
        doThrow(new RuntimeException("call failed"))
                .when(anonymousCallSender).initiateCall(anyString(), anyString(), anyString(), anyString());

        assertThat(deliver(ownerPhone)).isEqualTo(ConversationStatus.SENT);
        verify(conversationService).recordDelivery(conversationId, ConversationStatus.SENT, "msg-2", null);
    }
}
