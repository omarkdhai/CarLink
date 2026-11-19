package com.carlink.qr.controller;

import com.carlink.security.captcha.CaptchaGuard;

import org.springframework.web.bind.annotation.RequestHeader;

import com.carlink.common.web.TokenHeader;

import com.carlink.qr.dto.ContactSubmitRequest;
import com.carlink.qr.dto.ContactSubmitResponse;
import com.carlink.qr.dto.QrPublicView;
import com.carlink.qr.service.PublicQrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated entry point behind a QR scan. The raw token travels only in
 * the {@code X-CarLink-Token} header (never in the path, see {@link TokenHeader})
 * and is used solely to derive an SHA-256 lookup key; the phone
 * number is never resolved, loaded, or returned.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public contact")
public class PublicContactController {

    private final PublicQrService publicQrService;
    private final CaptchaGuard captchaGuard;

    @GetMapping("/qr")
    @Operation(summary = "Safe public data for a QR token "
            + "(vehicle summary + channels; never a phone or license plate)")
    public QrPublicView qrView(@RequestHeader(value = TokenHeader.NAME, required = false) String token,
                               HttpServletRequest request) {
        return publicQrService.resolve(TokenHeader.require(token), request.getRemoteAddr());
    }

    @PostMapping("/qr/contact")
    @Operation(summary = "Submit a visitor contact request to the vehicle owner")
    public ContactSubmitResponse submit(@RequestHeader(value = TokenHeader.NAME, required = false) String token,
                                        @Valid @RequestBody ContactSubmitRequest body,
                                        @RequestHeader(value = CaptchaGuard.HEADER, required = false) String captcha,
                                        HttpServletRequest request) {
        // S-11: every accepted alert can become a paid SMS.
        captchaGuard.require(captcha, request.getRemoteAddr());
        return publicQrService.submit(TokenHeader.require(token), request.getRemoteAddr(), body);
    }
}