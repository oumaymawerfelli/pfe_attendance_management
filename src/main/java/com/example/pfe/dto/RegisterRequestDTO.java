package com.example.pfe.dto;

import com.example.pfe.enums.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDTO {

    // Personal Information
    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;

    @NotNull(message = "Birth date is required")
    @Past(message = "Birth date must be in the past")
    private LocalDate birthDate;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotBlank(message = "National ID is required")
    @Pattern(regexp = "^[0-9]{8}$", message = "National ID must contain 8 digits")
    private String nationalId;

    @NotBlank(message = "Nationality is required")
    private String nationality;

    @NotNull(message = "Marital status is required")
    private MaritalStatus maritalStatus;

    // Professional Information
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{8}$", message = "Phone number must contain 8 digits")
    private String phone;

    private String address;
    private String jobTitle;

    @NotNull(message = "Department is required")
    private Department department;

    private String service;

    @NotNull(message = "Hire date is required")
    private LocalDate hireDate;

    @NotNull(message = "Contract type is required")
    private ContractType contractType;

    private LocalDate contractEndDate;

    @NotNull(message = "Base salary is required")
    @Positive(message = "Base salary must be positive")
    private Double baseSalary;

    @PositiveOrZero(message = "Housing allowance cannot be negative")
    private Double housingAllowance;

    @Min(value = 0, message = "Evaluation score must be at least 0")
    @Max(value = 5, message = "Evaluation score must be at most 5")
    private Integer evaluationScore;

    private Boolean active = true;

    @Pattern(regexp = "^[0-9]{10}$", message = "Social security number must contain 10 digits")
    private String socialSecurityNumber;

    // Relations
    private Long assignedProjectManagerId;
    private Long directManagerId;

    @Min(value = 0, message = "Number of children cannot be negative")
    private Integer childrenCount;
    private String description;

    // Registration-specific fields
    private List<Long> roleIds; // Role IDs to assign
    @Builder.Default
    private List<String> roleNames = new ArrayList<>();
    // Additional validations
    public void validate() {
        if (hireDate != null && hireDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Hire date cannot be in the future");
        }

        if (birthDate != null && !birthDate.isBefore(LocalDate.now().minusYears(16))) {
            throw new IllegalArgumentException("Employee must be at least 16 years old");
        }

        if (contractEndDate != null && hireDate != null && contractEndDate.isBefore(hireDate)) {
            throw new IllegalArgumentException("Contract end date cannot be before hire date");
        }
    }

}
