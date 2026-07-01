package com.example.pfe.entities;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rag_ingestion_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagIngestionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_slug", nullable = false)
    private String fileSlug;  // "reglement_interne"

    @Column(name = "file_name")
    private String fileName;  // "reglement_interne.pdf"

    @Column(name = "file_hash", nullable = false)
    private String fileHash;  // SHA-256 du PDF

    @Column(name = "previous_hash")
    private String previousHash;  // Hash version précédente

    @Column(name = "status", nullable = false)
    private String status;  // "SUCCESS", "FAILED", "NO_CHANGE"

    @Column(name = "message")
    private String message;  // détail si erreur

    @Column(name = "ingested_at", nullable = false)
    private LocalDateTime ingestedAt;

    @Column(name = "chunks_count")
    private Integer chunksCount;  // combien de chunks créés
}
