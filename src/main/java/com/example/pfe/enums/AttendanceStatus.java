package com.example.pfe.enums;

public enum AttendanceStatus {
    PRESENT,
    LATE,
    ABSENT,
    HALF_DAY,
    EARLY_DEPARTURE,  // worked less than 4 hours
    ON_LEAVE          // employee has an approved leave request
}