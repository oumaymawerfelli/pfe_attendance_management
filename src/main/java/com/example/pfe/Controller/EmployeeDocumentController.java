package com.example.pfe.Controller;

import com.example.pfe.Service.DocumentService;
import com.example.pfe.config.UserPrincipal;
import com.example.pfe.dto.DocumentFilterRequest;
import com.example.pfe.dto.LeaveDocumentDTO;
import com.example.pfe.entities.LeaveDocument;
import com.example.pfe.entities.LeaveDocument.DocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/me/documents")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class EmployeeDocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<Page<LeaveDocumentDTO>> myDocuments(
            DocumentFilterRequest filter,
            @PageableDefault(size = 20, sort = "uploadedAt") Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(
                documentService.findByEmployee(principal.getId(), filter, pageable));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) throws IOException {

        DocumentService.DocumentDownload dl =
                documentService.download(id, principal.getId(), false);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + dl.originalName() + "\"")
                .contentType(MediaType.parseMediaType(dl.mimeType()))
                .body(dl.resource());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LeaveDocument> upload(
            @RequestPart MultipartFile file,
            @RequestParam Long leaveRequestId,
            @RequestParam DocumentType type,
            @AuthenticationPrincipal UserPrincipal principal) throws IOException {

        if (type == DocumentType.ACCEPTATION_LETTER || type == DocumentType.JUSTIFICATION) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.uploadDocument(
                        file, leaveRequestId, principal.getId(), type));
    }
}