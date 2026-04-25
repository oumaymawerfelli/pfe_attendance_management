
package com.example.pfe.entities;

import jakarta.persistence.*;
import lombok.*;

    @Entity
    @Table(
            name = "leave_balance",
            uniqueConstraints = {
                    // One balance record per user per year
                    @UniqueConstraint(columnNames = {"user_id", "year"})
            }
    )
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public class LeaveBalance {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        // ─── Relation ────────────────────────────────────────────
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id", nullable = false)
        private User user;

        @Column(nullable = false)
        private Integer year;   // e.g. 2025

        // ─── Annual leave ─────────────────────────────────────────
        @Builder.Default
        private Double annualTotal = 22.0;   // Default: 22 days

        @Builder.Default
        private Double annualTaken = 0.0;    // Days used so far

        // ─── Sick leave ───────────────────────────────────────────
        @Builder.Default
        private Double sickTotal = 15.0;     // Default: 15 days

        @Builder.Default
        private Double sickTaken = 0.0;

        // ─── Unpaid leave ─────────────────────────────────────────
        @Builder.Default
        private Double unpaidTotal = 0.0;    // No cap — tracked only

        @Builder.Default
        private Double unpaidTaken = 0.0;
    }

