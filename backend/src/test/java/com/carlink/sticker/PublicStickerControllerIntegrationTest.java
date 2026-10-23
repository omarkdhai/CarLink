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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public sticker resolution: a visitor can check whether a short activation
 * code (or raw QR token) is still claimable before signing up. Unknown codes
 * read as 404 so enumeration reveals nothing.
 */
class PublicStickerControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;

    private static int seq;

    @Test
    void resolveReportsClaimableThenBoundAndRejectsUnknown() throws Exception {
        String[] codes = orderSingleCode();
        String rawToken = codes[0];
        String activationCode = codes[1];

        // Unknown code → 404.
        mockMvc.perform(post("/api/v1/public/stickers/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "ZZ-9999-9999"))))
                .andExpect(status().isNotFound());

        // Virgin sticker → CLAIMABLE (by code and by raw token).
        mockMvc.perform(post("/api/v1/public/stickers/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", activationCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLAIMABLE"));
        mockMvc.perform(post("/api/v1/public/stickers/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", rawToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLAIMABLE"));

        // Owner registers (with the code) and claims via activate-by-code.
        String auth = registerAndLogin(activationCode);
        mockMvc.perform(post("/api/v1/stickers/activate-by-code")
                        .header("Authorization", "Bearer " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", activationCode,
                                "details", Map.of(
                                        "birthDate", "1990-05-04",
                                        "licensePlate", "TN-123-AB",
                                        "nickname", "My Car")))))
                .andExpect(status().isOk());

        // Now BOUND.
        mockMvc.perform(post("/api/v1/public/stickers/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", activationCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("BOUND"));
    }

    // ---------- helpers ----------

    /** Orders a SINGLE sticker as a guest; returns [rawToken, activationCode]. */
    private String[] orderSingleCode() throws Exception {
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
        JsonNode root = objectMapper.readTree(r.getResponse().getContentAsString());
        return new String[]{
                root.get("stickers").get(0).get("rawToken").asText(),
                root.get("stickers").get(0).get("activationCode").asText(),
        };
    }

    private String registerAndLogin(String stickerCode) throws Exception {
        String email = "owner" + (++seq) + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "Secret123",
                                "firstName", "A",
                                "lastName", "B",
                                "phone", "+2165570" + seq,
                                "stickerCode", stickerCode))))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Secret123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
