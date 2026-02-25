package com.example.pfe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamAssignmentResponseDTO {
    private Long id;
    private Long projectId;
    private String projectName;
    private String projectCode;
    private Long employeeId;
    private String employeeFirstName;
    private String employeeLastName;
    private String employeeEmail;
    private Long assigningManagerId;
    private String assigningManagerName;
    private LocalDate addedDate;
    private boolean active;
}