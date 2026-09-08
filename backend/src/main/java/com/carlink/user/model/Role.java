package com.carlink.user.model;

/**
 * User roles. Stored as a string in {@code users.role} with a DB check
 * constraint; the server (never the frontend) decides authorization.
 */
public enum Role {
    USER,
    ADMIN
}