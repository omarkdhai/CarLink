package com.carlink.notification.tunisiesms;

import com.carlink.common.security.TokenGenerator;
import com.carlink.conversation.model.Channel;
import com.carlink.notification.contact.ContactDelivery;
import com.carlink.notification.contact.DeliveryReportClient;
import com.carlink.notification.contact.SendResult;
import com.carlink.notification.sms.SmsText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TunisieSmsContactChannelSenderTest {

    private static final String URL = "https://api.l2t.io/tn/v0/api/api.aspx";
    private static final String OWNER = "+21612345678";

    private MockRestServiceServer server;
    private TunisieSmsContactChannelSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new TunisieSmsContactChannelSender(builder.build(), properties("test-key", "CarLink"),
                new TokenGenerator(), JsonMapper.builder().build());
    }

    private static TunisieSmsProperties properties(String key, String senderName) {
        TunisieSmsProperties p = new TunisieSmsProperties();
        p.setApiKey(key);
        p.setSender(senderName);
        p.setUrl(URL);
        return p;
    }

    private static ContactDelivery sms(String note) {
        return new ContactDelivery(Channel.SMS, OWNER, note, "LIGHTS");
    }

    @Test
    void postsAFormSoTheKeyAndNumberNeverAppearInTheUrl() {
        server.expect(requestTo(URL))                    // exact: no query string
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of(
                        "fct", "sms",
                        "key", "test-key",
                        "mobile", "21612345678",
                        "sender", "CarLink",
                        "sms", "💡 ضو كرهبتك مازال شاعل\n💬 Garé au café",
                        "content-type", "application/json")))
                .andRespond(withSuccess("[{\"message_id\":\"12345\",\"status_mobile\":\"21612345678\","
                        + "\"status_msg\":\"OK\",\"status_code\":\"200\"}]", MediaType.APPLICATION_JSON));

        assertThat(sender.send(sms("Garé au café"))).isEqualTo(SendResult.accepted("12345"));
        server.verify();
    }

    @Test
    void providerErrorAsHttpStatusIsAFailureNotAnException() {
        // Verified against the live API: the HTTP status is the API code.
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.valueOf(402)).contentType(MediaType.APPLICATION_JSON)
                        .body("[{\"status_code\":402,\"status_msg\":\"Solde insuffisant\"}]"));

        assertThat(sender.send(sms(null)).outcome()).isEqualTo(SendResult.Outcome.FAILED);
    }

    @Test
    void errorCodeInsideAnHttp200ReplyIsAFailure() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("[{\"status_code\":\"442\",\"status_msg\":\"Expediteur non autorise\"}]",
                        MediaType.APPLICATION_JSON));

        assertThat(sender.send(sms(null)).outcome()).isEqualTo(SendResult.Outcome.FAILED);
    }

    @Test
    void acceptedWithoutMessageIdIsUnconfirmed() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("[{\"status_code\":\"200\"}]", MediaType.APPLICATION_JSON));

        assertThat(sender.send(sms(null)).outcome()).isEqualTo(SendResult.Outcome.FAILED);
    }

    @Test
    void unreadableReplyIsAFailure() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("<response>not json</response>", MediaType.APPLICATION_XML));

        assertThat(sender.send(sms(null)).outcome()).isEqualTo(SendResult.Outcome.FAILED);
    }

    @Test
    void onlySms() {
        assertThatThrownBy(() -> sender.send(new ContactDelivery(Channel.WHATSAPP, OWNER, null, "LIGHTS")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidNumberIsRefusedWithoutEchoingIt() {
        assertThatThrownBy(() -> sender.send(new ContactDelivery(Channel.SMS, "+216-12", null, "LIGHTS")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("216");
        server.verify();   // nothing was sent
    }

    @Test
    void missingKeyOrSenderStopsStartup() {
        assertThatThrownBy(() -> new TunisieSmsContactChannelSender(RestClient.create(), properties(" ", "CarLink"),
                new TokenGenerator(), JsonMapper.builder().build()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TUNISIESMS_API_KEY");
        assertThatThrownBy(() -> new TunisieSmsContactChannelSender(RestClient.create(), properties("k", ""),
                new TokenGenerator(), JsonMapper.builder().build()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TUNISIESMS_SENDER");
    }

    @Test
    void bodyIsTheDerjaReasonThenTheNoteAsTyped() {
        assertThat(TunisieSmsContactChannelSender.buildBody(sms("  "))).isEqualTo("💡 ضو كرهبتك مازال شاعل");
        assertThat(TunisieSmsContactChannelSender.buildBody(sms("ok 👍"))).isEqualTo("💡 ضو كرهبتك مازال شاعل\n💬 ok 👍");
    }

    @Test
    void longNoteIsCutToOneSms() {
        String body = TunisieSmsContactChannelSender.buildBody(sms("x".repeat(500)));
        assertThat(body).hasSize(SmsText.UNICODE_LIMIT).endsWith("...");
    }

    @Test
    void refusalCarriesTheApiCode() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.valueOf(402)).contentType(MediaType.APPLICATION_JSON)
                        .body("[{\"status_code\":402,\"status_msg\":\"Solde insuffisant\"}]"));

        assertThat(sender.send(sms(null))).isEqualTo(SendResult.failed("API_402"));
    }

    // --- delivery reports (fct=dlr), formats verified against the live API ---

    private void dlrReplies(String body) {
        server.expect(requestTo(URL))
                .andExpect(content().formDataContains(Map.of("fct", "dlr", "msg_id", "261124", "key", "test-key")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void noReportYetIsEmpty() {
        dlrReplies("[]");
        assertThat(sender.report("261124")).isEmpty();
    }

    @Test
    void delivrdIsDelivered() {
        dlrReplies("[{\"mobile\":\"21612345678\",\"message_id\":\"261124\",\"acknowledgement\":\"DELIVRD\","
                + "\"acknowledgement_date\":\"2026-11-24T16:41:52.817691\"}]");
        assertThat(sender.report("261124")).contains(new DeliveryReportClient.DeliveryReport(true, "DELIVRD"));
    }

    @Test
    void rejectedOrAnyOtherStatusIsNotDelivered() {
        dlrReplies("[{\"mobile\":\"21612345678\",\"message_id\":\"261124\",\"acknowledgement\":\"REJECTED\"}]");
        assertThat(sender.report("261124")).contains(new DeliveryReportClient.DeliveryReport(false, "REJECTED"));
    }

    @Test
    void unreachableReportServiceIsTreatedAsNotYetKnown() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new java.io.IOException("timeout");
        });
        assertThat(sender.report("261124")).isEmpty();
    }
}
