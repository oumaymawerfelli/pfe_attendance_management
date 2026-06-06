package com.example.pfe.Controller;

import com.example.pfe.Repository.LeaveRequestRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.config.UserPrincipal;
import com.example.pfe.entities.LeaveDocument;
import com.example.pfe.entities.LeaveRequest;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.pfe.Service.JasperService;
import com.example.pfe.dto.LeaveDocumentRequest;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import com.example.pfe.Service.DocumentService;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
@Slf4j
public class LeaveDocumentController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository         userRepository;
    private final JasperService jasperService;
    private final DocumentService        documentService;


    @Value("${app.upload.dir:uploads}")
    private String uploadBaseDir;

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * POST /api/leaves/{id}/document
     * Called by the frontend immediately after approving a leave.
     * Saves the PDF to disk and stores the relative path in the DB.
     *
     * Accessible by: ADMIN, GENERAL_MANAGER, PROJECT_MANAGER (approvers only).
     */
    @PostMapping(value = "/{id}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'GENERAL_MANAGER', 'PROJECT_MANAGER')")
    public ResponseEntity<Void> uploadDocument(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {

        LeaveRequest leave = getLeaveById(id);

        if (!"application/pdf".equals(file.getContentType())) {
            throw new BusinessException("Only PDF files are accepted.");
        }

        // ✅ Save to leave_documents table (GENERATED + APPROVED)
        documentService.saveGeneratedDocument(
                file.getBytes(),
                id,
                leave.getUser().getId(),
                LeaveDocument.DocumentType.ACCEPTATION_LETTER
        );

        // ✅ Keep legacy documentPath for openDocument() to still work
        Path dir = Paths.get(uploadBaseDir, "leave-docs");
        Files.createDirectories(dir);
        String safeName = (leave.getUser().getFirstName() + "_" + leave.getUser().getLastName())
                .replaceAll("[^a-zA-Z0-9_-]", "_");
        String fileName = "leave_" + id + "_" + safeName + ".pdf";
        Files.write(dir.resolve(fileName), file.getBytes());
        leave.setDocumentPath("leave-docs/" + fileName);
        leaveRequestRepository.save(leave);

        log.info("Authorization letter saved for leave {} → leave_documents + disk", id);
        return ResponseEntity.ok().build();
    }
    // ── Serve / Download ──────────────────────────────────────────────────────

    /**
     * GET /api/leaves/{id}/document
     * Returns the PDF file so the browser can display or print it.
     *
     * Accessible by any authenticated user — the frontend only shows this
     * button to authorised roles, and the employee can view their own document.
     */
    @GetMapping("/{id}/document")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> getDocument(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) throws MalformedURLException {

        LeaveRequest leave = getLeaveById(id);

        if (leave.getDocumentPath() == null) {
            throw new ResourceNotFoundException("No document found for leave request " + id);
        }

        Path filePath = Paths.get(uploadBaseDir).resolve(leave.getDocumentPath()).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            throw new ResourceNotFoundException("Document file not found on server.");
        }

        String fileName = filePath.getFileName().toString();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @PostMapping("/{id}/generate-document")
    @PreAuthorize("hasAnyRole('ADMIN', 'GENERAL_MANAGER', 'PROJECT_MANAGER')")
    public ResponseEntity<Void> generateAndSave(
            @PathVariable Long id,
            @RequestBody LeaveDocumentRequest request) throws Exception {

        LeaveRequest leave = getLeaveById(id);

        byte[] pdfBytes = jasperService.generateLeaveDocument(
                leave.getUser().getFirstName(),
                leave.getUser().getLastName(),
                leave.getUser().getDepartment() != null
                        ? leave.getUser().getDepartment().toString() : "—",
                leave.getLeaveType().toString(),
                request.getStartDate(),
                request.getEndDate(),
                leave.getDaysCount() != null ? leave.getDaysCount().intValue() : 0,
                request.getReason(),
                request.getApprovedBy(),
                request.getApprovalDate(),
                request.getSignatureBase64()
        );

        // ✅ Save to leave_documents table
        documentService.saveGeneratedDocument(
                pdfBytes,
                id,
                leave.getUser().getId(),
                LeaveDocument.DocumentType.ACCEPTATION_LETTER
        );

        // ✅ Keep legacy documentPath
        Path dir = Paths.get(uploadBaseDir, "leave-docs");
        Files.createDirectories(dir);
        String safeName = (leave.getUser().getFirstName() + "_" + leave.getUser().getLastName())
                .replaceAll("[^a-zA-Z0-9_-]", "_");
        String fileName = "leave_" + id + "_" + safeName + ".pdf";
        Files.write(dir.resolve(fileName), pdfBytes);
        leave.setDocumentPath("leave-docs/" + fileName);
        leaveRequestRepository.save(leave);

        log.info("✅ Leave document generated and saved for leave {}", id);
        return ResponseEntity.ok().build();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private LeaveRequest getLeaveById(Long id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave request with ID " + id + " not found"));
    }

    /**
     * POST /api/leaves/{id}/authorization-letter
     * Called by the frontend after admin approves and signs.
     * Saves the generated PDF as an ACCEPTATION_LETTER document.
     */
    @PostMapping(value = "/{leaveId}/authorization-letter",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'GENERAL_MANAGER', 'PROJECT_MANAGER')")
    public ResponseEntity<Void> saveAuthorizationLetter(
            @PathVariable Long leaveId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) throws IOException {

        // Get the leave request to find the employee's ID
        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found: " + leaveId));

        documentService.saveGeneratedDocument(
                file.getBytes(),
                leaveId,
                leave.getUser().getId(),   // ← employee ID, not admin ID
                LeaveDocument.DocumentType.ACCEPTATION_LETTER
        );

        return ResponseEntity.ok().build();
    }
}