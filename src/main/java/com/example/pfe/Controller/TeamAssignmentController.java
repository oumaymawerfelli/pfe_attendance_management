package com.example.pfe.Controller;

import com.example.pfe.Service.TeamAssignmentService;
import com.example.pfe.dto.TeamAssignmentDTO;
import com.example.pfe.entities.TeamAssignment;
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
    public ResponseEntity<List<TeamAssignment>> getProjectTeam(@PathVariable Long projectId) {

        List<TeamAssignment> team = teamAssignmentService.getProjectTeam(projectId);
        return ResponseEntity.ok(team);
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<TeamAssignment>> getEmployeeAssignments(
            @PathVariable Long employeeId) {

        List<TeamAssignment> assignments = teamAssignmentService.getEmployeeAssignments(employeeId);
        return ResponseEntity.ok(assignments);
    }
}