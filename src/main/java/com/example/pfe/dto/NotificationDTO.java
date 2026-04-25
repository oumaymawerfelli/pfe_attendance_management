package com.example.pfe.dto;

import com.example.pfe.enums.NotificationType;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationDTO {

    private Long             id;
    private NotificationType type;
    private String           title;
    private String           message;
    private String           link;
    private boolean          read;
    private LocalDateTime    createdAt;
}