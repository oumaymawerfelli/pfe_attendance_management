package com.example.pfe.config;

import com.example.pfe.Service.JwtService;
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
import com.example.pfe.Service.TokenBlacklistService;
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
                || path.startsWith("/auth/login")           // Add these without /api
                || path.startsWith("/auth/register")        // Add these without /api
                || path.startsWith("/auth/activate")        // Add these without /api
                || path.startsWith("/auth/resend-activation") // Add these without /api
                || path.startsWith("/auth/validate-activation-token") // Add these without /api
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
        log.debug("Processing request: {} {}", request.getMethod(), path);

        // Skip filter for public endpoints
        if (shouldNotFilter(request)) {
            log.debug("Skipping JWT filter for public endpoint: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        // If no auth header for protected endpoint, continue (Spring Security will handle 403)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No JWT token found for protected endpoint: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);

        // 🔴 NOUVEAU : Vérifier si le token est blacklisté
        // Tu dois d'abord injecter le service
        if (blacklistService.isBlacklisted(jwt)) {
            log.debug("Token blacklisté pour le chemin: {}", path);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token révoqué");
            return;
        }

        if (!jwtService.isAccessToken(jwt) || !jwtService.isTokenValid(jwt)) {
            log.debug("Invalid or expired token for path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        String userEmail = jwtService.extractUsername(jwt);

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            if (!jwtService.isTokenValid(jwt, userDetails)) {
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

            log.debug("Authenticated user {}", userEmail);
        }

        filterChain.doFilter(request, response);
    }}