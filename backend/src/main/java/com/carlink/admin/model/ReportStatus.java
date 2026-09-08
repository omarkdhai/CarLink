package com.carlink.admin.model;

/**
 * Lifecycle of a moderation report: OPEN (queued), REVIEWED (moderator saw
 * it), CLOSED (resolved/no action). Stored as a string in {@code reports.status}
 * with a DB check constraint.
 */
public enum ReportStatus {
    OPEN,
    REVIEWED,
    CLOSED
}