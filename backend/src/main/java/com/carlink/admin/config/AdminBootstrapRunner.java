package com.carlink.admin.config;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the initial ADMIN user on startup when {@code carlink.admin.bootstrap-email}
 * is configured and no user with that email exists. Disabled by default (empty
 * credentials). Idempotent — never overwrites.
 *
 * <p>Production uses it once for the first deploy: set the two variables, sign in,
 * change the password, then remove {@code ADMIN_BOOTSTRAP_PASSWORD}. An e-mail
 * without a usable password stops startup rather than creating an admin whose
 * password is empty.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CarLinkProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.admin().bootstrapEmail();
        if (email == null || email.isBlank()) {
            return;
        }
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        String password = properties.admin().bootstrapPassword() == null
                ? "" : properties.admin().bootstrapPassword().trim();
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL is set but ADMIN_BOOTSTRAP_PASSWORD "
                    + "is missing or shorter than " + MIN_PASSWORD_LENGTH + " characters");
        }
        User admin = User.newUser(
                email.trim().toLowerCase(),
                passwordEncoder.encode(password),
                "Admin", "Admin", Role.ADMIN);
        admin.setActive(true);
        admin.setEmailVerified(true);
        userRepository.save(admin);
        log.info("Bootstrapped initial ADMIN user: {}", admin.getEmail());
    }
}