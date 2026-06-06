package com.example.pfe.mapper;

import com.example.pfe.dto.LeaveDocumentDTO;
import com.example.pfe.entities.LeaveDocument;
import org.springframework.stereotype.Component;

@Component
public class LeaveDocumentMapper {

    public LeaveDocumentDTO toDTO(LeaveDocument doc) {
        if (doc == null) return null;

        LeaveDocumentDTO.LeaveDocumentDTOBuilder builder = LeaveDocumentDTO.builder()
                // ── Document info ──────────────────────────────
                .id(doc.getId())
                .fileName(doc.getFileName())
                .mimeType(doc.getMimeType())
                .fileSize(doc.getFileSize())
                .documentType(doc.getDocumentType())
                .documentCategory(doc.getDocumentCategory())
                .status(doc.getStatus())
                .adminNotes(doc.getAdminNotes())
                .uploadedAt(doc.getUploadedAt())
                .reviewedAt(doc.getReviewedAt())
                .generatedAt(doc.getGeneratedAt())
                .generatedBySystem(doc.getGeneratedBySystem());

        // ── Leave request info ─────────────────────────
        if (doc.getLeaveRequest() != null) {
            builder
                    .leaveRequestId(doc.getLeaveRequest().getId())
                    .leaveStartDate(doc.getLeaveRequest().getStartDate())
                    .leaveEndDate(doc.getLeaveRequest().getEndDate())
                    .leaveType(doc.getLeaveRequest().getLeaveType() != null
                            ? doc.getLeaveRequest().getLeaveType().name() : null)
                    .leaveStatus(doc.getLeaveRequest().getStatus() != null
                            ? doc.getLeaveRequest().getStatus().name() : null);
        }

        // ── Employee info ──────────────────────────────
        if (doc.getUser() != null) {
            builder
                    .userId(doc.getUser().getId())
                    .userFirstName(doc.getUser().getFirstName())
                    .userLastName(doc.getUser().getLastName())
                    .userEmail(doc.getUser().getEmail())
                    .userDepartment(doc.getUser().getDepartment() != null
                            ? doc.getUser().getDepartment().name() : null);
        }

        // ── Reviewer info ──────────────────────────────
        if (doc.getReviewedBy() != null) {
            builder
                    .reviewedById(doc.getReviewedBy().getId())
                    .reviewedByFullName(doc.getReviewedBy().getFirstName()
                            + " " + doc.getReviewedBy().getLastName());
        }

        // ── Uploader info ──────────────────────────────
        if (doc.getUploadedBy() != null) {
            builder
                    .uploadedById(doc.getUploadedBy().getId())
                    .uploadedByFullName(doc.getUploadedBy().getFirstName()
                            + " " + doc.getUploadedBy().getLastName());
        }

        return builder.build();
    }
}