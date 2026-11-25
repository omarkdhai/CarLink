package com.carlink.notification.whatsapp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WhatsApp alerts through Meta's WhatsApp Cloud API (setup: docs/whatsapp.md).
 *
 * <p>{@code provider}: {@code off} (default, SMS only), {@code mock} (logs only)
 * or {@code meta}. Secrets come from the environment and are never logged.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "carlink.whatsapp")
public class WhatsAppProperties {

    private String provider = "off";
    /** Phone number ID of the CarLink sender number (WhatsApp Manager, API setup). */
    private String phoneNumberId = "";
    /** Permanent System User token with whatsapp_business_messaging. */
    private String accessToken = "";
    /** Meta app secret: verifies the X-Hub-Signature-256 of every webhook call. */
    private String appSecret = "";
    /** Our own random string, typed into the webhook settings in Meta. */
    private String verifyToken = "";
    /** Approved Utility template with two body variables: {{1}} reason, {{2}} note. */
    private String templateName = "carlink_alert";
    private String templateLanguage = "ar";
    private String apiVersion = "v25.0";
    private String baseUrl = "https://graph.facebook.com";
    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 10_000;
}
