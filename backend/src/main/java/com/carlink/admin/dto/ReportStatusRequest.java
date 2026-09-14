package com.carlink.admin.dto;

import com.carlink.admin.model.ReportStatus;
import jakarta.validation.constraints.NotNull;

/** Target state for a moderation report transition. */
public record ReportStatusRequest(@NotNull ReportStatus status) {}