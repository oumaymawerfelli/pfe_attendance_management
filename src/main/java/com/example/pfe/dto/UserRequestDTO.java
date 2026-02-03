package com.example.pfe.dto;

import com.example.pfe.enums.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestDTO {



    @NotBlank(message = "Le prénom est obligatoire")
    @Size(min = 2, max = 50, message = "Le prénom doit contenir entre 2 et 50 caractères")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 2, max = 50, message = "Le nom doit contenir entre 2 et 50 caractères")
    private String lastName;

    @NotNull(message = "La date de naissance est obligatoire")
    @Past(message = "La date de naissance doit être dans le passé")
    private LocalDate birthDate;

    @NotNull(message = "Le genre est obligatoire")
    private Gender gender;

    @NotBlank(message = "La CIN est obligatoire")
    @Pattern(regexp = "^[0-9]{8}$", message = "La CIN doit contenir 8 chiffres")
    private String nationalId;

    @NotBlank(message = "La nationalité est obligatoire")
    private String nationality;

    @NotNull(message = "L'état civil est obligatoire")
    private MaritalStatus maritalStatus;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email doit être valide")
    private String email;

    @NotBlank(message = "Le téléphone est obligatoire")
    @Pattern(regexp = "^[0-9]{8}$", message = "Le numéro de téléphone doit contenir 8 chiffres")
    private String phone;

    private String address;
    private String jobTitle;

    @NotNull(message = "Le département est obligatoire")
    private Department department;

    private String service;

    @NotNull(message = "La date d'embauche est obligatoire")
    private LocalDate hireDate;

    @NotNull(message = "Le type de contrat est obligatoire")
    private ContractType contractType;

    private LocalDate contractEndDate;

    @NotNull(message = "Le salaire de base est obligatoire")
    @Positive(message = "Le salaire de base doit être positif")
    private Double baseSalary;

    @PositiveOrZero(message = "L'allocation logement ne peut pas être négative")
    private Double housingAllowance;

    @Min(value = 0, message = "Le score d'évaluation doit être au minimum 0")
    @Max(value = 5, message = "Le score d'évaluation doit être au maximum 5")
    private Integer evaluationScore;

    private Boolean active;

    @Pattern(regexp = "^[0-9]{10}$", message = "Le numéro de sécurité sociale doit contenir 10 chiffres")
    private String socialSecurityNumber;

    private Long assignedProjectManagerId;
    private Long directManagerId;

    @Min(value = 0, message = "Le nombre d'enfants ne peut pas être négatif")
    private Integer childrenCount;
}