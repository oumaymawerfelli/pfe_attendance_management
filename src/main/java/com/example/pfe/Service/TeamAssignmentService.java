package com.example.pfe.Service;

import com.example.pfe.dto.TeamAssignmentDTO;
import com.example.pfe.dto.TeamAssignmentResponseDTO;
import com.example.pfe.entities.Project;
import com.example.pfe.entities.TeamAssignment;
import com.example.pfe.entities.User;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.Repository.ProjectRepository;
import com.example.pfe.Repository.TeamAssignmentRepository;
import com.example.pfe.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TeamAssignmentService {

    private final TeamAssignmentRepository teamAssignmentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    /**
     * Assign an employee to a project
     */
    public TeamAssignmentResponseDTO assignEmployeeToProject(TeamAssignmentDTO assignmentDTO) {
        log.info("Assigning employee ID: {} to project ID: {}",
                assignmentDTO.getEmployeeId(), assignmentDTO.getProjectId());

        // Vérifier que le projet existe
        Project project = projectRepository.findById(assignmentDTO.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with ID: " + assignmentDTO.getProjectId()));

        // Vérifier que l'employé existe
        User employee = userRepository.findById(assignmentDTO.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found with ID: " + assignmentDTO.getEmployeeId()));

        // Vérifier que le manager qui assigne existe
        User assigningManager = userRepository.findById(assignmentDTO.getAssigningManagerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assigning manager not found with ID: " + assignmentDTO.getAssigningManagerId()));

        // Vérifier si l'employé est déjà assigné à ce projet
        boolean alreadyAssigned = teamAssignmentRepository
                .existsByProjectAndEmployeeAndActiveTrue(project, employee);

        if (alreadyAssigned) {
            throw new BusinessException(
                    "Employee is already assigned to this project");
        }

        // Créer et sauvegarder l'assignation avec le builder Lombok
        TeamAssignment assignment = TeamAssignment.builder()
                .project(project)
                .employee(employee)
                .assigningManager(assigningManager)
                .addedDate(LocalDate.now())
                .active(true)
                .build();

        TeamAssignment savedAssignment = teamAssignmentRepository.save(assignment);
        log.info("Employee {} successfully assigned to project {} with assignment ID: {}",
                employee.getEmail(), project.getCode(), savedAssignment.getId());

        return mapToResponseDTO(savedAssignment);
    }

    /**
     * Remove an employee from a project
     */
    public void removeEmployeeFromProject(Long assignmentId) {
        log.info("Removing team assignment ID: {}", assignmentId);

        // Convertir Long en Integer pour la recherche
        TeamAssignment assignment = teamAssignmentRepository.findById(assignmentId.intValue())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Team assignment not found with ID: " + assignmentId));

        // Soft delete - marquer comme inactif plutôt que supprimer
        assignment.setActive(false);
        teamAssignmentRepository.save(assignment);

        log.info("Team assignment ID: {} marked as inactive", assignmentId);
    }

    /**
     * Get all team members for a project
     */
    @Transactional(readOnly = true)
    public List<TeamAssignmentResponseDTO> getProjectTeam(Long projectId) {
        log.debug("Fetching team for project ID: {}", projectId);

        // Vérifier que le projet existe
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found with ID: " + projectId);
        }

        List<TeamAssignment> team = teamAssignmentRepository.findByProjectId(projectId);

        return team.stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getActive()))
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get all project assignments for an employee
     */
    @Transactional(readOnly = true)
    public List<TeamAssignmentResponseDTO> getEmployeeAssignments(Long employeeId) {
        log.debug("Fetching assignments for employee ID: {}", employeeId);

        // Vérifier que l'employé existe
        if (!userRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee not found with ID: " + employeeId);
        }

        List<TeamAssignment> assignments = teamAssignmentRepository.findByEmployeeId(employeeId);

        return assignments.stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getActive()))
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get active assignment for a specific employee on a specific project
     */
    @Transactional(readOnly = true)
    public TeamAssignmentResponseDTO getEmployeeProjectAssignment(Long employeeId, Long projectId) {
        log.debug("Checking if employee ID: {} is assigned to project ID: {}", employeeId, projectId);

        TeamAssignment assignment = teamAssignmentRepository
                .findByProjectIdAndEmployeeId(projectId, employeeId)
                .stream()
                .filter(assgn -> Boolean.TRUE.equals(assgn.getActive()))
                .findFirst()
                .orElse(null);

        return assignment != null ? mapToResponseDTO(assignment) : null;
    }

    /**
     * Check if an employee is assigned to a project
     */
    @Transactional(readOnly = true)
    public boolean isEmployeeAssignedToProject(Long employeeId, Long projectId) {
        return teamAssignmentRepository
                .findByProjectIdAndEmployeeId(projectId, employeeId)
                .stream()
                .anyMatch(assignment -> Boolean.TRUE.equals(assignment.getActive()));
    }

    /**
     * Count active team members for a project
     */
    @Transactional(readOnly = true)
    public long countTeamMembersByProject(Long projectId) {
        return teamAssignmentRepository.findByProjectId(projectId)
                .stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getActive()))
                .count();
    }

    /**
     * Get all assignments (active and inactive) for a project
     */
    @Transactional(readOnly = true)
    public List<TeamAssignmentResponseDTO> getAllProjectAssignments(Long projectId) {
        List<TeamAssignment> assignments = teamAssignmentRepository.findByProjectId(projectId);

        return assignments.stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Map TeamAssignment entity to TeamAssignmentResponseDTO
     */
    private TeamAssignmentResponseDTO mapToResponseDTO(TeamAssignment assignment) {
        if (assignment == null) {
            return null;
        }

        String assigningManagerName = null;
        if (assignment.getAssigningManager() != null) {
            assigningManagerName = assignment.getAssigningManager().getFirstName() +
                    " " + assignment.getAssigningManager().getLastName();
        }

        // Construire le DTO sans le champ notes
        TeamAssignmentResponseDTO dto = new TeamAssignmentResponseDTO();
        dto.setId(assignment.getId() != null ? assignment.getId().longValue() : null);
        dto.setProjectId(assignment.getProject().getId());
        dto.setProjectName(assignment.getProject().getName());
        dto.setProjectCode(assignment.getProject().getCode());
        dto.setEmployeeId(assignment.getEmployee().getId());
        dto.setEmployeeFirstName(assignment.getEmployee().getFirstName());
        dto.setEmployeeLastName(assignment.getEmployee().getLastName());
        dto.setEmployeeEmail(assignment.getEmployee().getEmail());
        dto.setAssigningManagerId(assignment.getAssigningManager() != null ?
                assignment.getAssigningManager().getId() : null);
        dto.setAssigningManagerName(assigningManagerName);
        dto.setAddedDate(assignment.getAddedDate());
        dto.setActive(assignment.getActive());

        return dto;
    }

    /**
     * Batch assign multiple employees to a project
     */
    @Transactional
    public List<TeamAssignmentResponseDTO> assignMultipleEmployeesToProject(
            Long projectId,
            List<Long> employeeIds,
            Long assigningManagerId) {

        log.info("Assigning {} employees to project ID: {}", employeeIds.size(), projectId);

        projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        userRepository.findById(assigningManagerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found: " + assigningManagerId));

        List<TeamAssignmentResponseDTO> results = employeeIds.stream()
                .map(employeeId -> {
                    try {
                        TeamAssignmentDTO dto = new TeamAssignmentDTO();
                        dto.setProjectId(projectId);
                        dto.setEmployeeId(employeeId);
                        dto.setAssigningManagerId(assigningManagerId);

                        return assignEmployeeToProject(dto);
                    } catch (Exception e) {
                        log.error("Failed to assign employee {}: {}", employeeId, e.getMessage());
                        return null;
                    }
                })
                .filter(result -> result != null)
                .collect(Collectors.toList());

        log.info("Successfully assigned {} out of {} employees", results.size(), employeeIds.size());
        return results;
    }

    /**
     * Reactivate a previously removed assignment
     */
    @Transactional
    public TeamAssignmentResponseDTO reactivateAssignment(Long assignmentId) {
        log.info("Reactivating team assignment ID: {}", assignmentId);

        TeamAssignment assignment = teamAssignmentRepository.findById(assignmentId.intValue())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Team assignment not found with ID: " + assignmentId));

        if (Boolean.TRUE.equals(assignment.getActive())) {
            throw new BusinessException("Assignment is already active");
        }

        // Vérifier si l'employé n'est pas déjà actif sur ce projet
        boolean alreadyActive = teamAssignmentRepository
                .findByProjectIdAndEmployeeId(assignment.getProject().getId(), assignment.getEmployee().getId())
                .stream()
                .filter(assgn -> Boolean.TRUE.equals(assgn.getActive()))
                .anyMatch(a -> !a.getId().equals(assignment.getId()));

        if (alreadyActive) {
            throw new BusinessException(
                    "Employee is already actively assigned to this project through another assignment");
        }

        assignment.setActive(true);
        TeamAssignment reactivatedAssignment = teamAssignmentRepository.save(assignment);

        log.info("Team assignment ID: {} reactivated", assignmentId);
        return mapToResponseDTO(reactivatedAssignment);
    }
}