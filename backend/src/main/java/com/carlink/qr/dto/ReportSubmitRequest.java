package com.carlink.qr.dto;

import com.carlink.admin.model.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for the anonymous moderation-report form on the public contact page.
 * The visitor reports the conversation their submission created.
 */
public record ReportSubmitRequest(
        @NotNull UUID conversationId,
        @NotNull ReportReason reason,
        @Size(max = 1000) String details
) {}