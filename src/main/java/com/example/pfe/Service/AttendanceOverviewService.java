package com.example.pfe.Service;

import com.example.pfe.Repository.AttendanceRepository;
import com.example.pfe.Repository.LeaveRequestRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.AttendanceOverviewDTO;
import com.example.pfe.enums.Department;
import com.example.pfe.enums.LeaveStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AttendanceOverviewService {

    private static final int LATE_THRESHOLD_MINUTES = 9 * 60;

    private final UserRepository         userRepository;
    private final AttendanceRepository   attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public AttendanceOverviewDTO getOverview(String period,
                                             String department,
                                             String dateStr) {
        LocalDate[] current  = getRange(period, dateStr, 0);
        LocalDate[] previous = getRange(period, dateStr, 1);

        Department dept = parseDept(department);

        Counts cur  = compute(current[0],  current[1],  dept);
        Counts prev = compute(previous[0], previous[1], dept);

        long total = totalActive(dept);

        double rate     = rate(cur.onTime() + cur.late(), total);
        double prevRate = rate(prev.onTime() + prev.late(), total);
        double rateTrend = trend(prevRate, rate);

        // ── Weekly rates & sparklines (last 7 days) ────────
        List<String> labels        = new ArrayList<>();
        List<Double> weeklyRates   = new ArrayList<>();
        List<Long>   lateSparkline = new ArrayList<>();
        List<Long>   absSparkline  = new ArrayList<>();
        List<Long>   lvSparkline   = new ArrayList<>();
        List<Long>   remSparkline  = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            Counts c      = compute(day, day, dept);

            labels.add(day.getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            weeklyRates.add(rate(c.onTime() + c.late(), total));
            lateSparkline.add(c.late());
            absSparkline.add(c.absent());
            lvSparkline.add(c.onLeave());
            remSparkline.add(c.remote());
        }

        return AttendanceOverviewDTO.builder()
                .onTime(cur.onTime())
                .late(cur.late())
                .absent(cur.absent())
                .onLeave(cur.onLeave())
                .remote(cur.remote())
                .onTimeTrend(trend(prev.onTime(),    cur.onTime()))
                .lateTrend(trend(prev.late(),        cur.late()))
                .absentTrend(trend(prev.absent(),    cur.absent()))
                .onLeaveTrend(trend(prev.onLeave(),  cur.onLeave()))
                .remoteTrend(trend(prev.remote(),    cur.remote()))
                .attendanceRate(rate)
                .attendanceRateTrend(rateTrend)
                .weekLabels(labels)
                .weeklyRates(weeklyRates)
                .lateSparkline(lateSparkline)
                .absentSparkline(absSparkline)
                .onLeaveSparkline(lvSparkline)
                .remoteSparkline(remSparkline)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private double rate(long present, long total) {
        if (total == 0) return 0.0;
        return Math.round((present * 100.0 / total) * 10.0) / 10.0;
    }

    private long totalActive(Department dept) {
        return dept == null
                ? userRepository.countByActiveTrue()
                : userRepository.countByActiveTrueAndDepartment(dept);
    }

    private Department parseDept(String s) {
        if (s == null || s.isBlank() || "ALL".equalsIgnoreCase(s)) return null;
        try { return Department.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private LocalDate[] getRange(String period, String dateStr, int offset) {
        if ("specific".equalsIgnoreCase(period) && dateStr != null) {
            LocalDate d = LocalDate.parse(dateStr).minusDays(offset);
            return new LocalDate[]{ d, d };
        }
        LocalDate today = LocalDate.now();
        return switch (period.toLowerCase()) {
            case "week"  -> new LocalDate[]{
                    today.minusWeeks(offset).with(DayOfWeek.MONDAY),
                    today.minusWeeks(offset).with(DayOfWeek.SUNDAY)};
            case "month" -> new LocalDate[]{
                    today.minusMonths(offset).withDayOfMonth(1),
                    today.minusMonths(offset).withDayOfMonth(
                            today.minusMonths(offset).lengthOfMonth())};
            case "year"  -> new LocalDate[]{
                    today.minusYears(offset).withDayOfYear(1),
                    today.minusYears(offset).withDayOfYear(
                            today.minusYears(offset).lengthOfYear())};
            default      -> new LocalDate[]{
                    today.minusDays(offset),
                    today.minusDays(offset)};
        };
    }

    private Counts compute(LocalDate from, LocalDate to, Department dept) {
        long onTime  = attendanceRepository.countOnTime(from, to, LATE_THRESHOLD_MINUTES, dept);
        long late    = attendanceRepository.countLate(from, to, LATE_THRESHOLD_MINUTES, dept);
        long onLeave = leaveRequestRepository
                .countByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        LeaveStatus.APPROVED, to, from);
        long total   = totalActive(dept);
        long absent  = Math.max(0, total - (onTime + late) - onLeave);
        return new Counts(onTime, late, absent, onLeave, 0L);
    }

    private double trend(double previous, double current) {
        if (previous == 0) return current > 0 ? 100.0 : 0.0;
        double raw = ((current - previous) / previous) * 100.0;
        return Math.round(raw * 10.0) / 10.0;
    }

    private record Counts(long onTime, long late, long absent,
                          long onLeave, long remote) {}
}