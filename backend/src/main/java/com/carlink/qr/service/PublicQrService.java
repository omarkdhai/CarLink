package com.carlink.qr.service;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.NotFoundException;
import com.carlink.common.exception.TooManyRequestsException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.service.ConversationService;
import com.carlink.notification.contact.ContactDeliveryService;
import com.carlink.qr.dto.ContactSubmitRequest;
import com.carlink.qr.dto.ContactSubmitResponse;
import com.carlink.qr.dto.QrPublicView;
import com.carlink.qr.model.QrCode;
import com.carlink.qr.repository.QrCodeRepository;
import com.carlink.security.ratelimit.RateLimiter;
import com.carlink.vehicle.model.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Public (unauthenticated) QR token resolution.
 *
 * <p>Guarantees that hold across this class:</p>
 * <ul>
 *   <li>The raw token is only used to derive its SHA-256 for lookup and for
 *       rate-limit keys — it is never persisted, logged, or echoed.</li>
 *   <li>Responses carry a safe vehicle summary and channel names only — no
 *       phone, no license plate, no owner identity.</li>
 *   <li>Invalid or inactive tokens read as 404, so enumeration reveals nothing.</li>
 *   <li>Redis-backed rate limiting by hashed IP and hashed token sends 429
 *       with a {@code Retry-After} header.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PublicQrService {

    private static final int WINDOW_IP_SECONDS = 60;
    private static final int WINDOW_TOKEN_SECONDS = 3600;

    private final QrCodeRepository qrCodeRepository;
    private final ConversationService conversationService;
    private final ContactDeliveryService contactDeliveryService;
    private final TokenGenerator tokenGenerator;
    private final RateLimiter rateLimiter;
    private final CarLinkProperties properties;

    /** Safe, ready-to-render view for the page / API. */
    @Transactional(readOnly = true)
    public QrPublicView resolve(String rawToken, String clientIp) {
        requireRate(new RateLimits(
                rateLimiter, tokenGenerator, properties, clientIp, rawToken));
        QrCode qr = activeQr(rawToken);
        Vehicle v = qr.getVehicle();
        return new QrPublicView(
                new QrPublicView.VehicleSafe(
                        v.getNickname(), v.getBrand(), v.getModel(), v.getColor()),
                List.of("WHATSAPP", "SMS"));
    }

    /** Persists a visitor's contact request and relays it to the owner. */
    @Transactional
    public ContactSubmitResponse submit(String rawToken, String clientIp,
                                        ContactSubmitRequest request) {
        requireRate(new RateLimits(
                rateLimiter, tokenGenerator, properties, clientIp, rawToken));
        QrCode qr = activeQr(rawToken);
        Channel channel = Channel.valueOf(request.channel());

        UUID conversationId = conversationService.open(qr.getVehicle(),
                channel, ConversationStatus.PENDING);
        conversationService.appendMessage(conversationId, request.message());

        // Owner phone stays server-side: passed to the relay, never exposed.
        contactDeliveryService.deliver(conversationId, channel,
                qr.getVehicle().getOwner().getPhone(), request.message());

        return new ContactSubmitResponse(conversationId, "Message sent to the owner.");
    }

    /** Verifies the token maps to an ACTIVE QR. Missing/inactive → 404. */
    private QrCode activeQr(String rawToken) {
        return qrCodeRepository
                .findByTokenHash(tokenGenerator.sha256(rawToken))
                .filter(found -> found.isActive())
                .orElseThrow(() -> new NotFoundException("QR code not found"));
    }

    /** Enforces per-IP and per-token limits; throws 429 with Retry-After. */
    private void requireRate(RateLimits limits) {
        if (!limits.ipAllowed()) {
            throw new TooManyRequestsException(
                    "Too many requests. Try again later.", limits.ipRetryAfter());
        }
        if (!limits.tokenAllowed()) {
            throw new TooManyRequestsException(
                    "Too many requests. Try again later.", limits.tokenRetryAfter());
        }
    }

    /**
     * Bundles the two rate-limiter probes so controllers can call a single
     * check. Redis keys never contain raw data: both the IP and the token are
     * SHA-256 hashed before they are placed in a key.
     */
    record RateLimits(RateLimiter limiter, TokenGenerator tokens,
                      CarLinkProperties props, String clientIp, String rawToken) {

        boolean ipAllowed() {
            return limiter.tryAcquire("public-ip:" + tokens.sha256(clientIp),
                    props.ratelimit().ipPerMinute(), WINDOW_IP_SECONDS);
        }

        boolean tokenAllowed() {
            return limiter.tryAcquire("public-qr:" + tokens.sha256(rawToken),
                    props.ratelimit().qrPerHour(), WINDOW_TOKEN_SECONDS);
        }

        long ipRetryAfter() {
            return limiter.retryAfterSeconds("public-ip:" + tokens.sha256(clientIp),
                    props.ratelimit().ipPerMinute(), WINDOW_IP_SECONDS);
        }

        long tokenRetryAfter() {
            return limiter.retryAfterSeconds("public-qr:" + tokens.sha256(rawToken),
                    props.ratelimit().qrPerHour(), WINDOW_TOKEN_SECONDS);
        }
    }
}