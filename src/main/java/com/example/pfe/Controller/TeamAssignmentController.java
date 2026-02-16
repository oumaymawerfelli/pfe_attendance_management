package com.example.pfe.Controller;

import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Service.TeamAssignmentService;
import com.example.pfe.dto.TeamAssignmentDTO;
import com.example.pfe.entities.TeamAssignment;
import com.example.pfe.entities.User;
import com.example.pfe.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/team-assignments")
@RequiredArgsConstructor
public class TeamAssignmentController {

    private final TeamAssignmentService teamAssignmentService;
    private final UserRepository userRepository;
    @PostMapping
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('PROJECT_MANAGER')")
    public ResponseEntity<TeamAssignment> assignTeamMember(
            @Valid @RequestBody TeamAssignmentDTO assignmentDTO) {

        TeamAssignment assignment = teamAssignmentService.assignEmployeeToProject(assignmentDTO);
        return ResponseEntity.ok(assignment);
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('PROJECT_MANAGER')")
    public ResponseEntity<String> removeTeamMember(@PathVariable Long assignmentId) {

        teamAssignmentService.removeEmployeeFromProject(assignmentId);
        return ResponseEntity.ok("Team member removed successfully");
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER') or @securityService.isProjectManager(#projectId) or hasRole('EMPLOYEE')")
    public ResponseEntity<List<TeamAssignment>> getProjectTeam(@PathVariable Long projectId) {

        List<TeamAssignment> team = teamAssignmentService.getProjectTeam(projectId);
        return ResponseEntity.ok(team);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('GENERAL_MANAGER') or #employeeId == authentication.principal.id")
    public ResponseEntity<List<TeamAssignment>> getEmployeeAssignments(
            @PathVariable Long employeeId) {

        List<TeamAssignment> assignments = teamAssignmentService.getEmployeeAssignments(employeeId);
        return ResponseEntity.ok(assignments);
    }

    @PostMapping("/by-employee-email")
    @PreAuthorize("hasRole('GENERAL_MANAGER') or hasRole('PROJECT_MANAGER')")
    public ResponseEntity<TeamAssignment> assignTeamMemberByEmployeeEmail(
            @RequestParam Long projectId,
            @RequestParam String employeeEmail,
            @RequestParam Long assigningManagerId,
            @RequestParam(required = false) String notes) {

        // Trouver l'employé par email professionnel
        User employee = userRepository.findByEmail(employeeEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with email: " + employeeEmail));

        // Créer le DTO
        TeamAssignmentDTO dto = new TeamAssignmentDTO();
        dto.setProjectId(projectId);
        dto.setEmployeeId(employee.getId());
        dto.setAssigningManagerId(assigningManagerId);
        dto.setNotes(notes);

        TeamAssignment assignment = teamAssignmentService.assignEmployeeToProject(dto);
        return ResponseEntity.ok(assignment);
    }
}
