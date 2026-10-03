package com.carlink.notification.contact;

import com.carlink.common.security.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Mock anonymous call service for production-like testing without a real
 * call provider. Simulates a masked call from CarLink to the vehicle owner
 * by logging the would-be call.
 *
 * <p>Replace this with a real provider (e.g. Twilio Voice + TwiML) when
 * credentials exist — switch class, keep the same method. The real
 * implementation connects the passenger → owner without revealing either
 * number to the other.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymousCallService {

    private final TokenGenerator tokenGenerator;

    /**
     * Initiates an anonymous masked call to the vehicle owner.
     * In the mock version, logs the call details to console.
     *
     * @param ownerPhone   the owner's phone number (E.164 format)
     * @param reason       the alert reason code
     * @param message      the passenger's custom message
     * @param conversationId for correlation
     * @return true if the call was "initiated" (mock always returns true)
     */
    public boolean initiateCall(String ownerPhone, String reason, String message, String conversationId) {
        String ownerHash = tokenGenerator.sha256(ownerPhone);
        String reasonLabel = reasonLabel(reason);

        log.info("[MOCK-CALL] ═══════════════════════════════════");
        log.info("[MOCK-CALL] CONVERSATION:  {}", conversationId);
        log.info("[MOCK-CALL] TO:            {} (hash: {})", maskPhone(ownerPhone), ownerHash);
        log.info("[MOCK-CALL] FROM:          CarLink Anonymous (+216 00 000 000)");
        log.info("[MOCK-CALL] REASON:        {} ({})", reasonLabel, reason);
        log.info("[MOCK-CALL] MESSAGE:       {}", message);
        log.info("[MOCK-CALL] CALL TYPE:     Anonymous masked call (owner sees CarLink)");
        log.info("[MOCK-CALL] SIMULATED:     ✅ Call connected — owner will hear automated message");
        log.info("[MOCK-CALL] ═══════════════════════════════════");

        // In a real Twilio implementation:
        // 1. Create a TwiML response that says the alert message
        // 2. Dial the owner with callerId = CarLink's Twilio number
        // 3. The owner answers → hears "CarLink alert: [reason]. Message: [message]"
        // 4. Owner can press 1 to reply via SMS/WhatsApp (future enhancement)
        return true;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return phone.substring(0, phone.length() - 4).replaceAll(".", "*") + phone.substring(phone.length() - 4);
    }

    private String reasonLabel(String reason) {
        return switch (reason) {
            case "BLOCKING" -> "Car is blocking me";
            case "LIGHTS" -> "Lights are on";
            case "LEFT_OPEN" -> "Car is left open";
            case "HIT_CAR" -> "Hit your car";
            case "WINDOWS_OPEN" -> "Windows left open";
            case "EV_CHARGE" -> "EV charge needed";
            default -> "Alert";
        };
    }
}