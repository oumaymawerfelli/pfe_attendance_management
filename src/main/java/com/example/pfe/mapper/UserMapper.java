package com.example.pfe.mapper;

import com.example.pfe.dto.UserRequestDTO;
import com.example.pfe.dto.UserResponseDTO;
import com.example.pfe.entities.User;
import org.springframework.stereotype.Component;

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
                .build();
    }

    public UserResponseDTO toResponseDTO(User user) {
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
                .build();
    }
}