package com.carlink.notification.whatsapp;

import com.carlink.common.security.TokenGenerator;
import com.carlink.conversation.model.Channel;
import com.carlink.notification.contact.ContactChannelSender;
import com.carlink.notification.contact.ContactDelivery;
import com.carlink.notification.contact.SendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev WhatsApp sender ({@code carlink.whatsapp.provider=mock}): logs metadata
 * only (the owner as a hash, the note's length) and reports it delivered.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "carlink.whatsapp.provider", havingValue = "mock")
public class MockWhatsAppSender implements ContactChannelSender {

    private final TokenGenerator tokenGenerator;

    @Override
    public Channel channel() {
        return Channel.WHATSAPP;
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public SendResult send(ContactDelivery delivery) {
        String note = delivery.message();
        log.info("[WHATSAPP-DEV] owner={} | reason={} | message={} chars (not logged)",
                tokenGenerator.sha256(delivery.ownerPhone()), delivery.reason(),
                note == null ? 0 : note.length());
        return SendResult.delivered();
    }
}
