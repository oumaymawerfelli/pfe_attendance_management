package com.example.pfe.enums;

public enum LeaveStatus {
    PENDING,    // Just submitted — waiting for HR/Admin decision
    APPROVED,   // HR/Admin approved → balance auto-deducted
    REJECTED    // HR/Admin rejected → balance untouched
}