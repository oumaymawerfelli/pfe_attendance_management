package com.example.pfe.dto;

import lombok.*;
import java.util.List;

/**
 * Extends LeaveBalanceDTO with the approval workflow chain.
 * Returned by GET /api/leaves/summary — powers the Request Leave page on load.
 * LeaveBalanceDTO itself is unchanged so /my/balance still works as before.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeaveSummaryDTO extends LeaveBalanceDTO {

    /**
     * Ordered approval steps for this employee.
     * Maps to WorkflowStep enum values on the frontend:
     * ["TEAM_LEAD", "HR_MANAGER", "GENERAL_MANAGER"]
     */
    private List<String> workflow;
}