package com.example.pfe.Service;



import com.example.pfe.dto.TeamAssignmentDTO;
import com.example.pfe.entities.TeamAssignment;
import com.example.pfe.entities.Project;
import com.example.pfe.entities.User;
import com.example.pfe.Repository.TeamAssignmentRepository;
import com.example.pfe.Repository.ProjectRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TeamAssignmentService {

    private final TeamAssignmentRepository teamAssignmentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    /**
     * Assign employee to project as team member
     */
    public TeamAssignment assignEmployeeToProject(TeamAssignmentDTO assignmentDTO) {
        log.info("Assigning employee {} to project {}",
                assignmentDTO.getEmployeeId(), assignmentDTO.getProjectId());

        Project project = projectRepository.findById(assignmentDTO.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + assignmentDTO.getProjectId()));

        User employee = userRepository.findById(assignmentDTO.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + assignmentDTO.getEmployeeId()));

        User assigningManager = userRepository.findById(assignmentDTO.getAssigningManagerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Manager not found: " + assignmentDTO.getAssigningManagerId()));

        // Check if already assigned
        boolean alreadyAssigned = teamAssignmentRepository.existsByProjectAndEmployee(
                project, employee);

        if (alreadyAssigned) {
            throw new BusinessException("Employee is already assigned to this project");
        }

        // Create team assignment
        TeamAssignment assignment = TeamAssignment.builder()
                .project(project)
                .employee(employee)
                .assigningManager(assigningManager)
                .addedDate(LocalDate.now())
                .active(true)
                .build();

        TeamAssignment saved = teamAssignmentRepository.save(assignment);
        log.info("Team assignment created: {}", saved.getId());

        return saved;
    }

    /**
     * Remove employee from project team
     */
    public void removeEmployeeFromProject(Long assignmentId) {
        TeamAssignment assignment = teamAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Team assignment not found: " + assignmentId));

        assignment.setActive(false);
        teamAssignmentRepository.save(assignment);

        log.info("Team assignment deactivated: {}", assignmentId);
    }

    /**
     * Get all team assignments for a project
     */
    @Transactional(readOnly = true)
    public List<TeamAssignment> getProjectTeam(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + projectId));

        return teamAssignmentRepository.findByProjectAndActiveTrue(project);
    }

    /**
     * Get all projects for an employee
     */
    @Transactional(readOnly = true)
    public List<TeamAssignment> getEmployeeAssignments(Long employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + employeeId));

        return teamAssignmentRepository.findByEmployeeAndActiveTrue(employee);
    }
}
