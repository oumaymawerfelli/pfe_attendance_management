package com.example.pfe.Controller;

import com.example.pfe.Service.AttendanceService;
import com.example.pfe.dto.AttendanceFilterDTO;
import com.example.pfe.dto.AttendanceResponseDTO;
import com.example.pfe.dto.AttendanceSummaryDTO;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Slf4j
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final UserRepository    userRepository;

    // ── Check-in / Check-out ──────────────────────────────────────────────────

    @PostMapping("/check-in")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> checkIn(@AuthenticationPrincipal UserDetails userDetails) {
        attendanceService.checkIn(resolveUserId(userDetails));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout-checkout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> checkOutOnLogout(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) Map<String, String> body) {
        attendanceService.checkOutOnLogout(resolveUserId(userDetails),
                body != null ? body.get("notes") : null);
        return ResponseEntity.ok().build();
    }

    // ── Employee — own data ───────────────────────────────────────────────────

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AttendanceResponseDTO>> getMyAttendance(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        AttendanceFilterDTO filter = AttendanceFilterDTO.builder().month(month).year(year).build();
        return ResponseEntity.ok(
                attendanceService.getMyAttendance(resolveUserId(userDetails), filter));
    }

    @GetMapping("/my/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceSummaryDTO> getMySummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        AttendanceFilterDTO filter = AttendanceFilterDTO.builder().month(month).year(year).build();
        return ResponseEntity.ok(
                attendanceService.getMySummary(resolveUserId(userDetails), filter));
    }

    /**
     * GET /api/attendance/my/day?date=2025-04-08
     * Returns the single attendance record for the authenticated user on a specific date.
     * Returns 404 if no record exists (absent / weekend / future).
     */
    @GetMapping("/my/day")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceResponseDTO> getMyDayRecord(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long userId = resolveUserId(userDetails);
        log.info("Day detail request — user {} date {}", userId, date);
        return ResponseEntity.ok(attendanceService.getMyDayRecord(userId, date));
    }

    @PutMapping("/fix-checkout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttendanceResponseDTO> fixMissedCheckout(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {

        return ResponseEntity.ok(
                attendanceService.fixMissedCheckout(
                        resolveUserId(userDetails), body.get("checkOutTime")));
    }

    @GetMapping("/missed-checkout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> hasMissedCheckout(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(
                attendanceService.hasMissedCheckout(resolveUserId(userDetails)));
    }

    // ── Project Manager — team only ───────────────────────────────────────────

    @GetMapping("/team")
    @PreAuthorize("hasRole('PROJECT_MANAGER')")
    public ResponseEntity<List<AttendanceResponseDTO>> getTeamAttendance(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        AttendanceFilterDTO filter = AttendanceFilterDTO.builder().month(month).year(year).build();
        return ResponseEntity.ok(
                attendanceService.getTeamAttendance(resolveUserId(userDetails), filter));
    }

    // ── General Manager / Admin ───────────────────────────────────────────────

    @GetMapping("/all")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<List<AttendanceResponseDTO>> getAllAttendance(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        AttendanceFilterDTO filter = AttendanceFilterDTO.builder().month(month).year(year).build();
        return ResponseEntity.ok(attendanceService.getAllAttendance(filter));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"))
                .getId();
    }
}