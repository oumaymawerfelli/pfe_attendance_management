package com.example.pfe.Controller;

import com.example.pfe.Service.ProjectAssignmentService;
import com.example.pfe.dto.ProjectAssignmentDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project-assignments")
@RequiredArgsConstructor
public class ProjectAssignmentController {

    private final ProjectAssignmentService projectAssignmentService;

    @PostMapping("/assign")
    @PreAuthorize("hasRole('GENERAL_MANAGER')")
    public ResponseEntity<String> assignProjectToEmployee(
            @Valid @RequestBody ProjectAssignmentDTO assignmentDTO) {

        projectAssignmentService.assignProjectToEmployee(assignmentDTO);
        return ResponseEntity.ok("Project assigned successfully");
    }

    @DeleteMapping("/{projectId}/remove-manager/{employeeId}")
    @PreAuthorize("hasRole('GENERAL_MANAGER')")
    public ResponseEntity<String> removeFromProject(
            @PathVariable Long projectId,
            @PathVariable Long employeeId) {

        projectAssignmentService.removeFromProject(projectId, employeeId);
        return ResponseEntity.ok("Employee removed from project");
    }
}