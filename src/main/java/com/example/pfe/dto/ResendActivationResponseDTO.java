package com.example.pfe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendActivationResponseDTO {
    private String message;
    private String email;
    @Builder.Default
    private boolean success = true;
}