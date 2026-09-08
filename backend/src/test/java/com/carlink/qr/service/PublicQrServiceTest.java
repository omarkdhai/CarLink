package com.carlink.qr.service;

import com.carlink.TestProperties;
import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.NotFoundException;
import com.carlink.common.exception.TooManyRequestsException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.conversation.service.ConversationService;
import com.carlink.qr.dto.ContactSubmitRequest;
import com.carlink.qr.dto.ContactSubmitResponse;
import com.carlink.qr.dto.QrPublicView;
import com.carlink.qr.model.QrCode;
import com.carlink.qr.repository.QrCodeRepository;
import com.carlink.security.ratelimit.RateLimiter;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.vehicle.model.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicQrServiceTest {

    @Mock private QrCodeRepository qrCodeRepository;
    @Mock private ConversationService conversationService;
    @Mock private RateLimiter rateLimiter;

    private final TokenGenerator tokenGenerator = new TokenGenerator();
    private final CarLinkProperties properties = TestProperties.minimal();
    private PublicQrService service;

    private final UUID vehicleId = UUID.randomUUID();
    private final Vehicle vehicle = Vehicle.builder()
            .id(vehicleId)
            .owner(User.builder().id(UUID.randomUUID()).role(Role.USER).build())
            .nickname("My Car")
            .brand("Toyota")
            .model("Corolla")
            .color("Blue")
            .licensePlate("AB-123-CD")
            .build();
    private final String rawToken = "public-token-abc";
    private final String clientIp = "203.0.113.7";

    @BeforeEach
    void setUp() {
        service = new PublicQrService(qrCodeRepository, conversationService,
                tokenGenerator, rateLimiter, properties);
    }

    private QrCode activeCode() {
        return QrCode.newActive(vehicle, tokenGenerator.sha256(rawToken));
    }

    private void allowAllRequests() {
        when(rateLimiter.tryAcquire(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
    }

    // ---------- resolve ----------

    @Test
    void resolveReturnsSafeVehicleDataAndChannels() {
        allowAllRequests();
        when(qrCodeRepository.findByTokenHash(tokenGenerator.sha256(rawToken)))
                .thenReturn(Optional.of(activeCode()));

        QrPublicView view = service.resolve(rawToken, clientIp);

        assertThat(view.vehicle().nickname()).isEqualTo("My Car");
        assertThat(view.vehicle().brand()).isEqualTo("Toyota");
        assertThat(view.channels()).containsExactly("WHATSAPP", "SMS");
    }

    @Test
    void resolveHidesLicensePlateAndAnythingAboutOwner() {
        allowAllRequests();
        when(qrCodeRepository.findByTokenHash(tokenGenerator.sha256(rawToken)))
                .thenReturn(Optional.of(activeCode()));

        QrPublicView view = service.resolve(rawToken, clientIp);

        // The DTO simply has no such fields — nothing can leak them.
        assertThat(view.toString()).doesNotContain("AB-123-CD");
        assertThat(view.toString()).doesNotContain("phone");
    }

    @Test
    void resolveNotFoundForUnknownToken() {
        allowAllRequests();
        when(qrCodeRepository.findByTokenHash(tokenGenerator.sha256("nope")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("nope", clientIp))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resolveNotFoundForInactiveToken() {
        allowAllRequests();
        QrCode inactive = activeCode();
        inactive.deactivate();
        when(qrCodeRepository.findByTokenHash(tokenGenerator.sha256(rawToken)))
                .thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.resolve(rawToken, clientIp))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resolveThrowsNotFoundsWithRateLimitHeaderDataWhenBlocked() {
        when(rateLimiter.tryAcquire(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        when(rateLimiter.retryAfterSeconds(anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(37L);

        assertThatThrownBy(() -> service.resolve(rawToken, clientIp))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(
                        ((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(37L));
    }

    @Test
    void rateLimitKeysNeverContainRawValues() {
        when(rateLimiter.tryAcquire(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(qrCodeRepository.findByTokenHash(tokenGenerator.sha256(rawToken)))
                .thenReturn(Optional.of(activeCode()));

        service.resolve(rawToken, clientIp);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter, org.mockito.Mockito.atLeastOnce())
                .tryAcquire(keys.capture(), org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());
        for (String key : keys.getAllValues()) {
            assertThat(key)
                    .doesNotContain(rawToken)
                    .doesNotContain(clientIp);
        }
    }

    // ---------- submit ----------

    @Test
    void submitPersistsConversationAndReturnsGenericSuccess() {
        allowAllRequests();
        when(qrCodeRepository.findByTokenHash(tokenGenerator.sha256(rawToken)))
                .thenReturn(Optional.of(activeCode()));
        when(conversationService.open(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(UUID.randomUUID());

        ContactSubmitResponse response = service.submit(rawToken, clientIp,
                new ContactSubmitRequest("WHATSAPP", "Hi! Is this car for sale?"));

        assertThat(response.message()).contains("owner");
        verify(conversationService).appendMessage(
                org.mockito.ArgumentMatchers.any(UUID.class), anyString());
    }

    @Test
    void submitRejectsInvalidToken() {
        allowAllRequests();
        when(qrCodeRepository.findByTokenHash(tokenGenerator.sha256("bad")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit("bad", clientIp,
                new ContactSubmitRequest("SMS", "hello")))
                .isInstanceOf(NotFoundException.class);
        verify(conversationService, never()).open(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void submitIsRateLimitedPerToken() {
        when(rateLimiter.tryAcquire(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        when(rateLimiter.retryAfterSeconds(anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(42L);

        assertThatThrownBy(() -> service.submit(rawToken, clientIp,
                new ContactSubmitRequest("WHATSAPP", "hi")))
                .isInstanceOf(TooManyRequestsException.class);
    }
}