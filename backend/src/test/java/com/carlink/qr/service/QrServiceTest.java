package com.carlink.qr.service;

import com.carlink.TestProperties;
import com.carlink.common.security.TokenGenerator;
import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.NotFoundException;
import com.carlink.qr.dto.QrIssuedResponse;
import com.carlink.qr.dto.QrStatusResponse;
import com.carlink.qr.model.QrCode;
import com.carlink.qr.repository.QrCodeRepository;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.vehicle.model.Vehicle;
import com.carlink.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QrServiceTest {

    @Mock private QrCodeRepository qrCodeRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private QrImageGenerator imageGenerator;

    private final TokenGenerator tokenGenerator = new TokenGenerator();
    private final CarLinkProperties properties = TestProperties.minimal();
    private QrService qrService;

    private final UUID ownerId = UUID.randomUUID();
    private final Vehicle vehicle = Vehicle.builder()
            .id(UUID.randomUUID())
            .owner(User.builder().id(ownerId).role(Role.USER).build())
            .licensePlate("AB-123-CD")
            .build();

    @BeforeEach
    void setUp() {
        qrService = new QrService(qrCodeRepository, vehicleRepository,
                tokenGenerator, imageGenerator, properties);
    }

    private QrCode activeCode() {
        return QrCode.newActive(vehicle, tokenGenerator.sha256("whatever"));
    }

    @Test
    void issueReturnsRawTokenAndPublicUrlAndPersistsOnlyHash() {
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(qrCodeRepository.findByVehicleIdAndActiveTrue(vehicle.getId()))
                .thenReturn(Optional.empty());
        when(imageGenerator.pngDataUri(anyString(), any(Integer.class)))
                .thenReturn("data:image/png;base64,AAA");
        when(qrCodeRepository.save(any(QrCode.class))).thenAnswer(inv -> inv.getArgument(0));

        QrIssuedResponse response = qrService.issue(ownerId, vehicle.getId());

        // Raw token shown once; the QR points at the backend's own page host.
        assertThat(response.rawToken()).isNotBlank();
        assertThat(response.publicUrl())
                .isEqualTo(properties.baseUrl() + "/c/" + response.rawToken());
        assertThat(response.imageDataUri()).startsWith("data:image/png;base64,");

        // Stored record hashes the token, never stores the raw value
        var captor = org.mockito.ArgumentCaptor.forClass(QrCode.class);
        verify(qrCodeRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash())
                .isEqualTo(tokenGenerator.sha256(response.rawToken()));
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void issueDeactivatesExistingActiveQr() {
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        QrCode existing = activeCode();
        when(qrCodeRepository.findByVehicleIdAndActiveTrue(vehicle.getId()))
                .thenReturn(Optional.of(existing));
        when(imageGenerator.pngDataUri(anyString(), any(Integer.class)))
                .thenReturn("data:image/png;base64,AAA");

        qrService.issue(ownerId, vehicle.getId());

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getDeactivatedAt()).isNotNull();
    }

    @Test
    void issueRejectsVehicleNotOwnedByCaller() {
        Vehicle otherVehicle = Vehicle.builder()
                .id(vehicle.getId())
                .owner(User.builder().id(UUID.randomUUID()).role(Role.USER).build())
                .licensePlate("AB-123-CD")
                .build();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(otherVehicle));

        assertThatThrownBy(() -> qrService.issue(ownerId, vehicle.getId()))
                .isInstanceOf(NotFoundException.class);
        verify(qrCodeRepository, never()).save(any(QrCode.class));
    }

    @Test
    void issueRejectsMissingVehicle() {
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> qrService.issue(ownerId, vehicle.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void currentReturnsActiveQrStatusWithoutToken() {
        QrCode code = activeCode();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(qrCodeRepository.findByVehicleIdAndActiveTrue(vehicle.getId()))
                .thenReturn(Optional.of(code));

        QrStatusResponse status = qrService.current(ownerId, vehicle.getId());

        assertThat(status.active()).isTrue();
        assertThat(status.id()).isEqualTo(code.getId());
        assertThat(status.vehicleId()).isEqualTo(vehicle.getId());
    }

    @Test
    void currentThrowsWhenNoActiveQr() {
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(qrCodeRepository.findByVehicleIdAndActiveTrue(vehicle.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> qrService.current(ownerId, vehicle.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No active QR");
    }

    @Test
    void historyReturnsAllCodesNewestFirst() {
        QrCode older = activeCode();
        older.setActivatedAt(Instant.now());
        QrCode newer = activeCode();
        newer.setActivatedAt(Instant.now().plusSeconds(10));
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(qrCodeRepository.findAllByVehicleId(vehicle.getId()))
                .thenReturn(List.of(older, newer));

        List<QrStatusResponse> history = qrService.history(ownerId, vehicle.getId());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).id()).isEqualTo(newer.getId());
        assertThat(history.get(1).id()).isEqualTo(older.getId());
    }

    @Test
    void deactivateDeactivatesActiveQr() {
        QrCode code = activeCode();
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(qrCodeRepository.findByVehicleIdAndActiveTrue(vehicle.getId()))
                .thenReturn(Optional.of(code));

        qrService.deactivate(ownerId, vehicle.getId());

        assertThat(code.isActive()).isFalse();
    }

    @Test
    void deactivateIsIdempotentWhenNoActiveQr() {
        when(vehicleRepository.findById(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(qrCodeRepository.findByVehicleIdAndActiveTrue(vehicle.getId()))
                .thenReturn(Optional.empty());

        qrService.deactivate(ownerId, vehicle.getId()); // does not throw
    }
}