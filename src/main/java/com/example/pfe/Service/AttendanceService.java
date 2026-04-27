package com.example.pfe.Service;

import com.example.pfe.Repository.AttendanceRepository;
import com.example.pfe.Repository.LeaveRequestRepository;
import com.example.pfe.Repository.TeamAssignmentRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.AttendanceFilterDTO;
import com.example.pfe.dto.AttendanceResponseDTO;
import com.example.pfe.dto.AttendanceSummaryDTO;
import com.example.pfe.entities.Attendance;
import com.example.pfe.entities.AttendanceConfig;
import com.example.pfe.entities.LeaveRequest;
import com.example.pfe.entities.User;
import com.example.pfe.enums.AttendanceStatus;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.AttendanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private final AttendanceRepository     attendanceRepository;
    private final LeaveRequestRepository   leaveRequestRepository;
    private final UserRepository           userRepository;
    private final TeamAssignmentRepository teamAssignmentRepository;
    private final AttendanceMapper         attendanceMapper;
    private final AttendanceConfigService  configService;
    private final NotificationService notificationService;


    // ── Check-in ──────────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkIn(Long userId) {
        LocalDateTime now   = LocalDateTime.now();
        LocalDate     today = LocalDate.now();

        int validCheckinStart = configService.getInt(AttendanceConfig.KEY_VALID_CHECKIN_START);
        if (now.getHour() < validCheckinStart) {
            log.info("User {} logged in before {:02d}:00 — no check-in recorded", userId, validCheckinStart);
            return;
        }
        if (attendanceRepository.existsByUserIdAndDate(userId, today)) {
            log.info("User {} already checked in today — skipping", userId);
            return;
        }

        User user = getUserById(userId);
        AttendanceStatus status = computeStatus(now, userId); // Modified to pass userId
        Attendance attendance = Attendance.builder()
                .user(user).date(today).checkIn(now)
                .status(status).overtimeHours(0.0)
                .build();

        attendanceRepository.save(attendance);
        log.info("Check-in recorded for user {} at {} — status: {}", userId, now, attendance.getStatus());
    }

    // ── Check-out ─────────────────────────────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkOutOnLogout(Long userId, String notes) {
        attendanceRepository.findByUserIdAndDate(userId, LocalDate.now())
                .filter(a -> a.getCheckIn() != null)
                .ifPresent(a -> {
                    a.setCheckOut(LocalDateTime.now());
                    if (notes != null && !notes.isBlank()) a.setNotes(notes);
                    computeDuration(a, userId); // Modified to pass userId
                    attendanceRepository.save(a);
                    log.info("Auto checkout on logout for user {} — status: {}, worked: {}h",
                            userId, a.getStatus(), a.getWorkDuration());

                    // Add early departure notification
                    if (a.getStatus() == AttendanceStatus.EARLY_DEPARTURE) {
                        String checkoutTime = a.getCheckOut().toLocalTime()
                                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                        notificationService.notifyEarlyDeparture(userId, checkoutTime);
                    }
                });
    }

    // ── Employee — own history ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttendanceResponseDTO> getMyAttendance(Long userId, AttendanceFilterDTO filter) {
        int year = (filter.getYear() != null) ? filter.getYear() : LocalDate.now().getYear();
        List<Attendance> records = (filter.getMonth() != null)
                ? attendanceRepository.findByUserIdAndMonthAndYear(userId, filter.getMonth(), year)
                : attendanceRepository.findByUserIdAndYear(userId, year);
        return records.stream().map(attendanceMapper::toResponseDTO).collect(Collectors.toList());
    }

    // ── Employee — single day detail ──────────────────────────────────────────

    /**
     * Returns the attendance record for a specific user on a specific date.
     * Throws ResourceNotFoundException if no record exists (absent / weekend / future).
     */
    @Transactional(readOnly = true)
    public AttendanceResponseDTO getMyDayRecord(Long userId, LocalDate date) {
        return attendanceRepository.findByUserIdAndDate(userId, date)
                .map(attendanceMapper::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No attendance record found for date: " + date));
    }

    // ── Employee — dashboard summary ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public AttendanceSummaryDTO getMySummary(Long userId, AttendanceFilterDTO filter) {
        int month = (filter.getMonth() != null) ? filter.getMonth() : LocalDate.now().getMonthValue();
        int year  = (filter.getYear()  != null) ? filter.getYear()  : LocalDate.now().getYear();

        Set<LocalDate> leaveDaySet   = buildLeaveDaySet(userId, month, year);
        int            leaveDayCount = leaveDaySet.size();

        int presentDays = attendanceRepository.countByUserIdAndStatusAndMonthAndYear(userId, AttendanceStatus.PRESENT, month, year);
        int lateDays    = attendanceRepository.countByUserIdAndStatusAndMonthAndYear(userId, AttendanceStatus.LATE,    month, year);
        int halfDays    = attendanceRepository.countByUserIdAndStatusAndMonthAndYear(userId, AttendanceStatus.HALF_DAY, month, year);
        int absentDays  = computeAbsentDays(month, year, presentDays, lateDays, halfDays, leaveDayCount);

        double totalWorkedHours   = attendanceRepository.sumWorkDurationByUserIdAndMonthAndYear(userId, month, year);
        double totalOvertimeHours = attendanceRepository.sumOvertimeByUserIdAndMonthAndYear(userId, month, year);

        Map<LocalDate, AttendanceSummaryDTO.DailyHoursDTO> dailyMap =
                attendanceRepository.findDailyHoursForChart(userId, month, year).stream()
                        .collect(Collectors.toMap(
                                Attendance::getDate,
                                a -> AttendanceSummaryDTO.DailyHoursDTO.builder()
                                        .day(String.valueOf(a.getDate().getDayOfMonth()))
                                        .workedHours(a.getWorkDuration()    != null ? a.getWorkDuration()    : 0.0)
                                        .overtimeHours(a.getOvertimeHours() != null ? a.getOvertimeHours()   : 0.0)
                                        .status(a.getStatus() != null ? a.getStatus().name() : "ABSENT")
                                        .build()));

        leaveDaySet.forEach(d -> dailyMap.put(d, AttendanceSummaryDTO.DailyHoursDTO.builder()
                .day(String.valueOf(d.getDayOfMonth())).workedHours(0.0).overtimeHours(0.0).status("LEAVE").build()));

        return AttendanceSummaryDTO.builder()
                .totalWorkingDays(presentDays + lateDays + halfDays)
                .presentDays(presentDays).lateDays(lateDays)
                .absentDays(absentDays).halfDays(halfDays).leaveDays(leaveDayCount)
                .totalWorkedHours(totalWorkedHours).totalOvertimeHours(totalOvertimeHours)
                .dailyHours(new ArrayList<>(dailyMap.values()))
                .checkedInToday(attendanceRepository.existsByUserIdAndDate(userId, LocalDate.now()))
                .build();
    }

    // ── GM / Admin — all employees ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttendanceResponseDTO> getAllAttendance(AttendanceFilterDTO filter) {
        int month = (filter.getMonth() != null) ? filter.getMonth() : LocalDate.now().getMonthValue();
        int year  = (filter.getYear()  != null) ? filter.getYear()  : LocalDate.now().getYear();
        return attendanceRepository.findAllByMonthAndYear(month, year)
                .stream().map(attendanceMapper::toResponseDTO).collect(Collectors.toList());
    }

    // ── Project Manager — team only ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttendanceResponseDTO> getTeamAttendance(Long pmId, AttendanceFilterDTO filter) {
        int month = (filter.getMonth() != null) ? filter.getMonth() : LocalDate.now().getMonthValue();
        int year  = (filter.getYear()  != null) ? filter.getYear()  : LocalDate.now().getYear();

        Set<Long> teamIds = resolveTeamMemberIds(pmId);
        if (teamIds.isEmpty()) return List.of();

        return attendanceRepository.findByUserIdInAndMonthAndYear(teamIds, month, year)
                .stream().map(attendanceMapper::toResponseDTO).collect(Collectors.toList());
    }

    // ── Retroactive checkout ──────────────────────────────────────────────────

    public AttendanceResponseDTO fixMissedCheckout(Long userId, String checkOutTime) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Attendance attendance = attendanceRepository.findByUserIdAndDate(userId, yesterday)
                .orElseThrow(() -> new BusinessException("No check-in found for yesterday."));

        if (attendance.getCheckOut() != null)
            throw new BusinessException("Yesterday's checkout is already recorded.");

        String[]      parts    = checkOutTime.split(":");
        LocalDateTime checkOut = yesterday.atTime(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));

        if (!checkOut.isAfter(attendance.getCheckIn()))
            throw new BusinessException("Checkout time must be after check-in time.");

        attendance.setCheckOut(checkOut);
        attendance.setNotes("Checkout time provided retroactively by employee");
        computeDuration(attendance, userId);

        Attendance saved = attendanceRepository.save(attendance);
        log.info("Retroactive checkout for user {} — yesterday at {}, worked {}h",
                userId, checkOutTime, saved.getWorkDuration());
        return attendanceMapper.toResponseDTO(saved);
    }

    public boolean hasMissedCheckout(Long userId) {
        return attendanceRepository.findByUserIdAndDate(userId, LocalDate.now().minusDays(1))
                .map(a -> a.getCheckOut() == null)
                .orElse(false);
    }

    // ── Scheduled job method for missed checkouts ────────────────────────────

    public void detectMissedCheckouts() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<Attendance> attendances = attendanceRepository.findByDateAndCheckOutIsNull(yesterday);

        for (Attendance attendance : attendances) {
            if (attendance.getCheckIn() != null) {
                String dateStr = yesterday.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
                notificationService.notifyMissedCheckout(attendance.getUser().getId(), dateStr);
                log.info("Missed checkout notification sent to user {} for date {}",
                        attendance.getUser().getId(), dateStr);
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Set<Long> resolveTeamMemberIds(Long pmId) {
        return teamAssignmentRepository.findByAssigningManagerId(pmId)
                .stream()
                .filter(a -> Boolean.TRUE.equals(a.getActive()))
                .map(a -> a.getEmployee().getId())
                .collect(Collectors.toSet());
    }

    private Set<LocalDate> buildLeaveDaySet(Long userId, int month, int year) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd   = YearMonth.of(year, month).atEndOfMonth();

        return leaveRequestRepository.findApprovedLeavesByUserAndPeriod(userId, monthStart, monthEnd)
                .stream()
                .flatMap(lr -> {
                    LocalDate start = lr.getStartDate().isBefore(monthStart) ? monthStart : lr.getStartDate();
                    LocalDate end   = lr.getEndDate().isAfter(monthEnd)       ? monthEnd   : lr.getEndDate();
                    List<LocalDate> days = new ArrayList<>();
                    LocalDate d = start;
                    while (!d.isAfter(end)) {
                        if (d.getDayOfWeek().getValue() < 6) days.add(d);
                        d = d.plusDays(1);
                    }
                    return days.stream();
                })
                .collect(Collectors.toSet());
    }

    private int computeAbsentDays(int month, int year, int present, int late, int half, int leave) {
        LocalDate today    = LocalDate.now();
        LocalDate monthEnd = YearMonth.of(year, month).atEndOfMonth();
        LocalDate boundary = today.isBefore(monthEnd) ? today : monthEnd;
        LocalDate d = LocalDate.of(year, month, 1);
        int elapsedWorkingDays = 0;
        while (!d.isAfter(boundary)) {
            if (d.getDayOfWeek().getValue() < 6) elapsedWorkingDays++;
            d = d.plusDays(1);
        }
        return Math.max(0, elapsedWorkingDays - (present + late + half + leave));
    }

    private AttendanceStatus computeStatus(LocalDateTime checkIn, Long userId) {
        int lateHour   = configService.getInt(AttendanceConfig.KEY_LATE_HOUR);
        int lateMinute = configService.getInt(AttendanceConfig.KEY_LATE_MINUTE);
        boolean isLate = checkIn.getHour() > lateHour
                || (checkIn.getHour() == lateHour && checkIn.getMinute() > lateMinute);

        AttendanceStatus status = isLate ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;

        // Add late notification
        if (status == AttendanceStatus.LATE) {
            String checkinTime = checkIn.toLocalTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            notificationService.notifyLateArrival(userId, checkinTime);
        }

        return status;
    }

    private void computeDuration(Attendance attendance, Long userId) {
        double standardHours      = configService.getDouble(AttendanceConfig.KEY_STANDARD_HOURS);
        int    halfDayThreshold   = configService.getInt(AttendanceConfig.KEY_HALF_DAY_THRESHOLD);
        int    earlyDepartureHour = configService.getInt(AttendanceConfig.KEY_EARLY_DEPARTURE_HOUR);

        long   minutes = ChronoUnit.MINUTES.between(attendance.getCheckIn(), attendance.getCheckOut());
        double worked  = minutes / 60.0;

        attendance.setWorkDuration(worked);
        attendance.setOvertimeHours(Math.max(0.0, worked - standardHours));

        if (worked < halfDayThreshold) {
            attendance.setStatus(AttendanceStatus.HALF_DAY);
        } else if (attendance.getCheckOut().getHour() < earlyDepartureHour) {
            attendance.setStatus(AttendanceStatus.EARLY_DEPARTURE);
            // Notification will be sent in checkOutOnLogout method
        }
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + userId + " not found"));
    }
}