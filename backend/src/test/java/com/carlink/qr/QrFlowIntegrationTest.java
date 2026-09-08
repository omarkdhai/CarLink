package com.carlink.qr;

import com.carlink.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end QR lifecycle: generate (raw token returned once, QR decodes to
 * only the public URL), status lookup never reveals the token, regenerate
 * deactivates the previous QR, deactivate turns the channel off.
 */
class QrFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static int seq;

    @Test
    void qrLifecycleWorks() throws Exception {
        String token = registerAndLogin(uniqueEmail());

        // --- Add a vehicle to attach the QR to ---
        MvcResult vehicleResult = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nickname", "My Car",
                                "licensePlate", "AB-123-CD"))))
                .andExpect(status().isOk())
                .andReturn();
        String vehicleId = objectMapper.readTree(vehicleResult.getResponse().getContentAsString())
                .get("id").asText();

        // --- Generate a QR ---
        MvcResult issued = mockMvc.perform(post("/api/v1/vehicles/{id}/qr", vehicleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawToken").isNotEmpty())
                .andExpect(jsonPath("$.publicUrl").isNotEmpty())
                .andExpect(jsonPath("$.imageDataUri").isNotEmpty())
                .andReturn();

        JsonNode issue = objectMapper.readTree(issued.getResponse().getContentAsString());
        String rawToken = issue.get("rawToken").asText();

        // The public URL encodes only the public route with the raw token.
        String publicUrl = issue.get("publicUrl").asText();
        assertThat(publicUrl).endsWith("/c/" + rawToken);

        // --- The QR image decodes back to exactly that URL (ZXing round-trip) ---
        String decoded = decodePng(issue.get("imageDataUri").asText());
        assertThat(decoded).isEqualTo(publicUrl);
        assertThat(decoded).doesNotContain("owner", "phone", "vehicle");

        // --- Status lookup shows the QR but never the token ---
        mockMvc.perform(get("/api/v1/vehicles/{id}/qr", vehicleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.rawToken").doesNotExist());

        // --- Regenerating deactivates the previous QR ---
        MvcResult secondIssued = mockMvc.perform(post("/api/v1/vehicles/{id}/qr", vehicleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawToken").isNotEmpty())
                .andReturn();
        String secondToken = objectMapper.readTree(secondIssued.getResponse().getContentAsString())
                .get("rawToken").asText();
        assertThat(secondToken).isNotEqualTo(rawToken); // fresh token, old one burned

        // History contains both codes, newest first
        mockMvc.perform(get("/api/v1/vehicles/{id}/qr/history", vehicleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // --- Deactivate turns the channel off ---
        mockMvc.perform(post("/api/v1/vehicles/{id}/qr/deactivate", vehicleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/vehicles/{id}/qr", vehicleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void qrIsScopedToOwnedVehicles() throws Exception {
        String ownerToken = registerAndLogin(uniqueEmail());
        String otherToken = registerAndLogin(uniqueEmail());

        String vehicleId = createVehicle(ownerToken, "AB-000-XX");

        // Only the owner can generate/read/deactivate.
        mockMvc.perform(post("/api/v1/vehicles/{id}/qr", vehicleId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/vehicles/{id}/qr", vehicleId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/vehicles/{id}/qr/deactivate", vehicleId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());

        // The owner still can.
        mockMvc.perform(post("/api/v1/vehicles/{id}/qr", vehicleId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedQrRequestGets401() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/{id}/qr", java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    /** Decodes a base64 PNG data URI with ZXing. */
    private String decodePng(String dataUri) throws Exception {
        String base64 = dataUri.replaceFirst("^data:image/png;base64,", "");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        assertThat(image).isNotNull();
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        return new MultiFormatReader().decode(bitmap).getText();
    }

    private String createVehicle(String token, String plate) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("licensePlate", plate))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    private String uniqueEmail() {
        return "qr" + (++seq) + "@example.com";
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "Secret123",
                                "firstName", "A",
                                "lastName", "B",
                                "phone", ""))))
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