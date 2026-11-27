package com.carlink.notification.whatsapp;

import com.carlink.common.security.TokenGenerator;
import com.carlink.conversation.model.Channel;
import com.carlink.notification.contact.ContactDelivery;
import com.carlink.notification.contact.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class MetaWhatsAppSenderTest {

    private static final String URL = "https://graph.facebook.com/v25.0/1234567890/messages";
    private static final String OWNER = "+21612345678";

    private MockRestServiceServer server;
    private MetaWhatsAppSender sender;

    static WhatsAppProperties properties() {
        WhatsAppProperties p = new WhatsAppProperties();
        p.setProvider("meta");
        p.setPhoneNumberId("1234567890");
        p.setAccessToken("test-token");
        p.setAppSecret("test-secret");
        p.setVerifyToken("verify-me");
        return p;
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new MetaWhatsAppSender(builder.build(), properties(), new TokenGenerator(),
                JsonMapper.builder().build());
    }

    private static ContactDelivery alert(String note) {
        return new ContactDelivery(Channel.WHATSAPP, OWNER, note, "LIGHTS");
    }

    @Test
    void sendsTheApprovedTemplateWithTheReasonAndTheNote() {
        server.expect(requestTo(URL))                    // the number is in the body, not the URL
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
                .andExpect(jsonPath("$.to").value("21612345678"))
                .andExpect(jsonPath("$.type").value("template"))
                .andExpect(jsonPath("$.template.name").value("carlink_alert"))
                .andExpect(jsonPath("$.template.language.code").value("ar"))
                .andExpect(jsonPath("$.template.components[0].type").value("body"))
                .andExpect(jsonPath("$.template.components[0].parameters[0].text").value("💡 ضو كرهبتك مازال شاعل"))
                .andExpect(jsonPath("$.template.components[0].parameters[1].text").value("Garé au café, merci"))
                .andRespond(withSuccess("{\"messaging_product\":\"whatsapp\",\"contacts\":[{\"input\":\"21612345678\","
                        + "\"wa_id\":\"21612345678\"}],\"messages\":[{\"id\":\"wamid.ABC\"}]}", MediaType.APPLICATION_JSON));

        assertThat(sender.send(alert("Garé au café,\nmerci"))).isEqualTo(SendResult.accepted(WhatsAppMessageIds.fingerprint("wamid.ABC")));
        server.verify();
    }

    @Test
    void missingNoteIsADash() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.template.components[0].parameters[1].text").value("—"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.X\"}]}", MediaType.APPLICATION_JSON));

        assertThat(sender.send(alert("  "))).isEqualTo(SendResult.accepted(WhatsAppMessageIds.fingerprint("wamid.X")));
    }

    @Test
    void refusalIsFailedWithMetasErrorCode() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Recipient phone number not in allowed list\","
                                + "\"type\":\"OAuthException\",\"code\":131030,\"fbtrace_id\":\"x\"}}"));

        assertThat(sender.send(alert("hi"))).isEqualTo(SendResult.failed("WA_131030"));
    }

    @Test
    void errorWithoutABodyUsesTheHttpStatus() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThat(sender.send(alert("hi"))).isEqualTo(SendResult.failed("WA_502"));
    }

    @Test
    void messageIdIsNeverLoggedAndOnlyItsFingerprintIsKept(CapturedOutput output) {
        // A real wamid is Base64 that embeds the recipient's number (here 21612345678).
        String wamid = "wamid.HBgLMjE2MTIzNDU2NzgVAgARGBI3MzRENzNDNTUxOTg1OEJCRTIA";
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"" + wamid + "\"}]}", MediaType.APPLICATION_JSON));

        SendResult result = sender.send(alert("ok"));

        assertThat(result.providerMessageId())
                .isEqualTo(WhatsAppMessageIds.fingerprint(wamid))
                .hasSize(64).matches("[0-9a-f]+");
        assertThat(output.getAll()).doesNotContain(wamid).doesNotContain("HBgLMjE2MTIzNDU2Nzg");
    }

    @Test
    void acceptedWithoutAMessageIdIsFailed() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(sender.send(alert("hi"))).isEqualTo(SendResult.failed("NO_MESSAGE_ID"));
    }

    @Test
    void invalidNumberIsRefusedBeforeAnyCall() {
        assertThatThrownBy(() -> sender.send(new ContactDelivery(Channel.WHATSAPP, "12", "hi", "LIGHTS")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("12");
    }

    @Test
    void refusesToStartWithoutCredentials() {
        WhatsAppProperties p = properties();
        p.setAccessToken(" ");
        assertThatThrownBy(() -> new MetaWhatsAppSender(RestClient.create(), p, new TokenGenerator(),
                JsonMapper.builder().build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP_ACCESS_TOKEN");
    }

    @Test
    void isTheWhatsAppChannel() {
        assertThat(sender.channel()).isEqualTo(Channel.WHATSAPP);
        assertThat(sender.provider()).isEqualTo("meta");
    }
}
