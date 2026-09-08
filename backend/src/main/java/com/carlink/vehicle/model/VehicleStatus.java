package com.carlink.vehicle.model;

/**
 * Lifecycle of a registered vehicle. {@code ARCHIVED} removes a vehicle from
 * the owner's active list (and its QR codes from active rotation) without
 * deleting history.
 */
public enum VehicleStatus {
    ACTIVE,
    ARCHIVED
}
