package com.example.pfe.mapper;

import com.example.pfe.dto.ProjectRequestDTO;
import com.example.pfe.dto.ProjectResponseDTO;
import com.example.pfe.entities.Project;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public Project toEntity(ProjectRequestDTO dto) {
        if (dto == null) {
            return null;
        }

        Project project = new Project();
        // PAS de setCode() ici - le code est généré dans le service
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setStatus(dto.getStatus());
        project.setStartDate(dto.getStartDate());
        project.setEndDate(dto.getEndDate());

        // Note: Le code, id, projectManager, teamAssignments, assignmentDate,
        // createdAt, updatedAt sont définis dans le service

        return project;
    }

    public ProjectResponseDTO toDTO(Project project) {
        if (project == null) {
            return null;
        }

        ProjectResponseDTO dto = new ProjectResponseDTO();
        dto.setId(project.getId());
        dto.setCode(project.getCode()); // Ici c'est OK, on lit depuis l'entité
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setStatus(project.getStatus());
        dto.setStartDate(project.getStartDate());
        dto.setEndDate(project.getEndDate());
        dto.setAssignmentDate(project.getAssignmentDate());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());

        // Set manager information if available
        if (project.getProjectManager() != null) {
            dto.setProjectManagerName(
                    project.getProjectManager().getFirstName() + " " +
                            project.getProjectManager().getLastName()
            );
            dto.setProjectManagerEmail(project.getProjectManager().getEmail());
        }

        return dto;
    }

    public void updateEntityFromDto(ProjectRequestDTO dto, Project project) {
        if (dto == null || project == null) {
            return;
        }

        // Update only non-null fields
        // NE PAS mettre à jour le code - il est généré une seule fois
        if (dto.getName() != null) {
            project.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            project.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null) {
            project.setStatus(dto.getStatus());
        }
        if (dto.getStartDate() != null) {
            project.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            project.setEndDate(dto.getEndDate());
        }

        // Note: On ne met pas à jour le code, id, projectManager, teamAssignments,
        // assignmentDate, createdAt, updatedAt ici
        // Ceux-ci sont gérés séparément dans le service
    }
}