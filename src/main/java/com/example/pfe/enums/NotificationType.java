package com.example.pfe.enums;

public enum NotificationType {

    // ── Auth ──────────────────────────────────────────────────────────────────
    WELCOME,                  // First login after account activation

    // ── Attendance ────────────────────────────────────────────────────────────
    LATE_ARRIVAL,             // Checked in after the grace period
    MISSED_CHECKOUT,          // No checkout recorded for yesterday
    EARLY_DEPARTURE,          // Checked out before end-of-day threshold

    // ── Leave ─────────────────────────────────────────────────────────────────
    LEAVE_APPROVED,           // Leave request approved
    LEAVE_REJECTED,           // Leave request rejected

    // ── Projects / Team ───────────────────────────────────────────────────────
    PROJECT_ASSIGNED,         // Employee added to a project
    PM_ASSIGNED,              // Employee assigned to a project manager
    PROJECT_UPDATED,          // A project the employee works on was updated
}