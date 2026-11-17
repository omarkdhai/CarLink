package com.carlink.admin.controller;

import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.dto.AdminStickerView;
import com.carlink.admin.service.AdminStickerService;
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

import java.util.Map;
import java.util.UUID;

/**
 * Admin QR-sticker management across all orders and owners. Access is
 * enforced by the {@code ROLE_ADMIN} URL matcher in {@code SecurityConfig};
 * controllers only resolve the acting id for the audit trail.
 */
@RestController
@RequestMapping("/api/v1/admin/stickers")
@RequiredArgsConstructor
@Tag(name = "Admin stickers")
public class AdminStickerController {

    private final AdminStickerService adminStickerService;

    @GetMapping
    @Operation(summary = "List/search every QR sticker with order, owner and vehicle (never tokens)")
    public Page<AdminStickerView> list(@AuthenticationPrincipal UserPrincipal principal,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) String status,
                                       @PageableDefault(size = 30, sort = "createdAt",
                                               direction = Sort.Direction.DESC) Pageable pageable) {
        requireId(principal);
        return adminStickerService.list(q, status, pageable);
    }

    @GetMapping("/counts")
    @Operation(summary = "Sticker totals per status")
    public Map<String, Long> counts(@AuthenticationPrincipal UserPrincipal principal) {
        requireId(principal);
        return adminStickerService.counts();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one sticker with order, owner and vehicle")
    public AdminStickerView get(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable UUID id) {
        requireId(principal);
        return adminStickerService.get(id);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a live (BOUND) sticker; it stops reaching the owner")
    public MessageResponse deactivate(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID id,
                                      HttpServletRequest request) {
        adminStickerService.deactivate(id, actor(principal, request));
        return MessageResponse.of("Sticker deactivated");
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
