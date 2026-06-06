package com.example.pfe.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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
    private int    leaveDays;

    private double totalWorkedHours;
    private double totalOvertimeHours;

    /** True if the employee has already logged in today (first login = check-in). */
    private boolean checkedInToday;

    /**
     * Earliest date attendance is meaningful for this user:
     * MAX(hireDate, first attendance record). Days before this should
     * not be rendered as "absent" on the client.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate accountStartDate;

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
        /** One of: PRESENT | LATE | ABSENT | HALF_DAY | EARLY_DEPARTURE | LEAVE */
        private String status;
    }
}