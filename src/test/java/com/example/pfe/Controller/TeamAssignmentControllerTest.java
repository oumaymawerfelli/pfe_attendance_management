package com.example.pfe.Controller;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Service.JwtService;
import com.example.pfe.Service.SecurityService;          // ✅ VOTRE SecurityService, pas springdoc
import com.example.pfe.Service.TeamAssignmentService;
import com.example.pfe.dto.TeamAssignmentDTO;
import com.example.pfe.dto.TeamAssignmentResponseDTO;
import com.example.pfe.entities.User;
import com.example.pfe.exception.ResourceNotFoundException;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TeamAssignmentController.class)
@ContextConfiguration(classes = {
        TeamAssignmentController.class,
        TeamAssignmentControllerTest.TestSecurityConfig.class,
        TeamAssignmentControllerTest.TestExceptionHandler.class,  // ✅ gère ResourceNotFoundException → 404
})
@DisplayName("TeamAssignmentController - Tests")
class TeamAssignmentControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TeamAssignmentService teamAssignmentService;
    @MockBean UserRepository        userRepository;
    @MockBean JwtService            jwtService;

    // ✅ Mock du BON SecurityService (com.example.pfe.Service.SecurityService)
    // Le nom "securityService" doit correspondre à @Service("securityService") dans votre classe
    @MockBean(name = "securityService")
    SecurityService securityService;

    // ═══════════════════════════════════════════════════════════════════════════
    // Configuration de sécurité minimale pour les tests
    // ═══════════════════════════════════════════════════════════════════════════
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint(
                                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
            return http.build();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Gestionnaire d'exceptions minimal pour les tests
    //
    // POURQUOI ?
    //   @WebMvcTest ne charge pas le vrai @ControllerAdvice global de l'application.
    //   Sans ce handler, ResourceNotFoundException remonte comme une erreur 500.
    //   Ce mini-handler mappe ResourceNotFoundException → 404 dans le contexte de test.
    // ═══════════════════════════════════════════════════════════════════════════
    @RestControllerAdvice
    static class TestExceptionHandler {

        @ExceptionHandler(ResourceNotFoundException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        void handleResourceNotFound(ResourceNotFoundException ex) {
            // On retourne juste 404 — pas besoin de body dans les tests
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private TeamAssignmentResponseDTO buildResponse() {
        return TeamAssignmentResponseDTO.builder()
                .id(1L)
                .projectId(10L)
                .employeeId(20L)
                .build();
    }

    private TeamAssignmentDTO buildRequest() {
        TeamAssignmentDTO dto = new TeamAssignmentDTO();
        dto.setProjectId(10L);
        dto.setEmployeeId(20L);
        dto.setAssigningManagerId(5L);
        return dto;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/team-assignments
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/team-assignments")
    class AssignTeamMember {

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("GENERAL_MANAGER peut assigner → 200 OK")
        void shouldAssignForGM() throws Exception {
            when(teamAssignmentService.assignEmployeeToProject(any())).thenReturn(buildResponse());

            mockMvc.perform(post("/api/team-assignments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.projectId").value(10));

            verify(teamAssignmentService).assignEmployeeToProject(any());
        }

        @Test
        @WithMockUser(roles = "PROJECT_MANAGER")
        @DisplayName("PROJECT_MANAGER peut assigner → 200 OK")
        void shouldAssignForPM() throws Exception {
            when(teamAssignmentService.assignEmployeeToProject(any())).thenReturn(buildResponse());

            mockMvc.perform(post("/api/team-assignments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE est refusé → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(post("/api/team-assignments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/team-assignments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DELETE /api/team-assignments/{assignmentId}
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("DELETE /api/team-assignments/{assignmentId}")
    class RemoveTeamMember {

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("GENERAL_MANAGER peut supprimer → 204 No Content")
        void shouldRemoveForGM() throws Exception {
            doNothing().when(teamAssignmentService).removeEmployeeFromProject(1L);

            mockMvc.perform(delete("/api/team-assignments/1"))
                    .andExpect(status().isNoContent());

            verify(teamAssignmentService).removeEmployeeFromProject(1L);
        }

        @Test
        @WithMockUser(roles = "PROJECT_MANAGER")
        @DisplayName("PROJECT_MANAGER peut supprimer → 204 No Content")
        void shouldRemoveForPM() throws Exception {
            doNothing().when(teamAssignmentService).removeEmployeeFromProject(1L);

            mockMvc.perform(delete("/api/team-assignments/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE est refusé → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(delete("/api/team-assignments/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(delete("/api/team-assignments/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/team-assignments/project/{projectId}
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/team-assignments/project/{projectId}")
    class GetProjectTeam {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN peut voir l'équipe → 200 OK")
        void shouldReturnTeamForAdmin() throws Exception {
            when(teamAssignmentService.getProjectTeam(10L))
                    .thenReturn(List.of(buildResponse()));

            mockMvc.perform(get("/api/team-assignments/project/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].projectId").value(10));
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("GENERAL_MANAGER peut voir l'équipe → 200 OK")
        void shouldReturnTeamForGM() throws Exception {
            when(teamAssignmentService.getProjectTeam(10L)).thenReturn(List.of());

            mockMvc.perform(get("/api/team-assignments/project/10"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "emp@test.com", roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE peut voir l'équipe → 200 OK")
        void shouldReturnTeamForEmployee() throws Exception {
            // ✅ FIX : on configure securityService.isProjectManager() pour retourner false
            // Cela permet au SpEL de s'évaluer sans erreur.
            // hasRole('EMPLOYEE') est vrai → l'accès est accordé avant même d'appeler isProjectManager
            when(securityService.isProjectManager(anyLong())).thenReturn(false);
            when(teamAssignmentService.getProjectTeam(10L)).thenReturn(List.of());

            mockMvc.perform(get("/api/team-assignments/project/10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/team-assignments/project/10"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/team-assignments/employee/{employeeId}
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/team-assignments/employee/{employeeId}")
    class GetEmployeeAssignments {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN peut voir les assignments → 200 OK")
        void shouldReturnAssignmentsForAdmin() throws Exception {
            when(teamAssignmentService.getEmployeeAssignments(20L))
                    .thenReturn(List.of(buildResponse()));

            mockMvc.perform(get("/api/team-assignments/employee/20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("GENERAL_MANAGER peut voir les assignments → 200 OK")
        void shouldReturnAssignmentsForGM() throws Exception {
            when(teamAssignmentService.getEmployeeAssignments(20L)).thenReturn(List.of());

            mockMvc.perform(get("/api/team-assignments/employee/20"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/team-assignments/employee/20"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/team-assignments/by-employee-email
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/team-assignments/by-employee-email")
    class AssignByEmail {

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("GENERAL_MANAGER peut assigner par email → 200 OK")
        void shouldAssignByEmailForGM() throws Exception {
            User mockUser = mock(User.class);
            when(mockUser.getId()).thenReturn(20L);
            when(userRepository.findByEmail("emp@test.com")).thenReturn(Optional.of(mockUser));
            when(teamAssignmentService.assignEmployeeToProject(any())).thenReturn(buildResponse());

            mockMvc.perform(post("/api/team-assignments/by-employee-email")
                            .param("projectId", "10")
                            .param("employeeEmail", "emp@test.com")
                            .param("assigningManagerId", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));

            verify(teamAssignmentService).assignEmployeeToProject(any());
        }

        @Test
        @WithMockUser(roles = "PROJECT_MANAGER")
        @DisplayName("PROJECT_MANAGER peut assigner par email → 200 OK")
        void shouldAssignByEmailForPM() throws Exception {
            User mockUser = mock(User.class);
            when(mockUser.getId()).thenReturn(20L);
            when(userRepository.findByEmail("emp@test.com")).thenReturn(Optional.of(mockUser));
            when(teamAssignmentService.assignEmployeeToProject(any())).thenReturn(buildResponse());

            mockMvc.perform(post("/api/team-assignments/by-employee-email")
                            .param("projectId", "10")
                            .param("employeeEmail", "emp@test.com")
                            .param("assigningManagerId", "5"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("Email inconnu → 404 Not Found")
        void shouldReturn404ForUnknownEmail() throws Exception {
            // ✅ FIX : avec TestExceptionHandler, ResourceNotFoundException → 404
            when(userRepository.findByEmail("unknown@test.com"))
                    .thenReturn(Optional.empty());

            mockMvc.perform(post("/api/team-assignments/by-employee-email")
                            .param("projectId", "10")
                            .param("employeeEmail", "unknown@test.com")
                            .param("assigningManagerId", "5"))
                    .andExpect(status().isNotFound()); // ← maintenant 404 grâce au TestExceptionHandler
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE est refusé → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(post("/api/team-assignments/by-employee-email")
                            .param("projectId", "10")
                            .param("employeeEmail", "emp@test.com")
                            .param("assigningManagerId", "5"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/team-assignments/by-employee-email")
                            .param("projectId", "10")
                            .param("employeeEmail", "emp@test.com")
                            .param("assigningManagerId", "5"))
                    .andExpect(status().isUnauthorized());
        }
    }
}