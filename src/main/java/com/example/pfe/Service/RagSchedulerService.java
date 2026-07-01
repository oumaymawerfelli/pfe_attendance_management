package com.example.pfe.Service;

import com.example.pfe.Repository.RagIngestionLogRepository;
import com.example.pfe.entities.RagIngestionLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagSchedulerService {

    private final GoogleDriveService googleDriveService;
    private final RagService ragService;
    private final RagIngestionLogRepository logRepository;

    @Value("${scheduler.file-name}")
    private String fileName;

    @Value("${google.drive.file-slug}")
    private String fileSlug;

    @Scheduled(fixedRateString = "${scheduler.check-interval}")
    public void scheduledCheck() {
        log.info("⏰ Scheduled check triggered");
        try {
            checkAndUpdateRegulations();
        } catch (Exception e) {
            log.error("Scheduled check failed", e);
        }
    }

    /**
     * Vérifie Google Drive et met à jour ChromaDB si le fichier a changé.
     * Retourne le log d'ingestion (créé par RagService).
     */
    public RagIngestionLog checkAndUpdateRegulations() {
        log.info("🔍 Checking Google Drive for updates...");

        try {
            // 1. Télécharge depuis Google Drive (retourne InputStream)
            InputStream pdfStream = googleDriveService.downloadFileFromDrive(fileName);

            // 2. Ingestion via RagService (qui log lui-même en DB)
            ragService.ingestPdfWithSlug(pdfStream, fileName, fileSlug);

            // 3. Récupère le log qui vient d'être créé
            return logRepository
                    .findFirstByFileSlugOrderByIngestedAtDesc(fileSlug)
                    .orElseThrow(() -> new RuntimeException("Ingestion log not found after sync"));

        } catch (Exception e) {
            log.error("❌ Sync failed: {}", e.getMessage(), e);

            // Si l'échec est en amont (download), RagService n'a pas pu logger
            // → on log manuellement ici
            RagIngestionLog failLog = RagIngestionLog.builder()
                    .fileSlug(fileSlug)
                    .fileName(fileName)
                    .fileHash("DOWNLOAD_FAILED")   // placeholder car fileHash est nullable=false
                    .status("FAILED")
                    .message(e.getMessage())       // ✅ "message" au lieu de "errorMessage"
                    .ingestedAt(LocalDateTime.now())
                    .build();
            return logRepository.save(failLog);
        }
    }
}