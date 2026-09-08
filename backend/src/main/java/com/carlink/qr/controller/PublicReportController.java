package com.carlink.qr.controller;

import com.carlink.admin.service.ReportService;
import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.TooManyRequestsException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.qr.dto.ReportSubmitRequest;
import com.carlink.security.ratelimit.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous moderation-report filing behind a QR scan. The raw token keeps the
 * URL on the public QR surface but is not used for lookup — the authoritative
 * validation is that the reported conversation exists (404 otherwise). Per-IP
 * rate limiting runs before any write; the Redis key hashes the IP.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public contact")
public class PublicReportController {

    private static final int WINDOW_SECONDS = 60;

    private final ReportService reportService;
    private final RateLimiter rateLimiter;
    private final TokenGenerator tokenGenerator;
    private final CarLinkProperties properties;

    @PostMapping("/qr/{token}/report")
    @Operation(summary = "File an anonymous SPAM/ABUSE/OTHER report against a conversation")
    public MessageResponse file(@PathVariable String token,
                                @Valid @RequestBody ReportSubmitRequest body,
                                HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String key = "public-report-ip:" + tokenGenerator.sha256(ip);
        int max = properties.ratelimit().reportIpPerMinute();
        if (!rateLimiter.tryAcquire(key, max, WINDOW_SECONDS)) {
            throw new TooManyRequestsException(
                    "Too many requests. Try again later.",
                    rateLimiter.retryAfterSeconds(key, max, WINDOW_SECONDS));
        }
        reportService.file(body.conversationId(), ip, body.reason(), body.details());
        return MessageResponse.of("Report submitted");
    }
}