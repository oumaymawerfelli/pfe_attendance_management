package com.example.pfe.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TeamAssignmentDTO {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    @NotNull(message = "Assigning manager ID is required")
    private Long assigningManagerId;

    private String notes;

}