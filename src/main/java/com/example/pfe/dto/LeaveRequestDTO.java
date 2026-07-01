package com.example.pfe.dto;

import com.example.pfe.enums.LeaveType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class LeaveRequestDTO {

    @NotNull(message = "Leave type is required")
    private LeaveType leaveType;

    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /**
     * Pre-calculated working days sent by the frontend.
     * The backend recalculates independently and uses its own value —
     * this field is stored on the draft for display purposes only.
     */
    private Double duration;

    @Size(min = 10, max = 200, message = "Reason must be between 10 and 200 characters")
    private String reason;

    // ─── Exit Authorization fields (only used when leaveType == EXIT_AUTHORIZATION) ───
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime exitTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime returnTime;
}