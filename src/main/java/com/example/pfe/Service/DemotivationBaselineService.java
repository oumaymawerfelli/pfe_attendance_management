package com.example.pfe.Service;

import com.example.pfe.Repository.AttendanceRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.DemotivationScoreDTO;
import com.example.pfe.entities.User;
import com.example.pfe.enums.AttendanceStatus;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemotivationBaselineService {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository       userRepository;

    // ── Poids de la baseline (analyse métier). Somme = 1.0 ──
    private static final double W_ABSENCE  = 0.45;
    private static final double W_LATE     = 0.25;
    private static final double W_HALF_DAY = 0.15;
    private static final double W_EARLY    = 0.15;

    // ── Seuils calibrés sur la distribution réelle des scores ──
    private static final double THRESHOLD_MEDIUM = 0.15;
    private static final double THRESHOLD_HIGH   = 0.35;

    /**
     * Calcule le score de risque de démotivation d'un employé pour un mois donné,
     * uniquement à partir de ses indicateurs de présence.
     */
    @Transactional(readOnly = true)
    public DemotivationScoreDTO computeScore(Long userId, int month, int year) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));

        // ── 1) Comptages bruts depuis la table attendance ──
        int present = attendanceRepository.countByUserIdAndStatusAndMonthAndYear(
                userId, AttendanceStatus.PRESENT, month, year);
        int late = attendanceRepository.countByUserIdAndStatusAndMonthAndYear(
                userId, AttendanceStatus.LATE, month, year);
        int half = attendanceRepository.countByUserIdAndStatusAndMonthAndYear(
                userId, AttendanceStatus.HALF_DAY, month, year);
        int early = attendanceRepository.countByUserIdAndStatusAndMonthAndYear(
                userId, AttendanceStatus.EARLY_DEPARTURE, month, year);


        int workedDays   = present + late + half + early;
        int expectedDays = countExpectedWorkingDays(month, year);
        int absent       = Math.max(0, expectedDays - workedDays);

// ── Garde : aucun pointage du tout = pas de données, pas un "absent total" ──
        if (workedDays == 0) {
            Map<String, Double> empty = new LinkedHashMap<>();
            empty.put("absence", 0.0);
            empty.put("retard", 0.0);
            empty.put("demiJournee", 0.0);
            empty.put("departAnticipe", 0.0);
            return DemotivationScoreDTO.builder()
                    .userId(userId)
                    .userFullName(user.getFirstName() + " " + user.getLastName())
                    .score(0.0)
                    .level("INCONNU")          // ou "SANS_DONNÉES"
                    .breakdown(empty)
                    .build();
        }

        // ── 2) Normalisation des 4 indicateurs sur [0,1] ──
        double absenceRate = safeDiv(absent, expectedDays);  // / jours attendus
        double lateRate    = safeDiv(late,  workedDays);     // / jours travaillés
        double halfRate    = safeDiv(half,  workedDays);     // / jours travaillés
        double earlyRate   = safeDiv(early, workedDays);     // / jours travaillés

        // ── 3) Score pondéré ──
        double score = W_ABSENCE  * absenceRate
                + W_LATE     * lateRate
                + W_HALF_DAY * halfRate
                + W_EARLY    * earlyRate;
        score = clamp(score);

        // ── 4) Détail par indicateur (transparence) ──
        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("absence",        round(W_ABSENCE  * absenceRate));
        breakdown.put("retard",         round(W_LATE     * lateRate));
        breakdown.put("demiJournee",    round(W_HALF_DAY * halfRate));
        breakdown.put("departAnticipe", round(W_EARLY    * earlyRate));

        log.debug("Score démotivation user {} ({}/{}) = {} [{}]",
                userId, month, year, round(score), toLevel(score));

        return DemotivationScoreDTO.builder()
                .userId(userId)
                .userFullName(user.getFirstName() + " " + user.getLastName())
                .score(round(score))
                .level(toLevel(score))
                .breakdown(breakdown)
                .build();
    }
    /**
     * Calcule le score de démotivation de TOUS les employés actifs pour un mois,
     * trié du plus à risque au moins à risque (idéal pour le tableau de bord RH).
     */
    @Transactional(readOnly = true)
    public List<DemotivationScoreDTO> computeAllScores(int month, int year) {
        return userRepository.findAllByActiveTrue()
                .stream()
                .map(user -> computeScore(user.getId(), month, year))
                .filter(dto -> !"INCONNU".equals(dto.getLevel()))   // on écarte les sans-données
                .sorted(Comparator.comparingDouble(DemotivationScoreDTO::getScore).reversed())
                .collect(Collectors.toList());
    }

    // ── Helpers ─────────────────────────────────────────────

    /** Jours ouvrés (lun-ven) du mois, sans compter le futur si mois courant. */
    private int countExpectedWorkingDays(int month, int year) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end   = YearMonth.of(year, month).atEndOfMonth();
        LocalDate today = LocalDate.now();
        if (end.isAfter(today)) end = today;

        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() < 6) count++; // 1=lun … 5=ven
        }
        return count;
    }

    private String toLevel(double score) {
        if (score >= THRESHOLD_HIGH)   return "ÉLEVÉ";
        if (score >= THRESHOLD_MEDIUM) return "MOYEN";
        return "FAIBLE";
    }

    private double safeDiv(double a, double b) { return b == 0 ? 0.0 : clamp(a / b); }
    private double clamp(double v)             { return Math.max(0.0, Math.min(1.0, v)); }
    private double round(double v)             { return Math.round(v * 100.0) / 100.0; }
}