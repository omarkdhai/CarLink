package com.carlink.admin.dto;

/**
 * Platform-level counters for the admin analytics overview.
 */
public record AdminStatsResponse(
        long usersTotal,
        long usersActive,
        long usersAdmins,
        long vehiclesTotal,
        long vehiclesActive,
        long qrTotal,
        long qrActive,
        long conversationsTotal,
        long conversationsPending,
        long conversationsSent,
        long conversationsFailed,
        long conversationsExpired,
        long messagesTotal,
        long reportsOpen,
        long reportsReviewed,
        long reportsClosed,
        double reportsOpenVsTotal
) {}