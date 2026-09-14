package com.carlink.admin.dto;

import com.carlink.user.model.Role;
import jakarta.validation.constraints.NotNull;

/** Target role for an admin promotion/demotion. */
public record ChangeRoleRequest(@NotNull Role role) {}