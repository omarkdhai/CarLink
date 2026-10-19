package com.carlink.notification.contact;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.security.TokenGenerator;
import com.carlink.conversation.model.Channel;
import com.carlink.qr.model.ContactReason;
import com.telnyx.sdk.client.TelnyxClient;
import com.telnyx.sdk.models.messages.MessageSendParams;
import com.telnyx.sdk.models.messages.MessageSendResponse;
import com.telnyx.sdk.models.messages.MessagingOutboundMessagePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Telnyx-backed relay of a visitor's message to the vehicle owner.
 *
 * <p>SMS only. WhatsApp is deliberately not attempted: Telnyx delivers WhatsApp
 * on {@code POST /v2/messages/whatsapp}, and free-form text there is rejected
 * outside a 24-hour window that only opens once the owner has messaged the
 * business number. An unprompted QR scan is always outside that window, so
 * WhatsApp requires a Meta-approved template — advertised channels are
 * restricted to SMS until one exists (see {@code carlink.contact.enabled-channels}).</p>
 *
 * <p>The owner phone is sent to Telnyx but never logged, persisted, or echoed —
 * only its SHA-256, and only for correlation.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "carlink.contact.provider", havingValue = "telnyx")
public class TelnyxContactChannelSender implements ContactChannelSender {

    private final TelnyxClient telnyxClient;
    private final CarLinkProperties properties;
    private final TokenGenerator tokenGenerator;

    @Override
    public boolean send(ContactDelivery delivery) {
        if (delivery.channel() != Channel.SMS) {
            throw new IllegalArgumentException(
                    "TelnyxContactChannelSender only delivers SMS, got " + delivery.channel());
        }

        String from = properties.telnyx() == null ? null : properties.telnyx().smsFrom();
        if (from == null || from.isBlank()) {
            throw new IllegalStateException(
                    "TELNYX_SMS_FROM is required to relay SMS via Telnyx");
        }

        String ownerHash = tokenGenerator.sha256(delivery.ownerPhone());

        MessageSendParams request = MessageSendParams.builder()
                .from(from)
                .to(delivery.ownerPhone())
                .text(buildBody(delivery))
                .build();

        MessageSendResponse response = telnyxClient.messages().send(request);

        String messageId = response.data()
                .flatMap(MessagingOutboundMessagePayload::id)
                .orElse(null);
        if (messageId == null) {
            // Accepted by the SDK but with no id means we cannot correlate it;
            // treat as unconfirmed rather than claiming success.
            log.warn("[TELNYX] SMS accepted with no message id (owner hash={})", ownerHash);
            return false;
        }

        log.info("[TELNYX] SMS sent id={} (owner hash={})", messageId, ownerHash);
        return true;
    }

    /**
     * Keeps the body ASCII-only so it stays in GSM-7 (160 chars/segment). An
     * emoji or curly punctuation would force UCS-2 and cut that to 70.
     */
    private String buildBody(ContactDelivery delivery) {
        String label = reasonLabel(delivery.reason());
        String note = delivery.message();
        if (note == null || note.isBlank()) {
            return "CarLink alert: " + label;
        }
        return "CarLink alert: " + label + ". Note: " + note.trim();
    }

    private String reasonLabel(String reason) {
        if (reason == null) {
            return "Alert";
        }
        try {
            return ContactReason.from(reason).label();
        } catch (IllegalArgumentException e) {
            return "Alert";
        }
    }
}
