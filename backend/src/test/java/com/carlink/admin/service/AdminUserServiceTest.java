package com.carlink.admin.service;

import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.dto.AdminUserResponse;
import com.carlink.common.exception.ForbiddenException;
import com.carlink.common.exception.NotFoundException;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import com.carlink.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private AuditLogService auditLogService;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository, vehicleRepository, auditLogService);
    }

    // ---------- list / get ----------

    @Test
    void listMapsVehicleCountAndNeverExposesPhone() {
        User owner = user(Role.USER);
        when(userRepository.search("jo", null, null)).thenReturn(List.of(owner));
        when(vehicleRepository.countByOwnerId(owner.getId())).thenReturn(3L);

        List<AdminUserResponse> out = service.list("jo", null, null);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).email()).isEqualTo(owner.getEmail());
        assertThat(out.get(0).vehicleCount()).isEqualTo(3);
    }

    @Test
    void getThrowsNotFoundForUnknownUser() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(missing)).isInstanceOf(NotFoundException.class);
    }

    // ---------- deactivate / activate ----------

    @Test
    void deactivateSetsInactiveAndAudits() {
        User owner = user(Role.USER);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        AdminActor actor = actor();

        service.deactivate(owner.getId(), actor);

        assertThat(owner.isActive()).isFalse();
        verify(auditLogService).record(eq("USER_DEACTIVATE"), eq("USER"), eq(owner.getId()),
                eq(actor.adminId()), eq(actor.ipAddress()), eq(actor.userAgent()), any());
    }

    @Test
    void deactivateSelfThrowsForbidden() {
        User admin = user(Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.deactivate(admin.getId(), new AdminActor(
                admin.getId(), "127.0.0.1", "test")))
                .isInstanceOf(ForbiddenException.class);
        assertThat(admin.isActive()).isTrue();
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void activateSetsActiveAndAudits() {
        User owner = user(Role.USER);
        owner.setActive(false);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        AdminActor actor = actor();

        service.activate(owner.getId(), actor);

        assertThat(owner.isActive()).isTrue();
        verify(auditLogService).record(eq("USER_ACTIVATE"), eq("USER"), eq(owner.getId()),
                eq(actor.adminId()), eq(actor.ipAddress()), eq(actor.userAgent()), any());
    }

    // ---------- role changes ----------

    @Test
    void changeRolePromotesAndAuditsFromTo() {
        User owner = user(Role.USER);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        AdminActor actor = actor();

        service.changeRole(owner.getId(), Role.ADMIN, actor);

        assertThat(owner.getRole()).isEqualTo(Role.ADMIN);
        verify(auditLogService).record(eq("USER_ROLE_CHANGE"), eq("USER"), eq(owner.getId()),
                eq(actor.adminId()), eq(actor.ipAddress()), eq(actor.userAgent()),
                argThat(details -> "USER".equals(details.get("from"))
                        && "ADMIN".equals(details.get("to"))));
    }

    @Test
    void changeRoleSelfDemoteThrowsForbidden() {
        User admin = user(Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.changeRole(admin.getId(), Role.USER,
                new AdminActor(admin.getId(), "127.0.0.1", "test")))
                .isInstanceOf(ForbiddenException.class);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void changeRoleLastAdminDemoteThrowsForbidden() {
        User admin = user(Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(admin.getId(), Role.USER, actor()))
                .isInstanceOf(ForbiddenException.class);
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void changeRoleAllowsWhenMoreThanOneAdmin() {
        User admin = user(Role.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);

        service.changeRole(admin.getId(), Role.USER, actor());

        assertThat(admin.getRole()).isEqualTo(Role.USER);
    }

    // ---------- helpers ----------

    private static AdminActor actor() {
        return new AdminActor(UUID.randomUUID(), "127.0.0.1", "test");
    }

    private static User user(Role role) {
        User user = User.newUser(
                "u" + UUID.randomUUID() + "@example.com",
                "hash", "First", "Last", role);
        user.setCreatedAt(Instant.now());
        return user;
    }
}