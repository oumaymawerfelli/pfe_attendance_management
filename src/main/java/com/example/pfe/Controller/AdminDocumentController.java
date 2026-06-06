// ═══════════════════════════════════════════════════════════════════════════
// AdminDocumentController.java
// ═══════════════════════════════════════════════════════════════════════════
package com.example.pfe.Controller;


import com.example.pfe.Service.DocumentService;
import com.example.pfe.dto.DocumentFilterRequest;
import com.example.pfe.dto.LeaveDocumentDTO;
import com.example.pfe.entities.LeaveDocument;
import com.example.pfe.entities.LeaveDocument.DocumentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.example.pfe.config.UserPrincipal;
import java.io.IOException;

/**
 * Admin / General Manager / Project Manager document management.
 *
 * Endpoints:
 *   GET  /api/admin/documents              — filtered + paginated list
 *   GET  /api/admin/documents/{id}/download — stream file
 *   PATCH /api/admin/documents/{id}/review  — approve or reject
 *   PATCH /api/admin/documents/{id}/archive — soft-archive
 *   POST /api/admin/documents/export/zip    — download as ZIP
 *   POST /api/admin/documents/export/excel  — download as .xlsx report
 */
@RestController
@RequestMapping("/api/admin/documents")
@PreAuthorize("hasAnyRole('ADMIN', 'GENERAL_MANAGER', 'PROJECT_MANAGER')")
@RequiredArgsConstructor
public class AdminDocumentController {

    private final DocumentService documentService;

    /**
     * GET /api/admin/documents
     *
     * All query params are optional:
     *   userFullName, userId, documentType, documentCategory,
     *   status, uploadedFrom (yyyy-MM-dd), uploadedTo,
     *   leaveFrom, leaveTo
     *   + Pageable: page, size, sort
     *
     * Examples:
     *   GET /api/admin/documents?userFullName=john&status=PENDING&page=0&size=20
     *   GET /api/admin/documents?documentType=MEDICAL_CERTIFICATE&uploadedFrom=2026-01-01
     *   GET /api/admin/documents?documentCategory=UPLOADED&sort=uploadedAt,desc
     */
    @GetMapping
    public ResponseEntity<Page<LeaveDocumentDTO>> listAll(
            DocumentFilterRequest filter,
            @PageableDefault(size = 20, sort = "uploadedAt") Pageable pageable) {
        return ResponseEntity.ok(documentService.findAll(filter, pageable));
    }

    /**
     * GET /api/admin/documents/{id}/download
     * Streams the actual file — any content type.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable Long id) throws IOException {
        DocumentService.DocumentDownload dl = documentService.download(id, null, true);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + dl.originalName() + "\"")
                .contentType(MediaType.parseMediaType(dl.mimeType()))
                .body(dl.resource());
    }

    /**
     * PATCH /api/admin/documents/{id}/review
     *
     * Body:
     * {
     *   "status": "APPROVED",       // or "REJECTED"
     *   "adminNotes": "Document OK."
     * }
     */

    @PatchMapping("/{id}/review")
    public ResponseEntity<LeaveDocumentDTO> review(      // ← LeaveDocumentDTO not LeaveDocument
                                                         @PathVariable Long id,
                                                         @RequestBody ReviewRequest req,
                                                         @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(
                documentService.reviewAndReturn(id, req.status(), req.adminNotes(), principal.getId()));
    }


    /**
     * PATCH /api/admin/documents/{id}/archive
     * Soft-archives the document (hidden from active view, kept in DB).
     */
    @PatchMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        documentService.archiveDocument(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/admin/documents/export/zip
     *
     * Body: same filter fields as GET list.
     * Returns a streaming ZIP — no full in-memory load.
     *
     * ZIP structure inside:
     *   Doe_John/2026-05/ACCEPTATION_LETTER_42_acceptation_letter.pdf
     *   Doe_John/2026-05/MEDICAL_CERTIFICATE_42_medical_certificate.pdf
     *
     * Example body:
     * { "userId": 7, "leaveFrom": "2026-01-01", "leaveTo": "2026-06-30" }
     */
    @PostMapping("/export/zip")
    public ResponseEntity<StreamingResponseBody> exportZip(
            @RequestBody DocumentFilterRequest filter) {
        StreamingResponseBody body = out -> documentService.streamZip(filter, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"documents_export.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    /**
     * POST /api/admin/documents/export/excel
     *
     * Body: same filter fields.
     * Returns a .xlsx report with one row per document.
     *
     * Columns: ID, Employé, Type doc, Catégorie, Statut, Fichier,
     *          Taille (KB), Date upload, Congé début, Congé fin,
     *          Revu par, Date révision, Notes admin
     */
    @PostMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestBody DocumentFilterRequest filter) throws IOException {
        byte[] wb = documentService.exportExcel(filter);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"documents_report.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(wb);
    }

    // ─── Helpers ──────────────────────────────────────────────



    public record ReviewRequest(DocumentStatus status, String adminNotes) {}
}
