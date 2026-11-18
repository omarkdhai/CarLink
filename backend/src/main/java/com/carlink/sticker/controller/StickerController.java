package com.carlink.sticker.controller;

import org.springframework.web.bind.annotation.RequestHeader;

import com.carlink.common.web.TokenHeader;

import com.carlink.security.ratelimit.Throttle;
import com.carlink.security.ratelimit.ThrottleProperties;
import jakarta.servlet.http.HttpServletRequest;

import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import com.carlink.sticker.dto.ActivateStickerByCodeRequest;
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
    private final Throttle throttle;
    private final ThrottleProperties limits;

    @PostMapping("/activate")
    @Operation(summary = "Claim a virgin sticker and link it to one of your cars")
    public MessageResponse activate(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestHeader(value = TokenHeader.NAME, required = false) String token,
                                    @Valid @RequestBody ActivateStickerRequest body,
                                    HttpServletRequest http) {
        throttleActivation(principal, http);
        stickerService.activate(requireId(principal), TokenHeader.require(token), body);
        return new MessageResponse("Sticker activated");
    }

    @PostMapping("/activate-by-code")
    @Operation(summary = "Claim a sticker by its printed activation code and link it to one of your cars")
    public MessageResponse activateByCode(@AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody ActivateStickerByCodeRequest body,
                                          HttpServletRequest http) {
        throttleActivation(principal, http);
        stickerService.activateByCode(requireId(principal), body.code(), body.details());
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

    /** S-03: activation codes are ~40 bits; cap guesses per account and per IP. */
    private void throttleActivation(UserPrincipal principal, HttpServletRequest http) {
        throttle.require("sticker-activate-user", limits.getStickerActivateUser(),
                requireId(principal).toString());
        throttle.require("sticker-activate-ip", limits.getStickerActivateIp(), http.getRemoteAddr());
    }

    private UUID requireId(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.id();
    }
}