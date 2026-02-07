package com.example.pfe.Controller;

import com.example.pfe.Service.ProjectService;
import com.example.pfe.dto.ProjectRequestDTO;
import com.example.pfe.dto.ProjectResponseDTO;
import com.example.pfe.enums.ProjectStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER')")
    public ResponseEntity<ProjectResponseDTO> createProject(
            @Valid @RequestBody ProjectRequestDTO requestDTO) {
        return ResponseEntity.ok(projectService.createProject(requestDTO));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER') or @securityService.isProjectManager(#id)")
    public ResponseEntity<ProjectResponseDTO> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

   /* @PutMapping("/{id}/assign-manager")
    @PreAuthorize("hasRole('GENERAL_MANAGER')")
    public ResponseEntity<ProjectResponseDTO> assignManager(
            @PathVariable Long id,
            @RequestParam String employeeCode,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(projectService.assignProjectManager(id, employeeCode, notes));
    }
*/
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER') or @securityService.isProjectManager(#id)")
    public ResponseEntity<ProjectResponseDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam ProjectStatus status) {
        return ResponseEntity.ok(projectService.updateProjectStatus(id, status));
    }

  /*  @GetMapping("/managed-by/{employeeCode}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER') or #employeeCode == authentication.principal.username")
    public ResponseEntity<List<ProjectResponseDTO>> getManagedProjects(
            @PathVariable String employeeCode) {
        return ResponseEntity.ok(projectService.getProjectsManagedByEmployee(employeeCode));
    }
*/
}