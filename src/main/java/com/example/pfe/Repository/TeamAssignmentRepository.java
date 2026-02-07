package com.example.pfe.Repository;

import com.example.pfe.entities.TeamAssignment;
import com.example.pfe.entities.Project;
import com.example.pfe.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamAssignmentRepository extends JpaRepository<TeamAssignment, Long> {

    List<TeamAssignment> findByProjectAndActiveTrue(Project project);

    List<TeamAssignment> findByEmployeeAndActiveTrue(User employee);

    List<TeamAssignment> findByAssigningManagerAndActiveTrue(User manager);

    boolean existsByProjectAndEmployee(Project project, User employee);

    // Ajoutez cette méthode
    long countByProject(Project project);

    // Méthode pour trouver par projet
    @Query("SELECT ta FROM TeamAssignment ta WHERE ta.project.id = :projectId AND ta.active = true")
    List<TeamAssignment> findByProjectId(@Param("projectId") Long projectId);
}