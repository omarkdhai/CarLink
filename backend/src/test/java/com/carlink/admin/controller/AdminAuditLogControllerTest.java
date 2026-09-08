package com.carlink.admin.controller;

import com.carlink.admin.service.AuditLogService;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogControllerTest {

    @Mock private AuditLogService auditLogService;

    private AdminAuditLogController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAuditLogController(auditLogService);
    }

    @Test
    void listDelegatesFiltersAndLimit() {
        when(auditLogService.list("USER_ROLE_CHANGE", "USER", 25)).thenReturn(List.of());
        var out = controller.list(new UserPrincipal(UUID.randomUUID(), "a@e.com", "ADMIN"),
                "USER_ROLE_CHANGE", "USER", 25);
        assertThat(out).isEmpty();
        verify(auditLogService).list("USER_ROLE_CHANGE", "USER", 25);
    }

    @Test
    void nullPrincipalThrowsUnauthorized() {
        assertThatThrownBy(() -> controller.list(null, null, null, 100))
                .isInstanceOf(UnauthorizedException.class);
    }
}