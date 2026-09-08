package com.carlink.qr;

import com.carlink.AbstractIntegrationTest;
import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.Message;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.conversation.repository.MessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end public flow behind a QR scan: the page and the JSON view expose
 * only safe data, a contact submission persists a conversation + message, and
 * nothing (page, API, DB) ever carries the phone number or license plate.
 */
class PublicContactFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MessageRepository messageRepository;

    private static int seq;

    @Test
    void publicPageAndJsonShowOnlySafeData() throws Exception {
        String[] creds = ownerWithQr(); // {token, rawToken, vehicleId}
        String auth = creds[0];
        String rawToken = creds[1];

        // --- The HTML page ---
        MvcResult page = mockMvc.perform(get("/c/{token}", rawToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("WhatsApp")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SMS")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("My Car")))
                .andReturn();

        String html = page.getResponse().getContentAsString();
        // No license plate, no phone digits, no owner email on the page.
        assertThat(html).doesNotContain("AB-123-CD");
        assertThat(html).doesNotContain("+216");
        assertThat(html).doesNotContain("owner@example");

        // --- The JSON view ---
        mockMvc.perform(get("/api/v1/public/qr/{token}", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicle.nickname").value("My Car"))
                .andExpect(jsonPath("$.vehicle.brand").value("Toyota"))
                .andExpect(jsonPath("$.channels").isArray())
                .andExpect(jsonPath("$.channels[0]").value("WHATSAPP"))
                .andExpect(jsonPath("$.licensePlate").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void contactSubmissionPersistsConversationAndMessage() throws Exception {
        String[] creds = ownerWithQr();
        String rawToken = creds[1];

        MvcResult result = mockMvc.perform(post("/api/v1/public/qr/{token}/contact", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "WHATSAPP",
                                "message", "Hi, is this car still available?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("owner")))
                .andReturn();

        String conversationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("conversationId").asText();

        // Persisted: the conversation carries the channel and a created stamp;
        // the message body is stored but never echoed anywhere.
        Conversation conv = conversationRepository.findById(UUID.fromString(conversationId))
                .orElseThrow();
        assertThat(conv.getChannel().name()).isEqualTo("WHATSAPP");
        assertThat(conv.getExpiresAt()).isAfter(conv.getCreatedAt());

        assertThat(messageRepository.findAll()).hasSize(1);
        Message msg = messageRepository.findAll().get(0);
        assertThat(msg.getContent()).isEqualTo("Hi, is this car still available?");
        assertThat(msg.getConversation().getId().toString()).isEqualTo(conversationId);
    }

    @Test
    void invalidAndInactiveTokensReturn404() throws Exception {
        // Unknown token
        mockMvc.perform(get("/c/{token}", "totally-unknown-token"))
                .andExpect(status().isNotFound());

        // Inactive token: owner generates then deactivates.
        String[] creds = ownerWithQr();
        String auth = creds[0];
        String rawToken = creds[1];
        String vehicleId = creds[2];
        mockMvc.perform(post("/api/v1/vehicles/{id}/qr/deactivate", vehicleId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/qr/{token}", rawToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void contactSubmissionRejectsOversizedMessage() throws Exception {
        String[] creds = ownerWithQr();
        String rawToken = creds[1];

        mockMvc.perform(post("/api/v1/public/qr/{token}/contact", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "SMS",
                                "message", "x".repeat(501)))))
                .andExpect(status().isBadRequest());
        assertThat(messageRepository.findAll()).isEmpty();
    }

    @Test
    void contactSubmissionRejectsInvalidChannel() throws Exception {
        String[] creds = ownerWithQr();
        String rawToken = creds[1];

        mockMvc.perform(post("/api/v1/public/qr/{token}/contact", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "FAX", "message", "hi"))))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    /** Registers an owner, adds a vehicle, mints a QR. Returns {auth, rawToken, vehicleId}. */
    private String[] ownerWithQr() throws Exception {
        String email = "pc" + (++seq) + "@example.com";
        String auth = registerAndLogin(email);

        MvcResult vehicle = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nickname", "My Car",
                                "brand", "Toyota",
                                "model", "Corolla",
                                "color", "Blue",
                                "licensePlate", "AB-123-CD"))))
                .andExpect(status().isOk())
                .andReturn();
        String vehicleId = objectMapper.readTree(vehicle.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult qr = mockMvc.perform(post("/api/v1/vehicles/{id}/qr", vehicleId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken = objectMapper.readTree(qr.getResponse().getContentAsString())
                .get("rawToken").asText();

        return new String[]{auth, rawToken, vehicleId};
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "Secret123",
                                "firstName", "A",
                                "lastName", "B",
                                "phone", "+21655123456"))))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Secret123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}