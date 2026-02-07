package com.example.pfe.dto;

import com.example.pfe.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestDTO {

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



    private Boolean active;

    @Pattern(regexp = "^[0-9]{10}$", message = "Social security number must contain 10 digits")
    private String socialSecurityNumber;

    private Long assignedProjectManagerId;
    private Long directManagerId;


    private List<String> roleNames;


    @Min(value = 0, message = "Number of children cannot be negative")
    private Integer childrenCount;
}
