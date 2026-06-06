package com.example.pfe.Service;

import com.example.pfe.entities.LeaveDocument.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;

/**
 * Handles all file I/O for leave documents.
 *
 * Directory structure produced:
 *   uploads/leaves/{userId}/authorizations/  ← acceptance letters (generated)
 *   uploads/leaves/{userId}/justifications/  ← medical certs, proofs (uploaded)
 *
 * Base path is read from app.upload.dir (same property your LeaveService uses).
 * We append /leaves/ to keep leave-documents separate from any other uploads.
 */
@Service
@Slf4j
public class LeaveFileStorageService {

    @Value("${app.upload.dir:uploads/leave-documents}")
    private String uploadDir;

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "jpg", "jpeg", "png", "docx", "xlsx");

    // ─── Store an UPLOADED file (employee / admin) ────────────────────────────

    /**
     * Saves a file uploaded by the user.
     *
     * Resulting path:
     *   uploads/leaves/{userId}/justifications/{leaveId}_{type}.{ext}
     */
    public StorageResult storeUpload(MultipartFile file,
                                     Long userId,
                                     Long leaveRequestId,
                                     DocumentType type) throws IOException {

        String extension = extractExtension(file.getOriginalFilename());
        validateExtension(extension);

        String subDir   = resolveSubDir(type);
        String fileName = leaveRequestId + "_" + type.name().toLowerCase() + "." + extension;
        String relative = "leaves/" + userId + "/" + subDir + "/" + fileName;

        write(file.getBytes(), relative);

        log.info("Uploaded document saved: {}", relative);
        return new StorageResult(fileName, relative,
                file.getContentType(), file.getSize());
    }

    // ─── Store a GENERATED file (system-produced acceptance letter / justif) ──

    /**
     * Saves a system-generated PDF.
     *
     * Resulting path:
     *   uploads/leaves/{userId}/authorizations/{leaveId}_acceptation_letter.pdf
     */
    public StorageResult storeGenerated(byte[] pdfContent,
                                        Long userId,
                                        Long leaveRequestId,
                                        DocumentType type) throws IOException {

        String subDir   = resolveSubDir(type);
        String fileName = leaveRequestId + "_" + type.name().toLowerCase() + ".pdf";
        String relative = "leaves/" + userId + "/" + subDir + "/" + fileName;

        write(pdfContent, relative);

        log.info("Generated document saved: {}", relative);
        return new StorageResult(fileName, relative,
                "application/pdf", (long) pdfContent.length);
    }

    // ─── Load (for download / streaming) ─────────────────────────────────────

    public Resource load(String filePath) throws IOException {
        Path path = Paths.get(uploadDir).resolve(filePath).normalize();
        guardPathTraversal(path);

        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists()) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        return resource;
    }

    // ─── Delete from disk ─────────────────────────────────────────────────────

    public void delete(String filePath) throws IOException {
        Path path = Paths.get(uploadDir).resolve(filePath).normalize();
        guardPathTraversal(path);
        boolean deleted = Files.deleteIfExists(path);
        log.info("File {} — deleted: {}", filePath, deleted);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void write(byte[] content, String relative) throws IOException {
        Path target = Paths.get(uploadDir).resolve(relative).normalize();
        Files.createDirectories(target.getParent());
        Files.write(target, content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * ACCEPTATION_LETTER and JUSTIFICATION (auto-generated) → authorizations/
     * Everything else (employee uploads) → justifications/
     */
    private String resolveSubDir(DocumentType type) {
        return switch (type) {
            case ACCEPTATION_LETTER, JUSTIFICATION -> "authorizations";
            default                                -> "justifications";
        };
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private void validateExtension(String ext) {
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException(
                    "File type not allowed: ." + ext +
                            " — Allowed types: " + ALLOWED_EXTENSIONS);
        }
    }

    private void guardPathTraversal(Path resolved) {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!resolved.toAbsolutePath().normalize().startsWith(base)) {
            throw new SecurityException("Path traversal attempt blocked.");
        }
    }

    // ─── Result record ────────────────────────────────────────────────────────

    public record StorageResult(
            String fileName,
            String filePath,   // relative path stored in DB
            String mimeType,
            Long   fileSize
    ) {}
}