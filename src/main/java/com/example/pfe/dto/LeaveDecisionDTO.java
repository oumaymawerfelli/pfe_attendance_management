package com.example.pfe.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveDecisionDTO {

    // Optional — only required when rejecting
    private String rejectionReason;
}