package com.carlink.admin.dto;

import com.carlink.order.model.Order;
import com.carlink.sticker.model.Sticker;
import com.carlink.user.model.User;
import com.carlink.vehicle.model.Vehicle;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin-side view of a QR sticker with its order, owner and vehicle.
 * Never carries the token or activation-code hashes, the owner's phone, or
 * the buyer's mobile/address — only what an operator needs to identify it.
 */
public record AdminStickerView(
        UUID id,
        String status,
        boolean legacy,
        Instant createdAt,
        Instant boundAt,
        Instant deactivatedAt,
        OrderRef order,
        Owner owner,
        VehicleRef vehicle
) {

    public record OrderRef(String reference, String customerName, String status, Instant createdAt) {

        static OrderRef from(Order o) {
            return o == null ? null
                    : new OrderRef(o.getReference(), o.getCustomerName(), o.getStatus().name(), o.getCreatedAt());
        }
    }

    public record Owner(UUID id, String firstName, String lastName, String email, boolean active) {

        static Owner from(User u) {
            return u == null ? null
                    : new Owner(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.isActive());
        }
    }

    public record VehicleRef(UUID id, String nickname, String brand, String model, String color,
                             String licensePlate, String status) {

        static VehicleRef from(Vehicle v) {
            return v == null ? null
                    : new VehicleRef(v.getId(), v.getNickname(), v.getBrand(), v.getModel(), v.getColor(),
                            v.getLicensePlate(), v.getStatus().name());
        }
    }

    public static AdminStickerView from(Sticker s) {
        return new AdminStickerView(
                s.getId(),
                s.getStatus().name(),
                s.getActivationCodeHash() == null,
                s.getCreatedAt(),
                s.getBoundAt(),
                s.getDeactivatedAt(),
                OrderRef.from(s.getOrder()),
                Owner.from(s.getOwner()),
                VehicleRef.from(s.getVehicle()));
    }
}
