package com.carlink.sticker.model;

import com.carlink.common.entity.BaseEntity;
import com.carlink.order.model.Order;
import com.carlink.user.model.User;
import com.carlink.vehicle.model.Vehicle;
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

import java.time.Instant;
import java.util.UUID;

/**
 * A physical QR sticker. Lifecycle: {@code UNBOUND} → {@code BOUND} →
 * {@code DEACTIVATED} (and {@code DEACTIVATED} → {@code BOUND} again on
 * re-claim). A sticker is owned by exactly one user and linked to one of their
 * vehicles.
 *
 * <p>Only {@code tokenHash} is stored — the raw token is returned to the buyer
 * exactly once at order creation and never persisted. The printed QR is fixed,
 * so stickers are never "regenerated"; the control for a lost or sold sticker
 * is {@code deactivate()}.</p>
 */
@Entity
@Table(name = "stickers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sticker extends BaseEntity {

    @Id
    private UUID id;

    /** The order this sticker came with; {@code null} for legacy backfilled stickers. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StickerStatus status;

    @Column(name = "bound_at")
    private Instant boundAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    /** A virgin (sold but unclaimed) sticker. The raw token is NOT stored. */
    public static Sticker newSticker(Order order, String tokenHash) {
        return Sticker.builder()
                .id(UUID.randomUUID())
                .order(order)
                .tokenHash(tokenHash)
                .status(StickerStatus.UNBOUND)
                .build();
    }

    /** Claims this sticker for {@code owner} and attaches it to a vehicle. */
    public void bind(User owner, Vehicle vehicle) {
        this.owner = owner;
        this.vehicle = vehicle;
        this.status = StickerStatus.BOUND;
        this.boundAt = Instant.now();
        this.deactivatedAt = null;
    }

    /** Releases the sticker; it becomes claimable again. The vehicle is freed. */
    public void unbind() {
        this.vehicle = null;
        this.status = StickerStatus.DEACTIVATED;
        this.deactivatedAt = Instant.now();
    }
}