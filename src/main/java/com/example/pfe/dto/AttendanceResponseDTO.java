package com.example.pfe.dto;
import com.example.pfe.enums.AttendanceStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceResponseDTO {
    private Long id;

    //Employee info ( for hr and admin view )
    private Long userId;
    private String userFullName;
    private String userDepartment;

    //Attendance Data
    private LocalDate date;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private AttendanceStatus status;

    private Double workDuration; //null if noy checked out yet
        private Double overtimeHours; //null if noy checked out yet
        private String notes;

}
