package com.carlink.admin.controller;

import com.carlink.admin.dto.AdminStatsResponse;
import com.carlink.admin.service.AdminStatsService;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsControllerTest {

    @Mock private AdminStatsService adminStatsService;

    private AdminStatsController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminStatsController(adminStatsService);
    }

    @Test
    void overviewDelegatesToService() {
        AdminStatsResponse expected = new AdminStatsResponse(1, 1, 1, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0);
        when(adminStatsService.overview()).thenReturn(expected);

        AdminStatsResponse out = controller.overview(
                new UserPrincipal(UUID.randomUUID(), "a@e.com", "ADMIN"));

        assertThat(out).isSameAs(expected);
    }

    @Test
    void nullPrincipalThrowsUnauthorized() {
        assertThatThrownBy(() -> controller.overview(null))
                .isInstanceOf(UnauthorizedException.class);
    }
}