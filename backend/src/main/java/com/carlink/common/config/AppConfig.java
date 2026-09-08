package com.carlink.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Registers application configuration beans shared across the modular monolith.
 */
@Configuration
@EnableConfigurationProperties(CarLinkProperties.class)
public class AppConfig {

    /**
     * Respects X-Forwarded-For / X-Forwarded-Proto when running behind a proxy.
     * Required for correct client IP extraction behind Docker/nginx — used by
     * rate limiting.
     */
    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> registration =
                new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }
}