package com.carlink.admin.controller;

import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.dto.AdminVehicleView;
import com.carlink.admin.service.AdminVehicleService;
import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin fleet management across all owners. Access is enforced by the
 * {@code ROLE_ADMIN} URL matcher in {@code SecurityConfig}; controllers only
 * resolve the acting id for the audit trail.
 */
@RestController
@RequestMapping("/api/v1/admin/vehicles")
@RequiredArgsConstructor
@Tag(name = "Admin vehicles")
public class AdminVehicleController {

    private final AdminVehicleService adminVehicleService;

    @GetMapping
    @Operation(summary = "List/search every vehicle with owner and activity counts (never the owner's phone)")
    public Page<AdminVehicleView> list(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) String status,
                                       @PageableDefault(size = 30, sort = "createdAt",
                                               direction = Sort.Direction.DESC) Pageable pageable) {
        requireId(principal);
        return adminVehicleService.list(q, status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one vehicle with owner and activity counts")
    public AdminVehicleView get(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable UUID id) {
        requireId(principal);
        return adminVehicleService.get(id);
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a vehicle (hidden from the owner's active fleet)")
    public MessageResponse archive(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable UUID id,
                                   HttpServletRequest request) {
        adminVehicleService.archive(id, actor(principal, request));
        return MessageResponse.of("Vehicle archived");
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore an archived vehicle to ACTIVE")
    public MessageResponse restore(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable UUID id,
                                   HttpServletRequest request) {
        adminVehicleService.restore(id, actor(principal, request));
        return MessageResponse.of("Vehicle restored");
    }

    private AdminActor actor(UserPrincipal principal, HttpServletRequest request) {
        return new AdminActor(requireId(principal),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    private UUID requireId(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.id();
    }
}
