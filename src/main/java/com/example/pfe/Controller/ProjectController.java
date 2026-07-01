package com.example.pfe.Controller;

import com.example.pfe.Service.ProjectService;
import com.example.pfe.dto.*;
import com.example.pfe.enums.ProjectStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // ── CREATE: General Manager ONLY ─────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('GENERAL_MANAGER')")
    public ResponseEntity<ProjectResponseDTO> createProject(
            @Valid @RequestBody ProjectRequestDTO requestDTO) {
        return ResponseEntity.ok(projectService.createProject(requestDTO));
    }

    // ── READ: any authenticated user ─────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProjectResponseDTO> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    // ── UPDATE STATUS: GM or PM of this project ──────────────────────────────
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or @securityService.isProjectManager(#id)")
    public ResponseEntity<ProjectResponseDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequestDTO request) {
        return ResponseEntity.ok(projectService.updateProjectStatus(id, request));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProjectStatusHistoryDTO>> getStatusHistory(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getStatusHistory(id));
    }

    // ── UPDATE PROJECT: GM or PM of this project ─────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or @securityService.isProjectManager(#id)")
    public ResponseEntity<ProjectResponseDTO> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequestDTO requestDTO) {
        return ResponseEntity.ok(projectService.updateProject(id, requestDTO));
    }

    // ── DELETE: General Manager ONLY ─────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GENERAL_MANAGER')")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ProjectResponseDTO>> getAllProjects(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(projectService.getAllProjects(pageable));
    }

    @GetMapping("/{id}/team")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TeamMemberDTO>> getTeamMembers(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectTeamMembers(id));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProjectResponseDTO>> getEmployeeProjects(
            @PathVariable Long employeeId) {
        return ResponseEntity.ok(projectService.getEmployeeProjects(employeeId));
    }
}