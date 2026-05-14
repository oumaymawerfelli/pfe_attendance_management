package com.example.pfe.dto;

import com.example.pfe.enums.ProjectStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectStatusHistoryDTO {
    private Long id;
    private ProjectStatus fromStatus;
    private ProjectStatus toStatus;
    private String changedBy;
    private LocalDateTime changedAt;
    private String reason;
}
