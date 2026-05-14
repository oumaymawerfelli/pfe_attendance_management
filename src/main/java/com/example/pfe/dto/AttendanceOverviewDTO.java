package com.example.pfe.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AttendanceOverviewDTO {
    // ── Status counts ──────────────────────────────────────
    private long   onTime;
    private long   late;
    private long   absent;
    private long   onLeave;
    private long   remote;

    // ── Trends ────────────────────────────────────────────
    private double onTimeTrend;
    private double lateTrend;
    private double absentTrend;
    private double onLeaveTrend;
    private double remoteTrend;

    // ── Attendance rate ───────────────────────────────────
    private double       attendanceRate;
    private double       attendanceRateTrend;

    // ── Weekly chart data (last 7 days) ───────────────────
    private List<String> weekLabels;
    private List<Double> weeklyRates;

    // ── Sparklines (last 7 days) ──────────────────────────
    private List<Long>   lateSparkline;
    private List<Long>   absentSparkline;
    private List<Long>   onLeaveSparkline;
    private List<Long>   remoteSparkline;
}