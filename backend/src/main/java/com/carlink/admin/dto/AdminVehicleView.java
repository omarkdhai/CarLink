package com.carlink.admin.dto;

import com.carlink.user.model.User;
import com.carlink.vehicle.model.Vehicle;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin-side view of a vehicle with its owner and activity counters.
 * Deliberately excludes the owner's {@code phone}, sticker tokens and any
 * message content — only identity fields and aggregate counts.
 */
public record AdminVehicleView(
        UUID id,
        String nickname,
        String brand,
        String model,
        String color,
        String licensePlate,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Owner owner,
        long stickerCount,
        long conversationCount,
        long unreadCount,
        Instant lastContactAt
) {

    public record Owner(UUID id, String firstName, String lastName, String email, boolean active) {

        static Owner from(User user) {
            return new Owner(user.getId(), user.getFirstName(), user.getLastName(),
                    user.getEmail(), user.isActive());
        }
    }

    /** Aggregate counters for one vehicle; {@link #EMPTY} when it has no activity. */
    public record Activity(long stickerCount, long conversationCount, long unreadCount,
                           Instant lastContactAt) {

        public static final Activity EMPTY = new Activity(0, 0, 0, null);
    }

    public static AdminVehicleView from(Vehicle vehicle, Activity activity) {
        return new AdminVehicleView(
                vehicle.getId(),
                vehicle.getNickname(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getColor(),
                vehicle.getLicensePlate(),
                vehicle.getStatus().name(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt(),
                Owner.from(vehicle.getOwner()),
                activity.stickerCount(),
                activity.conversationCount(),
                activity.unreadCount(),
                activity.lastContactAt());
    }
}
