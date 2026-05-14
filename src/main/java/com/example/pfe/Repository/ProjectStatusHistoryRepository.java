package com.example.pfe.Repository;


import com.example.pfe.entities.ProjectStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectStatusHistoryRepository
        extends JpaRepository<ProjectStatusHistory, Long> {

    List<ProjectStatusHistory> findByProjectIdOrderByChangedAtDesc(Long projectId);
}