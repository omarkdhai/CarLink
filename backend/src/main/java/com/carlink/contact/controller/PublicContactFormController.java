package com.carlink.contact.controller;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.TooManyRequestsException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.contact.dto.ContactFormRequest;
import com.carlink.contact.service.ContactFormService;
import com.carlink.security.ratelimit.RateLimiter;
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
 * Anonymous "Contact us" form submission. Per-IP rate limiting runs before any
 * email is sent; the Redis key hashes the IP (mirrors PublicReportController).
 * The send failures surface as a clean error instead of a bare 500.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public contact")
public class PublicContactFormController {

    private static final int WINDOW_SECONDS = 60;

    private final ContactFormService contactFormService;
    private final RateLimiter rateLimiter;
    private final TokenGenerator tokenGenerator;
    private final CarLinkProperties properties;

    @PostMapping("/contact")
    @Operation(summary = "Send a message to the CarLink team via the website contact form")
    public MessageResponse submit(@Valid @RequestBody ContactFormRequest body,
                                  HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String key = "public-contact-ip:" + tokenGenerator.sha256(ip);
        int max = properties.contactForm().ipPerMinute();
        if (!rateLimiter.tryAcquire(key, max, WINDOW_SECONDS)) {
            throw new TooManyRequestsException(
                    "Too many requests. Try again later.",
                    rateLimiter.retryAfterSeconds(key, max, WINDOW_SECONDS));
        }
        contactFormService.submit(body);
        return MessageResponse.of("Message sent");
    }
}