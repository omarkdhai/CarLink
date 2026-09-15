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
import com.carlink.qr.model.ContactReason;
import com.carlink.qr.model.QrCode;
import com.carlink.qr.repository.QrCodeRepository;
import com.carlink.security.ratelimit.RateLimiter;
import com.carlink.sticker.model.Sticker;
import com.carlink.sticker.model.StickerStatus;
import com.carlink.sticker.repository.StickerRepository;
import com.carlink.vehicle.model.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
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
 *
 * <p>Resolution order: the {@code stickers} table (physical stickers) is
 * checked first. If a matching hash is found, the sticker's status governs
 * the response. If no sticker matches, the legacy {@code qr_codes} table
 * is checked as a fallback so already-printed QR codes keep working.</p>
 */
@Service
@RequiredArgsConstructor
public class PublicQrService {

    private static final int WINDOW_IP_SECONDS = 60;
    private static final int WINDOW_TOKEN_SECONDS = 3600;

    private final QrCodeRepository qrCodeRepository;
    private final StickerRepository stickerRepository;
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

        String tokenHash = tokenGenerator.sha256(rawToken);

        // Sticker-first: physical stickers table
        Optional<Sticker> sticker = stickerRepository.findByTokenHash(tokenHash);
        if (sticker.isPresent()) {
            return viewFromSticker(sticker.get());
        }

        // Fallback: legacy qr_codes table (already-printed QRs keep resolving)
        QrCode qr = activeQrByHash(tokenHash);
        return viewFromLegacy(qr);
    }

    /** Persists a visitor's contact request and relays it to the owner. */
    @Transactional
    public ContactSubmitResponse submit(String rawToken, String clientIp,
                                        ContactSubmitRequest request) {
        requireRate(new RateLimits(
                rateLimiter, tokenGenerator, properties, clientIp, rawToken));

        String tokenHash = tokenGenerator.sha256(rawToken);

        // Sticker-first
        Optional<Sticker> sticker = stickerRepository.findByTokenHash(tokenHash);
        if (sticker.isPresent()) {
            Sticker s = sticker.get();
            if (s.getStatus() != StickerStatus.BOUND || s.getVehicle() == null) {
                throw new NotFoundException("QR code not found");
            }
            return doSubmit(s.getVehicle(), request);
        }

        // Fallback: legacy qr_codes
        QrCode qr = activeQrByHash(tokenHash);
        return doSubmit(qr.getVehicle(), request);
    }

    // ————————— internal —————————

    private ContactSubmitResponse doSubmit(Vehicle vehicle, ContactSubmitRequest request) {
        Channel channel = Channel.valueOf(request.channel());
        // The note is optional — when absent, the reason label alone is the content.
        String content = request.message() == null || request.message().isBlank()
                ? ContactReason.from(request.reason()).label()
                : request.message().trim();
        UUID conversationId = conversationService.open(vehicle,
                channel, ConversationStatus.PENDING);
        conversationService.appendMessage(conversationId, content, request.reason());

        // Owner phone stays server-side: passed to the relay, never exposed.
        contactDeliveryService.deliver(conversationId, channel,
                vehicle.getOwner().getPhone(), request.message(), request.reason());

        return new ContactSubmitResponse(conversationId, "Message sent to the owner.");
    }

    private QrPublicView viewFromSticker(Sticker sticker) {
        StickerStatus status = sticker.getStatus();
        if (status == StickerStatus.BOUND && sticker.getVehicle() != null) {
            Vehicle v = sticker.getVehicle();
            return new QrPublicView(
                    "BOUND",
                    new QrPublicView.VehicleSafe(
                            v.getNickname(), v.getBrand(), v.getModel(), v.getColor()),
                    List.of("WHATSAPP", "SMS"));
        }
        // UNBOUND or DEACTIVATED: no vehicle, no channels
        return new QrPublicView(status.name(), null, List.of());
    }

    private QrPublicView viewFromLegacy(QrCode qr) {
        Vehicle v = qr.getVehicle();
        return new QrPublicView(
                "BOUND",
                new QrPublicView.VehicleSafe(
                        v.getNickname(), v.getBrand(), v.getModel(), v.getColor()),
                List.of("WHATSAPP", "SMS"));
    }

    /** Verifies the token maps to an ACTIVE QR. Missing/inactive → 404. */
    private QrCode activeQrByHash(String tokenHash) {
        return qrCodeRepository
                .findByTokenHash(tokenHash)
                .filter(QrCode::isActive)
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
