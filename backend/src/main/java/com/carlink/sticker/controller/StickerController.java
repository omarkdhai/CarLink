package com.carlink.sticker.controller;

import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import com.carlink.sticker.dto.ActivateStickerRequest;
import com.carlink.sticker.dto.StickerView;
import com.carlink.sticker.service.StickerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Authenticated sticker ownership. Activation is triggered from the public
 * scan page but requires a logged-in account, so it lives here (not under
 * {@code /api/v1/public}); every action is scoped to the acting user.
 */
@RestController
@RequestMapping("/api/v1/stickers")
@RequiredArgsConstructor
@Tag(name = "Stickers")
public class StickerController {

    private final StickerService stickerService;

    @PostMapping("/{token}/activate")
    @Operation(summary = "Claim a virgin sticker and link it to one of your cars")
    public MessageResponse activate(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable String token,
                                    @Valid @RequestBody ActivateStickerRequest body) {
        stickerService.activate(requireId(principal), token, body);
        return new MessageResponse("Sticker activated");
    }

    @PostMapping("/{stickerId}/deactivate")
    @Operation(summary = "Release a sticker you own (e.g. you sold the car)")
    public MessageResponse deactivate(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID stickerId) {
        stickerService.deactivate(requireId(principal), stickerId);
        return new MessageResponse("Sticker deactivated");
    }

    @GetMapping("/me")
    @Operation(summary = "List your stickers (id, status, bound vehicle)")
    public List<StickerView> listMine(@AuthenticationPrincipal UserPrincipal principal) {
        return stickerService.listMine(requireId(principal));
    }

    private UUID requireId(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.id();
    }
}