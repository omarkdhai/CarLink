package com.carlink.sticker.service;

import com.carlink.common.exception.BadRequestException;
import com.carlink.common.exception.ConflictException;
import com.carlink.common.exception.NotFoundException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.sticker.dto.ActivateStickerRequest;
import com.carlink.sticker.dto.StickerView;
import com.carlink.sticker.model.Sticker;
import com.carlink.sticker.model.StickerStatus;
import com.carlink.sticker.repository.StickerRepository;
import com.carlink.user.model.User;
import com.carlink.user.service.UserService;
import com.carlink.vehicle.model.Vehicle;
import com.carlink.vehicle.model.VehicleStatus;
import com.carlink.vehicle.repository.VehicleRepository;
import com.carlink.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Sticker ownership lifecycle: claiming a virgin sticker, releasing it, and
 * listing the acting user's stickers.
 *
 * <p>Ownership rules:</p>
 * <ul>
 *   <li>A sticker is claimed by exactly one user — a second claim of a BOUND
 *       sticker returns 409 without revealing who owns it.</li>
 *   <li>A user may own many stickers; each binds to one of their vehicles. The
 *       vehicle is reused by license plate when it exists, so a second sticker
 *       can attach to an already-registered car (both then resolve to the same
 *       contact).</li>
 *   <li>{@code DEACTIVATED} stickers are claimable again (e.g. after the owner
 *       sold the car).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StickerService {

    private final StickerRepository stickerRepository;
    private final VehicleRepository vehicleRepository;
    private final UserService userService;
    private final TokenGenerator tokenGenerator;

    /**
     * Claims a virgin (or previously deactivated) sticker for the acting user,
     * attaching it to one of their vehicles (reused by plate when possible).
     */
    @Transactional
    public void activate(UUID userId, String rawToken, ActivateStickerRequest request) {
        User user = userService.getUser(userId);
        // Row lock so two concurrent claims cannot both win.
        Sticker sticker = stickerRepository
                .findByTokenHashForUpdate(tokenGenerator.sha256(rawToken))
                .orElseThrow(() -> new NotFoundException("Sticker not found"));

        if (sticker.getStatus() == StickerStatus.BOUND) {
            // Never reveal who owns it — only that it is taken.
            throw new ConflictException("This sticker is already linked to an account.");
        }

        String plate = request.licensePlate().trim();
        Vehicle vehicle = vehicleRepository
                .findFirstByOwnerIdAndStatusAndLicensePlateIgnoreCaseOrderByCreatedAtDesc(
                        userId, VehicleStatus.ACTIVE, plate)
                .orElseGet(() -> createVehicle(user, request, plate));

        user.setBirthDate(request.birthDate());
        sticker.bind(user, vehicle);
        stickerRepository.save(sticker);
    }

    /** Releases a sticker the acting user owns; the vehicle becomes sticker-free. */
    @Transactional
    public void deactivate(UUID userId, UUID stickerId) {
        Sticker sticker = getOwned(userId, stickerId);
        sticker.unbind();
        stickerRepository.save(sticker);
    }

    /** The acting user's stickers (BOUND, DEACTIVATED, and legacy backfills). */
    public List<StickerView> listMine(UUID userId) {
        return stickerRepository.findByOwnerIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(StickerView::from)
                .toList();
    }

    /** Loads a sticker and treats any non-owned sticker as absent (404). */
    private Sticker getOwned(UUID userId, UUID stickerId) {
        Sticker sticker = stickerRepository.findById(stickerId)
                .orElseThrow(() -> new NotFoundException("Sticker not found"));
        if (sticker.getOwner() == null || !sticker.getOwner().getId().equals(userId)) {
            throw new NotFoundException("Sticker not found");
        }
        return sticker;
    }

    private Vehicle createVehicle(User owner, ActivateStickerRequest request, String plate) {
        long count = vehicleRepository.countByOwnerId(owner.getId());
        if (count >= VehicleService.MAX_VEHICLES_PER_OWNER) {
            throw new BadRequestException(
                    "Vehicle limit reached (" + VehicleService.MAX_VEHICLES_PER_OWNER + ")");
        }
        return vehicleRepository.save(Vehicle.newVehicle(
                owner,
                trimToNull(request.nickname()),
                trimToNull(request.brand()),
                trimToNull(request.model()),
                trimToNull(request.color()),
                plate));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}