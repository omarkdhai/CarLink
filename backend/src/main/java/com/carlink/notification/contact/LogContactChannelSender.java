package com.carlink.notification.contact;

import com.carlink.common.security.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev-only contact sender: prints the would-be delivery as an INFO log line so
 * a scan-and-submit can be exercised without a real provider. The production
 * implementation is {@code TelnyxContactChannelSender} (SMS).
 *
 * <p>Selected by {@code carlink.contact.provider=mock} (the default — including
 * in production until a real provider is configured, see
 * {@code ProductionProviderGuard}). It therefore logs <em>metadata only</em>
 * (security plan S-16): the visitor's message is never written to a log, and
 * the owner phone appears only as its SHA-256 so a dev can correlate lines.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "carlink.contact.provider", havingValue = "mock", matchIfMissing = true)
public class LogContactChannelSender implements ContactChannelSender {

    private final TokenGenerator tokenGenerator;

    @Override
    public boolean send(ContactDelivery delivery) {
        String note = delivery.message();
        log.info("[CONTACT-DEV] channel={} | owner={} | reason={} | message={} chars (not logged)",
                delivery.channel(),
                tokenGenerator.sha256(delivery.ownerPhone()),
                delivery.reason(),
                note == null ? 0 : note.length());
        return true;
    }
}
