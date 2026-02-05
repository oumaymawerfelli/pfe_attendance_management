package com.example.pfe.Service;

import com.example.pfe.dto.*;
import com.example.pfe.entities.*;
import com.example.pfe.Repository.RoleRepository;
import com.example.pfe.Repository.UserRepository;
import com.example.pfe.enums.RoleName;
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
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private static final int MAX_EMPLOYEE_CODE_ATTEMPTS = 10;// Prevents infinite loops
    private static final int ACTIVATION_TOKEN_EXPIRY_DAYS = 7;// Account activation window
    private static final int PASSWORD_LENGTH = 12;// Temporary password complexity

    // Temporary in-memory storage (Volatile - lost on restart)
   // private final Map<Long, String> temporaryPasswordStore = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;// BCrypt hashing
    private final JwtService jwtService;// Token generation
    private final RoleRepository roleRepository;
    private final EmployeeCodeGenerator employeeCodeGenerator;
    private final EmailService emailService;
    private final UserMapper userMapper;// DTO conversion

    // ========== PUBLIC METHODS ==========

    public UserResponseDTO createEmployee(RegisterRequestDTO request) {
      //  Prevents duplicate user accounts
        validateEmployeeCreation(request);

        // Generate password BEFORE creating user
        String temporaryPassword = generateSecureRandomPassword();

        User user = createUserFromRequest(request, temporaryPassword);
        assignDefaultRole(user);
        setReportingRelationships(user, request);

        user = userRepository.save(user);


      //  Account Activation Setup
        setupAccountActivation(user);

        // Send email with password (stored temporarily)
        sendWelcomeEmail(user, temporaryPassword);
        return userMapper.toResponseDTO(user);
    }
