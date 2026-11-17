package com.carlink.admin.controller;

import com.carlink.admin.dto.AdminDashboardResponse;
import com.carlink.admin.service.AdminDashboardService;
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

/**
 * Admin analytics dashboard over a rolling window. Access is enforced by the
 * {@code ROLE_ADMIN} URL matcher in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
@Tag(name = "Admin stats")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard")
    @Operation(summary = "KPIs vs previous period, time series and breakdowns for the last N days (7, 30, 90, 365)")
    public AdminDashboardResponse dashboard(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestParam(defaultValue = "30") int days) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return adminDashboardService.dashboard(days);
    }
}
