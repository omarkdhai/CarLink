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
 * credentials); enabled in the dev profile only. Idempotent — never overwrites.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

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
        User admin = User.newUser(
                email.trim().toLowerCase(),
                passwordEncoder.encode(properties.admin().bootstrapPassword().trim()),
                "Admin", "Admin", Role.ADMIN);
        admin.setActive(true);
        admin.setEmailVerified(true);
        userRepository.save(admin);
        log.info("Bootstrapped initial ADMIN user: {}", admin.getEmail());
    }
}