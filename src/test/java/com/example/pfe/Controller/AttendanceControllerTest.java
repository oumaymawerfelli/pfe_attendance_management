package com.example.pfe.Controller;
import org.springframework.security.test.context.support.WithMockUser;
import com.example.pfe.Controller.AttendanceController;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Service.AttendanceService;
import com.example.pfe.Service.JwtService;
import com.example.pfe.dto.AttendanceFilterDTO;
import com.example.pfe.dto.AttendanceResponseDTO;
import com.example.pfe.dto.AttendanceSummaryDTO;
import com.example.pfe.entities.User;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AttendanceController.class)
@ContextConfiguration(classes = {
        AttendanceController.class,
        AttendanceControllerTest.TestSecurityConfig.class
})
@DisplayName("AttendanceController - Tests")
class AttendanceControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AttendanceService attendanceService;
    @MockBean UserRepository    userRepository;
    @MockBean JwtService        jwtService; // prevents JwtAuthenticationFilter wiring failure

    // ── Minimal security config (mirrors AttendanceConfigControllerTest) ──────
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

    // ── Shared fixtures ───────────────────────────────────────────────────────

    private static final Long   USER_ID    = 1L;
    private static final String USER_EMAIL = "user@test.com";

    /** Stub userRepository so resolveUserId() always returns USER_ID */
    @BeforeEach
    void stubUserRepository() {
        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(USER_ID);
        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(mockUser));
    }

    private AttendanceResponseDTO buildResponse() {
        return AttendanceResponseDTO.builder()
                .id(10L)
                .checkIn(LocalDateTime.of(2025, 4, 8, 9, 0))  // was checkInTime → checkIn
                .build();
    }

    private AttendanceSummaryDTO buildSummary() {
        return AttendanceSummaryDTO.builder()
                .totalWorkingDays(20)   // was totalDays → totalWorkingDays
                .presentDays(18)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/attendance/check-in
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/attendance/check-in")
    class CheckIn {

        @Test
        @WithMockUser(username = USER_EMAIL)
        @DisplayName("Authenticated user can check in → 200 OK")
        void shouldCheckInForAuthenticatedUser() throws Exception {
            doNothing().when(attendanceService).checkIn(USER_ID);

            mockMvc.perform(post("/api/attendance/check-in"))
                    .andExpect(status().isOk());

            verify(attendanceService).checkIn(USER_ID);
        }

        @Test
        @DisplayName("Unauthenticated request → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/attendance/check-in"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/attendance/logout-checkout
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("POST /api/attendance/logout-checkout")
    class LogoutCheckout {

        @Test
        @WithMockUser(username = USER_EMAIL)
        @DisplayName("Authenticated user can check out with notes → 200 OK")
        void shouldCheckOutWithNotes() throws Exception {
            doNothing().when(attendanceService).checkOutOnLogout(eq(USER_ID), anyString());

            mockMvc.perform(post("/api/attendance/logout-checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("notes", "leaving early"))))
                    .andExpect(status().isOk());

            verify(attendanceService).checkOutOnLogout(USER_ID, "leaving early");
        }

        @Test
        @WithMockUser(username = USER_EMAIL)
        @DisplayName("Authenticated user can check out without body → 200 OK")
        void shouldCheckOutWithoutBody() throws Exception {
            doNothing().when(attendanceService).checkOutOnLogout(eq(USER_ID), isNull());

            mockMvc.perform(post("/api/attendance/logout-checkout"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Unauthenticated request → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/attendance/logout-checkout"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/attendance/my
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/attendance/my")
    class GetMyAttendance {

        @Test
        @WithMockUser(username = USER_EMAIL)
        @DisplayName("Returns own attendance list → 200 OK")
        void shouldReturnMyAttendance() throws Exception {
            when(attendanceService.getMyAttendance(eq(USER_ID), any(AttendanceFilterDTO.class)))
                    .thenReturn(List.of(buildResponse()));

            mockMvc.perform(get("/api/attendance/my")
                            .param("month", "4")
                            .param("year",  "2025"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(10));
        }

        @Test
        @WithMockUser(username = USER_EMAIL)
        @DisplayName("Works without optional params → 200 OK")
        void shouldWorkWithoutParams() throws Exception {
            when(attendanceService.getMyAttendance(eq(USER_ID), any())).thenReturn(List.of());

            mockMvc.perform(get("/api/attendance/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Unauthenticated request → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/attendance/my"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/attendance/my/summary
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/attendance/my/summary")
    class GetMySummary {

        @Test
        @WithMockUser(username = USER_EMAIL)
        @DisplayName("Returns own summary → 200 OK")
        void shouldReturnMySummary() throws Exception {
            when(attendanceService.getMySummary(eq(USER_ID), any(AttendanceFilterDTO.class)))
                    .thenReturn(buildSummary());

            mockMvc.perform(get("/api/attendance/my/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalWorkingDays").value(20))  // was totalDays
                    .andExpect(jsonPath("$.presentDays").value(18));
        }

        @Test
        @DisplayName("Unauthenticated request → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/attendance/my/summary"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/attendance/my/day
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/attendance/my/day")
    class GetMyDayRecord {

        @Test
        @WithMockUser(username = USER_EMAIL)
        @DisplayName("Returns day record for given date → 200 OK")
        void shouldReturnDayRecord() throws Exception {
            when(attendanceService.getMyDayRecord(USER_ID, LocalDate.of(2025, 4, 8)))
                    .thenReturn(buildResponse());

            mockMvc.perform(get("/api/attendance/my/day")
                            .param("date", "2025-04-08"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));
        }

        @Test
        @DisplayName("Unauthenticated request → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/attendance/my/day").param("date", "2025-04-08"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PUT /api/attendance/fix-checkout
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("PUT /api/attendance/fix-checkout")
    class FixMissedCheckout {

        @Test
        @WithMockUser(username = USER_EMAIL)
        @DisplayName("Authenticated user can fix missed checkout → 200 OK")
        void shouldFixMissedCheckout() throws Exception {
            when(attendanceService.fixMissedCheckout(USER_ID, "17:30"))
                    .thenReturn(buildResponse());

            mockMvc.perform(put("/api/attendance/fix-checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("checkOutTime", "17:30"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));
        }

        @Test
        @DisplayName("Unauthenticated request → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(put("/api/attendance/fix-checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("checkOutTime", "17:30"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/attendance/missed-checkout
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/attendance/missed-checkout")
    class HasMissedCheckout {

        @Test
        @WithMockUser(username = USER_EMAIL)
        @DisplayName("Returns true when checkout was missed → 200 OK")
        void shouldReturnTrueWhenMissed() throws Exception {
            when(attendanceService.hasMissedCheckout(USER_ID)).thenReturn(true);

            mockMvc.perform(get("/api/attendance/missed-checkout"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }

        @Test
        @DisplayName("Unauthenticated request → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/attendance/missed-checkout"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/attendance/team
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/attendance/team")
    class GetTeamAttendance {

        @Test
        @WithMockUser(username = USER_EMAIL, roles = "PROJECT_MANAGER")
        @DisplayName("PROJECT_MANAGER can view team attendance → 200 OK")
        void shouldReturnTeamAttendanceForPM() throws Exception {
            when(attendanceService.getTeamAttendance(eq(USER_ID), any())).thenReturn(List.of(buildResponse()));

            mockMvc.perform(get("/api/attendance/team"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @WithMockUser(username = USER_EMAIL, roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(get("/api/attendance/team"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated request → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/attendance/team"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/attendance/all
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/attendance/all")
    class GetAllAttendance {

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("GENERAL_MANAGER can view all attendance → 200 OK")
        void shouldReturnAllForGeneralManager() throws Exception {
            when(attendanceService.getAllAttendance(any())).thenReturn(List.of(buildResponse()));

            mockMvc.perform(get("/api/attendance/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("ADMIN can view all attendance → 200 OK")
        void shouldReturnAllForAdmin() throws Exception {
            when(attendanceService.getAllAttendance(any())).thenReturn(List.of());

            mockMvc.perform(get("/api/attendance/all"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("EMPLOYEE is denied → 403 Forbidden")
        void shouldReturn403ForEmployee() throws Exception {
            mockMvc.perform(get("/api/attendance/all"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Unauthenticated request → 401 Unauthorized")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/attendance/all"))
                    .andExpect(status().isUnauthorized());
        }
    }
}