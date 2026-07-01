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
import com.example.pfe.Service.DocumentService;
import com.example.pfe.dto.LeaveDocumentRequest;
import com.example.pfe.enums.LeaveType;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/leaves")
@RequiredArgsConstructor
@Slf4j
public class LeaveDocumentController {

    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository         userRepository;
    private final JasperService          jasperService;
    private final DocumentService        documentService;

    @Value("${app.upload.dir:uploads}")
    private String uploadBaseDir;

    // ── Upload ────────────────────────────────────────────────────────────────

    @PostMapping(value = "/{id}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'GENERAL_MANAGER', 'PROJECT_MANAGER')")
    public ResponseEntity<Void> uploadDocument(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {

        LeaveRequest leave = getLeaveById(id);

        if (!"application/pdf".equals(file.getContentType())) {
            throw new BusinessException("Only PDF files are accepted.");
        }

        documentService.saveGeneratedDocument(
                file.getBytes(),
                id,
                leave.getUser().getId(),
                LeaveDocument.DocumentType.ACCEPTATION_LETTER
        );

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

    // ── Generate from template ────────────────────────────────────────────────

    @PostMapping("/{id}/generate-document")
    @PreAuthorize("hasAnyRole('ADMIN', 'GENERAL_MANAGER', 'PROJECT_MANAGER')")
    public ResponseEntity<Void> generateAndSave(
            @PathVariable Long id,
            @RequestBody LeaveDocumentRequest request) throws Exception {

        LeaveRequest leave = getLeaveById(id);

        byte[] pdfBytes;
        LeaveDocument.DocumentType docType;

        // ✅ Branch by leave type
        if (leave.getLeaveType() == LeaveType.EXIT_AUTHORIZATION) {
            int monthlyCount = leaveRequestRepository.countExitAuthorizationsInMonth(
                    leave.getUser().getId(),
                    LeaveType.EXIT_AUTHORIZATION,
                    leave.getStartDate().getYear(),
                    leave.getStartDate().getMonthValue()
            );
            pdfBytes = jasperService.generateAutorisationSortie(
                    leave, request.getSignatureBase64(), monthlyCount
            );
            docType = LeaveDocument.DocumentType.EXIT_AUTHORIZATION_LETTER;
        } else {
            pdfBytes = jasperService.generateDemandeConge(
                    leave, request.getSignatureBase64(), null
            );
            docType = LeaveDocument.DocumentType.ACCEPTATION_LETTER;
        }

        // ✅ Save to leave_documents table
        documentService.saveGeneratedDocument(
                pdfBytes, id, leave.getUser().getId(), docType
        );

        // ✅ Keep legacy documentPath
        Path dir = Paths.get(uploadBaseDir, "leave-docs");
        Files.createDirectories(dir);
        String safeName = (leave.getUser().getFirstName() + "_" + leave.getUser().getLastName())
                .replaceAll("[^a-zA-Z0-9_-]", "_");
        String fileName = (docType == LeaveDocument.DocumentType.EXIT_AUTHORIZATION_LETTER
                ? "exit_" : "leave_") + id + "_" + safeName + ".pdf";
        Files.write(dir.resolve(fileName), pdfBytes);
        leave.setDocumentPath("leave-docs/" + fileName);
        leaveRequestRepository.save(leave);

        log.info("✅ Document generated for {} {}: {}", docType, id, fileName);
        return ResponseEntity.ok().build();
    }
    // ── Helper ────────────────────────────────────────────────────────────────

    private LeaveRequest getLeaveById(Long id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave request with ID " + id + " not found"));
    }

    @PostMapping(value = "/{leaveId}/authorization-letter",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'GENERAL_MANAGER', 'PROJECT_MANAGER')")
    public ResponseEntity<Void> saveAuthorizationLetter(
            @PathVariable Long leaveId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) throws IOException {

        LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found: " + leaveId));

        documentService.saveGeneratedDocument(
                file.getBytes(),
                leaveId,
                leave.getUser().getId(),
                LeaveDocument.DocumentType.ACCEPTATION_LETTER
        );

        return ResponseEntity.ok().build();
    }
}