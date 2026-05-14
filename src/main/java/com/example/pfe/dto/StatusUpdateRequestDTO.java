package com.example.pfe.dto;

import com.example.pfe.enums.ProjectStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StatusUpdateRequestDTO {
    @NotNull(message = "Status is required")
    private ProjectStatus status;

    @Size(max = 500)
    private String reason;
}
