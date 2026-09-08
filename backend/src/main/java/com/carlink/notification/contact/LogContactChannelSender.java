package com.carlink.notification.contact;

import com.carlink.common.security.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev-only contact sender for both WhatsApp and SMS: prints the would-be
 * delivery as an INFO log line so a scan-and-submit can be exercised without
 * a real provider (Twilio/WhatsApp Business).
 *
 * <p><strong>Never active in production.</strong> Guarded by
 * {@code carlink.contact.provider=mock} (the default). The owner phone is
 * <em>never</em> logged raw — only its SHA-256, so a dev can correlate the
 * line to an owner without the number ever appearing in a log.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "carlink.contact.provider", havingValue = "mock", matchIfMissing = true)
public class LogContactChannelSender implements ContactChannelSender {

    private final TokenGenerator tokenGenerator;

    @Override
    public boolean send(ContactDelivery delivery) {
        log.info("[CONTACT-DEV] channel={} | owner={} | message={}",
                delivery.channel(),
                tokenGenerator.sha256(delivery.ownerPhone()),
                delivery.message());
        return true;
    }
}
