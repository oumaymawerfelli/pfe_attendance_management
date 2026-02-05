package com.example.pfe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponseDTO {
    private String token;
    private String type = "Bearer";
    private String message;
    private UserResponseDTO user; // Optionnel: retourner les infos utilisateur

    // Constructeurs pratiques
    public JwtResponseDTO(String token) {
        this.token = token;
        this.type = "Bearer";
    }

    public JwtResponseDTO(String token, String message) {
        this.token = token;
        this.type = "Bearer";
        this.message = message;
    }
}