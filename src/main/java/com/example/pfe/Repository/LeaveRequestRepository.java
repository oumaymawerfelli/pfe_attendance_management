package com.example.pfe.Repository;

import com.example.pfe.entities.LeaveRequest;
import com.example.pfe.enums.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.pfe.enums.LeaveStatus;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    // ── Employee ───────────────────────────────────────────────────────────────

    List<LeaveRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveStatus status);

    List<LeaveRequest> findAllByOrderByCreatedAtDesc();
    long countByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            LeaveStatus status, LocalDate endRef, LocalDate startRef);

    @Query("""
        SELECT COUNT(lr) > 0 FROM LeaveRequest lr
        WHERE lr.user.id  = :userId
          AND lr.status  != 'REJECTED'
          AND lr.startDate <= :end
          AND lr.endDate   >= :start
    """)
    boolean existsOverlappingLeave(
            @Param("userId") Long userId,
            @Param("start")  LocalDate start,
            @Param("end")    LocalDate end);

    // ── AttendanceService: approved leaves for a user in a date range ──────────

    @Query("""
        SELECT lr FROM LeaveRequest lr
        WHERE lr.user.id    = :userId
          AND lr.status     = 'APPROVED'
          AND lr.startDate <= :end
          AND lr.endDate   >= :start
    """)
    List<LeaveRequest> findApprovedLeavesByUserAndPeriod(
            @Param("userId") Long userId,
            @Param("start")  LocalDate start,
            @Param("end")    LocalDate end);

    // ── PROJECT MANAGER: team members' leaves ─────────────────────────────────

    /**
     * All leave requests for a set of employees, newest first.
     * Used by PM to see their whole team's leave history.
     */
    List<LeaveRequest> findByUserIdInOrderByCreatedAtDesc(Collection<Long> userIds);

    /**
     * Pending leave requests for a set of employees, oldest first (inbox order).
     * Used by PM to see their team's pending requests.
     */
    List<LeaveRequest> findByUserIdInAndStatusOrderByCreatedAtAsc(
            Collection<Long> userIds,
            LeaveStatus status);
}