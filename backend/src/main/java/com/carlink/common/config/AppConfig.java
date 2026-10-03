package com.carlink.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Registers application configuration beans shared across the modular monolith.
 */
@Configuration
@EnableConfigurationProperties(CarLinkProperties.class)
public class AppConfig {

    /**
     * Respects X-Forwarded-For / X-Forwarded-Proto when running behind a proxy.
     *
     * <p>Only registered in the prod profile, where the app sits behind nginx.
     * {@code ForwardedHeaderFilter} trusts the header from <em>any</em> client
     * and has no way to restrict it to a trusted proxy range, so enabling it
     * unconditionally let a direct caller forge {@code X-Forwarded-For} and mint
     * a fresh rate-limit bucket on every request — which also defeats the login
     * lockout, since every limiter keys off the remote address.
     *
     * <p>The prod nginx overwrites the header with {@code $remote_addr} rather
     * than appending to it, so the value it forwards is not client-controlled.
     * Keep those two settings together.
     */
    @Bean
    @Profile("prod")
    public org.springframework.boot.web.servlet.FilterRegistrationBean<
            org.springframework.web.filter.ForwardedHeaderFilter> forwardedHeaderFilter() {
        org.springframework.boot.web.servlet.FilterRegistrationBean<
                org.springframework.web.filter.ForwardedHeaderFilter> registration =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>(
                        new org.springframework.web.filter.ForwardedHeaderFilter());
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }
}