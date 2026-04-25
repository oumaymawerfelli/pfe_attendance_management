package com.example.pfe.Controller;

import com.example.pfe.Repository.LeaveRequestRepository;
import com.example.pfe.Repository.UserRepository;
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

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
@Slf4j
public class LeaveDocumentController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository         userRepository;
    private final JasperService jasperService;


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

        // Build target directory: uploads/leave-docs/
        Path dir = Paths.get(uploadBaseDir, "leave-docs");
        Files.createDirectories(dir);

        // Filename: leave_{id}_{employeeName}.pdf
        String safeName = leave.getUser().getFirstName() + "_" + leave.getUser().getLastName();
        safeName = safeName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String fileName = "leave_" + id + "_" + safeName + ".pdf";

        Path target = dir.resolve(fileName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        // Persist the relative path
        leave.setDocumentPath("leave-docs/" + fileName);
        leaveRequestRepository.save(leave);

        log.info("Leave document saved for leave {} → {}", id, target);
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
                        ? leave.getUser().getDepartment().toString() : "—",   // ← enum → String
                leave.getLeaveType().toString(),
                request.getStartDate(),
                request.getEndDate(),
                leave.getDaysCount() != null
                        ? leave.getDaysCount().intValue() : 0,                // ← Double → int
                request.getReason(),
                request.getApprovedBy(),
                request.getApprovalDate(),
                request.getSignatureBase64()
        );

        Path dir = Paths.get(uploadBaseDir, "leave-docs");
        Files.createDirectories(dir);
        String safeName = (leave.getUser().getFirstName()
                + "_" + leave.getUser().getLastName())
                .replaceAll("[^a-zA-Z0-9_-]", "_");
        String fileName = "leave_" + id + "_" + safeName + ".pdf";
        Files.write(dir.resolve(fileName), pdfBytes);

        leave.setDocumentPath("leave-docs/" + fileName);
        leaveRequestRepository.save(leave);

        log.info("✅ Leave document generated for leave {}", id);
        return ResponseEntity.ok().build();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private LeaveRequest getLeaveById(Long id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave request with ID " + id + " not found"));
    }
}