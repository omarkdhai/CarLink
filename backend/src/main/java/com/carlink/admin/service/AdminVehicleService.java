package com.carlink.admin.service;

import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.dto.AdminVehicleView;
import com.carlink.admin.dto.AdminVehicleView.Activity;
import com.carlink.common.exception.BadRequestException;
import com.carlink.common.exception.ConflictException;
import com.carlink.common.exception.NotFoundException;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.sticker.repository.StickerRepository;
import com.carlink.vehicle.model.Vehicle;
import com.carlink.vehicle.model.VehicleStatus;
import com.carlink.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin fleet management across every owner: search, inspect, archive and
 * restore. Activity counters are loaded in two grouped queries per page (no
 * N+1). Every state change is audit-logged.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminVehicleService {

    private final VehicleRepository vehicleRepository;
    private final StickerRepository stickerRepository;
    private final ConversationRepository conversationRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<AdminVehicleView> list(String q, String status, Pageable pageable) {
        String term = q == null || q.isBlank() ? null : q.trim();
        Page<Vehicle> page = vehicleRepository.adminSearch(term, parseStatus(status), pageable);
        Map<UUID, Activity> activity = activity(page.map(Vehicle::getId).getContent());
        return page.map(v -> AdminVehicleView.from(v, activity.getOrDefault(v.getId(), Activity.EMPTY)));
    }

    @Transactional(readOnly = true)
    public AdminVehicleView get(UUID vehicleId) {
        Vehicle vehicle = getEntity(vehicleId);
        return AdminVehicleView.from(vehicle,
                activity(List.of(vehicleId)).getOrDefault(vehicleId, Activity.EMPTY));
    }

    public void archive(UUID vehicleId, AdminActor actor) {
        changeStatus(vehicleId, VehicleStatus.ARCHIVED, "VEHICLE_ARCHIVE", actor);
    }

    public void restore(UUID vehicleId, AdminActor actor) {
        changeStatus(vehicleId, VehicleStatus.ACTIVE, "VEHICLE_RESTORE", actor);
    }

    private void changeStatus(UUID vehicleId, VehicleStatus target, String action, AdminActor actor) {
        Vehicle vehicle = getEntity(vehicleId);
        VehicleStatus from = vehicle.getStatus();
        if (from == target) {
            throw new ConflictException("Vehicle is already " + target.name().toLowerCase());
        }
        vehicle.setStatus(target);
        auditLogService.record(action, "VEHICLE", vehicleId,
                actor.adminId(), actor.ipAddress(), actor.userAgent(),
                Map.of("from", from.name(), "to", target.name()));
    }

    private Map<UUID, Activity> activity(List<UUID> vehicleIds) {
        if (vehicleIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> stickers = new HashMap<>();
        for (Object[] row : stickerRepository.countBoundByVehicleIds(vehicleIds)) {
            stickers.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        Map<UUID, Activity> result = new HashMap<>();
        for (Object[] row : conversationRepository.activityByVehicleIds(vehicleIds)) {
            UUID id = (UUID) row[0];
            result.put(id, new Activity(stickers.getOrDefault(id, 0L),
                    ((Number) row[1]).longValue(),
                    row[2] == null ? 0 : ((Number) row[2]).longValue(),
                    (Instant) row[3]));
        }
        stickers.forEach((id, count) ->
                result.putIfAbsent(id, new Activity(count, 0, 0, null)));
        return result;
    }

    private VehicleStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return VehicleStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Unknown status '" + status + "' (expected ACTIVE or ARCHIVED)");
        }
    }

    private Vehicle getEntity(UUID vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));
    }
}
