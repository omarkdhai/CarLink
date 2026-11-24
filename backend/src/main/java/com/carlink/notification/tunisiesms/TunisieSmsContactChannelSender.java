package com.carlink.notification.tunisiesms;

import com.carlink.common.security.TokenGenerator;
import com.carlink.conversation.model.Channel;
import com.carlink.notification.contact.ContactChannelSender;
import com.carlink.notification.contact.ContactDelivery;
import com.carlink.notification.contact.DeliveryReportClient;
import com.carlink.notification.contact.SendResult;
import com.carlink.notification.sms.AlertSms;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Locale;
import java.util.Optional;

/**
 * Relays a visitor's alert to the vehicle owner by SMS through TunisieSMS (L2T).
 *
 * <p>The API accepts GET or POST with the same parameters. This sender always
 * POSTs a form body, so the API key and the owner's number are never part of a
 * URL — they cannot end up in a proxy log, an access log or an exception
 * message (CLAUDE.md: phone numbers are never embedded in URLs).</p>
 *
 * <p>Replies (verified against the live API): HTTP 200 and
 * {@code [{"message_id": "...", "status_code": 200, ...}]} on success; on error
 * the HTTP status <em>is</em> the API code (e.g. 430) with
 * {@code [{"status_code": 430, "status_msg": "..."}]}. The reply also echoes the
 * destination number ({@code status_mobile}), so it is never logged.</p>
 *
 * <p>SMS only; the alert call is skipped with this provider
 * ({@link SkippedAnonymousCallSender}).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "carlink.contact.provider", havingValue = "tunisiesms")
public class TunisieSmsContactChannelSender implements ContactChannelSender, DeliveryReportClient {

    private final RestClient client;
    private final TunisieSmsProperties properties;
    private final TokenGenerator tokenGenerator;
    private final ObjectMapper objectMapper;

    @Autowired
    public TunisieSmsContactChannelSender(TunisieSmsProperties properties,
                                          TokenGenerator tokenGenerator,
                                          ObjectMapper objectMapper) {
        this(buildClient(properties), properties, tokenGenerator, objectMapper);
    }

    TunisieSmsContactChannelSender(RestClient client, TunisieSmsProperties properties,
                                   TokenGenerator tokenGenerator, ObjectMapper objectMapper) {
        if (isBlank(properties.getApiKey())) {
            throw new IllegalStateException(
                    "TUNISIESMS_API_KEY is required when CONTACT_PROVIDER=tunisiesms");
        }
        if (isBlank(properties.getSender())) {
            throw new IllegalStateException(
                    "TUNISIESMS_SENDER is required when CONTACT_PROVIDER=tunisiesms");
        }
        this.client = client;
        this.properties = properties;
        this.tokenGenerator = tokenGenerator;
        this.objectMapper = objectMapper;
    }

    private static RestClient buildClient(TunisieSmsProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public SendResult send(ContactDelivery delivery) {
        if (delivery.channel() != Channel.SMS) {
            throw new IllegalArgumentException(
                    "TunisieSmsContactChannelSender only delivers SMS, got " + delivery.channel());
        }
        String ownerHash = tokenGenerator.sha256(delivery.ownerPhone());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("fct", "sms");
        form.add("key", properties.getApiKey().trim());
        form.add("mobile", toApiNumber(delivery.ownerPhone()));
        form.add("sender", properties.getSender().trim());
        form.add("sms", buildBody(delivery));
        form.add("content-type", "application/json");

        Reply reply = client.post()
                .uri(properties.getUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange((request, response) ->
                        new Reply(response.getStatusCode().value(), response.bodyTo(String.class)));

        Result result = parse(reply);
        if (result.code() == 200 && result.messageId() != null) {
            log.info("[TUNISIESMS] SMS accepted id={} (owner hash={})", result.messageId(), ownerHash);
            return SendResult.accepted(result.messageId());
        }
        if (result.code() == 200) {
            log.warn("[TUNISIESMS] SMS accepted with no message id (owner hash={})", ownerHash);
            return SendResult.failed("NO_MESSAGE_ID");
        }
        logFailure(result.code(), ownerHash);
        return SendResult.failed("API_" + result.code());
    }

    /** E.164 "+21612345678" → "21612345678": the API wants digits only. */
    static String toApiNumber(String e164) {
        String digits = e164 == null ? "" : e164.replaceAll("[\\s+]", "");
        if (!digits.matches("\\d{8,15}")) {
            // Deliberately without the value: it is the owner's phone number.
            throw new IllegalArgumentException("Owner phone is not a valid international number");
        }
        return digits;
    }

    /** The alert in Derja plus the visitor's note as typed, fitted to one SMS ({@link AlertSms}). */
    static String buildBody(ContactDelivery delivery) {
        return AlertSms.compose(delivery.reason(), delivery.message());
    }

    /**
     * Delivery report (fct=dlr). Verified against the live API: an empty list
     * until the operator reports, then
     * {@code [{"acknowledgement": "DELIVRD"|"REJECTED"|..., ...}]}. REJECTED
     * comes back within about a second; DELIVRD can take minutes. The reply
     * echoes the number, so it is never logged.
     */
    @Override
    public Optional<DeliveryReport> report(String providerMessageId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("fct", "dlr");
        form.add("key", properties.getApiKey().trim());
        form.add("msg_id", providerMessageId);
        form.add("content-type", "application/json");
        try {
            Reply reply = client.post()
                    .uri(properties.getUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((request, response) ->
                            new Reply(response.getStatusCode().value(), response.bodyTo(String.class)));
            JsonNode root = reply.body() == null ? null : objectMapper.readTree(reply.body());
            if (root == null || !root.isArray() || root.isEmpty()) {
                return Optional.empty();                    // not reported yet
            }
            String ack = root.get(0).path("acknowledgement").asString("").trim().toUpperCase(Locale.ROOT);
            if (ack.isEmpty()) {
                return Optional.empty();
            }
            // Keep a short, safe code (letters/underscore only) for the dashboard.
            String code = ack.replaceAll("[^A-Z_]", "");
            code = code.length() > 32 ? code.substring(0, 32) : code;
            return Optional.of(new DeliveryReport("DELIVRD".equals(ack), code));
        } catch (RuntimeException e) {
            log.warn("[TUNISIESMS] delivery report unavailable for id={}: {}",
                    providerMessageId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Result parse(Reply reply) {
        int code = reply.httpStatus();
        String messageId = null;
        try {
            JsonNode root = reply.body() == null ? null : objectMapper.readTree(reply.body());
            JsonNode first = root != null && root.isArray() && !root.isEmpty() ? root.get(0) : root;
            if (first != null && first.isObject()) {
                String status = first.path("status_code").asString("");
                if (status.matches("\\d{3}")) {
                    code = Integer.parseInt(status);
                }
                String id = first.path("message_id").asString("");
                messageId = id.isBlank() ? null : id;
            }
        } catch (RuntimeException e) {
            // Unparseable reply: keep the HTTP status. Never log the body, it
            // echoes the destination number.
            log.warn("[TUNISIESMS] unreadable reply (HTTP {})", reply.httpStatus());
        }
        return new Result(code, messageId);
    }

    private static void logFailure(int code, String ownerHash) {
        switch (code) {
            case 400, 401 -> log.error("[TUNISIESMS] API key rejected ({}): check TUNISIESMS_API_KEY", code);
            case 402 -> log.error("[TUNISIESMS] insufficient balance (402): top up the TunisieSMS account");
            case 420 -> log.error("[TUNISIESMS] daily quota exceeded (420)");
            case 442 -> log.error("[TUNISIESMS] sender not authorized (442): check TUNISIESMS_SENDER");
            case 441 -> log.warn("[TUNISIESMS] destination not authorized (441) (owner hash={})", ownerHash);
            default -> log.warn("[TUNISIESMS] SMS refused, code {} (owner hash={})", code, ownerHash);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Reply(int httpStatus, String body) {
    }

    private record Result(int code, String messageId) {
    }
}
