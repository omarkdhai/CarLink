package com.carlink.vehicle;

import com.carlink.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end vehicle management: register → create → list → get → update →
 * archive → delete, plus cross-owner isolation and unauthenticated checks.
 */
class VehicleFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static int seq;

    @Test
    void vehicleLifecycleWorks() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        // Create
        MvcResult created = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nickname", "My Car",
                                "brand", "Toyota",
                                "model", "Corolla",
                                "color", "Blue",
                                "licensePlate", "AB-123-CD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licensePlate").value("AB-123-CD"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        // List shows it once
        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        // Get single
        mockMvc.perform(get("/api/v1/vehicles/{id}", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("Toyota"));

        // Update (partial)
        mockMvc.perform(patch("/api/v1/vehicles/{id}", id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("color", "Red", "model", "Camry"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("Red"))
                .andExpect(jsonPath("$.model").value("Camry"))
                .andExpect(jsonPath("$.brand").value("Toyota")); // untouched

        // Archive
        mockMvc.perform(post("/api/v1/vehicles/{id}/archive", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        // Archived vehicle is filtered out of the default list
        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // ... but visible with status=ARCHIVED
        mockMvc.perform(get("/api/v1/vehicles?status=ARCHIVED")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        // Delete
        mockMvc.perform(delete("/api/v1/vehicles/{id}", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCannotSeeOrTouchAnotherOwnersVehicle() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String secondToken = registerAndLogin(uniqueEmail());
        // Owner 1 creates a vehicle
        MvcResult created = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("licensePlate", "ZZ-999-AA"))))
                .andExpect(status().isOk())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText();

        // Owner 2 cannot read it (404 — no existence leak)
        mockMvc.perform(get("/api/v1/vehicles/{id}", id)
                        .header("Authorization", bearer(secondToken)))
                .andExpect(status().isNotFound());

        // Owner 2 cannot update or delete it
        mockMvc.perform(patch("/api/v1/vehicles/{id}", id)
                        .header("Authorization", bearer(secondToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("color", "hacked"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/vehicles/{id}", id)
                        .header("Authorization", bearer(secondToken)))
                .andExpect(status().isNotFound());

        // Owner 1's list does not include owner 2's vehicles
        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", bearer(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unauthenticatedVehicleRequestGets401() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    /** Unique address per call so tests can share one database container. */
    private String uniqueEmail() {
        return "owner" + (++seq) + "@example.com";
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
