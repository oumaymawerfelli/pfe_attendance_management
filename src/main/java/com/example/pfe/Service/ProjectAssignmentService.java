package com.example.pfe.Service;

import com.example.pfe.dto.ProjectAssignmentDTO;
import com.example.pfe.entities.Project;
import com.example.pfe.entities.User;
import com.example.pfe.enums.RoleName;
import com.example.pfe.Repository.ProjectRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.Repository.RoleRepository;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProjectAssignmentService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailService emailService;

    /**
     * Assign project to employee (called from controller)
     */
    @PreAuthorize("hasRole('GENERAL_MANAGER')")
    public void assignProjectToEmployee(ProjectAssignmentDTO assignmentDTO) {
        log.info("Assigning project {} to employee {}",
                assignmentDTO.getProjectId(), assignmentDTO.getEmployeeCode());

        assignSingleProject(assignmentDTO);
    }

    /**
     * Remove employee from project (called from controller)
     */
    @PreAuthorize("hasRole('GENERAL_MANAGER')")
    public void removeFromProject(Long projectId, Long employeeId) {
        log.info("Removing employee {} from project {}", employeeId, projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (project.getProjectManager() == null ||
                !project.getProjectManager().getId().equals(employeeId)) {
            throw new BusinessException("Employee is not assigned as manager of this project");
        }

        project.setProjectManager(null);
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);

        // Remove PROJECT_MANAGER role if no longer managing any projects
        removeProjectManagerRoleIfNeeded(employee);

        sendRemovalNotification(project, employee);
    }

    private void assignSingleProject(ProjectAssignmentDTO assignment) {
        Project project = projectRepository.findById(assignment.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + assignment.getProjectId()));

        User employee = userRepository.findByEmployeeCode(assignment.getEmployeeCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + assignment.getEmployeeCode()));

        validateAssignment(project, employee);
        addProjectManagerRole(employee);

        project.setProjectManager(employee);
        project.setAssignmentDate(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());

        projectRepository.save(project);

        sendAssignmentNotification(project, employee, assignment.getAssignmentNotes());
    }

    private void validateAssignment(Project project, User employee) {
        if (project.getProjectManager() != null &&
                project.getProjectManager().getId().equals(employee.getId())) {
            throw new BusinessException(
                    employee.getEmployeeCode() + " is already the manager of this project");
        }

        if (!employee.isEnabled() || !Boolean.TRUE.equals(employee.getActive())) {
            throw new BusinessException("Employee account is not active");
        }

        boolean hasEmployeeRole = employee.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.EMPLOYEE);

        if (!hasEmployeeRole) {
            throw new BusinessException("User is not an employee");
        }
    }

    private void addProjectManagerRole(User employee) {
        boolean alreadyProjectManager = employee.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.PROJECT_MANAGER);

        if (!alreadyProjectManager) {
            roleRepository.findByName(RoleName.PROJECT_MANAGER)
                    .ifPresent(role -> {
                        employee.getRoles().add(role);
                        userRepository.save(employee);
                        log.info("Added PROJECT_MANAGER role to employee: {}",
                                employee.getEmployeeCode());
                    });
        }
    }

    private void removeProjectManagerRoleIfNeeded(User employee) {
        // Check if employee still manages any projects
        long managedProjectsCount = projectRepository.countByProjectManager(employee);

        if (managedProjectsCount == 0) {
            boolean removed = employee.getRoles().removeIf(
                    role -> role.getName() == RoleName.PROJECT_MANAGER);

            if (removed) {
                userRepository.save(employee);
                log.info("Removed PROJECT_MANAGER role from employee: {}",
                        employee.getEmployeeCode());
            }
        }
    }

    private void sendAssignmentNotification(Project project, User employee, String notes) {
        try {
            emailService.sendProjectAssignmentEmail(
                    employee.getEmail(),
                    employee.getFirstName() + " " + employee.getLastName(),
                    project.getName(),
                    project.getDescription(),
                    notes
            );
        } catch (Exception e) {
            log.error("Failed to send assignment email: {}", e.getMessage());
        }
    }

    private void sendRemovalNotification(Project project, User employee) {
        try {
            emailService.sendProjectUnassignmentEmail(
                    employee.getEmail(),
                    employee.getFirstName() + " " + employee.getLastName(),
                    project.getName()
            );
        } catch (Exception e) {
            log.error("Failed to send removal email: {}", e.getMessage());
        }
    }
}