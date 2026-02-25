package com.example.pfe.Repository;

import com.example.pfe.entities.Project;
import com.example.pfe.entities.TeamAssignment;
import com.example.pfe.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamAssignmentRepository extends JpaRepository<TeamAssignment, Integer> {

    // Find by project
    List<TeamAssignment> findByProjectId(Long projectId);

    // Find by employee
    List<TeamAssignment> findByEmployeeId(Long employeeId);

    // Find by project and employee
    List<TeamAssignment> findByProjectIdAndEmployeeId(Long projectId, Long employeeId);

    // Check if exists with active true
    boolean existsByProjectAndEmployeeAndActiveTrue(Project project, User employee);

    // Find by assigning manager
    List<TeamAssignment> findByAssigningManagerId(Long managerId);
}