package com.example.pfe.Service;

import com.example.pfe.dto.*;
import com.example.pfe.entities.TeamAssignment;
import com.example.pfe.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProjectService {

    // CRUD Operations
    ProjectResponseDTO createProject(ProjectRequestDTO requestDTO);
    ProjectResponseDTO updateProject(Long id, ProjectRequestDTO requestDTO);
    ProjectResponseDTO getProjectById(Long id);
    ProjectWithTeamDTO getProjectWithTeam(Long projectId);
    Page<ProjectResponseDTO> getAllProjects(Pageable pageable);
    void deleteProject(Long id);

    // Business Operations
    ProjectResponseDTO updateProjectStatus(Long id, ProjectStatus status);
    List<ProjectResponseDTO> getProjectsByStatus(ProjectStatus status);
    List<ProjectResponseDTO> getProjectsByManager(Long managerId);
    List<ProjectResponseDTO> searchProjects(String keyword);
    List<ProjectResponseDTO> getActiveProjects();
    long countProjectsByStatus(ProjectStatus status);
    List<ProjectResponseDTO> getInactiveProjects();

    // Team Management
    TeamAssignment assignTeamMember(TeamAssignmentDTO assignmentDTO);
    void removeTeamMember(Long assignmentId);
    List<TeamMemberDTO> getProjectTeamMembers(Long projectId);
    List<ProjectResponseDTO> getEmployeeProjects(Long employeeId);

    // Utility Methods
    boolean existsByCode(String code);
    ProjectResponseDTO getProjectByCode(String code);
}