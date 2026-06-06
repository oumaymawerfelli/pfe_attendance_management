package com.example.pfe.Controller;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Service.MoodService;
import com.example.pfe.dto.MoodDTO;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/mood")
@RequiredArgsConstructor
public class MoodController {

    private final MoodService  moodService;
    private final UserRepository userRepository;

    // ── Employee: get today's status ──────────────────────
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MoodDTO.StatusDTO> getStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(moodService.getStatus(resolveId(userDetails)));
    }

    // ── Employee: submit mood ─────────────────────────────
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MoodDTO.StatusDTO> submit(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> body) {

        int    score = Integer.parseInt(body.get("score").toString());
        String note  = body.get("note") != null ? body.get("note").toString() : null;

        Long userId = resolveId(userDetails);
        moodService.submit(userId, score, note);
        return ResponseEntity.ok(moodService.getStatus(userId));
    }

    // ── Admin / HR: overview ──────────────────────────────
    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER') or hasRole('HR')")
    public ResponseEntity<MoodDTO.OverviewDTO> getOverview(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String department) {
        return ResponseEntity.ok(moodService.getOverview(date, department));
    }

    // ── Helper ────────────────────────────────────────────
    private Long resolveId(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getId();
    }
}