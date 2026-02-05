package com.example.pfe.Service;

import com.example.pfe.dto.UserRequestDTO;
import com.example.pfe.dto.UserResponseDTO;
import com.example.pfe.entities.Role;
import com.example.pfe.entities.User;
import com.example.pfe.enums.RoleName;
import com.example.pfe.Repository.RoleRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper; // Use UserMapper instead of ModelMapper
    private final EmployeeCodeGenerator employeeCodeGenerator;
    private final EmailService emailService;
    private final JwtService jwtService;

    /**
     * Create a new employee with automatic employee code generation
     */
    public UserResponseDTO createUser(UserRequestDTO userRequestDTO) {
        log.info("Creating a new user: {}", userRequestDTO.getEmail());

        // 1. Preliminary validations
        validateUserCreation(userRequestDTO);

        // 2. Generate employee code
        String employeeCode = generateUniqueEmployeeCode(userRequestDTO);
        log.info("Employee code generated: {}", employeeCode);

        // 3. Generate a secure temporary password
        String temporaryPassword = generateTemporaryPassword();
        log.debug("Temporary password generated for {}", userRequestDTO.getEmail());

        // 4. Map DTO to Entity
        User user = mapToUserEntity(userRequestDTO, employeeCode, temporaryPassword);

        // 5. Assign roles
        assignRolesToUser(user, userRequestDTO);

        // 6. Handle hierarchical relationships
        setUserRelations(user, userRequestDTO);

        // 7. Save the user to get ID
        user = userRepository.save(user);
        log.info("User created with ID: {}", user.getId());

        // 8. Generate and send activation email
        sendActivationEmail(user, employeeCode, temporaryPassword);

        // 9. Return response DTO
        return convertToResponseDTO(user);
    }

    /**
     * Validate data before creation
     */
    private void validateUserCreation(UserRequestDTO userRequestDTO) {
        // Check email uniqueness
        if (userRepository.existsByEmail(userRequestDTO.getEmail())) {
            throw new BusinessException("A user with email '" + userRequestDTO.getEmail() + "' already exists");
        }

        // Check national ID uniqueness
        if (userRepository.existsByNationalId(userRequestDTO.getNationalId())) {
            throw new BusinessException("A user with national ID '" + userRequestDTO.getNationalId() + "' already exists");
        }

        // Check hire date
        if (userRequestDTO.getHireDate() != null && userRequestDTO.getHireDate().isAfter(java.time.LocalDate.now())) {
            throw new BusinessException("Hire date cannot be in the future");
        }

        // Check birth date
        if (userRequestDTO.getBirthDate() != null && !userRequestDTO.getBirthDate().isBefore(java.time.LocalDate.now().minusYears(16))) {
            throw new BusinessException("Employee must be at least 16 years old");
        }
    }

    /**
     * Generate a unique employee code
     */
    private String generateUniqueEmployeeCode(UserRequestDTO userRequestDTO) {
        String employeeCode;
        int attempts = 0;
        final int MAX_ATTEMPTS = 10;

        do {
            // Create a temporary user to generate the code
            User tempUser = new User();
            tempUser.setJobTitle(userRequestDTO.getJobTitle());
            tempUser.setDepartment(userRequestDTO.getDepartment());

            employeeCode = employeeCodeGenerator.generateEmployeeCode(tempUser);

            attempts++;
            if (attempts > MAX_ATTEMPTS) {
                throw new BusinessException("Unable to generate unique employee code after " + MAX_ATTEMPTS + " attempts");
            }

        } while (userRepository.existsByEmployeeCode(employeeCode));

        log.debug("Unique employee code generated after {} attempt(s): {}", attempts, employeeCode);
        return employeeCode;
    }

    /**
     * Generate a secure temporary password
     */
    private String generateTemporaryPassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%";

        Random random = new Random();
        StringBuilder password = new StringBuilder();

        // Ensure at least one character of each type
        password.append(upper.charAt(random.nextInt(upper.length())));
        password.append(lower.charAt(random.nextInt(lower.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(special.charAt(random.nextInt(special.length())));

        // Add 4 additional random characters
        String allChars = upper + lower + digits + special;
        for (int i = 0; i < 4; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // Shuffle characters
        char[] chars = password.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        return new String(chars);
    }

    /**
     * Map DTO to User entity with authentication information
     */
    private User mapToUserEntity(UserRequestDTO userRequestDTO, String employeeCode, String temporaryPassword) {
        // Use UserMapper to map basic fields
        User user = userMapper.toEntity(userRequestDTO);

        // Define authentication information
        user.setEmployeeCode(employeeCode);
        user.setUsername(employeeCode); // Default username = employee code
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));

        // Account status
        user.setEnabled(false); // Account disabled until activation
        user.setFirstLogin(true); // First login required
        user.setAccountNonExpired(true);
        user.setCredentialsNonExpired(true);
        user.setAccountNonLocked(true);

        // Activate by default unless specified otherwise
        if (user.getActive() == null) {
            user.setActive(true);
        }

        // Generate activation token
        String activationToken = generateActivationToken(user);
        user.setActivationToken(activationToken);
        user.setActivationTokenExpiry(LocalDateTime.now().plusDays(7)); // Valid for 7 days

        return user;
    }

    /**
     * Generate JWT token for account activation
     */
    private String generateActivationToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("employeeCode", user.getEmployeeCode());
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());
        claims.put("type", "ACCOUNT_ACTIVATION");

        // Token valid for 7 days
        return jwtService.generateTokenWithExpiration(
                claims,
                user.getEmail(),
                7 * 24 * 60 * 60 * 1000L
        );
    }

    /**
     * Assign roles to user
     */
    private void assignRolesToUser(User user, UserRequestDTO userRequestDTO) {
        List<Role> roles = new ArrayList<>();

        // By default, assign EMPLOYEE role
        Role defaultRole = roleRepository.findByName(RoleName.EmPLOYEE)
                .orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE role not found in database"));
        roles.add(defaultRole);

        // If needed to assign other roles, add logic here
        if (userRequestDTO.getJobTitle() != null) {
            assignAdditionalRolesBasedOnJobTitle(userRequestDTO.getJobTitle(), roles);
        }

        user.setRoles(roles);
        log.debug("Roles assigned to user {}: {}", user.getEmail(),
                roles.stream().map(r -> r.getName().name()).collect(Collectors.toList()));
    }

    /**
     * Assign additional roles based on job title
     */
    private void assignAdditionalRolesBasedOnJobTitle(String jobTitle, List<Role> roles) {
        String title = jobTitle.toLowerCase();

        if (title.contains("manager") || title.contains("chef") || title.contains("directeur")) {
            roleRepository.findByName(RoleName.PROJECT_MANAGER).ifPresent(roles::add);
        }

        if (title.contains("admin") || title.contains("administrateur")) {
            roleRepository.findByName(RoleName.ADMIN).ifPresent(roles::add);
        }

        if (title.contains("directeur général") || title.contains("general manager")) {
            roleRepository.findByName(RoleName.GENERAL_MANAGER).ifPresent(roles::add);
        }
    }

    /**
     * Define hierarchical relationships
     */
    private void setUserRelations(User user, UserRequestDTO userRequestDTO) {
        // Direct manager
        if (userRequestDTO.getDirectManagerId() != null) {
            User directManager = userRepository.findById(userRequestDTO.getDirectManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Direct manager with ID " + userRequestDTO.getDirectManagerId() + " not found"));
            user.setDirectManager(directManager);
        }

        // Assigned project manager
        if (userRequestDTO.getAssignedProjectManagerId() != null) {
            User projectManager = userRepository.findById(userRequestDTO.getAssignedProjectManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Project manager with ID " + userRequestDTO.getAssignedProjectManagerId() + " not found"));
            user.setAssignedProjectManager(projectManager);
        }
    }

    /**
     * Send activation email with credentials
     */
    private void sendActivationEmail(User user, String employeeCode, String temporaryPassword) {
        try {
            emailService.sendWelcomeEmail(
                    user.getEmail(),
                    user.getFirstName() + " " + user.getLastName(),
                    employeeCode,
                    temporaryPassword,
                    user.getActivationToken()
            );
            log.info("Activation email sent to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Error sending activation email to {}: {}",
                    user.getEmail(), e.getMessage());
            // Do not block user creation if email fails
        }
    }

    /**
     * Convert User entity to UserResponseDTO
     */
    private UserResponseDTO convertToResponseDTO(User user) {
        return userMapper.toResponseDTO(user);
    }

    /**
     * Update an existing user
     */
    public UserResponseDTO updateUser(Long id, UserRequestDTO userRequestDTO) {
        return null;
    }

    /**
     * Determine if employee code should be regenerated
     */
    private boolean shouldRegenerateEmployeeCode(User existingUser, UserRequestDTO newData) {
        // Regenerate if department changes
        if (existingUser.getDepartment() != newData.getDepartment()) {
            return true;
        }

        // Regenerate if job title changes
        if (existingUser.getJobTitle() != null && newData.getJobTitle() != null &&
                !existingUser.getJobTitle().equals(newData.getJobTitle())) {
            return true;
        }

        return false;
    }

    /**
     * Get all users
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAllUsers() {
        log.debug("Retrieving all users");
        return userRepository.findAll().stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get user by ID
     */
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Long id) {
        log.debug("Retrieving user with ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + id + " not found"));
        return convertToResponseDTO(user);
    }

    /**
     * Get user by employee code
     */
    @Transactional(readOnly = true)
    public UserResponseDTO getUserByEmployeeCode(String employeeCode) {
        log.debug("Retrieving user with employee code: {}", employeeCode);
        User user = userRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("User with employee code " + employeeCode + " not found"));
        return convertToResponseDTO(user);
    }

    /**
     * Deactivate a user (soft delete)
     */
    public void deactivateUser(Long id) {
        log.info("Deactivating user with ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + id + " not found"));

        user.setActive(false);
        user.setEnabled(false); // Also disable access
        userRepository.save(user);

        log.info("User {} deactivated successfully", user.getEmail());
    }

    /**
     * Reactivate a user
     */
    public void reactivateUser(Long id) {
        log.info("Reactivating user with ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + id + " not found"));

        user.setActive(true);
        user.setEnabled(true); // Also enable access
        userRepository.save(user);

        log.info("User {} reactivated successfully", user.getEmail());
    }

    /**
     * Reset user password (admin only)
     */
    public void resetUserPassword(Long id) {
        log.info("Resetting password for user with ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + id + " not found"));

        // Generate new temporary password
        String newTemporaryPassword = generateTemporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(newTemporaryPassword));
        user.setFirstLogin(true); // Force password change on next login
        user.setCredentialsNonExpired(true);

        userRepository.save(user);

        // Send new password via email
        try {
            emailService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getFirstName() + " " + user.getLastName(),
                    newTemporaryPassword,
                    user.getEmployeeCode()
            );
            log.info("Password reset email sent to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Error sending password reset email: {}", e.getMessage());
            throw new BusinessException("Error sending password reset email");
        }
    }

    /**
     * Search users by criteria
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> searchUsers(String keyword, String department, Boolean active) {
        log.debug("Searching users with keyword: {}, department: {}, active: {}",
                keyword, department, active);

        // Convert string department to enum if necessary
        com.example.pfe.enums.Department deptEnum = null;
        if (department != null && !department.isEmpty()) {
            try {
                deptEnum = com.example.pfe.enums.Department.valueOf(department.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid department: {}", department);
            }
        }

        return userRepository.searchUsers(keyword, deptEnum, active).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Count total number of users
     */
    @Transactional(readOnly = true)
    public long countUsers() {
        return userRepository.count();
    }

    /**
     * Count users by department
     */
    @Transactional(readOnly = true)
    public Map<String, Long> countUsersByDepartment() {
        return userRepository.findAll().stream()
                .filter(user -> user.getDepartment() != null)
                .collect(Collectors.groupingBy(
                        user -> user.getDepartment().name(),
                        Collectors.counting()
                ));
    }

    /**
     * Check if an employee code exists
     */
    @Transactional(readOnly = true)
    public boolean existsByEmployeeCode(String employeeCode) {
        return userRepository.existsByEmployeeCode(employeeCode);
    }

    /**
     * Check if an email exists
     */
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Get users by department
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getUsersByDepartment(String department) {
        log.debug("Retrieving users from department: {}", department);

        com.example.pfe.enums.Department deptEnum = null;
        if (department != null && !department.isEmpty()) {
            try {
                deptEnum = com.example.pfe.enums.Department.valueOf(department.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid department: {}", department);
                return new ArrayList<>();
            }
        }

        return userRepository.findByDepartment(deptEnum).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get available managers for assignment
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getAvailableManagers() {
        log.debug("Retrieving available managers");

        // Get users with manager roles
        return userRepository.findByRoles_NameIn(
                        Arrays.asList(RoleName.GENERAL_MANAGER, RoleName.PROJECT_MANAGER)
                ).stream()
                .filter(user -> user.getActive() != null && user.getActive()) // ✅ Use getActive() instead of isActive()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public void deleteUser(Long id) {
    }
}