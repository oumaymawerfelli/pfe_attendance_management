package com.example.pfe.Service;

import com.example.pfe.Repository.ProjectStatusHistoryRepository;
import com.example.pfe.dto.*;
import com.example.pfe.entities.Project;
import com.example.pfe.entities.ProjectStatusHistory;
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
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final NotificationService notificationService;
    private final ProjectStatusHistoryRepository historyRepository;

    // ==================== CRUD OPERATIONS ====================

    @Override
    public ProjectResponseDTO createProject(ProjectRequestDTO requestDTO) {
        log.info("Creating project: {}......", requestDTO.getName());

        if (requestDTO.getEndDate() != null &&
                requestDTO.getStartDate() != null &&
                requestDTO.getEndDate().isBefore(requestDTO.getStartDate())) {
            throw new BusinessException("End date cannot be before start date");
        }

        String projectCode = generateProjectCode(requestDTO.getName());

        if (projectRepository.existsByCode(projectCode)) {
            projectCode = projectCode + "-" + System.currentTimeMillis() % 1000;
        }

        Project project = mapToEntity(requestDTO);
        project.setCode(projectCode);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());

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

    // ==================== UPDATE PROJECT ====================

    @Override
    public ProjectResponseDTO updateProject(Long id, ProjectRequestDTO requestDTO) {
        log.info("Updating project ID: {}.....", id);

        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + id));

        if (requestDTO.getEndDate() != null &&
                requestDTO.getStartDate() != null &&
                requestDTO.getEndDate().isBefore(requestDTO.getStartDate())) {
            throw new BusinessException("End date cannot be before start date");
        }

        project.setName(requestDTO.getName());
        project.setDescription(requestDTO.getDescription());
        project.setStatus(requestDTO.getStatus());
        project.setStartDate(requestDTO.getStartDate());
        project.setEndDate(requestDTO.getEndDate());
        project.setUpdatedAt(LocalDateTime.now());

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

        teamAssignmentRepository.findByProjectId(project.getId())
                .stream()
                .filter(a -> Boolean.TRUE.equals(a.getActive()))
                .forEach(a -> notificationService.notifyProjectUpdated(
                        a.getEmployee().getId(),
                        project.getName()
                ));

        return mapToProjectResponseDTO(updatedProject);
    }

    // ==================== READ OPERATIONS ====================

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
        return projectPage.map(this::mapToProjectResponseDTO);
    }

    // ==================== DELETE ====================

    @Override
    public void deleteProject(Long id) {
        log.info("Deleting project ID: {}", id);

        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + id));

        List<TeamAssignment> teamAssignments = teamAssignmentRepository.findByProjectId(id);
        if (!teamAssignments.isEmpty()) {
            throw new BusinessException("Cannot delete project with assigned team members");
        }

        projectRepository.delete(project);
        log.info("Project deleted: {} (ID: {})", project.getCode(), id);
    }

    // ==================== STATUS MANAGEMENT ====================

    @Override
    public ProjectResponseDTO updateProjectStatus(Long id, StatusUpdateRequestDTO request) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + id));

        String changedBy = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        ProjectStatusHistory history = ProjectStatusHistory.builder()
                .project(project)
                .fromStatus(project.getStatus())
                .toStatus(request.getStatus())
                .changedBy(changedBy)
                .changedAt(LocalDateTime.now())
                .reason(request.getReason())
                .build();

        project.setStatus(request.getStatus());
        project.setUpdatedAt(LocalDateTime.now());

        projectRepository.save(project);
        historyRepository.save(history);

        log.info("Project {} status updated to {} by {}", project.getCode(), request.getStatus(), changedBy);

        return mapToProjectResponseDTO(project);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectStatusHistoryDTO> getStatusHistory(Long projectId) {
        return historyRepository.findByProjectIdOrderByChangedAtDesc(projectId)
                .stream()
                .map(this::mapToHistoryDTO)
                .collect(Collectors.toList());
    }

    // ==================== BUSINESS QUERIES ====================

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getProjectsByStatus(ProjectStatus status) {
        return projectRepository.findByStatus(status).stream()
                .map(this::mapToProjectResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getProjectsByManager(Long managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Manager not found: " + managerId));
        return projectRepository.findByProjectManager(manager).stream()
                .map(this::mapToProjectResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> searchProjects(String keyword) {
        return projectRepository.searchProjects(keyword).stream()
                .map(this::mapToProjectResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getActiveProjects() {
        return projectRepository.findActiveProjects().stream()
                .map(this::mapToProjectResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getInactiveProjects() {
        return projectRepository.findInactiveProjects().stream()
                .map(this::mapToProjectResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long countProjectsByStatus(ProjectStatus status) {
        return projectRepository.countProjectsByStatus(status);
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

    // ==================== TEAM MANAGEMENT ====================

    @Override
    public TeamAssignmentResponseDTO assignTeamMember(TeamAssignmentDTO assignmentDTO) {
        TeamAssignmentResponseDTO response = teamAssignmentService.assignEmployeeToProject(assignmentDTO);

        Project project = projectRepository.findById(assignmentDTO.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        notificationService.notifyProjectAssigned(
                assignmentDTO.getEmployeeId(),
                project.getName()
        );

        if (project.getProjectManager() != null) {
            String pmName = project.getProjectManager().getFirstName()
                    + " " + project.getProjectManager().getLastName();
            notificationService.notifyPmAssigned(
                    assignmentDTO.getEmployeeId(),
                    pmName,
                    project.getName()
            );
        }

        return response;
    }

    @Override
    public void removeTeamMember(Long assignmentId) {
        teamAssignmentService.removeEmployeeFromProject(assignmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMemberDTO> getProjectTeamMembers(Long projectId) {
        return teamAssignmentRepository.findByProjectId(projectId).stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getActive()))
                .map(this::mapToTeamMemberDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResponseDTO> getEmployeeProjects(Long employeeId) {
        userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + employeeId));

        return teamAssignmentRepository.findByEmployeeId(employeeId).stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getActive()))
                .map(TeamAssignment::getProject)
                .distinct()
                .map(this::mapToProjectResponseDTO)
                .collect(Collectors.toList());
    }

    // ==================== PRIVATE HELPERS ====================

    private String generateProjectCode(String projectName) {
        String prefix = "PRJ";
        String year = String.valueOf(LocalDateTime.now().getYear()).substring(2);
        String random = String.format("%03d", (int) (Math.random() * 1000));
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
        if (project.getProjectManager() != null) {
            dto.setProjectManagerName(
                    project.getProjectManager().getFirstName() + " " +
                            project.getProjectManager().getLastName());
            dto.setProjectManagerEmail(project.getProjectManager().getEmail());
        }
        return dto;
    }

    private ProjectStatusHistoryDTO mapToHistoryDTO(ProjectStatusHistory h) {
        ProjectStatusHistoryDTO dto = new ProjectStatusHistoryDTO();
        dto.setId(h.getId());
        dto.setFromStatus(h.getFromStatus());
        dto.setToStatus(h.getToStatus());
        dto.setChangedBy(h.getChangedBy());
        dto.setChangedAt(h.getChangedAt());
        dto.setReason(h.getReason());
        return dto;
    }

    private ProjectWithTeamDTO mapToProjectResponseWithTeam(Project project,
                                                            List<TeamAssignment> team) {
        ProjectWithTeamDTO response = new ProjectWithTeamDTO();
        ProjectResponseDTO baseDto = mapToProjectResponseDTO(project);
        copyProperties(baseDto, response);
        List<TeamMemberDTO> teamMembers = team.stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getActive()))
                .map(this::mapToTeamMemberDTO)
                .collect(Collectors.toList());
        response.setTeamMembers(teamMembers);
        return response;
    }

    private TeamMemberDTO mapToTeamMemberDTO(TeamAssignment assignment) {
        return TeamMemberDTO.builder()
                .id(assignment.getEmployee().getId())
                .assignmentId(assignment.getId() != null ? assignment.getId().longValue() : null)
                .firstName(assignment.getEmployee().getFirstName())
                .lastName(assignment.getEmployee().getLastName())
                .email(assignment.getEmployee().getEmail())
                .assignedDate(assignment.getAddedDate())
                .assigningManager(
                        assignment.getAssigningManager().getFirstName() + " " +
                                assignment.getAssigningManager().getLastName())
                .build();
    }

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
}