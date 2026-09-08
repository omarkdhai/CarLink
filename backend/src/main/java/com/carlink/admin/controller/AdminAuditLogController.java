package com.carlink.admin.controller;

import com.carlink.admin.dto.AuditLogResponse;
import com.carlink.admin.service.AuditLogService;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin audit-trail viewer. Read-only; the limit is capped service-side so no
 * request can pull the whole table.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Admin audit logs")
public class AdminAuditLogController {

    private static final int DEFAULT_LIMIT = 100;

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "List audit entries, optionally filtered, newest first")
    public List<AuditLogResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestParam(required = false) String action,
                                       @RequestParam(required = false) String entityType,
                                       @RequestParam(defaultValue = "100") int limit) {
        requireId(principal);
        return auditLogService.list(action, entityType, limit);
    }

    private UUID requireId(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.id();
    }
}