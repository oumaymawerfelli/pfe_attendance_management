package com.example.pfe.Controller;

import com.example.pfe.Service.AttendanceConfigService;
import com.example.pfe.dto.AttendanceConfigDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HR-only endpoints to read and update attendance thresholds at runtime.
 * All routes are protected by the ADMIN / HR role.
 */
@RestController
@RequestMapping("/api/attendance-config")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
public class AttendanceConfigController {

    private final AttendanceConfigService configService;

    /**
     * GET /api/attendance-config
     * Returns the full list of configurable thresholds for the HR settings page.
     */
    @GetMapping
    public ResponseEntity<List<AttendanceConfigDTO.Response>> getAll() {
        return ResponseEntity.ok(configService.getAllConfigs());
    }

    /**
     * PATCH /api/attendance-config/{key}
     * Updates a single threshold by its key (e.g. "LATE_HOUR").
     *
     * Example body:
     * {
     *   "configValue": 8.5,
     *   "lastModifiedBy": "hr.manager@company.com"
     * }
     */
    @PatchMapping("/{key}")
    public ResponseEntity<AttendanceConfigDTO.Response> update(
            @PathVariable String key,
            @Valid @RequestBody AttendanceConfigDTO.UpdateRequest request) {

        return ResponseEntity.ok(configService.updateConfig(key, request));
    }
}