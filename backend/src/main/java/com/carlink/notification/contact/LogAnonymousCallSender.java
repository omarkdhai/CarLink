package com.carlink.notification.contact;

import com.carlink.common.security.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev-only alert call: logs the would-be call instead of dialling anything.
 * Selected by {@code carlink.contact.provider=mock} (the default). The owner
 * phone is never logged raw, only its SHA-256.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "carlink.contact.provider", havingValue = "mock", matchIfMissing = true)
public class LogAnonymousCallSender implements AnonymousCallSender {

    private final TokenGenerator tokenGenerator;

    @Override
    public boolean initiateCall(String ownerPhone, String reason, String message, String conversationId) {
        log.info("[CALL-DEV] conversation={} | owner={} | reason={}",
                conversationId, tokenGenerator.sha256(ownerPhone), reason);
        return true;
    }
}
