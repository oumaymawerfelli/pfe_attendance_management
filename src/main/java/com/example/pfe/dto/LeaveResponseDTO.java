package com.example.pfe.dto;

import com.example.pfe.enums.LeaveStatus;
import com.example.pfe.enums.LeaveType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveResponseDTO {

    private Long        id;

    // ─── Employee info ────────────────────────────────────────
    private Long        userId;
    private String      userFullName;
    private String      userDepartment;

    // ─── Leave details ────────────────────────────────────────
    private LeaveType   leaveType;
    private LocalDate   startDate;
    private LocalDate   endDate;
    private Double      daysCount;
    private String      reason;

    // ─── Status ───────────────────────────────────────────────
    private LeaveStatus status;
    private String      rejectionReason;

    // ─── Approval info — names match mapper method calls ──────
    private String      approvedByFullName;   // ← matches .approvedByFullName()
    private String      approvedByRole;       // ← matches .approvedByRole()

    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;

    // ─── Document ─────────────────────────────────────────────
    /**
     * Relative path of the generated PDF on the server.
     * Non-null only after the leave is approved and the document uploaded.
     * Frontend uses this to show/hide the "View Document" button.
     */
    private String documentPath;
}