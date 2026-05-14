package com.example.pfe.Controller;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Service.LeaveService;
import com.example.pfe.dto.*;
import com.example.pfe.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * GET /api/leaves/summary
     * Returns balance for all leave types + approval workflow.
     * Powers the Request Leave page — replaces the old /my/balance call on that page.
     */
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveSummaryDTO> getSummary(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(leaveService.getSummary(resolveUserId(userDetails)));
    }

    /**
     * POST /api/leaves/request
     * Content-Type: multipart/form-data
     *   leaveRequest  — JSON blob (application/json)
     *   attachment    — file (optional)
     *
     * NOTE: only ONE mapping for this path.
     * The old @RequestBody version is removed — it caused an ambiguous mapping error.
     */
    @PostMapping(value = "/request", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveResponseDTO> requestLeave(
            @RequestPart("leaveRequest")                         LeaveRequestDTO dto,
            @RequestPart(value = "attachment", required = false) MultipartFile   attachment,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = resolveUserId(userDetails);
        log.info("Leave request from user {}", userId);
        return ResponseEntity.ok(leaveService.requestLeave(userId, dto, attachment));
    }

    /**
     * POST /api/leaves/draft
     * Saves the current form state as DRAFT — no approval workflow triggered.
     * Content-Type: application/json
     */
    @PostMapping("/draft")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveResponseDTO> saveDraft(
            @RequestBody LeaveRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = resolveUserId(userDetails);
        log.info("Saving draft for user {}", userId);
        return ResponseEntity.ok(leaveService.saveDraft(userId, dto));
    }

    /**
     * GET /api/leaves/my
     * Returns the authenticated employee's full leave history.
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LeaveResponseDTO>> getMyLeaves(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(leaveService.getMyLeaves(resolveUserId(userDetails)));
    }

    /**
     * GET /api/leaves/my/balance
     * Returns the authenticated employee's current leave balance.
     * Still used by the dashboard widget; the request form uses /summary.
     */
    @GetMapping("/my/balance")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LeaveBalanceDTO> getMyBalance(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(leaveService.getMyBalance(resolveUserId(userDetails)));
    }

    // ══════════════════════════════════════════════════════════
    // PROJECT MANAGER ENDPOINTS  (own team only)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/team/all")
    @PreAuthorize("hasRole('PROJECT_MANAGER')")
    public ResponseEntity<List<LeaveResponseDTO>> getTeamAllLeaves(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long pmId = resolveUserId(userDetails);
        log.info("PM {} fetching all team leaves", pmId);
        return ResponseEntity.ok(leaveService.getTeamAllLeaves(pmId));
    }

    @GetMapping("/team/pending")
    @PreAuthorize("hasRole('PROJECT_MANAGER')")
    public ResponseEntity<List<LeaveResponseDTO>> getTeamPendingLeaves(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long pmId = resolveUserId(userDetails);
        log.info("PM {} fetching pending team leaves", pmId);
        return ResponseEntity.ok(leaveService.getTeamPendingLeaves(pmId));
    }

    @PostMapping("/team/{id}/approve")
    @PreAuthorize("hasRole('PROJECT_MANAGER')")
    public ResponseEntity<LeaveResponseDTO> approveTeamLeave(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long pmId = resolveUserId(userDetails);
        log.info("PM {} approving leave request {}", pmId, id);
        return ResponseEntity.ok(leaveService.approveLeaveByPM(id, pmId));
    }

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

    @GetMapping("/pending")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<List<LeaveResponseDTO>> getPendingLeaves() {
        return ResponseEntity.ok(leaveService.getPendingLeaves());
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<List<LeaveResponseDTO>> getAllLeaves() {
        return ResponseEntity.ok(leaveService.getAllLeaves());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<LeaveResponseDTO> approveLeave(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(leaveService.approveLeave(id, resolveUserId(userDetails)));
    }

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
    // PRIVATE HELPER
    // ══════════════════════════════════════════════════════════

    /**
     * Looks up the User entity from the JWT username (email) and returns its ID.
     * This is the only place we touch the database for auth resolution.
     */
    private Long resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found: " + userDetails.getUsername()))
                .getId();
    }
}