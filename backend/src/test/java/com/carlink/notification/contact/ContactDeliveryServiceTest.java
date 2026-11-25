package com.carlink.notification.contact;

import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.model.DeliveryStatus;
import com.carlink.conversation.service.ConversationDeliveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContactDeliveryServiceTest {

    @Mock private ContactChannelSender sms;
    @Mock private ContactChannelSender whatsapp;
    @Mock private AnonymousCallSender anonymousCallSender;
    @Mock private ConversationDeliveryService deliveries;

    private ContactDeliveryService service;
    private final UUID conversationId = UUID.randomUUID();
    private final String ownerPhone = "+21655123456";
    private final String message = "Is the car still available?";
    private final String reason = "BLOCKING";

    @BeforeEach
    void setUp() {
        when(sms.channel()).thenReturn(Channel.SMS);
        when(sms.provider()).thenReturn("tunisiesms");
        when(whatsapp.channel()).thenReturn(Channel.WHATSAPP);
        when(whatsapp.provider()).thenReturn("meta");
        // The aggregate itself is ConversationDeliveryService's job (tested there).
        when(deliveries.recompute(conversationId)).thenReturn(ConversationStatus.SENT);
        service = new ContactDeliveryService(List.of(whatsapp, sms), anonymousCallSender, deliveries);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    private ConversationStatus deliver(String phone, boolean whatsappAlerts) {
        return service.deliver(conversationId, phone, whatsappAlerts, message, reason);
    }

    @Test
    void sendsOnSmsAndWhatsAppAndRecordsEachChannel() {
        when(sms.send(any())).thenReturn(SendResult.accepted("sms-1"));
        when(whatsapp.send(any())).thenReturn(SendResult.accepted("wamid.1"));

        assertThat(deliver(ownerPhone, true)).isEqualTo(ConversationStatus.SENT);

        ArgumentCaptor<ContactDelivery> smsCall = ArgumentCaptor.forClass(ContactDelivery.class);
        verify(sms).send(smsCall.capture());
        assertThat(smsCall.getValue()).isEqualTo(new ContactDelivery(Channel.SMS, ownerPhone, message, reason));
        ArgumentCaptor<ContactDelivery> waCall = ArgumentCaptor.forClass(ContactDelivery.class);
        verify(whatsapp).send(waCall.capture());
        assertThat(waCall.getValue().channel()).isEqualTo(Channel.WHATSAPP);

        verify(deliveries).record(conversationId, Channel.SMS, "tunisiesms", DeliveryStatus.SENT, "sms-1", null);
        verify(deliveries).record(conversationId, Channel.WHATSAPP, "meta", DeliveryStatus.SENT, "wamid.1", null);
        verify(anonymousCallSender).initiateCall(ownerPhone, reason, message, conversationId.toString());
    }

    @Test
    void ownerWhoTurnedWhatsAppOffGetsSmsOnly() {
        when(sms.send(any())).thenReturn(SendResult.accepted("sms-1"));

        deliver(ownerPhone, false);

        verify(whatsapp, never()).send(any());
        verify(deliveries, never()).record(any(), eq(Channel.WHATSAPP), any(), any(), any(), any());
    }

    @Test
    void withoutAWhatsAppSenderOnlySmsIsSent() {
        service.shutdown();
        service = new ContactDeliveryService(List.of(sms), anonymousCallSender, deliveries);
        when(sms.send(any())).thenReturn(SendResult.delivered());

        deliver(ownerPhone, true);

        verify(deliveries).record(conversationId, Channel.SMS, "tunisiesms", DeliveryStatus.DELIVERED, null, null);
        assertThat(service.channels()).containsExactly("SMS");
    }

    @Test
    void channelsAreListedSmsFirst() {
        assertThat(service.channels()).containsExactly("SMS", "WHATSAPP");
    }

    @Test
    void oneChannelFailingStillCallsAndReturnsTheAggregate() {
        when(sms.send(any())).thenReturn(SendResult.failed("API_402"));
        when(whatsapp.send(any())).thenReturn(SendResult.accepted("wamid.2"));

        assertThat(deliver(ownerPhone, true)).isEqualTo(ConversationStatus.SENT);
        verify(deliveries).record(conversationId, Channel.SMS, "tunisiesms", DeliveryStatus.FAILED, null, "API_402");
        verify(anonymousCallSender).initiateCall(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void everyChannelFailingMeansNoCall() {
        when(sms.send(any())).thenReturn(SendResult.failed("API_402"));
        when(whatsapp.send(any())).thenReturn(SendResult.failed("WA_131030"));
        when(deliveries.recompute(conversationId)).thenReturn(ConversationStatus.FAILED);

        assertThat(deliver(ownerPhone, true)).isEqualTo(ConversationStatus.FAILED);
        verify(deliveries).record(conversationId, Channel.WHATSAPP, "meta", DeliveryStatus.FAILED, null, "WA_131030");
        verify(anonymousCallSender, never()).initiateCall(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void transportErrorsBecomeShortCodes() {
        when(sms.send(any())).thenThrow(new ResourceAccessException("timeout"));
        when(whatsapp.send(any())).thenThrow(new IllegalArgumentException("bad number"));

        deliver(ownerPhone, true);

        verify(deliveries).record(conversationId, Channel.SMS, "tunisiesms", DeliveryStatus.FAILED, null, "UNREACHABLE");
        verify(deliveries).record(conversationId, Channel.WHATSAPP, "meta", DeliveryStatus.FAILED, null, "INVALID_NUMBER");
    }

    @Test
    void anyOtherErrorIsError() {
        when(sms.send(any())).thenThrow(new IllegalStateException("misconfigured"));

        deliver(ownerPhone, false);

        verify(deliveries).record(conversationId, Channel.SMS, "tunisiesms", DeliveryStatus.FAILED, null, "ERROR");
    }

    @Test
    void ownerWithoutPhoneFailsEveryChannelWithoutCallingProviders() {
        deliver("  ", true);

        verify(sms, never()).send(any());
        verify(whatsapp, never()).send(any());
        verify(deliveries).record(conversationId, Channel.SMS, "none", DeliveryStatus.FAILED, null, "NO_PHONE");
        verify(deliveries).record(conversationId, Channel.WHATSAPP, "none", DeliveryStatus.FAILED, null, "NO_PHONE");
        verify(deliveries).recompute(conversationId);
    }

    @Test
    void aFailingAlertCallDoesNotDowngradeTheMessage() {
        when(sms.send(any())).thenReturn(SendResult.accepted("sms-2"));
        doThrow(new RuntimeException("call failed"))
                .when(anonymousCallSender).initiateCall(anyString(), anyString(), anyString(), anyString());

        assertThat(deliver(ownerPhone, false)).isEqualTo(ConversationStatus.SENT);
        verify(deliveries).record(eq(conversationId), eq(Channel.SMS), eq("tunisiesms"),
                eq(DeliveryStatus.SENT), eq("sms-2"), isNull());
    }
}
