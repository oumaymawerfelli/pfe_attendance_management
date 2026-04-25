package com.example.pfe.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Stores every tunable attendance threshold as a named key → numeric value row.
 *
 * Keys (use the static constants below to avoid magic strings):
 *  - VALID_CHECKIN_START   : earliest hour a login counts as check-in  (default 5)
 *  - LATE_HOUR             : hour boundary for LATE status              (default 9)
 *  - LATE_MINUTE           : minute boundary for LATE status            (default 0)
 *  - EARLY_DEPARTURE_HOUR  : checkout before this hour → EARLY_DEPARTURE (default 17)
 *  - HALF_DAY_THRESHOLD    : hours worked < this → HALF_DAY             (default 4)
 *  - STANDARD_HOURS        : full work day in hours                     (default 8)
 */
@Entity
@Table(name = "attendance_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceConfig {

    // ── Static key constants ────────────────────────────────────────────────────
    public static final String KEY_VALID_CHECKIN_START  = "VALID_CHECKIN_START";
    public static final String KEY_LATE_HOUR            = "LATE_HOUR";
    public static final String KEY_LATE_MINUTE          = "LATE_MINUTE";
    public static final String KEY_EARLY_DEPARTURE_HOUR = "EARLY_DEPARTURE_HOUR";
    public static final String KEY_HALF_DAY_THRESHOLD   = "HALF_DAY_THRESHOLD";
    public static final String KEY_STANDARD_HOURS       = "STANDARD_HOURS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique machine-readable key (e.g. "LATE_HOUR"). */
    @Column(nullable = false, unique = true, length = 60)
    private String configKey;

    /**
     * Numeric value stored as Double to cover both integer thresholds
     * (hours, minutes) and fractional ones (standard hours).
     */
    @Column(nullable = false)
    private Double configValue;

    /** Human-readable explanation shown in the HR settings UI. */
    @Column(length = 255)
    private String description;

    /** Who last changed this value (username or user-id). */
    @Column(length = 100)
    private String lastModifiedBy;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt;
}