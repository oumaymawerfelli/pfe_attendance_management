package com.example.pfe.Controller;

import com.example.pfe.Service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DebugController.class)
@ContextConfiguration(classes = {
        DebugController.class,
        DebugControllerTest.TestSecurityConfig.class
})
@DisplayName("DebugController - Tests")
class DebugControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @MockBean JwtService         jwtService;
    @MockBean UserDetailsService userDetailsService;

    // ── Security config ───────────────────────────────────────────────────────
    @Configuration
    @EnableWebSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    );
            return http.build();
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────
    private static final String VALID_TOKEN   = "valid.jwt.token";
    private static final String INVALID_TOKEN = "bad.token";
    private static final String EMAIL         = "john@test.com";

    private UserDetails buildUserDetails(String username) {
        return User.withUsername(username)
                .password("[PROTECTED]")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/debug/token-info/{token}
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/debug/token-info/{token}")
    class GetTokenInfo {

        @Test
        @DisplayName("Valid token with resolvable user → 200 OK with full info")
        void shouldReturnFullInfoForValidToken() throws Exception {
            UserDetails userDetails = buildUserDetails(EMAIL);

            when(jwtService.extractUsername(VALID_TOKEN)).thenReturn(EMAIL);
            when(jwtService.extractUserEmail(VALID_TOKEN)).thenReturn(EMAIL);
            when(jwtService.getTokenType(VALID_TOKEN)).thenReturn("ACCESS");
            when(jwtService.isTokenValid(VALID_TOKEN)).thenReturn(true);
            when(jwtService.isAccessToken(VALID_TOKEN)).thenReturn(true);
            when(jwtService.isTokenValid(VALID_TOKEN, userDetails)).thenReturn(true);
            when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails);

            mockMvc.perform(get("/api/debug/token-info/{token}", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenSubject").value(EMAIL))
                    .andExpect(jsonPath("$.tokenEmail").value(EMAIL))
                    .andExpect(jsonPath("$.tokenType").value("ACCESS"))
                    .andExpect(jsonPath("$.isValid").value(true))
                    .andExpect(jsonPath("$.isAccessToken").value(true))
                    .andExpect(jsonPath("$.userDetailsUsername").value(EMAIL))
                    .andExpect(jsonPath("$.tokenValidForUser").value(true));
        }

        @Test
        @DisplayName("Valid token but subject not found → falls back to email lookup → 200 OK")
        void shouldFallbackToEmailWhenSubjectNotFound() throws Exception {
            String subject = "some-uuid-subject";
            UserDetails userDetails = buildUserDetails(EMAIL);

            when(jwtService.extractUsername(VALID_TOKEN)).thenReturn(subject);
            when(jwtService.extractUserEmail(VALID_TOKEN)).thenReturn(EMAIL);
            when(jwtService.getTokenType(VALID_TOKEN)).thenReturn("ACCESS");
            when(jwtService.isTokenValid(VALID_TOKEN)).thenReturn(true);
            when(jwtService.isAccessToken(VALID_TOKEN)).thenReturn(true);
            when(userDetailsService.loadUserByUsername(subject))
                    .thenThrow(new RuntimeException("User not found by subject"));
            when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
            when(jwtService.isTokenValid(VALID_TOKEN, userDetails)).thenReturn(true);

            mockMvc.perform(get("/api/debug/token-info/{token}", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userDetailsError").value("User not found by subject"))
                    .andExpect(jsonPath("$.userDetailsByEmailUsername").value(EMAIL))
                    .andExpect(jsonPath("$.tokenValidForEmailUser").value(true));
        }

        @Test
        @DisplayName("Valid token but both subject and email lookups fail → 200 OK with errors")
        void shouldReturnBothErrorsWhenBothLookupsFail() throws Exception {
            String subject = "some-uuid-subject";

            when(jwtService.extractUsername(VALID_TOKEN)).thenReturn(subject);
            when(jwtService.extractUserEmail(VALID_TOKEN)).thenReturn(EMAIL);
            when(jwtService.getTokenType(VALID_TOKEN)).thenReturn("ACCESS");
            when(jwtService.isTokenValid(VALID_TOKEN)).thenReturn(false);
            when(jwtService.isAccessToken(VALID_TOKEN)).thenReturn(true);
            when(userDetailsService.loadUserByUsername(subject))
                    .thenThrow(new RuntimeException("User not found by subject"));
            when(userDetailsService.loadUserByUsername(EMAIL))
                    .thenThrow(new RuntimeException("User not found by email"));

            mockMvc.perform(get("/api/debug/token-info/{token}", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userDetailsError").value("User not found by subject"))
                    .andExpect(jsonPath("$.emailUserDetailsError").value("User not found by email"));
        }

        @Test
        @DisplayName("Token with null subject → skips user lookup → 200 OK")
        void shouldSkipUserLookupWhenSubjectIsNull() throws Exception {
            when(jwtService.extractUsername(VALID_TOKEN)).thenReturn(null);
            when(jwtService.extractUserEmail(VALID_TOKEN)).thenReturn(EMAIL);
            when(jwtService.getTokenType(VALID_TOKEN)).thenReturn("ACCESS");
            when(jwtService.isTokenValid(VALID_TOKEN)).thenReturn(false);
            when(jwtService.isAccessToken(VALID_TOKEN)).thenReturn(false);

            mockMvc.perform(get("/api/debug/token-info/{token}", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenSubject").isEmpty())
                    .andExpect(jsonPath("$.isValid").value(false));

            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("Subject equals email → skips email fallback even if subject lookup fails → 200 OK")
        void shouldSkipEmailFallbackWhenSubjectEqualsEmail() throws Exception {
            when(jwtService.extractUsername(VALID_TOKEN)).thenReturn(EMAIL);
            when(jwtService.extractUserEmail(VALID_TOKEN)).thenReturn(EMAIL);  // same as subject
            when(jwtService.getTokenType(VALID_TOKEN)).thenReturn("ACCESS");
            when(jwtService.isTokenValid(VALID_TOKEN)).thenReturn(true);
            when(jwtService.isAccessToken(VALID_TOKEN)).thenReturn(true);
            when(userDetailsService.loadUserByUsername(EMAIL))
                    .thenThrow(new RuntimeException("User not found"));

            mockMvc.perform(get("/api/debug/token-info/{token}", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userDetailsError").value("User not found"))
                    .andExpect(jsonPath("$.userDetailsByEmailUsername").doesNotExist())
                    .andExpect(jsonPath("$.emailUserDetailsError").doesNotExist());

            verify(userDetailsService, times(1)).loadUserByUsername(EMAIL);
        }

        @Test
        @DisplayName("JwtService throws on extractUsername → 400 Bad Request")
        void shouldReturn400WhenTokenIsMalformed() throws Exception {
            when(jwtService.extractUsername(INVALID_TOKEN))
                    .thenThrow(new RuntimeException("Malformed JWT token"));

            mockMvc.perform(get("/api/debug/token-info/{token}", INVALID_TOKEN))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string("Error: Malformed JWT token"));
        }

        @Test
        @DisplayName("Token is a refresh token → isAccessToken false → 200 OK")
        void shouldIndicateRefreshToken() throws Exception {
            UserDetails userDetails = buildUserDetails(EMAIL);

            when(jwtService.extractUsername(VALID_TOKEN)).thenReturn(EMAIL);
            when(jwtService.extractUserEmail(VALID_TOKEN)).thenReturn(EMAIL);
            when(jwtService.getTokenType(VALID_TOKEN)).thenReturn("REFRESH");
            when(jwtService.isTokenValid(VALID_TOKEN)).thenReturn(true);
            when(jwtService.isAccessToken(VALID_TOKEN)).thenReturn(false);
            when(jwtService.isTokenValid(VALID_TOKEN, userDetails)).thenReturn(true);
            when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails);

            mockMvc.perform(get("/api/debug/token-info/{token}", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("REFRESH"))
                    .andExpect(jsonPath("$.isAccessToken").value(false));
        }
    }
}