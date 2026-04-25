package com.example.pfe.mapper;

import com.example.pfe.dto.LeaveBalanceDTO;
import com.example.pfe.dto.LeaveResponseDTO;
import com.example.pfe.entities.LeaveBalance;
import com.example.pfe.entities.LeaveRequest;
import org.springframework.stereotype.Component;

@Component
public class LeaveMapper {

    public LeaveResponseDTO toResponseDTO(LeaveRequest request) {
        if (request == null) return null;

        return LeaveResponseDTO.builder()
                .id(request.getId())
                // ── Employee info ──────────────────────────────
                .userId(request.getUser().getId())
                .userFullName(
                        request.getUser().getFirstName() + " " +
                                request.getUser().getLastName()
                )
                .userDepartment(
                        request.getUser().getDepartment() != null
                                ? request.getUser().getDepartment().name()
                                : null
                )
                // ── Leave details ──────────────────────────────
                .leaveType(request.getLeaveType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .daysCount(request.getDaysCount())
                .reason(request.getReason())
                // ── Status ─────────────────────────────────────
                .status(request.getStatus())
                .approvedByFullName(
                        request.getApprovedBy() != null
                                ? request.getApprovedBy().getFirstName() + " " +
                                request.getApprovedBy().getLastName()
                                : null
                )
                .approvedByRole(
                        request.getApprovedBy() != null
                                ? request.getApprovedBy().getRoles().stream()
                                .map(r -> r.getName().name())
                                .filter(n -> n.equals("PROJECT_MANAGER")
                                        || n.equals("GENERAL_MANAGER")
                                        || n.equals("ADMIN"))
                                .findFirst()
                                .orElse(null)
                                : null
                )
                .rejectionReason(request.getRejectionReason())
                .createdAt(request.getCreatedAt())
                .decidedAt(request.getDecidedAt())
                // ── Document ───────────────────────────────────
                .documentPath(request.getDocumentPath())
                .build();
    }

    public LeaveBalanceDTO toBalanceDTO(LeaveBalance balance) {
        if (balance == null) return null;

        return LeaveBalanceDTO.builder()
                .year(balance.getYear())
                // ── Annual ─────────────────────────────────────
                .annualTotal(balance.getAnnualTotal())
                .annualTaken(balance.getAnnualTaken())
                .annualRemaining(balance.getAnnualTotal() - balance.getAnnualTaken())
                // ── Sick ───────────────────────────────────────
                .sickTotal(balance.getSickTotal())
                .sickTaken(balance.getSickTaken())
                .sickRemaining(balance.getSickTotal() - balance.getSickTaken())
                // ── Unpaid ─────────────────────────────────────
                .unpaidTotal(balance.getUnpaidTotal())
                .unpaidTaken(balance.getUnpaidTaken())
                .build();
    }
}