package com.carlink.notification.contact;

import com.carlink.TestProperties;
import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.security.TokenGenerator;
import com.carlink.conversation.model.Channel;
import com.telnyx.sdk.client.TelnyxClient;
import com.telnyx.sdk.models.messages.MessageSendParams;
import com.telnyx.sdk.models.messages.MessageSendResponse;
import com.telnyx.sdk.models.messages.MessagingOutboundMessagePayload;
import com.telnyx.sdk.services.blocking.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelnyxContactChannelSenderTest {

    @Mock private TelnyxClient telnyxClient;
    @Mock private MessageService messageService;
    @Mock private MessageSendResponse response;
    @Mock private MessagingOutboundMessagePayload payload;

    private final TokenGenerator tokenGenerator = new TokenGenerator();
    private final CarLinkProperties properties = TestProperties.minimal();
    private final String ownerPhone = "+21655123456";

    private TelnyxContactChannelSender sender() {
        return new TelnyxContactChannelSender(telnyxClient, properties, tokenGenerator);
    }

    @Test
    void sendsSmsAndReturnsTrue() {
        when(telnyxClient.messages()).thenReturn(messageService);
        when(messageService.send(any(MessageSendParams.class))).thenReturn(response);
        when(response.data()).thenReturn(Optional.of(payload));
        when(payload.id()).thenReturn(Optional.of("msg-1"));

        boolean delivered = sender().send(
                new ContactDelivery(Channel.SMS, ownerPhone, "Is the car available?", "BLOCKING"));

        assertThat(delivered).isTrue();

        ArgumentCaptor<MessageSendParams> captor = ArgumentCaptor.forClass(MessageSendParams.class);
        verify(messageService).send(captor.capture());
        MessageSendParams sent = captor.getValue();
        assertThat(sent.to()).isEqualTo(ownerPhone);
        assertThat(sent.from()).contains(properties.telnyx().smsFrom());
        assertThat(sent.text()).hasValueSatisfying(t -> {
            assertThat(t).contains("Is the car available?");
            assertThat(t).contains("Your car is blocking me"); // ContactReason.BLOCKING label
        });
    }

    @Test
    void returnsFalseWhenProviderGivesNoMessageId() {
        when(telnyxClient.messages()).thenReturn(messageService);
        when(messageService.send(any(MessageSendParams.class))).thenReturn(response);
        when(response.data()).thenReturn(Optional.of(payload));
        when(payload.id()).thenReturn(Optional.empty());

        assertThat(sender().send(new ContactDelivery(Channel.SMS, ownerPhone, "hi", "BLOCKING")))
                .isFalse();
    }

    @Test
    void rejectsNonSmsChannel() {
        assertThatThrownBy(() -> sender().send(
                new ContactDelivery(Channel.WHATSAPP, ownerPhone, "hi", "BLOCKING")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenSmsFromIsNotConfigured() {
        CarLinkProperties blankFrom = withSmsFrom("");
        TelnyxContactChannelSender sender =
                new TelnyxContactChannelSender(telnyxClient, blankFrom, tokenGenerator);

        assertThatThrownBy(() -> sender.send(
                new ContactDelivery(Channel.SMS, ownerPhone, "hi", "BLOCKING")))
                .isInstanceOf(IllegalStateException.class);
    }

    private CarLinkProperties withSmsFrom(String smsFrom) {
        CarLinkProperties p = TestProperties.minimal();
        CarLinkProperties.Telnyx t = p.telnyx();
        return new CarLinkProperties(p.baseUrl(), p.publicUrl(), p.jwt(), p.conversation(),
                p.qr(), p.ratelimit(), p.contact(), p.contactForm(), p.email(), p.security(),
                p.admin(), new CarLinkProperties.Telnyx(
                        t.apiKey(), smsFrom, t.voiceFrom(), t.connectionId(), t.voiceAudioUrl()));
    }
}
