package com.example.pfe.Controller;

import com.example.pfe.Repository.RagIngestionLogRepository;
import com.example.pfe.Service.RagSchedulerService;
import com.example.pfe.entities.RagIngestionLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/rag")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:4200")
public class RagAdminController {

    private final RagIngestionLogRepository logRepository;
    private final RagSchedulerService schedulerService;

    @GetMapping("/ingestion-logs")
    public ResponseEntity<List<RagIngestionLog>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<RagIngestionLog> logs = logRepository.findAll(
                PageRequest.of(page, size, Sort.by("ingestedAt").descending())
        ).getContent();
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/ingestion-logs/last")
    public ResponseEntity<RagIngestionLog> getLastLog(
            @RequestParam(defaultValue = "reglementation-rh-arabsoft") String slug
    ) {
        return logRepository
                .findFirstByFileSlugOrderByIngestedAtDesc(slug)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/force-sync")
    public ResponseEntity<Map<String, Object>> forceSync() {
        Map<String, Object> response = new HashMap<>();
        try {
            log.info("🔄 Force sync triggered");
            RagIngestionLog result = schedulerService.checkAndUpdateRegulations();
            response.put("status", "success");
            response.put("message", "Synchronisation terminée");
            response.put("log", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ Force sync failed", e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalIngestions", logRepository.count());
        stats.put("successCount", logRepository.countByStatus("SUCCESS"));
        stats.put("noChangeCount", logRepository.countByStatus("NO_CHANGE"));
        stats.put("failedCount", logRepository.countByStatus("FAILED"));
        return ResponseEntity.ok(stats);
    }
}