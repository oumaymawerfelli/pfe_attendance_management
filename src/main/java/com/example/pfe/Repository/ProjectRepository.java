package com.example.pfe.Repository;

import com.example.pfe.entities.Project;
import com.example.pfe.entities.User;
import com.example.pfe.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByCode(String code);
    boolean existsByName(String name);
    boolean existsByCode(String code);

    List<Project> findByProjectManager(User projectManager);
    List<Project> findByStatus(ProjectStatus status);

    long countByProjectManager(User projectManager);
    long countByStatus(ProjectStatus status);

    @Query("SELECT p FROM Project p WHERE " +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Project> searchProjects(@Param("keyword") String keyword);

    // CORRECTION : Utilisez l'énumération directement
    @Query("SELECT p FROM Project p WHERE p.status IN :statuses")
    List<Project> findByStatuses(@Param("statuses") List<ProjectStatus> statuses);

    // Méthode pour les projets actifs (utilise findByStatuses)
    default List<Project> findActiveProjects() {
        List<ProjectStatus> activeStatuses = List.of(
                ProjectStatus.PLANNED,
                ProjectStatus.IN_PROGRESS,
                ProjectStatus.ON_HOLD
        );
        return findByStatuses(activeStatuses);
    }
    boolean existsByProjectManagerAndIdNot(User projectManager, Long id);
    // Méthode pour les projets inactifs
    default List<Project> findInactiveProjects() {
        List<ProjectStatus> inactiveStatuses = List.of(
                ProjectStatus.COMPLETED,
                ProjectStatus.CANCELLED
        );
        return findByStatuses(inactiveStatuses);
    }

    // Compte par statut
    long countProjectsByStatus(ProjectStatus status);

    // Autres méthodes utiles
    @Query("SELECT p FROM Project p WHERE p.projectManager.id = :managerId")
    Page<Project> findByProjectManagerId(@Param("managerId") Long managerId, Pageable pageable);

    @Query("SELECT p FROM Project p WHERE p.projectManager IS NULL")
    List<Project> findUnassignedProjects();

    @Query("SELECT p FROM Project p WHERE p.endDate < CURRENT_DATE AND p.status NOT IN :excludedStatuses")
    List<Project> findOverdueProjects(@Param("excludedStatuses") List<ProjectStatus> excludedStatuses);

    // Méthode par défaut pour les projets en retard
    default List<Project> findOverdueProjects() {
        List<ProjectStatus> excluded = List.of(ProjectStatus.COMPLETED, ProjectStatus.CANCELLED);
        return findOverdueProjects(excluded);
    }

    @Query("SELECT p FROM Project p WHERE p.status = :status AND p.startDate > CURRENT_DATE")
    List<Project> findUpcomingProjects(@Param("status") ProjectStatus status);

    // Méthode par défaut pour les projets à venir
    default List<Project> findUpcomingProjects() {
        return findUpcomingProjects(ProjectStatus.PLANNED);
    }
}