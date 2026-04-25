package com.example.pfe.Service;

import com.example.pfe.Repository.LeaveBalanceRepository;
import com.example.pfe.Repository.LeaveRequestRepository;
import com.example.pfe.Repository.TeamAssignmentRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.dto.*;
import com.example.pfe.entities.LeaveBalance;
import com.example.pfe.entities.LeaveRequest;
import com.example.pfe.entities.User;
import com.example.pfe.enums.LeaveStatus;
import com.example.pfe.enums.LeaveType;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.LeaveMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class LeaveService {

    private final LeaveRequestRepository  leaveRequestRepository;
    private final LeaveBalanceRepository  leaveBalanceRepository;
    private final UserRepository          userRepository;
    private final TeamAssignmentRepository teamAssignmentRepository;
    private final LeaveMapper             leaveMapper;
    private final NotificationService notificationService; // ← ADD THIS

    // ══════════════════════════════════════════════════════════
    // EMPLOYEE — Submit leave request
    // ══════════════════════════════════════════════════════════

    public LeaveResponseDTO requestLeave(Long userId, LeaveRequestDTO dto) {
        log.info("Leave request from user {} — type: {}, from {} to {}",
                userId, dto.getLeaveType(), dto.getStartDate(), dto.getEndDate());

        validateDates(dto.getStartDate(), dto.getEndDate());
        validateNoOverlap(userId, dto.getStartDate(), dto.getEndDate());

        double daysCount = calculateWorkingDays(dto.getStartDate(), dto.getEndDate());

        if (dto.getLeaveType() != LeaveType.UNPAID) {
            validateBalance(userId, dto.getLeaveType(), daysCount);
        }

        User user = getUserById(userId);

        LeaveRequest request = LeaveRequest.builder()
                .user(user)
                .leaveType(dto.getLeaveType())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .daysCount(daysCount)
                .reason(dto.getReason())
                .status(LeaveStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        LeaveRequest saved = leaveRequestRepository.save(request);
        log.info("Leave request created — ID: {}, days: {}", saved.getId(), daysCount);

        return leaveMapper.toResponseDTO(saved);
    }

    // ══════════════════════════════════════════════════════════
    // EMPLOYEE — View own history
    // ══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<LeaveResponseDTO> getMyLeaves(Long userId) {
        return leaveRequestRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(leaveMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════
    // EMPLOYEE — View own balance
    // ══════════════════════════════════════════════════════════

    @Transactional
    public LeaveBalanceDTO getMyBalance(Long userId) {
        int currentYear = LocalDate.now().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByUserIdAndYear(userId, currentYear)
                .orElseGet(() -> createDefaultBalance(userId, currentYear));
        return leaveMapper.toBalanceDTO(balance);
    }

    // ══════════════════════════════════════════════════════════
    // GENERAL MANAGER / ADMIN — Full access
    // ══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<LeaveResponseDTO> getPendingLeaves() {
        return leaveRequestRepository
                .findByStatusOrderByCreatedAtAsc(LeaveStatus.PENDING)
                .stream()
                .map(leaveMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveResponseDTO> getAllLeaves() {
        return leaveRequestRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(leaveMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public LeaveResponseDTO approveLeave(Long requestId, Long adminId) {
        log.info("Admin/GM {} approving leave request {}", adminId, requestId);

        LeaveRequest request = getLeaveRequestById(requestId);
        validateIsPending(request);

        User admin = getUserById(adminId);
        request.setStatus(LeaveStatus.APPROVED);
        request.setApprovedBy(admin);
        request.setDecidedAt(LocalDateTime.now());

        deductBalance(request.getUser().getId(), request.getLeaveType(), request.getDaysCount());

        LeaveRequest saved = leaveRequestRepository.save(request);
        log.info("Leave request {} approved — {} days deducted from {} balance",
                requestId, request.getDaysCount(), request.getLeaveType());

        // Add notification
        notificationService.notifyLeaveApproved(
                request.getUser().getId(),
                leaveLabel(request.getLeaveType()),
                request.getStartDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                request.getEndDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        );

        return leaveMapper.toResponseDTO(saved);
    }

    public LeaveResponseDTO rejectLeave(Long requestId, Long adminId, LeaveDecisionDTO dto) {
        log.info("Admin/GM {} rejecting leave request {}", adminId, requestId);

        LeaveRequest request = getLeaveRequestById(requestId);
        validateIsPending(request);

        User admin = getUserById(adminId);
        request.setStatus(LeaveStatus.REJECTED);
        request.setApprovedBy(admin);
        request.setRejectionReason(dto.getRejectionReason());
        request.setDecidedAt(LocalDateTime.now());

        LeaveRequest saved = leaveRequestRepository.save(request);
        log.info("Leave request {} rejected", requestId);

        // Add notification
        notificationService.notifyLeaveRejected(
                request.getUser().getId(),
                leaveLabel(request.getLeaveType()),
                dto.getRejectionReason()
        );

        return leaveMapper.toResponseDTO(saved);
    }

    // ══════════════════════════════════════════════════════════
    // PROJECT MANAGER — Scoped to own team only
    // ══════════════════════════════════════════════════════════

    /**
     * Returns ALL leave requests (any status) for the PM's active team members.
     */
    @Transactional(readOnly = true)
    public List<LeaveResponseDTO> getTeamAllLeaves(Long pmId) {
        Set<Long> teamIds = resolveTeamMemberIds(pmId);
        if (teamIds.isEmpty()) return List.of();

        return leaveRequestRepository
                .findByUserIdInOrderByCreatedAtDesc(teamIds)
                .stream()
                .map(leaveMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Returns PENDING leave requests for the PM's active team members (inbox).
     */
    @Transactional(readOnly = true)
    public List<LeaveResponseDTO> getTeamPendingLeaves(Long pmId) {
        Set<Long> teamIds = resolveTeamMemberIds(pmId);
        if (teamIds.isEmpty()) return List.of();

        return leaveRequestRepository
                .findByUserIdInAndStatusOrderByCreatedAtAsc(teamIds, LeaveStatus.PENDING)
                .stream()
                .map(leaveMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * PM approves a leave — only allowed if the requester is in their team.
     */
    public LeaveResponseDTO approveLeaveByPM(Long requestId, Long pmId) {
        log.info("PM {} approving leave request {}", pmId, requestId);

        LeaveRequest request = getLeaveRequestById(requestId);
        assertIsTeamMember(pmId, request.getUser().getId());

        return approveLeave(requestId, pmId);
    }

    /**
     * PM rejects a leave — only allowed if the requester is in their team.
     */
    public LeaveResponseDTO rejectLeaveByPM(Long requestId, Long pmId, LeaveDecisionDTO dto) {
        log.info("PM {} rejecting leave request {}", pmId, requestId);

        LeaveRequest request = getLeaveRequestById(requestId);
        assertIsTeamMember(pmId, request.getUser().getId());

        return rejectLeave(requestId, pmId, dto);
    }

    // ══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════

    private String leaveLabel(LeaveType type) {
        return switch (type) {
            case ANNUAL -> "Annual";
            case SICK   -> "Sick";
            case UNPAID -> "Unpaid";
        };
    }

    /**
     * Returns the IDs of all ACTIVE employees currently assigned to the PM's team.
     * Uses the assignments where this PM was the assigning manager.
     */
    private Set<Long> resolveTeamMemberIds(Long pmId) {
        return teamAssignmentRepository.findByAssigningManagerId(pmId)
                .stream()
                .filter(a -> Boolean.TRUE.equals(a.getActive()))
                .map(a -> a.getEmployee().getId())
                .collect(Collectors.toSet());
    }

    /**
     * Throws BusinessException if the given employee is NOT in the PM's active team.
     * Central guard used before any PM approve/reject action.
     */
    private void assertIsTeamMember(Long pmId, Long employeeId) {
        boolean isMember = teamAssignmentRepository.findByAssigningManagerId(pmId)
                .stream()
                .filter(a -> Boolean.TRUE.equals(a.getActive()))
                .anyMatch(a -> a.getEmployee().getId().equals(employeeId));

        if (!isMember) {
            throw new BusinessException(
                    "Access denied: employee is not a member of your team");
        }
    }

    private double calculateWorkingDays(LocalDate start, LocalDate end) {
        long days = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            DayOfWeek day = current.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) days++;
            current = current.plusDays(1);
        }
        return days;
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start.isBefore(LocalDate.now())) {
            throw new BusinessException("Start date cannot be in the past");
        }
        if (end.isBefore(start)) {
            throw new BusinessException("End date must be after start date");
        }
        if (calculateWorkingDays(start, end) == 0) {
            throw new BusinessException("Selected period contains no working days");
        }
    }

    private void validateNoOverlap(Long userId, LocalDate start, LocalDate end) {
        if (leaveRequestRepository.existsOverlappingLeave(userId, start, end)) {
            throw new BusinessException(
                    "You already have a leave request overlapping these dates");
        }
    }

    private void validateBalance(Long userId, LeaveType type, double daysRequested) {
        int year = LocalDate.now().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByUserIdAndYear(userId, year)
                .orElseGet(() -> createDefaultBalance(userId, year));

        double remaining = switch (type) {
            case ANNUAL -> balance.getAnnualTotal() - balance.getAnnualTaken();
            case SICK   -> balance.getSickTotal()   - balance.getSickTaken();
            default     -> Double.MAX_VALUE;
        };

        if (daysRequested > remaining) {
            throw new BusinessException(
                    "Insufficient " + type.name().toLowerCase() +
                            " leave balance. Requested: " + daysRequested +
                            " days, Available: " + remaining + " days");
        }
    }

    private void deductBalance(Long userId, LeaveType type, double days) {
        int year = LocalDate.now().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByUserIdAndYear(userId, year)
                .orElseGet(() -> createDefaultBalance(userId, year));

        switch (type) {
            case ANNUAL -> balance.setAnnualTaken(balance.getAnnualTaken() + days);
            case SICK   -> balance.setSickTaken(balance.getSickTaken()     + days);
            case UNPAID -> balance.setUnpaidTaken(balance.getUnpaidTaken() + days);
        }

        leaveBalanceRepository.save(balance);
        log.info("Balance updated for user {} — {} days deducted from {}", userId, days, type);
    }

    private LeaveBalance createDefaultBalance(Long userId, int year) {
        User user = getUserById(userId);
        LeaveBalance balance = LeaveBalance.builder()
                .user(user)
                .year(year)
                .annualTotal(22.0).annualTaken(0.0)
                .sickTotal(15.0).sickTaken(0.0)
                .unpaidTotal(0.0).unpaidTaken(0.0)
                .build();
        return leaveBalanceRepository.save(balance);
    }

    private void validateIsPending(LeaveRequest request) {
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new BusinessException(
                    "This request is already " + request.getStatus().name().toLowerCase());
        }
    }

    private LeaveRequest getLeaveRequestById(Long id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leave request with ID " + id + " not found"));
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User with ID " + userId + " not found"));
    }
}