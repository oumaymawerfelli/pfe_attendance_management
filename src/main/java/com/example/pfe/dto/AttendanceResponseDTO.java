package com.example.pfe.dto;

import com.example.pfe.enums.AttendanceStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceResponseDTO {

    private Long   id;

    // Employee info (HR / admin view)
    private Long   userId;
    private String userFullName;
    private String userDepartment;

    // ── 3 new fields for the presence sheet ──────────────
    private String userJobTitle;   // poste / job title
    private String userPhone;      // phone number
    private String userEmail;      // email address
    // ─────────────────────────────────────────────────────

    // Attendance data
    private LocalDate     date;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private AttendanceStatus status;

    private Double workDuration;   // null if not checked out yet
    private Double overtimeHours;  // null if not checked out yet
    private String notes;
}