package com.example.pfe.Service;

import com.example.pfe.Repository.ProjectRepository;
import com.example.pfe.Repository.TeamAssignmentRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.TeamAssignmentDTO;
import com.example.pfe.dto.TeamAssignmentResponseDTO;
import com.example.pfe.entities.Project;
import com.example.pfe.entities.TeamAssignment;
import com.example.pfe.entities.User;
import com.example.pfe.enums.ProjectStatus;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamAssignmentService - Tests Unitaires")
class TeamAssignmentServiceTest {

    @Mock private TeamAssignmentRepository teamAssignmentRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private TeamAssignmentService teamAssignmentService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private User buildUser(Long id, String first, String last, String email) {
        User u = new User();
        u.setId(id);
        u.setFirstName(first);
        u.setLastName(last);
        u.setEmail(email);
        return u;
    }

    private Project buildProject(Long id, String name, String code) {
        Project p = new Project();
        p.setId(id);
        p.setName(name);
        p.setCode(code);
        p.setStatus(ProjectStatus.PLANNED);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    private TeamAssignment buildAssignment(Integer id, Project project, User employee,
                                           User manager, boolean active) {
        return TeamAssignment.builder()
                .project(project)
                .employee(employee)
                .assigningManager(manager)
                .addedDate(LocalDate.now())
                .active(active)
                .build();
    }

    private TeamAssignmentDTO buildDTO(Long projectId, Long employeeId, Long managerId) {
        TeamAssignmentDTO dto = new TeamAssignmentDTO();
        dto.setProjectId(projectId);
        dto.setEmployeeId(employeeId);
        dto.setAssigningManagerId(managerId);
        return dto;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // assignEmployeeToProject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("assignEmployeeToProject()")
    class AssignEmployeeToProject {

        @Test
        @DisplayName("Assigne un employé avec succès")
        void shouldAssignSuccessfully() {
            Project project = buildProject(1L, "Alpha", "PRJ001");
            User employee = buildUser(10L, "Jane", "Doe", "jane@test.com");
            User manager  = buildUser(99L, "Bob",  "Mgr", "bob@test.com");

            TeamAssignment saved = buildAssignment(1, project, employee, manager, true);

            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(userRepository.findById(10L)).thenReturn(Optional.of(employee));
            when(userRepository.findById(99L)).thenReturn(Optional.of(manager));
            when(teamAssignmentRepository.existsByProjectAndEmployeeAndActiveTrue(project, employee))
                    .thenReturn(false);
            when(teamAssignmentRepository.save(any())).thenReturn(saved);

            TeamAssignmentResponseDTO result =
                    teamAssignmentService.assignEmployeeToProject(buildDTO(1L, 10L, 99L));

            assertThat(result).isNotNull();
            assertThat(result.getProjectId()).isEqualTo(1L);
            assertThat(result.getEmployeeId()).isEqualTo(10L);
            verify(teamAssignmentRepository).save(any(TeamAssignment.class));
        }

        @Test
        @DisplayName("Lève BusinessException si l'employé est déjà assigné")
        void shouldThrowWhenAlreadyAssigned() {
            Project project  = buildProject(1L, "Alpha", "PRJ001");
            User employee    = buildUser(10L, "Jane", "Doe", "jane@test.com");
            User manager     = buildUser(99L, "Bob",  "Mgr", "bob@test.com");

            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(userRepository.findById(10L)).thenReturn(Optional.of(employee));
            when(userRepository.findById(99L)).thenReturn(Optional.of(manager));
            when(teamAssignmentRepository.existsByProjectAndEmployeeAndActiveTrue(project, employee))
                    .thenReturn(true);

            assertThatThrownBy(() ->
                    teamAssignmentService.assignEmployeeToProject(buildDTO(1L, 10L, 99L)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already assigned");

            verify(teamAssignmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le projet n'existe pas")
        void shouldThrowWhenProjectNotFound() {
            when(projectRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    teamAssignmentService.assignEmployeeToProject(buildDTO(99L, 1L, 1L)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si l'employé n'existe pas")
        void shouldThrowWhenEmployeeNotFound() {
            Project project = buildProject(1L, "Alpha", "PRJ001");
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(userRepository.findById(10L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    teamAssignmentService.assignEmployeeToProject(buildDTO(1L, 10L, 99L)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("10");
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le manager n'existe pas")
        void shouldThrowWhenManagerNotFound() {
            Project project  = buildProject(1L, "Alpha", "PRJ001");
            User employee    = buildUser(10L, "Jane", "Doe", "jane@test.com");

            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(userRepository.findById(10L)).thenReturn(Optional.of(employee));
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    teamAssignmentService.assignEmployeeToProject(buildDTO(1L, 10L, 99L)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // removeEmployeeFromProject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("removeEmployeeFromProject()")
    class RemoveEmployeeFromProject {

        @Test
        @DisplayName("Soft-delete : marque l'assignation comme inactive")
        void shouldMarkAssignmentInactive() {
            Project project  = buildProject(1L, "Alpha", "PRJ001");
            User employee    = buildUser(10L, "Jane", "Doe", "jane@test.com");
            User manager     = buildUser(99L, "Bob",  "Mgr", "bob@test.com");
            TeamAssignment assignment = buildAssignment(5, project, employee, manager, true);

            when(teamAssignmentRepository.findById(5)).thenReturn(Optional.of(assignment));

            teamAssignmentService.removeEmployeeFromProject(5L);

            assertThat(assignment.getActive()).isFalse();
            verify(teamAssignmentRepository).save(assignment);
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si l'assignation n'existe pas")
        void shouldThrowWhenAssignmentNotFound() {
            when(teamAssignmentRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> teamAssignmentService.removeEmployeeFromProject(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getProjectTeam
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getProjectTeam()")
    class GetProjectTeam {

        @Test
        @DisplayName("Retourne uniquement les membres actifs")
        void shouldReturnOnlyActiveMembers() {
            Project project  = buildProject(1L, "Alpha", "PRJ001");
            User emp1        = buildUser(10L, "Jane", "A", "jane@test.com");
            User emp2        = buildUser(11L, "Bob",  "B", "bob@test.com");
            User mgr         = buildUser(99L, "Mgr",  "M", "m@test.com");

            TeamAssignment active   = buildAssignment(1, project, emp1, mgr, true);
            TeamAssignment inactive = buildAssignment(2, project, emp2, mgr, false);

            when(projectRepository.existsById(1L)).thenReturn(true);
            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of(active, inactive));

            List<TeamAssignmentResponseDTO> result = teamAssignmentService.getProjectTeam(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getEmployeeFirstName()).isEqualTo("Jane");
        }

        @Test
        @DisplayName("Retourne une liste vide si aucun membre actif")
        void shouldReturnEmptyWhenNoActiveMembers() {
            Project project = buildProject(1L, "Alpha", "PRJ001");
            User emp        = buildUser(10L, "Jane", "A", "jane@test.com");
            User mgr        = buildUser(99L, "Mgr",  "M", "m@test.com");

            when(projectRepository.existsById(1L)).thenReturn(true);
            when(teamAssignmentRepository.findByProjectId(1L))
                    .thenReturn(List.of(buildAssignment(1, project, emp, mgr, false)));

            List<TeamAssignmentResponseDTO> result = teamAssignmentService.getProjectTeam(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le projet n'existe pas")
        void shouldThrowWhenProjectNotFound() {
            when(projectRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> teamAssignmentService.getProjectTeam(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getEmployeeAssignments
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getEmployeeAssignments()")
    class GetEmployeeAssignments {

        @Test
        @DisplayName("Retourne les assignations actives d'un employé")
        void shouldReturnActiveAssignments() {
            Project p1  = buildProject(1L, "Alpha", "PRJ001");
            Project p2  = buildProject(2L, "Beta",  "PRJ002");
            User emp    = buildUser(10L, "Jane", "Doe", "jane@test.com");
            User mgr    = buildUser(99L, "Mgr",  "M",   "m@test.com");

            TeamAssignment a1 = buildAssignment(1, p1, emp, mgr, true);
            TeamAssignment a2 = buildAssignment(2, p2, emp, mgr, false);

            when(userRepository.existsById(10L)).thenReturn(true);
            when(teamAssignmentRepository.findByEmployeeId(10L)).thenReturn(List.of(a1, a2));

            List<TeamAssignmentResponseDTO> result =
                    teamAssignmentService.getEmployeeAssignments(10L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProjectId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si l'employé n'existe pas")
        void shouldThrowWhenEmployeeNotFound() {
            when(userRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> teamAssignmentService.getEmployeeAssignments(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getEmployeeProjectAssignment
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getEmployeeProjectAssignment()")
    class GetEmployeeProjectAssignment {

        @Test
        @DisplayName("Retourne l'assignation active si elle existe")
        void shouldReturnActiveAssignment() {
            Project project = buildProject(1L, "Alpha", "PRJ001");
            User emp        = buildUser(10L, "Jane", "Doe", "jane@test.com");
            User mgr        = buildUser(99L, "Mgr",  "M",   "m@test.com");

            TeamAssignment active = buildAssignment(1, project, emp, mgr, true);

            when(teamAssignmentRepository.findByProjectIdAndEmployeeId(1L, 10L))
                    .thenReturn(List.of(active));

            TeamAssignmentResponseDTO result =
                    teamAssignmentService.getEmployeeProjectAssignment(10L, 1L);

            assertThat(result).isNotNull();
            assertThat(result.getEmployeeId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("Retourne null si aucune assignation active")
        void shouldReturnNullWhenNoActiveAssignment() {
            Project project = buildProject(1L, "Alpha", "PRJ001");
            User emp        = buildUser(10L, "Jane", "Doe", "jane@test.com");
            User mgr        = buildUser(99L, "Mgr",  "M",   "m@test.com");

            TeamAssignment inactive = buildAssignment(1, project, emp, mgr, false);

            when(teamAssignmentRepository.findByProjectIdAndEmployeeId(1L, 10L))
                    .thenReturn(List.of(inactive));

            TeamAssignmentResponseDTO result =
                    teamAssignmentService.getEmployeeProjectAssignment(10L, 1L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Retourne null si aucune assignation trouvée")
        void shouldReturnNullWhenNoAssignmentFound() {
            when(teamAssignmentRepository.findByProjectIdAndEmployeeId(1L, 10L))
                    .thenReturn(List.of());

            TeamAssignmentResponseDTO result =
                    teamAssignmentService.getEmployeeProjectAssignment(10L, 1L);

            assertThat(result).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // isEmployeeAssignedToProject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("isEmployeeAssignedToProject()")
    class IsEmployeeAssignedToProject {

        @Test
        @DisplayName("Retourne true si l'employé a une assignation active")
        void shouldReturnTrueWhenActiveAssignmentExists() {
            Project project = buildProject(1L, "Alpha", "PRJ001");
            User emp        = buildUser(10L, "Jane", "Doe", "jane@test.com");
            User mgr        = buildUser(99L, "Mgr",  "M",   "m@test.com");

            TeamAssignment active = buildAssignment(1, project, emp, mgr, true);

            when(teamAssignmentRepository.findByProjectIdAndEmployeeId(1L, 10L))
                    .thenReturn(List.of(active));

            assertThat(teamAssignmentService.isEmployeeAssignedToProject(10L, 1L)).isTrue();
        }

        @Test
        @DisplayName("Retourne false si l'assignation est inactive")
        void shouldReturnFalseWhenOnlyInactive() {
            Project project  = buildProject(1L, "Alpha", "PRJ001");
            User emp         = buildUser(10L, "Jane", "Doe", "jane@test.com");
            User mgr         = buildUser(99L, "Mgr",  "M",   "m@test.com");

            TeamAssignment inactive = buildAssignment(1, project, emp, mgr, false);

            when(teamAssignmentRepository.findByProjectIdAndEmployeeId(1L, 10L))
                    .thenReturn(List.of(inactive));

            assertThat(teamAssignmentService.isEmployeeAssignedToProject(10L, 1L)).isFalse();
        }

        @Test
        @DisplayName("Retourne false si aucune assignation")
        void shouldReturnFalseWhenNoAssignment() {
            when(teamAssignmentRepository.findByProjectIdAndEmployeeId(1L, 10L))
                    .thenReturn(List.of());

            assertThat(teamAssignmentService.isEmployeeAssignedToProject(10L, 1L)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // countTeamMembersByProject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("countTeamMembersByProject()")
    class CountTeamMembersByProject {

        @Test
        @DisplayName("Compte uniquement les membres actifs")
        void shouldCountOnlyActiveMembers() {
            Project project = buildProject(1L, "Alpha", "PRJ001");
            User emp1       = buildUser(10L, "Jane", "A", "jane@test.com");
            User emp2       = buildUser(11L, "Bob",  "B", "bob@test.com");
            User mgr        = buildUser(99L, "Mgr",  "M", "m@test.com");

            TeamAssignment a1 = buildAssignment(1, project, emp1, mgr, true);
            TeamAssignment a2 = buildAssignment(2, project, emp2, mgr, true);
            TeamAssignment a3 = buildAssignment(3, project, emp1, mgr, false);

            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of(a1, a2, a3));

            long count = teamAssignmentService.countTeamMembersByProject(1L);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("Retourne 0 si aucun membre actif")
        void shouldReturnZeroWhenNoActiveMembers() {
            Project project = buildProject(1L, "Alpha", "PRJ001");
            User emp        = buildUser(10L, "Jane", "A", "jane@test.com");
            User mgr        = buildUser(99L, "Mgr",  "M", "m@test.com");

            when(teamAssignmentRepository.findByProjectId(1L))
                    .thenReturn(List.of(buildAssignment(1, project, emp, mgr, false)));

            assertThat(teamAssignmentService.countTeamMembersByProject(1L)).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getAllProjectAssignments
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getAllProjectAssignments()")
    class GetAllProjectAssignments {

        @Test
        @DisplayName("Retourne toutes les assignations actives et inactives")
        void shouldReturnAllAssignments() {
            Project project = buildProject(1L, "Alpha", "PRJ001");
            User emp        = buildUser(10L, "Jane", "A", "jane@test.com");
            User mgr        = buildUser(99L, "Mgr",  "M", "m@test.com");

            TeamAssignment a1 = buildAssignment(1, project, emp, mgr, true);
            TeamAssignment a2 = buildAssignment(2, project, emp, mgr, false);

            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of(a1, a2));

            List<TeamAssignmentResponseDTO> result =
                    teamAssignmentService.getAllProjectAssignments(1L);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Retourne une liste vide si aucune assignation")
        void shouldReturnEmptyWhenNoAssignments() {
            when(teamAssignmentRepository.findByProjectId(1L)).thenReturn(List.of());

            List<TeamAssignmentResponseDTO> result =
                    teamAssignmentService.getAllProjectAssignments(1L);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // reactivateAssignment
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("reactivateAssignment()")
    class ReactivateAssignment {

        @Test
        @DisplayName("Réactive une assignation inactive avec succès")
        void shouldReactivateSuccessfully() {
            Project project  = buildProject(1L, "Alpha", "PRJ001");
            User emp         = buildUser(10L, "Jane", "Doe", "jane@test.com");
            User mgr         = buildUser(99L, "Mgr",  "M",   "m@test.com");

            TeamAssignment assignment = buildAssignment(5, project, emp, mgr, false);

            when(teamAssignmentRepository.findById(5)).thenReturn(Optional.of(assignment));
            when(teamAssignmentRepository.findByProjectIdAndEmployeeId(1L, 10L))
                    .thenReturn(List.of(assignment)); // only this one, inactive
            when(teamAssignmentRepository.save(any())).thenReturn(assignment);

            TeamAssignmentResponseDTO result =
                    teamAssignmentService.reactivateAssignment(5L);

            assertThat(assignment.getActive()).isTrue();
            assertThat(result).isNotNull();
            verify(teamAssignmentRepository).save(assignment);
        }

        @Test
        @DisplayName("Lève BusinessException si l'assignation est déjà active")
        void shouldThrowWhenAlreadyActive() {
            Project project  = buildProject(1L, "Alpha", "PRJ001");
            User emp         = buildUser(10L, "Jane", "Doe", "jane@test.com");
            User mgr         = buildUser(99L, "Mgr",  "M",   "m@test.com");

            TeamAssignment assignment = buildAssignment(5, project, emp, mgr, true);

            when(teamAssignmentRepository.findById(5)).thenReturn(Optional.of(assignment));

            assertThatThrownBy(() -> teamAssignmentService.reactivateAssignment(5L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already active");
        }

        @Test
        @DisplayName("Lève BusinessException si l'employé a déjà une assignation active sur ce projet")
        void shouldThrowWhenAnotherActiveAssignmentExists() {
            Project project   = buildProject(1L, "Alpha", "PRJ001");
            User emp          = buildUser(10L, "Jane", "Doe", "jane@test.com");
            User mgr          = buildUser(99L, "Mgr",  "M",   "m@test.com");

            // L'assignation à réactiver (inactive, id=5)
            TeamAssignment toReactivate = buildAssignment(5, project, emp, mgr, false);
            toReactivate.setId(5);
            // Une autre assignation active (id=6) sur le même projet
            TeamAssignment otherActive  = buildAssignment(6, project, emp, mgr, true);
            otherActive.setId(6);

            when(teamAssignmentRepository.findById(5)).thenReturn(Optional.of(toReactivate));
            when(teamAssignmentRepository.findByProjectIdAndEmployeeId(1L, 10L))
                    .thenReturn(List.of(toReactivate, otherActive));

            assertThatThrownBy(() -> teamAssignmentService.reactivateAssignment(5L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already actively assigned");
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si l'assignation n'existe pas")
        void shouldThrowWhenAssignmentNotFound() {
            when(teamAssignmentRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> teamAssignmentService.reactivateAssignment(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // assignMultipleEmployeesToProject
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("assignMultipleEmployeesToProject()")
    class AssignMultipleEmployees {

        @Test
        @DisplayName("Assigne plusieurs employés avec succès")
        void shouldAssignAllSuccessfully() {
            Project project  = buildProject(1L, "Alpha", "PRJ001");
            User emp1        = buildUser(10L, "Jane", "A", "jane@test.com");
            User emp2        = buildUser(11L, "Bob",  "B", "bob@test.com");
            User mgr         = buildUser(99L, "Mgr",  "M", "m@test.com");

            TeamAssignment saved1 = buildAssignment(1, project, emp1, mgr, true);
            TeamAssignment saved2 = buildAssignment(2, project, emp2, mgr, true);

            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(userRepository.findById(99L)).thenReturn(Optional.of(mgr));

            // Pour chaque appel interne à assignEmployeeToProject
            when(userRepository.findById(10L)).thenReturn(Optional.of(emp1));
            when(userRepository.findById(11L)).thenReturn(Optional.of(emp2));
            when(teamAssignmentRepository
                    .existsByProjectAndEmployeeAndActiveTrue(project, emp1)).thenReturn(false);
            when(teamAssignmentRepository
                    .existsByProjectAndEmployeeAndActiveTrue(project, emp2)).thenReturn(false);
            when(teamAssignmentRepository.save(any()))
                    .thenReturn(saved1)
                    .thenReturn(saved2);

            List<TeamAssignmentResponseDTO> results =
                    teamAssignmentService.assignMultipleEmployeesToProject(
                            1L, List.of(10L, 11L), 99L);

            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("Ignore silencieusement les employés déjà assignés et continue")
        void shouldSkipAlreadyAssignedEmployees() {
            Project project  = buildProject(1L, "Alpha", "PRJ001");
            User emp1        = buildUser(10L, "Jane", "A", "jane@test.com");
            User emp2        = buildUser(11L, "Bob",  "B", "bob@test.com");
            User mgr         = buildUser(99L, "Mgr",  "M", "m@test.com");

            TeamAssignment saved2 = buildAssignment(2, project, emp2, mgr, true);

            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(userRepository.findById(99L)).thenReturn(Optional.of(mgr));

            when(userRepository.findById(10L)).thenReturn(Optional.of(emp1));
            when(userRepository.findById(11L)).thenReturn(Optional.of(emp2));
            when(teamAssignmentRepository
                    .existsByProjectAndEmployeeAndActiveTrue(project, emp1)).thenReturn(true); // déjà assigné
            when(teamAssignmentRepository
                    .existsByProjectAndEmployeeAndActiveTrue(project, emp2)).thenReturn(false);
            when(teamAssignmentRepository.save(any())).thenReturn(saved2);

            List<TeamAssignmentResponseDTO> results =
                    teamAssignmentService.assignMultipleEmployeesToProject(
                            1L, List.of(10L, 11L), 99L);

            // emp1 échoue silencieusement, emp2 réussit
            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le projet n'existe pas")
        void shouldThrowWhenProjectNotFound() {
            when(projectRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    teamAssignmentService.assignMultipleEmployeesToProject(
                            99L, List.of(1L), 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("Lève ResourceNotFoundException si le manager n'existe pas")
        void shouldThrowWhenManagerNotFound() {
            Project project = buildProject(1L, "Alpha", "PRJ001");
            when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    teamAssignmentService.assignMultipleEmployeesToProject(
                            1L, List.of(10L), 99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }
}