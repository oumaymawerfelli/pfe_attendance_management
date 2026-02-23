// src/main/java/com/example/pfe/mapper/UserMapper.java

package com.example.pfe.mapper;

import com.example.pfe.dto.RegisterRequestDTO;
import com.example.pfe.dto.UserRequestDTO;
import com.example.pfe.dto.UserResponseDTO;
import com.example.pfe.entities.User;

import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class UserMapper {

    public User toEntity(UserRequestDTO dto) {
        return User.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .birthDate(dto.getBirthDate())
                .gender(dto.getGender())
                .nationalId(dto.getNationalId())
                .nationality(dto.getNationality())
                .maritalStatus(dto.getMaritalStatus())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .address(dto.getAddress())
                .jobTitle(dto.getJobTitle())
                .department(dto.getDepartment())
                .service(dto.getService())
                .hireDate(dto.getHireDate())
                .contractType(dto.getContractType())
                .contractEndDate(dto.getContractEndDate())
                .baseSalary(dto.getBaseSalary())
                .housingAllowance(dto.getHousingAllowance())
                .evaluationScore(dto.getEvaluationScore())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .socialSecurityNumber(dto.getSocialSecurityNumber())
                .childrenCount(dto.getChildrenCount() != null ? dto.getChildrenCount() : 0)
                // NEW: Add description
                .description(dto.getDescription())
                .build();
    }

    public User toEntity(RegisterRequestDTO dto) {
        return User.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .birthDate(dto.getBirthDate())
                .gender(dto.getGender())
                .nationalId(dto.getNationalId())
                .nationality(dto.getNationality())
                .maritalStatus(dto.getMaritalStatus())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .address(dto.getAddress())
                .jobTitle(dto.getJobTitle())
                .department(dto.getDepartment())
                .service(dto.getService())
                .hireDate(dto.getHireDate())
                .contractType(dto.getContractType())
                .contractEndDate(dto.getContractEndDate())
                .baseSalary(dto.getBaseSalary())
                .housingAllowance(dto.getHousingAllowance())
                .evaluationScore(dto.getEvaluationScore())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .socialSecurityNumber(dto.getSocialSecurityNumber())
                .childrenCount(dto.getChildrenCount() != null ? dto.getChildrenCount() : 0)
                // NEW: Add description (might be null for registration)
                .description(dto.getDescription())
                .build();
    }

    public void updateEntityFromDTO(UserRequestDTO dto, User user) {
        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName());
        if (dto.getBirthDate() != null) user.setBirthDate(dto.getBirthDate());
        if (dto.getGender() != null) user.setGender(dto.getGender());
        if (dto.getNationalId() != null) user.setNationalId(dto.getNationalId());
        if (dto.getNationality() != null) user.setNationality(dto.getNationality());
        if (dto.getMaritalStatus() != null) user.setMaritalStatus(dto.getMaritalStatus());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getAddress() != null) user.setAddress(dto.getAddress());
        if (dto.getJobTitle() != null) user.setJobTitle(dto.getJobTitle());
        if (dto.getDepartment() != null) user.setDepartment(dto.getDepartment());
        if (dto.getService() != null) user.setService(dto.getService());
        if (dto.getHireDate() != null) user.setHireDate(dto.getHireDate());
        if (dto.getContractType() != null) user.setContractType(dto.getContractType());
        if (dto.getContractEndDate() != null) user.setContractEndDate(dto.getContractEndDate());
        if (dto.getBaseSalary() != null) user.setBaseSalary(dto.getBaseSalary());
        if (dto.getHousingAllowance() != null) user.setHousingAllowance(dto.getHousingAllowance());
        if (dto.getEvaluationScore() != null) user.setEvaluationScore(dto.getEvaluationScore());
        if (dto.getActive() != null) user.setActive(dto.getActive());
        if (dto.getSocialSecurityNumber() != null) user.setSocialSecurityNumber(dto.getSocialSecurityNumber());
        if (dto.getChildrenCount() != null) user.setChildrenCount(dto.getChildrenCount());
        // NEW: Update description
        if (dto.getDescription() != null) user.setDescription(dto.getDescription());
    }

    public void updateEntityFromRegister(RegisterRequestDTO dto, User user) {
        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName());
        if (dto.getBirthDate() != null) user.setBirthDate(dto.getBirthDate());
        if (dto.getGender() != null) user.setGender(dto.getGender());
        if (dto.getNationalId() != null) user.setNationalId(dto.getNationalId());
        if (dto.getNationality() != null) user.setNationality(dto.getNationality());
        if (dto.getMaritalStatus() != null) user.setMaritalStatus(dto.getMaritalStatus());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getAddress() != null) user.setAddress(dto.getAddress());
        if (dto.getJobTitle() != null) user.setJobTitle(dto.getJobTitle());
        if (dto.getDepartment() != null) user.setDepartment(dto.getDepartment());
        if (dto.getService() != null) user.setService(dto.getService());
        if (dto.getHireDate() != null) user.setHireDate(dto.getHireDate());
        if (dto.getContractType() != null) user.setContractType(dto.getContractType());
        if (dto.getContractEndDate() != null) user.setContractEndDate(dto.getContractEndDate());
        if (dto.getBaseSalary() != null) user.setBaseSalary(dto.getBaseSalary());
        if (dto.getHousingAllowance() != null) user.setHousingAllowance(dto.getHousingAllowance());
        if (dto.getEvaluationScore() != null) user.setEvaluationScore(dto.getEvaluationScore());
        if (dto.getActive() != null) user.setActive(dto.getActive());
        if (dto.getSocialSecurityNumber() != null) user.setSocialSecurityNumber(dto.getSocialSecurityNumber());
        if (dto.getChildrenCount() != null) user.setChildrenCount(dto.getChildrenCount());
        // NEW: Update description (if present)
        if (dto.getDescription() != null) user.setDescription(dto.getDescription());
    }

    public UserResponseDTO toResponseDTO(User user) {
        if (user == null) return null;

        return UserResponseDTO.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .birthDate(user.getBirthDate())
                .gender(user.getGender())
                .nationalId(user.getNationalId())
                .nationality(user.getNationality())
                .maritalStatus(user.getMaritalStatus())
                .email(user.getEmail())
                .phone(user.getPhone())
                .address(user.getAddress())
                .jobTitle(user.getJobTitle())
                .department(user.getDepartment())
                .service(user.getService())
                .hireDate(user.getHireDate())
                .contractType(user.getContractType())
                .contractEndDate(user.getContractEndDate())
                .baseSalary(user.getBaseSalary())
                .housingAllowance(user.getHousingAllowance())
                .evaluationScore(user.getEvaluationScore())
                .evaluationDate(user.getEvaluationDate())
                .active(user.getActive())
                .socialSecurityNumber(user.getSocialSecurityNumber())
                .assignedProjectManagerId(user.getAssignedProjectManager() != null ?
                        user.getAssignedProjectManager().getId() : null)
                .directManagerId(user.getDirectManager() != null ?
                        user.getDirectManager().getId() : null)
                .childrenCount(user.getChildrenCount())
                .description(user.getDescription())
                .roles(user.getRoles() != null ?
                        user.getRoles().stream()
                                .map(role -> "ROLE_" + role.getName().name())
                                .collect(Collectors.toList())
                        : null)
                .enabled(user.isEnabled())
                .registrationPending(user.isRegistrationPending())
                .accountNonLocked(user.isAccountNonLocked())

                .lastLogin(user.getLastLogin() != null ?
                        user.getLastLogin().atStartOfDay() : null)
                .username(user.getUsername())
                .build();
    }}