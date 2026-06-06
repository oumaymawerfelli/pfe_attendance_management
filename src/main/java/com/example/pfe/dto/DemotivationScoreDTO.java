package com.example.pfe.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class DemotivationScoreDTO {
    private Long   userId;
    private String userFullName;
    private double score;          // 0.0 à 1.0
    private String level;          // FAIBLE / MOYEN / ÉLEVÉ
    private Map<String, Double> breakdown;  // contribution de chaque indicateur
}