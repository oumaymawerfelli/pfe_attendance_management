package com.example.pfe.dto;

import com.example.pfe.enums.ProjectStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectResponseDTO {
    private Long id;
    private String code; // Ici c'est OK, on le retourne dans la réponse
    private String name;
    private String description;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime assignmentDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Informations du manager (seulement en réponse)
    private String projectManagerName;
    private String projectManagerEmail;
}