//This handles user login:
    public JwtResponseDTO authenticate(LoginRequestDTO request) {
        User user = validateLoginCredentials(request);
        validateAccountStatus(user);

        String token = jwtService.generateToken(user);
        log.info("Login successful for: {}", user.getEmail());

        return buildLoginResponse(token, user);
    }

    // ========== PRIVATE HELPER METHODS ==========

    private void validateEmployeeCreation(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("A user with this email already exists");
        }
    }

    private User createUserFromRequest(RegisterRequestDTO request, String temporaryPassword) {
        String employeeCode = generateUniqueEmployeeCode(request);

        return buildUserEntity(request, employeeCode, temporaryPassword);
    }

    private String generateSecureRandomPassword() {
        String uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lowercase = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%";
        String allChars = uppercase + lowercase + digits + special;

        Random random = new Random();
        StringBuilder password = new StringBuilder();

        // Ensure at least one of each character type
        password.append(uppercase.charAt(random.nextInt(uppercase.length())));
        password.append(lowercase.charAt(random.nextInt(lowercase.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(special.charAt(random.nextInt(special.length())));

        // Fill remaining characters
        for (int i = 4; i < PASSWORD_LENGTH; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // Shuffle the password
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }

        return new String(passwordArray);
    }

    private String generateUniqueEmployeeCode(RegisterRequestDTO request) {
        User tempUser = User.builder()
                .jobTitle(request.getJobTitle())
                .department(request.getDepartment())
                .build();

        String employeeCode;
        int attempts = 0;

        do {
            employeeCode = employeeCodeGenerator.generateEmployeeCode(tempUser);
            attempts++;
            if (attempts >= MAX_EMPLOYEE_CODE_ATTEMPTS) {
                throw new BusinessException("Could not generate unique employee code");
            }
        } while (userRepository.existsByEmployeeCode(employeeCode));

        return employeeCode;
    }

    private User buildUserEntity(RegisterRequestDTO request, String employeeCode, String temporaryPassword) {
        // Store password temporarily for email
        // Note: We'll store it in the map after we have the user ID

        return User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .birthDate(request.getBirthDate())
                .hireDate(request.getHireDate())
                .nationality(request.getNationality())
                .nationalId(request.getNationalId())
                .baseSalary(request.getBaseSalary())
                .department(request.getDepartment())
                .contractType(request.getContractType())
                .gender(request.getGender())
                .maritalStatus(request.getMaritalStatus())
                .active(Optional.ofNullable(request.getActive()).orElse(true))
                .jobTitle(request.getJobTitle())
                .service(request.getService())
                .address(request.getAddress())
                .contractEndDate(request.getContractEndDate())
                .housingAllowance(request.getHousingAllowance())
                .evaluationScore(request.getEvaluationScore())
                .socialSecurityNumber(request.getSocialSecurityNumber())
                .childrenCount(Optional.ofNullable(request.getChildrenCount()).orElse(0))
                .employeeCode(employeeCode)
                .username(employeeCode)
                .passwordHash(passwordEncoder.encode(temporaryPassword))
                .enabled(false)
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .accountNonLocked(true)
                .firstLogin(true)
                .build();
    }

    private void assignDefaultRole(User user) {
        Role defaultRole = roleRepository.findByName(RoleName.EmPLOYEE)
                .orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE role not found"));
        user.setRoles(new ArrayList<>(List.of(defaultRole)));
    }

    private void setReportingRelationships(User user, RegisterRequestDTO request) {
        if (request.getDirectManagerId() != null) {
            User manager = userRepository.findById(request.getDirectManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Direct manager not found"));
            user.setDirectManager(manager);
        }

        if (request.getAssignedProjectManagerId() != null) {
            User projectManager = userRepository.findById(request.getAssignedProjectManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project manager not found"));
            user.setAssignedProjectManager(projectManager);
        }
    }

    private void setupAccountActivation(User user) {
        Map<String, Object> claims = Map.of(
                "userId", user.getId(),
                "type", "ACCOUNT_ACTIVATION"
        );

        long expirationMs = ACTIVATION_TOKEN_EXPIRY_DAYS * 24L * 60 * 60 * 1000;
        String activationToken = jwtService.generateTokenWithExpiration(claims, user, expirationMs);

        user.setActivationToken(activationToken);
        user.setActivationTokenExpiry(LocalDateTime.now().plusDays(ACTIVATION_TOKEN_EXPIRY_DAYS));

        userRepository.save(user);
    }

    private void sendWelcomeEmail(User user, String temporaryPassword) {
        try {

            emailService.sendWelcomeEmail(
                    user.getEmail(),
                    user.getFirstName() + " " + user.getLastName(),
                    user.getEmployeeCode(),
                    temporaryPassword,
                    user.getActivationToken()
            );
            log.info("Activation email sent to {}", user.getEmail());

        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
            handleEmailFailure(user, temporaryPassword, e);
        }
    }

    private void handleEmailFailure(User user, String temporaryPassword, Exception e) {
        // In development, use simulated version
        try {
            emailService.sendWelcomeEmailDev(
                    user.getEmail(),
                    user.getFirstName() + " " + user.getLastName(),
                    user.getEmployeeCode(),
                    temporaryPassword,
                    user.getActivationToken()
            );
            log.warn("Development email sent to {}", user.getEmail());
        } catch (Exception devException) {
            log.error("Even development email failed: {}", devException.getMessage());
            throw new BusinessException(
                    "Failed to send activation email. Please contact administrator to resend."
            );
        }
    }

    private User validateLoginCredentials(LoginRequestDTO request) {
        User user = userRepository.findByUsernameOrEmail(request.getUsername(), request.getUsername())
                .orElseThrow(() -> new BusinessException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid credentials");
        }

        return user;
    }

    private void validateAccountStatus(User user) {
        if (!user.isEnabled()) {
            throw new BusinessException("Please activate your account before logging in");
        }

        if (user.isFirstLogin()) {
            throw new BusinessException("Please change your password before first login");
        }
    }

    private JwtResponseDTO buildLoginResponse(String token, User user) {
        return JwtResponseDTO.builder()
                .token(token)
                .message("Login successful")
                .user(userMapper.toResponseDTO(user))
                .build();
    }

    // ========== ADDITIONAL METHODS ==========

    /**
     * Retry sending welcome email (for admin use)
     */
    public void resendActivationEmail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isEnabled()) {
            throw new BusinessException("User account is already activated");
        }

        // Verify activation token is still valid
        if (user.getActivationTokenExpiry().isBefore(LocalDateTime.now())) {
            // Regenerate activation token if expired
            setupAccountActivation(user);
        }

        // Send email with activation link only (no password)
        try {
            emailService.sendActivationReminderEmail(
                    user.getEmail(),
                    user.getFirstName() + " " + user.getLastName(),
                    user.getActivationToken()
            );
            log.info("Activation reminder sent to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to resend activation email: {}", e.getMessage());
            throw new BusinessException("Failed to resend activation email");
        }
    }
    //Reset password for user who lost their temporary password
    //     * Only admin can do this
    public String resetTemporaryPassword(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isEnabled()) {
            throw new BusinessException("User account is already activated. Use forgot password flow.");
        }

        // Generate new temporary password
        String newTemporaryPassword = generateSecureRandomPassword();
        user.setPasswordHash(passwordEncoder.encode(newTemporaryPassword));

        // Regenerate activation token
        setupAccountActivation(user);

        userRepository.save(user);

        // Send new welcome email
        sendWelcomeEmail(user, newTemporaryPassword);

        // Return password to admin (to give to user)
        return newTemporaryPassword;
    }
}