package com.example.pfe.entities;

import com.example.pfe.enums.LeaveStatus;
import com.example.pfe.enums.LeaveType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_request")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Who submitted ───────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ─── Leave details ────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private Double daysCount;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String reason;

    // ─── Status & approval ───────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private String rejectionReason;

    // ─── Timestamps ──────────────────────────────────────────
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime decidedAt;

    // ─── Authorization document ───────────────────────────────
    /**
     * Relative path to the generated PDF on disk.
     * e.g. "leave-docs/leave_42_John_Doe.pdf"
     * Null until the leave is approved and the document is generated.
     */
    @Column(name = "document_path")
    private String documentPath;
}