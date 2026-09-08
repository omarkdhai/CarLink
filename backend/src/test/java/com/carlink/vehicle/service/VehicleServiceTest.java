package com.carlink.vehicle.service;

import com.carlink.common.exception.BadRequestException;
import com.carlink.common.exception.NotFoundException;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.service.UserService;
import com.carlink.vehicle.dto.CreateVehicleRequest;
import com.carlink.vehicle.dto.UpdateVehicleRequest;
import com.carlink.vehicle.dto.VehicleResponse;
import com.carlink.vehicle.model.Vehicle;
import com.carlink.vehicle.model.VehicleStatus;
import com.carlink.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class VehicleServiceTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private UserService userService;

    private VehicleService vehicleService;

    private final UUID ownerId = UUID.randomUUID();
    private final User owner = User.builder()
            .id(ownerId).email("owner@example.com").role(Role.USER)
            .build();

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(vehicleRepository, userService);
    }

    private Vehicle ownedVehicle() {
        return Vehicle.newVehicle(owner, "My Car", "Toyota", "Corolla", "Blue", "AB-123-CD");
    }

    // ---------- Create ----------

    @Test
    void createBuildsActiveVehicleAndSaves() {
        when(userService.getUser(ownerId)).thenReturn(owner);
        when(vehicleRepository.countByOwnerId(ownerId)).thenReturn(0L);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        VehicleResponse response = vehicleService.create(ownerId,
                new CreateVehicleRequest("My Car", "Toyota", "Corolla", "Blue", " AB-123-CD "));

        assertThat(response.licensePlate()).isEqualTo("AB-123-CD"); // trimmed
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    void createTrimsBlankOptionalFieldsToNull() {
        when(userService.getUser(ownerId)).thenReturn(owner);
        when(vehicleRepository.countByOwnerId(ownerId)).thenReturn(0L);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        VehicleResponse response = vehicleService.create(ownerId,
                new CreateVehicleRequest("   ", "  ", "Corolla", "", "AB-123-CD"));

        assertThat(response.nickname()).isNull();
        assertThat(response.brand()).isNull();
        assertThat(response.model()).isEqualTo("Corolla");
        assertThat(response.color()).isNull();
    }

    @Test
    void createRejectsWhenVehicleLimitReached() {
        when(userService.getUser(ownerId)).thenReturn(owner);
        when(vehicleRepository.countByOwnerId(ownerId))
                .thenReturn((long) VehicleService.MAX_VEHICLES_PER_OWNER);

        assertThatThrownBy(() -> vehicleService.create(ownerId,
                new CreateVehicleRequest("X", "Toyota", "Corolla", "Blue", "AB-123-CD")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limit");
        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    // ---------- Get / ownership ----------

    @Test
    void getReturnsOwnVehicle() {
        Vehicle vehicle = ownedVehicle();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        VehicleResponse response = vehicleService.get(ownerId, vehicle.getId());

        assertThat(response.id()).isEqualTo(vehicle.getId());
    }

    @Test
    void getTreatsOtherOwnersVehicleAsNotFound() {
        Vehicle vehicle = ownedVehicle();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.get(UUID.randomUUID(), vehicle.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getTreatsMissingVehicleAsNotFound() {
        when(vehicleRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.get(ownerId, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- Update ----------

    @Test
    void updateAppliesProvidedFieldsOnly() {
        Vehicle vehicle = ownedVehicle();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        VehicleResponse response = vehicleService.update(ownerId, vehicle.getId(),
                new UpdateVehicleRequest(null, null, "Camry", "Red", null));

        assertThat(response.model()).isEqualTo("Camry");
        assertThat(response.color()).isEqualTo("Red");
        assertThat(response.brand()).isEqualTo("Toyota"); // untouched
        assertThat(response.licensePlate()).isEqualTo("AB-123-CD"); // untouched
    }

    @Test
    void updateRejectsEmptyLicensePlate() {
        Vehicle vehicle = ownedVehicle();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.update(ownerId, vehicle.getId(),
                new UpdateVehicleRequest(null, null, null, null, "  ")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateRejectsOtherOwnersVehicle() {
        Vehicle vehicle = ownedVehicle();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.update(UUID.randomUUID(), vehicle.getId(),
                new UpdateVehicleRequest("hax", null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- Archive / delete ----------

    @Test
    void archiveMarksVehicleArchived() {
        Vehicle vehicle = ownedVehicle();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        vehicleService.archive(ownerId, vehicle.getId());

        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.ARCHIVED);
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void deleteRemovesOwnVehicle() {
        Vehicle vehicle = ownedVehicle();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        vehicleService.delete(ownerId, vehicle.getId());

        verify(vehicleRepository).delete(vehicle);
    }

    @Test
    void deleteRejectsOtherOwnersVehicle() {
        Vehicle vehicle = ownedVehicle();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.delete(UUID.randomUUID(), vehicle.getId()))
                .isInstanceOf(NotFoundException.class);
        verify(vehicleRepository, never()).delete(any(Vehicle.class));
    }

    // ---------- List ----------

    @Test
    void listWithoutFilterDefaultsToActiveOnly() {
        when(vehicleRepository.findAllByOwnerIdAndStatusOrderByCreatedAtDesc(
                ownerId, VehicleStatus.ACTIVE)).thenReturn(List.of(ownedVehicle()));

        List<VehicleResponse> list = vehicleService.list(ownerId, null);

        assertThat(list).hasSize(1);
        verify(vehicleRepository)
                .findAllByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, VehicleStatus.ACTIVE);
    }

    @Test
    void listWithExplicitArchivedFilterUsesThatStatus() {
        when(vehicleRepository.findAllByOwnerIdAndStatusOrderByCreatedAtDesc(
                ownerId, VehicleStatus.ARCHIVED)).thenReturn(List.of());

        List<VehicleResponse> list = vehicleService.list(ownerId, "archived");

        assertThat(list).isEmpty();
        verify(vehicleRepository)
                .findAllByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, VehicleStatus.ARCHIVED);
    }

    @Test
    void listRejectsUnknownStatus() {
        assertThatThrownBy(() -> vehicleService.list(ownerId, "HACKED"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown status");
    }
}
