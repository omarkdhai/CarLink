package com.carlink;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.privacy.PhoneCipher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for integration tests that need a real PostgreSQL and Redis.
 *
 * <p>Containers are started once in a static holder and shared by every
 * integration test class. Sharing a single long-lived set is essential:
 * Spring's TestContext framework caches one {@code ApplicationContext} for all
 * classes with identical configuration, so each class must point at the same,
 * still-running datasource/Redis — otherwise the second class reuses a cached
 * context whose {@code @Container} database was already stopped.</p>
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired private JdbcTemplate issuedStickersJdbc;
    @Autowired private PhoneCipher issuedStickersCipher;
    @Autowired private CarLinkProperties issuedStickersProperties;

    /**
     * The stickers minted for a checkout response, read from the database the
     * way the admin order page gets them (the buyer no longer receives them).
     * Each entry has {@code rawToken}, {@code activationCode}, {@code publicUrl}.
     */
    protected JsonNode issuedStickers(JsonNode orderResponse) {
        ArrayNode out = JsonNodeFactory.instance.arrayNode();
        issuedStickersJdbc.query("""
                        SELECT s.token_enc, s.activation_code_enc FROM stickers s
                          JOIN orders o ON o.id = s.order_id
                         WHERE o.reference = ? ORDER BY s.created_at, s.id""",
                rs -> {
                    String token = issuedStickersCipher.decrypt(rs.getString(1));
                    ObjectNode n = out.addObject();
                    n.put("rawToken", token);
                    n.put("activationCode", issuedStickersCipher.decrypt(rs.getString(2)));
                    n.put("publicUrl", issuedStickersProperties.baseUrl() + "/c/" + token);
                },
                orderResponse.get("reference").asString());
        return out;
    }

    /**
     * Container holder. {@code static {} } starts both containers exactly once
     * when the first integration test loads, and they stay up for the whole
     * JVM so every cached application context can reach them.
     */
    static final class Containers {
        static final PostgreSQLContainer POSTGRES =
                new PostgreSQLContainer("postgres:15-alpine")
                        .withDatabaseName("carlink_test")
                        .withUsername("test")
                        .withPassword("test");
        @SuppressWarnings("rawtypes")
        static final GenericContainer REDIS =
                new GenericContainer<>("redis:7-alpine")
                        .withExposedPorts(6379);

        static {
            POSTGRES.start();
            REDIS.start();
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", Containers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", Containers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", Containers.POSTGRES::getPassword);
        registry.add("spring.data.redis.host", () -> Containers.REDIS.getHost());
        registry.add("spring.data.redis.port", () -> Containers.REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }
}
