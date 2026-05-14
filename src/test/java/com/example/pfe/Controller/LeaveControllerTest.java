package com.example.pfe.Controller;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Service.LeaveService;
import com.example.pfe.dto.*;
import com.example.pfe.entities.User;
import com.example.pfe.enums.LeaveType;
import com.example.pfe.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeaveController.class)
@ContextConfiguration(classes = {
        LeaveController.class,
        LeaveControllerTest.TestSecurityConfig.class,
        LeaveControllerTest.GlobalExceptionHandler.class
})
@DisplayName("LeaveController — Tests Unitaires")
class LeaveControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean LeaveService   leaveService;
    @MockBean UserRepository userRepository;

    // ── Security config ───────────────────────────────────────────────────────
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
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
            return http.build();
        }
    }

    // ── Exception handler ─────────────────────────────────────────────────────
    @RestControllerAdvice
    static class GlobalExceptionHandler {
        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────
    private static final String EMAIL   = "john@test.com";
    private static final Long   USER_ID = 1L;

    private User buildUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private LeaveRequestDTO buildLeaveRequestDTO() {
        // @Data seul ne génère pas de builder — on utilise les setters
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setLeaveType(LeaveType.ANNUAL);
        dto.setStartDate(LocalDate.now().plusDays(1));
        dto.setEndDate(LocalDate.now().plusDays(5));
        dto.setReason("Need some time off for personal reasons"); // >= 10 chars
        return dto;
    }

    private LeaveResponseDTO buildLeaveResponseDTO(Long id) {
        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(id);
        return dto;
    }

    private LeaveBalanceDTO buildLeaveBalanceDTO() {
        return new LeaveBalanceDTO();
    }

    /**
     * Construit la partie JSON "leaveRequest" pour les requêtes multipart.
     * Le contrôleur utilise @RequestPart("leaveRequest"), donc le Content-Type
     * de la partie DOIT être application/json.
     */
    private MockMultipartFile leaveRequestPart(LeaveRequestDTO dto) throws Exception {
        return new MockMultipartFile(
                "leaveRequest",          // nom de la partie (correspond à @RequestPart)
                "",                      // nom de fichier original (vide pour une partie JSON)
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(dto));
    }

    @BeforeEach
    void stubUserResolution() {
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(buildUser(USER_ID, EMAIL)));
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 1 — GET /api/leaves/summary
    // ✅ NOUVEAU : endpoint présent dans le contrôleur mais absent des tests.
    // Autorisé : tout utilisateur authentifié
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/summary")
    class GetSummary {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("✅ Utilisateur authentifié reçoit son résumé → 200 OK")
        void shouldReturnSummary() throws Exception {
            LeaveSummaryDTO summary = new LeaveSummaryDTO();
            when(leaveService.getSummary(USER_ID)).thenReturn(summary);

            mockMvc.perform(get("/api/leaves/summary"))
                    .andExpect(status().isOk());

            verify(leaveService).getSummary(USER_ID);
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/summary"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = "ghost@test.com")
        @DisplayName("❌ Utilisateur introuvable en base → 404 Not Found")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/leaves/summary"))
                    .andExpect(status().isNotFound());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 2 — POST /api/leaves/request  (multipart/form-data)
    //
    // ✅ CORRIGÉ :
    //   - Le contrôleur déclare @RequestPart + consumes=MULTIPART_FORM_DATA.
    //     On utilise donc mockMvc.perform(multipart(...)) avec MockMultipartFile,
    //     pas post() + application/json.
    //   - Le mock du service inclut le 3e paramètre (MultipartFile attachment).
    //   - Le contrôleur retourne ResponseEntity.ok(...) → 200, pas 201.
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/leaves/request")
    class RequestLeave {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("✅ Employé soumet une demande de congé → 200 OK")
        void shouldCreateLeaveRequest() throws Exception {
            LeaveRequestDTO  requestDTO  = buildLeaveRequestDTO();
            LeaveResponseDTO responseDTO = buildLeaveResponseDTO(1L);

            // ✅ CORRIGÉ : signature réelle = (Long userId, LeaveRequestDTO dto, MultipartFile attachment)
            when(leaveService.requestLeave(eq(USER_ID), any(LeaveRequestDTO.class), any()))
                    .thenReturn(responseDTO);

            // ✅ CORRIGÉ : multipart() au lieu de post() + JSON
            mockMvc.perform(multipart("/api/leaves/request")
                            .file(leaveRequestPart(requestDTO)))
                    .andExpect(status().isOk())   // ✅ 200, pas 201
                    .andExpect(jsonPath("$.id").value(1));

            verify(leaveService).requestLeave(eq(USER_ID), any(LeaveRequestDTO.class), any());
        }

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("✅ Demande avec pièce jointe → 200 OK")
        void shouldCreateLeaveRequestWithAttachment() throws Exception {
            LeaveRequestDTO requestDTO = buildLeaveRequestDTO();
            MockMultipartFile attachment = new MockMultipartFile(
                    "attachment", "doc.pdf", MediaType.APPLICATION_PDF_VALUE,
                    "PDF content".getBytes());

            when(leaveService.requestLeave(eq(USER_ID), any(LeaveRequestDTO.class), any(MultipartFile.class)))
                    .thenReturn(buildLeaveResponseDTO(2L));

            mockMvc.perform(multipart("/api/leaves/request")
                            .file(leaveRequestPart(requestDTO))
                            .file(attachment))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(multipart("/api/leaves/request")
                            .file(leaveRequestPart(buildLeaveRequestDTO())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = "ghost@test.com")
        @DisplayName("❌ Utilisateur introuvable en base → 404 Not Found")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            mockMvc.perform(multipart("/api/leaves/request")
                            .file(leaveRequestPart(buildLeaveRequestDTO())))
                    .andExpect(status().isNotFound());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 3 — POST /api/leaves/draft
    // ✅ NOUVEAU : endpoint présent dans le contrôleur mais absent des tests.
    // Autorisé : tout utilisateur authentifié, corps JSON classique
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/leaves/draft")
    class SaveDraft {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("✅ Employé sauvegarde un brouillon → 200 OK")
        void shouldSaveDraft() throws Exception {
            LeaveRequestDTO  requestDTO  = buildLeaveRequestDTO();
            LeaveResponseDTO responseDTO = buildLeaveResponseDTO(10L);

            when(leaveService.saveDraft(eq(USER_ID), any(LeaveRequestDTO.class)))
                    .thenReturn(responseDTO);

            mockMvc.perform(post("/api/leaves/draft")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));

            verify(leaveService).saveDraft(eq(USER_ID), any(LeaveRequestDTO.class));
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/leaves/draft")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildLeaveRequestDTO())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = "ghost@test.com")
        @DisplayName("❌ Utilisateur introuvable en base → 404 Not Found")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/leaves/draft")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildLeaveRequestDTO())))
                    .andExpect(status().isNotFound());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 4 — GET /api/leaves/my
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/my")
    class GetMyLeaves {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("✅ Retourne l'historique de congés de l'employé → 200 OK")
        void shouldReturnMyLeaves() throws Exception {
            List<LeaveResponseDTO> leaves = List.of(buildLeaveResponseDTO(1L), buildLeaveResponseDTO(2L));
            when(leaveService.getMyLeaves(USER_ID)).thenReturn(leaves);

            mockMvc.perform(get("/api/leaves/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));

            verify(leaveService).getMyLeaves(USER_ID);
        }

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("✅ Retourne une liste vide si aucun congé → 200 OK")
        void shouldReturnEmptyList() throws Exception {
            when(leaveService.getMyLeaves(USER_ID)).thenReturn(List.of());

            mockMvc.perform(get("/api/leaves/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/my"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 5 — GET /api/leaves/my/balance
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/my/balance")
    class GetMyBalance {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("✅ Retourne le solde de congés de l'employé → 200 OK")
        void shouldReturnBalance() throws Exception {
            when(leaveService.getMyBalance(USER_ID)).thenReturn(buildLeaveBalanceDTO());

            mockMvc.perform(get("/api/leaves/my/balance"))
                    .andExpect(status().isOk());

            verify(leaveService).getMyBalance(USER_ID);
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/my/balance"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 6 — GET /api/leaves/team/all
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/team/all")
    class GetTeamAllLeaves {

        @Test
        @WithMockUser(username = EMAIL, roles = "PROJECT_MANAGER")
        @DisplayName("✅ PM récupère tous les congés de son équipe → 200 OK")
        void shouldReturnAllTeamLeaves() throws Exception {
            List<LeaveResponseDTO> leaves = List.of(buildLeaveResponseDTO(1L));
            when(leaveService.getTeamAllLeaves(USER_ID)).thenReturn(leaves);

            mockMvc.perform(get("/api/leaves/team/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            verify(leaveService).getTeamAllLeaves(USER_ID);
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "EMPLOYEE")
        @DisplayName("❌ Rôle non-PM → 403 Forbidden")
        void shouldReturn403ForNonPM() throws Exception {
            mockMvc.perform(get("/api/leaves/team/all"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/team/all"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 7 — GET /api/leaves/team/pending
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/team/pending")
    class GetTeamPendingLeaves {

        @Test
        @WithMockUser(username = EMAIL, roles = "PROJECT_MANAGER")
        @DisplayName("✅ PM récupère les congés en attente de son équipe → 200 OK")
        void shouldReturnPendingTeamLeaves() throws Exception {
            List<LeaveResponseDTO> leaves = List.of(buildLeaveResponseDTO(5L));
            when(leaveService.getTeamPendingLeaves(USER_ID)).thenReturn(leaves);

            mockMvc.perform(get("/api/leaves/team/pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            verify(leaveService).getTeamPendingLeaves(USER_ID);
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "EMPLOYEE")
        @DisplayName("❌ Rôle non-PM → 403 Forbidden")
        void shouldReturn403ForNonPM() throws Exception {
            mockMvc.perform(get("/api/leaves/team/pending"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/team/pending"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 8 — POST /api/leaves/team/{id}/approve
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/leaves/team/{id}/approve")
    class ApproveTeamLeave {

        @Test
        @WithMockUser(username = EMAIL, roles = "PROJECT_MANAGER")
        @DisplayName("✅ PM approuve un congé de son équipe → 200 OK")
        void shouldApproveTeamLeave() throws Exception {
            when(leaveService.approveLeaveByPM(10L, USER_ID)).thenReturn(buildLeaveResponseDTO(10L));

            mockMvc.perform(post("/api/leaves/team/10/approve"))
                    .andExpect(status().isOk());

            verify(leaveService).approveLeaveByPM(10L, USER_ID);
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "PROJECT_MANAGER")
        @DisplayName("✅ L'ID du congé est correctement transmis au service")
        void shouldPassCorrectLeaveId() throws Exception {
            when(leaveService.approveLeaveByPM(42L, USER_ID)).thenReturn(buildLeaveResponseDTO(42L));

            mockMvc.perform(post("/api/leaves/team/42/approve"))
                    .andExpect(status().isOk());

            verify(leaveService).approveLeaveByPM(42L, USER_ID);
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "EMPLOYEE")
        @DisplayName("❌ Rôle non-PM → 403 Forbidden")
        void shouldReturn403ForNonPM() throws Exception {
            mockMvc.perform(post("/api/leaves/team/10/approve"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/leaves/team/10/approve"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 9 — POST /api/leaves/team/{id}/reject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/leaves/team/{id}/reject")
    class RejectTeamLeave {

        @Test
        @WithMockUser(username = EMAIL, roles = "PROJECT_MANAGER")
        @DisplayName("✅ PM rejette un congé avec motif → 200 OK")
        void shouldRejectTeamLeaveWithReason() throws Exception {
            LeaveDecisionDTO decision = new LeaveDecisionDTO();
            when(leaveService.rejectLeaveByPM(eq(10L), eq(USER_ID), any(LeaveDecisionDTO.class)))
                    .thenReturn(buildLeaveResponseDTO(10L));

            mockMvc.perform(post("/api/leaves/team/10/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(decision)))
                    .andExpect(status().isOk());

            verify(leaveService).rejectLeaveByPM(eq(10L), eq(USER_ID), any(LeaveDecisionDTO.class));
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "PROJECT_MANAGER")
        @DisplayName("✅ PM rejette sans corps de requête → 200 OK (dto vide par défaut)")
        void shouldRejectTeamLeaveWithoutBody() throws Exception {
            when(leaveService.rejectLeaveByPM(eq(10L), eq(USER_ID), any(LeaveDecisionDTO.class)))
                    .thenReturn(buildLeaveResponseDTO(10L));

            mockMvc.perform(post("/api/leaves/team/10/reject"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "EMPLOYEE")
        @DisplayName("❌ Rôle non-PM → 403 Forbidden")
        void shouldReturn403ForNonPM() throws Exception {
            mockMvc.perform(post("/api/leaves/team/10/reject"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/leaves/team/10/reject"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 10 — GET /api/leaves/pending
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/pending")
    class GetPendingLeaves {

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ GM récupère tous les congés en attente → 200 OK")
        void shouldReturnPendingLeavesAsGM() throws Exception {
            when(leaveService.getPendingLeaves()).thenReturn(List.of(buildLeaveResponseDTO(1L)));

            mockMvc.perform(get("/api/leaves/pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("✅ Admin récupère tous les congés en attente → 200 OK")
        void shouldReturnPendingLeavesAsAdmin() throws Exception {
            when(leaveService.getPendingLeaves()).thenReturn(List.of(buildLeaveResponseDTO(2L)));

            mockMvc.perform(get("/api/leaves/pending"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("❌ Rôle non autorisé → 403 Forbidden")
        void shouldReturn403ForUnauthorizedRole() throws Exception {
            mockMvc.perform(get("/api/leaves/pending"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/pending"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 11 — GET /api/leaves/all
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/all")
    class GetAllLeaves {

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ GM récupère tous les congés → 200 OK")
        void shouldReturnAllLeavesAsGM() throws Exception {
            when(leaveService.getAllLeaves())
                    .thenReturn(List.of(buildLeaveResponseDTO(1L), buildLeaveResponseDTO(2L)));

            mockMvc.perform(get("/api/leaves/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("✅ Admin récupère tous les congés → 200 OK")
        void shouldReturnAllLeavesAsAdmin() throws Exception {
            when(leaveService.getAllLeaves()).thenReturn(List.of());

            mockMvc.perform(get("/api/leaves/all"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("❌ Rôle non autorisé → 403 Forbidden")
        void shouldReturn403ForUnauthorizedRole() throws Exception {
            mockMvc.perform(get("/api/leaves/all"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/all"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 12 — POST /api/leaves/{id}/approve
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/leaves/{id}/approve")
    class ApproveLeave {

        @Test
        @WithMockUser(username = EMAIL, roles = "GENERAL_MANAGER")
        @DisplayName("✅ GM approuve un congé → 200 OK")
        void shouldApproveLeaveAsGM() throws Exception {
            when(leaveService.approveLeave(10L, USER_ID)).thenReturn(buildLeaveResponseDTO(10L));

            mockMvc.perform(post("/api/leaves/10/approve"))
                    .andExpect(status().isOk());

            verify(leaveService).approveLeave(10L, USER_ID);
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "ADMIN")
        @DisplayName("✅ Admin approuve un congé → 200 OK")
        void shouldApproveLeaveAsAdmin() throws Exception {
            when(leaveService.approveLeave(10L, USER_ID)).thenReturn(buildLeaveResponseDTO(10L));

            mockMvc.perform(post("/api/leaves/10/approve"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("❌ Rôle non autorisé → 403 Forbidden")
        void shouldReturn403ForUnauthorizedRole() throws Exception {
            mockMvc.perform(post("/api/leaves/10/approve"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/leaves/10/approve"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 13 — POST /api/leaves/{id}/reject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/leaves/{id}/reject")
    class RejectLeave {

        @Test
        @WithMockUser(username = EMAIL, roles = "GENERAL_MANAGER")
        @DisplayName("✅ GM rejette un congé avec motif → 200 OK")
        void shouldRejectLeaveAsGM() throws Exception {
            LeaveDecisionDTO decision = new LeaveDecisionDTO();
            when(leaveService.rejectLeave(eq(10L), eq(USER_ID), any(LeaveDecisionDTO.class)))
                    .thenReturn(buildLeaveResponseDTO(10L));

            mockMvc.perform(post("/api/leaves/10/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(decision)))
                    .andExpect(status().isOk());

            verify(leaveService).rejectLeave(eq(10L), eq(USER_ID), any(LeaveDecisionDTO.class));
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "GENERAL_MANAGER")
        @DisplayName("✅ GM rejette sans corps de requête → 200 OK (dto vide par défaut)")
        void shouldRejectLeaveWithoutBody() throws Exception {
            when(leaveService.rejectLeave(eq(10L), eq(USER_ID), any(LeaveDecisionDTO.class)))
                    .thenReturn(buildLeaveResponseDTO(10L));

            mockMvc.perform(post("/api/leaves/10/reject"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("❌ Rôle non autorisé → 403 Forbidden")
        void shouldReturn403ForUnauthorizedRole() throws Exception {
            mockMvc.perform(post("/api/leaves/10/reject"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/leaves/10/reject"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // resolveUserId — utilisateur introuvable en base
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("resolveUserId — utilisateur introuvable")
    class ResolveUserIdNotFound {

        @Test
        @WithMockUser(username = "ghost@test.com")
        @DisplayName("❌ Email authentifié absent de la base → 404 Not Found")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/leaves/my"))
                    .andExpect(status().isNotFound());
        }
    }
}