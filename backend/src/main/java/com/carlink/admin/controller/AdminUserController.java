package com.carlink.admin.controller;

import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.dto.AdminUserResponse;
import com.carlink.admin.dto.ChangeRoleRequest;
import com.carlink.admin.service.AdminUserService;
import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import com.carlink.user.model.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin user management. Access is enforced by the {@code ROLE_ADMIN} URL
 * matcher in {@code SecurityConfig}; controllers only resolve the acting id
 * for the audit trail.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "List/search users (never returns the private phone)")
    public List<AdminUserResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) Boolean active,
                                        @RequestParam(required = false) Role role) {
        requireId(principal);
        return adminUserService.list(q, active, role);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one user with vehicle count")
    public AdminUserResponse get(@AuthenticationPrincipal UserPrincipal principal,
                                 @PathVariable UUID id) {
        requireId(principal);
        return adminUserService.get(id);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a user's account (blocks login); never self-service")
    public MessageResponse deactivate(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID id,
                                      HttpServletRequest request) {
        adminUserService.deactivate(id, actor(principal, request));
        return MessageResponse.of("User deactivated");
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Reactivate a deactivated account")
    public MessageResponse activate(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID id,
                                    HttpServletRequest request) {
        adminUserService.activate(id, actor(principal, request));
        return MessageResponse.of("User activated");
    }

    @PostMapping("/{id}/role")
    @Operation(summary = "Promote a user to ADMIN or demote (last admin is protected)")
    public MessageResponse changeRole(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody ChangeRoleRequest body,
                                      HttpServletRequest request) {
        adminUserService.changeRole(id, body.role(), actor(principal, request));
        return MessageResponse.of("User role updated");
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