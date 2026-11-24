package com.carlink.notification.tunisiesms;

import com.carlink.notification.contact.AnonymousCallSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * TunisieSMS sends SMS only: there is no alert call with this provider. The
 * relay treats the call as best-effort, so reporting "skipped" is enough.
 */
@Component
@ConditionalOnProperty(name = "carlink.contact.provider", havingValue = "tunisiesms")
public class SkippedAnonymousCallSender implements AnonymousCallSender {

    @Override
    public boolean initiateCall(String ownerPhone, String reason, String message, String conversationId) {
        return false;
    }
}
