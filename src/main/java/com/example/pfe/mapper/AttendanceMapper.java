package com.example.pfe.mapper;

import com.example.pfe.dto.AttendanceResponseDTO;
import com.example.pfe.entities.Attendance;
import org.springframework.stereotype.Component;

@Component
public class AttendanceMapper {

    public AttendanceResponseDTO toResponseDTO(Attendance attendance) {
        if (attendance == null) return null;

        return AttendanceResponseDTO.builder()
                .id(attendance.getId())
                // ── User info ─────────────────────────────────
                .userId(attendance.getUser().getId())
                .userFullName(
                        attendance.getUser().getFirstName() + " " +
                                attendance.getUser().getLastName()
                )
                .userDepartment(
                        attendance.getUser().getDepartment() != null
                                ? attendance.getUser().getDepartment().name()
                                : null
                )
                // ── Attendance data ───────────────────────────
                .date(attendance.getDate())
                .checkIn(attendance.getCheckIn())
                .checkOut(attendance.getCheckOut())
                .status(attendance.getStatus())
                .workDuration(attendance.getWorkDuration())
                .overtimeHours(attendance.getOvertimeHours())
                .notes(attendance.getNotes())
                .build();
    }
}