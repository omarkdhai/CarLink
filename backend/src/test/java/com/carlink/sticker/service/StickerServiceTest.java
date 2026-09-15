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
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.service.UserService;
import com.carlink.vehicle.model.Vehicle;
import com.carlink.vehicle.model.VehicleStatus;
import com.carlink.vehicle.repository.VehicleRepository;
import com.carlink.vehicle.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StickerServiceTest {

    @Mock private StickerRepository stickerRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private UserService userService;

    private final TokenGenerator tokenGenerator = new TokenGenerator();
    private StickerService stickerService;

    private final UUID userId = UUID.randomUUID();
    private final User user = User.builder()
            .id(userId).email("owner@example.com").role(Role.USER).build();
    private final String rawToken = "sticker-token-abc";
    private final ActivateStickerRequest request = new ActivateStickerRequest(
            LocalDate.of(1990, 5, 4), "AB-123-CD", "My Car", "Toyota", "Corolla", "Blue");

    @BeforeEach
    void setUp() {
        stickerService = new StickerService(stickerRepository, vehicleRepository,
                userService, tokenGenerator);
    }

    private Sticker virginSticker() {
        return Sticker.newSticker(null, tokenGenerator.sha256(rawToken));
    }

    private Vehicle vehicle(String plate) {
        return Vehicle.newVehicle(user, "My Car", "Toyota", "Corolla", "Blue", plate);
    }

    // ---------- activate ----------

    @Test
    void activateReusesExistingVehicleByPlateAndCapturesBirthDate() {
        when(userService.getUser(userId)).thenReturn(user);
        when(stickerRepository.findByTokenHashForUpdate(tokenGenerator.sha256(rawToken)))
                .thenReturn(Optional.of(virginSticker()));
        when(vehicleRepository.findFirstByOwnerIdAndStatusAndLicensePlateIgnoreCaseOrderByCreatedAtDesc(
                userId, VehicleStatus.ACTIVE, "AB-123-CD")).thenReturn(Optional.of(vehicle("AB-123-CD")));

        stickerService.activate(userId, rawToken, request);

        assertThat(user.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 4));
        verify(stickerRepository).save(any(Sticker.class));
        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    @Test
    void activateCreatesNewVehicleWhenPlateIsNotRegistered() {
        when(userService.getUser(userId)).thenReturn(user);
        when(stickerRepository.findByTokenHashForUpdate(tokenGenerator.sha256(rawToken)))
                .thenReturn(Optional.of(virginSticker()));
        when(vehicleRepository.findFirstByOwnerIdAndStatusAndLicensePlateIgnoreCaseOrderByCreatedAtDesc(
                userId, VehicleStatus.ACTIVE, "AB-123-CD")).thenReturn(Optional.empty());
        when(vehicleRepository.countByOwnerId(userId)).thenReturn(0L);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        stickerService.activate(userId, rawToken, request);

        ArgumentCaptor<Vehicle> savedVehicle = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(savedVehicle.capture());
        assertThat(savedVehicle.getValue().getLicensePlate()).isEqualTo("AB-123-CD");
        assertThat(savedVehicle.getValue().getStatus()).isEqualTo(VehicleStatus.ACTIVE);
    }

    @Test
    void activateRejectsBoundStickerWithConflictAndNeverRevealsOwner() {
        when(userService.getUser(userId)).thenReturn(user);
        Sticker bound = virginSticker();
        bound.bind(user, vehicle("AB-123-CD"));
        when(stickerRepository.findByTokenHashForUpdate(tokenGenerator.sha256(rawToken)))
                .thenReturn(Optional.of(bound));

        assertThatThrownBy(() -> stickerService.activate(userId, rawToken, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already linked");
        verify(stickerRepository, never()).save(any());
        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    @Test
    void activateAllowsReclaimingADeactivatedSticker() {
        when(userService.getUser(userId)).thenReturn(user);
        Sticker deactivated = virginSticker();
        deactivated.bind(user, vehicle("OLD-111"));
        deactivated.unbind();
        when(stickerRepository.findByTokenHashForUpdate(tokenGenerator.sha256(rawToken)))
                .thenReturn(Optional.of(deactivated));
        when(vehicleRepository.findFirstByOwnerIdAndStatusAndLicensePlateIgnoreCaseOrderByCreatedAtDesc(
                userId, VehicleStatus.ACTIVE, "AB-123-CD")).thenReturn(Optional.of(vehicle("AB-123-CD")));

        stickerService.activate(userId, rawToken, request);

        assertThat(deactivated.getStatus()).isEqualTo(StickerStatus.BOUND);
        assertThat(deactivated.getBoundAt()).isNotNull();
        assertThat(deactivated.getDeactivatedAt()).isNull();
        verify(stickerRepository).save(deactivated);
    }

    @Test
    void activateNotFoundForUnknownToken() {
        when(userService.getUser(userId)).thenReturn(user);
        when(stickerRepository.findByTokenHashForUpdate(tokenGenerator.sha256(rawToken)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> stickerService.activate(userId, rawToken, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void activateRejectsWhenVehicleLimitReached() {
        when(userService.getUser(userId)).thenReturn(user);
        when(stickerRepository.findByTokenHashForUpdate(tokenGenerator.sha256(rawToken)))
                .thenReturn(Optional.of(virginSticker()));
        when(vehicleRepository.findFirstByOwnerIdAndStatusAndLicensePlateIgnoreCaseOrderByCreatedAtDesc(
                userId, VehicleStatus.ACTIVE, "AB-123-CD")).thenReturn(Optional.empty());
        when(vehicleRepository.countByOwnerId(userId))
                .thenReturn((long) VehicleService.MAX_VEHICLES_PER_OWNER);

        assertThatThrownBy(() -> stickerService.activate(userId, rawToken, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limit");
    }

    // ---------- deactivate ----------

    @Test
    void deactivateUnbindsOwnedStickerAndFreesTheVehicle() {
        Sticker owned = virginSticker();
        owned.bind(user, vehicle("AB-123-CD"));
        when(stickerRepository.findById(owned.getId())).thenReturn(Optional.of(owned));

        stickerService.deactivate(userId, owned.getId());

        assertThat(owned.getStatus()).isEqualTo(StickerStatus.DEACTIVATED);
        assertThat(owned.getVehicle()).isNull();
        assertThat(owned.getDeactivatedAt()).isNotNull();
        verify(stickerRepository).save(owned);
    }

    @Test
    void deactivateTreatsOtherOwnersStickerAs404() {
        User other = User.builder().id(UUID.randomUUID()).email("other@example.com")
                .role(Role.USER).build();
        Sticker owned = virginSticker();
        owned.bind(other, vehicle("AB-123-CD"));
        when(stickerRepository.findById(owned.getId())).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> stickerService.deactivate(userId, owned.getId()))
                .isInstanceOf(NotFoundException.class);
        verify(stickerRepository, never()).save(any());
    }

    // ---------- list ----------

    @Test
    void listMineReturnsOwnedStickerViewsWithoutRawTokens() {
        Sticker owned = virginSticker();
        owned.bind(user, vehicle("AB-123-CD"));
        when(stickerRepository.findByOwnerIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(owned));

        List<StickerView> views = stickerService.listMine(userId);

        assertThat(views).hasSize(1);
        StickerView view = views.get(0);
        assertThat(view.status()).isEqualTo("BOUND");
        assertThat(view.vehicle().licensePlate()).isEqualTo("AB-123-CD");
        // The raw token exists nowhere in the owner-facing view.
        assertThat(view.toString()).doesNotContain(rawToken);
    }
}