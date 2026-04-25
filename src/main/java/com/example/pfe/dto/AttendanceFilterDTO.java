package com.example.pfe.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceFilterDTO {

    private Integer month;   // 1–12, null = all months
    private Integer year;    // e.g. 2025, null = current year
}