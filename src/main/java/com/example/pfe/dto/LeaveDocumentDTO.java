package com.example.pfe.dto;

import com.example.pfe.entities.LeaveDocument.DocumentCategory;
import com.example.pfe.entities.LeaveDocument.DocumentStatus;
import com.example.pfe.entities.LeaveDocument.DocumentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class LeaveDocumentDTO {

    // ── Document info ──────────────────────────────
    private Long id;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private DocumentType documentType;
    private DocumentCategory documentCategory;
    private DocumentStatus status;
    private String adminNotes;
    private LocalDateTime uploadedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime generatedAt;
    private boolean generatedBySystem;

    // ── Leave request info ─────────────────────────
    private Long leaveRequestId;
    private LocalDate leaveStartDate;
    private LocalDate leaveEndDate;
    private String leaveType;
    private String leaveStatus;

    // ── Employee info ──────────────────────────────
    private Long userId;
    private String userFirstName;
    private String userLastName;
    private String userEmail;
    private String userDepartment;

    // ── Reviewer info ──────────────────────────────
    private Long reviewedById;
    private String reviewedByFullName;

    // ── Uploader info ──────────────────────────────
    private Long uploadedById;
    private String uploadedByFullName;
}