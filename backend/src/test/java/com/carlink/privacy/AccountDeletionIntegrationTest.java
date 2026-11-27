package com.carlink.privacy;

import com.carlink.AbstractIntegrationTest;
import com.carlink.auth.service.JwtService;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security plan S-17: phone numbers are encrypted at rest, and an owner can
 * delete their account, which releases their stickers and erases their data.
 */
class AccountDeletionIntegrationTest extends AbstractIntegrationTest {

    private static final String PHONE = "+21655123456";

    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private PhoneEncryptionBackfill backfill;
    @Autowired private com.carlink.common.security.TokenGenerator tokenGenerator;

    @Test
    void phoneIsEncryptedAtRestAndPlainForItsOwner() throws Exception {
        Owner owner = registerOwner("cipher@example.com");

        String stored = jdbc.queryForObject("SELECT phone FROM users WHERE id = ?", String.class, owner.id);
        assertThat(stored).startsWith("v1:").doesNotContain("55123456");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", owner.bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(PHONE));
    }

    @Test
    void legacyPlaintextPhonesAreEncryptedByTheBackfill() throws Exception {
        UUID id = UUID.randomUUID();
        userRepository.save(User.builder().id(id).email("legacy-" + id + "@example.com")
                .passwordHash("x").role(Role.USER).active(true).build());
        jdbc.update("UPDATE users SET phone = ? WHERE id = ?", "+21698765432", id);

        backfill.run(null);

        String stored = jdbc.queryForObject("SELECT phone FROM users WHERE id = ?", String.class, id);
        assertThat(stored).startsWith("v1:").doesNotContain("98765432");
        assertThat(userRepository.findById(id).orElseThrow().getPhone()).isEqualTo("+21698765432");
    }

    @Test
    void ownerDeletesTheirAccountAndEverythingGoesWithIt() throws Exception {
        Owner owner = registerOwner("leaving@example.com");
        mockMvc.perform(post("/api/v1/stickers/activate-by-code")
                        .header("Authorization", owner.bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", owner.stickerCode, "details", Map.of(
                                "birthDate", "1990-01-01", "licensePlate", "170 TU 1717")))))
                .andExpect(status().isOk());
        UUID vehicleId = jdbc.queryForObject(
                "SELECT id FROM vehicles WHERE owner_id = ?", UUID.class, owner.id);
        jdbc.update("""
                INSERT INTO conversations (id, vehicle_id, channel, status, expires_at)
                VALUES (gen_random_uuid(), ?, 'SMS', 'SENT', now())""", vehicleId);

        // Wrong password: nothing happens.
        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", owner.bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "not-my-password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
        assertThat(userRepository.existsById(owner.id)).isTrue();

        MvcResult deleted = mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", owner.bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "Secret1234"))))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(deleted.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        // The account, its vehicle and its conversations are gone; the token is dead.
        assertThat(userRepository.existsById(owner.id)).isFalse();
        assertThat(count("SELECT count(*) FROM vehicles WHERE owner_id = ?", owner.id)).isZero();
        assertThat(count("SELECT count(*) FROM conversations WHERE vehicle_id = ?", vehicleId)).isZero();
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", owner.bearer))
                .andExpect(status().isUnauthorized());

        // The sticker is released, with no owner left on it, and claimable again.
        Map<String, Object> sticker = jdbc.queryForMap(
                "SELECT status, owner_id, vehicle_id FROM stickers WHERE id = ?", owner.stickerId);
        assertThat(sticker.get("status")).isEqualTo("DEACTIVATED");
        assertThat(sticker.get("owner_id")).isNull();
        assertThat(sticker.get("vehicle_id")).isNull();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "next-owner@example.com", "password", "Secret1234",
                                "firstName", "N", "lastName", "O", "phone", "",
                                "stickerCode", owner.stickerCode))))
                .andExpect(status().isOk());

        // Only an audit entry with the id remains.
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'ACCOUNT_DELETED' AND entity_id = ?",
                owner.id)).isEqualTo(1);
    }

    @Test
    void administratorsCannotDeleteThemselves() throws Exception {
        UUID id = UUID.randomUUID();
        User admin = userRepository.save(User.builder().id(id).email("admin-" + id + "@example.com")
                .passwordHash(passwordEncoder.encode("Secret1234")).role(Role.ADMIN).active(true)
                .emailVerified(true).build());
        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + jwtService.issueAccessToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "Secret1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCOUNT_DELETION"));
        assertThat(userRepository.existsById(id)).isTrue();
    }

    // ---------- helpers ----------

    private record Owner(UUID id, String bearer, String stickerCode, UUID stickerId) {}

    private Owner registerOwner(String email) throws Exception {
        MvcResult order = mockMvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "items", List.of(Map.of("stickerPackage", "SINGLE", "quantity", 1)),
                                "customerName", "Test Buyer", "mobile", "+21655123456",
                                "email", "buyer@example.com", "governorate", "TUNIS",
                                "deliveryAddress", "1 Place de la République"))))
                .andExpect(status().isOk()).andReturn();
        var sticker = issuedStickers(objectMapper.readTree(order.getResponse().getContentAsString())).get(0);
        String code = sticker.get("activationCode").asString();

        MvcResult registered = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Secret1234",
                                "firstName", "D", "lastName", "Owner", "phone", PHONE, "stickerCode", code))))
                .andExpect(status().isOk()).andReturn();
        var body = objectMapper.readTree(registered.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("user").get("id").asString());
        UUID stickerId = jdbc.queryForObject("SELECT id FROM stickers WHERE activation_code_hash = ?",
                UUID.class, tokenGenerator.sha256(tokenGenerator.normalizeActivationCode(code)));
        return new Owner(id, "Bearer " + body.get("accessToken").asString(), code, stickerId);
    }

    private long count(String sql, Object arg) {
        Long n = jdbc.queryForObject(sql, Long.class, arg);
        return n == null ? 0 : n;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
