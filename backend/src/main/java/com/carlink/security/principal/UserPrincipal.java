package com.carlink.security.principal;

import java.util.UUID;

/**
 * Authenticated principal carried in the Spring Security context. Derived from
 * the JWT claims — no user lookup is performed on ordinary requests.
 */
public record UserPrincipal(UUID id, String email, String role) {

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}