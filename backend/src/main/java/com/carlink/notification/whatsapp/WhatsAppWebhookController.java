package com.carlink.notification.whatsapp;

import com.carlink.conversation.model.DeliveryStatus;
import com.carlink.notification.contact.DeliveryStatusService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Meta's WhatsApp webhook: delivery statuses of the alerts we sent.
 *
 * <ul>
 *   <li>{@code GET}: the one-time subscription check. Meta sends our verify
 *       token and a challenge to echo.</li>
 *   <li>{@code POST}: {@code entry[].changes[].value.statuses[]}, each
 *       {@code {id, status: sent|delivered|read|failed, errors[]}}. Only calls
 *       signed with the app secret ({@link WebhookSignature}) are read.
 *       {@code delivered}/{@code read} mark the WhatsApp delivery, {@code failed}
 *       records {@code WA_<code>}, and the alert's aggregate status follows.</li>
 * </ul>
 *
 * <p>Incoming messages from owners ({@code value.messages[]}) are ignored, as
 * are the {@code recipient_id} numbers in the statuses: nothing from the body is
 * logged except counts and error codes. Meta retries any non-2xx reply, so a
 * correctly signed call is always answered 200.</p>
 */
@Slf4j
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping(WhatsAppWebhookController.PATH)
@ConditionalOnProperty(name = "carlink.whatsapp.provider", havingValue = "meta")
public class WhatsAppWebhookController {

    static final String PATH = "/api/v1/public/webhooks/whatsapp";

    private final WhatsAppProperties properties;
    private final DeliveryStatusService deliveryStatusService;
    private final ObjectMapper objectMapper;

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String token,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        boolean ok = "subscribe".equals(mode) && token != null
                && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                        properties.getVerifyToken().trim().getBytes(StandardCharsets.UTF_8))
                && challenge != null && challenge.matches("[A-Za-z0-9_-]{1,100}");
        return ok ? ResponseEntity.ok(challenge) : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> receive(@RequestBody(required = false) byte[] body,
                                        @RequestHeader(name = "X-Hub-Signature-256", required = false)
                                        String signature) {
        if (!WebhookSignature.valid(body, signature, properties.getAppSecret())) {
            log.warn("[WHATSAPP] webhook call with a bad or missing signature ignored");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (RuntimeException e) {
            log.warn("[WHATSAPP] unreadable webhook body ignored");
            return ResponseEntity.ok().build();
        }
        int applied = 0;
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode status : change.path("value").path("statuses")) {
                    applied += apply(status) ? 1 : 0;
                }
            }
        }
        if (applied > 0) {
            log.debug("[WHATSAPP] {} status update(s) applied", applied);
        }
        return ResponseEntity.ok().build();
    }

    private boolean apply(JsonNode status) {
        String id = status.path("id").asString("");
        String value = status.path("status").asString("").toLowerCase(Locale.ROOT);
        if (id.isBlank()) {
            return false;
        }
        DeliveryStatus outcome;
        String error = null;
        switch (value) {
            case "delivered" -> outcome = DeliveryStatus.DELIVERED;
            case "read" -> outcome = DeliveryStatus.READ;
            case "failed" -> {
                outcome = DeliveryStatus.FAILED;
                JsonNode code = status.path("errors").path(0).path("code");
                error = code.isMissingNode() || code.isNull() ? "WA_FAILED" : "WA_" + code.asInt();
            }
            default -> {
                return false;                         // "sent" adds nothing to accepted
            }
        }
        try {
            // Deliveries keep only the wamid's fingerprint (it embeds the phone number).
            return deliveryStatusService.applyPushedReport(
                    "meta", WhatsAppMessageIds.fingerprint(id), outcome, error);
        } catch (RuntimeException e) {
            // Answer 200 anyway: a retry would hit the same error.
            log.warn("[WHATSAPP] status update failed: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}
