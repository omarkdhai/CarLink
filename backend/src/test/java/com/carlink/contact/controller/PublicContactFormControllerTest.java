package com.carlink.contact.controller;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.TooManyRequestsException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.contact.dto.ContactFormRequest;
import com.carlink.contact.service.ContactFormService;
import com.carlink.security.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicContactFormControllerTest {

    @Mock private ContactFormService contactFormService;
    @Mock private RateLimiter rateLimiter;
    @Mock private HttpServletRequest request;

    private final TokenGenerator tokenGenerator = new TokenGenerator();
    private final CarLinkProperties properties = com.carlink.TestProperties.minimal();

    private PublicContactFormController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicContactFormController(contactFormService, rateLimiter, tokenGenerator, properties);
    }

    @Test
    void submitDelegatesAndReturnsAck() {
        when(request.getRemoteAddr()).thenReturn("203.0.113.7");
        when(rateLimiter.tryAcquire(eq("public-contact-ip:"
                + tokenGenerator.sha256("203.0.113.7")), eq(1000), eq(60))).thenReturn(true);

        ContactFormRequest body = new ContactFormRequest("Jane", "jane@example.com", "Question", null, "Hello");
        MessageResponse response = controller.submit(body, request);

        assertThat(response.message()).isEqualTo("Message sent");
        verify(contactFormService).submit(body);
    }

    @Test
    void submitThrows429WhenRateLimitedAndNeverSends() {
        when(request.getRemoteAddr()).thenReturn("198.51.100.9");
        when(rateLimiter.tryAcquire(eq("public-contact-ip:"
                + tokenGenerator.sha256("198.51.100.9")), eq(1000), eq(60))).thenReturn(false);
        when(rateLimiter.retryAfterSeconds(eq("public-contact-ip:"
                + tokenGenerator.sha256("198.51.100.9")), eq(1000), eq(60))).thenReturn(9L);

        ContactFormRequest body = new ContactFormRequest("Joe", "joe@example.com", "Hi", null, "There");
        assertThatThrownBy(() -> controller.submit(body, request))
                .isInstanceOf(TooManyRequestsException.class);
        verify(contactFormService, never()).submit(body);
    }
}