package com.carlink.qr.controller;

import com.carlink.admin.model.ReportReason;
import com.carlink.admin.service.ReportService;
import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.NotFoundException;
import com.carlink.common.exception.TooManyRequestsException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.qr.dto.ReportSubmitRequest;
import com.carlink.security.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicReportControllerTest {

    @Mock private ReportService reportService;
    @Mock private RateLimiter rateLimiter;
    @Mock private HttpServletRequest request;

    private final TokenGenerator tokenGenerator = new TokenGenerator();
    private final CarLinkProperties properties = com.carlink.TestProperties.minimal();

    private PublicReportController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicReportController(reportService, rateLimiter, tokenGenerator, properties);
    }

    @Test
    void fileDelegatesAndReturnsAck() {
        UUID conversationId = UUID.randomUUID();
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        when(rateLimiter.tryAcquire(eq("public-report-ip:"
                + tokenGenerator.sha256("203.0.113.7")), eq(10), eq(60))).thenReturn(true);

        MessageResponse response = controller.file("raw-token", new ReportSubmitRequest(
                conversationId, ReportReason.SPAM, "spam"), request);

        assertThat(response.message()).isEqualTo("Report submitted");
        verify(reportService).file(conversationId, "203.0.113.7", ReportReason.SPAM, "spam");
    }

    @Test
    void fileThrows429WhenRateLimitedAndNeverWrites() {
        UUID conversationId = UUID.randomUUID();
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        when(rateLimiter.tryAcquire(eq("public-report-ip:"
                + tokenGenerator.sha256("203.0.113.7")), eq(10), eq(60))).thenReturn(false);
        when(rateLimiter.retryAfterSeconds(eq("public-report-ip:"
                + tokenGenerator.sha256("203.0.113.7")), eq(10), eq(60))).thenReturn(12L);

        assertThatThrownBy(() -> controller.file("t", new ReportSubmitRequest(
                conversationId, ReportReason.OTHER, "x"), request))
                .isInstanceOf(TooManyRequestsException.class);
        verify(reportService, never()).file(conversationId, "203.0.113.7", ReportReason.OTHER, "x");
    }

    @Test
    void propagatesNotFoundForUnknownConversation() {
        UUID conversationId = UUID.randomUUID();
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        when(rateLimiter.tryAcquire(eq("public-report-ip:"
                + tokenGenerator.sha256("203.0.113.7")), eq(10), eq(60))).thenReturn(true);
        org.mockito.Mockito.doThrow(new NotFoundException("Conversation not found"))
                .when(reportService).file(conversationId, "203.0.113.7", ReportReason.ABUSE, "d");

        assertThatThrownBy(() -> controller.file("t", new ReportSubmitRequest(
                conversationId, ReportReason.ABUSE, "d"), request))
                .isInstanceOf(NotFoundException.class);
    }
}