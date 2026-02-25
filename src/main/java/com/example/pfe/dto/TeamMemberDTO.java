package com.example.pfe.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class TeamMemberDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private LocalDate assignedDate;
    private String assigningManager;
    private Long assignmentId;
}