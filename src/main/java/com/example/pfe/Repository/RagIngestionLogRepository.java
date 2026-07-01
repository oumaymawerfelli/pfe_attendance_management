package com.example.pfe.Repository;

import com.example.pfe.entities.RagIngestionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RagIngestionLogRepository extends JpaRepository<RagIngestionLog, Long> {

    Optional<RagIngestionLog> findFirstByFileSlugOrderByIngestedAtDesc(String fileSlug);

    // 👇 AJOUTE CETTE LIGNE
    long countByStatus(String status);
}