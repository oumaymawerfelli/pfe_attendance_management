package com.example.pfe.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class DemotivationScoreDTO {
    private Long   userId;
    private String userFullName;
    private double score;                   // 0.0 à 1.0 (baseline)
    private String level;                   // FAIBLE / MOYEN / ÉLEVÉ (baseline)
    private Map<String, Double> breakdown;  // contribution de chaque indicateur

    // ── Enrichissement ML ──
    private Boolean mlArisque;       // verdict de l'arbre de décision
    private Double  mlProba;         // probabilité de risque (0.0 à 1.0)
    private Boolean verdictHybride;  // baseline OU ml → à risque
}