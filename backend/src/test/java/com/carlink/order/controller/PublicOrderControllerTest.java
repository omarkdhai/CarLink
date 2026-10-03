package com.carlink.order.controller;

import com.carlink.TestProperties;
import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.NotFoundException;
import com.carlink.common.exception.TooManyRequestsException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.order.dto.OrderCreateRequest;
import com.carlink.order.dto.OrderItemRequest;
import com.carlink.order.dto.OrderResponse;
import com.carlink.order.dto.OrderSummaryResponse;
import com.carlink.order.model.Governorate;
import com.carlink.order.model.StickerPackage;
import com.carlink.order.service.OrderService;
import com.carlink.security.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicOrderControllerTest {

    @Mock private OrderService orderService;
    @Mock private RateLimiter rateLimiter;
    @Mock private HttpServletRequest request;

    private final TokenGenerator tokenGenerator = new TokenGenerator();
    private final CarLinkProperties properties = TestProperties.minimal();

    private PublicOrderController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicOrderController(orderService, rateLimiter, tokenGenerator, properties);
    }

    private OrderCreateRequest validBody() {
        return new OrderCreateRequest(
                List.of(new OrderItemRequest(StickerPackage.SINGLE, 1)),
                "Aymen Ben Ali",
                "+21655123456",
                "buyer@example.com",
                Governorate.TUNIS,
                "12 Rue de Carthage",
                null);
    }

    private static final String ORDER_KEY = "public-order-ip:";

    @Test
    void placeOrderRateLimitsThenDelegates() {
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        when(rateLimiter.tryAcquire(eq(ORDER_KEY + tokenGenerator.sha256("203.0.113.7")),
                eq(10), eq(60))).thenReturn(true);
        OrderResponse expected = new OrderResponse(
                "CL-ABC12345", "PLACED", new BigDecimal("20.00"), "TND",
                Instant.now(), List.of(), List.of());
        when(orderService.create(validBody())).thenReturn(expected);

        OrderResponse response = controller.placeOrder(validBody(), request);

        assertThat(response.reference()).isEqualTo("CL-ABC12345");
        verify(orderService).create(validBody());
    }

    @Test
    void placeOrderReturns429WhenRateLimitedAndNeverWrites() {
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        when(rateLimiter.tryAcquire(eq(ORDER_KEY + tokenGenerator.sha256("203.0.113.7")),
                eq(10), eq(60))).thenReturn(false);
        when(rateLimiter.retryAfterSeconds(eq(ORDER_KEY + tokenGenerator.sha256("203.0.113.7")),
                eq(10), eq(60))).thenReturn(31L);

        assertThatThrownBy(() -> controller.placeOrder(validBody(), request))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(
                        ((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(31L));
        verify(orderService, never()).create(validBody());
    }

    @Test
    void getOrderDelegatesAfterRateLimit() {
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        when(rateLimiter.tryAcquire(eq(ORDER_KEY + tokenGenerator.sha256("203.0.113.7")),
                eq(10), eq(60))).thenReturn(true);
        OrderSummaryResponse expected = new OrderSummaryResponse(
                "CL-AB", "PLACED", new BigDecimal("20.00"), "TND", 1, Instant.now());
        when(orderService.summary("CL-AB")).thenReturn(expected);

        OrderSummaryResponse response = controller.getOrder("CL-AB", request);

        assertThat(response.reference()).isEqualTo("CL-AB");
        assertThat(response.stickerCount()).isEqualTo(1);
        verify(orderService).summary("CL-AB");
    }

    @Test
    void getOrderReturns404ForUnknownReference() {
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        when(rateLimiter.tryAcquire(eq(ORDER_KEY + tokenGenerator.sha256("203.0.113.7")),
                eq(10), eq(60))).thenReturn(true);
        org.mockito.Mockito.doThrow(new NotFoundException("Order not found"))
                .when(orderService).summary("CL-XYZ");

        assertThatThrownBy(() -> controller.getOrder("CL-XYZ", request))
                .isInstanceOf(NotFoundException.class);
    }
}