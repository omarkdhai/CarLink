package com.carlink.admin.service;

import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.dto.AdminStickerView;
import com.carlink.common.exception.BadRequestException;
import com.carlink.common.exception.ConflictException;
import com.carlink.common.exception.NotFoundException;
import com.carlink.sticker.model.Sticker;
import com.carlink.sticker.model.StickerStatus;
import com.carlink.sticker.repository.StickerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Admin QR-sticker management across every order and owner: search, inspect,
 * per-status totals, and deactivation of a live (BOUND) sticker. Every state
 * change is audit-logged.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminStickerService {

    private final StickerRepository stickerRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<AdminStickerView> list(String q, String status, Pageable pageable) {
        String term = q == null || q.isBlank() ? null : q.trim();
        return stickerRepository.adminSearch(term, parseStatus(status), pageable)
                .map(AdminStickerView::from);
    }

    @Transactional(readOnly = true)
    public AdminStickerView get(UUID stickerId) {
        return AdminStickerView.from(getEntity(stickerId));
    }

    /** Totals per status, every status present (zero when none). */
    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        Map<StickerStatus, Long> counts = new EnumMap<>(StickerStatus.class);
        for (StickerStatus s : StickerStatus.values()) {
            counts.put(s, stickerRepository.countByStatus(s));
        }
        Map<String, Long> result = new HashMap<>();
        counts.forEach((k, v) -> result.put(k.name(), v));
        return result;
    }

    /**
     * Releases a live sticker (lost, stolen, abuse): it stops reaching the
     * owner and its vehicle is freed, exactly like an owner deactivation.
     */
    public void deactivate(UUID stickerId, AdminActor actor) {
        Sticker sticker = getEntity(stickerId);
        if (sticker.getStatus() != StickerStatus.BOUND) {
            throw new ConflictException("Only an active (bound) sticker can be deactivated");
        }
        UUID vehicleId = sticker.getVehicle() == null ? null : sticker.getVehicle().getId();
        sticker.unbind();
        Map<String, Object> details = new HashMap<>();
        details.put("from", StickerStatus.BOUND.name());
        details.put("to", StickerStatus.DEACTIVATED.name());
        if (vehicleId != null) {
            details.put("vehicleId", vehicleId.toString());
        }
        auditLogService.record("STICKER_DEACTIVATE", "STICKER", stickerId,
                actor.adminId(), actor.ipAddress(), actor.userAgent(), details);
    }

    private StickerStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return StickerStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Unknown status '" + status + "' (expected UNBOUND, BOUND or DEACTIVATED)");
        }
    }

    private Sticker getEntity(UUID stickerId) {
        return stickerRepository.findById(stickerId)
                .orElseThrow(() -> new NotFoundException("Sticker not found"));
    }
}
