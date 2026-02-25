package com.example.pfe.dto;

import com.example.pfe.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private Long id;

    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private Gender gender;
    private String nationalId;
    private String nationality;
    private MaritalStatus maritalStatus;
    private String email;
    private String phone;
    private String address;
    private String jobTitle;
    private Department department;
    private String service;
    private LocalDate hireDate;
    private ContractType contractType;
    private LocalDate contractEndDate;
    private Double baseSalary;
    private Double housingAllowance;
    private Integer evaluationScore;
    private LocalDate evaluationDate;
    private Boolean active;
    private String socialSecurityNumber;
    private Long assignedProjectManagerId;
    private Long directManagerId;
    private Integer childrenCount;
    private List<String> roles;
    private String description;
    private boolean registrationPending;
    private boolean enabled;
    private LocalDateTime lastLogin;
    private String username;
    private LocalDateTime createdAt;
    private String avatar;


    private boolean accountNonLocked;
}