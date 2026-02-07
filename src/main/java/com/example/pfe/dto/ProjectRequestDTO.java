package com.example.pfe.dto;

import com.example.pfe.enums.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectRequestDTO {

    @NotBlank(message = "Project name is required")
    private String name;

    private String description;

    private ProjectStatus status;

    private LocalDate startDate;

    private LocalDate endDate;

    private Long projectManagerId; // ID du manager, pas l'objet entier

    // PAS de champ "code" ici - il est généré automatiquement
}