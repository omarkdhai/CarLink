package com.carlink.security;

import com.carlink.AbstractIntegrationTest;
import com.carlink.auth.service.JwtService;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pentest finding 2026-11-19: malformed client requests answered 500 (and an
 * unauthenticated one could fire the 5xx alert). They must be clean 4xx that
 * echo nothing back.
 */
class MalformedRequestIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;

    @Test
    void wrongContentTypeOnLoginIs415() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_XML).content("<a/>"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void malformedPathIdsAre400AndNotEchoed() throws Exception {
        UUID id = UUID.randomUUID();
        User owner = userRepository.save(User.builder().id(id).email("malformed-" + id + "@example.com")
                .passwordHash("x").role(Role.USER).active(true).build());
        String bearer = "Bearer " + jwtService.issueAccessToken(owner);
        mockMvc.perform(get("/api/v1/conversations/not-a-uuid-SECRETISH").header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(content().string(not(containsString("SECRETISH"))));
        mockMvc.perform(get("/api/v1/vehicles/123").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }
}
