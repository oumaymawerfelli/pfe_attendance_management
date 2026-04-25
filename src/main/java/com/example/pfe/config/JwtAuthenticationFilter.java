package com.example.pfe.config;

import com.example.pfe.Service.JwtService;
import com.example.pfe.Service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService blacklistService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();

        // Always allow OPTIONS requests (pre-flight CORS)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Check both with and without /api prefix
        return path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/activate")
                || path.startsWith("/api/auth/resend-activation")
                || path.startsWith("/api/auth/validate-activation-token")
                || path.startsWith("/auth/login")
                || path.startsWith("/auth/register")
                || path.startsWith("/auth/activate")
                || path.startsWith("/auth/resend-activation")
                || path.startsWith("/auth/validate-activation-token")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api/debug");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getServletPath();
        String method = request.getMethod();

        log.debug("Processing request: {} {}", method, path);

        // Always allow OPTIONS requests (pre-flight CORS)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            log.debug("Allowing OPTIONS request for: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // Skip filter for public endpoints
        if (shouldNotFilter(request)) {
            log.debug("Skipping JWT filter for public endpoint: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // Try to get JWT from Authorization header first
        String authHeader = request.getHeader("Authorization");
        String jwt = null;

        // If no Authorization header, try to get token from query parameter (for SSE)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            String tokenParam = request.getParameter("token");
            if (tokenParam != null && !tokenParam.isEmpty()) {
                log.debug("Found token in query parameter for SSE connection: {}", path);
                jwt = tokenParam;
                // Also set it as a header for consistency in logs
                authHeader = "Bearer " + tokenParam;
            }
        } else {
            // Extract from header if present
            jwt = authHeader.substring(7);
        }

        log.debug("Auth header present: {}", authHeader != null);

        if (authHeader == null) {
            log.debug("No Authorization header or token param found for protected endpoint: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // If we got here via query param, jwt is already set
        if (jwt == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
        }

        if (jwt == null || jwt.isEmpty()) {
            log.debug("No valid JWT token found for path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("Extracted JWT token length: {}", jwt.length());
        log.debug("Token preview: {}...", jwt.substring(0, Math.min(20, jwt.length())));

        // Check if token is blacklisted
        if (blacklistService.isBlacklisted(jwt)) {
            log.debug("Token is blacklisted for path: {}", path);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
            return;
        }

        // Check token validity
        boolean isValid = jwtService.isTokenValid(jwt);
        log.debug("Token valid basic check: {}", isValid);

        if (!isValid) {
            log.debug("Token is not valid: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        // Check if it's an access token
        boolean isAccessToken = jwtService.isAccessToken(jwt);
        log.debug("Is access token: {}", isAccessToken);

        if (!isAccessToken) {
            log.debug("Token is not an access token for path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        String userEmail = jwtService.extractUsername(jwt);
        log.debug("Extracted username: {}", userEmail);

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
            log.debug("Loaded user details for: {}", userEmail);

            boolean isValidForUser = jwtService.isTokenValid(jwt, userDetails);
            log.debug("Token valid for user: {}", isValidForUser);

            if (!isValidForUser) {
                log.debug("Token validation failed for user: {}", userEmail);
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("✅ Authenticated user {} for SSE/path: {}", userEmail, path);
        }

        filterChain.doFilter(request, response);
    }
}