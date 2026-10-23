package com.carlink.sticker.controller;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.TooManyRequestsException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.security.ratelimit.RateLimiter;
import com.carlink.sticker.dto.StickerResolveRequest;
import com.carlink.sticker.dto.StickerResolveResponse;
import com.carlink.sticker.service.StickerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated sticker registration lookup. Lets the public registration
 * page verify a short activation code (or raw QR token) before the visitor
 * signs in or creates an account. Rate-limited per hashed IP; the raw code is
 * never logged or persisted.
 */
@RestController
@RequestMapping("/api/v1/public/stickers")
@RequiredArgsConstructor
@Tag(name = "Public stickers")
public class PublicStickerController {

    private static final int WINDOW_SECONDS = 60;

    private final StickerService stickerService;
    private final RateLimiter rateLimiter;
    private final TokenGenerator tokenGenerator;
    private final CarLinkProperties properties;

    @PostMapping("/resolve")
    @Operation(summary = "Check whether a sticker code can be registered "
            + "(CLAIMABLE or BOUND; unknown reads as 404)")
    public StickerResolveResponse resolve(@Valid @RequestBody StickerResolveRequest body,
                                          HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String key = "public-sticker-resolve-ip:" + tokenGenerator.sha256(ip);
        int max = properties.ratelimit().orderIpPerMinute();
        if (!rateLimiter.tryAcquire(key, max, WINDOW_SECONDS)) {
            throw new TooManyRequestsException(
                    "Too many requests. Try again later.",
                    rateLimiter.retryAfterSeconds(key, max, WINDOW_SECONDS));
        }
        return stickerService.resolve(body.code());
    }
}
