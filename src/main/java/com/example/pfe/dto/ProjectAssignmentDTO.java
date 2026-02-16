package com.example.pfe.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectAssignmentDTO {
    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "Employee email is required")
    private String employeeEmail;

    private String assignmentNotes;
}