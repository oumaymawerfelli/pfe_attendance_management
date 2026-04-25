package com.example.pfe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

public class AttendanceConfigDTO {

    /** Payload the HR admin sends to update a single config entry. */
    @Data
    public static class UpdateRequest {

        @NotNull(message = "Value is required")
        private Double configValue;

        // Optional — client may send a note about why it was changed.
        private String lastModifiedBy;
    }

    /** What the API returns for each config entry. */
    @Data
    @Builder
    public static class Response {
        private Long          id;
        private String        configKey;
        private Double        configValue;
        private String        description;
        private String        lastModifiedBy;
        private LocalDateTime lastModifiedAt;
    }
}