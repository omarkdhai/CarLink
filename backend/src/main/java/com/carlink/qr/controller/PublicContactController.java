package com.carlink.qr.controller;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated entry point behind a QR scan. The raw token travels only in
 * the URL path and is used solely to derive an SHA-256 lookup key; the phone
 * number is never resolved, loaded, or returned.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public contact")
public class PublicContactController {

    private final PublicQrService publicQrService;

    @GetMapping("/qr/{token}")
    @Operation(summary = "Safe public data for a QR token "
            + "(vehicle summary + channels; never a phone or license plate)")
    public QrPublicView qrView(@PathVariable String token, HttpServletRequest request) {
        return publicQrService.resolve(token, request.getRemoteAddr());
    }

    @PostMapping("/qr/{token}/contact")
    @Operation(summary = "Submit a visitor contact request to the vehicle owner")
    public ContactSubmitResponse submit(@PathVariable String token,
                                        @Valid @RequestBody ContactSubmitRequest body,
                                        HttpServletRequest request) {
        return publicQrService.submit(token, request.getRemoteAddr(), body);
    }
}