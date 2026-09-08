package com.carlink.admin.service;

import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.dto.AdminUserResponse;
import com.carlink.common.exception.ForbiddenException;
import com.carlink.common.exception.NotFoundException;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import com.carlink.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin user management: search, activate/deactivate, promote/demote.
 * Guards: an admin never deactivates or demotes themselves, and the last
 * administrator can never be demoted. Every state change is audit-logged.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list(String q, Boolean active, Role role) {
        return userRepository.search(q, active, role).stream()
                .map(u -> AdminUserResponse.from(u, vehicleRepository.countByOwnerId(u.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID userId) {
        User user = getEntity(userId);
        return AdminUserResponse.from(user, vehicleRepository.countByOwnerId(user.getId()));
    }

    public void deactivate(UUID userId, AdminActor actor) {
        User user = getEntity(userId);
        if (user.getId().equals(actor.adminId())) {
            throw new ForbiddenException("You cannot deactivate your own account");
        }
        user.setActive(false);
        auditLogService.record("USER_DEACTIVATE", "USER", userId,
                actor.adminId(), actor.ipAddress(), actor.userAgent(), Map.of("action", "deactivate"));
    }

    public void activate(UUID userId, AdminActor actor) {
        getEntity(userId).setActive(true);
        auditLogService.record("USER_ACTIVATE", "USER", userId,
                actor.adminId(), actor.ipAddress(), actor.userAgent(), Map.of("action", "activate"));
    }

    public void changeRole(UUID userId, Role newRole, AdminActor actor) {
        User user = getEntity(userId);
        if (user.getId().equals(actor.adminId()) && newRole == Role.USER) {
            throw new ForbiddenException("You cannot demote your own role");
        }
        Role from = user.getRole();
        if (from == Role.ADMIN && newRole == Role.USER
                && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new ForbiddenException("Cannot demote the last administrator");
        }
        user.setRole(newRole);
        auditLogService.record("USER_ROLE_CHANGE", "USER", userId,
                actor.adminId(), actor.ipAddress(), actor.userAgent(),
                Map.of("from", from.name(), "to", newRole.name()));
    }

    private User getEntity(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}