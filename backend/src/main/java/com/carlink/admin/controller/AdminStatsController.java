package com.carlink.admin.controller;

import com.carlink.admin.dto.AdminStatsResponse;
import com.carlink.admin.service.AdminStatsService;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Platform analytics overview. Access is enforced by the {@code ROLE_ADMIN}
 * URL matcher.
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@Tag(name = "Admin stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/overview")
    @Operation(summary = "Platform counters (users, vehicles, QRs, conversations, reports)")
    public AdminStatsResponse overview(@AuthenticationPrincipal UserPrincipal principal) {
        requireId(principal);
        return adminStatsService.overview();
    }

    private UUID requireId(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.id();
    }
}