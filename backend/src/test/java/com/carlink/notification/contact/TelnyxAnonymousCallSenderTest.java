package com.carlink.notification.contact;

import com.carlink.TestProperties;
import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.security.TokenGenerator;
import com.telnyx.sdk.client.TelnyxClient;
import com.telnyx.sdk.models.calls.CallDialParams;
import com.telnyx.sdk.models.calls.CallDialResponse;
import com.telnyx.sdk.services.blocking.CallService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelnyxAnonymousCallSenderTest {

    @Mock private TelnyxClient telnyxClient;
    @Mock private CallService callService;
    @Mock private CallDialResponse response;
    @Mock private CallDialResponse.Data data;

    private final TokenGenerator tokenGenerator = new TokenGenerator();
    private final CarLinkProperties properties = TestProperties.minimal();
    private final String ownerPhone = "+21655123456";

    private TelnyxAnonymousCallSender sender() {
        return new TelnyxAnonymousCallSender(telnyxClient, properties, tokenGenerator);
    }

    @Test
    void placesCallWithConfiguredAudioClip() {
        when(telnyxClient.calls()).thenReturn(callService);
        when(callService.dial(any(CallDialParams.class))).thenReturn(response);
        when(response.data()).thenReturn(Optional.of(data));
        when(data.callControlId()).thenReturn("cc-1");

        boolean placed = sender().initiateCall(ownerPhone, "BLOCKING", "note", "conv-1");

        assertThat(placed).isTrue();

        ArgumentCaptor<CallDialParams> captor = ArgumentCaptor.forClass(CallDialParams.class);
        verify(callService).dial(captor.capture());
        CallDialParams sent = captor.getValue();
        assertThat(sent.from()).isEqualTo(properties.telnyx().voiceFrom());
        assertThat(sent.connectionId()).isEqualTo(properties.telnyx().connectionId());
        assertThat(sent.audioUrl()).contains(properties.telnyx().voiceAudioUrl());
        assertThat(sent.to().isString()).isTrue();
        assertThat(sent.to().asString()).isEqualTo(ownerPhone);
    }

    @Test
    void skipsCallWhenNoAudioClipConfigured() {
        CarLinkProperties noClip = withAudioUrl("");
        TelnyxAnonymousCallSender sender =
                new TelnyxAnonymousCallSender(telnyxClient, noClip, tokenGenerator);

        assertThat(sender.initiateCall(ownerPhone, "BLOCKING", "note", "conv-1")).isFalse();
        // A call that would connect to silence must not be placed at all.
        verifyNoInteractions(telnyxClient);
    }

    @Test
    void returnsFalseWhenProviderGivesNoCallControlId() {
        when(telnyxClient.calls()).thenReturn(callService);
        when(callService.dial(any(CallDialParams.class))).thenReturn(response);
        when(response.data()).thenReturn(Optional.of(data));
        when(data.callControlId()).thenReturn(null);

        assertThat(sender().initiateCall(ownerPhone, "BLOCKING", "note", "conv-1")).isFalse();
    }

    private CarLinkProperties withAudioUrl(String audioUrl) {
        CarLinkProperties p = TestProperties.minimal();
        CarLinkProperties.Telnyx t = p.telnyx();
        return new CarLinkProperties(p.baseUrl(), p.publicUrl(), p.jwt(), p.conversation(),
                p.qr(), p.ratelimit(), p.contact(), p.contactForm(), p.email(), p.security(),
                p.admin(), new CarLinkProperties.Telnyx(
                        t.apiKey(), t.smsFrom(), t.voiceFrom(), t.connectionId(), audioUrl));
    }
}
