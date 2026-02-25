package com.example.pfe.Service;

import com.example.pfe.dto.*;
import com.example.pfe.entities.Project;
import com.example.pfe.entities.TeamAssignment;
import com.example.pfe.entities.User;
import com.example.pfe.enums.ProjectStatus;
import com.example.pfe.mapper.ProjectMapper;
import com.example.pfe.Repository.ProjectRepository;
import com.example.pfe.Repository.TeamAssignmentRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TeamAssignmentRepository teamAssignmentRepository;
    private final TeamAssignmentService teamAssignmentService;
    private final ProjectMapper projectMapper;

    // ==================== CRUD OPERATIONS ====================

    @Override
    public ProjectResponseDTO createProject(ProjectRequestDTO requestDTO) {
        log.info("Creating project: {}", requestDTO.getName());

        // Validate dates
        if (requestDTO.getEndDate() != null &&
                requestDTO.getStartDate() != null &&
                requestDTO.getEndDate().isBefore(requestDTO.getStartDate())) {
            throw new BusinessException("End date cannot be before start date");
        }

        // Generate project code
        String projectCode = generateProjectCode(requestDTO.getName());

        // Check if code already exists
        if (projectRepository.existsByCode(projectCode)) {
            projectCode = projectCode + "-" + System.currentTimeMillis() % 1000;
        }

        // Map DTO to entity
        Project project = mapToEntity(requestDTO);
        project.setCode(projectCode);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());

        // Set project manager if provided
        if (requestDTO.getProjectManagerId() != null) {
            User projectManager = userRepository.findById(requestDTO.getProjectManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Project manager not found: " + requestDTO.getProjectManagerId()));
            project.setProjectManager(projectManager);
        }

        Project savedProject = projectRepository.save(project);
        log.info("Project created successfully: {} (ID: {})", projectCode, savedProject.getId());

        return mapToProjectResponseDTO(savedProject);
    }

    @Override
    public ProjectResponseDTO updateProject(Long id, ProjectRequestDTO requestDTO) {
        log.info("Updating project ID: {}", id);

        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + id));

        // Validate dates
        if (requestDTO.getEndDate() != null &&
                requestDTO.getStartDate() != null &&
                requestDTO.getEndDate().isBefore(requestDTO.getStartDate())) {
            throw new BusinessException("End date cannot be before start date");
        }

        // Update fields
        project.setName(requestDTO.getName());
        project.setDescription(requestDTO.getDescription());
        project.setStatus(requestDTO.getStatus());
        project.setStartDate(requestDTO.getStartDate());
        project.setEndDate(requestDTO.getEndDate());
        project.setUpdatedAt(LocalDateTime.now());

        // Update project manager if changed
        if (requestDTO.getProjectManagerId() != null &&
                (project.getProjectManager() == null ||
                        !project.getProjectManager().getId().equals(requestDTO.getProjectManagerId()))) {
            User projectManager = userRepository.findById(requestDTO.getProjectManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Project manager not found: " + requestDTO.getProjectManagerId()));
            project.setProjectManager(projectManager);
            project.setAssignmentDate(LocalDateTime.now());
        } else if (requestDTO.getProjectManagerId() == null && project.getProjectManager() != null) {
            project.setProjectManager(null);
            project.setAssignmentDate(null);
        }

        Project updatedProject = projectRepository.save(project);
        log.info("Project updated successfully: {} (ID: {})", project.getCode(), updatedProject.getId());

        return mapToProjectResponseDTO(updatedProject);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponseDTO getProjectById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + id));
        return mapToProjectResponseDTO(project);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectWithTeamDTO getProjectWithTeam(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + projectId));

        List<TeamAssignment> team = teamAssignmentRepository.findByProjectId(projectId);

        return mapToProjectResponseWithTeam(project, team);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectResponseDTO> getAllProjects(Pageable pageable) {
        log.debug("Fetching all projects with pageable: page={}, size={}, sort={}",
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());

        Page<Project> projectPage = projectRepository.findAll(pageable);

        log.debug("Found {} projects on page {}",
                projectPage.getNumberOfElements(), projectPage.getNumber());

        return projectPage.map(project -> mapToProjectResponseDTO(project));
    }

    @Override
    public void deleteProject(Long id) {
        log.info("Deleting project ID: {}", id);

        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + id));

        // Check if project has team members
        List<TeamAssignment> teamAssignments = teamAssignmentRepository.findByProjectId(id);
        if (!teamAssignments.isEmpty()) {
            throw new BusinessException("Cannot delete project with assigned team members");
        }

        projectRepository.delete(project);
        log.info("Project deleted: {} (ID: {})", project.getCode(), id);
    }

    // ==================== BUSINESS OPERATIONS ====================

    @Override
    public ProjectResponseDTO updateProjectStatus(Long id, ProjectStatus status) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + id));

        project.setStatus(status);
        project.setUpdatedAt(LocalDateTime.now());

        Project updatedProject = projectRepository.save(project);
        log.info("Project {} status updated to {}", project.getCode(), status);

        return mapToProjectResponseDTO(updatedProject);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getProjectsByStatus(ProjectStatus status) {
        return projectRepository.findByStatus(status).stream()
                .map(project -> mapToProjectResponseDTO(project))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getProjectsByManager(Long managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Manager not found: " + managerId));

        return projectRepository.findByProjectManager(manager).stream()
                .map(project -> mapToProjectResponseDTO(project))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> searchProjects(String keyword) {
        return projectRepository.searchProjects(keyword).stream()
                .map(project -> mapToProjectResponseDTO(project))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getActiveProjects() {
        return projectRepository.findActiveProjects().stream()
                .map(project -> mapToProjectResponseDTO(project))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long countProjectsByStatus(ProjectStatus status) {
        return projectRepository.countProjectsByStatus(status);
    }

    // ==================== TEAM MANAGEMENT ====================

    @Override
    public TeamAssignmentResponseDTO assignTeamMember(TeamAssignmentDTO assignmentDTO) {
        return teamAssignmentService.assignEmployeeToProject(assignmentDTO);
    }

    @Override
    public void removeTeamMember(Long assignmentId) {
        teamAssignmentService.removeEmployeeFromProject(assignmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMemberDTO> getProjectTeamMembers(Long projectId) {
        List<TeamAssignment> team = teamAssignmentRepository.findByProjectId(projectId);

        return team.stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getActive()))
                .map(assignment -> mapToTeamMemberDTO(assignment))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getEmployeeProjects(Long employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + employeeId));

        // Utiliser findByEmployeeId avec filtrage manuel
        List<TeamAssignment> assignments = teamAssignmentRepository.findByEmployeeId(employeeId)
                .stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getActive()))
                .collect(Collectors.toList());

        return assignments.stream()
                .map(TeamAssignment::getProject)
                .distinct()
                .map(project -> mapToProjectResponseDTO(project))
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private String generateProjectCode(String projectName) {
        String prefix = "PRJ";
        String year = String.valueOf(LocalDateTime.now().getYear()).substring(2);
        String random = String.format("%03d", (int) (Math.random() * 1000));

        // Take first 3 letters of project name (uppercase)
        String namePart = projectName.length() >= 3
                ? projectName.substring(0, 3).toUpperCase()
                : projectName.toUpperCase();

        return prefix + year + namePart + random;
    }

    private Project mapToEntity(ProjectRequestDTO dto) {
        return Project.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .status(dto.getStatus() != null ? dto.getStatus() : ProjectStatus.PLANNED)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .build();
    }

    /**
     * Map Project entity to ProjectResponseDTO
     */
    private ProjectResponseDTO mapToProjectResponseDTO(Project project) {
        ProjectResponseDTO dto = new ProjectResponseDTO();

        dto.setId(project.getId());
        dto.setCode(project.getCode());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setStatus(project.getStatus());
        dto.setStartDate(project.getStartDate());
        dto.setEndDate(project.getEndDate());
        dto.setAssignmentDate(project.getAssignmentDate());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());

        // Project manager info
        if (project.getProjectManager() != null) {
            dto.setProjectManagerName(
                    project.getProjectManager().getFirstName() + " " +
                            project.getProjectManager().getLastName());
            dto.setProjectManagerEmail(project.getProjectManager().getEmail());
        }

        return dto;
    }

    /**
     * Map Project + Team to ProjectWithTeamDTO
     */
    private ProjectWithTeamDTO mapToProjectResponseWithTeam(Project project,
                                                            List<TeamAssignment> team) {
        ProjectWithTeamDTO response = new ProjectWithTeamDTO();

        ProjectResponseDTO baseDto = mapToProjectResponseDTO(project);
        copyProperties(baseDto, response);

        List<TeamMemberDTO> teamMembers = team.stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getActive()))
                .map(assignment -> mapToTeamMemberDTO(assignment))
                .collect(Collectors.toList());

        response.setTeamMembers(teamMembers);
        return response;
    }

    /**
     * Map TeamAssignment to TeamMemberDTO
     */
    private TeamMemberDTO mapToTeamMemberDTO(TeamAssignment assignment) {
        return TeamMemberDTO.builder()
                .id(assignment.getEmployee().getId())
                .assignmentId(assignment.getId() != null ? assignment.getId().longValue() : null) // ← AJOUTER
                .firstName(assignment.getEmployee().getFirstName())
                .lastName(assignment.getEmployee().getLastName())
                .email(assignment.getEmployee().getEmail())
                .assignedDate(assignment.getAddedDate())
                .assigningManager(
                        assignment.getAssigningManager().getFirstName() + " " +
                                assignment.getAssigningManager().getLastName())
                .build();
    }

    /**
     * Helper method to copy properties
     */
    private void copyProperties(ProjectResponseDTO source, ProjectWithTeamDTO target) {
        target.setId(source.getId());
        target.setCode(source.getCode());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setStatus(source.getStatus());
        target.setStartDate(source.getStartDate());
        target.setEndDate(source.getEndDate());
        target.setAssignmentDate(source.getAssignmentDate());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setProjectManagerName(source.getProjectManagerName());
        target.setProjectManagerEmail(source.getProjectManagerEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        return projectRepository.existsByCode(code);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponseDTO getProjectByCode(String code) {
        Project project = projectRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with code: " + code));
        return mapToProjectResponseDTO(project);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getInactiveProjects() {
        return projectRepository.findInactiveProjects().stream()
                .map(this::mapToProjectResponseDTO)
                .collect(Collectors.toList());
    }
}