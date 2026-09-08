package com.carlink.admin.dto;

import java.util.UUID;

/**
 * Who performed an admin mutation. Built in the controller from the JWT
 * principal and the servlet request so the service can write the audit row
 * atomically with the change — the actor id is never re-looked-up.
 */
public record AdminActor(UUID adminId, String ipAddress, String userAgent) {
}