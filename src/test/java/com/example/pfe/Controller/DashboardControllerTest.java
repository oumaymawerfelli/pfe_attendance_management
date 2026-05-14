package com.example.pfe.Controller;

import com.example.pfe.Service.AttendanceOverviewService;
import com.example.pfe.Service.DashboardService;
import com.example.pfe.dto.AttendanceOverviewDTO;
import com.example.pfe.dto.DashboardStatsDTO;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@ContextConfiguration(classes = {
        DashboardController.class,
        DashboardControllerTest.TestSecurityConfig.class
})
@DisplayName("DashboardController — Tests Unitaires")
class DashboardControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean DashboardService          dashboardService;
    @MockBean AttendanceOverviewService attendanceOverviewService;

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

    // ── Fixtures ──────────────────────────────────────────────────────────────
    private DashboardStatsDTO    statsDTO;
    private AttendanceOverviewDTO overviewDTO;

    @BeforeEach
    void setUp() {
        // @Data + @Builder génère un constructeur package-private → on passe par le builder
        statsDTO = DashboardStatsDTO.builder()
                .totalEmployees(42)
                .newHiresThisMonth(3)
                .byDepartment(List.of(
                        DashboardStatsDTO.DeptStatDTO.builder()
                                .department("Engineering")
                                .count(20)
                                .build(),
                        DashboardStatsDTO.DeptStatDTO.builder()
                                .department("HR")
                                .count(8)
                                .build()
                ))
                .build();

        // AttendanceOverviewDTO utilise @Builder
        overviewDTO = AttendanceOverviewDTO.builder()
                .onTime(30)
                .late(5)
                .absent(3)
                .onLeave(2)
                .remote(10)
                .attendanceRate(85.5)
                .attendanceRateTrend(1.2)
                .weekLabels(List.of("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"))
                .weeklyRates(List.of(90.0, 88.0, 85.0, 87.0, 82.0, 80.0, 85.5))
                .lateSparkline(List.of(4L, 3L, 5L, 2L, 6L, 1L, 5L))
                .absentSparkline(List.of(2L, 1L, 3L, 1L, 2L, 0L, 3L))
                .onLeaveSparkline(List.of(1L, 2L, 2L, 3L, 1L, 2L, 2L))
                .remoteSparkline(List.of(8L, 9L, 10L, 11L, 10L, 12L, 10L))
                .build();
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 1 — GET /api/dashboard/stats
    // Autorisé : GENERAL_MANAGER, ADMIN
    // Refusé   : PROJECT_MANAGER, EMPLOYEE, non authentifié
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/dashboard/stats")
    class GetStats {

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ GENERAL_MANAGER reçoit les stats → 200 OK")
        void gmCanGetStats() throws Exception {
            when(dashboardService.getStats()).thenReturn(statsDTO);

            mockMvc.perform(get("/api/dashboard/stats"))
                    .andExpect(status().isOk());

            verify(dashboardService).getStats();
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("✅ ADMIN reçoit les stats → 200 OK")
        void adminCanGetStats() throws Exception {
            when(dashboardService.getStats()).thenReturn(statsDTO);

            mockMvc.perform(get("/api/dashboard/stats"))
                    .andExpect(status().isOk());

            verify(dashboardService).getStats();
        }

        @Test
        @WithMockUser(roles = "PROJECT_MANAGER")
        @DisplayName("❌ PROJECT_MANAGER est refusé → 403 Forbidden")
        void pmIsDenied() throws Exception {
            mockMvc.perform(get("/api/dashboard/stats"))
                    .andExpect(status().isForbidden());

            verify(dashboardService, never()).getStats();
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("❌ EMPLOYEE est refusé → 403 Forbidden")
        void employeeIsDenied() throws Exception {
            mockMvc.perform(get("/api/dashboard/stats"))
                    .andExpect(status().isForbidden());

            verify(dashboardService, never()).getStats();
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void unauthenticatedIsRejected() throws Exception {
            mockMvc.perform(get("/api/dashboard/stats"))
                    .andExpect(status().isUnauthorized());
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // GROUPE 2 — GET /api/dashboard/attendance-overview
    // Autorisé : GENERAL_MANAGER, ADMIN
    // Refusé   : PROJECT_MANAGER, EMPLOYEE, non authentifié
    // Paramètres : period (défaut "day"), department (optionnel), date (optionnel)
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/dashboard/attendance-overview")
    class GetAttendanceOverview {

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ Sans paramètre — period='day' par défaut → 200 OK")
        void gmCanGetOverviewWithDefaults() throws Exception {
            when(attendanceOverviewService.getOverview("day", null, null))
                    .thenReturn(overviewDTO);

            mockMvc.perform(get("/api/dashboard/attendance-overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.onTime").value(30))
                    .andExpect(jsonPath("$.late").value(5))
                    .andExpect(jsonPath("$.absent").value(3))
                    .andExpect(jsonPath("$.onLeave").value(2))
                    .andExpect(jsonPath("$.remote").value(10))
                    .andExpect(jsonPath("$.attendanceRate").value(85.5));

            verify(attendanceOverviewService).getOverview("day", null, null);
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ Avec period='week' → 200 OK")
        void gmCanGetOverviewByWeek() throws Exception {
            when(attendanceOverviewService.getOverview("week", null, null))
                    .thenReturn(overviewDTO);

            mockMvc.perform(get("/api/dashboard/attendance-overview")
                            .param("period", "week"))
                    .andExpect(status().isOk());

            verify(attendanceOverviewService).getOverview("week", null, null);
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ Avec period='month' → 200 OK")
        void gmCanGetOverviewByMonth() throws Exception {
            when(attendanceOverviewService.getOverview("month", null, null))
                    .thenReturn(overviewDTO);

            mockMvc.perform(get("/api/dashboard/attendance-overview")
                            .param("period", "month"))
                    .andExpect(status().isOk());

            verify(attendanceOverviewService).getOverview("month", null, null);
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ Avec department filtré → 200 OK")
        void gmCanGetOverviewWithDepartmentFilter() throws Exception {
            when(attendanceOverviewService.getOverview("day", "Engineering", null))
                    .thenReturn(overviewDTO);

            mockMvc.perform(get("/api/dashboard/attendance-overview")
                            .param("period", "day")
                            .param("department", "Engineering"))
                    .andExpect(status().isOk());

            verify(attendanceOverviewService).getOverview("day", "Engineering", null);
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ Avec date spécifique → 200 OK")
        void gmCanGetOverviewWithDateFilter() throws Exception {
            when(attendanceOverviewService.getOverview("day", null, "2025-06-01"))
                    .thenReturn(overviewDTO);

            mockMvc.perform(get("/api/dashboard/attendance-overview")
                            .param("period", "day")
                            .param("date", "2025-06-01"))
                    .andExpect(status().isOk());

            verify(attendanceOverviewService).getOverview("day", null, "2025-06-01");
        }

        @Test
        @WithMockUser(roles = "GENERAL_MANAGER")
        @DisplayName("✅ Tous les paramètres combinés → 200 OK")
        void gmCanGetOverviewWithAllParams() throws Exception {
            when(attendanceOverviewService.getOverview("week", "HR", "2025-06-01"))
                    .thenReturn(overviewDTO);

            mockMvc.perform(get("/api/dashboard/attendance-overview")
                            .param("period", "week")
                            .param("department", "HR")
                            .param("date", "2025-06-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.weekLabels.length()").value(7))
                    .andExpect(jsonPath("$.weeklyRates.length()").value(7))
                    .andExpect(jsonPath("$.lateSparkline.length()").value(7));

            verify(attendanceOverviewService).getOverview("week", "HR", "2025-06-01");
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("✅ ADMIN reçoit l'overview → 200 OK")
        void adminCanGetOverview() throws Exception {
            when(attendanceOverviewService.getOverview("day", null, null))
                    .thenReturn(overviewDTO);

            mockMvc.perform(get("/api/dashboard/attendance-overview"))
                    .andExpect(status().isOk());

            verify(attendanceOverviewService).getOverview("day", null, null);
        }

        @Test
        @WithMockUser(roles = "PROJECT_MANAGER")
        @DisplayName("❌ PROJECT_MANAGER est refusé → 403 Forbidden")
        void pmIsDenied() throws Exception {
            mockMvc.perform(get("/api/dashboard/attendance-overview"))
                    .andExpect(status().isForbidden());

            verify(attendanceOverviewService, never()).getOverview(any(), any(), any());
        }

        @Test
        @WithMockUser(roles = "EMPLOYEE")
        @DisplayName("❌ EMPLOYEE est refusé → 403 Forbidden")
        void employeeIsDenied() throws Exception {
            mockMvc.perform(get("/api/dashboard/attendance-overview"))
                    .andExpect(status().isForbidden());

            verify(attendanceOverviewService, never()).getOverview(any(), any(), any());
        }

        @Test
        @DisplayName("❌ Non authentifié → 401 Unauthorized")
        void unauthenticatedIsRejected() throws Exception {
            mockMvc.perform(get("/api/dashboard/attendance-overview"))
                    .andExpect(status().isUnauthorized());
        }
    }
}