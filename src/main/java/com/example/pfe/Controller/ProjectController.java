package com.example.pfe.Controller;

import com.example.pfe.Service.ProjectService;
import com.example.pfe.dto.ProjectRequestDTO;
import com.example.pfe.dto.ProjectResponseDTO;
import com.example.pfe.dto.TeamMemberDTO;
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

    /* Legacy endpoints using employeeCode have been removed.
     * Assignment is now done via dedicated services using employee email. */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER') or @securityService.isProjectManager(#id)")
    public ResponseEntity<ProjectResponseDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam ProjectStatus status) {
        return ResponseEntity.ok(projectService.updateProjectStatus(id, status));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER')")
    public ResponseEntity<ProjectResponseDTO> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequestDTO requestDTO) {
        return ResponseEntity.ok(projectService.updateProject(id, requestDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER')")
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
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER')")
    public ResponseEntity<List<TeamMemberDTO>> getTeamMembers(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectTeamMembers(id));
    }

}