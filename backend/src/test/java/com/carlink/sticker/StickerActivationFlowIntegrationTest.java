package com.carlink.sticker;

import com.carlink.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Physical sticker lifecycle end-to-end: a guest buys a virgin sticker, the
 * buyer registers, the public view shows UNBOUND, a claim binds it to a car
 * (BOUND), a second claim conflicts, the owner deactivates it (DEACTIVATED),
 * it can be re-claimed, and a stranger can never claim or mutate the sticker.
 */
class StickerActivationFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;

    private static int seq;

    @Test
    void virginStickerIsClaimedDeactivatedAndReclaimed() throws Exception {
        String rawToken = orderSingleToken();

        // Public view: virgin sticker, no vehicle data at all.
        mockMvc.perform(get("/api/v1/public/qr/{token}", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UNBOUND"))
                .andExpect(jsonPath("$.channels").isEmpty());

        // The raw HTML page also works (neutral "not yet activated" page).
        mockMvc.perform(get("/c/{token}", rawToken))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("hasn't been activated")));

        // Owner registers and claims the sticker with car details.
        String[] owner = registerAndLogin("stkr-owner" + (++seq) + "@example.com");
        mockMvc.perform(post("/api/v1/stickers/{token}/activate", rawToken)
                        .header("Authorization", bearer(owner[0]))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "birthDate", "1990-05-04",
                                "licensePlate", "TN-123-AB",
                                "nickname", "My Car",
                                "brand", "Toyota",
                                "model", "Corolla",
                                "color", "Blue"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("activated")));

        // Public view now BOUND with the safe vehicle summary — still no plate.
        MvcResult bound = mockMvc.perform(get("/api/v1/public/qr/{token}", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("BOUND"))
                .andExpect(jsonPath("$.vehicle.nickname").value("My Car"))
                .andReturn();
        assertThat(bound.getResponse().getContentAsString()).doesNotContain("TN-123-AB");

        // A second claim of the same sticker conflicts (sticker → one owner).
        mockMvc.perform(post("/api/v1/stickers/{token}/activate", rawToken)
                        .header("Authorization", bearer(owner[0]))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("birthDate", "1985-01-01", "licensePlate", "TN-999-XX"))))
                .andExpect(status().isConflict());

        // Owner's sticker list shows it BOUND with their own plate.
        String stickerId = myStickerId(owner[0]);

        // Deactivate → DEACTIVATED, vehicle freed, contact submissions 404.
        mockMvc.perform(post("/api/v1/stickers/{id}/deactivate", stickerId)
                        .header("Authorization", bearer(owner[0])))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/qr/{token}", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DEACTIVATED"))
                .andExpect(jsonPath("$.channels").isEmpty());

        mockMvc.perform(post("/api/v1/public/qr/{token}/contact", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "SMS", "message", "hi", "reason", "BLOCKING"))))
                .andExpect(status().isNotFound());

        // Owner can re-claim the released sticker (plate already exists → reuse).
        mockMvc.perform(post("/api/v1/stickers/{token}/activate", rawToken)
                        .header("Authorization", bearer(owner[0]))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("birthDate", "1990-05-04", "licensePlate", "TN-123-AB"))))
                .andExpect(status().isOk());
    }

    @Test
    void strangerCannotClaimOrMutateAnAlreadyBoundSticker() throws Exception {
        String rawToken = orderSingleToken();
        String[] owner = registerAndLogin("stkr-owner" + (++seq) + "@example.com");
        claim(rawToken, owner[0]);
        String stickerId = myStickerId(owner[0]);

        // A completely different account:
        String[] stranger = registerAndLogin("stranger" + (++seq) + "@example.com");

        // Cannot claim the foreign BOUND sticker (409, without revealing who owns it)…
        mockMvc.perform(post("/api/v1/stickers/{token}/activate", rawToken)
                        .header("Authorization", bearer(stranger[0]))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("birthDate", "1999-01-01", "licensePlate", "XX-99-YY"))))
                .andExpect(status().isConflict());

        // …cannot deactivate someone else's sticker (404)…
        mockMvc.perform(post("/api/v1/stickers/{id}/deactivate", stickerId)
                        .header("Authorization", bearer(stranger[0])))
                .andExpect(status().isNotFound());

        // …and it never appears in the stranger's own list (404-free empty view).
        mockMvc.perform(get("/api/v1/stickers/me")
                        .header("Authorization", bearer(stranger[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---------- helpers ----------

    private void claim(String rawToken, String auth) throws Exception {
        mockMvc.perform(post("/api/v1/stickers/{token}/activate", rawToken)
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "birthDate", "1990-05-04",
                                "licensePlate", "TN-123-AB",
                                "nickname", "My Car"))))
                .andExpect(status().isOk());
    }

    private String myStickerId(String auth) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/stickers/me")
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode list = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(list).hasSize(1);
        return list.get(0).get("id").asText();
    }

    /** Buys a SINGLE sticker pack as an anonymous guest and returns the raw token. */
    private String orderSingleToken() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "items", List.of(Map.of("stickerPackage", "SINGLE", "quantity", 1)),
                                "customerName", "Guest Buyer",
                                "mobile", "+21655123456",
                                "email", "guest" + (++seq) + "@example.com",
                                "governorate", "TUNIS",
                                "deliveryAddress", "1 Place de la République"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .get("stickers").get(0).get("rawToken").asText();
    }

    private String[] registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "Secret123",
                                "firstName", "A",
                                "lastName", "B",
                                "phone", "+2165570" + seq))))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Secret123"))))
                .andExpect(status().isOk())
                .andReturn();
        return new String[]{objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText()};
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}