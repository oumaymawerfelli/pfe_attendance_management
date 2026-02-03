package com.example.pfe.Service;

import com.example.pfe.dto.UserRequestDTO;
import com.example.pfe.dto.UserResponseDTO;
import com.example.pfe.entities.User;
import com.example.pfe.enums.ContractType;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.UserMapper;
import com.example.pfe.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final EmployeeCodeGenerator employeeCodeGenerator;

    @Override
    @Transactional
    public UserResponseDTO createUser(UserRequestDTO userDTO) {
        log.info("Creating new user with email: {}", userDTO.getEmail());

        // 1. Business validation
        validateBusinessRulesForCreate(userDTO);

        // 2. Map DTO to Entity (without employeeCode)
        User user = userMapper.toEntity(userDTO);

        // 3. AUTO-GENERATE employee code
        String generatedEmployeeCode = employeeCodeGenerator.generateEmployeeCode(user);
        user.setEmployeeCode(generatedEmployeeCode);
        log.info("Generated employee code: {}", generatedEmployeeCode);

        // 4. Save to database
        User savedUser = userRepository.save(user);

        log.info("User created successfully with ID: {}", savedUser.getId());
        return userMapper.toResponseDTO(savedUser);
    }

    private void validateBusinessRulesForCreate(UserRequestDTO userDTO) {
        // Check email uniqueness
        if (userRepository.findByEmail(userDTO.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists: " + userDTO.getEmail());
        }

        // Check national ID uniqueness (if provided)
        if (userDTO.getNationalId() != null &&
                userRepository.findByNationalId(userDTO.getNationalId()).isPresent()) {
            throw new IllegalArgumentException("National ID already exists: " + userDTO.getNationalId());
        }

        // ✅ NO employee code uniqueness check - it's auto-generated, so guaranteed unique

        // Contract business logic
        if (userDTO.getContractType() == ContractType.CDD && userDTO.getContractEndDate() == null) {
            throw new IllegalArgumentException("Contract end date is required for CDD contracts");
        }

        // Date validation
        if (userDTO.getBirthDate() != null && userDTO.getBirthDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Birth date cannot be in the future");
        }

        // Salary validation
        if (userDTO.getBaseSalary() != null && userDTO.getBaseSalary() < 0) {
            throw new IllegalArgumentException("Base salary cannot be negative");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return userMapper.toResponseDTO(user);
    }

    @Override
    @Transactional
    public UserResponseDTO updateUser(Long id, UserRequestDTO userDTO) {
        log.info("Updating user with ID: {}", id);

        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Validate business rules for update
        validateBusinessRulesForUpdate(existingUser, userDTO);

        // Update entity from DTO
        updateEntityFromDTO(existingUser, userDTO);

        User updatedUser = userRepository.save(existingUser);
        log.info("User updated successfully with ID: {}", id);

        return userMapper.toResponseDTO(updatedUser);
    }

    private void validateBusinessRulesForUpdate(User existingUser, UserRequestDTO userDTO) {
        // Email uniqueness check (if changing email)
        if (userDTO.getEmail() != null &&
                !userDTO.getEmail().equals(existingUser.getEmail())) {
            if (userRepository.findByEmail(userDTO.getEmail()).isPresent()) {
                throw new IllegalArgumentException("Email already exists: " + userDTO.getEmail());
            }
        }

        // National ID uniqueness check (if changing)
        if (userDTO.getNationalId() != null &&
                !userDTO.getNationalId().equals(existingUser.getNationalId())) {
            if (userRepository.findByNationalId(userDTO.getNationalId()).isPresent()) {
                throw new IllegalArgumentException("National ID already exists: " + userDTO.getNationalId());
            }
        }

        // Contract business logic
        if (userDTO.getContractType() == ContractType.CDD) {
            // If changing to CDD or already CDD and updating end date to null
            if (userDTO.getContractEndDate() == null &&
                    (existingUser.getContractType() != ContractType.CDD ||
                            existingUser.getContractEndDate() == null)) {
                throw new IllegalArgumentException("Contract end date is required for CDD contracts");
            }
        }

        // Date validation
        if (userDTO.getBirthDate() != null && userDTO.getBirthDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Birth date cannot be in the future");
        }

        // Salary validation
        if (userDTO.getBaseSalary() != null && userDTO.getBaseSalary() < 0) {
            throw new IllegalArgumentException("Base salary cannot be negative");
        }

        // Evaluation score validation (1-5)
        if (userDTO.getEvaluationScore() != null &&
                (userDTO.getEvaluationScore() < 1 || userDTO.getEvaluationScore() > 5)) {
            throw new IllegalArgumentException("Evaluation score must be between 1 and 5");
        }
    }

    private void updateEntityFromDTO(User user, UserRequestDTO dto) {
        // Personal Information
        if (dto.getFirstName() != null) user.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) user.setLastName(dto.getLastName());
        if (dto.getBirthDate() != null) user.setBirthDate(dto.getBirthDate());
        if (dto.getGender() != null) user.setGender(dto.getGender());
        if (dto.getNationalId() != null) user.setNationalId(dto.getNationalId());
        if (dto.getNationality() != null) user.setNationality(dto.getNationality());
        if (dto.getMaritalStatus() != null) user.setMaritalStatus(dto.getMaritalStatus());

        // Contact Information
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getAddress() != null) user.setAddress(dto.getAddress());

        // Job Information
        if (dto.getJobTitle() != null) user.setJobTitle(dto.getJobTitle());
        if (dto.getDepartment() != null) user.setDepartment(dto.getDepartment());
        if (dto.getService() != null) user.setService(dto.getService());

        // Contract Information
        if (dto.getHireDate() != null) user.setHireDate(dto.getHireDate());
        if (dto.getContractType() != null) user.setContractType(dto.getContractType());
        if (dto.getContractEndDate() != null) user.setContractEndDate(dto.getContractEndDate());

        // Salary Information
        if (dto.getBaseSalary() != null) user.setBaseSalary(dto.getBaseSalary());
        if (dto.getHousingAllowance() != null) user.setHousingAllowance(dto.getHousingAllowance());

        // Evaluation
        if (dto.getEvaluationScore() != null) {
            user.setEvaluationScore(dto.getEvaluationScore());
            user.setEvaluationDate(LocalDate.now());  // Auto-set evaluation date
        }

        // System Information
        if (dto.getActive() != null) user.setActive(dto.getActive());

        // Government Information
        if (dto.getSocialSecurityNumber() != null) user.setSocialSecurityNumber(dto.getSocialSecurityNumber());

        // Family Information
        if (dto.getChildrenCount() != null) user.setChildrenCount(dto.getChildrenCount());

        // Note: We don't update employeeCode, passwordHash, createdBy, lastLogin here
        // Note: Manager relationships would need separate endpoints
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
        log.info("User deleted successfully with ID: {}", id);
    }
}