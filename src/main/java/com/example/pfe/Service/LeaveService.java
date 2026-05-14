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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private final LeaveRequestRepository   leaveRequestRepository;
    private final LeaveBalanceRepository   leaveBalanceRepository;
    private final UserRepository           userRepository;
    private final TeamAssignmentRepository teamAssignmentRepository;
    private final LeaveMapper              leaveMapper;
    private final NotificationService      notificationService;

    @Value("${app.upload.dir:uploads/leave-documents}")
    private String uploadDir;

    // ══════════════════════════════════════════════════════════
    // EMPLOYEE — Submit leave request (multipart: JSON + optional file)
    // ══════════════════════════════════════════════════════════

    public LeaveResponseDTO requestLeave(Long userId,
                                         LeaveRequestDTO dto,
                                         MultipartFile attachment) {

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

        // Store attachment if provided
        if (attachment != null && !attachment.isEmpty()) {
            String filePath = storeAttachment(saved.getId(), attachment);
            saved.setDocumentPath(filePath);
            saved = leaveRequestRepository.save(saved);
        }

        return leaveMapper.toResponseDTO(saved);
    }

    // ══════════════════════════════════════════════════════════
    // EMPLOYEE — Save draft (no workflow triggered)
    // ══════════════════════════════════════════════════════════

    public LeaveResponseDTO saveDraft(Long userId, LeaveRequestDTO dto) {
        log.info("Saving draft for user {}", userId);

        User user = getUserById(userId);

        LeaveRequest draft = LeaveRequest.builder()
                .user(user)
                .leaveType(dto.getLeaveType())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .daysCount(dto.getDuration() != null
                        ? dto.getDuration()
                        : calculateWorkingDays(dto.getStartDate(), dto.getEndDate()))
                .reason(dto.getReason() != null ? dto.getReason() : "")
                .status(LeaveStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .build();

        return leaveMapper.toResponseDTO(leaveRequestRepository.save(draft));
    }

    // ══════════════════════════════════════════════════════════
    // EMPLOYEE — Summary (balances + workflow) for the request form
    // ══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public LeaveSummaryDTO getSummary(Long userId) {
        int year = LocalDate.now().getYear();

        LeaveBalance balance = leaveBalanceRepository
                .findByUserIdAndYear(userId, year)
                .orElseGet(() -> createDefaultBalance(userId, year));

        LeaveSummaryDTO dto = new LeaveSummaryDTO();

        // Reuse all LeaveBalanceDTO fields
        dto.setYear(year);
        dto.setAnnualTotal(balance.getAnnualTotal());
        dto.setAnnualTaken(balance.getAnnualTaken());
        dto.setAnnualRemaining(balance.getAnnualTotal() - balance.getAnnualTaken());

        dto.setSickTotal(balance.getSickTotal());
        dto.setSickTaken(balance.getSickTaken());
        dto.setSickRemaining(balance.getSickTotal() - balance.getSickTaken());

        dto.setUnpaidTotal(balance.getUnpaidTotal());
        dto.setUnpaidTaken(balance.getUnpaidTaken());

        // Workflow chain — fixed 3-step for now
        dto.setWorkflow(List.of("TEAM_LEAD", "HR_MANAGER", "GENERAL_MANAGER"));

        return dto;
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

    @Transactional(readOnly = true)
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

        deductBalance(request.getUser().getId(),
                request.getLeaveType(), request.getDaysCount());

        LeaveRequest saved = leaveRequestRepository.save(request);
        log.info("Leave {} approved — {} days deducted from {} balance",
                requestId, request.getDaysCount(), request.getLeaveType());

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

        notificationService.notifyLeaveRejected(
                request.getUser().getId(),
                leaveLabel(request.getLeaveType()),
                dto.getRejectionReason()
        );

        return leaveMapper.toResponseDTO(saved);
    }

    // ══════════════════════════════════════════════════════════
    // PROJECT MANAGER — Scoped to own team
    // ══════════════════════════════════════════════════════════

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

    public LeaveResponseDTO approveLeaveByPM(Long requestId, Long pmId) {
        log.info("PM {} approving leave request {}", pmId, requestId);
        LeaveRequest request = getLeaveRequestById(requestId);
        assertIsTeamMember(pmId, request.getUser().getId());
        return approveLeave(requestId, pmId);
    }

    public LeaveResponseDTO rejectLeaveByPM(Long requestId, Long pmId, LeaveDecisionDTO dto) {
        log.info("PM {} rejecting leave request {}", pmId, requestId);
        LeaveRequest request = getLeaveRequestById(requestId);
        assertIsTeamMember(pmId, request.getUser().getId());
        return rejectLeave(requestId, pmId, dto);
    }

    // ══════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════

    /**
     * Saves the uploaded file to the configured upload directory.
     * Returns the relative path stored in the database.
     */
    private String storeAttachment(Long leaveId, MultipartFile file) {
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);

            String originalName = file.getOriginalFilename() != null
                    ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_")
                    : "attachment";
            String filename = "leave_" + leaveId + "_" + originalName;
            Path target = dir.resolve(filename);
            Files.write(target, file.getBytes());

            log.info("Attachment saved: {}", target);
            return uploadDir + "/" + filename;

        } catch (IOException e) {
            log.error("Failed to store attachment for leave {}: {}", leaveId, e.getMessage());
            // Non-fatal — request is saved, attachment is just missing
            return null;
        }
    }

    private String leaveLabel(LeaveType type) {
        return switch (type) {
            case ANNUAL -> "Annual";
            case SICK   -> "Sick";
            case UNPAID -> "Unpaid";
        };
    }

    private Set<Long> resolveTeamMemberIds(Long pmId) {
        return teamAssignmentRepository.findByAssigningManagerId(pmId)
                .stream()
                .filter(a -> Boolean.TRUE.equals(a.getActive()))
                .map(a -> a.getEmployee().getId())
                .collect(Collectors.toSet());
    }

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