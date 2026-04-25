package com.example.pfe.Service;

import com.example.pfe.Repository.ProjectRepository;
import com.example.pfe.Repository.TeamAssignmentRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.*;
import com.example.pfe.entities.Project;
import com.example.pfe.entities.TeamAssignment;
import com.example.pfe.entities.User;
import com.example.pfe.enums.ProjectStatus;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.ProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectServiceImpl - Tests Unitaires")
class ProjectServiceImplTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private TeamAssignmentRepository teamAssignmentRepository;
    @Mock private TeamAssignmentService teamAssignmentService;
    @Mock private ProjectMapper projectMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private ProjectServiceImpl projectService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private User buildUser(Long id, String firstName, String lastName, String email) {
        User u = new User();
        u.setId(id);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setEmail(email);
        return u;
    }

    private Project buildProject(Long id, String name, String code, ProjectStatus status) {
        Project p = new Project();
        p.setId(id);
        p.setName(name);
        p.setCode(code);
        p.setStatus(status);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    private ProjectRequestDTO buildRequestDTO(String name, ProjectStatus status) {
        ProjectRequestDTO dto = new ProjectRequestDTO();
        dto.setName(name);
        dto.setDescription("Description of " + name);
        dto.setStatus(status);
        dto.setStartDate(LocalDate.now());
        dto.setEndDate(LocalDate.now().plusMonths(3));
        return dto;
    }

    private TeamAssignment buildAssignment(Long id, Project project, User employee, User manager, boolean active) {
        TeamAssignment a = new TeamAssignment();
        a.setId(id.intValue());
        a.setProject(project);
        a.setEmployee(employee);
        a.setAssigningManager(manager);
        a.setActive(active);
        a.setAddedDate(LocalDate.now());
        return a;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // createProject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("createProject()")
    class CreateProject {

        @Test
        @DisplayName("Crée un projet avec succès sans project manager")
        void shouldCreateProjectWithoutManager() {
            ProjectRequestDTO dto = buildRequestDTO("Alpha", ProjectStatus.PLANNED);
            Project saved = buildProject(1L, "Alpha", "PRJ26ALP001", ProjectStatus.PLANNED);

            when(projectRepository.existsByCode(anyString())).thenReturn(false);
            when(projectRepository.save(any(Project.class))).thenReturn(saved);

            ProjectResponseDTO result = projectService.createProject(dto);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Alpha");
            verify(projectRepository).save(any(Project.class));
        }

        @Test
        @DisplayName("Crée un projet avec project manager")
        void shouldCreateProjectWithManager() {
            ProjectRequestDTO dto = buildRequestDTO("Beta", ProjectStatus.PLANNED);
            dto.setProjectManagerId(10L);

            User pm = buildUser(10L, "Alice", "Smith", "alice@test.com");
            Project saved = buildProject(1L, "Beta", "PRJ26BET001", ProjectStatus.PLANNED);
            saved.setProjectManager(pm);

            when(projectRepository.existsByCode(anyString())).thenReturn(false);
            when(userRepository.findById(10L)).thenReturn(Optional.of(pm));
            when(projectRepository.save(any(Project.class))).thenReturn(saved);

            ProjectResponseDTO result = projectService.createProject(dto);

            assertThat(result.getProjectManagerName()).isEqualTo("Alice Smith");
            assertThat(result.getProjectManagerEmail()).isEqualTo("alice@test.com");
        }

        @Test
        @DisplayName("Lève BusinessException si endDate est avant startDate")
        void shouldThrowWhenEndDateBeforeStartDate() {
            ProjectRequestDTO dto = buildRequestDTO("Gamma", ProjectStatus.PLANNED);
            dto.setStartDate(LocalDate.now().plusMonths(1));
            dto.setEndDate(LocalDate.now());

            assertThatThrownBy(() -> projectService.createProject(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("End date cannot be before start date");

            verify(projectRepository, never()).save(any());
        }

        @Test
        @DisplayName("Ajoute un suffixe si le code projet existe déjà")
        void shouldAppendSuffixWhenCodeExists() {
            ProjectRequestDTO dto = buildRequestDTO("Delta", ProjectStatus.PLANNED);
            Project saved = buildProject(1L, "Delta", "PRJ26DEL001-123", ProjectStatus.PLANNED);

            when(projectRepository.existsByCode(anyString())).thenReturn(true);
            when(projectRepository.save(any(Project.class))).thenReturn(saved);

            ProjectResponseDTO result = projectService.createProject(dto);

            assertThat(result).isNotNull();
            // Le code doit avoir été modifié avec un suffixe
            ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
            verify(projectRepository).save(captor.capture());
            assertThat(captor.getValue().getCode()).contains("-");
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le project manager n'existe pas")
        void shouldThrowWhenManagerNotFound() {
            ProjectRequestDTO dto = buildRequestDTO("Epsilon", ProjectStatus.PLANNED);
            dto.setProjectManagerId(99L);

            when(projectRepository.existsByCode(anyString())).thenReturn(false);
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.createProject(dto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("Utilise le statut PLANNED par défaut si status est null")
        void shouldDefaultToPlannedStatus() {
            ProjectRequestDTO dto = buildRequestDTO("Zeta", null);
            Project saved = buildProject(1L, "Zeta", "PRJ26ZET001", ProjectStatus.PLANNED);

            when(projectRepository.existsByCode(anyString())).thenReturn(false);
            when(projectRepository.save(any(Project.class))).thenReturn(saved);

            projectService.createProject(dto);

            ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
            verify(projectRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(ProjectStatus.PLANNED);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // updateProject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("updateProject()")
    class UpdateProject {

        @Test
        @DisplayName("Met à jour les champs basiques du projet")
        void shouldUpdateBasicFields() {
            Project existing = buildProject(1L, "Old Name", "PRJ001", ProjectStatus.PLANNED);
            ProjectRequestDTO dto = buildRequestDTO("New Name", ProjectStatus.IN_PROGRESS);

            when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(projectRepository.save(any())).thenReturn(existing);
            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of());

            ProjectResponseDTO result = projectService.updateProject(1L, dto);

            assertThat(existing.getName()).isEqualTo("New Name");
            assertThat(existing.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
            verify(projectRepository).save(existing);
        }

        @Test
        @DisplayName("Change le project manager si l'ID est différent")
        void shouldChangeProjectManager() {
            User oldPm = buildUser(1L, "Old", "PM", "old@test.com");
            User newPm = buildUser(2L, "New", "PM", "new@test.com");
            Project existing = buildProject(1L, "Proj", "PRJ001", ProjectStatus.PLANNED);
            existing.setProjectManager(oldPm);

            ProjectRequestDTO dto = buildRequestDTO("Proj", ProjectStatus.PLANNED);
            dto.setProjectManagerId(2L);

            when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(userRepository.findById(2L)).thenReturn(Optional.of(newPm));
            when(projectRepository.save(any())).thenReturn(existing);
            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of());

            projectService.updateProject(1L, dto);

            assertThat(existing.getProjectManager()).isSameAs(newPm);
            assertThat(existing.getAssignmentDate()).isNotNull();
        }

        @Test
        @DisplayName("Supprime le project manager si projectManagerId est null")
        void shouldRemoveProjectManager() {
            User pm = buildUser(1L, "PM", "User", "pm@test.com");
            Project existing = buildProject(1L, "Proj", "PRJ001", ProjectStatus.PLANNED);
            existing.setProjectManager(pm);

            ProjectRequestDTO dto = buildRequestDTO("Proj", ProjectStatus.PLANNED);
            dto.setProjectManagerId(null);

            when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(projectRepository.save(any())).thenReturn(existing);
            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of());

            projectService.updateProject(1L, dto);

            assertThat(existing.getProjectManager()).isNull();
            assertThat(existing.getAssignmentDate()).isNull();
        }

        @Test
        @DisplayName("Notifie les membres actifs de l'équipe après la mise à jour")
        void shouldNotifyActiveTeamMembers() {
            Project existing = buildProject(1L, "Proj", "PRJ001", ProjectStatus.PLANNED);
            ProjectRequestDTO dto = buildRequestDTO("Proj Updated", ProjectStatus.IN_PROGRESS);

            User emp1 = buildUser(10L, "Alice", "A", "a@test.com");
            User emp2 = buildUser(11L, "Bob", "B", "b@test.com");
            User manager = buildUser(99L, "Mgr", "M", "m@test.com");

            TeamAssignment a1 = buildAssignment(1L, existing, emp1, manager, true);
            TeamAssignment a2 = buildAssignment(2L, existing, emp2, manager, false); // inactive

            when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(projectRepository.save(any())).thenReturn(existing);
            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of(a1, a2));

            projectService.updateProject(1L, dto);

            // Seul emp1 (active=true) doit être notifié, avec le nouveau nom du projet
            verify(notificationService, times(1)).notifyProjectUpdated(10L, "Proj Updated");
            verify(notificationService, never()).notifyProjectUpdated(eq(11L), any());
        }

        @Test
        @DisplayName("Lève BusinessException si endDate est avant startDate")
        void shouldThrowWhenDatesInvalid() {
            Project existing = buildProject(1L, "Proj", "PRJ001", ProjectStatus.PLANNED);
            ProjectRequestDTO dto = buildRequestDTO("Proj", ProjectStatus.PLANNED);
            dto.setStartDate(LocalDate.now().plusMonths(2));
            dto.setEndDate(LocalDate.now());

            when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> projectService.updateProject(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("End date cannot be before start date");
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le projet n'existe pas")
        void shouldThrowWhenProjectNotFound() {
            when(projectRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.updateProject(999L, buildRequestDTO("X", ProjectStatus.PLANNED)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getProjectById
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getProjectById()")
    class GetProjectById {

        @Test
        @DisplayName("Retourne le DTO quand le projet existe")
        void shouldReturnProject() {
            Project project = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

            ProjectResponseDTO result = projectService.getProjectById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Alpha");
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le projet n'existe pas")
        void shouldThrowWhenNotFound() {
            when(projectRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getProjectById(42L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("42");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getProjectWithTeam
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getProjectWithTeam()")
    class GetProjectWithTeam {

        @Test
        @DisplayName("Retourne le projet avec les membres actifs de l'équipe")
        void shouldReturnProjectWithActiveTeam() {
            Project project = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            User emp = buildUser(5L, "Jane", "Doe", "jane@test.com");
            User mgr = buildUser(6L, "Bob", "Mgr", "bob@test.com");

            TeamAssignment active = buildAssignment(1L, project, emp, mgr, true);
            TeamAssignment inactive = buildAssignment(2L, project, emp, mgr, false);

            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of(active, inactive));

            ProjectWithTeamDTO result = projectService.getProjectWithTeam(1L);

            assertThat(result.getTeamMembers()).hasSize(1);
            assertThat(result.getTeamMembers().get(0).getFirstName()).isEqualTo("Jane");
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le projet n'existe pas")
        void shouldThrowWhenProjectNotFound() {
            when(projectRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getProjectWithTeam(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getAllProjects
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getAllProjects()")
    class GetAllProjects {

        @Test
        @DisplayName("Retourne une page de projets")
        void shouldReturnPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Project p = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            when(projectRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(p)));

            Page<ProjectResponseDTO> result = projectService.getAllProjects(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Retourne une page vide si aucun projet")
        void shouldReturnEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(projectRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

            Page<ProjectResponseDTO> result = projectService.getAllProjects(pageable);

            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // deleteProject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("deleteProject()")
    class DeleteProject {

        @Test
        @DisplayName("Supprime un projet sans membres d'équipe")
        void shouldDeleteProjectWithNoTeam() {
            Project project = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of());

            projectService.deleteProject(1L);

            verify(projectRepository).delete(project);
        }

        @Test
        @DisplayName("Lève BusinessException si le projet a des membres d'équipe")
        void shouldThrowWhenProjectHasTeamMembers() {
            Project project = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            User emp = buildUser(5L, "Jane", "Doe", "jane@test.com");
            User mgr = buildUser(6L, "Bob", "Mgr", "bob@test.com");
            TeamAssignment assignment = buildAssignment(1L, project, emp, mgr, true);

            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of(assignment));

            assertThatThrownBy(() -> projectService.deleteProject(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot delete project with assigned team members");

            verify(projectRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le projet n'existe pas")
        void shouldThrowWhenNotFound() {
            when(projectRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.deleteProject(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // updateProjectStatus
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("updateProjectStatus()")
    class UpdateProjectStatus {

        @Test
        @DisplayName("Met à jour le statut du projet")
        void shouldUpdateStatus() {
            Project project = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(projectRepository.save(any())).thenReturn(project);

            ProjectResponseDTO result = projectService.updateProjectStatus(1L, ProjectStatus.IN_PROGRESS);

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
            assertThat(project.getUpdatedAt()).isNotNull();
            verify(projectRepository).save(project);
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le projet n'existe pas")
        void shouldThrowWhenNotFound() {
            when(projectRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.updateProjectStatus(99L, ProjectStatus.COMPLETED))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getProjectsByStatus
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getProjectsByStatus()")
    class GetProjectsByStatus {

        @Test
        @DisplayName("Retourne les projets filtrés par statut")
        void shouldReturnByStatus() {
            Project p = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.IN_PROGRESS);
            when(projectRepository.findByStatus(ProjectStatus.IN_PROGRESS)).thenReturn(List.of(p));

            List<ProjectResponseDTO> result = projectService.getProjectsByStatus(ProjectStatus.IN_PROGRESS);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("Retourne une liste vide si aucun projet avec ce statut")
        void shouldReturnEmptyList() {
            when(projectRepository.findByStatus(ProjectStatus.COMPLETED)).thenReturn(List.of());

            List<ProjectResponseDTO> result = projectService.getProjectsByStatus(ProjectStatus.COMPLETED);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getProjectsByManager
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getProjectsByManager()")
    class GetProjectsByManager {

        @Test
        @DisplayName("Retourne les projets gérés par un manager")
        void shouldReturnProjectsForManager() {
            User manager = buildUser(1L, "Alice", "Smith", "alice@test.com");
            Project p = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            p.setProjectManager(manager);

            when(userRepository.findById(1L)).thenReturn(Optional.of(manager));
            when(projectRepository.findByProjectManager(manager)).thenReturn(List.of(p));

            List<ProjectResponseDTO> result = projectService.getProjectsByManager(1L);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le manager n'existe pas")
        void shouldThrowWhenManagerNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getProjectsByManager(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // searchProjects
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("searchProjects()")
    class SearchProjects {

        @Test
        @DisplayName("Retourne les projets correspondant au mot-clé")
        void shouldReturnMatchingProjects() {
            Project p = buildProject(1L, "Alpha Project", "PRJ001", ProjectStatus.PLANNED);
            when(projectRepository.searchProjects("alpha")).thenReturn(List.of(p));

            List<ProjectResponseDTO> result = projectService.searchProjects("alpha");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Alpha Project");
        }

        @Test
        @DisplayName("Retourne une liste vide si aucun résultat")
        void shouldReturnEmptyWhenNoMatch() {
            when(projectRepository.searchProjects("xyz")).thenReturn(List.of());

            List<ProjectResponseDTO> result = projectService.searchProjects("xyz");

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // assignTeamMember
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("assignTeamMember()")
    class AssignTeamMember {

        @Test
        @DisplayName("Assigne un membre et envoie les notifications")
        void shouldAssignAndNotify() {
            User pm = buildUser(5L, "Alice", "Smith", "alice@test.com");
            Project project = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            project.setProjectManager(pm);

            TeamAssignmentDTO dto = new TeamAssignmentDTO();
            dto.setProjectId(1L);
            dto.setEmployeeId(10L);

            TeamAssignmentResponseDTO response = new TeamAssignmentResponseDTO();

            when(teamAssignmentService.assignEmployeeToProject(dto)).thenReturn(response);
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

            TeamAssignmentResponseDTO result = projectService.assignTeamMember(dto);

            assertThat(result).isSameAs(response);
            verify(notificationService).notifyProjectAssigned(10L, "Alpha");
            verify(notificationService).notifyPmAssigned(eq(10L), eq("Alice Smith"), eq("Alpha"));
        }

        @Test
        @DisplayName("N'envoie pas la notification PM si pas de project manager")
        void shouldNotNotifyPmWhenNoneSet() {
            Project project = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            // pas de PM

            TeamAssignmentDTO dto = new TeamAssignmentDTO();
            dto.setProjectId(1L);
            dto.setEmployeeId(10L);

            when(teamAssignmentService.assignEmployeeToProject(dto)).thenReturn(new TeamAssignmentResponseDTO());
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

            projectService.assignTeamMember(dto);

            verify(notificationService).notifyProjectAssigned(10L, "Alpha");
            verify(notificationService, never()).notifyPmAssigned(any(), any(), any());
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le projet n'existe pas")
        void shouldThrowWhenProjectNotFound() {
            TeamAssignmentDTO dto = new TeamAssignmentDTO();
            dto.setProjectId(99L);
            dto.setEmployeeId(10L);

            when(teamAssignmentService.assignEmployeeToProject(dto)).thenReturn(new TeamAssignmentResponseDTO());
            when(projectRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.assignTeamMember(dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // removeTeamMember
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("removeTeamMember()")
    class RemoveTeamMember {

        @Test
        @DisplayName("Délègue la suppression au TeamAssignmentService")
        void shouldDelegateToService() {
            projectService.removeTeamMember(5L);

            verify(teamAssignmentService).removeEmployeeFromProject(5L);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getProjectTeamMembers
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getProjectTeamMembers()")
    class GetProjectTeamMembers {

        @Test
        @DisplayName("Retourne uniquement les membres actifs")
        void shouldReturnOnlyActiveMembers() {
            Project project = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            User emp1 = buildUser(10L, "Jane", "A", "jane@test.com");
            User emp2 = buildUser(11L, "Bob", "B", "bob@test.com");
            User mgr = buildUser(99L, "Mgr", "M", "m@test.com");

            TeamAssignment active = buildAssignment(1L, project, emp1, mgr, true);
            TeamAssignment inactive = buildAssignment(2L, project, emp2, mgr, false);

            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of(active, inactive));

            List<TeamMemberDTO> result = projectService.getProjectTeamMembers(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFirstName()).isEqualTo("Jane");
        }

        @Test
        @DisplayName("Retourne une liste vide si aucun membre actif")
        void shouldReturnEmptyWhenNoActiveMembers() {
            Project project = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            User emp = buildUser(10L, "Jane", "A", "jane@test.com");
            User mgr = buildUser(99L, "Mgr", "M", "m@test.com");

            TeamAssignment inactive = buildAssignment(1L, project, emp, mgr, false);
            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of(inactive));

            List<TeamMemberDTO> result = projectService.getProjectTeamMembers(1L);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getEmployeeProjects
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getEmployeeProjects()")
    class GetEmployeeProjects {

        @Test
        @DisplayName("Retourne les projets actifs d'un employé sans doublons")
        void shouldReturnDistinctActiveProjects() {
            User employee = buildUser(5L, "Jane", "Doe", "jane@test.com");
            User mgr = buildUser(99L, "Mgr", "M", "m@test.com");
            Project p1 = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            Project p2 = buildProject(2L, "Beta", "PRJ002", ProjectStatus.IN_PROGRESS);

            TeamAssignment a1 = buildAssignment(1L, p1, employee, mgr, true);
            TeamAssignment a2 = buildAssignment(2L, p2, employee, mgr, true);
            TeamAssignment a3 = buildAssignment(3L, p1, employee, mgr, false); // inactive, same project

            when(userRepository.findById(5L)).thenReturn(Optional.of(employee));
            when(teamAssignmentRepository.findByEmployeeId(5L)).thenReturn(List.of(a1, a2, a3));

            List<ProjectResponseDTO> result = projectService.getEmployeeProjects(5L);

            // a3 est inactif, a1 et a2 sont actifs → 2 projets distincts
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si l'employé n'existe pas")
        void shouldThrowWhenEmployeeNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getEmployeeProjects(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // existsByCode / getProjectByCode
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("existsByCode() & getProjectByCode()")
    class CodeLookup {

        @Test
        @DisplayName("existsByCode retourne true si le code existe")
        void shouldReturnTrueWhenCodeExists() {
            when(projectRepository.existsByCode("PRJ001")).thenReturn(true);

            assertThat(projectService.existsByCode("PRJ001")).isTrue();
        }

        @Test
        @DisplayName("getProjectByCode retourne le DTO si le code existe")
        void shouldReturnProjectByCode() {
            Project p = buildProject(1L, "Alpha", "PRJ001", ProjectStatus.PLANNED);
            when(projectRepository.findByCode("PRJ001")).thenReturn(Optional.of(p));

            ProjectResponseDTO result = projectService.getProjectByCode("PRJ001");

            assertThat(result.getCode()).isEqualTo("PRJ001");
        }

        @Test
        @DisplayName("getProjectByCode lève ResourceNotFoundException si le code n'existe pas")
        void shouldThrowWhenCodeNotFound() {
            when(projectRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> projectService.getProjectByCode("UNKNOWN"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("UNKNOWN");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // countProjectsByStatus
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("countProjectsByStatus()")
    class CountProjectsByStatus {

        @Test
        @DisplayName("Retourne le nombre correct de projets")
        void shouldReturnCount() {
            when(projectRepository.countProjectsByStatus(ProjectStatus.IN_PROGRESS)).thenReturn(5L);

            long count = projectService.countProjectsByStatus(ProjectStatus.IN_PROGRESS);

            assertThat(count).isEqualTo(5L);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getActiveProjects / getInactiveProjects
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getActiveProjects() & getInactiveProjects()")
    class ActiveInactiveProjects {

        @Test
        @DisplayName("getActiveProjects retourne les projets actifs")
        void shouldReturnActiveProjects() {
            Project p = buildProject(1L, "Active", "PRJ001", ProjectStatus.IN_PROGRESS);
            when(projectRepository.findActiveProjects()).thenReturn(List.of(p));

            List<ProjectResponseDTO> result = projectService.getActiveProjects();

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getInactiveProjects retourne les projets inactifs")
        void shouldReturnInactiveProjects() {
            Project p = buildProject(1L, "Done", "PRJ002", ProjectStatus.COMPLETED);
            when(projectRepository.findInactiveProjects()).thenReturn(List.of(p));

            List<ProjectResponseDTO> result = projectService.getInactiveProjects();

            assertThat(result).hasSize(1);
        }
    }
}