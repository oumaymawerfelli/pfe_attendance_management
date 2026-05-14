package com.example.pfe.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveBalanceDTO {

    private Integer year;

    // Annual
    private Double annualTotal;
    private Double annualTaken;
    private Double annualRemaining;   // calculated: total - taken

    // Sick
    private Double sickTotal;
    private Double sickTaken;
    private Double sickRemaining;     // calculated: total - taken  ← was missing

    // Unpaid
    private Double unpaidTotal;
    private Double unpaidTaken;
}