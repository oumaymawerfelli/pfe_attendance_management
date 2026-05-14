package com.example.pfe.entities;

import com.example.pfe.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_status_history")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ProjectStatus fromStatus;      // null on first creation

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ProjectStatus toStatus;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;              // pulled from SecurityContext

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "reason", length = 500)
    private String reason;
}