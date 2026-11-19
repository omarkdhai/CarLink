package com.carlink.security.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Static credential for Prometheus (security plan S-25). Admin JWTs live five
 * minutes (S-10), so a scraper cannot hold one; instead
 * {@code METRICS_SCRAPE_TOKEN} (32+ characters) grants ROLE_METRICS, which
 * opens {@code /actuator/prometheus} and nothing else. Blank = disabled.
 *
 * <p>The actuator is never reachable from the internet anyway: nginx serves
 * only /actuator/health (S-05) and the backend port is bound to loopback.
 */
public class MetricsScrapeTokenFilter extends OncePerRequestFilter {

    public static final String PATH = "/actuator/prometheus";
    public static final String ROLE = "METRICS";

    private final byte[] token;

    public MetricsScrapeTokenFilter(String token) {
        if (token != null && !token.isBlank() && token.trim().length() < 32) {
            throw new IllegalStateException("METRICS_SCRAPE_TOKEN must be at least 32 characters.");
        }
        this.token = token == null || token.isBlank() ? null : token.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return token == null || !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            byte[] presented = header.substring(7).trim().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(presented, token)) { // constant-time
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("prometheus", null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
            }
        }
        chain.doFilter(request, response);
    }
}
