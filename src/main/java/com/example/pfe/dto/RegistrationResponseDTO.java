package com.example.pfe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationResponseDTO {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private Boolean active;
    private Boolean enabled;
    private String message;
    private boolean activationEmailSent;
}