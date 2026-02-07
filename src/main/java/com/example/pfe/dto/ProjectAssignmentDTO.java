package com.example.pfe.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectAssignmentDTO {
    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "Employee code is required")
    private String employeeCode;

    private String assignmentNotes;
}