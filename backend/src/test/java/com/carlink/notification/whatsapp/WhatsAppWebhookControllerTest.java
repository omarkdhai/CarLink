package com.carlink.notification.whatsapp;

import com.carlink.conversation.model.DeliveryStatus;
import com.carlink.notification.contact.DeliveryStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WhatsAppWebhookControllerTest {

    private final DeliveryStatusService statuses = mock(DeliveryStatusService.class);
    private final WhatsAppWebhookController controller = new WhatsAppWebhookController(
            MetaWhatsAppSenderTest.properties(), statuses, JsonMapper.builder().build());

    private static String statusCall(String id, String status, String errors) {
        return "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"WABA\",\"changes\":[{\"field\":\"messages\","
                + "\"value\":{\"messaging_product\":\"whatsapp\",\"statuses\":[{\"id\":\"" + id + "\",\"status\":\""
                + status + "\",\"timestamp\":\"1700000000\",\"recipient_id\":\"21612345678\"" + errors + "}]}}]}]}";
    }

    private HttpStatus post(String json, boolean signed) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String signature = signed ? WebhookSignature.sign(body, "test-secret") : "sha256=00";
        return HttpStatus.valueOf(controller.receive(body, signature).getStatusCode().value());
    }

    @Test
    void deliveredReadAndFailedStatusesAreApplied() {
        assertThat(post(statusCall("wamid.1", "delivered", ""), true)).isEqualTo(HttpStatus.OK);
        assertThat(post(statusCall("wamid.1", "read", ""), true)).isEqualTo(HttpStatus.OK);
        assertThat(post(statusCall("wamid.2", "failed", ",\"errors\":[{\"code\":131026,\"title\":\"x\"}]"), true))
                .isEqualTo(HttpStatus.OK);

        verify(statuses).applyPushedReport("meta", "wamid.1", DeliveryStatus.DELIVERED, null);
        verify(statuses).applyPushedReport("meta", "wamid.1", DeliveryStatus.READ, null);
        verify(statuses).applyPushedReport("meta", "wamid.2", DeliveryStatus.FAILED, "WA_131026");
    }

    @Test
    void sentStatusAndIncomingMessagesChangeNothing() {
        post(statusCall("wamid.3", "sent", ""), true);
        post("{\"entry\":[{\"changes\":[{\"value\":{\"messages\":[{\"from\":\"21612345678\",\"text\":{\"body\":\"ok\"}}]}}]}]}", true);

        verify(statuses, never()).applyPushedReport(anyString(), anyString(), any(), any());
    }

    @Test
    void unsignedOrWronglySignedCallsAreRejected() {
        assertThat(post(statusCall("wamid.4", "delivered", ""), false)).isEqualTo(HttpStatus.UNAUTHORIZED);
        byte[] body = statusCall("wamid.4", "delivered", "").getBytes(StandardCharsets.UTF_8);
        assertThat(controller.receive(body, null).getStatusCode().value()).isEqualTo(401);
        assertThat(controller.receive(body, WebhookSignature.sign(body, "other-secret")).getStatusCode().value())
                .isEqualTo(401);

        verify(statuses, never()).applyPushedReport(anyString(), anyString(), any(), any());
    }

    @Test
    void unreadableSignedBodyIsAnsweredOkSoMetaDoesNotRetry() {
        assertThat(post("not json", true)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void subscriptionCheckEchoesTheChallengeOnlyWithOurVerifyToken() {
        assertThat(controller.verify("subscribe", "verify-me", "1158201444").getBody()).isEqualTo("1158201444");
        assertThat(controller.verify("subscribe", "wrong", "1158201444").getStatusCode().value()).isEqualTo(403);
        assertThat(controller.verify("subscribe", "verify-me", "<script>").getStatusCode().value()).isEqualTo(403);
        assertThat(controller.verify(null, "verify-me", "1").getStatusCode().value()).isEqualTo(403);
    }
}
