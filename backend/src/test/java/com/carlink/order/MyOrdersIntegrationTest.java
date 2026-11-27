package com.carlink.order;

import com.carlink.AbstractIntegrationTest;
import com.carlink.user.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An owner's purchase history ({@code GET /api/v1/orders/me}): orders whose
 * stickers they activated, plus orders placed with their e-mail once it is
 * verified. Never another account's orders, never buyer PII or tokens.
 */
class MyOrdersIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private static int seq;

    @Test
    void historyShowsOwnOrdersOnlyAndNoPii() throws Exception {
        String email = "history" + (++seq) + "@example.com";

        // Sticker bought under another e-mail, activated at sign-up → it's theirs.
        JsonNode stickerOrder = placeOrder("gift" + seq + "@example.com", "DOUBLE");
        String token = register(email, issuedStickers(stickerOrder).get(0).get("activationCode").asString());

        // An order placed with the account's e-mail, still unverified → not shown.
        JsonNode emailOrder = placeOrder(email.toUpperCase(), "SINGLE");
        JsonNode list = mine(token);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("reference").asString()).isEqualTo(stickerOrder.get("reference").asString());
        assertThat(list.get(0).get("stickerCount").asInt()).isEqualTo(2);
        assertThat(list.get(0).get("activatedCount").asInt()).isEqualTo(1);
        assertThat(list.get(0).get("items").get(0).get("stickerPackage").asString()).isEqualTo("DOUBLE");

        // Once the e-mail is verified, that order joins, newest first.
        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        userRepository.save(user);
        list = mine(token);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("reference").asString()).isEqualTo(emailOrder.get("reference").asString());
        assertThat(list.get(0).get("activatedCount").asInt()).isZero();

        // No buyer details, tokens or activation codes in the payload.
        String raw = mineRaw(token);
        assertThat(raw).doesNotContain("Guest Buyer", "+21655123456", "Rue", "rawToken", "activationCode", "customerName");

        // A stranger sees none of it.
        JsonNode otherOrder = placeOrder("other" + (++seq) + "@example.com", "SINGLE");
        String stranger = register("stranger" + seq + "@example.com",
                issuedStickers(otherOrder).get(0).get("activationCode").asString());
        JsonNode theirs = mine(stranger);
        assertThat(theirs).hasSize(1);
        assertThat(theirs.get(0).get("reference").asString()).isEqualTo(otherOrder.get("reference").asString());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/orders/me")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private JsonNode mine(String token) throws Exception {
        return objectMapper.readTree(mineRaw(token));
    }

    private String mineRaw(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/orders/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode placeOrder(String email, String pack) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "items", List.of(Map.of("stickerPackage", pack, "quantity", 1)),
                                "customerName", "Guest Buyer",
                                "mobile", "+21655123456",
                                "email", email,
                                "governorate", "TUNIS",
                                "deliveryAddress", "1 Rue de la Liberté"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private String register(String email, String stickerCode) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Secret1234",
                                "firstName", "A",
                                "lastName", "B",
                                "phone", "+216558" + (10000 + seq),
                                "stickerCode", stickerCode))))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Secret1234"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
        // Sign-up only checks the code; activation binds the sticker to the account.
        mockMvc.perform(post("/api/v1/stickers/activate-by-code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", stickerCode,
                                "details", Map.of("birthDate", "1990-05-04", "licensePlate", "TN-" + (500 + seq) + "-HX")))))
                .andExpect(status().isOk());
        return token;
    }
}
