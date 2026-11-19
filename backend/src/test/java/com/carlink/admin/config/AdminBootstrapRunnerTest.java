package com.carlink.admin.config;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapRunnerTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final CarLinkProperties properties = mock(CarLinkProperties.class);
    private final AdminBootstrapRunner runner = new AdminBootstrapRunner(users, encoder, properties);

    private void configure(String email, String password) {
        when(properties.admin()).thenReturn(new CarLinkProperties.Admin(email, password));
    }

    private void run() {
        runner.run(new DefaultApplicationArguments());
    }

    @Test
    void blankEmailDisablesTheBootstrap() {
        configure("", "");
        run();
        verify(users, never()).save(any());
    }

    @Test
    void existingAccountIsNeverTouched() {
        configure("admin@example.org", "a-long-enough-password");
        when(users.findByEmailIgnoreCase("admin@example.org")).thenReturn(Optional.of(mock(User.class)));
        run();
        verify(users, never()).save(any());
    }

    @Test
    void createsVerifiedActiveAdmin() {
        configure(" Admin@Example.org ", "a-long-enough-password");
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(encoder.encode("a-long-enough-password")).thenReturn("hash");
        run();
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.org");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().isEmailVerified()).isTrue();
    }

    @Test
    void missingPasswordStopsStartupInsteadOfCreatingAnEmptyPasswordAdmin() {
        configure("admin@example.org", null);
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(this::run).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");
        verify(users, never()).save(any());
    }

    @Test
    void shortPasswordStopsStartup() {
        configure("admin@example.org", "   short   ");
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(this::run).isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
    }
}
