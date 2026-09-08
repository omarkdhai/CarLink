package com.carlink.conversation.controller;

import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.conversation.dto.ConversationDetailResponse;
import com.carlink.conversation.dto.ConversationSummaryResponse;
import com.carlink.conversation.service.ConversationService;
import com.carlink.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Owner-facing conversation dashboard. Every endpoint resolves the acting user
 * from the JWT and scopes access via the vehicles they own — a cross-owner
 * conversation resolves to 404, never 403.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Owner conversations")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    @Operation(summary = "List own conversations across all vehicles (optionally per vehicle)")
    public List<ConversationSummaryResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID vehicleId) {
        return vehicleId == null
                ? conversationService.listForOwner(requireId(principal))
                : conversationService.listForVehicle(requireId(principal), vehicleId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of own conversations with full message history")
    public ConversationDetailResponse get(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID id) {
        return conversationService.getForOwner(requireId(principal), id);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one of own conversations as read")
    public MessageResponse markRead(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID id) {
        conversationService.markRead(requireId(principal), id);
        return new MessageResponse("Conversation marked as read");
    }

    private UUID requireId(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.id();
    }
}