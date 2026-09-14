package com.carlink.qr;

import com.carlink.AbstractIntegrationTest;
import com.carlink.admin.model.Report;
import com.carlink.admin.model.ReportReason;
import com.carlink.admin.model.ReportStatus;
import com.carlink.admin.repository.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end anonymous moderation reporting from the public QR surface:
 * a visitor files SPAM/ABUSE/OTHER against the conversation their contact
 * created, the report lands in the OPEN moderation queue, and malformed or
 * unknown-conversation requests are rejected.
 */
class PublicReportFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private ReportRepository reportRepository;

    private static int seq;

    @Test
    void visitorFilesReportThatEntersTheModerationQueue() throws Exception {
        String rawToken = ownerWithQr()[1];
        String convId = submitContact(rawToken, "WHATSAPP", "Is this still available?");

        mockMvc.perform(post("/api/v1/public/qr/{token}/report", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("conversationId", convId,
                                "reason", "SPAM", "details", "  Suspicious offer  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Report submitted"));

        // The report lands in the OPEN moderation queue with trimmed details.
        // Filtered by detail text so the assertion survives test-run ordering
        // (the shared Testcontainers DB may already hold other reports).
        List<Report> ours = reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> "Suspicious offer".equals(r.getDetails()))
                .toList();
        assertThat(ours).isNotEmpty();
        Report saved = ours.get(0);
        assertThat(saved.getReason()).isEqualTo(ReportReason.SPAM);
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(saved.getReporterIp()).isNotBlank();
    }

    @Test
    void reportAgainstUnknownConversationReturns404() throws Exception {
        String rawToken = ownerWithQr()[1];

        mockMvc.perform(post("/api/v1/public/qr/{token}/report", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("conversationId", UUID.randomUUID(),
                                "reason", "OTHER", "details", "x"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingReasonIsRejectedWith400() throws Exception {
        String rawToken = ownerWithQr()[1];
        String convId = submitContact(rawToken, "WHATSAPP", "hi");

        mockMvc.perform(post("/api/v1/public/qr/{token}/report", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("conversationId", convId))))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private String[] ownerWithQr() throws Exception {
        String auth = registerAndLogin("rp" + (++seq) + "@example.com");

        MvcResult vehicle = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearer(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nickname", "My Car",
                                "brand", "Toyota",
                                "model", "Corolla",
                                "color", "Blue",
                                "licensePlate", "RP-" + seq))))
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

    private String submitContact(String rawToken, String channel, String message)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/public/qr/{token}/contact", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", channel, "message", message))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("conversationId").asText();
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "Secret123",
                                "firstName", "A",
                                "lastName", "B",
                                "phone", "+2165510" + seq))))
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