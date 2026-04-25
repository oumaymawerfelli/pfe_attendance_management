package com.example.pfe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSummaryDTO {

    private int    totalWorkingDays;
    private int    presentDays;
    private int    lateDays;
    private int    absentDays;
    private int    halfDays;
    private int    leaveDays;          // ← NEW — approved leave days in the period

    private double totalWorkedHours;
    private double totalOvertimeHours;

    /** True if the employee has already logged in today (first login = check-in). */
    private boolean checkedInToday;
    // checkedOutToday removed — checkout is automatic on last logout, no UI flag needed

    private List<DailyHoursDTO> dailyHours;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyHoursDTO {
        /** Day-of-month as a string, e.g. "5" or "27" */
        private String day;
        private double workedHours;
        private double overtimeHours;
        /**
         * One of: PRESENT | LATE | ABSENT | HALF_DAY | EARLY_DEPARTURE | LEAVE
         */
        private String status;
    }
}