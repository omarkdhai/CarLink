package com.carlink.notification.telnyx;

import com.carlink.common.config.CarLinkProperties;
import com.telnyx.sdk.client.TelnyxClient;
import com.telnyx.sdk.client.okhttp.TelnyxOkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the single {@link TelnyxClient} shared by every Telnyx-backed sender.
 *
 * <p>Only created when {@code carlink.contact.provider=telnyx}, so the default
 * mock configuration never constructs a client and never needs an API key.</p>
 *
 * <p>A missing key is a hard startup failure rather than a lazy error at the
 * first scan: an operator who sets the provider to {@code telnyx} but forgets
 * the key should not discover it from a visitor's failed message.</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "carlink.contact.provider", havingValue = "telnyx")
public class TelnyxClientConfig {

    @Bean
    public TelnyxClient telnyxClient(CarLinkProperties properties) {
        CarLinkProperties.Telnyx telnyx = properties.telnyx();
        String apiKey = telnyx == null ? null : telnyx.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "carlink.contact.provider=telnyx requires TELNYX_API_KEY");
        }
        log.info("Telnyx client initialised (SMS from {})", telnyx.smsFrom());
        return TelnyxOkHttpClient.builder().apiKey(apiKey).build();
    }
}
