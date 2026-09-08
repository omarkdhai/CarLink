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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactDeliveryServiceTest {

    @Mock private ContactChannelSender sender;
    @Mock private ConversationService conversationService;

    private ContactDeliveryService service;
    private final UUID conversationId = UUID.randomUUID();
    private final String ownerPhone = "+21655123456";
    private final String message = "Is the car still available?";

    @BeforeEach
    void setUp() {
        service = new ContactDeliveryService(sender, conversationService);
    }

    @Test
    void deliveredMessageMarksConversationSent() {
        when(sender.send(new ContactDelivery(Channel.WHATSAPP, ownerPhone, message)))
                .thenReturn(true);

        service.deliver(conversationId, Channel.WHATSAPP, ownerPhone, message);

        ArgumentCaptor<ContactDelivery> captor = ArgumentCaptor.forClass(ContactDelivery.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().channel()).isEqualTo(Channel.WHATSAPP);
        assertThat(captor.getValue().ownerPhone()).isEqualTo(ownerPhone);
        assertThat(captor.getValue().message()).isEqualTo(message);
        verify(conversationService).updateStatus(conversationId, ConversationStatus.SENT);
    }

    @Test
    void rejectedByProviderMarksConversationFailed() {
        when(sender.send(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        service.deliver(conversationId, Channel.SMS, ownerPhone, message);

        verify(conversationService).updateStatus(conversationId, ConversationStatus.FAILED);
    }

    @Test
    void throwingProviderMarksConversationFailedAndDoesNotPropagate() {
        when(sender.send(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("provider down"));

        assertThatCode(() -> service.deliver(conversationId, Channel.SMS, ownerPhone, message))
                .doesNotThrowAnyException();

        verify(conversationService).updateStatus(conversationId, ConversationStatus.FAILED);
    }
}
