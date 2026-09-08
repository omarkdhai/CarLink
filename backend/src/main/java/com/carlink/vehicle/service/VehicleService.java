package com.carlink.vehicle.service;

import com.carlink.common.exception.BadRequestException;
import com.carlink.common.exception.NotFoundException;
import com.carlink.user.model.User;
import com.carlink.user.service.UserService;
import com.carlink.vehicle.dto.CreateVehicleRequest;
import com.carlink.vehicle.dto.UpdateVehicleRequest;
import com.carlink.vehicle.dto.VehicleResponse;
import com.carlink.vehicle.model.Vehicle;
import com.carlink.vehicle.model.VehicleStatus;
import com.carlink.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Vehicle management. Every read and write is scoped to the acting owner:
 * a vehicle can only be seen or modified by the user who owns it (admins
 * manage vehicles through the admin module, not here).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserService userService;

    /** Max vehicles per owner — a soft cap to keep the dashboard usable. */
    public static final int MAX_VEHICLES_PER_OWNER = 50;

    public List<VehicleResponse> list(UUID ownerId, String status) {
        // The default view is the owner's active fleet; archived vehicles are
        // only shown when explicitly requested with status=ARCHIVED.
        VehicleStatus effective;
        if (status == null || status.isBlank()) {
            effective = VehicleStatus.ACTIVE;
        } else {
            try {
                effective = VehicleStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(
                        "Unknown status '" + status + "' (expected ACTIVE or ARCHIVED)");
            }
        }
        return vehicleRepository.findAllByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, effective)
                .stream()
                .map(VehicleResponse::from)
                .toList();
    }

    public VehicleResponse get(UUID ownerId, UUID vehicleId) {
        return VehicleResponse.from(getOwned(ownerId, vehicleId));
    }

    @Transactional
    public VehicleResponse create(UUID ownerId, CreateVehicleRequest request) {
        User owner = userService.getUser(ownerId);
        long count = vehicleRepository.countByOwnerId(ownerId);
        if (count >= MAX_VEHICLES_PER_OWNER) {
            throw new BadRequestException(
                    "Vehicle limit reached (" + MAX_VEHICLES_PER_OWNER + ")");
        }
        Vehicle vehicle = Vehicle.newVehicle(
                owner,
                trimToNull(request.nickname()),
                trimToNull(request.brand()),
                trimToNull(request.model()),
                trimToNull(request.color()),
                request.licensePlate().trim());
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponse update(UUID ownerId, UUID vehicleId, UpdateVehicleRequest request) {
        Vehicle vehicle = getOwned(ownerId, vehicleId);
        if (request.nickname() != null) {
            vehicle.setNickname(trimToNull(request.nickname()));
        }
        if (request.brand() != null) {
            vehicle.setBrand(trimToNull(request.brand()));
        }
        if (request.model() != null) {
            vehicle.setModel(trimToNull(request.model()));
        }
        if (request.color() != null) {
            vehicle.setColor(trimToNull(request.color()));
        }
        if (request.licensePlate() != null) {
            String plate = request.licensePlate().trim();
            if (plate.isEmpty()) {
                throw new BadRequestException("licensePlate cannot be empty");
            }
            vehicle.setLicensePlate(plate);
        }
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void archive(UUID ownerId, UUID vehicleId) {
        Vehicle vehicle = getOwned(ownerId, vehicleId);
        vehicle.setStatus(VehicleStatus.ARCHIVED);
        vehicleRepository.save(vehicle);
    }

    @Transactional
    public void delete(UUID ownerId, UUID vehicleId) {
        Vehicle vehicle = getOwned(ownerId, vehicleId);
        vehicleRepository.delete(vehicle);
    }

    /**
     * Loads a vehicle and asserts the acting user owns it. Any vehicle the
     * user does not own is treated as absent (404) so a cross-owner probe
     * leaks nothing about whether it exists. Public so other services (e.g.
     * conversation ownership checks) reuse the same scoping primitive.
     */
    public Vehicle getOwned(UUID ownerId, UUID vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle not found"));
        if (!vehicle.getOwner().getId().equals(ownerId)) {
            throw new NotFoundException("Vehicle not found");
        }
        return vehicle;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
