package com.carlink.vehicle.model;

import com.carlink.common.entity.BaseEntity;
import com.carlink.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A vehicle owned by a {@link User}. Each vehicle can carry an active QR code
 * that lets passers-by contact the owner without exposing the owner's phone.
 *
 * <p>All write operations must verify that the acting user is the {@code owner}
 * (or an admin) — see {@code VehicleService}.</p>
 */
@Entity
@Table(name = "vehicles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle extends BaseEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(length = 100)
    private String nickname;

    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String model;

    @Column(length = 50)
    private String color;

    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status;

    public static Vehicle newVehicle(User owner, String nickname, String brand, String model,
                                     String color, String licensePlate) {
        return Vehicle.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .nickname(nickname)
                .brand(brand)
                .model(model)
                .color(color)
                .licensePlate(licensePlate)
                .status(VehicleStatus.ACTIVE)
                .build();
    }
}
