package com.example.pfe.dto;

import com.example.pfe.entities.LeaveDocument.DocumentCategory;
import com.example.pfe.entities.LeaveDocument.DocumentStatus;
import com.example.pfe.entities.LeaveDocument.DocumentType;
import lombok.Builder;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * All fields are optional — any null field is ignored by DocumentSpecification.
 * Used as query params on GET /api/admin/documents and GET /api/me/documents.
 *
 * Example:
 *   GET /api/admin/documents?userFullName=ahmed&status=PENDING&uploadedFrom=2026-01-01
 */
@Data
public class DocumentFilterRequest {

    /** Partial match on firstName OR lastName */
    private String userFullName;

    /** Exact employee ID */
    private Long userId;

    private DocumentType     documentType;
    private DocumentCategory documentCategory;   // GENERATED or UPLOADED
    private DocumentStatus   status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate uploadedFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate uploadedTo;

    /** Filter by the leave request's period (overlap check) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate leaveFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate leaveTo;

    private boolean includeArchived = false;
    private Long leaveRequestId;
}