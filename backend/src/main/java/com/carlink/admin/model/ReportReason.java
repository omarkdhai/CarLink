package com.carlink.admin.model;

/**
 * Why a visitor filed a report against a conversation. Stored as a string in
 * {@code reports.reason} with a DB check constraint.
 */
public enum ReportReason {
    SPAM,
    ABUSE,
    OTHER
}