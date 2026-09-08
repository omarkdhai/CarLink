package com.carlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CarLink — anonymous QR-based vehicle owner contact platform.
 *
 * Modular monolith entry point for the backend application.
 *
 * <p>{@code UserDetailsServiceAutoConfiguration} is excluded because the
 * application supplies its own Spring Security configuration (Phase 2 adds the
 * JWT-backed {@code UserDetailsService}). Without this exclusion Spring Boot
 * would expose an auto-generated demo user/password.</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableJpaAuditing
@EnableScheduling
public class CarLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(CarLinkApplication.class, args);
    }
}