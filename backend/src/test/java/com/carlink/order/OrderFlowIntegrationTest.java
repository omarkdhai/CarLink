package com.carlink.order;

import com.carlink.AbstractIntegrationTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end guest COD checkout. An anonymous buyer picks packages, receives
 * an order reference plus exactly-Σ stickers (raw tokens shown exactly once),
 * and can later fetch a token-free, PII-free summary by reference. No
 * authorization header anywhere.
 */
class OrderFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;

    private static int seq;

    @Test
    void guestPlacesOrderAndReceivesReferencePlusExactStickerCount() throws Exception {
        Map<String, Object> body = Map.of(
                "items", List.of(Map.of("stickerPackage", "DOUBLE", "quantity", 2)),
                "customerName", "Aymen Ben Ali",
                "mobile", "+21655123456",
                "email", "buyer" + (++seq) + "@example.com",
                "governorate", "TUNIS",
                "deliveryAddress", "12 Rue de Carthage, Tunis",
                "deliveryNotes", "Ring the bell");

        MvcResult result = mockMvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").isString())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.currency").value("TND"))
                .andExpect(jsonPath("$.items[0].stickerPackage").value("DOUBLE"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.stickers").doesNotExist())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        String reference = node.get("reference").asString();
        assertThat(reference).startsWith("CL-").hasSize(11);

        // Server-side total: DOUBLE (35.00) × 2 + 7.00 delivery = 77.00 TND.
        assertThat(new java.math.BigDecimal(node.get("totalAmount").asString()))
                .isEqualByComparingTo("77.00");

        // The buyer receives no QR token or activation code.
        String raw = result.getResponse().getContentAsString();
        assertThat(raw).doesNotContain("rawToken", "activationCode", "imageDataUri", "/c/");

        // Exactly Σ(quantity × stickers_per_pack) = 4 stickers were minted.
        assertThat(issuedStickers(node)).hasSize(4);
    }

    @Test
    void adminSeesEachStickersQrAndActivationCodeAndTheReadIsAudited() throws Exception {
        JsonNode order = objectMapper.readTree(mockMvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "items", List.of(Map.of("stickerPackage", "DOUBLE", "quantity", 1)),
                                "customerName", "Aymen Ben Ali",
                                "mobile", "+21655123456",
                                "email", "buyer" + (++seq) + "@example.com",
                                "governorate", "TUNIS",
                                "deliveryAddress", "12 Rue de Carthage, Tunis",
                                "stickerStyle", "LIGHT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stickerStyle").value("LIGHT"))
                .andReturn().getResponse().getContentAsString());
        String reference = order.get("reference").asString();
        JsonNode stored = issuedStickers(order);

        String admin = adminToken();
        // The admin order list carries the chosen design.
        mockMvc.perform(get("/api/v1/admin/orders").param("q", reference)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference").value(reference))
                .andExpect(jsonPath("$.content[0].stickerStyle").value("LIGHT"));
        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/orders/{ref}/stickers", reference)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(list).hasSize(2);
        for (int i = 0; i < 2; i++) {
            JsonNode s = list.get(i);
            assertThat(s.get("codesAvailable").asBoolean()).isTrue();
            assertThat(s.get("status").asString()).isEqualTo("UNBOUND");
            assertThat(s.get("publicUrl").asString()).isEqualTo(stored.get(i).get("publicUrl").asString());
            assertThat(s.get("activationCode").asString()).isEqualTo(stored.get(i).get("activationCode").asString());
            assertThat(s.get("imageDataUri").asString()).startsWith("data:image/png;base64,");
        }

        // The admin's read is in the audit log (with no code in it).
        Integer audited = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'ORDER_STICKER_CODES_VIEW'
                   AND details::text LIKE ?""", Integer.class, "%" + reference + "%");
        assertThat(audited).isEqualTo(1);
        String details = jdbc.queryForObject("""
                SELECT details FROM audit_logs WHERE action = 'ORDER_STICKER_CODES_VIEW'
                   AND details::text LIKE ?""", String.class, "%" + reference + "%");
        assertThat(details).doesNotContain(stored.get(0).get("activationCode").asString());

        // Not for owners, not for anonymous callers.
        mockMvc.perform(get("/api/v1/admin/orders/{ref}/stickers", reference))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/orders/{ref}/stickers", "CL-NOPE0000")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private com.carlink.user.repository.UserRepository userRepository;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private String adminToken() throws Exception {
        String email = "order-admin" + (++seq) + "@carlink.test";
        userRepository.save(com.carlink.user.model.User.builder()
                .id(java.util.UUID.randomUUID())
                .email(email)
                .passwordHash(passwordEncoder.encode("Admin123"))
                .firstName("Root").lastName("Admin")
                .role(com.carlink.user.model.Role.ADMIN)
                .active(true).emailVerified(true)
                .build());
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Admin123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asString();
    }

    @Test
    void summaryByReferenceIsTokenFreeAndPiiFree() throws Exception {
        String reference = placeOrder();

        mockMvc.perform(get("/api/v1/public/orders/{reference}", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference))
                .andExpect(jsonPath("$.stickerCount").value(1))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.rawToken").doesNotExist())
                .andExpect(jsonPath("$.rawTokens").doesNotExist())
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.mobile").doesNotExist())
                .andExpect(jsonPath("$.deliveryAddress").doesNotExist());
    }

    @Test
    void emptyOrUnknownPackageIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "items", List.of(),
                                "customerName", "A",
                                "mobile", "+21655123456",
                                "email", "buyer@example.com",
                                "governorate", "TUNIS",
                                "deliveryAddress", "addr"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "items", List.of(Map.of("stickerPackage", "PLATINUM", "quantity", 1)),
                                "customerName", "A",
                                "mobile", "+21655123456",
                                "email", "buyer@example.com",
                                "governorate", "TUNIS",
                                "deliveryAddress", "addr"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrderReferenceReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/public/orders/CL-NOPE123"))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private String placeOrder() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "items", List.of(Map.of("stickerPackage", "SINGLE", "quantity", 1)),
                                "customerName", "Aymen",
                                "mobile", "+21655123456",
                                "email", "order" + (++seq) + "@example.com",
                                "governorate", "BEN_AROUS",
                                "deliveryAddress", "Avenue Habib Bourguiba"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString())
                .get("reference").asString();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}