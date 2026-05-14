package com.example.pfe.Controller;

import com.example.pfe.Service.AttendanceOverviewService;
import com.example.pfe.Service.DashboardService;
import com.example.pfe.dto.AttendanceOverviewDTO;
import com.example.pfe.dto.DashboardStatsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService          dashboardService;
    private final AttendanceOverviewService attendanceOverviewService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<DashboardStatsDTO> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }

    @GetMapping("/attendance-overview")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<AttendanceOverviewDTO> getAttendanceOverview(
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(required = false)     String department,
            @RequestParam(required = false)     String date) {
        return ResponseEntity.ok(
                attendanceOverviewService.getOverview(period, department, date)
        );
    }
}