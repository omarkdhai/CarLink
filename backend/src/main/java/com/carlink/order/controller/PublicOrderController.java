package com.carlink.order.controller;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.TooManyRequestsException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.order.dto.OrderCreateRequest;
import com.carlink.order.dto.OrderResponse;
import com.carlink.order.dto.OrderSummaryResponse;
import com.carlink.order.service.OrderService;
import com.carlink.security.ratelimit.RateLimiter;
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
 * Anonymous guest checkout for COD sticker orders. Both endpoints are
 * per-hashed-IP rate-limited at {@code carlink.ratelimit.orderIpPerMinute}
 * (default 5 per minute).
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public orders")
public class PublicOrderController {

    private static final int WINDOW_SECONDS = 60;

    private final OrderService orderService;
    private final RateLimiter rateLimiter;
    private final TokenGenerator tokenGenerator;
    private final CarLinkProperties properties;

    @PostMapping("/orders")
    @Operation(summary = "Place a COD order for virgin stickers — returns raw tokens once")
    public OrderResponse placeOrder(@Valid @RequestBody OrderCreateRequest body,
                                    HttpServletRequest request) {
        rateLimit(request);
        return orderService.create(body);
    }

    @GetMapping("/orders/{reference}")
    @Operation(summary = "Look up order summary by reference (token-free, PII-free)")
    public OrderSummaryResponse getOrder(@PathVariable String reference,
                                         HttpServletRequest request) {
        rateLimit(request);
        return orderService.summary(reference);
    }

    private void rateLimit(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String key = "public-order-ip:" + tokenGenerator.sha256(ip);
        int max = properties.ratelimit().orderIpPerMinute();
        if (!rateLimiter.tryAcquire(key, max, WINDOW_SECONDS)) {
            throw new TooManyRequestsException(
                    "Too many requests. Try again later.",
                    rateLimiter.retryAfterSeconds(key, max, WINDOW_SECONDS));
        }
    }
}
