package com.carlink.notification.contact;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.security.TokenGenerator;
import com.telnyx.sdk.client.TelnyxClient;
import com.telnyx.sdk.models.calls.CallDialParams;
import com.telnyx.sdk.models.calls.CallDialResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Telnyx alert call: dials the owner on a Call Control connection and plays a
 * pre-recorded clip when they answer.
 *
 * <p>The clip is configured ({@code TELNYX_VOICE_AUDIO_URL}) rather than spoken
 * from the reason and message: the outbound dial API carries only a static
 * audio URL, so it cannot say a dynamic string. Speaking dynamic text needs a
 * Call Control {@code speak} command driven by a webhook, which this
 * application does not host. The visitor's actual message is delivered by SMS —
 * the call exists only to make the owner look at their phone.</p>
 *
 * <p>With no clip configured the call is skipped rather than placed, because a
 * call that connects to silence is worse than no call.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "carlink.contact.provider", havingValue = "telnyx")
public class TelnyxAnonymousCallSender implements AnonymousCallSender {

    private final TelnyxClient telnyxClient;
    private final CarLinkProperties properties;
    private final TokenGenerator tokenGenerator;

    @Override
    public boolean initiateCall(String ownerPhone, String reason, String message, String conversationId) {
        CarLinkProperties.Telnyx telnyx = properties.telnyx();
        String audioUrl = telnyx == null ? null : telnyx.voiceAudioUrl();
        if (audioUrl == null || audioUrl.isBlank()) {
            log.warn("[TELNYX] Alert call skipped, TELNYX_VOICE_AUDIO_URL is not set (conversation={})",
                    conversationId);
            return false;
        }

        CallDialParams params = CallDialParams.builder()
                .from(telnyx.voiceFrom())
                .to(ownerPhone)
                .connectionId(telnyx.connectionId())
                .audioUrl(audioUrl)
                .build();

        CallDialResponse response = telnyxClient.calls().dial(params);

        String callControlId = response.data()
                .map(CallDialResponse.Data::callControlId)
                .orElse(null);
        if (callControlId == null) {
            log.warn("[TELNYX] Alert call returned no call control id (conversation={})", conversationId);
            return false;
        }

        log.info("[TELNYX] Alert call placed id={} (conversation={}, owner hash={})",
                callControlId, conversationId, tokenGenerator.sha256(ownerPhone));
        return true;
    }
}
