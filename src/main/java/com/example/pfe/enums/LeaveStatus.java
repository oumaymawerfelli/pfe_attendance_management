package com.example.pfe.enums;

public enum LeaveStatus {
    DRAFT,            // saved but not submitted
    PENDING,          // submitted, awaiting decision
    APPROVED,         // approved by the authorised approver
    REJECTED          // rejected with a reason
}