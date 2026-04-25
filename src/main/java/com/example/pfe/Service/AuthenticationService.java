package com.example.pfe.Service;

import com.example.pfe.Repository.RoleRepository;
import com.example.pfe.dto.*;
import com.example.pfe.entities.Role;
import com.example.pfe.entities.User;
import com.example.pfe.enums.RoleName;
import com.example.pfe.exception.BusinessException;
import com.example.pfe.exception.ResourceNotFoundException;
import com.example.pfe.mapper.UserMapper;
import com.example.pfe.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.pfe.Service.AttendanceService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final UserMapper userMapper;
    private final AttendanceService attendanceService;
    private final RoleRepository roleRepository;
    private final NotificationService notificationService;
    // ==================== REGISTRATION ====================

    public RegistrationResponseDTO register(RegisterRequestDTO request) {
        // Log that someone is trying to register
        log.info("Starting registration for email: {}..........", request.getEmail());

        // STEP 1: Check if the data itself is valid (like email format, etc)
        request.validate();

        // STEP 2: Check if email or national ID already exists in database
        validateUniqueConstraints(request);

        // STEP 3: Create a new User object from the request data
        User user = createUserFromRequest(request);


        // STEP 4: Set username as email (they use email to login)
        user.setUsername(request.getEmail());
        // STEP 5: Save the user to database
        User savedUser = userRepository.save(user);
        // STEP 6: Log success
        log.info("User saved: ID={}, Email={}", savedUser.getId(), savedUser.getEmail());
        // STEP 7: Return response to controller
        return buildRegistrationResponse(savedUser);
    }
    // Checking for duplicates (The Helper Methods)
    private void validateUniqueConstraints(RegisterRequestDTO request) {
        // Check if email already exists in database
        if (userRepository.existsByEmail(request.getEmail())) {
            // If yes, stop and tell the user
            throw new BusinessException("Email already registered");
        }

        // Check if national ID already exists in database
        if (userRepository.existsByNationalId(request.getNationalId())) {
            // If yes, stop and tell the user
            throw new BusinessException("National ID already registered");
        }
    }
    //Building the user object
    private User createUserFromRequest(RegisterRequestDTO request) {
        // Convert the DTO (request data) to a User entity
        User user = userMapper.toEntity(request);


        // Generate a random temporary password (user will change it later)
        // UUID.randomUUID() creates something like "123e4567-e89b-12d3-a456-426614174000"
        // .substring(0,12) takes first 12 characters: "123e4567-e89b"
        String tempPassword = UUID.randomUUID().toString().substring(0, 12);
        // Encrypt the password before saving (never store plain passwords!)
        user.setPasswordHash(passwordEncoder.encode(tempPassword));

        // Set account status flags
        user.setEnabled(false);// Account not active yet (needs email verification)
        user.setFirstLogin(true);// This will be their first login
        user.setActive(request.getActive() != null ? request.getActive() : true);// Account is active
        // Mark as waiting for admin / general manager approval
        user.setRegistrationPending(true);

        // Assign default EMPLOYEE role to every self-registered user
        Role employeeRole = roleRepository.findByName(RoleName.EMPLOYEE)
                .orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE role not found"));
        user.setRoles(new ArrayList<>(List.of(employeeRole)));

        // Generate a special token for email verification (like a digital key that expires)
        String activationToken = jwtService.generateActivationToken(user);
        user.setActivationToken(activationToken);// Save the token to the user so we can verify it later
        user.setActivationTokenExpiry(LocalDateTime.now().plusDays(7));// Token expires in 7 days

        return user;
    }
    //Preparing the response Creates actual instance, Has data?  Yes, fills with real values,Reusable?  Called each time we need a response
    private RegistrationResponseDTO buildRegistrationResponse(User user) {
        return RegistrationResponseDTO.builder()
                .userId(user.getId())// Database ID
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .active(user.getActive())
                .enabled(user.isEnabled())
                .message("Registration successful.")
                .activationEmailSent(false)// Email not sent yet (admin approval needed)
                .build();
    }

    // ==================== REGISTRATION APPROVAL (ADMIN / GENERAL MANAGER) ====================


     // List all users that self‑registered and are still waiting for approval.


    //Marks the method as a read-only database transaction.
    //Spring will not allow modifications to the database inside this method.
    @Transactional(readOnly = true)
    //Returns a list of DTOs, not entities.
    //get all users whose registration is pending approval.
    public java.util.List<RegistrationResponseDTO> getPendingRegistrations() {
        // Find all users who are waiting for approval (registrationPending = true)
        return userRepository.findByRegistrationPendingTrue()
                //allows functional operations like map, filter, peek, etc on the list of users.
                .stream()// Convert list to stream for processing
                //method that converts a User entity into RegistrationResponseDTO
                .map(this::buildRegistrationResponse)
                //lets you inspect or modify each element in the stream without changing the stream type.
                //Here, you add extra info for the frontend:
                //message = "Waiting for approval"
                //activationEmailSent = false
                //Useful for adding runtime data without touching the entity.
                .peek(dto -> {
                    dto.setMessage("Waiting for approval");
                    dto.setActivationEmailSent(false);
                })
                //Converts the stream back into a regular List<RegistrationResponseDTO> for return.
                //Now the frontend can use it to display pending users.
                .toList();
    }


     //Approve a pending self‑registration and send the activation email.
    //Approve Registration (Admin clicks "Approve")
    public RegistrationResponseDTO approveRegistration(Long userId) {
        // STEP 1: Find the user in database
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // STEP 2: Check if already activated
        if (user.isEnabled() && !user.isRegistrationPending()) {
            throw new BusinessException("Account already activated");
        }
        // STEP 3: Check if token needs to be regenerated
        if (user.getActivationToken() == null ||
                user.getActivationTokenExpiry() == null ||
                user.getActivationTokenExpiry().isBefore(LocalDateTime.now())) {

            // Token is missing or expired - create new one
            String token = jwtService.generateActivationToken(user);
            user.setActivationToken(token);
            user.setActivationTokenExpiry(LocalDateTime.now().plusDays(7));
        }
        // STEP 4: Update user status
        user.setRegistrationPending(false);// No longer pending
        user.setActive(true);  // Mark as active
        userRepository.save(user);

        // STEP 5: Send activation email to user (using the method with 2 parameters)
        emailService.sendActivationReminderEmail(user.getEmail(), user.getActivationToken());

        // STEP 6: Build and return response
        RegistrationResponseDTO dto = buildRegistrationResponse(user);
        dto.setMessage("Registration approved. Activation email sent.");
        dto.setActivationEmailSent(true);
        return dto;
    }
    // ==================== LOGIN ====================

    public JwtResponseDTO authenticate(LoginRequestDTO request) {

        // Step 1: Check if email and password are correct
        User user = validateCredentials(request);

        // Step 2: Check if account is active and ready to use
        checkAccountStatus(user);

        // Step 3: Update last login date
        user.setLastLogin(LocalDate.now());
        user = userRepository.save(user);

        log.info(" User {} logged in. Last login updated to {}",
                user.getEmail(), user.getLastLogin());

        // ─────────────────────────────────────────────────────
        // Step 3b: AUTO CHECK-IN on first login of the day ← ADD THIS
        // Safe to call every login — service guards against duplicates
        try {
            attendanceService.checkIn(user.getId());
        } catch (Exception e) {
            // Never block login if check-in fails
            log.error("Check-in failed for user {} — login not blocked: {}",
                    user.getId(), e.getMessage());
        }
        // ─────────────────────────────────────────────────────

        // Step 4: Generate token
        String accessToken = jwtService.generateAccessToken(user);

        // Step 5: Return response
        return JwtResponseDTO.builder()
                .token(accessToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .message("Login successful")
                .user(userMapper.toResponseDTO(user))
                .build();
    }
    // ==================== ACCOUNT ACTIVATION ====================


    public JwtResponseDTO activateAccount(ActivationRequestDTO request) {
        // Check passwords match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        // Validate token and get user
        User user = getValidUserFromActivationToken(request.getToken());

        // Set username and password
        if (request.getUsername() == null || request.getUsername().length() < 3) {
            throw new BusinessException("Username must be at least 3 characters");
        }

        // ACTIVATE THE ACCOUNT!
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setEnabled(true);
        user.setFirstLogin(false);
        user.setActivationToken(null);
        user.setActivationTokenExpiry(null);

        userRepository.save(user);
        notificationService.notifyWelcome(user.getId());

        // Return JWT token so user is logged in immediately after activation
        String accessToken = jwtService.generateAccessToken(user);

        return JwtResponseDTO.builder()
                .token(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getRemainingTime(accessToken) / 1000)
                .message("Account activated successfully")
                .build();
    }

    //Check if token is valid
    //User clicks activation link, frontend checks if token is still valid before showing the activation form. This prevents showing a form that will fail if the token is expired.
    public boolean validateActivationTokenApi(String token) {
        try {
            // Try to get user from token
            User user = getValidUserFromActivationToken(token);
            // Check if user exists, not enabled, and token not expired
            return user != null && !user.isEnabled()
                    && user.getActivationTokenExpiry().isAfter(LocalDateTime.now());
        } catch (Exception e) {
            // Any error means token is invalid
            return false;
        }
    }

  //Send new activation email if user lost the first one or it expired.
  // User enters their email, we check if they exist and are not activated,
  // then we generate a new token and send the email again.
    public ResendActivationResponseDTO resendActivationEmail(String email) {

        // STEP 1: Find user by email (case insensitive)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        // STEP 2: Check if already activated         (if yes, no need to resend)
        if (user.isEnabled()) {
            throw new BusinessException("Account already activated");
        }

        // STEP 3: Generate new token and save to user
        String token = jwtService.generateActivationToken(user);
        user.setActivationToken(token);
        user.setActivationTokenExpiry(LocalDateTime.now().plusDays(7));
        userRepository.save(user);

        // STEP 4: Send email with new token
        emailService.sendActivationReminderEmail(user.getEmail(), token);

        return ResendActivationResponseDTO.builder()
                .email(email)
                .message("Activation email resent")
                .build();
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private User validateCredentials(LoginRequestDTO request) {

        // Find user by email (case insensitive)
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new BusinessException("Invalid credentials"));

        // Check if password matches the encrypted one in database
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid credentials");
        }

        return user;
    }

    private void checkAccountStatus(User user) {

        // Check if account is activated (user clicked email link)
        if (!user.isEnabled()) {
            throw new BusinessException("Account not activated. Please check your email.");
        }
        // Check if this is first login (forces password change)
        if (user.isFirstLogin()) {
            throw new BusinessException("Password change required on first login");
        }
    }


    private User getValidUserFromActivationToken(String token) {
        // STEP 1: Check if token itself is valid (signature, expiration, type)
        if (!jwtService.isTokenValid(token) || !jwtService.isActivationToken(token)) {
            throw new BusinessException("Invalid or expired activation token");
        }

        // STEP 2: Find user with this token
        User user = userRepository.findByActivationToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // STEP 3: Check if already activated
        if (user.isEnabled()) {
            throw new BusinessException("Account already activated");
        }

        // STEP 4: Check if token expired
        if (user.getActivationTokenExpiry() == null ||
                user.getActivationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Activation token expired");
        }

        return user;
    }
}