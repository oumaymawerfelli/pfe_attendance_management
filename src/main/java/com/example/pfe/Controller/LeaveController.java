package com.example.pfe.Controller;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Service.LeaveService;
import com.example.pfe.dto.*;
import com.example.pfe.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
@Slf4j
public class LeaveController {

    private final LeaveService   leaveService;
    private final UserRepository userRepository;

    // ══════════════════════════════════════════════════════════
    // EMPLOYEE ENDPOINTS
    // ══════════════════════════════════════════════════════════

    /** POST /api/leaves/request — employee submits a leave request. */
    @PostMapping("/request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveResponseDTO> requestLeave(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody LeaveRequestDTO dto) {

        Long userId = resolveUserId(userDetails);
        log.info("Leave request from user ID: {}", userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(leaveService.requestLeave(userId, dto));
    }

    /** GET /api/leaves/my — employee's own leave history. */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LeaveResponseDTO>> getMyLeaves(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(leaveService.getMyLeaves(resolveUserId(userDetails)));
    }

    /** GET /api/leaves/my/balance — employee's current leave balance. */
    @GetMapping("/my/balance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveBalanceDTO> getMyBalance(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(leaveService.getMyBalance(resolveUserId(userDetails)));
    }

    // ══════════════════════════════════════════════════════════
    // PROJECT MANAGER ENDPOINTS  (own team only)
    // ══════════════════════════════════════════════════════════

    /**
     * GET /api/leaves/team/all
     * PM sees ALL leave requests (any status) for their active team members.
     */
    @GetMapping("/team/all")
    @PreAuthorize("hasRole('PROJECT_MANAGER')")
    public ResponseEntity<List<LeaveResponseDTO>> getTeamAllLeaves(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long pmId = resolveUserId(userDetails);
        log.info("PM {} fetching all team leaves", pmId);
        return ResponseEntity.ok(leaveService.getTeamAllLeaves(pmId));
    }

    /**
     * GET /api/leaves/team/pending
     * PM sees PENDING leave requests for their team (inbox).
     */
    @GetMapping("/team/pending")
    @PreAuthorize("hasRole('PROJECT_MANAGER')")
    public ResponseEntity<List<LeaveResponseDTO>> getTeamPendingLeaves(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long pmId = resolveUserId(userDetails);
        log.info("PM {} fetching pending team leaves", pmId);
        return ResponseEntity.ok(leaveService.getTeamPendingLeaves(pmId));
    }

    /**
     * POST /api/leaves/team/{id}/approve
     * PM approves a pending leave — validated that the requester is in their team.
     */
    @PostMapping("/team/{id}/approve")
    @PreAuthorize("hasRole('PROJECT_MANAGER')")
    public ResponseEntity<LeaveResponseDTO> approveTeamLeave(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long pmId = resolveUserId(userDetails);
        log.info("PM {} approving leave request {}", pmId, id);
        return ResponseEntity.ok(leaveService.approveLeaveByPM(id, pmId));
    }

    /**
     * POST /api/leaves/team/{id}/reject
     * PM rejects a pending leave — validated that the requester is in their team.
     */
    @PostMapping("/team/{id}/reject")
    @PreAuthorize("hasRole('PROJECT_MANAGER')")
    public ResponseEntity<LeaveResponseDTO> rejectTeamLeave(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) LeaveDecisionDTO dto) {

        Long pmId = resolveUserId(userDetails);
        log.info("PM {} rejecting leave request {}", pmId, id);
        if (dto == null) dto = new LeaveDecisionDTO();
        return ResponseEntity.ok(leaveService.rejectLeaveByPM(id, pmId, dto));
    }

    // ══════════════════════════════════════════════════════════
    // GENERAL MANAGER / ADMIN ENDPOINTS  (full access)
    // ══════════════════════════════════════════════════════════

    /** GET /api/leaves/pending — all pending requests in the system. */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<List<LeaveResponseDTO>> getPendingLeaves() {
        return ResponseEntity.ok(leaveService.getPendingLeaves());
    }

    /** GET /api/leaves/all — all requests from all employees. */
    @GetMapping("/all")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<List<LeaveResponseDTO>> getAllLeaves() {
        return ResponseEntity.ok(leaveService.getAllLeaves());
    }

    /** POST /api/leaves/{id}/approve — GM/Admin approves any request. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<LeaveResponseDTO> approveLeave(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(leaveService.approveLeave(id, resolveUserId(userDetails)));
    }

    /** POST /api/leaves/{id}/reject — GM/Admin rejects any request. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<LeaveResponseDTO> rejectLeave(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) LeaveDecisionDTO dto) {

        if (dto == null) dto = new LeaveDecisionDTO();
        return ResponseEntity.ok(leaveService.rejectLeave(id, resolveUserId(userDetails), dto));
    }

    // ══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════

    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"))
                .getId();
    }
}