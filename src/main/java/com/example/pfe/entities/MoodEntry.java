package com.example.pfe.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "mood_entry",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "date"}) // 1 per day
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MoodEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // userId stored only for dedup — NEVER returned to admin
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "department", nullable = false)
    private String department;   // stored at submission time for dept breakdown

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int score;           // 1–5

    @Column(columnDefinition = "TEXT")
    private String note;         // optional, anonymous

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;
}