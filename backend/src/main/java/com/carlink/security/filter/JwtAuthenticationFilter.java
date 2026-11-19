package com.carlink.security.filter;

import com.carlink.auth.service.JwtService;
import com.carlink.security.principal.UserPrincipal;
import com.carlink.security.session.SessionStateCache;
import com.carlink.security.session.SessionStateCache.SessionState;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Extracts and validates the {@code Authorization: Bearer <jwt>} header on each
 * request. On success, builds a {@link UserPrincipal} and populates the
 * SecurityContext.
 *
 * <p>Security plan S-10: a valid signature is not enough. The token is checked
 * against the account's live state ({@link SessionStateCache}): the account
 * must still exist and be active, and the token's version must be current
 * (deactivation, role change and password reset bump it). The role is taken
 * from that state, not from the token. Admin routes read the database on every
 * request; other routes use a copy cached for a few seconds.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String ADMIN_PREFIX = "/api/v1/admin";

    private final JwtService jwtService;
    private final SessionStateCache sessionStateCache;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = jwtService.parseAccessToken(token);
            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);

            boolean adminRoute = request.getRequestURI().startsWith(ADMIN_PREFIX);
            Optional<SessionState> state = adminRoute
                    ? sessionStateCache.fresh(userId)
                    : sessionStateCache.get(userId);
            if (state.isEmpty() || !state.get().active()
                    || state.get().tokenVersion() != jwtService.extractTokenVersion(claims)) {
                // Revoked session: proceed unauthenticated (protected routes answer 401).
                log.debug("Rejected access token of a revoked session");
                filterChain.doFilter(request, response);
                return;
            }
            String role = state.get().role().name();

            UserPrincipal principal = new UserPrincipal(userId, email, role);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            // Invalid/expired token: proceed unauthenticated; protected routes
            // will return 401 via the entry point.
            log.debug("Rejected access token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}