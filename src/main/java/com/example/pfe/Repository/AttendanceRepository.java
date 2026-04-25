package com.example.pfe.Repository;

import com.example.pfe.entities.Attendance;
import com.example.pfe.enums.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    // ── Core ───────────────────────────────────────────────────────────────────

    Optional<Attendance> findByUserIdAndDate(Long userId, LocalDate date);

    boolean existsByUserIdAndDate(Long userId, LocalDate date);

    // ── Employee history ───────────────────────────────────────────────────────

    List<Attendance> findByUserIdOrderByDateDesc(Long userId);

    @Query("""
        SELECT a FROM Attendance a
        WHERE a.user.id = :userId
          AND MONTH(a.date) = :month
          AND YEAR(a.date)  = :year
        ORDER BY a.date DESC
    """)
    List<Attendance> findByUserIdAndMonthAndYear(
            @Param("userId") Long userId,
            @Param("month")  int month,
            @Param("year")   int year);

    @Query("""
        SELECT a FROM Attendance a
        WHERE a.user.id = :userId
          AND YEAR(a.date) = :year
        ORDER BY a.date DESC
    """)
    List<Attendance> findByUserIdAndYear(
            @Param("userId") Long userId,
            @Param("year")   int year);

    // ── Dashboard stats ────────────────────────────────────────────────────────

    @Query("""
        SELECT COUNT(a) FROM Attendance a
        WHERE a.user.id      = :userId
          AND a.status       = :status
          AND MONTH(a.date)  = :month
          AND YEAR(a.date)   = :year
    """)
    int countByUserIdAndStatusAndMonthAndYear(
            @Param("userId") Long userId,
            @Param("status") AttendanceStatus status,
            @Param("month")  int month,
            @Param("year")   int year);

    @Query("""
        SELECT COALESCE(SUM(a.workDuration), 0)
        FROM Attendance a
        WHERE a.user.id       = :userId
          AND MONTH(a.date)   = :month
          AND YEAR(a.date)    = :year
          AND a.workDuration IS NOT NULL
    """)
    double sumWorkDurationByUserIdAndMonthAndYear(
            @Param("userId") Long userId,
            @Param("month")  int month,
            @Param("year")   int year);

    @Query("""
        SELECT COALESCE(SUM(a.overtimeHours), 0)
        FROM Attendance a
        WHERE a.user.id       = :userId
          AND MONTH(a.date)   = :month
          AND YEAR(a.date)    = :year
          AND a.overtimeHours IS NOT NULL
    """)
    double sumOvertimeByUserIdAndMonthAndYear(
            @Param("userId") Long userId,
            @Param("month")  int month,
            @Param("year")   int year);

    @Query("""
        SELECT a FROM Attendance a
        WHERE a.user.id       = :userId
          AND MONTH(a.date)   = :month
          AND YEAR(a.date)    = :year
          AND a.checkOut      IS NOT NULL
        ORDER BY a.date ASC
    """)
    List<Attendance> findDailyHoursForChart(
            @Param("userId") Long userId,
            @Param("month")  int month,
            @Param("year")   int year);

    // ── HR / Admin: all employees ──────────────────────────────────────────────

    @Query("""
        SELECT a FROM Attendance a
        WHERE MONTH(a.date) = :month
          AND YEAR(a.date)  = :year
        ORDER BY a.date DESC, a.user.lastName ASC
    """)
    List<Attendance> findAllByMonthAndYear(
            @Param("month") int month,
            @Param("year")  int year);

    // ── PROJECT MANAGER: scoped to a set of team-member IDs ───────────────────

    /**
     * Attendance records for a specific set of employees in a given month/year.
     * Used by PROJECT_MANAGER to view their team's attendance.
     */
    @Query("""
        SELECT a FROM Attendance a
        WHERE a.user.id     IN :userIds
          AND MONTH(a.date)  = :month
          AND YEAR(a.date)   = :year
        ORDER BY a.date DESC, a.user.lastName ASC
    """)
    List<Attendance> findByUserIdInAndMonthAndYear(
            @Param("userIds") Collection<Long> userIds,
            @Param("month")   int month,
            @Param("year")    int year);

    // ── MISSED CHECKOUT DETECTION ──────────────────────────────────────────────

    /**
     * Find all attendance records for a specific date where checkout is missing.
     * Used by scheduled job to detect missed checkouts.
     */
    @Query("""
        SELECT a FROM Attendance a
        WHERE a.date = :date
          AND a.checkOut IS NULL
          AND a.checkIn IS NOT NULL
    """)
    List<Attendance> findByDateAndCheckOutIsNull(@Param("date") LocalDate date);
}