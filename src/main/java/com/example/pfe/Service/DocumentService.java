package com.example.pfe.Service;

import com.example.pfe.Repository.LeaveDocumentRepository;
import com.example.pfe.Repository.LeaveRequestRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.LeaveDocumentDTO;
import com.example.pfe.mapper.LeaveDocumentMapper;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import com.example.pfe.dto.DocumentFilterRequest;
import com.example.pfe.entities.LeaveDocument;
import com.example.pfe.entities.LeaveDocument.*;
import com.example.pfe.entities.LeaveRequest;
import com.example.pfe.entities.User;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final LeaveDocumentRepository documentRepository;
    private final LeaveRequestRepository  leaveRequestRepository;
    private final UserRepository          userRepository;
    private final LeaveFileStorageService storageService;
    private final LeaveDocumentMapper leaveDocumentMapper;

    // ══════════════════════════════════════════════════════════
    // CALLED BY LeaveService.approveLeave() — auto-save generated letter
    // ══════════════════════════════════════════════════════════

    /**
     * Persists a system-generated PDF (acceptance letter or justification).
     * Call this from LeaveService.approveLeave() after building the PDF bytes.
     *
     * Usage in LeaveService:
     *   byte[] pdf = pdfGeneratorService.generateAcceptanceLetter(request);
     *   documentService.saveGeneratedDocument(
     *       pdf, request.getId(), request.getUser().getId(), DocumentType.ACCEPTATION_LETTER);
     */
    public LeaveDocument saveGeneratedDocument(byte[] pdfContent,
                                               Long leaveRequestId,
                                               Long userId,
                                               DocumentType type) throws IOException {

        LeaveRequest leaveRequest = getLeaveRequestById(leaveRequestId);
        User user = getUserById(userId);

        LeaveFileStorageService.StorageResult stored =
                storageService.storeGenerated(pdfContent, userId, leaveRequestId, type);

        LeaveDocument doc = LeaveDocument.builder()
                .leaveRequest(leaveRequest)
                .user(user)
                .documentType(type)
                .documentCategory(DocumentCategory.GENERATED)
                .fileName(stored.fileName())
                .filePath(stored.filePath())
                .mimeType(stored.mimeType())
                .fileSize(stored.fileSize())
                .status(DocumentStatus.APPROVED)
                .generatedBySystem(true)
                .generatedAt(LocalDateTime.now())
                .uploadedAt(LocalDateTime.now())
                .build();

        LeaveDocument saved = documentRepository.save(doc);
        log.info("Generated document saved — leaveId: {}, type: {}, path: {}",
                leaveRequestId, type, stored.filePath());
        return saved;
    }

    public LeaveDocument saveGeneratedDocumentFromFile(MultipartFile file,
                                                       Long leaveRequestId,
                                                       Long userId,
                                                       DocumentType type) throws IOException {
        return saveGeneratedDocument(file.getBytes(), leaveRequestId, userId, type);
    }

    // ══════════════════════════════════════════════════════════
    // EMPLOYEE — Upload supporting document (medical cert, proof…)
    // ══════════════════════════════════════════════════════════

    /**
     * Stores an employee-uploaded file and persists the record.
     * Status is PENDING so admin can review it.
     */
    public LeaveDocument uploadDocument(MultipartFile file,
                                        Long leaveRequestId,
                                        Long userId,
                                        DocumentType type) throws IOException {

        LeaveRequest leaveRequest = getLeaveRequestById(leaveRequestId);
        User user = getUserById(userId);

        LeaveFileStorageService.StorageResult stored =
                storageService.storeUpload(file, userId, leaveRequestId, type);

        LeaveDocument doc = LeaveDocument.builder()
                .leaveRequest(leaveRequest)
                .user(user)
                .uploadedBy(user)
                .documentType(type)
                .documentCategory(DocumentCategory.UPLOADED)
                .fileName(stored.fileName())
                .filePath(stored.filePath())
                .mimeType(stored.mimeType())
                .fileSize(stored.fileSize())
                .status(DocumentStatus.PENDING)
                .generatedBySystem(false)
                .uploadedAt(LocalDateTime.now())
                .build();

        LeaveDocument saved = documentRepository.save(doc);
        log.info("Employee document uploaded — leaveId: {}, userId: {}, type: {}",
                leaveRequestId, userId, type);
        return saved;
    }

    // ══════════════════════════════════════════════════════════
    // ADMIN — Filtered paginated list
    // ══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<LeaveDocumentDTO> findAll(DocumentFilterRequest filter, Pageable pageable) {
        return documentRepository.findAll(Spec.build(filter), pageable)
                .map(leaveDocumentMapper::toDTO);
    }

    // ══════════════════════════════════════════════════════════
    // EMPLOYEE — Own documents only (security enforced here)
    // ══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<LeaveDocumentDTO> findByEmployee(Long userId,
                                                 DocumentFilterRequest filter,
                                                 Pageable pageable) {
        if (userId == null) {                                     // ← new guard
            throw new SecurityException("Cannot resolve authenticated user.");
        }
        return documentRepository.findAll(Spec.forEmployee(userId, filter), pageable)
                .map(leaveDocumentMapper::toDTO);
    }
    // ══════════════════════════════════════════════════════════
    // DOWNLOAD — single file streamed
    // ══════════════════════════════════════════════════════════

    /**
     * @param requesterId the currently authenticated user's ID
     * @param isAdmin     true → skip ownership check
     */
    @Transactional(readOnly = true)
    public DocumentDownload download(Long documentId,
                                     Long requesterId,
                                     boolean isAdmin) throws IOException {
        LeaveDocument doc = getDocumentById(documentId);

        if (!isAdmin) {
            if (requesterId == null) {                            // ← new guard
                throw new SecurityException("Cannot resolve authenticated user.");
            }
            if (!doc.getUser().getId().equals(requesterId)) {
                throw new SecurityException(
                        "Access denied: this document does not belong to you.");
            }
        }

        Resource resource = storageService.load(doc.getFilePath());
        return new DocumentDownload(doc.getFileName(), doc.getMimeType(), resource);
    }

    // ══════════════════════════════════════════════════════════
    // ADMIN — Review (approve / reject) a document
    // ══════════════════════════════════════════════════════════

    public LeaveDocument review(Long documentId,
                                DocumentStatus newStatus,
                                String adminNotes,
                                Long reviewerId) {
        LeaveDocument doc = getDocumentById(documentId);
        User reviewer = getUserById(reviewerId);

        doc.setStatus(newStatus);
        doc.setAdminNotes(adminNotes);
        doc.setReviewedBy(reviewer);
        doc.setReviewedAt(LocalDateTime.now());

        LeaveDocument saved = documentRepository.save(doc);
        log.info("Document {} reviewed — status: {}, by: {}", documentId, newStatus, reviewerId);
        return saved;
    }

    // ══════════════════════════════════════════════════════════
    // ADMIN — Archive a document (keeps the row, hides from active view)
    // ══════════════════════════════════════════════════════════

    public void archiveDocument(Long documentId) {
        LeaveDocument doc = getDocumentById(documentId);
        doc.setStatus(DocumentStatus.ARCHIVED);
        documentRepository.save(doc);
        log.info("Document {} archived.", documentId);
    }

    // ══════════════════════════════════════════════════════════
    // EXPORT — Stream ZIP (no full in-memory load)
    // ══════════════════════════════════════════════════════════

    /**
     * Streams a ZIP of all matching documents.
     *
     * ZIP internal structure:
     *   {lastName}_{firstName}/{yyyy-MM}/{TYPE}_{filename}
     *   e.g. Doe_John/2026-05/ACCEPTATION_LETTER_42_acceptation_letter.pdf
     */
    public void streamZip(DocumentFilterRequest filter, OutputStream out) throws IOException {
        List<LeaveDocument> docs = documentRepository.findAll(Spec.build(filter));

        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (LeaveDocument doc : docs) {
                try {
                    Resource file = storageService.load(doc.getFilePath());
                    zip.putNextEntry(new ZipEntry(buildZipEntryName(doc)));
                    StreamUtils.copy(file.getInputStream(), zip);
                    zip.closeEntry();
                } catch (IOException e) {
                    // Log missing file but continue — don't abort the whole ZIP
                    log.warn("File not found for document {}: {}", doc.getId(), doc.getFilePath());
                    zip.putNextEntry(new ZipEntry("MISSING_doc_" + doc.getId() + ".txt"));
                    zip.write(("File missing: " + doc.getFilePath()).getBytes());
                    zip.closeEntry();
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // EXPORT — Excel metadata report (Apache POI)
    // ══════════════════════════════════════════════════════════

    public byte[] exportExcel(DocumentFilterRequest filter) throws IOException {
        List<LeaveDocument> docs = documentRepository.findAll(Spec.build(filter));

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Documents Congés");

            // Header
            CellStyle headerStyle = buildHeaderStyle(wb);
            String[] headers = {
                    "ID", "Employé", "Type doc", "Catégorie", "Statut",
                    "Fichier", "Taille (KB)", "Date upload",
                    "Congé début", "Congé fin",
                    "Revu par", "Date révision", "Notes admin"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Rows — uses your User.firstName / User.lastName / LeaveRequest.startDate
            int rowNum = 1;
            for (LeaveDocument doc : docs) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(doc.getId());
                row.createCell(1).setCellValue(
                        doc.getUser().getFirstName() + " " + doc.getUser().getLastName());
                row.createCell(2).setCellValue(doc.getDocumentType().name());
                row.createCell(3).setCellValue(doc.getDocumentCategory().name());
                row.createCell(4).setCellValue(doc.getStatus().name());
                row.createCell(5).setCellValue(doc.getFileName());
                row.createCell(6).setCellValue(doc.getFileSize() != null
                        ? Math.round(doc.getFileSize() / 1024.0 * 10) / 10.0 : 0);
                row.createCell(7).setCellValue(doc.getUploadedAt() != null
                        ? doc.getUploadedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "");
                row.createCell(8).setCellValue(
                        doc.getLeaveRequest().getStartDate().toString());
                row.createCell(9).setCellValue(
                        doc.getLeaveRequest().getEndDate().toString());
                row.createCell(10).setCellValue(doc.getReviewedBy() != null
                        ? doc.getReviewedBy().getFirstName() + " " + doc.getReviewedBy().getLastName() : "—");
                row.createCell(11).setCellValue(doc.getReviewedAt() != null
                        ? doc.getReviewedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "—");
                row.createCell(12).setCellValue(
                        doc.getAdminNotes() != null ? doc.getAdminNotes() : "");
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    // ──────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────

    private String buildZipEntryName(LeaveDocument doc) {
        // Uses your User.lastName / User.firstName
        String folder = doc.getUser().getLastName() + "_" + doc.getUser().getFirstName();
        String month  = doc.getUploadedAt() != null
                ? doc.getUploadedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                : "unknown";
        return folder + "/" + month + "/" + doc.getDocumentType() + "_" + doc.getFileName();
    }

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private LeaveDocument getDocumentById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document with ID " + id + " not found"));
    }

    private LeaveRequest getLeaveRequestById(Long id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave request with ID " + id + " not found"));
    }

    private User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User with ID " + id + " not found"));
    }

    // ─── Records ──────────────────────────────────────────────
    public record DocumentDownload(String originalName, String mimeType, Resource resource) {}

    // ══════════════════════════════════════════════════════════
    // PRIVATE — Query specifications (only this service needs them)
    // ══════════════════════════════════════════════════════════

    private static class Spec {

        static Specification<LeaveDocument> build(DocumentFilterRequest f) {
            return Specification
                    .where(f.isIncludeArchived() ? null : notArchived())  // ← conditional
                    .and(byUserId(f.getUserId()))
                    .and(byUserFullName(f.getUserFullName()))
                    .and(byType(f.getDocumentType()))
                    .and(byCategory(f.getDocumentCategory()))
                    .and(byStatus(f.getStatus()))
                    .and(uploadedBetween(f.getUploadedFrom(), f.getUploadedTo()))
                    .and(leavePeriodOverlaps(f.getLeaveFrom(), f.getLeaveTo()));
        }

        /** Forces userId — employees can never see other people's documents. */
        static Specification<LeaveDocument> forEmployee(Long userId, DocumentFilterRequest f) {
            return Specification
                    .where(f.isIncludeArchived() ? null : notArchived())  // ← conditional
                    .and(byUserId(userId))
                    .and(byLeaveRequestId(f.getLeaveRequestId()))
                    .and(byType(f.getDocumentType()))
                    .and(byCategory(f.getDocumentCategory()))
                    .and(byStatus(f.getStatus()))
                    .and(uploadedBetween(f.getUploadedFrom(), f.getUploadedTo()))
                    .and(leavePeriodOverlaps(f.getLeaveFrom(), f.getLeaveTo()));
        }

        private static Specification<LeaveDocument> notArchived() {
            return (root, q, cb) ->
                    cb.notEqual(root.get("status"), DocumentStatus.ARCHIVED);
        }

        private static Specification<LeaveDocument> byUserId(Long id) {
            return (root, q, cb) ->
                    id == null ? null : cb.equal(root.get("user").get("id"), id);
        }

        private static Specification<LeaveDocument> byUserFullName(String name) {
            return (root, q, cb) -> {
                if (name == null || name.isBlank()) return null;
                String pattern = "%" + name.toLowerCase() + "%";
                return cb.or(
                        cb.like(cb.lower(root.get("user").get("firstName")), pattern),
                        cb.like(cb.lower(root.get("user").get("lastName")),  pattern)
                );
            };
        }

        private static Specification<LeaveDocument> byType(DocumentType type) {
            return (root, q, cb) ->
                    type == null ? null : cb.equal(root.get("documentType"), type);
        }

        private static Specification<LeaveDocument> byCategory(DocumentCategory category) {
            return (root, q, cb) ->
                    category == null ? null : cb.equal(root.get("documentCategory"), category);
        }

        private static Specification<LeaveDocument> byStatus(DocumentStatus status) {
            return (root, q, cb) ->
                    status == null ? null : cb.equal(root.get("status"), status);
        }

        private static Specification<LeaveDocument> uploadedBetween(java.time.LocalDate from,
                                                                    java.time.LocalDate to) {
            return (root, q, cb) -> {
                if (from == null && to == null) return null;
                var field = root.<java.time.LocalDateTime>get("uploadedAt");
                if (from == null) return cb.lessThanOrEqualTo(field, to.atTime(23, 59, 59));
                if (to   == null) return cb.greaterThanOrEqualTo(field, from.atStartOfDay());
                return cb.between(field, from.atStartOfDay(), to.atTime(23, 59, 59));
            };
        }

        private static Specification<LeaveDocument> leavePeriodOverlaps(java.time.LocalDate from,
                                                                        java.time.LocalDate to) {
            return (root, q, cb) -> {
                if (from == null && to == null) return null;
                var req = root.join("leaveRequest", JoinType.LEFT);
                if (from == null)
                    return cb.lessThanOrEqualTo(req.get("startDate"), to);
                if (to == null)
                    return cb.greaterThanOrEqualTo(req.get("endDate"), from);
                return cb.and(
                        cb.lessThanOrEqualTo(req.get("startDate"), to),
                        cb.greaterThanOrEqualTo(req.get("endDate"), from)
                );
            };
        }
    }

    private static Specification<LeaveDocument> byLeaveRequestId(Long id) {
        return (root, q, cb) ->
                id == null ? null : cb.equal(root.get("leaveRequest").get("id"), id);
    }

    public LeaveDocumentDTO reviewAndReturn(Long documentId,
                                            DocumentStatus newStatus,
                                            String adminNotes,
                                            Long reviewerId) {
        LeaveDocument doc = review(documentId, newStatus, adminNotes, reviewerId);
        return leaveDocumentMapper.toDTO(doc);
    }



}