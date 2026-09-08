package com.carlink.conversation;

import com.carlink.AbstractIntegrationTest;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.conversation.service.ConversationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end owner dashboard: list conversations, read message history,
 * mark-read, cross-owner isolation, vehicle-scoped filtering, and the
 * expired-conversation sweeper.
 */
class ConversationFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ConversationService conversationService;

    private static int seq;

    // ---------- dashboard listing ----------

    @Test
    void ownerListsConversationsFromTwoContacts() throws Exception {
        String[] creds = ownerWithQr();
        String auth = creds[0], rawToken = creds[1];

        // Submit two contacts on different channels
        mockMvc.perform(post("/api/v1/public/qr/{token}/contact", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("channel", "WHATSAPP", "message", "Hello!"))));
        mockMvc.perform(post("/api/v1/public/qr/{token}/contact", rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("channel", "SMS", "message", "Is this car still for sale?"))));

        mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                // Newest first: SMS submission came second
                .andExpect(jsonPath("$[0].channel").value("SMS"))
                .andExpect(jsonPath("$[0].unread").value(true))
                .andExpect(jsonPath("$[0].vehicleNickname").value("My Car"))
                .andExpect(jsonPath("$[0].lastMessagePreview").value("Is this car still for sale?"))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[1].channel").value("WHATSAPP"))
                .andExpect(jsonPath("$[1].unread").value(true))
                .andExpect(jsonPath("$[1].lastMessagePreview").value("Hello!"));
    }

    // ---------- full message history ----------

    @Test
    void ownerReadsConversationDetailWithMessageHistory() throws Exception {
        String[] creds = ownerWithQr();
        String auth = creds[0], rawToken = creds[1];
        String convId = submitContact(auth, rawToken, "WHATSAPP", "First message");

        mockMvc.perform(get("/api/v1/conversations/{id}", convId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(convId))
                .andExpect(jsonPath("$.channel").value("WHATSAPP"))
                .andExpect(jsonPath("$.unread").value(true))
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].content").value("First message"))
                // Ensure sensitive vehicle data never leaks
                .andExpect(jsonPath("$.licensePlate").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    // ---------- mark-as-read ----------

    @Test
    void ownerMarksConversationRead() throws Exception {
        String[] creds = ownerWithQr();
        String auth = creds[0], rawToken = creds[1];
        String convId = submitContact(auth, rawToken, "WHATSAPP", "Hi there");

        // Initially unread
        mockMvc.perform(get("/api/v1/conversations/{id}", convId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(true));

        // Mark read
        mockMvc.perform(post("/api/v1/conversations/{id}/read", convId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk());

        // Now reads as false
        mockMvc.perform(get("/api/v1/conversations/{id}", convId)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(false));

        // DB confirms read_at is populated
        var conv = conversationRepository.findById(UUID.fromString(convId)).orElseThrow();
        assertThat(conv.getReadAt()).isNotNull();
    }

    // ---------- cross-owner isolation ----------

    @Test
    void crossOwnerAccessReturns404() throws Exception {
        String[] creds = ownerWithQr();
        String auth = creds[0], rawToken = creds[1];
        String convId = submitContact(auth, rawToken, "WHATSAPP", "Test");

        String otherAuth = registerAndLogin(uniqueEmail());

        // Other owner sees an empty dashboard
        mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", bearer(otherAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // Other owner cannot read a specific conversation
        mockMvc.perform(get("/api/v1/conversations/{id}", convId)
                        .header("Authorization", bearer(otherAuth)))
                .andExpect(status().isNotFound());
    }

    // ---------- authentication ----------

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/conversations/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- vehicle-scoped listing ----------

    @Test
    void vehicleScopedListReturnsOnlyThatVehiclesConversations() throws Exception {
        String[] creds1 = ownerWithQr();
        String[] creds2 = ownerWithQr();
        String auth1 = creds1[0], rawToken1 = creds1[1], vehicleId1 = creds1[2];
        String auth2 = creds2[0], rawToken2 = creds2[1], vehicleId2 = creds2[2];

        submitContact(auth1, rawToken1, "WHATSAPP", "For vehicle 1");
        submitContact(auth2, rawToken2, "SMS", "For vehicle 2");

        // Scoped to vehicle 1 — shows only vehicle 1's conversation
        mockMvc.perform(get("/api/v1/conversations?vehicleId={vid}", vehicleId1)
                        .header("Authorization", bearer(auth1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].vehicleId").value(vehicleId1));

        // Scoped to vehicle 2 — shows only vehicle 2's conversation
        mockMvc.perform(get("/api/v1/conversations?vehicleId={vid}", vehicleId2)
                        .header("Authorization", bearer(auth2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].vehicleId").value(vehicleId2));
    }

    // ---------- expiry sweeper ----------

    @Test
    void sweeperExpiresStaleConversations() throws Exception {
        String[] creds = ownerWithQr();
        String auth = creds[0], rawToken = creds[1];
        String convId = submitContact(auth, rawToken, "WHATSAPP", "Will expire");

        var conv = conversationRepository.findById(UUID.fromString(convId)).orElseThrow();
        assertThat(conv.getStatus().name()).isEqualTo("SENT");

        // Simulate expiry: push expiresAt into the past
        conv.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        conversationRepository.save(conv);

        // Run the sweeper directly (scheduler starts too late in tests)
        conversationService.sweepExpiredConversations();

        var updated = conversationRepository.findById(UUID.fromString(convId)).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ConversationStatus.EXPIRED);
    }

    // ---------- message content never leaks through public surfaces ----------

    @Test
    void publicJsonViewDoesNotExposeMessageContent() throws Exception {
        String[] creds = ownerWithQr();
        String rawToken = creds[1];
        submitContact(creds[0], rawToken, "WHATSAPP", "Private message text");

        mockMvc.perform(get("/api/v1/public/qr/{token}", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").doesNotExist())
                .andExpect(jsonPath("$.messageContent").doesNotExist());
    }

    // ---------- helpers ----------

    private String[] ownerWithQr() throws Exception {
        String auth = registerAndLogin(uniqueEmail());

        MvcResult vehicle = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nickname", "My Car",
                                "brand", "Toyota",
                                "model", "Corolla",
                                "color", "Blue",
                                "licensePlate", "AB-" + (++seq) + "-CD"))))
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

    /** Submits a contact and returns the new conversationId. */
    private String submitContact(String auth, String rawToken, String channel, String message)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/public/qr/{token}/contact", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", channel, "message", message))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("conversationId").asText();
    }

    private String uniqueEmail() {
        return "conv" + (++seq) + "@example.com";
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "Secret123",
                                "firstName", "A",
                                "lastName", "B",
                                "phone", "+21655100" + seq))))
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
