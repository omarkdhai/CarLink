package com.carlink.admin;

import com.carlink.AbstractIntegrationTest;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end admin dashboard: user management with self/last-admin guards and
 * audit trail, report moderation (list/detail/status/CSV), the survival of a
 * report after its conversation is deleted, access control, and the stats
 * overview.
 */
class AdminFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static int seq;

    // ---------- user management ----------

    @Test
    void adminListsSearchesAndGetsUsersWithoutPhone() throws Exception {
        Admin admin = newAdmin();
        Owner owner = newOwner();

        // List never exposes the private phone
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", bearer(admin.auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].phone").doesNotExist());

        // Search by email fragment finds the owner
        String fragment = owner.email.split("@")[0];
        JsonNode searched = getJson(admin.auth, "/api/v1/admin/users?q=" + fragment);
        boolean found = false;
        for (JsonNode u : searched) {
            if (owner.email.equals(u.get("email").asText())) {
                found = true;
                assertThat(u.get("vehicleCount").asLong()).isGreaterThanOrEqualTo(0);
            }
        }
        assertThat(found).as("search must include the registered owner").isTrue();

        // Role filter keeps only admins
        JsonNode adminsOnly = getJson(admin.auth, "/api/v1/admin/users?role=ADMIN");
        assertThat(adminsOnly.findValues("role"))
                .allMatch(n -> "ADMIN".equals(n.asText()));

        // Single-user lookup
        JsonNode got = getJson(admin.auth, "/api/v1/admin/users/" + owner.userId);
        assertThat(got.get("email").asText()).isEqualTo(owner.email);
    }

    @Test
    void deactivatedUserCannotLoginUntilReactivated() throws Exception {
        Admin admin = newAdmin();
        Owner owner = newOwner();

        // Owner can log in before deactivation
        login(owner.email, "Secret123").andExpect(status().isOk());

        // Admin deactivates; the account is excluded from active listings
        mockMvc.perform(post("/api/v1/admin/users/{id}/deactivate", owner.userId)
                        .header("Authorization", bearer(admin.auth)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users?active=false")
                        .header("Authorization", bearer(admin.auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + owner.userId + "')]").exists());

        // Deactivation blocks login
        login(owner.email, "Secret123").andExpect(status().isUnauthorized());

        // The deactivation is in the audit trail
        JsonNode audit = getJson(admin.auth, "/api/v1/admin/audit-logs?action=USER_DEACTIVATE");
        assertThat(audit.at("/0/entityId").asText()).isEqualTo(owner.userId.toString());

        // Reactivation restores access
        mockMvc.perform(post("/api/v1/admin/users/{id}/activate", owner.userId)
                        .header("Authorization", bearer(admin.auth)))
                .andExpect(status().isOk());
        login(owner.email, "Secret123").andExpect(status().isOk());
    }

    @Test
    void adminCannotDeactivateOrDemoteSelf() throws Exception {
        Admin admin = newAdmin();

        mockMvc.perform(post("/api/v1/admin/users/{id}/deactivate", admin.userId)
                        .header("Authorization", bearer(admin.auth)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/users/{id}/role", admin.userId)
                        .header("Authorization", bearer(admin.auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void promoteAndDemoteWithSecondAdminWritesAuditTrail() throws Exception {
        Admin admin = newAdmin();
        Owner owner = newOwner();

        // Promote the owner to ADMIN
        mockMvc.perform(post("/api/v1/admin/users/{id}/role", owner.userId)
                        .header("Authorization", bearer(admin.auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk());

        // The promotion is audit-logged with from/to and the acting admin
        JsonNode audit = getJson(admin.auth, "/api/v1/admin/audit-logs?action=USER_ROLE_CHANGE");
        JsonNode newest = audit.get(0);
        assertThat(newest.get("entityId").asText()).isEqualTo(owner.userId.toString());
        assertThat(newest.get("details").get("from").asText()).isEqualTo("USER");
        assertThat(newest.get("details").get("to").asText()).isEqualTo("ADMIN");
        assertThat(newest.get("actorEmail").asText()).isEqualTo(admin.email);

        // Two admins exist now, so demoting the new admin succeeds
        mockMvc.perform(post("/api/v1/admin/users/{id}/role", owner.userId)
                        .header("Authorization", bearer(admin.auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "USER"))))
                .andExpect(status().isOk());
    }

    // ---------- report moderation ----------

    @Test
    void adminModeratesReportLifecycleAndExportsCsv() throws Exception {
        Admin admin = newAdmin();
        Owner owner = newOwner();
        String convId = fileReportAsVisitor(owner.auth);

        // The report shows up in the OPEN queue, linked to its conversation
        JsonNode mine = findReportByConversation(admin.auth, convId);
        String reportId = mine.get("id").asText();
        assertThat(mine.get("reason").asText()).isEqualTo("SPAM");
        assertThat(mine.get("status").asText()).isEqualTo("OPEN");

        // Detail exposes the reported message content (admin-only exception)
        JsonNode detail = getJson(admin.auth, "/api/v1/admin/reports/" + reportId);
        assertThat(detail.get("conversation").get("conversationId").asText()).isEqualTo(convId);
        assertThat(detail.get("conversation").get("lastMessageContent").asText())
                .isEqualTo("Buy it now!");
        assertThat(detail.get("conversation").get("messageCount").asLong()).isEqualTo(1);

        // Transition OPEN → REVIEWED
        mockMvc.perform(post("/api/v1/admin/reports/{id}/status", reportId)
                        .header("Authorization", bearer(admin.auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "REVIEWED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Report marked reviewed"));

        // The transition reflects in the filtered list
        mockMvc.perform(get("/api/v1/admin/reports?status=REVIEWED")
                        .header("Authorization", bearer(admin.auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + reportId + "')].status")
                        .value(org.hamcrest.Matchers.hasItem("REVIEWED")));

        // CSV export: attachment with fixed header, our row included
        MvcResult export = mockMvc.perform(get("/api/v1/admin/reports/export")
                        .param("status", "REVIEWED")
                        .header("Authorization", bearer(admin.auth)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        containsString("filename=\"reports.csv\"")))
                .andReturn();
        String csv = export.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("id,createdAt,reason,status,reporterIp,conversationId,");
        assertThat(csv).contains(reportId, "Repeated spam about the sale");
    }

    @Test
    void reportSurvivesConversationDeletionWithNullContext() throws Exception {
        Admin admin = newAdmin();
        Owner owner = newOwner();
        String convId = fileReportAsVisitor(owner.auth);
        String reportId = findReportByConversation(admin.auth, convId).get("id").asText();

        // Deleting the reported conversation must SET NULL the FK, keeping the report
        conversationRepository.deleteById(UUID.fromString(convId));

        mockMvc.perform(get("/api/v1/admin/reports/{id}", reportId)
                        .header("Authorization", bearer(admin.auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.conversation").value(nullValue()));
    }

    // ---------- access control ----------

    @Test
    void regularUserIsForbiddenAndAnonymousIsUnauthorized() throws Exception {
        Owner owner = newOwner();

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", bearer(owner.auth)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- analytics ----------

    @Test
    void adminStatsOverviewReflectsSeededCounts() throws Exception {
        Admin admin = newAdmin();
        Owner owner = newOwner();
        fileReportAsVisitor(owner.auth);

        JsonNode stats = getJson(admin.auth, "/api/v1/admin/stats/overview");
        assertThat(stats.get("usersTotal").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(stats.get("usersAdmins").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("vehiclesTotal").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("qrTotal").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("conversationsSent").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("messagesTotal").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("reportsOpen").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("reportsOpenVsTotal").asDouble()).isGreaterThan(0.0);
    }

    // ---------- helpers ----------

    /** Inserts an ADMIN directly (bootstrap is dev-only) and logs in. */
    private Admin newAdmin() throws Exception {
        String email = "ad-root" + (++seq) + "@carlink.test";
        userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordEncoder.encode("Admin123"))
                .firstName("Root")
                .lastName("Admin")
                .role(Role.ADMIN)
                .active(true)
                .emailVerified(true)
                .build());

        JsonNode body = postJson("/api/v1/auth/login",
                Map.of("email", email, "password", "Admin123"));
        return new Admin(body.get("accessToken").asText(),
                UUID.fromString(body.get("user").get("id").asText()), email);
    }

    private Owner newOwner() throws Exception {
        String email = "ad" + (++seq) + "@example.com";
        JsonNode body = postJson("/api/v1/auth/register",
                Map.of("email", email, "password", "Secret123",
                        "firstName", "Own", "lastName", "Er", "phone", "+21655100" + seq));
        return new Owner(body.get("accessToken").asText(),
                UUID.fromString(body.get("user").get("id").asText()), email);
    }

    /** owner → vehicle → QR → contact → anonymous SPAM report; returns the conversation id. */
    private String fileReportAsVisitor(String ownerAuth) throws Exception {
        MvcResult vehicle = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearer(ownerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nickname", "My Car",
                                "brand", "Toyota",
                                "model", "Corolla",
                                "color", "Blue",
                                "licensePlate", "AD-" + (++seq) + "-XY"))))
                .andExpect(status().isOk())
                .andReturn();
        String vehicleId = objectMapper.readTree(vehicle.getResponse().getContentAsString())
                .get("id").asText();

        MvcResult qr = mockMvc.perform(post("/api/v1/vehicles/{id}/qr", vehicleId)
                        .header("Authorization", bearer(ownerAuth)))
                .andExpect(status().isOk())
                .andReturn();
        String rawToken = objectMapper.readTree(qr.getResponse().getContentAsString())
                .get("rawToken").asText();

        MvcResult contact = mockMvc.perform(post("/api/v1/public/qr/{token}/contact", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "WHATSAPP", "message", "Buy it now!"))))
                .andExpect(status().isOk())
                .andReturn();
        String convId = objectMapper.readTree(contact.getResponse().getContentAsString())
                .get("conversationId").asText();

        mockMvc.perform(post("/api/v1/public/qr/{token}/report", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("conversationId", convId,
                                "reason", "SPAM", "details", "Repeated spam about the sale"))))
                .andExpect(status().isOk());

        return convId;
    }

    private JsonNode findReportByConversation(String adminAuth, String conversationId)
            throws Exception {
        JsonNode list = getJson(adminAuth, "/api/v1/admin/reports");
        for (JsonNode report : list) {
            if (!report.get("conversationId").isNull()
                    && conversationId.equals(report.get("conversationId").asText())) {
                return report;
            }
        }
        throw new AssertionError("No report found for conversation " + conversationId);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))));
    }

    private JsonNode postJson(String url, Map<String, String> body) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String auth, String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url)
                        .header("Authorization", bearer(auth)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private record Admin(String auth, UUID userId, String email) {}
    private record Owner(String auth, UUID userId, String email) {}
}