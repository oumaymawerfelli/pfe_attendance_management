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
@DisplayName("LeaveController - Tests")
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
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                    );
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
        return LeaveRequestDTO.builder()
                .leaveType(LeaveType.ANNUAL)   // any valid enum value
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .reason("Need some time off for personal reasons")  // >= 10 chars
                .build();
    }

    private LeaveResponseDTO buildLeaveResponseDTO(Long id) {
        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(id);
        return dto;
    }

    private LeaveBalanceDTO buildLeaveBalanceDTO() {
        LeaveBalanceDTO dto = new LeaveBalanceDTO();
        // populate fields as needed
        return dto;
    }

    @BeforeEach
    void stubUserResolution() {
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(buildUser(USER_ID, EMAIL)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/leaves/request
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/leaves/request")
    class RequestLeave {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("Employee submits leave request → 201 Created")
        void shouldCreateLeaveRequest() throws Exception {
            LeaveRequestDTO requestDTO  = buildLeaveRequestDTO();
            LeaveResponseDTO responseDTO = buildLeaveResponseDTO(1L);

            when(leaveService.requestLeave(eq(USER_ID), any(LeaveRequestDTO.class)))
                    .thenReturn(responseDTO);

            mockMvc.perform(post("/api/leaves/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDTO)))
                    .andExpect(status().isCreated());

            verify(leaveService).requestLeave(eq(USER_ID), any(LeaveRequestDTO.class));
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/leaves/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildLeaveRequestDTO())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = "ghost@test.com")
        @DisplayName("Authenticated user not in DB → 404 Not Found")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/leaves/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildLeaveRequestDTO())))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/leaves/my
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/my")
    class GetMyLeaves {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("Returns employee's own leave list → 200 OK")
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
        @DisplayName("Returns empty list when no leaves → 200 OK")
        void shouldReturnEmptyList() throws Exception {
            when(leaveService.getMyLeaves(USER_ID)).thenReturn(List.of());

            mockMvc.perform(get("/api/leaves/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/my"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/leaves/my/balance
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/my/balance")
    class GetMyBalance {

        @Test
        @WithMockUser(username = EMAIL)
        @DisplayName("Returns employee's leave balance → 200 OK")
        void shouldReturnBalance() throws Exception {
            when(leaveService.getMyBalance(USER_ID)).thenReturn(buildLeaveBalanceDTO());

            mockMvc.perform(get("/api/leaves/my/balance"))
                    .andExpect(status().isOk());

            verify(leaveService).getMyBalance(USER_ID);
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/my/balance"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/leaves/team/all
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/team/all")
    class GetTeamAllLeaves {

        @Test
        @WithMockUser(username = EMAIL, roles = "PROJECT_MANAGER")
        @DisplayName("PM fetches all team leaves → 200 OK")
        void shouldReturnAllTeamLeaves() throws Exception {
            List<LeaveResponseDTO> leaves = List.of(buildLeaveResponseDTO(1L));
            when(leaveService.getTeamAllLeaves(USER_ID)).thenReturn(leaves);

            mockMvc.perform(get("/api/leaves/team/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            verify(leaveService).getTeamAllLeaves(USER_ID);
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "USER")
        @DisplayName("Non-PM role → 403 Forbidden")
        void shouldReturn403ForNonPM() throws Exception {
            mockMvc.perform(get("/api/leaves/team/all"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/team/all"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/leaves/team/pending
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/team/pending")
    class GetTeamPendingLeaves {

        @Test
        @WithMockUser(username = EMAIL, roles = "PROJECT_MANAGER")
        @DisplayName("PM fetches pending team leaves → 200 OK")
        void shouldReturnPendingTeamLeaves() throws Exception {
            List<LeaveResponseDTO> leaves = List.of(buildLeaveResponseDTO(5L));
            when(leaveService.getTeamPendingLeaves(USER_ID)).thenReturn(leaves);

            mockMvc.perform(get("/api/leaves/team/pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            verify(leaveService).getTeamPendingLeaves(USER_ID);
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "USER")
        @DisplayName("Non-PM role → 403 Forbidden")
        void shouldReturn403ForNonPM() throws Exception {
            mockMvc.perform(get("/api/leaves/team/pending"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/team/pending"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/leaves/team/{id}/approve
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/leaves/team/{id}/approve")
    class ApproveTeamLeave {

        @Test
        @WithMockUser(username = EMAIL, roles = "PROJECT_MANAGER")
        @DisplayName("PM approves team leave → 200 OK")
        void shouldApproveTeamLeave() throws Exception {
            when(leaveService.approveLeaveByPM(10L, USER_ID)).thenReturn(buildLeaveResponseDTO(10L));

            mockMvc.perform(post("/api/leaves/team/10/approve"))
                    .andExpect(status().isOk());

            verify(leaveService).approveLeaveByPM(10L, USER_ID);
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "PROJECT_MANAGER")
        @DisplayName("Passes correct leave ID to service")
        void shouldPassCorrectLeaveId() throws Exception {
            when(leaveService.approveLeaveByPM(42L, USER_ID)).thenReturn(buildLeaveResponseDTO(42L));

            mockMvc.perform(post("/api/leaves/team/42/approve"))
                    .andExpect(status().isOk());

            verify(leaveService).approveLeaveByPM(42L, USER_ID);
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "USER")
        @DisplayName("Non-PM role → 403 Forbidden")
        void shouldReturn403ForNonPM() throws Exception {
            mockMvc.perform(post("/api/leaves/team/10/approve"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/leaves/team/10/approve"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/leaves/team/{id}/reject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/leaves/team/{id}/reject")
    class RejectTeamLeave {

        @Test
        @WithMockUser(username = EMAIL, roles = "PROJECT_MANAGER")
        @DisplayName("PM rejects team leave with reason → 200 OK")
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
        @DisplayName("PM rejects team leave without body → 200 OK (dto defaults to empty)")
        void shouldRejectTeamLeaveWithoutBody() throws Exception {
            when(leaveService.rejectLeaveByPM(eq(10L), eq(USER_ID), any(LeaveDecisionDTO.class)))
                    .thenReturn(buildLeaveResponseDTO(10L));

            mockMvc.perform(post("/api/leaves/team/10/reject"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "USER")
        @DisplayName("Non-PM role → 403 Forbidden")
        void shouldReturn403ForNonPM() throws Exception {
            mockMvc.perform(post("/api/leaves/team/10/reject"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/leaves/team/10/reject"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/leaves/pending
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/pending")
    class GetPendingLeaves {

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("GM fetches all pending leaves → 200 OK")
        void shouldReturnPendingLeavesAsGM() throws Exception {
            when(leaveService.getPendingLeaves()).thenReturn(List.of(buildLeaveResponseDTO(1L)));

            mockMvc.perform(get("/api/leaves/pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin fetches all pending leaves → 200 OK")
        void shouldReturnPendingLeavesAsAdmin() throws Exception {
            when(leaveService.getPendingLeaves()).thenReturn(List.of(buildLeaveResponseDTO(2L)));

            mockMvc.perform(get("/api/leaves/pending"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Non-GM/Admin role → 403 Forbidden")
        void shouldReturn403ForUnauthorizedRole() throws Exception {
            mockMvc.perform(get("/api/leaves/pending"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/pending"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/leaves/all
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/leaves/all")
    class GetAllLeaves {

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("GM fetches all leaves → 200 OK")
        void shouldReturnAllLeavesAsGM() throws Exception {
            when(leaveService.getAllLeaves()).thenReturn(List.of(buildLeaveResponseDTO(1L), buildLeaveResponseDTO(2L)));

            mockMvc.perform(get("/api/leaves/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin fetches all leaves → 200 OK")
        void shouldReturnAllLeavesAsAdmin() throws Exception {
            when(leaveService.getAllLeaves()).thenReturn(List.of());

            mockMvc.perform(get("/api/leaves/all"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Non-GM/Admin role → 403 Forbidden")
        void shouldReturn403ForUnauthorizedRole() throws Exception {
            mockMvc.perform(get("/api/leaves/all"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/leaves/all"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/leaves/{id}/approve
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/leaves/{id}/approve")
    class ApproveLeave {

        @Test
        @WithMockUser(username = EMAIL, roles = "GENERAL_MANAGER")
        @DisplayName("GM approves any leave → 200 OK")
        void shouldApproveLeaveAsGM() throws Exception {
            when(leaveService.approveLeave(10L, USER_ID)).thenReturn(buildLeaveResponseDTO(10L));

            mockMvc.perform(post("/api/leaves/10/approve"))
                    .andExpect(status().isOk());

            verify(leaveService).approveLeave(10L, USER_ID);
        }

        @Test
        @WithMockUser(username = EMAIL, roles = "ADMIN")
        @DisplayName("Admin approves any leave → 200 OK")
        void shouldApproveLeaveAsAdmin() throws Exception {
            when(leaveService.approveLeave(10L, USER_ID)).thenReturn(buildLeaveResponseDTO(10L));

            mockMvc.perform(post("/api/leaves/10/approve"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Non-GM/Admin role → 403 Forbidden")
        void shouldReturn403ForUnauthorizedRole() throws Exception {
            mockMvc.perform(post("/api/leaves/10/approve"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/leaves/10/approve"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/leaves/{id}/reject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/leaves/{id}/reject")
    class RejectLeave {

        @Test
        @WithMockUser(username = EMAIL, roles = "GENERAL_MANAGER")
        @DisplayName("GM rejects any leave with reason → 200 OK")
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
        @DisplayName("GM rejects leave without body → 200 OK (dto defaults to empty)")
        void shouldRejectLeaveWithoutBody() throws Exception {
            when(leaveService.rejectLeave(eq(10L), eq(USER_ID), any(LeaveDecisionDTO.class)))
                    .thenReturn(buildLeaveResponseDTO(10L));

            mockMvc.perform(post("/api/leaves/10/reject"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("Non-GM/Admin role → 403 Forbidden")
        void shouldReturn403ForUnauthorizedRole() throws Exception {
            mockMvc.perform(post("/api/leaves/10/reject"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/leaves/10/reject"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // resolveUserId — user not found
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("resolveUserId — user not found")
    class ResolveUserIdNotFound {

        @Test
        @WithMockUser(username = "ghost@test.com")
        @DisplayName("Authenticated email not in DB → 404 Not Found")
        void shouldReturn404WhenUserNotFound() throws Exception {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/leaves/my"))
                    .andExpect(status().isNotFound());
        }
    }
}