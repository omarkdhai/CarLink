package com.carlink.admin.service;

import com.carlink.admin.dto.AuditLogResponse;
import com.carlink.admin.model.AuditLog;
import com.carlink.admin.repository.AuditLogRepository;
import com.carlink.user.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Write-only audit trail for admin (and other sensitive) actions.
 *
 * <p>{@code record} runs in a {@code REQUIRES_NEW} transaction and swallows
 * failures: a broken audit write can never roll back the mutation it records
 * or turn a successful admin action into a 500. The actor is passed as a
 * stub ({@code User} with only the id) so no actor lookup is performed inside
 * the caller's transaction.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_LIST_LIMIT = 500;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, UUID entityId,
                       UUID actorId, String ipAddress, String userAgent,
                       Map<String, Object> details) {
        try {
            User actor = actorId == null ? null : User.builder().id(actorId).build();
            String detailsJson = details == null || details.isEmpty()
                    ? null
                    : objectMapper.writeValueAsString(details);
            auditLogRepository.save(AuditLog.of(
                    actor, action, entityType, entityId,
                    ipAddress, userAgent, detailsJson, Instant.now()));
        } catch (Exception e) {
            log.warn("Audit record failed for action {}: {}", action, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> list(String action, String entityType, int limit) {
        int capped = Math.max(1, Math.min(limit, MAX_LIST_LIMIT));
        return auditLogRepository.search(action, entityType, PageRequest.of(0, capped))
                .stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}