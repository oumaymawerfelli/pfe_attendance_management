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
    Page<ProjectResponseDTO> getAllProjects(Pageable pageable);
    void deleteProject(Long id);

    // Business Operations
    ProjectResponseDTO updateProjectStatus(Long id, ProjectStatus status);
    List<ProjectResponseDTO> getProjectsByStatus(ProjectStatus status);
    List<ProjectResponseDTO> getProjectsByManager(Long managerId);
    List<ProjectResponseDTO> searchProjects(String keyword);
    List<ProjectResponseDTO> getActiveProjects();
    List<ProjectResponseDTO> getInactiveProjects();
    long countProjectsByStatus(ProjectStatus status);
    boolean existsByCode(String code);
    ProjectResponseDTO getProjectByCode(String code);

    // Team Management - CHANGER LE TYPE DE RETOUR ICI
    TeamAssignmentResponseDTO assignTeamMember(TeamAssignmentDTO assignmentDTO);
    void removeTeamMember(Long assignmentId);
    List<TeamMemberDTO> getProjectTeamMembers(Long projectId);
    List<ProjectResponseDTO> getEmployeeProjects(Long employeeId);
    ProjectWithTeamDTO getProjectWithTeam(Long projectId);
}