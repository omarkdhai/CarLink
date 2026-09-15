package com.carlink.order;

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
                .andExpect(jsonPath("$.stickers.length()").value(4))
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        String reference = node.get("reference").asText();
        assertThat(reference).startsWith("CL-").hasSize(11);

        // Server-side total: DOUBLE (35.00) × 2 = 70.00 TND.
        assertThat(new java.math.BigDecimal(node.get("totalAmount").asText()))
                .isEqualByComparingTo("70.00");

        // Exactly Σ(quantity × stickers_per_pack) = 4 raw tokens, each with a QR.
        assertThat(node.get("stickers")).hasSize(4);
        for (JsonNode s : node.get("stickers")) {
            String rawToken = s.get("rawToken").asText();
            assertThat(s.get("publicUrl").asText()).isEqualTo("http://localhost:8080/c/" + rawToken);
            assertThat(s.get("imageDataUri").asText()).startsWith("data:image/png;base64,");
        }
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
                .get("reference").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}