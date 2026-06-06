package com.example.pfe.Controller;

import com.example.pfe.Service.DemotivationBaselineService;
import com.example.pfe.dto.DemotivationScoreDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/demotivation")
@RequiredArgsConstructor
public class DemotivationController {

    private final DemotivationBaselineService demotivationService;

    /**
     * Classement de tous les employés actifs par risque de démotivation.
     * Exemple : GET /api/demotivation/scores?month=5&year=2026
     */
    @GetMapping("/scores")
    @PreAuthorize("hasAnyRole('ADMIN', 'GENERAL_MANAGER')")
    public ResponseEntity<List<DemotivationScoreDTO>> getAllScores(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        int m = (month != null) ? month : LocalDate.now().getMonthValue();
        int y = (year  != null) ? year  : LocalDate.now().getYear();

        return ResponseEntity.ok(demotivationService.computeAllScores(m, y));
    }

    /**
     * Score détaillé d'un seul employé (avec breakdown par indicateur).
     * Exemple : GET /api/demotivation/scores/89?month=5&year=2026
     */
    @GetMapping("/scores/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GENERAL_MANAGER')")
    public ResponseEntity<DemotivationScoreDTO> getUserScore(
            @PathVariable Long userId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {

        int m = (month != null) ? month : LocalDate.now().getMonthValue();
        int y = (year  != null) ? year  : LocalDate.now().getYear();

        return ResponseEntity.ok(demotivationService.computeScore(userId, m, y));
    }
}