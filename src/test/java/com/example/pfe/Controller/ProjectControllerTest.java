package com.example.pfe.Controller;

import com.example.pfe.Service.JwtService;
import com.example.pfe.Service.ProjectService;
import com.example.pfe.Service.SecurityService;
import com.example.pfe.dto.*;
import com.example.pfe.enums.ProjectStatus;
import com.example.pfe.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// ═══════════════════════════════════════════════════════════════════════════════
// @WebMvcTest(ProjectController.class)
//   → Lance UNIQUEMENT la couche Web (contrôleurs, filtres de sécurité).
//     La base de données et les services réels ne sont PAS démarrés.
//
// @ContextConfiguration
//   → Spécifie exactement quels beans charger dans le contexte de test.
//     On inclut notre configuration de sécurité et le gestionnaire d'exceptions.
// ═══════════════════════════════════════════════════════════════════════════════
@WebMvcTest(ProjectController.class)
@ContextConfiguration(classes = {
        ProjectController.class,
        ProjectControllerTest.TestSecurityConfig.class,
        ProjectControllerTest.TestExceptionHandler.class,
})
@DisplayName("ProjectController — Tests Unitaires")
class ProjectControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ProjectService  projectService;
    @MockBean JwtService      jwtService;

    @MockBean(name = "securityService")
    SecurityService securityService;

    // ═══════════════════════════════════════════════════════════════════════════
    // Configuration de sécurité minimale pour les tests :
    // désactive CSRF, exige l'authentification, retourne 401 si non authentifié.
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
    // Gestionnaire d'exceptions pour les tests :
    // ResourceNotFoundException → 404 (sinon @WebMvcTest retourne 500)
    // ═══════════════════════════════════════════════════════════════════════════
    @RestControllerAdvice
    static class TestExceptionHandler {
        @ExceptionHandler(ResourceNotFoundException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        void handleNotFound() {}
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Données de test réutilisables
    // ─────────────────────────────────────────────────────────────────────────
    private ProjectResponseDTO    projectResponse;
    private ProjectRequestDTO     projectRequest;
    // ✅ CORRIGÉ : le contrôleur reçoit un @RequestBody StatusUpdateRequestDTO,
    //              pas un simple @RequestParam.
    private StatusUpdateRequestDTO statusUpdateRequest;

    @BeforeEach
    void setUp() {
        // Réponse fictive retournée par le service
        projectResponse = new ProjectResponseDTO();
        projectResponse.setId(1L);
        projectResponse.setCode("PRJ26ABC123");
        projectResponse.setName("Test Project");
        projectResponse.setStatus(ProjectStatus.PLANNED);
        projectResponse.setStartDate(LocalDate.of(2025, 1, 1));
        projectResponse.setEndDate(LocalDate.of(2025, 12, 31));

        // Requête de création / mise à jour
        projectRequest = new ProjectRequestDTO();
        projectRequest.setName("Test Project");
        projectRequest.setStatus(ProjectStatus.PLANNED);
        projectRequest.setStartDate(LocalDate.of(2025, 1, 1));
        projectRequest.setEndDate(LocalDate.of(2025, 12, 31));

        // ✅ CORRIGÉ : corps JSON envoyé à PUT /api/projects/{id}/status
        statusUpdateRequest = new StatusUpdateRequestDTO();
        statusUpdateRequest.setStatus(ProjectStatus.IN_PROGRESS);
        // Si StatusUpdateRequestDTO possède un champ comment, décommentez :
        // statusUpdateRequest.setComment("Passage en cours");
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 1 — POST /api/projects  (créer un projet)
    // Autorisé : ADMIN, GENERAL_MANAGER
    // Refusé   : PROJECT_MANAGER, EMPLOYEE, non authentifié
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/projects")
    class CreateProject {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("✅ ADMIN peut créer un projet → 200 OK")
        void adminCanCreate() throws Exception {
            when(projectService.createProject(any())).thenReturn(projectResponse);

            mockMvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(projectRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Test Project"))
                    .andExpect(jsonPath("$.code").value("PRJ26ABC123"));

            verify(projectService, times(1)).createProject(any());
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ GENERAL_MANAGER peut créer un projet → 200 OK")
        void gmCanCreate() throws Exception {
            when(projectService.createProject(any())).thenReturn(projectResponse);

            mockMvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(projectRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "PROJECT_MANAGER")
        @DisplayName("❌ PROJECT_MANAGER est refusé → 403 Forbidden")
        void pmIsDenied() throws Exception {
            mockMvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(projectRequest)))
                    .andExpect(status().isForbidden());

            verify(projectService, never()).createProject(any());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("❌ EMPLOYEE est refusé → 403 Forbidden")
        void employeeIsDenied() throws Exception {
            mockMvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(projectRequest)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void unauthenticatedIsRejected() throws Exception {
            mockMvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(projectRequest)))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 2 — GET /api/projects/{id}  (voir un projet)
    // Autorisé : tout utilisateur authentifié
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/projects/{id}")
    class GetProjectById {

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("✅ Tout utilisateur authentifié peut voir un projet → 200 OK")
        void authenticatedCanGetProject() throws Exception {
            when(projectService.getProjectById(1L)).thenReturn(projectResponse);

            mockMvc.perform(get("/api/projects/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Test Project"))
                    .andExpect(jsonPath("$.status").value("PLANNED"));
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("❌ Projet introuvable → 404 Not Found")
        void projectNotFound_returns404() throws Exception {
            when(projectService.getProjectById(99L))
                    .thenThrow(new ResourceNotFoundException("Project not found: 99"));

            mockMvc.perform(get("/api/projects/99"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void unauthenticatedIsRejected() throws Exception {
            mockMvc.perform(get("/api/projects/1"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 3 — GET /api/projects  (liste paginée)
    // Autorisé : tout utilisateur authentifié
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/projects")
    class GetAllProjects {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("✅ ADMIN peut lister les projets → 200 OK avec pagination")
        void adminCanListProjects() throws Exception {
            Page<ProjectResponseDTO> page = new PageImpl<>(List.of(projectResponse));
            when(projectService.getAllProjects(any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/projects"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Test Project"));
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("✅ EMPLOYEE peut aussi lister les projets → 200 OK")
        void employeeCanListProjects() throws Exception {
            Page<ProjectResponseDTO> page = new PageImpl<>(List.of());
            when(projectService.getAllProjects(any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get("/api/projects"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void unauthenticatedIsRejected() throws Exception {
            mockMvc.perform(get("/api/projects"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 4 — PUT /api/projects/{id}  (modifier un projet)
    // Autorisé : ADMIN, GENERAL_MANAGER
    // Refusé   : PROJECT_MANAGER, EMPLOYEE
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("PUT /api/projects/{id}")
    class UpdateProject {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("✅ ADMIN peut modifier un projet → 200 OK")
        void adminCanUpdate() throws Exception {
            when(projectService.updateProject(eq(1L), any())).thenReturn(projectResponse);

            mockMvc.perform(put("/api/projects/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(projectRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));

            verify(projectService).updateProject(eq(1L), any());
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ GENERAL_MANAGER peut modifier un projet → 200 OK")
        void gmCanUpdate() throws Exception {
            when(projectService.updateProject(eq(1L), any())).thenReturn(projectResponse);

            mockMvc.perform(put("/api/projects/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(projectRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "PROJECT_MANAGER")
        @DisplayName("❌ PROJECT_MANAGER est refusé → 403 Forbidden")
        void pmIsDenied() throws Exception {
            mockMvc.perform(put("/api/projects/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(projectRequest)))
                    .andExpect(status().isForbidden());

            verify(projectService, never()).updateProject(any(), any());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void unauthenticatedIsRejected() throws Exception {
            mockMvc.perform(put("/api/projects/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(projectRequest)))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 5 — PUT /api/projects/{id}/status  (changer le statut)
    //
    // ✅ CORRIGÉ : le contrôleur attend un @RequestBody StatusUpdateRequestDTO,
    //              pas un @RequestParam.  Les mocks du service sont aussi corrigés
    //              pour utiliser la signature updateProjectStatus(Long, StatusUpdateRequestDTO).
    //
    // Autorisé : ADMIN, GENERAL_MANAGER, ou le PROJECT_MANAGER du projet
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("PUT /api/projects/{id}/status")
    class UpdateProjectStatus {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("✅ ADMIN peut changer le statut → 200 OK")
        void adminCanUpdateStatus() throws Exception {
            projectResponse.setStatus(ProjectStatus.IN_PROGRESS);
            // ✅ signature corrigée : (Long id, StatusUpdateRequestDTO request)
            when(projectService.updateProjectStatus(eq(1L), any(StatusUpdateRequestDTO.class)))
                    .thenReturn(projectResponse);

            mockMvc.perform(put("/api/projects/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusUpdateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ GENERAL_MANAGER peut changer le statut → 200 OK")
        void gmCanUpdateStatus() throws Exception {
            StatusUpdateRequestDTO completedRequest = new StatusUpdateRequestDTO();
            completedRequest.setStatus(ProjectStatus.COMPLETED);

            when(projectService.updateProjectStatus(eq(1L), any(StatusUpdateRequestDTO.class)))
                    .thenReturn(projectResponse);

            mockMvc.perform(put("/api/projects/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(completedRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "PROJECT_MANAGER")
        @DisplayName("✅ PROJECT_MANAGER du projet peut changer le statut → 200 OK")
        void pmOfProjectCanUpdateStatus() throws Exception {
            // Le SpEL @securityService.isProjectManager(#id) doit retourner true
            when(securityService.isProjectManager(1L)).thenReturn(true);
            when(projectService.updateProjectStatus(eq(1L), any(StatusUpdateRequestDTO.class)))
                    .thenReturn(projectResponse);

            mockMvc.perform(put("/api/projects/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusUpdateRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "PROJECT_MANAGER")
        @DisplayName("❌ PROJECT_MANAGER d'un AUTRE projet est refusé → 403")
        void pmOfOtherProjectIsDenied() throws Exception {
            // isProjectManager retourne false → ce PM n'est pas responsable du projet 1
            when(securityService.isProjectManager(1L)).thenReturn(false);

            mockMvc.perform(put("/api/projects/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusUpdateRequest)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("❌ EMPLOYEE est refusé → 403 Forbidden")
        void employeeIsDenied() throws Exception {
            when(securityService.isProjectManager(anyLong())).thenReturn(false);

            mockMvc.perform(put("/api/projects/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusUpdateRequest)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void unauthenticatedIsRejected() throws Exception {
            mockMvc.perform(put("/api/projects/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusUpdateRequest)))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 6 — GET /api/projects/{id}/status-history  (historique des statuts)
    //
    // ✅ NOUVEAU : cet endpoint existait dans le contrôleur mais n'était pas testé.
    // Autorisé : tout utilisateur authentifié
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/projects/{id}/status-history")
    class GetStatusHistory {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("✅ ADMIN peut voir l'historique → 200 OK avec la liste")
        void adminCanGetHistory() throws Exception {
            ProjectStatusHistoryDTO entry = new ProjectStatusHistoryDTO();
            entry.setFromStatus(ProjectStatus.PLANNED);
            entry.setToStatus(ProjectStatus.IN_PROGRESS);
            entry.setChangedBy("admin@company.com");

            when(projectService.getStatusHistory(1L)).thenReturn(List.of(entry));

            mockMvc.perform(get("/api/projects/1/status-history"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].fromStatus").value("PLANNED"))
                    .andExpect(jsonPath("$[0].toStatus").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$[0].changedBy").value("admin@company.com"));

            verify(projectService).getStatusHistory(1L);
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("✅ EMPLOYEE peut voir l'historique → 200 OK liste vide")
        void employeeCanGetHistory() throws Exception {
            when(projectService.getStatusHistory(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/projects/1/status-history"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @WithMockUser(roles = "PROJECT_MANAGER")
        @DisplayName("✅ PROJECT_MANAGER peut voir l'historique → 200 OK")
        void pmCanGetHistory() throws Exception {
            when(projectService.getStatusHistory(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/projects/1/status-history"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("❌ Projet introuvable → 404 Not Found")
        void projectNotFound_returns404() throws Exception {
            when(projectService.getStatusHistory(99L))
                    .thenThrow(new ResourceNotFoundException("Project not found: 99"));

            mockMvc.perform(get("/api/projects/99/status-history"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void unauthenticatedIsRejected() throws Exception {
            mockMvc.perform(get("/api/projects/1/status-history"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 7 — DELETE /api/projects/{id}  (supprimer un projet)
    // Autorisé : ADMIN, GENERAL_MANAGER
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("DELETE /api/projects/{id}")
    class DeleteProject {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("✅ ADMIN peut supprimer un projet → 204 No Content")
        void adminCanDelete() throws Exception {
            doNothing().when(projectService).deleteProject(1L);

            mockMvc.perform(delete("/api/projects/1"))
                    .andExpect(status().isNoContent());

            verify(projectService).deleteProject(1L);
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ GENERAL_MANAGER peut supprimer → 204 No Content")
        void gmCanDelete() throws Exception {
            doNothing().when(projectService).deleteProject(1L);

            mockMvc.perform(delete("/api/projects/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(roles = "PROJECT_MANAGER")
        @DisplayName("❌ PROJECT_MANAGER est refusé → 403 Forbidden")
        void pmIsDenied() throws Exception {
            mockMvc.perform(delete("/api/projects/1"))
                    .andExpect(status().isForbidden());

            verify(projectService, never()).deleteProject(any());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("❌ EMPLOYEE est refusé → 403 Forbidden")
        void employeeIsDenied() throws Exception {
            mockMvc.perform(delete("/api/projects/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void unauthenticatedIsRejected() throws Exception {
            mockMvc.perform(delete("/api/projects/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("❌ Projet introuvable → 404 Not Found")
        void projectNotFound_returns404() throws Exception {
            doThrow(new ResourceNotFoundException("Project not found: 99"))
                    .when(projectService).deleteProject(99L);

            mockMvc.perform(delete("/api/projects/99"))
                    .andExpect(status().isNotFound());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 8 — GET /api/projects/{id}/team  (membres de l'équipe)
    // Autorisé : tout utilisateur authentifié
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/projects/{id}/team")
    class GetTeamMembers {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("✅ ADMIN peut voir l'équipe → 200 OK avec la liste")
        void adminCanGetTeam() throws Exception {
            TeamMemberDTO member = TeamMemberDTO.builder()
                    .id(10L)
                    .firstName("Alice")
                    .lastName("Martin")
                    .email("alice@company.com")
                    .build();

            when(projectService.getProjectTeamMembers(1L)).thenReturn(List.of(member));

            mockMvc.perform(get("/api/projects/1/team"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].firstName").value("Alice"))
                    .andExpect(jsonPath("$[0].email").value("alice@company.com"));
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("✅ EMPLOYEE peut voir l'équipe → 200 OK liste vide")
        void employeeCanGetTeam() throws Exception {
            when(projectService.getProjectTeamMembers(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/projects/1/team"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @WithMockUser(roles = "PROJECT_MANAGER")
        @DisplayName("✅ PROJECT_MANAGER peut voir l'équipe → 200 OK")
        void pmCanGetTeam() throws Exception {
            when(projectService.getProjectTeamMembers(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/projects/1/team"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("❌ Projet introuvable → 404 Not Found")
        void projectNotFound_returns404() throws Exception {
            when(projectService.getProjectTeamMembers(99L))
                    .thenThrow(new ResourceNotFoundException("Project not found: 99"));

            mockMvc.perform(get("/api/projects/99/team"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void unauthenticatedIsRejected() throws Exception {
            mockMvc.perform(get("/api/projects/1/team"))
                    .andExpect(status().isUnauthorized());
        }
    }
}