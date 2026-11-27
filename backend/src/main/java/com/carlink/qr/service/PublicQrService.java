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
import com.carlink.notification.sms.AlertSms;
import com.carlink.qr.dto.DeliveryStatusResponse;
import com.carlink.qr.dto.QrPublicView;
import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.notification.contact.DeliveryStatusService;
import com.carlink.qr.model.ContactReason;
import com.carlink.qr.model.QrCode;
import com.carlink.qr.repository.QrCodeRepository;
import com.carlink.security.ratelimit.RateLimiter;
import com.carlink.security.ratelimit.Throttle;
import com.carlink.security.ratelimit.ThrottleProperties;
import com.carlink.sticker.model.Sticker;
import com.carlink.sticker.model.StickerStatus;
import com.carlink.sticker.repository.StickerRepository;
import com.carlink.vehicle.model.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
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
    private final Throttle throttle;
    private final ThrottleProperties limits;
    private final TokenGenerator tokenGenerator;
    private final RateLimiter rateLimiter;
    private final CarLinkProperties properties;
    private final ConversationRepository conversationRepository;
    private final DeliveryStatusService deliveryStatusService;
    private final TransactionTemplate transactionTemplate;
    private final NoteSpamFilter noteSpamFilter;

    /** Status checks while the visitor waits: every ~2 s for ~20 s, per hashed IP. */
    static final int STATUS_CHECKS_PER_MINUTE = 40;

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

    /**
     * Persists a visitor's contact request and relays it to the owner on every
     * channel (SMS, and WhatsApp when enabled). The alert is saved and committed
     * first; the sends run after, outside the transaction, so no database
     * connection is held during the provider calls and each channel's outcome is
     * committed as soon as it is known. The visitor's {@code channel} field is
     * ignored (kept for older pages).
     */
    public ContactSubmitResponse submit(String rawToken, String clientIp,
                                        ContactSubmitRequest request) {
        requireRate(new RateLimits(
                rateLimiter, tokenGenerator, properties, clientIp, rawToken));
        // Off-topic notes (ads, scams, insults, gibberish) are refused before
        // anything is saved: the visitor is asked to change the note.
        noteSpamFilter.requireOnTopic(request.message());
        // "Other problem" says nothing by itself: the visitor must describe it.
        if (com.carlink.qr.model.ContactReason.OTHER.name().equals(request.reason())
                && (request.message() == null || request.message().isBlank())) {
            throw new com.carlink.common.exception.BadRequestException("NOTE_REQUIRED",
                    "Please describe the problem in the note.");
        }

        Prepared alert = transactionTemplate.execute(tx -> {
            String tokenHash = tokenGenerator.sha256(rawToken);
            // Sticker-first
            Optional<Sticker> sticker = stickerRepository.findByTokenHash(tokenHash);
            if (sticker.isPresent()) {
                Sticker s = sticker.get();
                if (s.getStatus() != StickerStatus.BOUND || s.getVehicle() == null) {
                    throw new NotFoundException("QR code not found");
                }
                return save(s.getVehicle(), request, rawToken, clientIp);
            }
            // Fallback: legacy qr_codes
            return save(activeQrByHash(tokenHash).getVehicle(), request, rawToken, clientIp);
        });

        // Owner phone stays server-side: passed to the relay, never exposed.
        ConversationStatus status = contactDeliveryService.deliver(alert.conversationId(),
                alert.ownerPhone(), alert.whatsappAlerts(), alert.note(), request.reason());

        return new ContactSubmitResponse(alert.conversationId(), visitorStatus(status),
                status == ConversationStatus.FAILED
                        ? "The alert could not be sent to the owner."
                        : "Alert sent to the owner.");
    }

    /** What the send needs once the alert is saved; never leaves the server. */
    private record Prepared(UUID conversationId, String ownerPhone, boolean whatsappAlerts, String note) {
    }

    // ————————— internal —————————

    private Prepared save(Vehicle vehicle, ContactSubmitRequest request,
                          String rawToken, String clientIp) {
        // S-12: each alert can become a paid SMS to a phone the visitor cannot
        // see. Beyond the per-token hourly limit above: one alert per visitor per
        // sticker per cooldown, a daily cap per sticker, and per owner (all of
        // their stickers together).
        String tokenHash = tokenGenerator.sha256(rawToken);
        throttle.require("relay-visitor", limits.getRelayVisitorCooldown(), clientIp + "|" + tokenHash);
        throttle.require("relay-sticker", limits.getRelayStickerDay(), tokenHash);
        throttle.require("relay-owner", limits.getRelayOwnerDay(), vehicle.getOwner().getId().toString());

        // Links have no place in a parking alert and make the relay a phishing
        // channel: neutralise them before the note is stored or sent.
        String note = RelayTextSanitizer.neutraliseLinks(request.message());
        // The note is optional — when absent, the reason label alone is the content.
        String content = note == null || note.isBlank()
                ? ContactReason.from(request.reason()).label()
                : note.trim();
        // SMS is the channel every owner gets; per-channel outcomes are in
        // conversation_deliveries.
        UUID conversationId = conversationService.open(vehicle,
                Channel.SMS, ConversationStatus.PENDING);
        conversationService.appendMessage(conversationId, content, request.reason());
        return new Prepared(conversationId, vehicle.getOwner().getPhone(),
                vehicle.getOwner().isWhatsappAlerts(), note);
    }

    /**
     * Delivery status of an alert the visitor just sent, refreshed from the SMS
     * provider's report. Bound to the sticker: the conversation must belong to the
     * vehicle behind {@code rawToken} (anything else reads as 404), and only a
     * coarse status leaves the server — never the failure reason, which could
     * say something about the owner's number. One exception that says nothing
     * about the owner: REFUSED when the operator refused the note's content, so
     * the visitor knows to change the note rather than retry it.
     */
    public DeliveryStatusResponse deliveryStatus(String rawToken, UUID conversationId, String clientIp) {
        String ipKey = "public-status:" + tokenGenerator.sha256(clientIp);
        if (!rateLimiter.tryAcquire(ipKey, STATUS_CHECKS_PER_MINUTE, WINDOW_IP_SECONDS)) {
            throw new TooManyRequestsException("Too many requests. Try again later.",
                    rateLimiter.retryAfterSeconds(ipKey, STATUS_CHECKS_PER_MINUTE, WINDOW_IP_SECONDS));
        }
        UUID vehicleId = vehicleIdForToken(rawToken);
        Conversation conversation = conversationRepository.findById(conversationId)
                .filter(c -> c.getVehicle().getId().equals(vehicleId))
                .orElseThrow(() -> new NotFoundException("Alert not found"));
        ConversationStatus status = deliveryStatusService.refresh(conversation);
        if (status == ConversationStatus.FAILED && deliveryStatusService.refusedForContent(conversationId)) {
            return new DeliveryStatusResponse("REFUSED");
        }
        return new DeliveryStatusResponse(visitorStatus(status));
    }

    private UUID vehicleIdForToken(String rawToken) {
        String tokenHash = tokenGenerator.sha256(rawToken);
        Optional<Sticker> sticker = stickerRepository.findByTokenHash(tokenHash);
        if (sticker.isPresent()) {
            Sticker s = sticker.get();
            if (s.getStatus() != StickerStatus.BOUND || s.getVehicle() == null) {
                throw new NotFoundException("QR code not found");
            }
            return s.getVehicle().getId();
        }
        return activeQrByHash(tokenHash).getVehicle().getId();
    }

    /** What the visitor may know: DELIVERED, SENT (waiting for confirmation) or FAILED. */
    static String visitorStatus(ConversationStatus status) {
        if (status == null) {
            return "SENT";
        }
        return switch (status) {
            case DELIVERED -> "DELIVERED";
            case FAILED -> "FAILED";
            default -> "SENT";
        };
    }

    private QrPublicView viewFromSticker(Sticker sticker) {
        StickerStatus status = sticker.getStatus();
        if (status == StickerStatus.BOUND && sticker.getVehicle() != null) {
            Vehicle v = sticker.getVehicle();
            return new QrPublicView(
                    "BOUND",
                    new QrPublicView.VehicleSafe(
                            v.getNickname(), v.getBrand(), v.getModel(), v.getColor()),
                    advertisedChannels(), AlertSms.noteLimits());
        }
        // UNBOUND or DEACTIVATED: no vehicle, no channels
        return new QrPublicView(status.name(), null, List.of(), Map.of());
    }

    private QrPublicView viewFromLegacy(QrCode qr) {
        Vehicle v = qr.getVehicle();
        return new QrPublicView(
                "BOUND",
                new QrPublicView.VehicleSafe(
                        v.getNickname(), v.getBrand(), v.getModel(), v.getColor()),
                advertisedChannels(), AlertSms.noteLimits());
    }

    /**
     * Channels every alert goes out on (SMS, plus WHATSAPP when a WhatsApp
     * provider is configured), for the page to tell the visitor. Not per owner:
     * whether an owner turned WhatsApp off stays private.
     */
    private List<String> advertisedChannels() {
        return contactDeliveryService.channels();
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
