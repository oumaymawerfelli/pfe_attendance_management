// File: src/main/java/com/example/pfe/dto/UserStatsDTO.java
package com.example.pfe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsDTO {
    private long pending;
    private long active;
    private long disabled;
    private long locked;
    private long total;
}