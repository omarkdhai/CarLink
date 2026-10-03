package com.carlink.security.config;

import com.carlink.auth.service.CustomUserDetailsService;
import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.dto.ApiError;
import com.carlink.security.filter.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Stateless JWT security configuration.
 *
 * <p>Route rules (Phase 2):</p>
 * <ul>
 *   <li>Public: auth endpoints, actuator health/info, Swagger UI.</li>
 *   <li>Public (Phase 5): the QR page {@code /c/**} and the JSON/contact
 *       API under {@code /api/v1/public/**} (both rate-limited).</li>
 *   <li>Authenticated: everything else.</li>
 *   <li>{@code /api/v1/admin/**} additionally requires {@code ROLE_ADMIN}.</li>
 * </ul>
 *
 * <p>Security headers (Phase 9): HSTS, CSP, X-Frame-Options, X-Content-Type-Options,
 * Referrer-Policy.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * HSTS is opt-in. Browsers ignore the header over plain HTTP, so sending it
     * before TLS is in place is noise, and {@code includeSubDomains} is a
     * commitment that cannot be withdrawn once a browser has seen it. Enabled in
     * the prod profile only.
     */
    @Value("${carlink.security.hsts-enabled:false}")
    private boolean hstsEnabled;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final CarLinkProperties properties;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider(passwordEncoder()))
                // Security headers (Phase 9)
                .headers(headers -> {
                    headers
                            // CSP: restrictive default, allow self + swagger
                            .contentSecurityPolicy(csp -> csp
                                    .policyDirectives("default-src 'self'; "
                                            + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                                            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                                            + "font-src 'self' https://fonts.gstatic.com; "
                                            + "img-src 'self' data:; "
                                            + "connect-src 'self';")
                            )
                            // X-Frame-Options: deny framing
                            .frameOptions(frame -> frame.deny())
                            // Referrer-Policy: strict-origin-when-cross-origin
                            .referrerPolicy(referrer -> referrer
                                    .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                            );
                    // Cache-Control is likewise left at the Spring default, which
                    // writes "no-cache, no-store, max-age=0, must-revalidate".
                    // Calling cacheControl.disable() here did not switch caching
                    // off — it removed the writer, so the header was absent
                    // entirely and any shared cache was free to store the
                    // response.
                    if (hstsEnabled) {
                        // Only meaningful once TLS is terminated in front of the
                        // app. Sending it over plain HTTP is ignored by browsers,
                        // and includeSubDomains cannot be withdrawn later.
                        headers.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        );
                    } else {
                        headers.httpStrictTransportSecurity(hsts -> hsts.disable());
                    }
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public, unauthenticated
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        // Public QR page + JSON behind a scan (rate-limited)
                        .requestMatchers("/c/**", "/api/v1/public/**").permitAll()
                        // Admin only (Phase 8 adds the dashboard endpoints)
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(),
                                    ApiError.of(401, "UNAUTHORIZED",
                                            "Authentication required"));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(),
                                    ApiError.of(403, "FORBIDDEN", "Access denied"));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Blank entries are dropped so an unset variable means "no cross-origin
        // callers" rather than a literal "" origin that matches nothing.
        List<String> allowedOrigins = Arrays.stream(
                        properties.security().corsAllowedOrigins().split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}