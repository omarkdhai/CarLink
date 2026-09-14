package com.carlink.admin.dto;

import com.carlink.admin.model.AuditLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * One admin-facing audit row. {@code details} is the stored jsonb, parsed back
 * into a tree; malformed or null JSON surfaces as {@code null}.
 */
public record AuditLogResponse(
        UUID id,
        String action,
        String entityType,
        UUID entityId,
        String actorEmail,
        String ipAddress,
        String userAgent,
        JsonNode details,
        Instant createdAt
) {

    private static final Logger log = LoggerFactory.getLogger(AuditLogResponse.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getActor() == null ? null : auditLog.getActor().getEmail(),
                auditLog.getIpAddress(),
                auditLog.getUserAgent(),
                parseDetails(auditLog.getDetails()),
                auditLog.getCreatedAt());
    }

    private static JsonNode parseDetails(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(stored);
        } catch (JsonProcessingException e) {
            // An audit row that never round-trips JSON shouldn't break the admin view.
            log.warn("Audit log row {} holds invalid JSON; exposing null details", "malformed");
            return null;
        }
    }
}