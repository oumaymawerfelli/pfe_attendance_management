package com.example.pfe.Controller;

import com.example.pfe.Controller.AttendanceConfigController;
import com.example.pfe.Service.AttendanceConfigService;
import com.example.pfe.dto.AttendanceConfigDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AttendanceConfigController.class)
@ContextConfiguration(classes = {
        AttendanceConfigController.class,
        AttendanceConfigControllerTest.TestSecurityConfig.class
})
@DisplayName("AttendanceConfigController - Tests")
class AttendanceConfigControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AttendanceConfigService configService;

    // ── Mock JwtService so Spring can satisfy JwtAuthenticationFilter ─────────
    @MockBean com.example.pfe.Service.JwtService jwtService;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    // 👇 This is the key fix: return 401 instead of 403 for anonymous requests
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(
                                    new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                                            org.springframework.http.HttpStatus.UNAUTHORIZED
                                    )
                            )
                    );
            return http.build();
        }

}

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private AttendanceConfigDTO.Response buildResponse(String key, double value) {
        return AttendanceConfigDTO.Response.builder()
                .configKey(key)
                .configValue(value)
                .build();
    }

    private AttendanceConfigDTO.UpdateRequest buildUpdateRequest(double value, String modifiedBy) {
        AttendanceConfigDTO.UpdateRequest req = new AttendanceConfigDTO.UpdateRequest();
        req.setConfigValue(value);
        req.setLastModifiedBy(modifiedBy);
        return req;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/attendance-config
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/attendance-config")
    class GetAll {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN peut récupérer toutes les configurations → 200 OK")
        void shouldReturnAllConfigsForAdmin() throws Exception {
            List<AttendanceConfigDTO.Response> configs = List.of(
                    buildResponse("LATE_HOUR",   8),
                    buildResponse("LATE_MINUTE", 30)
            );
            when(configService.getAllConfigs()).thenReturn(configs);

            mockMvc.perform(get("/api/attendance-config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].configKey").value("LATE_HOUR"))
                    .andExpect(jsonPath("$[0].configValue").value(8.0))
                    .andExpect(jsonPath("$[1].configKey").value("LATE_MINUTE"))
                    .andExpect(jsonPath("$[1].configValue").value(30.0));
        }

        @Test
        @WithMockUser(roles = "HR")
        @DisplayName("HR peut récupérer toutes les configurations → 200 OK")
        void shouldReturnAllConfigsForHR() throws Exception {
            when(configService.getAllConfigs()).thenReturn(List.of(buildResponse("LATE_HOUR", 8)));

            mockMvc.perform(get("/api/attendance-config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE est refusé → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(get("/api/attendance-config"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Utilisateur non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/attendance-config"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Retourne une liste vide si aucune configuration")
        void shouldReturnEmptyListWhenNoConfigs() throws Exception {
            when(configService.getAllConfigs()).thenReturn(List.of());

            mockMvc.perform(get("/api/attendance-config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PATCH /api/attendance-config/{key}
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("PATCH /api/attendance-config/{key}")
    class UpdateConfig {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN peut mettre à jour une configuration → 200 OK")
        void shouldUpdateConfigForAdmin() throws Exception {
            AttendanceConfigDTO.Response response = buildResponse("LATE_HOUR", 9);
            when(configService.updateConfig(eq("LATE_HOUR"), any())).thenReturn(response);

            mockMvc.perform(patch("/api/attendance-config/LATE_HOUR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildUpdateRequest(9, "admin@company.com"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configKey").value("LATE_HOUR"))
                    .andExpect(jsonPath("$.configValue").value(9.0));

            verify(configService).updateConfig(eq("LATE_HOUR"), any());
        }

        @Test
        @WithMockUser(roles = "HR")
        @DisplayName("HR peut mettre à jour une configuration → 200 OK")
        void shouldUpdateConfigForHR() throws Exception {
            AttendanceConfigDTO.Response response = buildResponse("LATE_MINUTE", 30);
            when(configService.updateConfig(eq("LATE_MINUTE"), any())).thenReturn(response);

            mockMvc.perform(patch("/api/attendance-config/LATE_MINUTE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildUpdateRequest(30, "hr@company.com"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configValue").value(30.0));
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE est refusé → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(patch("/api/attendance-config/LATE_HOUR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildUpdateRequest(9, "emp"))))
                    .andExpect(status().isForbidden());

            verify(configService, never()).updateConfig(any(), any());
        }

        @Test
        @DisplayName("Utilisateur non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(patch("/api/attendance-config/LATE_HOUR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildUpdateRequest(9, "anon"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Le path variable {key} est transmis correctement au service")
        void shouldPassKeyToService() throws Exception {
            AttendanceConfigDTO.Response response = buildResponse("HALF_DAY_THRESHOLD", 4);
            when(configService.updateConfig(eq("HALF_DAY_THRESHOLD"), any())).thenReturn(response);

            mockMvc.perform(patch("/api/attendance-config/HALF_DAY_THRESHOLD")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildUpdateRequest(4, "admin@company.com"))))
                    .andExpect(status().isOk());

            verify(configService).updateConfig(eq("HALF_DAY_THRESHOLD"), any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Body sans configValue → 400 Bad Request (validation @NotNull)")
        void shouldReturn400WhenBodyInvalid() throws Exception {
            mockMvc.perform(patch("/api/attendance-config/LATE_HOUR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }
}