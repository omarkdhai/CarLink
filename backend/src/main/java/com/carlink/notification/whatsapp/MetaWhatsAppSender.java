package com.carlink.notification.whatsapp;

import com.carlink.common.security.TokenGenerator;
import com.carlink.conversation.model.Channel;
import com.carlink.notification.contact.ContactChannelSender;
import com.carlink.notification.contact.ContactDelivery;
import com.carlink.notification.contact.SendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Sends the alert to the owner's WhatsApp through Meta's WhatsApp Cloud API.
 *
 * <p>The owner never wrote to CarLink first, so only an approved <b>template</b>
 * may be sent ({@code carlink_alert}, Utility, Arabic): {@code {{1}}} is the
 * reason line in Derja, {@code {{2}}} the visitor's note ({@link WhatsAppTemplateText}).</p>
 *
 * <p>Reply on success: {@code {"contacts":[{"wa_id":...}],"messages":[{"id":"wamid..."}]}}.
 * That means accepted only; whether the phone got it arrives later on the webhook
 * ({@link WhatsAppWebhookController}). On error: {@code {"error":{"code":131030,...}}},
 * recorded as {@code WA_<code>}. Replies echo the number ({@code wa_id}), so
 * bodies are never logged; neither is the token.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "carlink.whatsapp.provider", havingValue = "meta")
public class MetaWhatsAppSender implements ContactChannelSender {

    private final RestClient client;
    private final WhatsAppProperties properties;
    private final TokenGenerator tokenGenerator;
    private final ObjectMapper objectMapper;

    @Autowired
    public MetaWhatsAppSender(WhatsAppProperties properties, TokenGenerator tokenGenerator,
                              ObjectMapper objectMapper) {
        this(buildClient(properties), properties, tokenGenerator, objectMapper);
    }

    MetaWhatsAppSender(RestClient client, WhatsAppProperties properties,
                       TokenGenerator tokenGenerator, ObjectMapper objectMapper) {
        require(properties.getPhoneNumberId(), "WHATSAPP_PHONE_NUMBER_ID");
        require(properties.getAccessToken(), "WHATSAPP_ACCESS_TOKEN");
        require(properties.getAppSecret(), "WHATSAPP_APP_SECRET");
        require(properties.getVerifyToken(), "WHATSAPP_VERIFY_TOKEN");
        require(properties.getTemplateName(), "WHATSAPP_TEMPLATE_NAME");
        this.client = client;
        this.properties = properties;
        this.tokenGenerator = tokenGenerator;
        this.objectMapper = objectMapper;
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when WHATSAPP_PROVIDER=meta");
        }
    }

    private static RestClient buildClient(WhatsAppProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Channel channel() {
        return Channel.WHATSAPP;
    }

    @Override
    public String provider() {
        return "meta";
    }

    @Override
    public SendResult send(ContactDelivery delivery) {
        String ownerHash = tokenGenerator.sha256(delivery.ownerPhone());
        Map<String, Object> payload = payload(delivery, properties);
        String url = properties.getBaseUrl() + "/" + properties.getApiVersion().trim()
                + "/" + properties.getPhoneNumberId().trim() + "/messages";

        Reply reply = client.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken().trim())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .exchange((request, response) ->
                        new Reply(response.getStatusCode().value(), response.bodyTo(String.class)));

        JsonNode body = parse(reply.body());
        String messageId = body.path("messages").path(0).path("id").asString("");
        boolean ok = reply.status() / 100 == 2;
        if (ok && !messageId.isBlank()) {
            log.info("[WHATSAPP] accepted id={} (owner hash={})", messageId, ownerHash);
            return SendResult.accepted(messageId);
        }
        if (ok) {
            log.warn("[WHATSAPP] accepted with no message id (owner hash={})", ownerHash);
            return SendResult.failed("NO_MESSAGE_ID");
        }
        int code = body.path("error").path("code").asInt(reply.status());
        log.warn("[WHATSAPP] refused: HTTP {} code {} (owner hash={})", reply.status(), code, ownerHash);
        return SendResult.failed("WA_" + code);
    }

    /** The template message. The phone goes in the body, never in the URL. */
    static Map<String, Object> payload(ContactDelivery delivery, WhatsAppProperties properties) {
        List<Map<String, String>> parameters = List.of(
                Map.of("type", "text", "text", WhatsAppTemplateText.reason(delivery.reason())),
                Map.of("type", "text", "text", WhatsAppTemplateText.note(delivery.message())));
        return Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", toWaNumber(delivery.ownerPhone()),
                "type", "template",
                "template", Map.of(
                        "name", properties.getTemplateName().trim(),
                        "language", Map.of("code", properties.getTemplateLanguage().trim()),
                        "components", List.of(Map.of("type", "body", "parameters", parameters))));
    }

    /** E.164 "+21612345678" becomes "21612345678". */
    static String toWaNumber(String e164) {
        String digits = e164 == null ? "" : e164.replaceAll("[\\s+]", "");
        if (!digits.matches("\\d{8,15}")) {
            // Deliberately without the value: it is the owner's phone number.
            throw new IllegalArgumentException("Owner phone is not a valid international number");
        }
        return digits;
    }

    private JsonNode parse(String body) {
        try {
            return body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
        } catch (RuntimeException e) {
            return objectMapper.createObjectNode();
        }
    }

    private record Reply(int status, String body) {
    }
}
