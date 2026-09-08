package com.carlink.admin.controller;

import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.dto.ChangeRoleRequest;
import com.carlink.admin.service.AdminUserService;
import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import com.carlink.user.model.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock private AdminUserService adminUserService;
    @Mock private HttpServletRequest request;

    private AdminUserController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminUserController(adminUserService);
    }

    @Test
    void deactivatePassesActorFromPrincipalAndRequest() {
        UUID target = UUID.randomUUID();
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("curl");

        MessageResponse response = controller.deactivate(principal(), target, request);

        assertThat(response.message()).isEqualTo("User deactivated");
        verify(adminUserService).deactivate(target,
                new AdminActor(ADMIN_ID, "127.0.0.1", "curl"));
    }

    @Test
    void changeRolePassesRoleThrough() {
        UUID target = UUID.randomUUID();
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        controller.changeRole(principal(), target, new ChangeRoleRequest(Role.ADMIN), request);

        verify(adminUserService).changeRole(target, Role.ADMIN,
                new AdminActor(ADMIN_ID, "127.0.0.1", null));
    }

    @Test
    void listAndMutationsWithNullPrincipalThrowUnauthorized() {
        assertThatThrownBy(() -> controller.list(null, null, null, null))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> controller.deactivate(null, UUID.randomUUID(), request))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> controller.changeRole(null, UUID.randomUUID(),
                new ChangeRoleRequest(Role.ADMIN), request))
                .isInstanceOf(UnauthorizedException.class);
    }

    private static UserPrincipal principal() {
        return new UserPrincipal(ADMIN_ID, "admin@example.com", "ADMIN");
    }
}