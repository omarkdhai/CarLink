package com.carlink.qr.model;

import com.carlink.common.entity.BaseEntity;
import com.carlink.vehicle.model.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * An active or deactivated QR token linked to a {@link Vehicle}.
 *
 * <p>Only {@code tokenHash} is stored — the raw token is returned to the
 * caller exactly once at generation and never persisted.</p>
 *
 * <p>Business rules (enforced by the DB partial unique index + service):</p>
 * <ul>
 *   <li>At most one ACTIVE QR per vehicle.</li>
 *   <li>When a new QR is generated, the previous one is deactivated.</li>
 * </ul>
 */
@Entity
@Table(name = "qr_codes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrCode extends BaseEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    /**
     * Creates a new active QR record. The raw token is NOT stored — the
     * caller must return it to the user exactly once.
     */
    public static QrCode newActive(Vehicle vehicle, String tokenHash) {
        return QrCode.builder()
                .id(UUID.randomUUID())
                .vehicle(vehicle)
                .tokenHash(tokenHash)
                .active(true)
                .activatedAt(Instant.now())
                .build();
    }

    /** Marks the QR as inactive and records the deactivation timestamp. */
    public void deactivate() {
        this.active = false;
        this.deactivatedAt = Instant.now();
    }
}
